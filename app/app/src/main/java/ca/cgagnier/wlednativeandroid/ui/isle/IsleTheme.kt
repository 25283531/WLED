package ca.cgagnier.wlednativeandroid.ui.isle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 光屿的视觉基调：深空底色 + 玻璃卡片 + 冷调渐变。
 *
 * 刻意不做浅色主题 —— 这是一个控制灯具的 App，界面本身越暗，灯效预览越准；
 * 深色也让「发光」这件事在视觉上成立。原来的浅色/深色切换因此收起。
 */
object IsleColors {
    val Background = Color(0xFF07080B)
    val BackgroundTop = Color(0xFF0C0E14)
    val Surface = Color(0xFF14161D)
    val SurfaceHigh = Color(0xFF1A1D25)
    val Stroke = Color(0x1FFFFFFF)
    val StrokeStrong = Color(0x33FFFFFF)
    val TextPrimary = Color(0xFFF3F5FA)
    val TextSecondary = Color(0xFF9AA2B1)
    val TextMuted = Color(0xFF6A7284)
    val Online = Color(0xFF3EE08F)
    val Cyan = Color(0xFF5FE3E4)
    val Violet = Color(0xFF8C7BFF)
    val Pink = Color(0xFFFF7AD0)
    val Amber = Color(0xFFFFC46B)

    /** 选中态统一的青紫渐变。 */
    val Accent = listOf(Cyan, Violet)

    /** 卡片底：上亮下暗，模拟玻璃的掠射光。 */
    val CardBrush = Brush.verticalGradient(
        listOf(Color(0xF21A1D25), Color(0xF2101218)),
    )
}

/** 全屏背景：底色 + 顶部青、底部紫的两团柔光。 */
@Composable
fun IsleBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IsleColors.Background),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(IsleColors.BackgroundTop, IsleColors.Background),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x2E3FD5C8), Color.Transparent),
                    center = Offset(size.width * 0.18f, -size.height * 0.02f),
                    radius = size.width * 0.95f,
                ),
                radius = size.width * 0.95f,
                center = Offset(size.width * 0.18f, -size.height * 0.02f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x2A6C4BE0), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.30f),
                    radius = size.width * 0.85f,
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * 0.92f, size.height * 0.30f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x1F2A7BD0), Color.Transparent),
                    center = Offset(size.width * 0.05f, size.height * 0.92f),
                    radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.05f, size.height * 0.92f),
            )
        }
        content()
    }
}
