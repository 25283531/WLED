package ca.cgagnier.wlednativeandroid.ui.device

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.effects.EffectInfo
import ca.cgagnier.wlednativeandroid.ui.effects.EffectMood
import ca.cgagnier.wlednativeandroid.ui.isle.DeviceTube
import ca.cgagnier.wlednativeandroid.ui.isle.EffectArtwork
import ca.cgagnier.wlednativeandroid.ui.isle.GlassCard
import ca.cgagnier.wlednativeandroid.ui.isle.GlowCircleButton
import ca.cgagnier.wlednativeandroid.ui.isle.Glyph
import ca.cgagnier.wlednativeandroid.ui.isle.GlyphIcon
import ca.cgagnier.wlednativeandroid.ui.isle.IsleBackdrop
import ca.cgagnier.wlednativeandroid.ui.isle.IsleColors
import ca.cgagnier.wlednativeandroid.ui.isle.IsleSlider
import ca.cgagnier.wlednativeandroid.ui.isle.SectionLabel
import ca.cgagnier.wlednativeandroid.ui.isle.StarButton
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceAdd.DeviceAdd
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

private const val PERCENT_MAX = 100
private const val MAX_CHANNEL_VALUE = 255
private val NIGHTLIGHT_OPTIONS = listOf(5, 10, 15, 30, 60, 120)

/**
 * 首页（设备）：高频日常主控台。
 *
 * 电源、亮度、四个律动模式、2×2 快捷卡片都在一屏内；多灯同步和定时收进顶部的两个微缩胶囊，
 * 点开是底部弹层 —— 它们不是每天都改的参数，不该占据主视觉。
 */
@Composable
fun DeviceScreen(modifier: Modifier = Modifier, viewModel: DeviceHomeViewModel = koinViewModel()) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedMac by viewModel.selectedMacAddress.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val mood by viewModel.mood.collectAsStateWithLifecycle()
    val syncEnabled by viewModel.syncEnabled.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val device = devices.firstOrNull { it.device.macAddress == selectedMac }
    LaunchedEffect(devices, selectedMac) {
        if (device == null) {
            val fallback = devices.firstOrNull { it.isOnline } ?: devices.firstOrNull()
            fallback?.let { viewModel.selectDevice(it.device.macAddress) }
        }
    }

    var showSyncSheet by remember { mutableStateOf(false) }
    var showTimerSheet by remember { mutableStateOf(false) }
    var showDeviceSheet by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    val state = device?.stateInfo?.state
    val isOn = state?.isOn ?: false
    val brightness = (state?.brightness ?: 128).coerceIn(0, MAX_CHANNEL_VALUE)
    val segment = state?.segment?.firstOrNull()
    val currentEffect = segment?.effect

    val quickCards = remember(favorites, mood) { viewModel.quickCards(favorites, mood) }
    val favoriteIds = remember(favorites) { favorites.map { it.effectId }.toSet() }

    IsleBackdrop(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        ) {
            item {
                HeaderRow(
                    devices = devices,
                    device = device,
                    isOn = isOn,
                    syncEnabled = syncEnabled,
                    onTogglePower = { device?.let { viewModel.setPower(it, !isOn) } },
                    onOpenSync = { showSyncSheet = true },
                    onOpenTimer = { showTimerSheet = true },
                    onOpenDevices = { showDeviceSheet = true },
                )
            }

            item {
                DeviceTube(
                    colors = remember(segment) { segment?.tubeColors().orEmpty() },
                    isOn = isOn,
                    brightness = brightness / MAX_CHANNEL_VALUE.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .padding(top = 4.dp),
                )
            }

            item {
                BrightnessRow(
                    value = brightness,
                    onCommit = { percent ->
                        device?.let { viewModel.setBrightness(it, percent * MAX_CHANNEL_VALUE / PERCENT_MAX) }
                    },
                )
            }

            item {
                SectionLabel(
                    text = stringResource(R.string.isle_rhythm_mode),
                    modifier = Modifier.padding(top = 18.dp),
                )
                MoodRow(selected = mood, onSelect = viewModel::selectMood)
            }

            item {
                SectionLabel(
                    text = stringResource(R.string.isle_quick_effects),
                    modifier = Modifier.padding(top = 20.dp),
                )
                QuickGrid(
                    cards = quickCards,
                    favoriteIds = favoriteIds,
                    currentEffect = currentEffect,
                    onApply = { effect ->
                        val target = device
                        if (target == null) {
                            Toast.makeText(context, R.string.effects_no_device, Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.applyEffect(target, effect.id)
                        }
                    },
                    onToggleFavorite = { effect ->
                        device?.let { viewModel.toggleFavorite(it, effect) }
                    },
                )
            }
        }
    }

    if (showSyncSheet) {
        BottomSheet(onDismiss = { showSyncSheet = false }) {
            SheetTitle(stringResource(R.string.isle_sync))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.isle_sync_title), color = IsleColors.TextPrimary, fontSize = 15.sp)
                    Text(
                        stringResource(R.string.isle_sync_desc),
                        color = IsleColors.TextMuted,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = syncEnabled,
                    onCheckedChange = viewModel::setSyncEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF08121A),
                        checkedTrackColor = IsleColors.Cyan,
                    ),
                )
            }
        }
    }

    if (showTimerSheet) {
        BottomSheet(onDismiss = { showTimerSheet = false }) {
            SheetTitle(stringResource(R.string.isle_nightlight_off))
            NIGHTLIGHT_OPTIONS.forEach { minutes ->
                SheetRow(
                    label = stringResource(R.string.isle_minutes, minutes),
                    onClick = {
                        device?.let { viewModel.startNightlight(it, minutes) }
                        showTimerSheet = false
                    },
                )
            }
            SheetRow(
                label = stringResource(R.string.isle_cancel_nightlight),
                onClick = {
                    device?.let { viewModel.cancelNightlight(it) }
                    showTimerSheet = false
                },
                tint = IsleColors.TextMuted,
            )
        }
    }

    if (showDeviceSheet) {
        BottomSheet(onDismiss = { showDeviceSheet = false }) {
            SheetTitle(stringResource(R.string.isle_devices))
            devices.forEach { item ->
                val name = item.device.displayName()
                SheetRow(
                    label = name,
                    subtitle = item.device.address,
                    selected = item.device.macAddress == selectedMac,
                    onClick = {
                        viewModel.selectDevice(item.device.macAddress)
                        showDeviceSheet = false
                    },
                )
            }
            SheetRow(
                label = stringResource(R.string.isle_add_device),
                onClick = {
                    showDeviceSheet = false
                    showAddDialog = true
                },
                tint = IsleColors.Cyan,
            )
        }
    }

    if (showAddDialog) {
        DeviceAdd(onDismissRequest = { showAddDialog = false })
    }
}

private fun Device.displayName(): String = customName.ifBlank { originalName.ifBlank { address } }

private fun Segment.tubeColors(): List<Color> = colors.orEmpty().mapNotNull { color ->
    if (color.size >= 3) Color(color[0], color[1], color[2]) else null
}

@Composable
private fun HeaderRow(
    devices: List<DeviceWithState>,
    device: DeviceWithState?,
    isOn: Boolean,
    syncEnabled: Boolean,
    onTogglePower: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenDevices),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp),
                color = IsleColors.TextPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (device?.isOnline == true) IsleColors.Online else IsleColors.TextMuted, CircleShape),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = when {
                        device == null -> stringResource(R.string.isle_status_no_device)
                        device.isOnline -> stringResource(R.string.isle_status_connected)
                        else -> stringResource(R.string.isle_status_offline)
                    },
                    color = IsleColors.TextSecondary,
                    fontSize = 12.sp,
                )
                if (device != null) {
                    Text(
                        text = "· ${device.device.displayName()}",
                        color = IsleColors.TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.isle_device_count, devices.size),
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        MiniAction(
            glyph = Glyph.Sync,
            label = stringResource(R.string.isle_sync),
            active = syncEnabled,
            onClick = onOpenSync,
        )
        Spacer(Modifier.width(10.dp))
        MiniAction(
            glyph = Glyph.Timer,
            label = stringResource(R.string.isle_timer),
            active = false,
            onClick = onOpenTimer,
        )
        Spacer(Modifier.width(12.dp))
        GlowCircleButton(
            onClick = onTogglePower,
            active = isOn,
            size = 44.dp,
        ) {
            GlyphIcon(
                glyph = Glyph.Power,
                size = 22.dp,
                tint = if (isOn) Color(0xFF0A0C12) else IsleColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun MiniAction(glyph: Glyph, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 30.dp)
                .background(
                    if (active) Brush.linearGradient(listOf(Color(0x3D5FE3E4), Color(0x3D8C7BFF)))
                    else Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x0AFFFFFF))),
                    RoundedCornerShape(999.dp),
                )
                .border(1.dp, if (active) Color(0x805FE3E4) else IsleColors.Stroke, RoundedCornerShape(999.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph = glyph, size = 16.dp, tint = if (active) IsleColors.Cyan else IsleColors.TextSecondary)
        }
        Text(
            text = label,
            color = IsleColors.TextMuted,
            fontSize = 9.5.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun BrightnessRow(value: Int, onCommit: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value / MAX_CHANNEL_VALUE.toFloat()) }
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = stringResource(R.string.isle_brightness),
            color = IsleColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(glyph = Glyph.Sparkle, size = 18.dp, tint = IsleColors.TextMuted)
            IsleSlider(
                value = local,
                onValueChange = { local = it },
                onValueChangeFinished = { onCommit((local * PERCENT_MAX).roundToInt()) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Text(
                text = "${(local * PERCENT_MAX).roundToInt()}%",
                color = IsleColors.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.width(40.dp),
            )
        }
    }
}

@Composable
private fun MoodRow(selected: EffectMood, onSelect: (EffectMood) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        EffectMood.entries.forEach { mood ->
            MoodItem(
                mood = mood,
                selected = mood == selected,
                onClick = { onSelect(mood) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MoodItem(mood: EffectMood, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glyph = when (mood) {
        EffectMood.AURORA -> Glyph.Aurora
        EffectMood.MUSIC -> Glyph.Music
        EffectMood.TEXT -> Glyph.Text
        EffectMood.SLEEP -> Glyph.Moon
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    if (selected) Brush.linearGradient(listOf(Color(0x335FE3E4), Color(0x338C7BFF)))
                    else Brush.linearGradient(listOf(Color(0x0FFFFFFF), Color(0x08FFFFFF))),
                    RoundedCornerShape(18.dp),
                )
                .border(
                    width = if (selected) 1.4.dp else 1.dp,
                    brush = if (selected) Brush.linearGradient(IsleColors.Accent) else Brush.linearGradient(
                        listOf(IsleColors.Stroke, IsleColors.Stroke),
                    ),
                    shape = RoundedCornerShape(18.dp),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = glyph,
                size = 22.dp,
                tint = if (selected) IsleColors.Cyan else IsleColors.TextSecondary,
            )
        }
        Text(
            text = stringResource(mood.labelRes()),
            color = if (selected) IsleColors.TextPrimary else IsleColors.TextMuted,
            fontSize = 10.5.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun EffectMood.labelRes(): Int = when (this) {
    EffectMood.AURORA -> R.string.isle_mood_aurora
    EffectMood.MUSIC -> R.string.isle_mood_music
    EffectMood.TEXT -> R.string.isle_mood_text
    EffectMood.SLEEP -> R.string.isle_mood_sleep
}

@Composable
private fun QuickGrid(
    cards: List<EffectInfo>,
    favoriteIds: Set<Int>,
    currentEffect: Int?,
    onApply: (EffectInfo) -> Unit,
    onToggleFavorite: (EffectInfo) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { effect ->
                    QuickCard(
                        effect = effect,
                        isFavorite = effect.id in favoriteIds,
                        isActive = effect.id == currentEffect,
                        onClick = { onApply(effect) },
                        onToggleFavorite = { onToggleFavorite(effect) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickCard(
    effect: EffectInfo,
    isFavorite: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1.55f)
            .background(Color(0xFF101218), RoundedCornerShape(20.dp))
            .border(
                width = if (isActive) 1.6.dp else 1.dp,
                brush = if (isActive) Brush.linearGradient(IsleColors.Accent)
                else Brush.linearGradient(listOf(IsleColors.Stroke, IsleColors.Stroke)),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick),
    ) {
        // 画面压在底部渐隐，保证文字在任何灯效上都读得清
        EffectArtwork(effect = effect, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x40000000), Color(0xD9030408)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        ) {
            Text(
                text = effect.nameZh,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (effect.hasChineseName) {
                Text(
                    text = effect.name,
                    color = Color(0xFFB9C0CC),
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
                .padding(8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
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
            content()
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        color = IsleColors.TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SheetRow(
    label: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    selected: Boolean = false,
    tint: Color = IsleColors.TextPrimary,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        cornerRadius = 16.dp,
        padding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = if (selected) IsleColors.Cyan else tint, fontSize = 15.sp)
                subtitle?.let {
                    Text(it, color = IsleColors.TextMuted, fontSize = 11.sp)
                }
            }
            if (selected) {
                GlyphIcon(glyph = Glyph.Check, size = 16.dp, tint = IsleColors.Cyan)
            }
        }
    }
}
