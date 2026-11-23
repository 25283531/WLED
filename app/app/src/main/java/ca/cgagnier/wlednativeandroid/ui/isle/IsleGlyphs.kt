package ca.cgagnier.wlednativeandroid.ui.isle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 细线条图标集。
 *
 * 项目只依赖 `material-icons-core`，里面的图标是实心剪影，和设计稿的 1.6dp 细线风格不搭；
 * 直接引 `material-icons-extended` 会带进几千个用不到的图标。所以这里用 24×24 坐标系
 * 手绘一套，笔画粗细和圆角都与设计稿一致。
 */
enum class Glyph {
    Devices,
    Timer,
    Power,
    Library,
    Create,
    Settings,
    Search,
    Sync,
    Aurora,
    Music,
    Text,
    Moon,
    Wifi,
    Mic,
    Chip,
    Brush,
    Eraser,
    Dropper,
    Trash,
    ChevronRight,
    Plus,
    Check,
    Sparkle,
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = IsleColors.TextSecondary,
    strokeWidth: Float = 1.7f,
) {
    Canvas(modifier = modifier.size(size)) {
        val factor = this.size.minDimension / 24f
        scale(factor, factor, pivot = Offset.Zero) {
            drawGlyph(glyph, tint, strokeWidth)
        }
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color, width: Float) {
    val stroke = Stroke(width = width, cap = StrokeCap.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = width, cap = StrokeCap.Round)

    when (glyph) {
        Glyph.Devices -> {
            drawRoundRect(
                color, Offset(3.4f, 2.6f), Size(6.8f, 18.8f),
                CornerRadius(2.4f), style = stroke,
            )
            drawRoundRect(
                color, Offset(13.0f, 6.4f), Size(6.6f, 15f),
                CornerRadius(2.2f), style = stroke,
            )
        }

        Glyph.Timer -> {
            drawCircle(color, radius = 7.2f, center = Offset(12f, 13.4f), style = stroke)
            line(12f, 13.4f, 12f, 9.2f)
            line(12f, 13.4f, 15.2f, 14.8f)
            line(9f, 3.4f, 15f, 3.4f)
            line(12f, 3.4f, 12f, 6.2f)
        }

        Glyph.Power -> {
            drawArc(
                color = color,
                startAngle = 132f,
                sweepAngle = 276f,
                useCenter = false,
                topLeft = Offset(3.4f, 3.6f),
                size = Size(17.2f, 17.2f),
                style = stroke,
            )
            line(12f, 2.4f, 12f, 11.4f)
        }

        Glyph.Library -> {
            drawRoundRect(
                color, Offset(3.2f, 4.4f), Size(17.6f, 15.6f),
                CornerRadius(3.4f), style = stroke,
            )
            drawCircle(color, radius = 1.5f, center = Offset(8.6f, 9.6f), style = stroke)
            val mountain = Path().apply {
                moveTo(4.8f, 16.9f)
                lineTo(9.7f, 12.3f)
                lineTo(12.9f, 15.1f)
                lineTo(15.9f, 12.5f)
                lineTo(19.4f, 16.5f)
            }
            drawPath(mountain, color, style = stroke)
        }

        Glyph.Create -> {
            val sparkle = Path().apply {
                moveTo(11f, 3.2f)
                quadraticBezierTo(12.6f, 9.4f, 18.8f, 11f)
                quadraticBezierTo(12.6f, 12.6f, 11f, 18.8f)
                quadraticBezierTo(9.4f, 12.6f, 3.2f, 11f)
                quadraticBezierTo(9.4f, 9.4f, 11f, 3.2f)
                close()
            }
            drawPath(sparkle, color, style = stroke)
            drawCircle(color, radius = 1.5f, center = Offset(18.6f, 18f))
            val small = Path().apply {
                moveTo(18.6f, 14.4f)
                quadraticBezierTo(19.2f, 17.4f, 22.2f, 18f)
                quadraticBezierTo(19.2f, 18.6f, 18.6f, 21.6f)
                quadraticBezierTo(18f, 18.6f, 15f, 18f)
                quadraticBezierTo(18f, 17.4f, 18.6f, 14.4f)
                close()
            }
            drawPath(small, color)
        }

        Glyph.Settings -> {
            // 三条带滑块的横线：比齿轮更轻，也更符合"设置"的动作语义
            val rows = listOf(6.2f to 15.4f, 12f to 9.6f, 17.8f to 17.2f)
            rows.forEach { (y, knobX) ->
                line(3.6f, y, 20.4f, y)
                drawCircle(color, radius = 2.3f, center = Offset(knobX, y), style = stroke)
            }
        }

        Glyph.Search -> {
            drawCircle(color, radius = 6.4f, center = Offset(10.8f, 10.8f), style = stroke)
            line(15.6f, 15.6f, 20.4f, 20.4f)
        }

        Glyph.Sync -> {
            val top = Path().apply {
                moveTo(4.2f, 9.4f)
                lineTo(18.4f, 9.4f)
            }
            drawPath(top, color, style = stroke)
            val topHead = Path().apply {
                moveTo(14.6f, 5.4f)
                lineTo(18.8f, 9.4f)
                lineTo(14.6f, 13.4f)
            }
            drawPath(topHead, color, style = stroke)
            val bottom = Path().apply {
                moveTo(19.8f, 15f)
                lineTo(5.6f, 15f)
            }
            drawPath(bottom, color, style = stroke)
            val bottomHead = Path().apply {
                moveTo(9.4f, 11f)
                lineTo(5.2f, 15f)
                lineTo(9.4f, 19f)
            }
            drawPath(bottomHead, color, style = stroke)
        }

        Glyph.Aurora -> {
            listOf(7.4f, 12.4f, 17f).forEachIndexed { index, y ->
                val path = Path().apply {
                    moveTo(3.2f, y)
                    cubicTo(8f, y - 3.6f + index * 0.4f, 12.4f, y + 3.4f, 20.8f, y - 1.2f)
                }
                drawPath(path, color, style = stroke)
            }
        }

        Glyph.Music -> {
            drawCircle(color, radius = 3.1f, center = Offset(8.4f, 16.6f), style = stroke)
            line(11.5f, 16.6f, 11.5f, 5.2f)
            val flag = Path().apply {
                moveTo(11.5f, 5.2f)
                cubicTo(14.6f, 5.2f, 17.4f, 6.6f, 17.4f, 10.2f)
            }
            drawPath(flag, color, style = stroke)
        }

        Glyph.Text -> {
            line(4.4f, 6.6f, 19.6f, 6.6f)
            line(4.4f, 12f, 15f, 12f)
            line(4.4f, 17.4f, 17.4f, 17.4f)
        }

        Glyph.Moon -> {
            val crescent = Path().apply {
                moveTo(19.6f, 14.4f)
                cubicTo(13.2f, 15.8f, 8.9f, 11.4f, 10.2f, 4.8f)
                cubicTo(5.6f, 6.4f, 2.9f, 10.6f, 4.6f, 15.2f)
                cubicTo(6.4f, 20.1f, 11.9f, 22.2f, 16.9f, 19.6f)
                cubicTo(18.2f, 18.8f, 19.1f, 17.7f, 19.6f, 16.4f)
                cubicTo(18.6f, 17f, 17.7f, 17.3f, 16.8f, 17.3f)
                cubicTo(13.2f, 17.3f, 10.4f, 14.5f, 10.4f, 10.9f)
                cubicTo(10.4f, 9.9f, 10.6f, 8.9f, 11f, 8f)
                close()
            }
            drawPath(crescent, color)
        }

        Glyph.Wifi -> {
            for (index in 0 until 3) {
                val radius = 3.4f + index * 3.4f
                drawArc(
                    color = color,
                    startAngle = 215f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(12f - radius, 17.4f - radius),
                    size = Size(radius * 2, radius * 2),
                    style = stroke,
                )
            }
            drawCircle(color, radius = 1.1f, center = Offset(12f, 17f))
        }

        Glyph.Mic -> {
            drawRoundRect(
                color, Offset(9f, 2.6f), Size(6f, 10.4f),
                CornerRadius(3f), style = stroke,
            )
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(6.6f, 8.2f),
                size = Size(10.8f, 10.8f),
                style = stroke,
            )
            line(12f, 19f, 12f, 21.4f)
        }

        Glyph.Chip -> {
            drawRoundRect(
                color, Offset(6.2f, 6.2f), Size(11.6f, 11.6f),
                CornerRadius(2.6f), style = stroke,
            )
            drawRoundRect(
                color, Offset(9.6f, 9.6f), Size(4.8f, 4.8f),
                CornerRadius(1.2f), style = stroke,
            )
            for (offset in listOf(9.4f, 14.6f)) {
                line(offset, 2.6f, offset, 6.2f)
                line(offset, 17.8f, offset, 21.4f)
                line(2.6f, offset, 6.2f, offset)
                line(17.8f, offset, 21.4f, offset)
            }
        }

        Glyph.Brush -> {
            line(6.4f, 17.6f, 15.4f, 8.6f)
            val tip = Path().apply {
                moveTo(15.4f, 8.6f)
                lineTo(18.4f, 5.6f)
                lineTo(20.4f, 7.6f)
                lineTo(17.4f, 10.6f)
                close()
            }
            drawPath(tip, color, style = stroke)
            line(6.4f, 17.6f, 5.4f, 21f)
        }

        Glyph.Eraser -> {
            val body = Path().apply {
                moveTo(4.2f, 15.6f)
                lineTo(11.8f, 8f)
                lineTo(18.4f, 14.6f)
                lineTo(13.6f, 19.4f)
                lineTo(7.6f, 19.4f)
                close()
            }
            drawPath(body, color, style = stroke)
            line(11.8f, 8f, 16.6f, 12.8f)
        }

        Glyph.Dropper -> {
            line(4.6f, 19.4f, 13.4f, 10.6f)
            val bulb = Path().apply {
                moveTo(12.4f, 9.6f)
                lineTo(15.4f, 6.6f)
                cubicTo(16.4f, 5.6f, 18.4f, 5.6f, 19.4f, 6.6f)
                cubicTo(20.4f, 7.6f, 20.4f, 9.6f, 19.4f, 10.6f)
                lineTo(16.4f, 13.6f)
                close()
            }
            drawPath(bulb, color, style = stroke)
        }

        Glyph.Trash -> {
            line(4.4f, 7f, 19.6f, 7f)
            val handle = Path().apply {
                moveTo(9.4f, 7f)
                lineTo(9.4f, 4.4f)
                lineTo(14.6f, 4.4f)
                lineTo(14.6f, 7f)
            }
            drawPath(handle, color, style = stroke)
            val bin = Path().apply {
                moveTo(6.4f, 7f)
                lineTo(7.4f, 20.4f)
                lineTo(16.6f, 20.4f)
                lineTo(17.6f, 7f)
            }
            drawPath(bin, color, style = stroke)
        }

        Glyph.ChevronRight -> {
            val path = Path().apply {
                moveTo(9.4f, 5.4f)
                lineTo(15.6f, 12f)
                lineTo(9.4f, 18.6f)
            }
            drawPath(path, color, style = stroke)
        }

        Glyph.Plus -> {
            line(12f, 5.6f, 12f, 18.4f)
            line(5.6f, 12f, 18.4f, 12f)
        }

        Glyph.Check -> {
            val path = Path().apply {
                moveTo(4.8f, 12.6f)
                lineTo(9.6f, 17.2f)
                lineTo(19.2f, 6.6f)
            }
            drawPath(path, color, style = stroke)
        }

        Glyph.Sparkle -> {
            val path = Path().apply {
                moveTo(12f, 2.6f)
                quadraticBezierTo(13.8f, 10.2f, 21.4f, 12f)
                quadraticBezierTo(13.8f, 13.8f, 12f, 21.4f)
                quadraticBezierTo(10.2f, 13.8f, 2.6f, 12f)
                quadraticBezierTo(10.2f, 10.2f, 12f, 2.6f)
                close()
            }
            drawPath(path, color)
        }
    }
}
