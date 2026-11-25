package ca.cgagnier.wlednativeandroid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/** 设备端音频来源，持久化在设备 cfg 里，不随 App 重启丢失。 */
enum class DeviceAudioSource {
    DEVICE,
    APP,
}

/**
 * 设备页「音频来源」。
 *
 * 来源写在固件 `um.AudioReactive` 上：
 * - APP：`sync.mode = 2`（只收网络音频，手机当音源）
 * - 设备麦克风：`sync.mode = 0` + `digitalmic.type = 4`（ES7210）
 *
 * 写入后请求设备重启；重启期间页面只提示，不假设配置已经生效。
 */
class AudioSettingsViewModel(
    private val deviceApiFactory: DeviceApiFactory,
) : ViewModel() {

    data class UiState(
        val address: String? = null,
        val source: DeviceAudioSource? = null,
        val deviceAudioStatus: String? = null,
        val switching: Boolean = false,
        val switchFailed: Boolean = false,
        /** 配置已写入、设备正在重启。 */
        val rebootPending: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** 打开页面或下拉刷新时重新读设备状态。 */
    fun load(address: String) {
        if (_uiState.value.address == address && loadJob?.isActive == true) return
        loadJob?.cancel()
        _uiState.value = UiState(address = address)
        loadJob = viewModelScope.launch {
            val api = deviceApiFactory.create(address)
            val config = runCatching { api.getConfig().body }.getOrNull()
            val info = runCatching { api.getInfo().body }.getOrNull()
            _uiState.update {
                it.copy(
                    source = config?.um?.audioReactive?.sync?.let { sync ->
                        if (sync.isReceiveMode) DeviceAudioSource.APP else DeviceAudioSource.DEVICE
                    },
                    deviceAudioStatus = info?.userMods?.audioReactive?.let { audio ->
                        listOfNotNull(audio.audioSourceText, audio.soundProcessingText)
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
        _uiState.update { it.copy(switching = true, switchFailed = false) }
        viewModelScope.launch {
            val api = deviceApiFactory.create(address)
            val accepted = runCatching {
                val response = api.postConfig(configFor(source))
                response.code in 200..299
            }.getOrDefault(false)
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

    private fun configFor(source: DeviceAudioSource): WledConfig {
        val audio = if (source == DeviceAudioSource.APP) {
            AudioReactiveConfig(
                enabled = true,
                sync = AudioSyncConfig(port = AUDIO_SYNC_PORT, mode = SYNC_MODE_RECEIVE),
            )
        } else {
            AudioReactiveConfig(
                enabled = true,
                digitalmic = DigitalMicConfig(type = DM_TYPE_ES7210),
                sync = AudioSyncConfig(port = AUDIO_SYNC_PORT, mode = SYNC_MODE_LOCAL),
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
