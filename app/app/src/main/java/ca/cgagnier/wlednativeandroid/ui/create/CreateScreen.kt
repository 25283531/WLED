package ca.cgagnier.wlednativeandroid.ui.create

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import ca.cgagnier.wlednativeandroid.ui.effects.PaletteTemplate
import ca.cgagnier.wlednativeandroid.ui.effects.PaletteTemplateStore
import ca.cgagnier.wlednativeandroid.ui.isle.ColorPickerSheet
import ca.cgagnier.wlednativeandroid.ui.isle.GlassCard
import ca.cgagnier.wlednativeandroid.ui.isle.Glyph
import ca.cgagnier.wlednativeandroid.ui.isle.GlyphIcon
import ca.cgagnier.wlednativeandroid.ui.isle.IsleBackdrop
import ca.cgagnier.wlednativeandroid.ui.isle.IsleCapsule
import ca.cgagnier.wlednativeandroid.ui.isle.IsleColors
import ca.cgagnier.wlednativeandroid.ui.isle.IsleSlider
import ca.cgagnier.wlednativeandroid.ui.isle.SectionLabel
import ca.cgagnier.wlednativeandroid.ui.isle.packedToColor
import ca.cgagnier.wlednativeandroid.util.normalizedToPercent
import org.koin.androidx.compose.koinViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val WHEEL_RING_DP = 26

/**
 * 创作工坊：色轮调色 + 自定义图文。
 *
 * 色轮取的两色会实时压到灯上；自定义图文把文字 / 时间 / 图片叠加后按灯珠矩阵
 * 栅格化成 GIF 上传，用 `Image` 效果显示，因此中文与 Emoji 也能正常出现在灯上。
 */
@Composable
fun CreateScreen(
    modifier: Modifier = Modifier,
    openComposerRequest: Int = 0,
    viewModel: CreateViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedMac by viewModel.selectedMacAddress.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val primary by viewModel.primary.collectAsStateWithLifecycle()
    val secondary by viewModel.secondary.collectAsStateWithLifecycle()
    val saturation by viewModel.saturation.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val layers by viewModel.layers.collectAsStateWithLifecycle()
    val frameCount by viewModel.frameCount.collectAsStateWithLifecycle()
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    val cols by viewModel.gridCols.collectAsStateWithLifecycle()
    val rows by viewModel.gridRows.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val templateColors by viewModel.templateColors.collectAsStateWithLifecycle()
    val paletteTemplates by viewModel.paletteTemplates.collectAsStateWithLifecycle()
    val reverse by viewModel.reverse.collectAsStateWithLifecycle()
    val trailPercent by viewModel.trailPercent.collectAsStateWithLifecycle()
    val darkCutoff by viewModel.darkCutoff.collectAsStateWithLifecycle()
    val background by viewModel.background.collectAsStateWithLifecycle()
    var templateColorIndex by remember { mutableIntStateOf(-1) }
    var imageLayerId by remember { mutableStateOf<Long?>(null) }
    var showBackgroundPicker by remember { mutableStateOf(false) }

    val device = devices.firstOrNull { it.device.macAddress == selectedMac }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            imageLayerId?.let { id ->
                viewModel.updateLayer(id) { it.copy(imageUri = uri.toString()) }
            }
        }
        imageLayerId = null
    }

    LaunchedEffect(device) { viewModel.configureGrid(device) }
    // 从设备页/灯效库选中「动态字幕」时，自动切到自定义图文分段
    LaunchedEffect(openComposerRequest) {
        if (openComposerRequest > 0) viewModel.selectMode(CreateMode.PIXEL)
    }
    // 图层或画布尺寸变化后重新栅格化预览
    LaunchedEffect(layers, cols, rows, speed, reverse, trailPercent, darkCutoff, background) {
        viewModel.refreshPreview(context)
    }
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
            Text(
                text = stringResource(R.string.isle_create_title),
                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp),
                color = IsleColors.TextPrimary,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IsleCapsule(
                    selected = mode == CreateMode.COLOR,
                    onClick = { viewModel.selectMode(CreateMode.COLOR) },
                    contentPadding = 11.dp,
                ) {
                    Text(stringResource(R.string.isle_create_color), fontSize = 11.5.sp, color = IsleColors.TextPrimary)
                }
                IsleCapsule(
                    selected = mode == CreateMode.PIXEL,
                    onClick = { viewModel.selectMode(CreateMode.PIXEL) },
                    contentPadding = 11.dp,
                ) {
                    Text(stringResource(R.string.isle_create_pixel), fontSize = 11.5.sp, color = IsleColors.TextPrimary)
                }
                IsleCapsule(
                    selected = mode == CreateMode.TEMPLATE,
                    onClick = { viewModel.selectMode(CreateMode.TEMPLATE) },
                    contentPadding = 11.dp,
                ) {
                    Text(
                        stringResource(R.string.isle_create_template),
                        fontSize = 11.5.sp,
                        color = IsleColors.TextPrimary,
                    )
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
                // 自定义图文不再对应灯效库里的效果（Image 已隐藏），收藏后无处展示，
                // 因此只在色轮调色模式保留收藏入口。
                if (mode == CreateMode.COLOR) {
                    SaveButton(
                        onClick = { device?.let { viewModel.saveFavorite(it) } },
                    )
                }
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

                CreateMode.PIXEL -> GraphicPanel(
                    grid = grid,
                    cols = cols,
                    rows = rows,
                    layers = layers,
                    frameCount = frameCount,
                    speed = speed,
                    reverse = reverse,
                    trailPercent = trailPercent,
                    darkCutoff = darkCutoff,
                    background = background,
                    onSpeed = viewModel::setSpeed,
                    onReverse = viewModel::setReverse,
                    onTrail = viewModel::setTrailPercent,
                    onDarkCutoff = viewModel::setDarkCutoff,
                    onPickBackground = { showBackgroundPicker = true },
                    onAddLayer = viewModel::addLayer,
                    onUpdateLayer = viewModel::updateLayer,
                    onRemoveLayer = viewModel::removeLayer,
                    onMoveLayer = viewModel::moveLayer,
                    onPickImage = { id ->
                        imageLayerId = id
                        pickImage.launch("image/*")
                    },
                )

                CreateMode.TEMPLATE -> TemplatePanel(
                    colors = templateColors,
                    templates = paletteTemplates,
                    onEditColor = { templateColorIndex = it },
                    onAddColor = viewModel::addTemplateColor,
                    onRemoveColor = viewModel::removeTemplateColor,
                    onSave = {
                        if (viewModel.saveTemplate()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.isle_palette_template_saved),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onDelete = viewModel::deleteTemplate,
                )
            }

            if (mode != CreateMode.TEMPLATE) {
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
                            stringResource(R.string.isle_upload_art)
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
                                viewModel.uploadGraphic(target, context)
                            }
                        },
                    )
                }
            }

            Text(
                text = stringResource(
                    when (mode) {
                        CreateMode.COLOR -> R.string.isle_color_hint
                        CreateMode.PIXEL -> R.string.isle_pixel_hint
                        CreateMode.TEMPLATE -> R.string.isle_create_template_hint
                    },
                ),
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            )
        }

        // 模板色标取色：松手才提交，拖动过程只在弹层里预览
        if (templateColorIndex in templateColors.indices) {
            ColorPickerSheet(
                title = stringResource(R.string.isle_template_color_title, templateColorIndex + 1),
                initial = templateColors[templateColorIndex],
                onDismiss = { templateColorIndex = -1 },
                onPick = { viewModel.setTemplateColor(templateColorIndex, it) },
            )
        }

        if (showBackgroundPicker) {
            ColorPickerSheet(
                title = stringResource(R.string.isle_graphic_background),
                initial = background,
                onDismiss = { showBackgroundPicker = false },
                onPick = { viewModel.setBackground(it) },
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
private fun TemplatePanel(
    colors: List<Int>,
    templates: List<PaletteTemplate>,
    onEditColor: (Int) -> Unit,
    onAddColor: () -> Unit,
    onRemoveColor: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, padding = 18.dp) {
        Text(
            text = stringResource(R.string.isle_palette_template_editor),
            color = IsleColors.TextPrimary,
            fontSize = 16.sp,
        )
        Text(
            text = stringResource(R.string.isle_palette_template_edit_hint),
            color = IsleColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        // 色标实时预览，与固件上传后的渐变方向一致（从左到右）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .height(46.dp)
                .background(
                    Brush.horizontalGradient(colors.map { packedToColor(it) }),
                    RoundedCornerShape(14.dp),
                )
                .border(1.dp, IsleColors.StrokeStrong, RoundedCornerShape(14.dp)),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            colors.forEachIndexed { index, packed ->
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(packedToColor(packed), CircleShape)
                            .border(1.dp, IsleColors.StrokeStrong, CircleShape)
                            .clickable { onEditColor(index) },
                    )
                    if (colors.size > PaletteTemplateStore.MIN_COLORS) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(16.dp)
                                .background(IsleColors.Surface, CircleShape)
                                .border(1.dp, IsleColors.StrokeStrong, CircleShape)
                                .clickable { onRemoveColor(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("×", color = IsleColors.TextSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }
            if (colors.size < PaletteTemplateStore.MAX_COLORS) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0x14FFFFFF), CircleShape)
                        .border(1.dp, IsleColors.StrokeStrong, CircleShape)
                        .clickable(onClick = onAddColor),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(glyph = Glyph.Plus, size = 16.dp, tint = IsleColors.TextSecondary)
                }
            }
        }

        ActionButton(
            label = stringResource(R.string.isle_palette_template_save),
            busy = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            onClick = onSave,
        )
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        cornerRadius = 26.dp,
        padding = 18.dp,
    ) {
        Text(
            text = stringResource(R.string.isle_saved_templates),
            color = IsleColors.TextPrimary,
            fontSize = 16.sp,
        )
        if (templates.isEmpty()) {
            Text(
                text = stringResource(R.string.isle_saved_templates_empty),
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            templates.forEach { template ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(28.dp)
                            .background(
                                Brush.horizontalGradient(template.colors.map { packedToColor(it) }),
                                RoundedCornerShape(8.dp),
                            )
                            .border(1.dp, IsleColors.Stroke, RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = template.name,
                        color = IsleColors.TextPrimary,
                        fontSize = 12.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onDelete(template.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(glyph = Glyph.Trash, size = 15.dp, tint = IsleColors.TextMuted)
                    }
                }
            }
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
            text = "${normalizedToPercent(value)}%",
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

    // pointerInput(Unit) 的手势协程永不重启，会永久捕获首次组合的回调，
    // 这里用 rememberUpdatedState 让它每次都能读到最新的主/副色与回调。
    val currentPrimary by rememberUpdatedState(primary)
    val currentSecondary by rememberUpdatedState(secondary)
    val currentOnPick by rememberUpdatedState(onPick)

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
            val primaryAngle = hueOf(currentPrimary)
            val secondaryAngle = hueOf(currentSecondary)
            dragging = if (angleDistance(angle, primaryAngle) <= angleDistance(angle, secondaryAngle)) 0 else 1
        }
        currentOnPick(dragging == 0, colorOfHue(angle))
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
private fun GraphicPanel(
    grid: List<Int>,
    cols: Int,
    rows: Int,
    layers: List<GraphicLayer>,
    frameCount: Int,
    speed: Float,
    reverse: Boolean,
    trailPercent: Int,
    darkCutoff: Int,
    background: Int,
    onSpeed: (Float) -> Unit,
    onReverse: (Boolean) -> Unit,
    onTrail: (Int) -> Unit,
    onDarkCutoff: (Int) -> Unit,
    onPickBackground: () -> Unit,
    onAddLayer: (GraphicLayerType) -> Unit,
    onUpdateLayer: (Long, (GraphicLayer) -> GraphicLayer) -> Unit,
    onRemoveLayer: (Long) -> Unit,
    onMoveLayer: (Long, Int) -> Unit,
    onPickImage: (Long) -> Unit,
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
                text = if (frameCount > 1) {
                    stringResource(R.string.isle_graphic_frames, frameCount)
                } else {
                    "$cols × $rows"
                },
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
            )
        }

        // 画布只做预览，不再支持手绘；真实画面在投送时按设备矩阵栅格化。
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
                onPaint = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            )
        }

        Text(
            text = stringResource(R.string.isle_graphic_layers),
            color = IsleColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 14.dp),
        )

        layers.forEachIndexed { index, layer ->
            GraphicLayerCard(
                layer = layer,
                index = index,
                count = layers.size,
                onChange = { transform -> onUpdateLayer(layer.id, transform) },
                onRemove = { onRemoveLayer(layer.id) },
                onMove = { delta -> onMoveLayer(layer.id, delta) },
                onPickImage = { onPickImage(layer.id) },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToolButton(Glyph.Text, R.string.isle_graphic_add_text, false) {
                onAddLayer(GraphicLayerType.TEXT)
            }
            ToolButton(Glyph.Timer, R.string.isle_graphic_add_time, false) {
                onAddLayer(GraphicLayerType.TIME)
            }
            ToolButton(Glyph.Sparkle, R.string.isle_graphic_add_image, false) {
                onAddLayer(GraphicLayerType.IMAGE)
            }
        }

        LabeledSlider(
            label = stringResource(R.string.isle_graphic_scroll_speed),
            glyph = Glyph.Sync,
            value = speed,
            onChange = onSpeed,
        )
        LabeledSlider(
            label = stringResource(R.string.isle_graphic_trail),
            glyph = Glyph.Sparkle,
            value = trailPercent / 90f,
            onChange = { value -> onTrail((value * 90).toInt()) },
        )
        LabeledSlider(
            label = stringResource(R.string.isle_graphic_dark_cutoff),
            glyph = Glyph.Dropper,
            value = darkCutoff / 255f,
            onChange = { value -> onDarkCutoff((value * 255).toInt()) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniChip(
                label = stringResource(R.string.isle_graphic_reverse),
                selected = reverse,
                onClick = { onReverse(!reverse) },
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(Color(0xFF000000.toInt() or background), CircleShape)
                    .border(2.dp, Color(0x66FFFFFF), CircleShape)
                    .clickable(onClick = onPickBackground),
            )
            Text(
                text = stringResource(R.string.isle_graphic_background),
                color = IsleColors.TextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun GraphicLayerCard(
    layer: GraphicLayer,
    index: Int,
    count: Int,
    onChange: ((GraphicLayer) -> GraphicLayer) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onPickImage: () -> Unit,
) {
    val typeLabel = when (layer.type) {
        GraphicLayerType.TEXT -> stringResource(R.string.isle_graphic_layer_text)
        GraphicLayerType.TIME -> stringResource(R.string.isle_graphic_layer_time)
        GraphicLayerType.IMAGE -> stringResource(R.string.isle_graphic_layer_image)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(Color(0x0FFFFFFF), RoundedCornerShape(14.dp))
            .border(1.dp, IsleColors.Stroke, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$typeLabel ${index + 1}",
                color = IsleColors.TextPrimary,
                fontSize = 12.5.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "↑",
                color = if (index > 0) IsleColors.TextSecondary else IsleColors.TextMuted,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .clickable(enabled = index > 0) { onMove(-1) },
            )
            Text(
                text = "↓",
                color = if (index < count - 1) IsleColors.TextSecondary else IsleColors.TextMuted,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .clickable(enabled = index < count - 1) { onMove(1) },
            )
            Text(
                text = stringResource(R.string.isle_graphic_remove),
                color = IsleColors.TextMuted,
                fontSize = 11.5.sp,
                modifier = Modifier.clickable(onClick = onRemove),
            )
        }

        when (layer.type) {
            GraphicLayerType.IMAGE -> {
                Text(
                    text = stringResource(
                        if (layer.imageUri == null) {
                            R.string.isle_graphic_pick_image
                        } else {
                            R.string.isle_graphic_image_selected
                        },
                    ),
                    color = IsleColors.Cyan,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clickable(onClick = onPickImage),
                )
            }

            else -> {
                BasicTextField(
                    value = layer.text,
                    onValueChange = { value ->
                        if (value.length <= 40) onChange { it.copy(text = value) }
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = IsleColors.TextPrimary, fontSize = 13.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(IsleColors.Cyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                // 常用表情：直接点选追加到文字末尾，省去切换输入法。
                if (layer.type == GraphicLayerType.TEXT) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GRAPHIC_EMOJIS.forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable {
                                        if (layer.text.length + emoji.length <= 40) {
                                            onChange { it.copy(text = it.text + emoji) }
                                        }
                                    },
                            )
                        }
                    }
                }
                // 时间函数占位符：对齐 PixelForge 的 Available Tokens，点一下追加。
                Text(
                    text = stringResource(R.string.isle_graphic_tokens_hint),
                    color = IsleColors.TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GRAPHIC_TOKENS.forEach { token ->
                        MiniChip(
                            label = token,
                            selected = false,
                            onClick = {
                                if (layer.text.length + token.length <= 40) {
                                    onChange { it.copy(text = it.text + token) }
                                }
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GRAPHIC_COLORS.forEach { color ->
                        val selected = color == layer.color
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color(0xFF000000.toInt() or color), CircleShape)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Color.White else Color(0x33FFFFFF),
                                    shape = CircleShape,
                                )
                                .clickable { onChange { it.copy(color = color) } },
                        )
                    }
                }
            }
        }

        // 区域对应 WLED 段落的 Start/Stop X/Y：决定这一层能点亮的灯珠矩形。
        LabeledSlider(
            label = stringResource(R.string.isle_graphic_region_start_x),
            glyph = Glyph.ChevronRight,
            value = layer.startXPercent / 100f,
            onChange = { value ->
                val next = (value * 100).toInt().coerceIn(0, layer.stopXPercent)
                onChange { it.copy(startXPercent = next) }
            },
        )
        LabeledSlider(
            label = stringResource(R.string.isle_graphic_region_stop_x),
            glyph = Glyph.ChevronRight,
            value = layer.stopXPercent / 100f,
            onChange = { value ->
                val next = (value * 100).toInt().coerceIn(layer.startXPercent, 100)
                onChange { it.copy(stopXPercent = next) }
            },
        )
        LabeledSlider(
            label = stringResource(R.string.isle_graphic_region_start_y),
            glyph = Glyph.Timer,
            value = layer.startYPercent / 100f,
            onChange = { value ->
                val next = (value * 100).toInt().coerceIn(0, layer.stopYPercent)
                onChange { it.copy(startYPercent = next) }
            },
        )
        LabeledSlider(
            label = stringResource(R.string.isle_graphic_region_stop_y),
            glyph = Glyph.Timer,
            value = layer.stopYPercent / 100f,
            onChange = { value ->
                val next = (value * 100).toInt().coerceIn(layer.startYPercent, 100)
                onChange { it.copy(stopYPercent = next) }
            },
        )
        LabeledSlider(
            label = stringResource(R.string.isle_graphic_size),
            glyph = Glyph.Text,
            value = layer.sizePercent / 100f,
            onChange = { value ->
                onChange { it.copy(sizePercent = (value * 100).toInt().coerceAtLeast(10)) }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.isle_graphic_rotate),
                color = IsleColors.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 2.dp),
            )
            GraphicRotation.entries.forEach { rotation ->
                MiniChip(
                    label = "${rotation.degrees}°",
                    selected = layer.rotation == rotation,
                    onClick = { onChange { it.copy(rotation = rotation) } },
                )
            }
            if (layer.type != GraphicLayerType.IMAGE) {
                MiniChip(
                    label = stringResource(R.string.isle_graphic_gradient),
                    selected = layer.gradientEnd != null,
                    onClick = {
                        onChange { it.copy(gradientEnd = if (it.gradientEnd == null) 0x8C7BFF else null) }
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniChip(
                label = stringResource(R.string.isle_graphic_mirror_x),
                selected = layer.mirrorX,
                onClick = { onChange { it.copy(mirrorX = !it.mirrorX) } },
            )
            MiniChip(
                label = stringResource(R.string.isle_graphic_mirror_y),
                selected = layer.mirrorY,
                onClick = { onChange { it.copy(mirrorY = !it.mirrorY) } },
            )
            MiniChip(
                label = stringResource(R.string.isle_graphic_transpose),
                selected = layer.transpose,
                onClick = { onChange { it.copy(transpose = !it.transpose) } },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.isle_graphic_blend),
                color = IsleColors.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 2.dp),
            )
            GraphicBlend.entries.forEach { blend ->
                MiniChip(
                    label = blendLabel(blend),
                    selected = layer.blend == blend,
                    onClick = { onChange { it.copy(blend = blend) } },
                )
            }
        }
    }
}

@Composable
private fun blendLabel(blend: GraphicBlend): String = stringResource(
    when (blend) {
        GraphicBlend.TOP -> R.string.isle_graphic_blend_top
        GraphicBlend.ADD -> R.string.isle_graphic_blend_add
        GraphicBlend.SCREEN -> R.string.isle_graphic_blend_screen
        GraphicBlend.MULTIPLY -> R.string.isle_graphic_blend_multiply
        GraphicBlend.LIGHTEN -> R.string.isle_graphic_blend_lighten
        GraphicBlend.DARKEN -> R.string.isle_graphic_blend_darken
        GraphicBlend.OVERLAY -> R.string.isle_graphic_blend_overlay
    },
)

@Composable
private fun MiniChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color(0xFF08090E) else IsleColors.TextSecondary,
        fontSize = 10.5.sp,
        modifier = Modifier
            .background(
                if (selected) IsleColors.Cyan else Color(0x0FFFFFFF),
                RoundedCornerShape(9.dp),
            )
            .border(
                1.dp,
                if (selected) IsleColors.Cyan else IsleColors.Stroke,
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
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
private fun PixelGrid(
    grid: List<Int>,
    cols: Int,
    rows: Int,
    onPaint: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = if (onPaint == null) {
            modifier
        } else {
            modifier.pointerInput(cols, rows, grid.size) {
                detectTapGestures { pos ->
                    val cell = minOf(size.width / cols, size.height / rows)
                    if (cell <= 0f) return@detectTapGestures
                    val offsetX = (size.width - cell * cols) / 2f
                    val offsetY = (size.height - cell * rows) / 2f
                    val col = ((pos.x - offsetX) / cell).toInt()
                    val row = ((pos.y - offsetY) / cell).toInt()
                    if (col in 0 until cols && row in 0 until rows) onPaint(row * cols + col)
                }
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
