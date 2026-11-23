package ca.cgagnier.wlednativeandroid.ui.isle

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.effects.EffectInfo
import ca.cgagnier.wlednativeandroid.ui.components.EffectPreviewGif
import kotlin.math.cos
import kotlin.math.sin

/** 玻璃卡片：半透明底 + 1dp 描边 + 24dp 圆角。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(IsleColors.CardBrush)
            .border(1.dp, IsleColors.Stroke, shape)
            .padding(padding),
        content = content,
    )
}

/** 小组标题：13sp、字距略松，和正文拉开层次。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                color = IsleColors.TextSecondary,
            ),
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/** 胶囊按钮。选中时描边渐变、内部透出微光。 */
@Composable
fun IsleCapsule(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 12.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val radius = 999.dp
    val shape = RoundedCornerShape(radius)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    Brush.linearGradient(listOf(Color(0x2E5FE3E4), Color(0x2E8C7BFF)))
                } else {
                    Brush.linearGradient(listOf(Color(0x0FFFFFFF), Color(0x0AFFFFFF)))
                },
            )
            .drawBehind {
                if (selected) {
                    drawRoundRect(
                        brush = Brush.linearGradient(IsleColors.Accent),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                        style = Stroke(width = 1.4.dp.toPx()),
                    )
                }
            }
            .border(if (selected) 0.dp else 1.dp, IsleColors.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = contentPadding, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** 圆形柔光按钮（电源、工具）。 */
@Composable
fun GlowCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    active: Boolean = false,
    glow: List<Color> = IsleColors.Accent,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            (if (active) glow.first() else Color.White).copy(alpha = if (active) 0.35f else 0.10f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = this.size.minDimension * 0.95f,
                    ),
                    radius = this.size.minDimension * 0.95f,
                )
            }
            .clip(CircleShape)
            .background(if (active) Color(0xFFF3F6FF) else Color(0x14FFFFFF))
            .border(1.dp, if (active) Color.Transparent else IsleColors.StrokeStrong, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 收藏星标：垫一层半透明圆底，压在亮色封面上也看得清。 */
@Composable
fun StarButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xB3080910))
            .border(1.dp, Color(0x1FFFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.Star,
            contentDescription = stringResource(
                if (selected) R.string.isle_favorite_remove else R.string.isle_favorite_add,
            ),
            tint = if (selected) IsleColors.Cyan else Color(0xFFB9C0CC),
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/**
 * 亮度/参数滑条。
 *
 * 拖动过程中只回调 [onValueChange]（改本地值），松手才 [onValueChangeFinished]，
 * 避免一路拖一路下发把设备打满。
 */
@Composable
fun IsleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    accent: List<Color> = IsleColors.Accent,
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                    onValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onValueChangeFinished() },
                    onDragCancel = { onValueChangeFinished() },
                ) { change, _ ->
                    change.consume()
                    onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 4.dp.toPx()
            val cy = size.height / 2f
            val radius = trackHeight / 2f
            drawRoundRect(
                color = Color(0x1FFFFFFF),
                topLeft = Offset(0f, cy - radius),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(radius, radius),
            )
            val filled = size.width * value.coerceIn(0f, 1f)
            if (filled > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(accent),
                    topLeft = Offset(0f, cy - radius),
                    size = Size(filled, trackHeight),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
            val thumbRadius = 8.dp.toPx()
            val thumbCenter = Offset(filled.coerceIn(thumbRadius, size.width - thumbRadius), cy)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x665FE3E4), Color.Transparent),
                    center = thumbCenter,
                    radius = thumbRadius * 3.1f,
                ),
                radius = thumbRadius * 3.1f,
                center = thumbCenter,
            )
            drawCircle(color = Color(0xFFF7FAFF), radius = thumbRadius, center = thumbCenter)
            drawCircle(
                color = Color(0x33FFFFFF),
                radius = thumbRadius + 2.dp.toPx(),
                center = thumbCenter,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/**
 * 灯效封面。
 *
 * 直接播放打包在 assets 里的 GIF 动图（`assets/effect_previews/<effectId>.gif`），
 * 文件按**效果 id** 命名，与 `seg.fx` 一一对应，不存在"名字对、图不对"的可能。
 */
@Composable
fun EffectArtwork(effect: EffectInfo, modifier: Modifier = Modifier) {
    val preview = effect.preview
    if (preview != null) {
        EffectPreviewGif(
            assetName = preview,
            contentDescription = effect.nameZh,
            modifier = modifier,
        )
    } else {
        // 理论上不会走到：目录里每个效果都有预览图。留个兜底避免空白卡片。
        Box(modifier = modifier.background(Brush.linearGradient(listOf(IsleColors.SurfaceHigh, IsleColors.Surface))))
    }
}

/**
 * 灯管示意图。
 *
 * 用 Canvas 现画而不是放一张渲染图：灯管里的颜色要跟着当前灯效变，
 * 静态图做不到这件事，而且换产品形态（不同长径比）也不用重新出图。
 */
@Composable
fun DeviceTube(
    modifier: Modifier = Modifier,
    colors: List<Color>,
    isOn: Boolean,
    brightness: Float,
) {
    val palette = if (colors.isEmpty()) listOf(IsleColors.Cyan, IsleColors.Violet, IsleColors.Pink) else colors
    val transition = rememberInfiniteTransition(label = "tube")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val intensity = if (isOn) 0.25f + 0.75f * brightness.coerceIn(0f, 1f) else 0f

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val tubeWidth = w * 0.34f
        val left = (w - tubeWidth) / 2f
        val top = h * 0.06f
        val tubeHeight = h * 0.60f
        val baseTop = top + tubeHeight - 2f
        val baseWidth = tubeWidth * 1.16f
        val baseLeft = (w - baseWidth) / 2f
        val baseHeight = h * 0.16f
        val corner = tubeWidth * 0.16f

        // 环境光：灯管在桌面上的溢光
        if (intensity > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette[0].copy(alpha = 0.34f * intensity),
                        palette.getOrNull(1)?.copy(alpha = 0.16f * intensity) ?: Color.Transparent,
                        Color.Transparent,
                    ),
                    center = Offset(w / 2f, top + tubeHeight * 0.45f),
                    radius = w * 0.62f,
                ),
                radius = w * 0.62f,
                center = Offset(w / 2f, top + tubeHeight * 0.45f),
            )
        }

        // 地面接触阴影
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x99000000), Color.Transparent),
                center = Offset(w / 2f, baseTop + baseHeight * 0.92f),
                radius = baseWidth * 0.85f,
            ),
            topLeft = Offset(w / 2f - baseWidth * 0.72f, baseTop + baseHeight * 0.62f),
            size = Size(baseWidth * 1.44f, baseHeight * 0.62f),
        )

        // 玻璃管：深色亚克力，内部透出光
        val tubePath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = top,
                    right = left + tubeWidth,
                    bottom = top + tubeHeight,
                    radiusX = corner,
                    radiusY = corner,
                ),
            )
        }
        drawPath(tubePath, Color(0xFF0A0B10))
        clipPath(tubePath) {
            if (intensity > 0f) {
                // 三团流动的光斑，用相位错开，看起来像混光腔里的流体
                for (index in 0 until 3) {
                    val hue = palette[index % palette.size]
                    val t = phase + index * 2.1f
                    val cx = left + tubeWidth * (0.5f + 0.26f * sin(t * 0.7f + index))
                    val cy = top + tubeHeight * (0.5f + 0.30f * cos(t * 0.9f + index * 1.7f))
                    val radius = tubeWidth * (0.62f + 0.12f * sin(t))
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(hue.copy(alpha = 0.95f), hue.copy(alpha = 0.28f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = radius,
                        ),
                        radius = radius,
                        center = Offset(cx, cy),
                    )
                }
                // 纵向亮带：亚克力管壁的反射
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x1AFFFFFF),
                            Color(0x33FFFFFF),
                            Color(0x0DFFFFFF),
                            Color.Transparent,
                        ),
                        startX = left,
                        endX = left + tubeWidth,
                    ),
                    topLeft = Offset(left, top),
                    size = Size(tubeWidth, tubeHeight),
                )
            }
        }
        drawPath(tubePath, Color(0x33FFFFFF), style = Stroke(width = 1.4f.dp.toPx()))
        // 管顶的掠射高光：渐变到全透明，避免出现一条生硬的横接缝
        val sheenPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = top,
                    right = left + tubeWidth,
                    bottom = top + tubeHeight * 0.62f,
                    radiusX = corner,
                    radiusY = corner,
                ),
            )
        }
        clipPath(sheenPath) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x2BFFFFFF), Color(0x08FFFFFF), Color(0x00FFFFFF)),
                    startY = top,
                    endY = top + tubeHeight * 0.62f,
                ),
                topLeft = Offset(left, top),
                size = Size(tubeWidth, tubeHeight * 0.62f),
            )
        }

        // 金属底座
        val baseShape = Path().apply {
            addRoundRect(
                RoundRect(
                    left = baseLeft,
                    top = baseTop,
                    right = baseLeft + baseWidth,
                    bottom = baseTop + baseHeight,
                    radiusX = baseWidth * 0.14f,
                    radiusY = baseWidth * 0.14f,
                ),
            )
        }
        drawPath(
            baseShape,
            Brush.verticalGradient(
                colors = listOf(Color(0xFF8A9099), Color(0xFF4A4F58), Color(0xFF23262C)),
                startY = baseTop,
                endY = baseTop + baseHeight,
            ),
        )
        drawPath(baseShape, Color(0x22FFFFFF), style = Stroke(width = 1.dp.toPx()))
        // 底座顶面高光
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(Color(0x00FFFFFF), Color(0x66FFFFFF), Color(0x00FFFFFF)),
                startX = baseLeft,
                endX = baseLeft + baseWidth,
            ),
            topLeft = Offset(baseLeft + baseWidth * 0.12f, baseTop + baseHeight * 0.06f),
            size = Size(baseWidth * 0.76f, 1.6f.dp.toPx()),
            cornerRadius = CornerRadius(1f, 1f),
        )
        // 指示灯
        drawCircle(
            color = if (isOn) Color(0xFF9FF5D2) else Color(0x33FFFFFF),
            radius = 1.6f.dp.toPx(),
            center = Offset(w / 2f, baseTop + baseHeight * 0.74f),
        )
    }
}

/** 两行文字卡片标题：中文名 + 英文名。 */
@Composable
fun EffectTitle(effect: EffectInfo, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = effect.nameZh,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (effect.hasChineseName) {
            Text(
                text = effect.name,
                color = Color(0xCCFFFFFF).copy(alpha = 0.62f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
