package ca.cgagnier.wlednativeandroid.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.update.FirmwareChannelException
import ca.cgagnier.wlednativeandroid.service.update.FirmwareChecksumException
import ca.cgagnier.wlednativeandroid.service.update.FirmwareRelease
import ca.cgagnier.wlednativeandroid.service.update.FirmwareReleaseRepository
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val UPLOAD_TIMEOUT_SECONDS = 180L

enum class FirmwareUpdatePhase {
    Idle,
    Checking,
    UpToDate,
    Available,
    Downloading,
    Installing,
    Done,
    Failed,
}

enum class FirmwareFailure {
    NoDevice,
    DeviceOffline,
    ModelMismatch,
    Check,
    Download,
    Checksum,
    Upload,
}

data class FirmwareUpdateState(
    val phase: FirmwareUpdatePhase = FirmwareUpdatePhase.Idle,
    val release: FirmwareRelease? = null,
    val currentVersion: String? = null,
    val progress: Int = 0,
    val failure: FirmwareFailure? = null,
    val detail: String? = null,
) {
    val canDismiss: Boolean
        get() = phase != FirmwareUpdatePhase.Downloading &&
            phase != FirmwareUpdatePhase.Installing &&
            phase != FirmwareUpdatePhase.Checking
}

/**
 * 原生固件 OTA。
 *
 * 数据来源是统一固件通道（AList）的 manifest.json，而不是固件自带的 web 升级页：
 * 拉 manifest → 比较 versionCode → 下载并校验 sha256 → 用设备 /update 接口写入。
 */
class FirmwareUpdateViewModel(
    private val repository: FirmwareReleaseRepository,
    private val deviceApiFactory: DeviceApiFactory,
    private val appContext: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(FirmwareUpdateState())
    val state = _state.asStateFlow()

    private var checkJob: Job? = null
    private var updateJob: Job? = null

    fun check(device: DeviceWithState?) {
        if (device == null) {
            _state.value = FirmwareUpdateState(
                phase = FirmwareUpdatePhase.Failed,
                failure = FirmwareFailure.NoDevice,
            )
            return
        }
        if (checkJob?.isActive == true || updateJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            _state.value = FirmwareUpdateState(
                phase = FirmwareUpdatePhase.Checking,
                currentVersion = device.stateInfo?.info?.version,
            )
            try {
                val release = repository.fetchLatestFirmware()
                val deviceModel = device.stateInfo?.info?.product
                if (
                    !release.model.isNullOrBlank() &&
                    !deviceModel.isNullOrBlank() &&
                    !release.model.equals(deviceModel, ignoreCase = true)
                ) {
                    // 通道里是别的机型固件，绝不能下发
                    _state.value = FirmwareUpdateState(
                        phase = FirmwareUpdatePhase.Failed,
                        failure = FirmwareFailure.ModelMismatch,
                        release = release,
                        currentVersion = device.stateInfo?.info?.version,
                    )
                    return@launch
                }
                val hasUpdate = hasUpdate(device, release)
                _state.value = FirmwareUpdateState(
                    phase = if (hasUpdate) {
                        FirmwareUpdatePhase.Available
                    } else {
                        FirmwareUpdatePhase.UpToDate
                    },
                    release = release,
                    currentVersion = device.stateInfo?.info?.version,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                _state.value = FirmwareUpdateState(
                    phase = FirmwareUpdatePhase.Failed,
                    failure = FirmwareFailure.Check,
                    currentVersion = device.stateInfo?.info?.version,
                    detail = e.message,
                )
            }
        }
    }

    fun startUpdate(device: DeviceWithState?) {
        val release = _state.value.release ?: return
        if (device == null) {
            _state.value = _state.value.copy(phase = FirmwareUpdatePhase.Failed, failure = FirmwareFailure.NoDevice)
            return
        }
        if (!device.isOnline) {
            _state.value = _state.value.copy(
                phase = FirmwareUpdatePhase.Failed,
                failure = FirmwareFailure.DeviceOffline,
            )
            return
        }
        if (updateJob?.isActive == true) return

        updateJob = viewModelScope.launch {
            _state.update {
                it.copy(phase = FirmwareUpdatePhase.Downloading, progress = 0, failure = null, detail = null)
            }
            var downloaded: File? = null
            try {
                val file = repository.downloadFirmware(release, appContext.cacheDir) { percent ->
                    _state.update { it.copy(progress = percent) }
                }
                downloaded = file
                _state.update { it.copy(phase = FirmwareUpdatePhase.Installing) }

                val bytes = withContext(ioDispatcher) { file.readBytes() }
                val api = deviceApiFactory.create(device.device, UPLOAD_TIMEOUT_SECONDS)
                val response = api.updateDevice(bytes, release.file.substringAfterLast('/'))

                if (response.isSuccessful) {
                    _state.update { it.copy(phase = FirmwareUpdatePhase.Done) }
                } else {
                    _state.update {
                        it.copy(
                            phase = FirmwareUpdatePhase.Failed,
                            failure = FirmwareFailure.Upload,
                            detail = "HTTP ${response.code}",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirmwareChecksumException) {
                failWith(FirmwareFailure.Checksum, e)
            } catch (e: Exception) {
                val failure = if (e is FirmwareChannelException) {
                    FirmwareFailure.Download
                } else {
                    FirmwareFailure.Upload
                }
                failWith(failure, e)
            } finally {
                downloaded?.delete()
            }
        }
    }

    fun dismissError() {
        if (_state.value.phase == FirmwareUpdatePhase.Failed) {
            _state.value = FirmwareUpdateState()
        }
    }

    fun reset() {
        checkJob?.cancel()
        updateJob?.cancel()
        checkJob = null
        updateJob = null
        _state.value = FirmwareUpdateState()
    }

    private fun failWith(failure: FirmwareFailure, throwable: Exception) {
        _state.update {
            it.copy(
                phase = FirmwareUpdatePhase.Failed,
                failure = failure,
                detail = throwable.message,
            )
        }
    }

    private fun hasUpdate(device: DeviceWithState, release: FirmwareRelease): Boolean {
        val info = device.stateInfo?.info
        val currentCode = info?.buildId ?: 0
        if (currentCode > 0) return release.versionCode > currentCode
        // 极老的固件没有 vid，退化成版本串比较
        val currentVersion = info?.version
        return currentVersion.isNullOrBlank() || currentVersion != release.version
    }
}
