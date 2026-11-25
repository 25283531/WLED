package ca.cgagnier.wlednativeandroid.ui.settings

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.audio.BleAudioSyncSender
import ca.cgagnier.wlednativeandroid.model.wledapi.AudioReactiveConfig
import ca.cgagnier.wlednativeandroid.model.wledapi.AudioSyncConfig
import ca.cgagnier.wlednativeandroid.model.wledapi.DigitalMicConfig
import ca.cgagnier.wlednativeandroid.model.wledapi.UserModsConfig
import ca.cgagnier.wlednativeandroid.model.wledapi.WledConfig
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 灯带的音频来源。DEVICE 写在设备 cfg 里，BLE 与 PLAYBACK 的区分只能记在本地。 */
enum class DeviceAudioSource {
    DEVICE,
    BLE,
    PLAYBACK,
}

/**
 * 设备页「音频来源」。
 *
 * 设备端配置：
 * - 设备麦克风：`sync.mode = 0` + `digitalmic.type = 4`（ES7210）
 * - 蓝牙 / 直通：`sync.mode = 2`（只收网络音频，手机当音源）
 *
 * 后两者在固件里完全相同，具体走 BLE 还是 UDP 由界面选择并持久化在本地。
 * 写入后请求设备重启；重启期间页面只提示，不假设配置已经生效。
 */
class AudioSettingsViewModel(
    private val deviceApiFactory: DeviceApiFactory,
    context: Context,
    private val sourcePreferences: AudioSourcePreferenceStore,
) : ViewModel() {

    data class UiState(
        val address: String? = null,
        val source: DeviceAudioSource? = null,
        val deviceAudioStatus: String? = null,
        val switching: Boolean = false,
        val switchFailed: Boolean = false,
        /** 配置已写入、设备正在重启。 */
        val rebootPending: Boolean = false,
        val bleSupported: Boolean = false,
        val playbackSupported: Boolean = false,
    )

    private val playbackSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private val bleSupported = playbackSupported && BleAudioSyncSender.isSupported(context)

    private val _uiState = MutableStateFlow(
        UiState(bleSupported = bleSupported, playbackSupported = playbackSupported),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** 设备当前是否处于「接收网络音频」模式；null 表示还没读到配置。 */
    private var receivesNetworkAudio: Boolean? = null

    /** 打开页面或下拉刷新时重新读设备状态。 */
    fun load(address: String) {
        if (_uiState.value.address == address && loadJob?.isActive == true) return
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                address = address,
                source = null,
                deviceAudioStatus = null,
                switching = false,
                switchFailed = false,
                rebootPending = false,
            )
        }
        loadJob = viewModelScope.launch {
            val api = deviceApiFactory.create(address)
            val config = runCatching { api.getConfig().body }.getOrNull()
            val info = runCatching { api.getInfo().body }.getOrNull()
            val sync = config?.um?.audioReactive?.sync
            val receives = sync?.isReceiveMode
            receivesNetworkAudio = receives
            val stored = sourcePreferences.sourceFor(address)
            _uiState.update {
                it.copy(
                    source = when {
                        config == null -> null
                        receives != true -> DeviceAudioSource.DEVICE
                        stored == DeviceAudioSource.BLE -> DeviceAudioSource.BLE
                        else -> DeviceAudioSource.PLAYBACK
                    },
                    deviceAudioStatus = info?.userMods?.let { mods ->
                        listOfNotNull(mods.audioSourceText, mods.soundProcessingText)
                            .joinToString(" · ")
                            .takeIf { text -> text.isNotBlank() }
                    },
                )
            }
        }
    }

    fun selectSource(source: DeviceAudioSource) {
        val address = _uiState.value.address ?: return
        if (_uiState.value.switching) return
        // 接收模式下 BLE / 直通共用同一份 cfg，不必为了换传输方式重启设备
        if (!needsConfigWrite(source)) {
            sourcePreferences.save(address, source)
            _uiState.update { it.copy(source = source, switchFailed = false) }
            return
        }
        _uiState.update { it.copy(switching = true, switchFailed = false) }
        viewModelScope.launch {
            val api = deviceApiFactory.create(address)
            val accepted = runCatching {
                val response = api.postConfig(configFor(source))
                response.code in 200..299
            }.getOrDefault(false)
            if (accepted) {
                sourcePreferences.save(address, source)
                receivesNetworkAudio = source != DeviceAudioSource.DEVICE
            }
            _uiState.update {
                it.copy(
                    switching = false,
                    switchFailed = !accepted,
                    rebootPending = accepted,
                    source = if (accepted) source else it.source,
                    // 设备正在重启，旧的运行状态立刻失效，避免继续显示上一次的来源
                    deviceAudioStatus = if (accepted) null else it.deviceAudioStatus,
                )
            }
        }
    }

    private fun needsConfigWrite(source: DeviceAudioSource): Boolean = when (source) {
        DeviceAudioSource.DEVICE -> receivesNetworkAudio != false
        DeviceAudioSource.BLE, DeviceAudioSource.PLAYBACK -> receivesNetworkAudio != true
    }

    private fun configFor(source: DeviceAudioSource): WledConfig {
        val audio = if (source == DeviceAudioSource.DEVICE) {
            AudioReactiveConfig(
                enabled = true,
                digitalmic = DigitalMicConfig(type = DM_TYPE_ES7210),
                sync = AudioSyncConfig(port = AUDIO_SYNC_PORT, mode = SYNC_MODE_LOCAL),
            )
        } else {
            AudioReactiveConfig(
                enabled = true,
                sync = AudioSyncConfig(port = AUDIO_SYNC_PORT, mode = SYNC_MODE_RECEIVE),
            )
        }
        return WledConfig(um = UserModsConfig(audioReactive = audio), rb = true)
    }

    private companion object {
        const val AUDIO_SYNC_PORT = 11_988
        const val SYNC_MODE_RECEIVE = 2
        const val SYNC_MODE_LOCAL = 0
        const val DM_TYPE_ES7210 = 4
    }
}
