package ca.cgagnier.wlednativeandroid.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.FavoriteEffect
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val FAVORITES_SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * 灯效库：四个律动模式 Tab + 搜索 + 星标收藏。
 *
 * 目录来自 [EffectCatalog]（由固件源码生成），因此离线也能浏览全部 187 个效果；
 * 收藏只是打标，星标点亮后首页的 2×2 快捷卡片会同步出现。
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

    private val _mood = MutableStateFlow(EffectMood.AURORA)
    val mood: StateFlow<EffectMood> = _mood.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val visibleEffects: StateFlow<List<EffectInfo>> = combine(_mood, _query) { mood, query ->
        val base = EffectCatalog.byMood(mood)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            base
        } else {
            base.filter {
                it.name.contains(trimmed, ignoreCase = true) || it.nameZh.contains(trimmed)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(FAVORITES_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = EffectCatalog.byMood(EffectMood.AURORA),
    )

    fun selectMood(mood: EffectMood) {
        _mood.value = mood
    }

    fun updateQuery(query: String) {
        _query.value = query
    }

    fun applyEffect(device: DeviceWithState, effectId: Int, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = deviceConnectionManager.sendSegmentPatch(device.device.macAddress) { segment ->
                segment.copy(effect = effectId)
            }
            onResult(success)
        }
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
