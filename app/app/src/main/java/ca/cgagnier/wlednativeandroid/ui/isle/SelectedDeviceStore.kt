package ca.cgagnier.wlednativeandroid.ui.isle

import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 当前选中的设备，以及跨页签共享的多灯同步开关。
 *
 * 四个页签是同一个设备的不同切面，选中状态必须共享 —— 在「灯效库」里点了灯效，
 * 切回「设备」看到的应该还是同一台灯。放在 Koin 里做单例，比每个页面各存一份干净。
 *
 * 多灯同步开关也放在这里，理由同上：它是**设备级**的偏好，不属于某一个页签。
 * 以前它只存在于设备页的 ViewModel 里，结果「灯效库」点灯效时不跟随同步开关，
 * 用户开了同步却只有当前那台灯变化 —— 开关和实际行为对不上。
 *
 * 开关本身**持久化**在 [UserPreferences] 里：它是用户主动设置的状态，
 * App 重启后仍然生效才符合直觉（否则每次开 App 都要重开一遍）。
 */
class SelectedDeviceStore(
    private val userPreferencesRepository: UserPreferencesRepository,
) {

    // Koin single 没有 ViewModel 的生命周期，用一个自带 Job 的 scope 收尾。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _macAddress = MutableStateFlow<String?>(null)
    val macAddress: StateFlow<String?> = _macAddress.asStateFlow()

    private val _syncEnabled = MutableStateFlow(false)
    val syncEnabled: StateFlow<Boolean> = _syncEnabled.asStateFlow()

    /**
     * 用户是否已经手动切换过开关。
     *
     * 持久化值只在**首次加载**时用来初始化，之后不再回灌 —— 否则用户在
     * App 启动瞬间点一下开关，稍后到达的磁盘读结果会把他的选择冲掉
     * （写是异步的，读回可能晚于一次手动切换）。
     */
    @Volatile
    private var userOverrode = false

    init {
        scope.launch {
            val persisted = userPreferencesRepository.multiLampSyncEnabled.first()
            if (!userOverrode) {
                _syncEnabled.value = persisted
            }
        }
    }

    fun select(macAddress: String?) {
        if (_macAddress.value != macAddress) {
            _macAddress.value = macAddress
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        userOverrode = true
        if (_syncEnabled.value == enabled) return
        _syncEnabled.value = enabled
        scope.launch {
            userPreferencesRepository.updateMultiLampSyncEnabled(enabled)
        }
    }
}
