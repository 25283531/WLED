package ca.cgagnier.wlednativeandroid.ui.isle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.cgagnier.wlednativeandroid.R
import kotlin.math.roundToInt

/** 产品预设色，取色弹层与色板共用。 */
val PRESET_COLORS = listOf(
    0xFFFFFF,         // 白
    0x00F5FF,         // 亮青
    0x1E78FF,         // 蔚蓝
    0xAF28FF,         // 紫罗兰
    0xFF4FA8,         // 洋红
    0xFF8A3C,         // 橙
    0x3CFF8A,         // 翠绿
    0xFFE9D7,         // 暖白
    0xFFB46B,         // 暖黄（约 2700K，睡眠夜灯的基准色）
)

/** `0xRRGGBB`（协议里的 `col[]` 分量）转 Compose 颜色。 */
fun packedToColor(packed: Int): Color = Color(
    red = (packed shr 16) and 0xFF,
    green = (packed shr 8) and 0xFF,
    blue = packed and 0xFF,
)

/** Compose 颜色压回 `0xRRGGBB`，与 `col[]` 协议字段一致。 */
fun packedRgb(color: Color): Int = color.toArgb() and 0xFFFFFF

/** HSV（0..1）压成 `0xRRGGBB`。 */
fun hsvToPacked(hue: Float, saturation: Float, brightness: Float): Int =
    android.graphics.Color.HSVToColor(
        floatArrayOf(
            hue.coerceIn(0f, 1f) * 360f,
            saturation.coerceIn(0f, 1f),
            brightness.coerceIn(0f, 1f),
        ),
    ) and 0xFFFFFF

/**
 * 任意取色弹层：HSV 三个滑条 + 产品预设色。
 *
 * 设备页的频谱/舞台/三频呼吸/声波脉冲/频谱灯/螺旋灯，以及创作页的多色模板色标，
 * 都复用这一个弹层。滑条松手与预设点按都会回调 [onPick]，拖动过程中只更新本地预览。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0] / 360f) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(if (hsv[2] < 0.05f) 1f else hsv[2]) }
    val current = hsvToPacked(hue, saturation, brightness)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF12141A),
        contentColor = IsleColors.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = title,
                color = IsleColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(packedToColor(current))
                    .border(1.dp, IsleColors.StrokeStrong, RoundedCornerShape(12.dp)),
            )
            ColorSlider(
                label = stringResource(R.string.isle_freq_hue),
                value = hue,
                track = Brush.horizontalGradient(
                    listOf(
                        0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(), 0xFF00FFFF.toInt(),
                        0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt(),
                    ).map { packedToColor(it) },
                ),
                onValueChange = { hue = it },
                onCommit = { onPick(hsvToPacked(hue, saturation, brightness)) },
            )
            ColorSlider(
                label = stringResource(R.string.isle_freq_saturation),
                value = saturation,
                track = Brush.horizontalGradient(
                    listOf(Color.White, packedToColor(hsvToPacked(hue, 1f, brightness))),
                ),
                onValueChange = { saturation = it },
                onCommit = { onPick(hsvToPacked(hue, saturation, brightness)) },
            )
            ColorSlider(
                label = stringResource(R.string.isle_freq_brightness),
                value = brightness,
                track = Brush.horizontalGradient(
                    listOf(Color.Black, packedToColor(hsvToPacked(hue, saturation, 1f))),
                ),
                onValueChange = { brightness = it },
                onCommit = { onPick(hsvToPacked(hue, saturation, brightness)) },
            )

            Text(
                text = stringResource(R.string.isle_freq_preset_colors),
                color = IsleColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                PRESET_COLORS.forEach { packed ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(packedToColor(packed), CircleShape)
                            .border(1.dp, IsleColors.StrokeStrong, CircleShape)
                            .clickable {
                                onPick(packed)
                                onDismiss()
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    track: Brush,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = IsleColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(value * 100).roundToInt()}%",
                color = IsleColors.TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier.width(40.dp),
            )
        }
        IsleSlider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onCommit,
            trackBrush = track,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        )
    }
}
