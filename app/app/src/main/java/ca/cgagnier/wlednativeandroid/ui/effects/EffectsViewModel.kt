package ca.cgagnier.wlednativeandroid.ui.effects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.FavoriteEffect
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import ca.cgagnier.wlednativeandroid.repository.FavoriteEffectDao
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val FAVORITES_SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * 灯效库：列表 + 应用 + 收藏。
 *
 * 灯效目录用 [EffectCatalog]（由固件源码生成），因此离线也能列出全部效果；
 * 下发只写 `seg.fx`，走 WebSocket，不需要 HTTP。
 */
class EffectsViewModel(
    private val deviceConnectionManager: DeviceConnectionManager,
    private val favoriteEffectDao: FavoriteEffectDao,
) : ViewModel() {

    val devices: StateFlow<List<DeviceWithState>> = deviceConnectionManager.allDevicesWithState

    private val _selectedDeviceMac = MutableStateFlow<String?>(null)
    val selectedDeviceMac: StateFlow<String?> = _selectedDeviceMac.asStateFlow()

    private val _filter = MutableStateFlow(EffectFilter.ALL)
    val filter: StateFlow<EffectFilter> = _filter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _lastAppliedEffectId = MutableStateFlow<Int?>(null)

    /** 刚刚应用成功的灯效，用于界面高亮；失败时为 null。 */
    val lastAppliedEffectId: StateFlow<Int?> = _lastAppliedEffectId.asStateFlow()

    val favoriteIds: StateFlow<Set<Int>> = favoriteEffectDao.observeFavoriteEffectIds()
        .map { it.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(FAVORITES_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptySet(),
        )

    fun selectDevice(macAddress: String?) {
        if (_selectedDeviceMac.value != macAddress) {
            _selectedDeviceMac.value = macAddress
            _lastAppliedEffectId.value = null
        }
    }

    fun selectFilter(filter: EffectFilter) {
        _filter.value = filter
    }

    fun updateQuery(query: String) {
        _query.value = query
    }

    /** 下发灯效。成功返回 true，用于界面给出失败提示。 */
    fun applyEffect(macAddress: String, effectId: Int, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = deviceConnectionManager.sendSegmentPatch(macAddress) { segment ->
                segment.copy(effect = effectId)
            }
            _lastAppliedEffectId.value = if (success) effectId else null
            onResult(success)
        }
    }

    /**
     * 收藏 / 取消收藏。收藏时把当前段落的生效参数一并存下，
     * 这样以后调出来能复现同一套观感，而不只是同一个效果 id。
     */
    fun toggleFavorite(macAddress: String, effect: EffectInfo) {
        viewModelScope.launch {
            val existing = favoriteEffectDao.findByEffectId(effect.id)
            if (existing != null) {
                favoriteEffectDao.delete(existing)
                return@launch
            }
            val segment = currentSegment(macAddress)
            favoriteEffectDao.insert(
                FavoriteEffect(
                    effectId = effect.id,
                    effectName = effect.name,
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

    private fun currentSegment(macAddress: String): Segment? = devices.value
        .firstOrNull { it.device.macAddress == macAddress }
        ?.stateInfo
        ?.state
        ?.segment
        ?.firstOrNull()
}
