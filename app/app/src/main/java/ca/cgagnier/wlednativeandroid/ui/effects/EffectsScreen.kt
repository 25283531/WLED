package ca.cgagnier.wlednativeandroid.ui.effects

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.components.DeviceSelector
import ca.cgagnier.wlednativeandroid.ui.components.EffectPreviewGif
import org.koin.androidx.compose.koinViewModel

/**
 * 灯效库：搜索 + 分类筛选 + 动态预览网格。
 *
 * 每个格子的预览是打包在 APK 里的 GIF，与设备灯珠数量无关，所以同一套素材能适配任意硬件规格。
 */
@Composable
fun EffectsScreen(
    modifier: Modifier = Modifier,
    viewModel: EffectsViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedMac by viewModel.selectedDeviceMac.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val lastAppliedId by viewModel.lastAppliedEffectId.collectAsStateWithLifecycle()

    val selectedDevice = devices.firstOrNull { it.device.macAddress == selectedMac && it.isOnline }

    LaunchedEffect(devices, selectedMac) {
        if (selectedDevice == null) {
            devices.firstOrNull { it.isOnline }?.let { viewModel.selectDevice(it.device.macAddress) }
        }
    }

    // 分类 chips 始终显示全部五项（含 2D / 字画）。
    // 早期版本按设备是否支持 2D 隐藏这两类，结果没连设备时「字画」入口直接消失，
    // 用户以为没有这个分类 —— 浏览不该受当前设备能力限制，能否下发交给固件处理。

    val visibleEffects = remember(filter, query) {
        filterEffects(EffectCatalog.all, filter, query)
    }
    val currentEffectId = selectedDevice?.stateInfo?.state?.segment?.firstOrNull()?.effect

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.effects_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                DeviceSelector(
                    devices = devices,
                    selected = selectedDevice,
                    onSelect = { viewModel.selectDevice(it.device.macAddress) },
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.effects_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateQuery("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.effects_search_clear),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(EffectFilter.entries) { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { viewModel.selectFilter(item) },
                    label = { Text(stringResource(filterLabel(item))) },
                )
            }
        }

        if (visibleEffects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.effects_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(visibleEffects, key = { it.id }) { effect ->
                EffectGridCard(
                    effect = effect,
                    isFavorite = effect.id in favoriteIds,
                    isActive = effect.id == currentEffectId || effect.id == lastAppliedId,
                    enabled = selectedDevice != null,
                    onClick = {
                        val device = selectedDevice
                        if (device == null) {
                            Toast.makeText(context, R.string.effects_no_device, Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.applyEffect(device.device.macAddress, effect.id) { success ->
                                if (!success) {
                                    Toast.makeText(context, R.string.effects_apply_failed, Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        }
                    },
                    onToggleFavorite = {
                        val device = selectedDevice
                        if (device == null) {
                            Toast.makeText(context, R.string.effects_no_device, Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.toggleFavorite(device.device.macAddress, effect)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun EffectGridCard(
    effect: EffectInfo,
    isFavorite: Boolean,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Box {
                EffectPreviewGif(
                    assetName = effect.preview,
                    contentDescription = effect.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.effects_favorite_remove else R.string.effects_favorite_add,
                        ),
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (!effect.hasPreview) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.effects_no_preview),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = effect.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

private fun filterLabel(filter: EffectFilter): Int = when (filter) {
    EffectFilter.ALL -> R.string.effects_filter_all
    EffectFilter.POPULAR -> R.string.effects_filter_popular
    EffectFilter.ONE_D -> R.string.effects_filter_one_d
    EffectFilter.TWO_D -> R.string.effects_filter_two_d
    EffectFilter.ART -> R.string.effects_filter_art
}
