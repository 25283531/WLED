package ca.cgagnier.wlednativeandroid.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.FavoriteEffect
import ca.cgagnier.wlednativeandroid.model.restoreFrom
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
import ca.cgagnier.wlednativeandroid.ui.effects.unpackRgb
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

/** WLED 颜色槽位数量（主色 / 次色 / 第三色）。 */
private const val COLOR_SLOT_COUNT = 3

/**
 * 首页快捷卡片的一项。
 *
 * [favorite] 只在用户收藏过该灯效时非空 —— 点下去时用它把收藏时存下的
 * 速度、强度、调色板与三色一并恢复，而不只是切一个效果 id。
 */
data class QuickCardItem(
    val effect: EffectInfo,
    val favorite: FavoriteEffect?,
)

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

    /** 灯效速度（`seg.sx`）。 */
    fun setSpeed(device: DeviceWithState, speed: Int) = broadcast(device) { mac ->
        deviceConnectionManager.sendSegmentPatch(mac) {
            it.copy(effectSpeed = speed.coerceIn(0, 255))
        }
    }

    /** 灯效强度（`seg.ix`）。 */
    fun setIntensity(device: DeviceWithState, intensity: Int) = broadcast(device) { mac ->
        deviceConnectionManager.sendSegmentPatch(mac) {
            it.copy(effectIntensity = intensity.coerceIn(0, 255))
        }
    }

    /**
     * 设置主色，保留次色与第三色。
     *
     * WLED 的颜色是 `[[r,g,b], ...]`，这里始终补齐成三个槽位，
     * 避免设备端沿用上一次的旧值。
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

    /**
     * 应用灯效。
     *
     * [favorite] 非空时是这张卡片的收藏记录，会**连同收藏时存下的速度、强度、
     * 调色板与三色一起恢复** —— 否则收藏就只是个书签，调不出收藏当时的观感。
     */
    fun applyEffect(
        device: DeviceWithState,
        effectId: Int,
        favorite: FavoriteEffect? = null,
    ) = broadcast(device) { mac ->
        deviceConnectionManager.sendSegmentPatch(mac) { segment ->
            segment.copy(effect = effectId).restoreFrom(favorite)
        }
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
     *
     * 收藏项带上收藏记录本身，点下去才能连参数一起恢复。
     */
    fun quickCards(favorites: List<FavoriteEffect>, mood: EffectMood): List<QuickCardItem> {
        val favoriteById = favorites.associateBy { it.effectId }
        val inMood = favorites.mapNotNull { fav ->
            EffectCatalog.find(fav.effectId)?.let { QuickCardItem(it, fav) }
        }.filter { it.effect.mood == mood }
        if (inMood.size >= QUICK_CARD_COUNT) return inMood.take(QUICK_CARD_COUNT)
        val takenIds = inMood.map { it.effect.id }.toSet()
        val fillers = EffectCatalog.byMood(mood)
            .filter { it.isPopular && it.id !in takenIds }
            .ifEmpty { EffectCatalog.byMood(mood).filter { it.id !in takenIds } }
        return (inMood + fillers.map { QuickCardItem(it, favoriteById[it.id]) })
            .distinctBy { it.effect.id }
            .take(QUICK_CARD_COUNT)
    }

    private companion object {
        const val QUICK_CARD_COUNT = 4
    }
}
