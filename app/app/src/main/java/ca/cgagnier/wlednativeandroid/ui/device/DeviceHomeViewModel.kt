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
import ca.cgagnier.wlednativeandroid.ui.effects.FourColorPalette
import ca.cgagnier.wlednativeandroid.ui.effects.FrequencyPixels
import ca.cgagnier.wlednativeandroid.ui.effects.PaletteCatalog
import ca.cgagnier.wlednativeandroid.ui.effects.buildEffectSegment
import ca.cgagnier.wlednativeandroid.ui.effects.packRgb
import ca.cgagnier.wlednativeandroid.ui.effects.unpackRgb
import ca.cgagnier.wlednativeandroid.ui.isle.SelectedDeviceStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val FAVORITES_SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val NIGHTLIGHT_MODE_FADE = 1
private const val NIGHTLIGHT_TARGET_BRIGHTNESS_OFF = 0

/**
 * 睡眠夜灯配方：纯色 + 暖黄 + 低亮度。
 *
 * 「睡眠夜灯」不再是灯效分类，只是一个一键参数组合，所以效果与调色板按名字反查，
 * 不写死 id。
 */
private const val SLEEP_LIGHT_EFFECT_NAME = "Solid"
private const val SLEEP_LIGHT_PALETTE_NAME = "Default"
private const val SLEEP_LIGHT_COLOR = 0xFFB46B
private const val SLEEP_LIGHT_BRIGHTNESS = 51

/** 定时关灯的一次操作结果，用于给用户明确反馈。 */
sealed interface NightlightEvent {
    data class Started(val minutes: Int) : NightlightEvent

    data object Cancelled : NightlightEvent

    /** 指令没写进连接（设备离线或 WebSocket 未连上）。 */
    data object Failed : NightlightEvent

    /**
     * 指令已发出，但灯是关闭状态。
     *
     * 固件在 `bri == tbri` 时会立刻清掉夜灯，所以灯关着时设定时不会生效 —— 必须如实告知用户。
     */
    data object LightOff : NightlightEvent
}

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
 * 首页（设备）：电源、亮度、三个律动模式 + 睡眠夜灯、快捷卡片、多灯同步、定时关灯。
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

    /**
     * 多灯同步开关。
     *
     * 存在 [SelectedDeviceStore] 里而不是本 ViewModel 的私有状态 —— 它是设备级偏好，
     * 「灯效库」点灯效时也要跟随，否则开了同步却只有当前这台灯在变。
     */
    val syncEnabled: StateFlow<Boolean> = selectedDeviceStore.syncEnabled

    private val _nightlightEvents = MutableSharedFlow<NightlightEvent>(extraBufferCapacity = 4)

    /** 定时关灯的操作结果，界面用它弹 Toast，避免「点完什么都没发生」。 */
    val nightlightEvents: SharedFlow<NightlightEvent> = _nightlightEvents.asSharedFlow()

    fun selectDevice(macAddress: String?) = selectedDeviceStore.select(macAddress)

    fun selectMood(mood: EffectMood) {
        _mood.value = mood
    }

    fun setSyncEnabled(enabled: Boolean) {
        selectedDeviceStore.setSyncEnabled(enabled)
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
     * 调色板（`seg.pal`）。
     *
     * id 取自 [ca.cgagnier.wlednativeandroid.ui.effects.PaletteCatalog]，
     * 不在业务代码里写死 —— 固件插一个调色板，后面所有 id 都会前移。
     */
    fun setPalette(device: DeviceWithState, palette: Int) = broadcast(device) { mac ->
        deviceConnectionManager.sendSegmentPatch(mac) {
            it.copy(palette = palette.coerceIn(0, 255))
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
     * 频谱像素：设置第 [band] 个频段的颜色。
     *
     * 前 3 个频段写入 `col[0..2]`；第 4 个频段写入 `c1/c2/c3`（RGB777），
     * 与固件 `mode_freqpixels()` 的解包约定保持一致。
     */
    fun setFrequencyColor(device: DeviceWithState, band: Int, packedRgb: Int) {
        if (band !in 0 until FrequencyPixels.BAND_COUNT) return
        broadcast(device) { mac ->
            deviceConnectionManager.sendSegmentPatch(mac) { segment ->
                if (band >= COLOR_SLOT_COUNT) {
                    val (c1, c2, c3) = FrequencyPixels.packBand4(packedRgb)
                    segment.copy(custom1 = c1, custom2 = c2, custom3 = c3)
                } else {
                    val colors = List(COLOR_SLOT_COUNT) { slot ->
                        if (slot == band) {
                            packedRgb.unpackRgb()
                        } else {
                            segment.colors?.getOrNull(slot) ?: listOf(0, 0, 0)
                        }
                    }
                    segment.copy(colors = colors)
                }
            }
        }
    }

    /** 频谱像素：应用一套四色模板。 */
    fun applyFrequencyPalette(device: DeviceWithState, palette: FourColorPalette) {
        if (palette.colors.size != FrequencyPixels.BAND_COUNT) return
        val colors = palette.colors.take(COLOR_SLOT_COUNT).map { it.unpackRgb() }
        val (c1, c2, c3) = FrequencyPixels.packBand4(palette.colors[3])
        broadcast(device) { mac ->
            deviceConnectionManager.sendSegmentPatch(mac) {
                it.copy(colors = colors, custom1 = c1, custom2 = c2, custom3 = c3)
            }
        }
    }

    /**
     * 应用灯效。
     *
     * 参数来源的优先级由 [buildEffectSegment] 统一决定：收藏快照 > 精选配方 > 设备当前值。
     * [favorite] 非空时是这张卡片的收藏记录，会**连同收藏时存下的速度、强度、
     * 调色板与三色一起恢复** —— 否则收藏就只是个书签，调不出收藏当时的观感。
     */
    fun applyEffect(
        device: DeviceWithState,
        effectId: Int,
        favorite: FavoriteEffect? = null,
    ) = broadcast(device) { mac ->
        deviceConnectionManager.sendSegmentPatch(mac) { segment ->
            buildEffectSegment(segment, effectId, favorite)
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

    /**
     * 睡眠夜灯：一键切到低亮度温馨暖光。
     *
     * 只下发一组参数（纯色 + 暖黄 + 低亮度），之后亮度滑杆与色板仍然可调，
     * 不需要为它单独做一个灯效分类。
     */
    fun startSleepLight(device: DeviceWithState) {
        val solid = EffectCatalog.byName(SLEEP_LIGHT_EFFECT_NAME)
        val palette = PaletteCatalog.byName(SLEEP_LIGHT_PALETTE_NAME)
        val colors = listOf(
            SLEEP_LIGHT_COLOR.unpackRgb(),
        ) + List(COLOR_SLOT_COUNT - 1) { listOf(0, 0, 0) }
        broadcast(device) { mac ->
            deviceConnectionManager.sendSegmentPatch(mac) { segment ->
                segment.copy(
                    effect = solid?.id ?: segment.effect,
                    palette = palette?.id ?: segment.palette,
                    colors = colors,
                )
            }
            deviceConnectionManager.setBrightness(mac, SLEEP_LIGHT_BRIGHTNESS)
            deviceConnectionManager.setPower(mac, true)
        }
    }

    /** 定时关灯：[minutes] 分钟后淡出熄灭。 */
    fun startNightlight(device: DeviceWithState, minutes: Int) {
        // 灯关着时固件会立刻清掉夜灯（bri 与目标亮度相等），此时必须先告知用户，
        // 否则又是一次「点了没反应」。
        val lightWasOn = device.stateInfo?.state?.isOn == true
        viewModelScope.launch {
            val delivered = targets(device).map { mac -> sendNightlight(mac, minutes) }
            _nightlightEvents.emit(
                when {
                    delivered.isEmpty() || delivered.any { !it } -> NightlightEvent.Failed
                    !lightWasOn -> NightlightEvent.LightOff
                    else -> NightlightEvent.Started(minutes)
                },
            )
        }
    }

    fun cancelNightlight(device: DeviceWithState) {
        viewModelScope.launch {
            val delivered = targets(device).map { mac ->
                deviceConnectionManager.sendState(mac, State(nightlight = Nightlight(isOn = false)))
            }
            _nightlightEvents.emit(
                if (delivered.isEmpty() || delivered.any { !it }) NightlightEvent.Failed
                else NightlightEvent.Cancelled,
            )
        }
    }

    private suspend fun sendNightlight(mac: String, minutes: Int): Boolean =
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

    private fun broadcast(device: DeviceWithState, action: suspend (String) -> Boolean) {
        viewModelScope.launch {
            targets(device).forEach { mac -> action(mac) }
        }
    }

    private fun targets(device: DeviceWithState): List<String> = if (selectedDeviceStore.syncEnabled.value) {
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
