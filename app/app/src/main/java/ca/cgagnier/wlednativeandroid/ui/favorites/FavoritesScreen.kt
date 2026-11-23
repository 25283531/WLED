package ca.cgagnier.wlednativeandroid.ui.favorites

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.FavoriteEffect
import ca.cgagnier.wlednativeandroid.ui.components.DeviceSelector
import ca.cgagnier.wlednativeandroid.ui.components.EffectPreviewGif
import ca.cgagnier.wlednativeandroid.ui.effects.EffectCatalog
import org.koin.androidx.compose.koinViewModel

/**
 * 收藏页：横向卡片列表，点一下就把整条配方（灯效 + 速度 + 强度 + 调色板 + 颜色）下发到灯上。
 */
@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedMac by viewModel.selectedDeviceMac.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    val selectedDevice = devices.firstOrNull { it.device.macAddress == selectedMac && it.isOnline }

    LaunchedEffect(devices, selectedMac) {
        if (selectedDevice == null) {
            devices.firstOrNull { it.isOnline }?.let { viewModel.selectDevice(it.device.macAddress) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.favorites_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            DeviceSelector(
                devices = devices,
                selected = selectedDevice,
                onSelect = { viewModel.selectDevice(it.device.macAddress) },
            )
        }

        if (favorites.isEmpty()) {
            EmptyFavorites()
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(favorites, key = { it.id }) { favorite ->
                FavoriteRow(
                    favorite = favorite,
                    onClick = {
                        val device = selectedDevice
                        if (device == null) {
                            Toast.makeText(context, R.string.effects_no_device, Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.apply(device.device.macAddress, favorite) { success ->
                                if (!success) {
                                    Toast.makeText(context, R.string.effects_apply_failed, Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        }
                    },
                    onRemove = { viewModel.remove(favorite) },
                )
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    favorite: FavoriteEffect,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val preview = EffectCatalog.find(favorite.effectId)?.preview
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EffectPreviewGif(
                assetName = preview,
                contentDescription = favorite.effectName,
                modifier = Modifier.size(width = 128.dp, height = 72.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = favorite.effectName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = describe(favorite),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.favorites_remove),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun describe(favorite: FavoriteEffect): String {
    val parts = buildList {
        favorite.speed?.let { add(stringResource(R.string.favorites_speed, it)) }
        favorite.intensity?.let { add(stringResource(R.string.favorites_intensity, it)) }
        favorite.color1?.let { add("#%06X".format(it and 0xFFFFFF)) }
    }
    return if (parts.isEmpty()) {
        stringResource(R.string.favorites_default_params)
    } else {
        parts.joinToString(" · ")
    }
}

@Composable
private fun EmptyFavorites() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.favorites_empty),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.favorites_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
