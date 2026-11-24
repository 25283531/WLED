package ca.cgagnier.wlednativeandroid.ui.isle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前选中的设备，以及跨页签共享的同步开关。
 *
 * 四个页签是同一个设备的不同切面，选中状态必须共享 —— 在「灯效库」里点了灯效，
 * 切回「设备」看到的应该还是同一台灯。放在 Koin 里做单例，比每个页面各存一份干净。
 *
 * 多灯同步开关也放在这里，理由同上：它是**设备级**的偏好，不属于某一个页签。
 * 以前它只存在于设备页的 ViewModel 里，结果「灯效库」点灯效时不跟随同步开关，
 * 用户开了同步却只有当前那台灯变化 —— 开关和实际行为对不上。
 */
class SelectedDeviceStore {

    private val _macAddress = MutableStateFlow<String?>(null)
    val macAddress: StateFlow<String?> = _macAddress.asStateFlow()

    private val _syncEnabled = MutableStateFlow(false)
    val syncEnabled: StateFlow<Boolean> = _syncEnabled.asStateFlow()

    fun select(macAddress: String?) {
        if (_macAddress.value != macAddress) {
            _macAddress.value = macAddress
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        _syncEnabled.value = enabled
    }
}
