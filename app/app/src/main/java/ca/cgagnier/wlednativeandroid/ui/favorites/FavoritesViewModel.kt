package ca.cgagnier.wlednativeandroid.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.FavoriteEffect
import ca.cgagnier.wlednativeandroid.repository.FavoriteEffectDao
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.effects.unpackRgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val FAVORITES_SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * 收藏页：保存的灯效配方一键调出。
 *
 * 收藏存在本地数据库，不区分设备——同一套配方可以套到任何一台灯上。
 */
class FavoritesViewModel(
    private val deviceConnectionManager: DeviceConnectionManager,
    private val favoriteEffectDao: FavoriteEffectDao,
) : ViewModel() {

    val devices: StateFlow<List<DeviceWithState>> = deviceConnectionManager.allDevicesWithState

    private val _selectedDeviceMac = MutableStateFlow<String?>(null)
    val selectedDeviceMac: StateFlow<String?> = _selectedDeviceMac.asStateFlow()

    val favorites: StateFlow<List<FavoriteEffect>> = favoriteEffectDao.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(FAVORITES_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    fun selectDevice(macAddress: String?) {
        _selectedDeviceMac.value = macAddress
    }

    fun apply(macAddress: String, favorite: FavoriteEffect, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val colors = favorite.packedColors.map { it.unpackRgb() }.ifEmpty { null }
            val success = deviceConnectionManager.sendSegmentPatch(macAddress) { segment ->
                segment.copy(
                    effect = favorite.effectId,
                    effectSpeed = favorite.speed ?: segment.effectSpeed,
                    effectIntensity = favorite.intensity ?: segment.effectIntensity,
                    palette = favorite.palette ?: segment.palette,
                    colors = colors ?: segment.colors,
                )
            }
            onResult(success)
        }
    }

    fun remove(favorite: FavoriteEffect) {
        viewModelScope.launch { favoriteEffectDao.delete(favorite) }
    }
}
