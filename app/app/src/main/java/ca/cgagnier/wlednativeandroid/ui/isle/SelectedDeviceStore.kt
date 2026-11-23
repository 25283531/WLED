package ca.cgagnier.wlednativeandroid.ui.isle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前选中的设备。
 *
 * 四个页签是同一个设备的不同切面，选中状态必须共享 —— 在「灯效库」里点了灯效，
 * 切回「设备」看到的应该还是同一台灯。放在 Koin 里做单例，比每个页面各存一份干净。
 */
class SelectedDeviceStore {

    private val _macAddress = MutableStateFlow<String?>(null)
    val macAddress: StateFlow<String?> = _macAddress.asStateFlow()

    fun select(macAddress: String?) {
        if (_macAddress.value != macAddress) {
            _macAddress.value = macAddress
        }
    }
}
