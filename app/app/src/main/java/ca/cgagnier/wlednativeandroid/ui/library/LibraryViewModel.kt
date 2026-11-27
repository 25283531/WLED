package ca.cgagnier.wlednativeandroid.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.FavoriteEffect
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import ca.cgagnier.wlednativeandroid.repository.FavoriteEffectDao
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.effects.EffectCatalog
import ca.cgagnier.wlednativeandroid.ui.effects.EffectFilter
import ca.cgagnier.wlednativeandroid.ui.effects.EffectInfo
import ca.cgagnier.wlednativeandroid.ui.effects.buildEffectSegment
import ca.cgagnier.wlednativeandroid.ui.effects.filterEffects
import ca.cgagnier.wlednativeandroid.ui.effects.packRgb
import ca.cgagnier.wlednativeandroid.ui.isle.SelectedDeviceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val FAVORITES_SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * 灯效库：一行筛选胶囊 + 搜索 + 星标收藏。
 *
 * 目录来自 [EffectCatalog]（由固件源码生成），因此离线也能浏览全部灯效；
 * 收藏只是打标，星标点亮后首页的 2×2 快捷卡片会同步出现。
 * 收藏本身也存了完整参数（速度/强度/调色板/三色），见 [FavoriteEffect]。
 */
class LibraryViewModel(
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

    val favoriteIds: StateFlow<Set<Int>> = favorites
        .map { list -> list.map { it.effectId }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(FAVORITES_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptySet(),
        )

    private val _filter = MutableStateFlow(EffectFilter.ALL)
    val filter: StateFlow<EffectFilter> = _filter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val visibleEffects: StateFlow<List<EffectInfo>> = combine(_filter, _query, favoriteIds) {
            filter, query, favoriteIds ->
        filterEffects(EffectCatalog.visible, filter, query, favoriteIds)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(FAVORITES_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = EffectCatalog.visible,
    )

    fun selectFilter(filter: EffectFilter) {
        _filter.value = filter
    }

    fun updateQuery(query: String) {
        _query.value = query
    }

    /**
     * 应用灯效。
     *
     * 参数来源的优先级由 [buildEffectSegment] 统一决定：
     * 收藏快照（用户自己调过并存下的那套）> 精选配方（该灯效的推荐观感）> 设备当前值。
     *
     * 与首页快捷卡片走同一套逻辑，所以两处点出来的观感一致。
     */
    fun applyEffect(
        device: DeviceWithState,
        effectId: Int,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val favorite = favoriteEffectDao.findByEffectId(effectId)
            val macs = targets(device)
            var allSucceeded = true
            for (mac in macs) {
                val success = deviceConnectionManager.sendSegmentPatch(mac) { segment ->
                    buildEffectSegment(segment, effectId, favorite)
                }
                if (!success) allSucceeded = false
            }
            onResult(allSucceeded)
        }
    }

    /**
     * 多灯同步开启时下发到所有在线设备，否则只下发当前设备。
     *
     * 同步开关存在 [SelectedDeviceStore] 里，是设备级偏好 —— 首页和灯效库读的是同一个值。
     */
    private fun targets(device: DeviceWithState): List<String> =
        if (selectedDeviceStore.syncEnabled.value) {
            devices.value.filter { it.isOnline }.map { it.device.macAddress }
        } else {
            listOf(device.device.macAddress)
        }

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

    private fun currentSegment(device: DeviceWithState): Segment? =
        device.stateInfo?.state?.segment?.firstOrNull()
}
