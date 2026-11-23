package ca.cgagnier.wlednativeandroid.ui.create

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.isle.GlassCard
import ca.cgagnier.wlednativeandroid.ui.isle.Glyph
import ca.cgagnier.wlednativeandroid.ui.isle.GlyphIcon
import ca.cgagnier.wlednativeandroid.ui.isle.IsleBackdrop
import ca.cgagnier.wlednativeandroid.ui.isle.IsleCapsule
import ca.cgagnier.wlednativeandroid.ui.isle.IsleColors
import ca.cgagnier.wlednativeandroid.ui.isle.IsleSlider
import ca.cgagnier.wlednativeandroid.ui.isle.SectionLabel
import org.koin.androidx.compose.koinViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WHEEL_RING_DP = 26

/**
 * 创作工坊：色轮调色 + 自定义图文。
 *
 * 色轮取的两色会实时压到灯上；字幕直接写进固件的滚动字幕效果；
 * 像素画板则编码成 GIF 上传到设备后用 `Image` 效果显示。
 */
@Composable
fun CreateScreen(modifier: Modifier = Modifier, viewModel: CreateViewModel = koinViewModel()) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedMac by viewModel.selectedMacAddress.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val primary by viewModel.primary.collectAsStateWithLifecycle()
    val secondary by viewModel.secondary.collectAsStateWithLifecycle()
    val saturation by viewModel.saturation.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val text by viewModel.text.collectAsStateWithLifecycle()
    val tool by viewModel.tool.collectAsStateWithLifecycle()
    val brushColor by viewModel.brushColor.collectAsStateWithLifecycle()
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    val cols by viewModel.gridCols.collectAsStateWithLifecycle()
    val rows by viewModel.gridRows.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val device = devices.firstOrNull { it.device.macAddress == selectedMac }

    LaunchedEffect(device) { viewModel.configureGrid(device) }
    LaunchedEffect(status) {
        status?.let {
            Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
            viewModel.dismissStatus()
        }
    }

    IsleBackdrop(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.isle_create_title),
                    style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp),
                    color = IsleColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                IsleCapsule(
                    selected = mode == CreateMode.COLOR,
                    onClick = { viewModel.selectMode(CreateMode.COLOR) },
                    contentPadding = 11.dp,
                ) {
                    Text(stringResource(R.string.isle_create_color), fontSize = 11.5.sp, color = IsleColors.TextPrimary)
                }
                Spacer(Modifier.width(6.dp))
                IsleCapsule(
                    selected = mode == CreateMode.PIXEL,
                    onClick = { viewModel.selectMode(CreateMode.PIXEL) },
                    contentPadding = 11.dp,
                ) {
                    Text(stringResource(R.string.isle_create_pixel), fontSize = 11.5.sp, color = IsleColors.TextPrimary)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            if (device?.isOnline == true) IsleColors.Online else IsleColors.TextMuted,
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (device?.isOnline == true) {
                        stringResource(R.string.isle_status_connected)
                    } else {
                        stringResource(R.string.isle_status_offline)
                    },
                    color = IsleColors.TextMuted,
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f),
                )
                SaveButton(
                    onClick = { device?.let { viewModel.saveFavorite(it) } },
                )
            }

            when (mode) {
                CreateMode.COLOR -> ColorPanel(
                    primary = primary,
                    secondary = secondary,
                    saturation = saturation,
                    speed = speed,
                    onPick = { isPrimary, color ->
                        if (isPrimary) viewModel.setPrimary(color) else viewModel.setSecondary(color)
                    },
                    onSaturation = viewModel::setSaturation,
                    onSpeed = viewModel::setSpeed,
                )

                CreateMode.PIXEL -> PixelPanel(
                    grid = grid,
                    cols = cols,
                    rows = rows,
                    text = text,
                    tool = tool,
                    brushColor = brushColor,
                    onPaint = viewModel::paint,
                    onTextChange = viewModel::setText,
                    onSelectTool = viewModel::setTool,
                    onSelectColor = viewModel::setBrushColor,
                    onClear = viewModel::clearGrid,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionButton(
                    label = if (mode == CreateMode.COLOR) {
                        stringResource(R.string.isle_apply_colors)
                    } else {
                        stringResource(R.string.isle_send_text)
                    },
                    busy = busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val target = device
                        if (target == null) {
                            Toast.makeText(context, R.string.effects_no_device, Toast.LENGTH_SHORT).show()
                        } else if (mode == CreateMode.COLOR) {
                            viewModel.applyColors(target)
                        } else {
                            viewModel.sendText(target)
                        }
                    },
                )
                if (mode == CreateMode.PIXEL) {
                    ActionButton(
                        label = stringResource(R.string.isle_upload_art),
                        busy = busy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val target = device
                            if (target == null) {
                                Toast.makeText(context, R.string.effects_no_device, Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.uploadPixelArt(target)
                            }
                        },
                    )
                }
            }

            Text(
                text = stringResource(
                    if (mode == CreateMode.COLOR) R.string.isle_color_hint else R.string.isle_pixel_hint,
                ),
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SaveButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(
                Brush.linearGradient(listOf(Color(0x335FE3E4), Color(0x338C7BFF))),
                RoundedCornerShape(999.dp),
            )
            .border(1.dp, Color(0x595FE3E4), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = IsleColors.Cyan,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(stringResource(R.string.isle_save_favorite), color = IsleColors.TextPrimary, fontSize = 11.5.sp)
    }
}

@Composable
private fun ActionButton(label: String, busy: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(46.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFF5FE3E4), Color(0xFF8C7BFF))),
                RoundedCornerShape(16.dp),
            )
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color(0xFF0A0C12),
                strokeWidth = 2.dp,
            )
        } else {
            Text(label, color = Color(0xFF08090E), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ColorPanel(
    primary: Int,
    secondary: Int,
    saturation: Float,
    speed: Float,
    onPick: (Boolean, Int) -> Unit,
    onSaturation: (Float) -> Unit,
    onSpeed: (Float) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, padding = 18.dp) {
        Text(
            text = stringResource(R.string.isle_color_tuning),
            color = IsleColors.TextPrimary,
            fontSize = 16.sp,
        )
        ColorWheel(
            primary = primary,
            secondary = secondary,
            onPick = onPick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp)
                .height(236.dp),
        )
        LabeledSlider(
            label = stringResource(R.string.isle_flow_speed),
            glyph = Glyph.Aurora,
            value = speed,
            onChange = onSpeed,
        )
        LabeledSlider(
            label = stringResource(R.string.isle_saturation),
            glyph = Glyph.Dropper,
            value = saturation,
            onChange = onSaturation,
        )
    }
}

@Composable
private fun LabeledSlider(label: String, glyph: Glyph, value: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph = glyph, size = 15.dp, tint = IsleColors.TextMuted)
        Spacer(Modifier.width(8.dp))
        Text(label, color = IsleColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.width(72.dp))
        IsleSlider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = {},
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(value * 100).roundToInt()}%",
            color = IsleColors.TextPrimary,
            fontSize = 11.5.sp,
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun ColorWheel(
    primary: Int,
    secondary: Int,
    onPick: (Boolean, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableIntStateOf(-1) }
    val hues = remember { List(37) { index -> Color.hue(index / 36f) } }

    fun pick(pos: Offset, size: Size) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val dx = pos.x - cx
        val dy = pos.y - cy
        val distance = hypot(dx, dy)
        val outer = size.minDimension / 2f
        if (distance < outer * 0.42f || distance > outer * 1.05f) return
        var angle = atan2(dy, dx) / (2f * Math.PI.toFloat())
        if (angle < 0f) angle += 1f
        if (dragging == -1) {
            val primaryAngle = hueOf(primary)
            val secondaryAngle = hueOf(secondary)
            dragging = if (angleDistance(angle, primaryAngle) <= angleDistance(angle, secondaryAngle)) 0 else 1
        }
        onPick(dragging == 0, colorOfHue(angle))
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { pos -> dragging = -1; pick(pos, size.toSize()) }
        }.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { pos -> dragging = -1; pick(pos, size.toSize()) },
                onDragEnd = { dragging = -1 },
                onDragCancel = { dragging = -1 },
            ) { change, _ -> pick(change.position, size.toSize()) }
        },
    ) {
        val outer = size.minDimension / 2f
        val ringWidth = WHEEL_RING_DP.dp.toPx()
        val ringRadius = outer - ringWidth / 2f
        drawCircle(
            brush = Brush.sweepGradient(hues),
            radius = ringRadius,
            center = center,
            style = Stroke(width = ringWidth),
        )
        // 内圈做一点暗色填充，色环才不像一个悬空的甜甜圈
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x14FFFFFF), Color(0x00000000)),
                center = center,
                radius = ringRadius - ringWidth * 0.6f,
            ),
            radius = ringRadius - ringWidth * 0.6f,
            center = center,
        )
        listOf(primary, secondary).forEachIndexed { index, color ->
            val angle = hueOf(color) * 2f * Math.PI.toFloat()
            val handle = Offset(
                center.x + cos(angle) * ringRadius,
                center.y + sin(angle) * ringRadius,
            )
            val radius = if (index == 0) 13.dp.toPx() else 10.dp.toPx()
            drawCircle(color = Color(0xFF07080B), radius = radius + 3.dp.toPx(), center = handle)
            drawCircle(color = Color(0xFFFFFFFF), radius = radius + 1.5f.dp.toPx(), center = handle)
            drawCircle(color = Color(0xFF000000.toInt() or color), radius = radius, center = handle)
        }
    }
}

@Composable
private fun PixelPanel(
    grid: List<Int>,
    cols: Int,
    rows: Int,
    text: String,
    tool: PixelTool,
    brushColor: Int,
    onPaint: (Int) -> Unit,
    onTextChange: (String) -> Unit,
    onSelectTool: (PixelTool) -> Unit,
    onSelectColor: (Int) -> Unit,
    onClear: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.isle_pixel_title),
                color = IsleColors.TextPrimary,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$cols × $rows",
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(210.dp)
                .background(Color(0xFF07080B), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PixelGrid(
                grid = grid,
                cols = cols,
                rows = rows,
                onPaint = onPaint,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .background(Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            if (text.isEmpty()) {
                Text(
                    text = stringResource(R.string.isle_text_hint),
                    color = IsleColors.TextMuted,
                    fontSize = 13.sp,
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = TextStyle(color = IsleColors.TextPrimary, fontSize = 13.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(IsleColors.Cyan),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolButton(Glyph.Brush, R.string.isle_brush, tool == PixelTool.BRUSH) { onSelectTool(PixelTool.BRUSH) }
            ToolButton(Glyph.Eraser, R.string.isle_eraser, tool == PixelTool.ERASER) { onSelectTool(PixelTool.ERASER) }
            ToolButton(Glyph.Dropper, R.string.isle_picker, tool == PixelTool.PICKER) { onSelectTool(PixelTool.PICKER) }
            ToolButton(Glyph.Trash, R.string.isle_clear, false) { onClear() }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PIXEL_PALETTE.forEach { color ->
                val selected = color == brushColor
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color(0xFF000000.toInt() or color), CircleShape)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) Color.White else Color(0x33FFFFFF),
                            shape = CircleShape,
                        )
                        .clickable { onSelectColor(color) },
                )
            }
        }
    }
}

@Composable
private fun ToolButton(glyph: Glyph, labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 34.dp)
                .background(
                    if (selected) Color(0x2E5FE3E4) else Color(0x0FFFFFFF),
                    RoundedCornerShape(10.dp),
                )
                .border(
                    1.dp,
                    if (selected) Color(0x805FE3E4) else IsleColors.Stroke,
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = glyph,
                size = 16.dp,
                tint = if (selected) IsleColors.Cyan else IsleColors.TextSecondary,
            )
        }
        Text(
            text = stringResource(labelRes),
            color = if (selected) IsleColors.TextPrimary else IsleColors.TextMuted,
            fontSize = 9.5.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PixelGrid(grid: List<Int>, cols: Int, rows: Int, onPaint: (Int) -> Unit, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.pointerInput(cols, rows, grid.size) {
            detectTapGestures { pos ->
                val cell = minOf(size.width / cols, size.height / rows)
                if (cell <= 0f) return@detectTapGestures
                val offsetX = (size.width - cell * cols) / 2f
                val offsetY = (size.height - cell * rows) / 2f
                val col = ((pos.x - offsetX) / cell).toInt()
                val row = ((pos.y - offsetY) / cell).toInt()
                if (col in 0 until cols && row in 0 until rows) onPaint(row * cols + col)
            }
        },
    ) {
        val cell = minOf(size.width / cols, size.height / rows)
        val offsetX = (size.width - cell * cols) / 2f
        val offsetY = (size.height - cell * rows) / 2f
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val packed = grid.getOrElse(row * cols + col) { 0 }
                val base = Offset(offsetX + col * cell, offsetY + row * cell)
                val inset = cell * 0.12f
                drawRect(
                    color = if (packed == 0) Color(0x16FFFFFF) else Color(0xFF000000.toInt() or packed),
                    topLeft = Offset(base.x + inset, base.y + inset),
                    size = Size(cell - inset * 2, cell - inset * 2),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 色相工具

private fun Color.Companion.hue(h: Float): Color {
    val rgb = android.graphics.Color.HSVToColor(floatArrayOf((h * 360f) % 360f, 0.92f, 1f))
    return Color(0xFF000000 or rgb.toLong())
}

private fun colorOfHue(h: Float): Int {
    val rgb = android.graphics.Color.HSVToColor(floatArrayOf((h * 360f) % 360f, 0.92f, 1f))
    return rgb and 0xFFFFFF
}

private fun hueOf(packed: Int): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (packed shr 16) and 0xFF,
        (packed shr 8) and 0xFF,
        packed and 0xFF,
        hsv,
    )
    return hsv[0] / 360f
}

private fun angleDistance(a: Float, b: Float): Float {
    val d = kotlin.math.abs(a - b) % 1f
    return minOf(d, 1f - d)
}

private fun androidx.compose.ui.unit.IntSize.toSize() =
    androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())
