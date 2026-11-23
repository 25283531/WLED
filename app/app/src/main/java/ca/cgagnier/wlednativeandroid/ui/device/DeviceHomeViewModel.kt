package ca.cgagnier.wlednativeandroid.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.FavoriteEffect
import ca.cgagnier.wlednativeandroid.model.wledapi.Nightlight
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.repository.FavoriteEffectDao
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.effects.EffectCatalog
import ca.cgagnier.wlednativeandroid.ui.effects.EffectInfo
import ca.cgagnier.wlednativeandroid.ui.effects.EffectMood
import ca.cgagnier.wlednativeandroid.ui.effects.packRgb
import ca.cgagnier.wlednativeandroid.ui.isle.SelectedDeviceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val FAVORITES_SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val NIGHTLIGHT_MODE_FADE = 1
private const val NIGHTLIGHT_TARGET_BRIGHTNESS_OFF = 0

/**
 * 首页（设备）：电源、亮度、四个律动模式、快捷卡片、多灯同步、定时关灯。
 *
 * 所有下发都走 WebSocket 的 [`seg` 补丁]，不会覆盖其它段落。
 */
class DeviceHomeViewModel(
    private val deviceConnectionManager: DeviceConnectionManager,
    private val favoriteEffectDao: FavoriteEffectDao,
    private val selectedDeviceStore: SelectedDeviceStore,
) : ViewModel() {

    val devices: StateFlow<List<DeviceWithState>> = deviceConnectionManager.allDevicesWithState

    val selectedMacAddress: StateFlow<String?> = selectedDeviceStore.macAddress

    val favorites: StateFlow<List<FavoriteEffect>> = favoriteEffectDao.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(FAVORITES_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    private val _mood = MutableStateFlow(EffectMood.AURORA)
    val mood: StateFlow<EffectMood> = _mood.asStateFlow()

    private val _syncEnabled = MutableStateFlow(false)
    val syncEnabled: StateFlow<Boolean> = _syncEnabled.asStateFlow()

    fun selectDevice(macAddress: String?) = selectedDeviceStore.select(macAddress)

    fun selectMood(mood: EffectMood) {
        _mood.value = mood
    }

    fun setSyncEnabled(enabled: Boolean) {
        _syncEnabled.value = enabled
    }

    fun setPower(device: DeviceWithState, isOn: Boolean) = broadcast(device) { mac ->
        deviceConnectionManager.setPower(mac, isOn)
    }

    fun setBrightness(device: DeviceWithState, brightness: Int) = broadcast(device) { mac ->
        deviceConnectionManager.setBrightness(mac, brightness.coerceIn(0, 255))
    }

    fun applyEffect(device: DeviceWithState, effectId: Int) = broadcast(device) { mac ->
        deviceConnectionManager.sendSegmentPatch(mac) { it.copy(effect = effectId) }
    }

    /**
     * 收藏 / 取消收藏。收藏时连当前段落的速度、强度、调色板、三色一起存，
     * 以后调出来才能复现同一套观感，而不只是同一个效果 id。
     */
    fun toggleFavorite(device: DeviceWithState, effect: EffectInfo) {
        viewModelScope.launch {
            val existing = favoriteEffectDao.findByEffectId(effect.id)
            if (existing != null) {
                favoriteEffectDao.delete(existing)
                return@launch
            }
            val segment = currentSegment(device)
            favoriteEffectDao.insert(
                FavoriteEffect(
                    effectId = effect.id,
                    effectName = effect.nameZh,
                    speed = segment?.effectSpeed,
                    intensity = segment?.effectIntensity,
                    palette = segment?.palette,
                    color1 = segment?.colors?.getOrNull(0)?.packRgb(),
                    color2 = segment?.colors?.getOrNull(1)?.packRgb(),
                    color3 = segment?.colors?.getOrNull(2)?.packRgb(),
                ),
            )
        }
    }

    /** 定时关灯：[minutes] 分钟后淡出熄灭。 */
    fun startNightlight(device: DeviceWithState, minutes: Int) = broadcast(device) { mac ->
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

    fun cancelNightlight(device: DeviceWithState) = broadcast(device) { mac ->
        deviceConnectionManager.sendState(mac, State(nightlight = Nightlight(isOn = false)))
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

    /**
     * 首页 2×2 快捷卡片：先放该律动模式下已收藏的，不够四张再用该模式下最常用的补齐。
     * 这样首页永远只有 4 张，且优先呈现用户自己的选择。
     */
    fun quickCards(favorites: List<FavoriteEffect>, mood: EffectMood): List<EffectInfo> {
        val inMood = favorites.mapNotNull { EffectCatalog.find(it.effectId) }
            .filter { it.mood == mood }
            .distinctBy { it.id }
        if (inMood.size >= QUICK_CARD_COUNT) return inMood.take(QUICK_CARD_COUNT)
        val fillers = EffectCatalog.byMood(mood)
            .filter { it.isPopular && it.id !in inMood.map { effect -> effect.id } }
            .ifEmpty { EffectCatalog.byMood(mood) }
        return (inMood + fillers).distinctBy { it.id }.take(QUICK_CARD_COUNT)
    }

    private companion object {
        const val QUICK_CARD_COUNT = 4
    }
}
