package ca.cgagnier.wlednativeandroid.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val PREVIEW_ASSET_DIR = "effect_previews"

/**
 * 灯效预览动图。
 *
 * GIF 按**32×18 的灯珠栅格**（渲染成 128×72 像素）生成，1D 效果会先算出一条蛇形灯带的
 * 颜色再按 S 形铺满矩阵，所以看到的是整块光栅而不是一条线。
 * 预览图与具体硬件解耦，这里只负责播放。
 *
 * @param assetName `assets/effect_previews/<assetName>.gif` 的文件名（不含扩展名）；null 时显示占位块。
 */
@Composable
fun EffectPreviewGif(
    assetName: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val drawable = remember(assetName) { assetName?.let { loadPreviewDrawable(context, it) } }
    val description = contentDescription
    val semantics = description?.let { text ->
        Modifier.semantics { this.contentDescription = text }
    } ?: Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            )
            .then(semantics),
        contentAlignment = Alignment.Center,
    ) {
        if (drawable != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { view -> view.setImageDrawable(drawable) },
            )
        } else {
            Text(
                text = "◍",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.35f),
            )
        }
    }
}

/**
 * 加载预览图。API 28+ 用 [AnimatedImageDrawable] 播放动图；更低版本退回首帧静态图。
 * 素材缺失或格式异常时返回 null，由调用方显示占位块。
 */
private fun loadPreviewDrawable(context: Context, assetName: String): Drawable? {
    val path = "$PREVIEW_ASSET_DIR/$assetName.gif"
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.assets, path)
            val decoderDrawable = android.graphics.ImageDecoder.decodeDrawable(source)
            (decoderDrawable as? AnimatedImageDrawable)?.apply {
                repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                start()
            }
            decoderDrawable
        } else {
            context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream)?.let { BitmapDrawable(context.resources, it) }
            }
        }
    }.getOrNull()
}
