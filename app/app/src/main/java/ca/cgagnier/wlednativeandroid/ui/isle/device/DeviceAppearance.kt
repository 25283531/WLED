package ca.cgagnier.wlednativeandroid.ui.isle.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import ca.cgagnier.wlednativeandroid.ui.components.loadPreviewDrawable
import ca.cgagnier.wlednativeandroid.ui.isle.DeviceTube
import ca.cgagnier.wlednativeandroid.ui.isle.IsleColors
import kotlinx.coroutines.isActive
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 设备外观资源根目录：`assets/device_models/<型号目录>/{meta.json,lamp.webp}`。 */
private const val MODELS_ASSET_DIR = "device_models"

/** 灯效帧的低分辨率画布：先缩到这么小再模糊，等于给磨砂罩做扩散。 */
private const val BLUR_WIDTH = 48
private const val BLUR_HEIGHT = 64
private const val BLUR_RADIUS = 3

/** 亚克力罩内的底色：灯灭或灯效偏暗时露出来的磨砂玻璃。 */
private val ACRYLIC_GLASS = Color(0xFF16211C)

/** 磨砂罩的乳白泛光色。 */
private val FROST_TINT = Color(0xFFE2F2E9)

/**
 * 一个硬件型号的外观素材。
 *
 * 灯体图是透明底的渲染图，发光区被掏空；[glass] 是整个圆柱罩范围，
 * [glow] 是纯绿色发光体范围。动态内容只画在 [glow] 里，罩壁内缩留出亚克力的厚度，
 * 避免灯效糊到灯体边界外。
 */
private data class DeviceModelAppearance(
    val product: String,
    val image: ImageBitmap,
    val aspectRatio: Float,
    val glass: LampRect,
    val glow: LampRect,
)

/** 归一化矩形（相对整张灯体图）。 */
private data class LampRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * 设备页的灯体外观。
 *
 * 固件 `/json/info` 的 `product` 决定外形（素材放在 `assets/device_models/<目录>/meta.json`）；
 * 未识别型号时退回抽象灯管 [DeviceTube]，保证老设备与新设备都有画面。
 *
 * 发光区按当前灯效播放打包的预览动图，经过模糊与磨砂罩处理后呈现亚克力灯罩的质感，
 * 亮度、开关状态实时生效。
 */
@Composable
fun DeviceAppearance(
    product: String?,
    effectPreview: String?,
    colors: List<Color>,
    isOn: Boolean,
    brightness: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val models = remember(context) { loadModelAppearances(context.applicationContext) }
    val model = product?.let { models[it.uppercase()] }

    if (model == null) {
        DeviceTube(colors = colors, isOn = isOn, brightness = brightness, modifier = modifier)
        return
    }

    ModelLamp(
        model = model,
        effectPreview = effectPreview,
        colors = colors,
        isOn = isOn,
        brightness = brightness,
        modifier = modifier,
    )
}

/** 按 meta.json 里登记的 product 建立型号索引，素材缺失或损坏时跳过该型号。 */
private fun loadModelAppearances(context: Context): Map<String, DeviceModelAppearance> {
    val dirs = runCatching { context.assets.list(MODELS_ASSET_DIR).orEmpty().toList() }
        .getOrDefault(emptyList())
    return dirs.mapNotNull { dir ->
        runCatching {
            val base = "$MODELS_ASSET_DIR/$dir"
            val meta = JSONObject(
                context.assets.open("$base/meta.json").bufferedReader().use { it.readText() },
            )
            val bitmap = context.assets.open("$base/${meta.getString("image")}")
                .use { BitmapFactory.decodeStream(it) } ?: return@runCatching null

            DeviceModelAppearance(
                product = meta.getString("product").uppercase(),
                image = bitmap.asImageBitmap(),
                aspectRatio = meta.getInt("width").toFloat() / meta.getInt("height").toFloat(),
                glass = meta.getJSONObject("glass").toLampRect(),
                glow = meta.getJSONObject("glow").toLampRect(),
            )
        }.getOrNull()
    }.associateBy { it.product }
}

private fun JSONObject.toLampRect(): LampRect = LampRect(
    left = getDouble("left").toFloat(),
    top = getDouble("top").toFloat(),
    right = getDouble("right").toFloat(),
    bottom = getDouble("bottom").toFloat(),
)

@Composable
private fun ModelLamp(
    model: DeviceModelAppearance,
    effectPreview: String?,
    colors: List<Color>,
    isOn: Boolean,
    brightness: Float,
    modifier: Modifier = Modifier,
) {
    val palette = colors.ifEmpty { listOf(IsleColors.Cyan, IsleColors.Violet, IsleColors.Pink) }
    val intensity = if (isOn) brightness.coerceIn(0f, 1f) else 0f

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val lampWidth = minOf(maxWidth, maxHeight * model.aspectRatio)
        val lampHeight = lampWidth / model.aspectRatio
        // 灯体的环境溢光，画在灯后面的整块区域里
        AmbientHalo(lampWidth = lampWidth, lampHeight = lampHeight, color = palette.first(), intensity = intensity)
        Box(Modifier.size(lampWidth, lampHeight)) {
            GlassWindow(
                model = model,
                lampWidth = lampWidth,
                lampHeight = lampHeight,
                effectPreview = effectPreview,
                palette = palette,
                isOn = isOn,
                intensity = intensity,
            )
            Image(
                bitmap = model.image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

/**
 * 圆柱罩内部：亚克力底 → 模糊后的灯效 → 磨砂罩。
 *
 * 画在灯体图之前，灯体图的金属顶盖/底座与罩壁会盖住内容的溢出部分。
 */
@Composable
private fun GlassWindow(
    model: DeviceModelAppearance,
    lampWidth: Dp,
    lampHeight: Dp,
    effectPreview: String?,
    palette: List<Color>,
    isOn: Boolean,
    intensity: Float,
) {
    val context = LocalContext.current
    val drawable = remember(effectPreview, isOn) {
        if (isOn) effectPreview?.let { loadPreviewDrawable(context, it) } else null
    }

    Box(
        modifier = Modifier
            .offset(x = lampWidth * model.glass.left, y = lampHeight * model.glass.top)
            .size(lampWidth * model.glass.width, lampHeight * model.glass.height)
            .clipToBounds(),
    ) {
        // 罩内的亚克力底，保证任何状态下都不是透空的
        Box(Modifier.fillMaxSize().background(ACRYLIC_GLASS))
        Box(
            modifier = Modifier
                .offset(
                    x = lampWidth * (model.glow.left - model.glass.left),
                    y = lampHeight * (model.glow.top - model.glass.top),
                )
                .size(lampWidth * model.glow.width, lampHeight * model.glow.height)
                .clipToBounds(),
        ) {
            when {
                !isOn -> Unit
                drawable != null -> FrostedEffect(drawable)
                else -> PaletteGlow(palette)
            }
        }
        // 关灯时由底色直接呈现磨砂罩，不再叠黑，否则整个灯管会变成一块死黑
        FrostedShell(dim = if (isOn) (1f - intensity) * 0.85f else 0f)
    }
}

/**
 * 灯效画面：先按低分辨率重绘一帧，再做盒式模糊再放大，得到磨砂扩散的观感。
 *
 * 直接用 AnimatedImageDrawable 画到 Compose 画布上，避免 View 互操作带来的层级问题，
 * 同时也让发光区严格限制在自己的边界内。
 */
@Suppress("UNUSED_EXPRESSION")
@Composable
private fun FrostedEffect(drawable: Drawable) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(drawable) {
        while (isActive) {
            withFrameNanos { tick++ }
        }
    }
    // 切换灯效后旧动图还在后台解码，离开时主动停掉
    DisposableEffect(drawable) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (drawable as? AnimatedImageDrawable)?.stop()
            }
        }
    }
    val frameBitmap = remember { Bitmap.createBitmap(BLUR_WIDTH, BLUR_HEIGHT, Bitmap.Config.ARGB_8888) }
    val frameCanvas = remember { android.graphics.Canvas(frameBitmap) }
    val buffer = remember { IntArray(BLUR_WIDTH * BLUR_HEIGHT) }
    val scratch = remember { IntArray(BLUR_WIDTH * BLUR_HEIGHT) }
    val paint = remember { Paint().apply { isFilterBitmap = true } }

    Canvas(Modifier.fillMaxSize()) {
        // 读取帧计数：值每帧变化，Compose 只重绘这块画布
        tick
        frameCanvas.drawColor(ACRYLIC_GLASS.toArgb())
        drawable.setBounds(0, 0, BLUR_WIDTH, BLUR_HEIGHT)
        drawable.draw(frameCanvas)
        blurArgb(frameBitmap, buffer, scratch)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(
                frameBitmap,
                null,
                Rect(0, 0, size.width.toInt(), size.height.toInt()),
                paint,
            )
        }
    }
}

/** 可分离盒式模糊（只作用在 48×64 的帧上，开销可以忽略）。 */
private fun blurArgb(bitmap: Bitmap, src: IntArray, scratch: IntArray) {
    val width = bitmap.width
    val height = bitmap.height
    bitmap.getPixels(src, 0, width, 0, 0, width, height)
    val span = BLUR_RADIUS * 2 + 1

    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            for (k in -BLUR_RADIUS..BLUR_RADIUS) {
                val color = src[row + (x + k).coerceIn(0, width - 1)]
                red += (color shr 16) and 0xFF
                green += (color shr 8) and 0xFF
                blue += color and 0xFF
            }
            scratch[row + x] = packArgb(red / span, green / span, blue / span)
        }
    }

    for (x in 0 until width) {
        for (y in 0 until height) {
            var red = 0
            var green = 0
            var blue = 0
            for (k in -BLUR_RADIUS..BLUR_RADIUS) {
                val color = scratch[(y + k).coerceIn(0, height - 1) * width + x]
                red += (color shr 16) and 0xFF
                green += (color shr 8) and 0xFF
                blue += color and 0xFF
            }
            src[y * width + x] = packArgb(red / span, green / span, blue / span)
        }
    }
    bitmap.setPixels(src, 0, width, 0, 0, width, height)
}

private fun packArgb(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

/** 无预览图的灯效：用调色板颜色做缓慢流动的光团，保证发光区始终是活的。 */
@Composable
private fun PaletteGlow(palette: List<Color>) {
    val transition = rememberInfiniteTransition(label = "glow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    Canvas(Modifier.fillMaxSize()) {
        for (index in 0 until 3) {
            val hue = palette[index % palette.size]
            val t = phase * (2f * PI.toFloat()) + index * 2.1f
            val center = Offset(
                x = size.width * (0.5f + 0.26f * sin(t * 0.7f + index)),
                y = size.height * (0.5f + 0.30f * cos(t * 0.9f + index * 1.7f)),
            )
            val radius = size.width * (0.9f + 0.12f * sin(t))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(hue.copy(alpha = 0.95f), hue.copy(alpha = 0.28f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

/** 磨砂亚克力罩：乳白半透明 + 圆柱压暗、竖向高光、上下厚度差，最后叠亮度遮罩。 */
@Composable
private fun FrostedShell(dim: Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(FROST_TINT.copy(alpha = 0.16f))
        // 罩壁上下更厚：靠近顶盖与底座的位置泛白更明显
        drawRect(
            brush = Brush.verticalGradient(listOf(FROST_TINT.copy(alpha = 0.14f), Color.Transparent)),
            size = Size(size.width, size.height * 0.14f),
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, FROST_TINT.copy(alpha = 0.14f))),
            topLeft = Offset(0f, size.height * 0.86f),
            size = Size(size.width, size.height * 0.14f),
        )
        // 圆柱侧壁压暗，平面图才有体积感
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)),
            size = Size(size.width * 0.14f, size.height),
        )
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))),
            topLeft = Offset(size.width * 0.86f, 0f),
            size = Size(size.width * 0.14f, size.height),
        )
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.16f),
                    Color.Transparent,
                ),
            ),
            topLeft = Offset(size.width * 0.22f, 0f),
            size = Size(size.width * 0.28f, size.height),
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)),
            size = Size(size.width, size.height * 0.08f),
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f))),
            topLeft = Offset(0f, size.height * 0.92f),
            size = Size(size.width, size.height * 0.08f),
        )
        if (dim > 0f) {
            drawRect(Color.Black.copy(alpha = dim))
        }
    }
}

/** 灯体背后的柔和溢光：亮度越高越明显，关灯时完全消失。 */
@Composable
private fun AmbientHalo(lampWidth: Dp, lampHeight: Dp, color: Color, intensity: Float) {
    if (intensity <= 0f) return
    Canvas(Modifier.fillMaxSize()) {
        val radius = lampWidth.toPx() * 1.1f
        val center = Offset(size.width / 2f, lampHeight.toPx() * 0.38f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.20f * intensity),
                    color.copy(alpha = 0.07f * intensity),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}
