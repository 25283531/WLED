package ca.cgagnier.wlednativeandroid.ui.library

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.effects.CuratedEffects
import ca.cgagnier.wlednativeandroid.ui.effects.EffectFilter
import ca.cgagnier.wlednativeandroid.ui.effects.EffectInfo
import ca.cgagnier.wlednativeandroid.ui.isle.EffectArtwork
import ca.cgagnier.wlednativeandroid.ui.isle.Glyph
import ca.cgagnier.wlednativeandroid.ui.isle.GlyphIcon
import ca.cgagnier.wlednativeandroid.ui.isle.IsleBackdrop
import ca.cgagnier.wlednativeandroid.ui.isle.IsleCapsule
import ca.cgagnier.wlednativeandroid.ui.isle.IsleColors
import ca.cgagnier.wlednativeandroid.ui.isle.StarButton
import org.koin.androidx.compose.koinViewModel

/**
 * 灯效库：一行筛选胶囊 + 搜索 + 星标收藏。
 *
 * 筛选是扁平的一维选择（全部 / 四个律动模式 / 字画 / 收藏 / 常用），
 * 点亮星标后该灯效会同步到首页的 2×2 快捷卡片。
 */
@Composable
fun LibraryScreen(modifier: Modifier = Modifier, viewModel: LibraryViewModel = koinViewModel()) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedMac by viewModel.selectedMacAddress.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val effects by viewModel.visibleEffects.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    val device = devices.firstOrNull { it.device.macAddress == selectedMac }
    val currentEffect = device?.stateInfo?.state?.segment?.firstOrNull()?.effect
    var searchOpen by remember { mutableStateOf(false) }

    IsleBackdrop(modifier = modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.isle_library_title),
                            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp),
                            color = IsleColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0x14FFFFFF), CircleShape)
                                .border(1.dp, IsleColors.Stroke, CircleShape)
                                .clickable { searchOpen = !searchOpen },
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(
                                glyph = if (searchOpen) Glyph.Check else Glyph.Search,
                                size = 19.dp,
                                tint = IsleColors.TextSecondary,
                            )
                        }
                    }

                    AnimatedVisibility(visible = searchOpen) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .background(Color(0x14FFFFFF), RoundedCornerShape(14.dp))
                                .border(1.dp, IsleColors.Stroke, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.isle_search),
                                    color = IsleColors.TextMuted,
                                    fontSize = 13.sp,
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = viewModel::updateQuery,
                                singleLine = true,
                                textStyle = TextStyle(color = IsleColors.TextPrimary, fontSize = 13.sp),
                                cursorBrush = SolidColor(IsleColors.Cyan),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier
                            .padding(top = 16.dp, bottom = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        EffectFilter.entries.forEach { item ->
                            IsleCapsule(
                                selected = item == filter,
                                onClick = { viewModel.selectFilter(item) },
                                contentPadding = 11.dp,
                            ) {
                                Text(
                                    text = stringResource(item.labelRes()),
                                    color = if (item == filter) IsleColors.TextPrimary else IsleColors.TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }

            if (effects.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(
                                if (filter == EffectFilter.FAVORITE) {
                                    R.string.isle_favorite_empty
                                } else {
                                    R.string.isle_search_empty
                                }
                            ),
                            color = IsleColors.TextMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            items(effects, key = { it.id }) { effect ->
                LibraryCard(
                    effect = effect,
                    isFavorite = effect.id in favoriteIds,
                    isActive = effect.id == currentEffect,
                    onClick = {
                        val target = device
                        if (target == null) {
                            Toast.makeText(context, R.string.effects_no_device, Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.applyEffect(target, effect.id) { ok ->
                                if (!ok) {
                                    Toast.makeText(context, R.string.effects_apply_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onToggleFavorite = {
                        val target = device
                        if (target == null) {
                            Toast.makeText(context, R.string.effects_no_device, Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.toggleFavorite(target, effect)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryCard(
    effect: EffectInfo,
    isFavorite: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    // 精选灯效有自己的产品名与推荐观感，卡片上用产品名而不是固件技术名 ——
    // 「流光溢彩」比「Aurora / 极光」好认得多，而点下去拿到的也正是那套推荐观感。
    val curated = CuratedEffects.find(effect.id)
    val shape = RoundedCornerShape(18.dp)
    val accent = curated?.gradient?.map { Color(it) } ?: IsleColors.Accent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.32f)
            .background(Color(0xFF101218), shape)
            .border(
                width = if (isActive) 1.6.dp else 1.dp,
                brush = if (isActive) Brush.linearGradient(accent)
                else Brush.linearGradient(listOf(IsleColors.Stroke, IsleColors.Stroke)),
                shape = shape,
            )
            .clickable(onClick = onClick),
    ) {
        // 精选灯效的渐变底衬：GIF 解码完成前不会闪出灰底，
        // 同时让每张精选卡片有独立的色彩标识。
        if (curated != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            curated.gradient.map { Color(it).copy(alpha = 0.38f) },
                        ),
                    ),
            )
        }
        EffectArtwork(effect = effect, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x26000000), Color(0xE6040508)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 9.dp),
        ) {
            Text(
                text = curated?.title ?: effect.nameZh,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = curated?.tagline ?: effect.name.takeIf { effect.hasChineseName }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color(0xFFB4BCC9),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        StarButton(
            selected = isFavorite,
            onClick = onToggleFavorite,
            size = 24.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(7.dp),
        )
    }
}

private fun EffectFilter.labelRes(): Int = when (this) {
    EffectFilter.ALL -> R.string.isle_filter_all
    EffectFilter.POPULAR -> R.string.isle_filter_popular
    EffectFilter.FAVORITE -> R.string.isle_filter_favorite
    EffectFilter.ART -> R.string.isle_filter_art
    EffectFilter.ONE_D -> R.string.isle_filter_1d
    EffectFilter.TWO_D -> R.string.isle_filter_2d
    EffectFilter.AURORA -> R.string.isle_mood_aurora
    EffectFilter.MUSIC -> R.string.isle_mood_music
    EffectFilter.TEXT -> R.string.isle_mood_text
}
