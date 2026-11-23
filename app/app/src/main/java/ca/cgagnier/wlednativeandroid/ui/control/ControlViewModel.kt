package ca.cgagnier.wlednativeandroid.ui.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.wledapi.Nightlight
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.effects.unpackRgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** WLED 夜灯（定时关灯）模式：渐变到目标亮度。 */
private const val NIGHTLIGHT_MODE_FADE = 1

/** 关闭时把目标亮度设为 0，即「关灯」而不是「调暗」。 */
private const val NIGHTLIGHT_TARGET_BRIGHTNESS_OFF = 0

/** WLED 颜色槽位数量（主色 / 次色 / 第三色）。 */
private const val COLOR_SLOT_COUNT = 3

/**
 * 控制页：开关、亮度、灯效、速度/强度、颜色、多灯同步、定时关灯。
 *
 * 所有指令都走 WebSocket 状态推送（[DeviceConnectionManager.sendState] /
 * [DeviceConnectionManager.sendSegmentPatch]），因此不需要 HTTP 往返，界面也能立即反映结果。
 */
class ControlViewModel(
    private val deviceConnectionManager: DeviceConnectionManager,
) : ViewModel() {

    val devices: StateFlow<List<DeviceWithState>> = deviceConnectionManager.allDevicesWithState

    private val _selectedDeviceMac = MutableStateFlow<String?>(null)
    val selectedDeviceMac: StateFlow<String?> = _selectedDeviceMac.asStateFlow()

    private val _syncEnabled = MutableStateFlow(false)

    /** 多灯同步：开启后所有在线设备执行同一条指令。 */
    val syncEnabled: StateFlow<Boolean> = _syncEnabled.asStateFlow()

    fun selectDevice(macAddress: String?) {
        _selectedDeviceMac.value = macAddress
    }

    fun setSyncEnabled(enabled: Boolean) {
        _syncEnabled.value = enabled
    }

    fun setPower(device: DeviceWithState, isOn: Boolean) {
        broadcast(device) { mac -> deviceConnectionManager.setPower(mac, isOn) }
    }

    fun setBrightness(device: DeviceWithState, brightness: Int) {
        broadcast(device) { mac -> deviceConnectionManager.setBrightness(mac, brightness.coerceIn(0, 255)) }
    }

    fun setEffect(device: DeviceWithState, effectId: Int) {
        broadcast(device) { mac ->
            deviceConnectionManager.sendSegmentPatch(mac) { it.copy(effect = effectId) }
        }
    }

    fun setSpeed(device: DeviceWithState, speed: Int) {
        broadcast(device) { mac ->
            deviceConnectionManager.sendSegmentPatch(mac) { it.copy(effectSpeed = speed.coerceIn(0, 255)) }
        }
    }

    fun setIntensity(device: DeviceWithState, intensity: Int) {
        broadcast(device) { mac ->
            deviceConnectionManager.sendSegmentPatch(mac) { it.copy(effectIntensity = intensity.coerceIn(0, 255)) }
        }
    }

    /**
     * 设置主色，保留次色与第三色。
     *
     * WLED 的颜色是 `[[r,g,b], ...]`，这里始终补齐成三个槽位，避免设备端沿用上一次的旧值。
     */
    fun setPrimaryColor(device: DeviceWithState, packedRgb: Int) {
        val current = currentSegment(device)?.colors.orEmpty()
        val rebuilt = buildList {
            add(packedRgb.unpackRgb())
            for (slot in 1 until COLOR_SLOT_COUNT) {
                add(current.getOrNull(slot) ?: listOf(0, 0, 0))
            }
        }
        broadcast(device) { mac ->
            deviceConnectionManager.sendSegmentPatch(mac) { it.copy(colors = rebuilt) }
        }
    }

    /** 定时关灯：[minutes] 分钟后淡出熄灭。 */
    fun startNightlight(device: DeviceWithState, minutes: Int) {
        broadcast(device) { mac ->
            deviceConnectionManager.sendState(
                mac,
                State(
                    nightlight = Nightlight(
                        isOn = true,
                        duration = minutes,
                        fade = true,
                        mode = NIGHTLIGHT_MODE_FADE,
                        targetBrightness = NIGHTLIGHT_TARGET_BRIGHTNESS_OFF,
                    ),
                ),
            )
        }
    }

    fun cancelNightlight(device: DeviceWithState) {
        broadcast(device) { mac ->
            deviceConnectionManager.sendState(mac, State(nightlight = Nightlight(isOn = false)))
        }
    }

    private fun broadcast(device: DeviceWithState, action: suspend (String) -> Boolean) {
        viewModelScope.launch {
            targets(device).forEach { mac -> action(mac) }
        }
    }

    private fun targets(device: DeviceWithState): List<String> = if (_syncEnabled.value) {
        devices.value.filter { it.isOnline }.map { it.device.macAddress }
    } else {
        listOf(device.device.macAddress)
    }

    private fun currentSegment(device: DeviceWithState): Segment? =
        device.stateInfo?.state?.segment?.firstOrNull()
}
