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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import ca.cgagnier.wlednativeandroid.ui.effects.AudioSpiral
import ca.cgagnier.wlednativeandroid.ui.effects.BassLava
import ca.cgagnier.wlednativeandroid.ui.effects.CuratedEffects
import ca.cgagnier.wlednativeandroid.ui.effects.EffectCatalog
import ca.cgagnier.wlednativeandroid.ui.effects.EffectInfo
import ca.cgagnier.wlednativeandroid.ui.effects.EffectMood
import ca.cgagnier.wlednativeandroid.ui.effects.FourColorPalette
import ca.cgagnier.wlednativeandroid.ui.effects.FrequencyPixels
import ca.cgagnier.wlednativeandroid.ui.effects.PaletteCatalog
import ca.cgagnier.wlednativeandroid.ui.effects.PaletteInfo
import ca.cgagnier.wlednativeandroid.ui.effects.SonicPulse
import ca.cgagnier.wlednativeandroid.ui.effects.SpectrumLamp
import ca.cgagnier.wlednativeandroid.ui.effects.StageLight
import ca.cgagnier.wlednativeandroid.ui.effects.TriBreath
import ca.cgagnier.wlednativeandroid.ui.effects.TriColorPalette
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
import ca.cgagnier.wlednativeandroid.ui.isle.device.DeviceAppearance
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceAdd.DeviceAdd
import ca.cgagnier.wlednativeandroid.util.CHANNEL_MAX
import ca.cgagnier.wlednativeandroid.util.channelToNormalized
import ca.cgagnier.wlednativeandroid.util.normalizedToChannel
import ca.cgagnier.wlednativeandroid.util.normalizedToPercent
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

private val NIGHTLIGHT_OPTIONS = listOf(5, 10, 15, 30, 60, 120)

/**
 * 参数调节区的预设色。
 *
 * 前三位刻意取产品自己的招牌色（亮青 / 蔚蓝 / 紫罗兰），与灯效 188「极光之舞」
 * 和 App 图标同源；后几位补足暖色与绿，避免只能用冷色。
 */
private val PRESET_COLORS = listOf(
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

/** 频谱像素的 4 个频段显示名，顺序与固件 FFT 分组一致（低 -> 高）。 */
private val FREQUENCY_BAND_LABELS = intArrayOf(
    R.string.isle_freq_band_low,
    R.string.isle_freq_band_low_mid,
    R.string.isle_freq_band_high_mid,
    R.string.isle_freq_band_high,
)

/** 舞台灯与声波脉冲共用的 3 个频段显示名，顺序与固件一致（低 -> 中 -> 高）。 */
private val STAGE_BAND_LABELS = intArrayOf(
    R.string.isle_stage_band_low,
    R.string.isle_stage_band_mid,
    R.string.isle_stage_band_high,
)

/**
 * 固件调色板 2「* Color 1」：整条灯带只取 segment 的第一个颜色。
 * App 把它包装成「单色」模板放进调色板列表，选中后即可在上方色块里换色，
 * 对任何依赖调色板的灯效都生效。
 */
private const val SOLID_PALETTE_ID = 2

/** `0xRRGGBB` 还原成 Compose 的颜色。显式拆分量，避免 `Color(Int)` 的 ARGB 歧义。 */
private fun packedToColor(packed: Int): Color = Color(
    red = (packed shr 16) and 0xFF,
    green = (packed shr 8) and 0xFF,
    blue = packed and 0xFF,
)

/** Compose 颜色压回 `0xRRGGBB`，与 `col[]` 协议字段一致。 */
private fun packedRgb(color: Color): Int = color.toArgb() and 0xFFFFFF

/**
 * 首页（设备）：高频日常主控台。
 *
 * 电源、亮度、三个律动模式 + 睡眠夜灯、2×2 快捷卡片都在一屏内；
 * 多灯同步和定时收进顶部的两个微缩胶囊，点开是底部弹层 —— 它们不是每天都改的参数，不该占据主视觉。
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
    var tuningExpanded by remember { mutableStateOf(false) }
    var frequencyColorBand by remember { mutableStateOf<Int?>(null) }
    var stageColorBand by remember { mutableStateOf<Int?>(null) }
    var triBreathColorBand by remember { mutableStateOf<Int?>(null) }
    var sonicColorBand by remember { mutableStateOf<Int?>(null) }
    var audioSpiralColorChain by remember { mutableStateOf<Int?>(null) }
    var spectrumColorBand by remember { mutableStateOf<Int?>(null) }

    val state = device?.stateInfo?.state
    val isOn = state?.isOn ?: false
    val brightness = (state?.brightness ?: 128).coerceIn(0, CHANNEL_MAX)
    val segment = state?.segment?.firstOrNull()
    val currentEffect = segment?.effect
    val frequencyEffectId = remember { FrequencyPixels.effectId }
    val frequencyPixelsActive = currentEffect != null && currentEffect == frequencyEffectId
    val frequencyColors = remember(segment) { FrequencyPixels.currentColors(segment) }
    // 线型频谱（PS GEQ 1D）只跟随声量等级，速度/强度不参与运算，设备页隐藏这两个滑块
    val linearSpectrumId = remember { EffectCatalog.byName("PS GEQ 1D")?.id }
    val linearSpectrumActive = currentEffect != null && currentEffect == linearSpectrumId
    // 舞台灯：频段选择与配色由 App 参数面板管理，速度/强度同样不参与运算
    val stageLightId = remember { StageLight.effectId }
    val stageLightActive = currentEffect != null && currentEffect == stageLightId
    val stageMask = remember(segment) { StageLight.currentMask(segment) }
    val stageColors = remember(segment) { StageLight.currentColors(segment) }
    // 三频呼吸：三段等分，颜色由 App 参数面板管理，速度/强度不参与运算
    val triBreathId = remember { TriBreath.effectId }
    val triBreathActive = currentEffect != null && currentEffect == triBreathId
    val triBreathColors = remember(segment) { TriBreath.currentColors(segment) }
    // 声波脉冲：关联频段与配色由 App 参数面板管理，速度只影响声波推进速度（保留滑块）
    val sonicPulseId = remember { SonicPulse.effectId }
    val sonicPulseActive = currentEffect != null && currentEffect == sonicPulseId
    val sonicMask = remember(segment) { SonicPulse.currentMask(segment) }
    val sonicColors = remember(segment) { SonicPulse.currentColors(segment) }
    // 音频螺旋：两条主链颜色由 App 参数面板管理；速度控制转速、强度控制填充带亮度
    val audioSpiralId = remember { AudioSpiral.effectId }
    val audioSpiralActive = currentEffect != null && currentEffect == audioSpiralId
    val audioSpiralColors = remember(segment) { AudioSpiral.currentColors(segment) }
    // 低频熔岩：内部使用固定熔岩色带，只暴露速度（漂移）与强度（泵浦增益）
    val bassLavaId = remember { BassLava.effectId }
    val bassLavaActive = currentEffect != null && currentEffect == bassLavaId
    // 频谱灯：三频配色由 App 参数面板管理，列宽由固件按百分比镜像分配
    val spectrumLampId = remember { SpectrumLamp.effectId }
    val spectrumLampActive = currentEffect != null && currentEffect == spectrumLampId
    val spectrumLampColors = remember(segment) { SpectrumLamp.currentColors(segment) }

    // 律动模式跟随当前灯效：从灯效库或快捷卡片切到音律灯效时，胶囊同步高亮「音乐律动」
    LaunchedEffect(currentEffect) {
        viewModel.syncMoodWithEffect(currentEffect)
    }

    val nightlight = state?.nightlight
    val nightlightActive = nightlight?.isOn == true
    val nightlightRemaining = rememberNightlightRemaining(nightlight?.remainingTime)

    LaunchedEffect(Unit) {
        viewModel.nightlightEvents.collect { event ->
            val message = when (event) {
                is NightlightEvent.Started -> context.getString(R.string.isle_nightlight_started, event.minutes)
                NightlightEvent.Cancelled -> context.getString(R.string.isle_nightlight_cancelled)
                NightlightEvent.Failed -> context.getString(R.string.isle_nightlight_failed)
                NightlightEvent.LightOff -> context.getString(R.string.isle_nightlight_light_off)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

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
                    nightlightActive = nightlightActive,
                    onTogglePower = { device?.let { viewModel.setPower(it, !isOn) } },
                    onOpenSync = { showSyncSheet = true },
                    onOpenTimer = { showTimerSheet = true },
                    onOpenDevices = { showDeviceSheet = true },
                )
            }

            item {
                DeviceAppearance(
                    product = device?.stateInfo?.info?.product,
                    effectPreview = remember(currentEffect) { EffectCatalog.find(currentEffect)?.preview },
                    colors = remember(segment) { segment?.tubeColors().orEmpty() },
                    isOn = isOn,
                    brightness = channelToNormalized(brightness),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(top = 4.dp),
                )
            }

            item {
                BrightnessRow(
                    value = brightness,
                    onCommit = { channel ->
                        device?.let { viewModel.setBrightness(it, channel) }
                    },
                )
            }

            item {
                EffectTuning(
                    expanded = tuningExpanded,
                    speed = segment?.effectSpeed ?: 0,
                    intensity = segment?.effectIntensity ?: 0,
                    palette = segment?.palette ?: 0,
                    colors = remember(segment) { segment?.tubeColors().orEmpty() },
                    frequencyMode = frequencyPixelsActive,
                    stageMode = stageLightActive,
                    triBreathMode = triBreathActive,
                    sonicMode = sonicPulseActive,
                    audioSpiralMode = audioSpiralActive,
                    bassLavaMode = bassLavaActive,
                    spectrumLampMode = spectrumLampActive,
                    slidersHidden = linearSpectrumActive || stageLightActive ||
                        triBreathActive || sonicPulseActive || spectrumLampActive,
                    frequencyColors = frequencyColors,
                    stageMask = stageMask,
                    stageColors = stageColors,
                    triBreathColors = triBreathColors,
                    sonicMask = sonicMask,
                    sonicColors = sonicColors,
                    audioSpiralColors = audioSpiralColors,
                    spectrumLampColors = spectrumLampColors,
                    onToggle = { tuningExpanded = !tuningExpanded },
                    onSpeed = { channel ->
                        device?.let { viewModel.setSpeed(it, channel) }
                    },
                    onIntensity = { channel ->
                        device?.let { viewModel.setIntensity(it, channel) }
                    },
                    onPalette = { paletteId ->
                        device?.let { viewModel.setPalette(it, paletteId) }
                    },
                    onColor = { packed ->
                        device?.let { viewModel.setPrimaryColor(it, packed) }
                    },
                    onFrequencyPalette = { palette ->
                        device?.let { viewModel.applyFrequencyPalette(it, palette) }
                    },
                    onEditFrequencyColor = { band -> frequencyColorBand = band },
                    onStageBand = { band ->
                        device?.let { viewModel.setStageBands(it, StageLight.toggleBand(stageMask, band)) }
                    },
                    onEditStageColor = { band -> stageColorBand = band },
                    onTriBreathPalette = { palette ->
                        device?.let { viewModel.applyTriBreathPalette(it, palette) }
                    },
                    onEditTriBreathColor = { band -> triBreathColorBand = band },
                    onSonicBand = { band ->
                        device?.let { viewModel.setSonicBands(it, SonicPulse.toggleBand(sonicMask, band)) }
                    },
                    onEditSonicColor = { band -> sonicColorBand = band },
                    onEditAudioSpiralColor = { chain -> audioSpiralColorChain = chain },
                    onSpectrumPalette = { palette ->
                        device?.let { viewModel.applyTriBreathPalette(it, palette) }
                    },
                    onEditSpectrumColor = { band -> spectrumColorBand = band },
                )
            }

            item {
                SectionLabel(
                    text = stringResource(R.string.isle_rhythm_mode),
                    modifier = Modifier.padding(top = 18.dp),
                )
                MoodRow(
                    selected = mood,
                    onSelect = viewModel::selectMood,
                    onSleepLight = {
                        val target = device
                        if (target == null) {
                            Toast.makeText(context, R.string.effects_no_device, Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.startSleepLight(target)
                            Toast.makeText(context, R.string.isle_sleep_light_applied, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
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
                    onApply = { card ->
                        val target = device
                        if (target == null) {
                            Toast.makeText(context, R.string.effects_no_device, Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.applyEffect(target, card.effect.id, card.favorite)
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
            if (nightlightRemaining != null) {
                Text(
                    text = stringResource(
                        R.string.isle_nightlight_remaining,
                        formatCountdown(nightlightRemaining),
                    ),
                    color = IsleColors.Cyan,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            NIGHTLIGHT_OPTIONS.forEach { minutes ->
                SheetRow(
                    label = stringResource(R.string.isle_minutes, minutes),
                    selected = nightlightActive && nightlight?.duration == minutes,
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

    frequencyColorBand?.let { band ->
        FrequencyColorSheet(
            title = stringResource(
                R.string.isle_freq_color_title,
                stringResource(FREQUENCY_BAND_LABELS[band]),
            ),
            initial = frequencyColors.getOrElse(band) { 0xFFFFFF },
            onDismiss = { frequencyColorBand = null },
            onPick = { packed ->
                device?.let { viewModel.setFrequencyColor(it, band, packed) }
            },
        )
    }

    stageColorBand?.let { band ->
        FrequencyColorSheet(
            title = stringResource(
                R.string.isle_freq_color_title,
                stringResource(STAGE_BAND_LABELS[band]),
            ),
            initial = stageColors.getOrElse(band) { StageLight.DEFAULT_COLOR },
            onDismiss = { stageColorBand = null },
            onPick = { packed ->
                device?.let { viewModel.setStageBandColor(it, band, packed) }
            },
        )
    }

    triBreathColorBand?.let { band ->
        FrequencyColorSheet(
            title = stringResource(
                R.string.isle_freq_color_title,
                stringResource(STAGE_BAND_LABELS[band]),
            ),
            initial = triBreathColors.getOrElse(band) { TriBreath.DEFAULT_COLORS[band] },
            onDismiss = { triBreathColorBand = null },
            onPick = { packed ->
                device?.let { viewModel.setTriBreathColor(it, band, packed) }
            },
        )
    }

    sonicColorBand?.let { band ->
        FrequencyColorSheet(
            title = stringResource(
                R.string.isle_freq_color_title,
                stringResource(STAGE_BAND_LABELS[band]),
            ),
            initial = sonicColors.getOrElse(band) { SonicPulse.DEFAULT_COLORS[band] },
            onDismiss = { sonicColorBand = null },
            onPick = { packed ->
                device?.let { viewModel.setSonicBandColor(it, band, packed) }
            },
        )
    }

    audioSpiralColorChain?.let { chain ->
        FrequencyColorSheet(
            title = stringResource(
                R.string.isle_freq_color_title,
                stringResource(
                    if (chain == 0) R.string.isle_spiral_chain_a else R.string.isle_spiral_chain_b,
                ),
            ),
            initial = audioSpiralColors.getOrElse(chain) { AudioSpiral.DEFAULT_COLORS[chain] },
            onDismiss = { audioSpiralColorChain = null },
            onPick = { packed ->
                device?.let { viewModel.setAudioSpiralColor(it, chain, packed) }
            },
        )
    }

    spectrumColorBand?.let { band ->
        FrequencyColorSheet(
            title = stringResource(
                R.string.isle_freq_color_title,
                stringResource(STAGE_BAND_LABELS[band]),
            ),
            initial = spectrumLampColors.getOrElse(band) { SpectrumLamp.DEFAULT_COLORS[band] },
            onDismiss = { spectrumColorBand = null },
            onPick = { packed ->
                device?.let { viewModel.setSpectrumLampColor(it, band, packed) }
            },
        )
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
    nightlightActive: Boolean,
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
            active = nightlightActive,
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
    var local by remember(value) { mutableFloatStateOf(channelToNormalized(value)) }
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
                onValueChangeFinished = { onCommit(normalizedToChannel(local)) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Text(
                text = "${normalizedToPercent(local)}%",
                color = IsleColors.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.width(40.dp),
            )
        }
    }
}

/**
 * 灯效参数：速度、强度、颜色。
 *
 * 折叠时只占一行（标题 + 当前速度/强度摘要），展开后才露出滑块与配色 ——
 * 设备页要保持「一屏一目的」，参数调节是低频微调，不该抢走主控台的视觉重心。
 */
@Composable
private fun EffectTuning(
    expanded: Boolean,
    speed: Int,
    intensity: Int,
    palette: Int,
    colors: List<Color>,
    frequencyMode: Boolean,
    stageMode: Boolean,
    triBreathMode: Boolean,
    sonicMode: Boolean,
    audioSpiralMode: Boolean,
    bassLavaMode: Boolean,
    spectrumLampMode: Boolean,
    slidersHidden: Boolean,
    frequencyColors: List<Int>,
    stageMask: Int,
    stageColors: List<Int>,
    triBreathColors: List<Int>,
    sonicMask: Int,
    sonicColors: List<Int>,
    audioSpiralColors: List<Int>,
    spectrumLampColors: List<Int>,
    onToggle: () -> Unit,
    onSpeed: (Int) -> Unit,
    onIntensity: (Int) -> Unit,
    onPalette: (Int) -> Unit,
    onColor: (Int) -> Unit,
    onFrequencyPalette: (FourColorPalette) -> Unit,
    onEditFrequencyColor: (Int) -> Unit,
    onStageBand: (Int) -> Unit,
    onEditStageColor: (Int) -> Unit,
    onTriBreathPalette: (TriColorPalette) -> Unit,
    onEditTriBreathColor: (Int) -> Unit,
    onSonicBand: (Int) -> Unit,
    onEditSonicColor: (Int) -> Unit,
    onEditAudioSpiralColor: (Int) -> Unit,
    onSpectrumPalette: (TriColorPalette) -> Unit,
    onEditSpectrumColor: (Int) -> Unit,
) {
    var localSpeed by remember(speed) { mutableFloatStateOf(channelToNormalized(speed)) }
    var localIntensity by remember(intensity) {
        mutableFloatStateOf(channelToNormalized(intensity))
    }

    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
        ) {
            Text(
                text = stringResource(R.string.isle_effect_tuning),
                color = IsleColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    expanded -> stringResource(R.string.isle_effect_tuning_collapse)
                    frequencyMode -> stringResource(R.string.isle_freq_tuning_summary)
                    triBreathMode -> stringResource(R.string.isle_triband_tuning_summary)
                    spectrumLampMode -> stringResource(R.string.isle_spectrum_tuning_summary)
                    sonicMode -> stringResource(R.string.isle_sonic_tuning_summary, sonicMask.countOneBits())
                    stageMode -> stringResource(R.string.isle_stage_tuning_summary, stageMask.countOneBits())
                    audioSpiralMode -> stringResource(R.string.isle_spiral_tuning_summary)
                    slidersHidden -> stringResource(R.string.isle_color_tuning_summary)
                    else -> stringResource(
                        R.string.isle_effect_tuning_summary,
                        normalizedToPercent(localSpeed),
                        normalizedToPercent(localIntensity),
                    )
                },
                color = IsleColors.TextMuted,
                fontSize = 11.5.sp,
            )
        }

        if (expanded) {
            GlassCard(modifier = Modifier.padding(top = 10.dp)) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (frequencyMode) {
                        FrequencyColorEditor(
                            colors = frequencyColors,
                            onEdit = onEditFrequencyColor,
                            onPalette = onFrequencyPalette,
                        )
                    } else if (triBreathMode) {
                        TriBandColorEditor(
                            colors = triBreathColors,
                            defaultColors = TriBreath.DEFAULT_COLORS,
                            palettes = TriBreath.palettes,
                            matched = remember(triBreathColors) {
                                TriBreath.matchPalette(triBreathColors)
                            },
                            onEdit = onEditTriBreathColor,
                            onPalette = onTriBreathPalette,
                        )
                    } else if (spectrumLampMode) {
                        TriBandColorEditor(
                            colors = spectrumLampColors,
                            defaultColors = SpectrumLamp.DEFAULT_COLORS,
                            palettes = SpectrumLamp.palettes,
                            matched = remember(spectrumLampColors) {
                                SpectrumLamp.matchPalette(spectrumLampColors)
                            },
                            onEdit = onEditSpectrumColor,
                            onPalette = onSpectrumPalette,
                        )
                    } else if (stageMode) {
                        BandColorEditor(
                            mask = stageMask,
                            colors = stageColors,
                            defaultColors = List(StageLight.BAND_COUNT) { StageLight.DEFAULT_COLOR },
                            hintRes = R.string.isle_stage_hint,
                            onToggleBand = onStageBand,
                            onEditColor = onEditStageColor,
                        )
                    } else if (sonicMode) {
                        BandColorEditor(
                            mask = sonicMask,
                            colors = sonicColors,
                            defaultColors = SonicPulse.DEFAULT_COLORS,
                            hintRes = R.string.isle_sonic_hint,
                            onToggleBand = onSonicBand,
                            onEditColor = onEditSonicColor,
                        )
                    } else if (audioSpiralMode) {
                        TuningSlider(
                            label = stringResource(R.string.isle_spiral_speed),
                            value = localSpeed,
                            onValueChange = { localSpeed = it },
                            onCommit = { onSpeed(normalizedToChannel(localSpeed)) },
                        )
                        TuningSlider(
                            label = stringResource(R.string.isle_spiral_intensity),
                            value = localIntensity,
                            onValueChange = { localIntensity = it },
                            onCommit = { onIntensity(normalizedToChannel(localIntensity)) },
                        )
                        Text(
                            text = stringResource(R.string.isle_spiral_hint),
                            color = IsleColors.TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        SpiralColorEditor(
                            colors = audioSpiralColors,
                            onEdit = onEditAudioSpiralColor,
                        )
                    } else if (bassLavaMode) {
                        TuningSlider(
                            label = stringResource(R.string.isle_basslava_speed),
                            value = localSpeed,
                            onValueChange = { localSpeed = it },
                            onCommit = { onSpeed(normalizedToChannel(localSpeed)) },
                        )
                        TuningSlider(
                            label = stringResource(R.string.isle_basslava_intensity),
                            value = localIntensity,
                            onValueChange = { localIntensity = it },
                            onCommit = { onIntensity(normalizedToChannel(localIntensity)) },
                        )
                        Text(
                            text = stringResource(R.string.isle_basslava_hint),
                            color = IsleColors.TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    } else {
                        if (!slidersHidden) {
                            TuningSlider(
                                label = stringResource(R.string.isle_effect_speed),
                                value = localSpeed,
                                onValueChange = { localSpeed = it },
                                onCommit = {
                                    onSpeed(normalizedToChannel(localSpeed))
                                },
                            )
                            TuningSlider(
                                label = stringResource(R.string.isle_effect_intensity),
                                value = localIntensity,
                                onValueChange = { localIntensity = it },
                                onCommit = {
                                    onIntensity(normalizedToChannel(localIntensity))
                                },
                            )
                        }

                        Text(
                            text = stringResource(
                                if (palette == SOLID_PALETTE_ID) {
                                    R.string.isle_solid_color
                                } else {
                                    R.string.isle_effect_color
                                },
                            ),
                            color = IsleColors.TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            colors.take(3).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(color, CircleShape)
                                        .border(1.dp, IsleColors.StrokeStrong, CircleShape),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            PRESET_COLORS.forEach { packed ->
                                val rgb = packedToColor(packed)
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .background(rgb, CircleShape)
                                        .border(1.dp, IsleColors.StrokeStrong, CircleShape)
                                        .clickable { onColor(packed) },
                                )
                            }
                        }

                        PalettePicker(
                            selected = palette,
                            primaryPacked = colors.firstOrNull()?.let { packedRgb(it) } ?: 0xFFFFFF,
                            onSelect = onPalette,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 调色板选择器：一排横向滚动的渐变色块。
 *
 * 每个色块画的是该调色板**真实的色标**（从固件 `palettes.cpp` 提取，
 * 见 `tools/gen_palette_catalog.py`），所以用户点之前就能看见它长什么样，
 * 而不是面对一串编号。固件里「只取当前颜色槽位」的那几个动态调色板
 * 没有固定颜色，用中性底纹示意，名字仍会显示在标题右侧。
 */
@Composable
private fun PalettePicker(
    selected: Int,
    primaryPacked: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palettes = remember { PaletteCatalog.all }
    val solidName = stringResource(R.string.isle_palette_solid)
    // 单色模板：取固件的「* Color 1」，色块直接用当前主色渲染，选中后在上方换色
    val solid = remember(primaryPacked, solidName) {
        PaletteInfo(
            id = SOLID_PALETTE_ID,
            name = solidName,
            isStatic = true,
            colors = listOf(primaryPacked, primaryPacked),
        )
    }
    val current = if (selected == SOLID_PALETTE_ID) solid else palettes.firstOrNull { it.id == selected }
    val visiblePalettes = remember(palettes) { palettes.filterNot { it.id == SOLID_PALETTE_ID } }
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.isle_palette),
                color = IsleColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = current?.canonicalName ?: "-",
                color = IsleColors.TextMuted,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaletteSwatch(
                palette = solid,
                selected = selected == SOLID_PALETTE_ID,
                onClick = { onSelect(SOLID_PALETTE_ID) },
            )
            visiblePalettes.forEach { palette ->
                PaletteSwatch(
                    palette = palette,
                    selected = palette.id == selected,
                    onClick = { onSelect(palette.id) },
                )
            }
        }
    }
}

@Composable
private fun PaletteSwatch(
    palette: PaletteInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 30.dp)
            .clip(shape)
            .background(
                // 有固定色标才画渐变；`*` 系列调色板没有色标，用中性底纹示意，避免出现空白方框
                if (palette.hasPreview && palette.colors.isNotEmpty()) {
                    Brush.horizontalGradient(palette.colors.map { Color(0xFF000000.toInt() or it) })
                } else {
                    Brush.horizontalGradient(listOf(IsleColors.SurfaceHigh, IsleColors.Surface))
                }
            )
            .border(
                width = if (selected) 1.8.dp else 1.dp,
                color = if (selected) IsleColors.Cyan else IsleColors.StrokeStrong,
                shape = shape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun FrequencyColorEditor(
    colors: List<Int>,
    onEdit: (Int) -> Unit,
    onPalette: (FourColorPalette) -> Unit,
) {
    val matched = remember(colors) { FrequencyPixels.matchPalette(colors) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.isle_freq_colors),
            color = IsleColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = matched?.let { stringResource(it.nameRes) }
                ?: stringResource(R.string.isle_freq_custom),
            color = IsleColors.TextMuted,
            fontSize = 11.5.sp,
        )
    }
    FREQUENCY_BAND_LABELS.forEachIndexed { band, labelRes ->
        val packed = colors.getOrElse(band) { 0 }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onEdit(band) }
                .padding(vertical = 7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(packedToColor(packed), CircleShape)
                    .border(1.dp, IsleColors.StrokeStrong, CircleShape),
            )
            Text(
                text = stringResource(labelRes),
                color = IsleColors.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            )
            Text(
                text = String.format("#%06X", packed and 0xFFFFFF),
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
            )
        }
    }

    Text(
        text = stringResource(R.string.isle_freq_templates),
        color = IsleColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FrequencyPixels.palettes.forEach { palette ->
            FrequencyPaletteSwatch(
                palette = palette,
                selected = palette == matched,
                onClick = { onPalette(palette) },
            )
        }
    }
}

/**
 * 三频配色面板：为低频 / 中频 / 高频三段各取一色，或直接套用三色模板。
 *
 * 三频呼吸与频谱灯共用这套交互；频段顺序与固件一致（低 / 中 / 高），
 * 颜色分别写进 `col[0..2]`，未设置时按 [defaultColors] 显示。
 */
@Composable
private fun TriBandColorEditor(
    colors: List<Int>,
    defaultColors: List<Int>,
    palettes: List<TriColorPalette>,
    matched: TriColorPalette?,
    onEdit: (Int) -> Unit,
    onPalette: (TriColorPalette) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.isle_freq_colors),
            color = IsleColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = matched?.let { stringResource(it.nameRes) }
                ?: stringResource(R.string.isle_freq_custom),
            color = IsleColors.TextMuted,
            fontSize = 11.5.sp,
        )
    }
    STAGE_BAND_LABELS.forEachIndexed { band, labelRes ->
        val packed = colors.getOrElse(band) { defaultColors.getOrElse(band) { 0xFFFFFF } }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onEdit(band) }
                .padding(vertical = 7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(packedToColor(packed), CircleShape)
                    .border(1.dp, IsleColors.StrokeStrong, CircleShape),
            )
            Text(
                text = stringResource(labelRes),
                color = IsleColors.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            )
            Text(
                text = String.format("#%06X", packed and 0xFFFFFF),
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
            )
        }
    }

    Text(
        text = stringResource(R.string.isle_triband_templates),
        color = IsleColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        palettes.forEach { palette ->
            TriColorPaletteSwatch(
                palette = palette,
                selected = palette == matched,
                onClick = { onPalette(palette) },
            )
        }
    }
}

/**
 * 音频螺旋参数：两条主链各取一色。
 *
 * `col[0]` 为 A 链、`col[1]` 为 B 链，与固件 `mode_audiospiral()` 一致；
 * 中间的填充带颜色由音频实时决定，不需要用户配置。
 */
@Composable
private fun SpiralColorEditor(
    colors: List<Int>,
    onEdit: (Int) -> Unit,
) {
    val labels = intArrayOf(R.string.isle_spiral_chain_a, R.string.isle_spiral_chain_b)
    Text(
        text = stringResource(R.string.isle_spiral_colors),
        color = IsleColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
    labels.forEachIndexed { chain, labelRes ->
        val packed = colors.getOrElse(chain) { AudioSpiral.DEFAULT_COLORS[chain] }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onEdit(chain) }
                .padding(vertical = 7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(packedToColor(packed), CircleShape)
                    .border(1.dp, IsleColors.StrokeStrong, CircleShape),
            )
            Text(
                text = stringResource(labelRes),
                color = IsleColors.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            )
            Text(
                text = String.format("#%06X", packed and 0xFFFFFF),
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun TriColorPaletteSwatch(
    palette: TriColorPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(7.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .border(
                    width = if (selected) 1.8.dp else 1.dp,
                    color = if (selected) IsleColors.Cyan else IsleColors.StrokeStrong,
                    shape = shape,
                ),
        ) {
            palette.colors.forEach { packed ->
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 28.dp)
                        .background(packedToColor(packed)),
                )
            }
        }
        Text(
            text = stringResource(palette.nameRes),
            color = if (selected) IsleColors.Cyan else IsleColors.TextMuted,
            fontSize = 10.5.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * 多频段参数面板：选择关联的频段（至少一个），再为每个关联频段单独取色。
 *
 * 舞台灯与声波脉冲共用这套交互；频段顺序与固件一致（低 / 中 / 高），
 * 颜色分别写进 `col[0..2]`，未设置时按 [defaultColors] 展示。
 */
@Composable
private fun BandColorEditor(
    mask: Int,
    colors: List<Int>,
    defaultColors: List<Int>,
    hintRes: Int,
    onToggleBand: (Int) -> Unit,
    onEditColor: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.isle_stage_bands),
            color = IsleColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.isle_stage_band_count, mask.countOneBits()),
            color = IsleColors.TextMuted,
            fontSize = 11.5.sp,
        )
    }
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        STAGE_BAND_LABELS.forEachIndexed { band, labelRes ->
            val selected = (mask and (1 shl band)) != 0
            val shape = RoundedCornerShape(9.dp)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(if (selected) IsleColors.Cyan else IsleColors.SurfaceHigh)
                    .border(
                        width = 1.dp,
                        color = if (selected) IsleColors.Cyan else IsleColors.StrokeStrong,
                        shape = shape,
                    )
                    .clickable { onToggleBand(band) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(labelRes),
                    color = if (selected) IsleColors.Background else IsleColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }

    Text(
        text = stringResource(R.string.isle_freq_colors),
        color = IsleColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
    STAGE_BAND_LABELS.forEachIndexed { band, labelRes ->
        if ((mask and (1 shl band)) == 0) return@forEachIndexed
        val packed = colors.getOrElse(band) { defaultColors.getOrElse(band) { 0xFFFFFF } }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onEditColor(band) }
                .padding(vertical = 7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(packedToColor(packed), CircleShape)
                    .border(1.dp, IsleColors.StrokeStrong, CircleShape),
            )
            Text(
                text = stringResource(labelRes),
                color = IsleColors.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            )
            Text(
                text = String.format("#%06X", packed and 0xFFFFFF),
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
            )
        }
    }

    Text(
        text = stringResource(hintRes),
        color = IsleColors.TextMuted,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun FrequencyPaletteSwatch(
    palette: FourColorPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(7.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .border(
                    width = if (selected) 1.8.dp else 1.dp,
                    color = if (selected) IsleColors.Cyan else IsleColors.StrokeStrong,
                    shape = shape,
                ),
        ) {
            palette.colors.forEach { packed ->
                Box(
                    modifier = Modifier
                        .size(width = 15.dp, height = 28.dp)
                        .background(packedToColor(packed)),
                )
            }
        }
        Text(
            text = stringResource(palette.nameRes),
            color = if (selected) IsleColors.Cyan else IsleColors.TextMuted,
            fontSize = 10.5.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * 频谱像素的单频段取色弹层。
 *
 * 用 HSV 三个滑条覆盖任意颜色，同时保留一排产品预设色方便快速选择；
 * 滑条松手与预设点按都会立即下发，拖动过程中只更新本地预览。
 */
@Composable
private fun FrequencyColorSheet(
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

    BottomSheet(onDismiss = onDismiss) {
        SheetTitle(title)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(packedToColor(current))
                .border(1.dp, IsleColors.StrokeStrong, RoundedCornerShape(12.dp)),
        )
        FrequencyColorSlider(
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
        FrequencyColorSlider(
            label = stringResource(R.string.isle_freq_saturation),
            value = saturation,
            track = Brush.horizontalGradient(
                listOf(Color.White, packedToColor(hsvToPacked(hue, 1f, brightness))),
            ),
            onValueChange = { saturation = it },
            onCommit = { onPick(hsvToPacked(hue, saturation, brightness)) },
        )
        FrequencyColorSlider(
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

@Composable
private fun FrequencyColorSlider(
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

private fun hsvToPacked(hue: Float, saturation: Float, brightness: Float): Int =
    android.graphics.Color.HSVToColor(
        floatArrayOf(
            hue.coerceIn(0f, 1f) * 360f,
            saturation.coerceIn(0f, 1f),
            brightness.coerceIn(0f, 1f),
        ),
    ) and 0xFFFFFF

@Composable
private fun TuningSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = IsleColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${normalizedToPercent(value)}%",
                color = IsleColors.TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier.width(40.dp),
            )
        }
        IsleSlider(
            value = value,
            onValueChange = onValueChange,
            // 提交时不传数值，由调用方读自己的 state —— 拖拽结束到重组之间
            // 参数快照可能是旧值，直接用快照会提交偏小的数值。
            onValueChangeFinished = onCommit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MoodRow(
    selected: EffectMood,
    onSelect: (EffectMood) -> Unit,
    onSleepLight: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        EffectMood.entries.forEach { mood ->
            MoodTile(
                glyph = mood.glyph(),
                label = stringResource(mood.labelRes()),
                selected = mood == selected,
                onClick = { onSelect(mood) },
                modifier = Modifier.weight(1f),
            )
        }
        // 「睡眠夜灯」不是筛选项：点一下直接切到低亮度暖光，之后颜色与亮度仍可继续调。
        MoodTile(
            glyph = Glyph.Moon,
            label = stringResource(R.string.isle_sleep_light),
            selected = false,
            onClick = onSleepLight,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MoodTile(
    glyph: Glyph,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            text = label,
            color = if (selected) IsleColors.TextPrimary else IsleColors.TextMuted,
            fontSize = 10.5.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun EffectMood.glyph(): Glyph = when (this) {
    EffectMood.AURORA -> Glyph.Aurora
    EffectMood.MUSIC -> Glyph.Music
    EffectMood.TEXT -> Glyph.Text
}

private fun EffectMood.labelRes(): Int = when (this) {
    EffectMood.AURORA -> R.string.isle_mood_aurora
    EffectMood.MUSIC -> R.string.isle_mood_music
    EffectMood.TEXT -> R.string.isle_mood_text
}

/**
 * 把设备报来的 `nl.rem`（秒）做成每秒递减的本地倒计时。
 *
 * 固件只在状态变化时推送，倒计时期间不会每秒来一帧，所以必须本地自减；
 * 每当设备发来新的 `rem`（例如重新设定时），基准值会被重置。
 */
@Composable
private fun rememberNightlightRemaining(seconds: Long?): Long? {
    var remaining by remember { mutableStateOf(seconds) }
    LaunchedEffect(seconds) {
        remaining = seconds
        var left = seconds ?: return@LaunchedEffect
        while (left > 0) {
            remaining = left
            delay(1_000)
            left -= 1
        }
        remaining = 0L
    }
    return remaining?.takeIf { it > 0 }
}

/** `90` -> `1:30`。 */
private fun formatCountdown(seconds: Long): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

@Composable
private fun QuickGrid(
    cards: List<QuickCardItem>,
    favoriteIds: Set<Int>,
    currentEffect: Int?,
    onApply: (QuickCardItem) -> Unit,
    onToggleFavorite: (EffectInfo) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { card ->
                    QuickCard(
                        effect = card.effect,
                        isFavorite = card.effect.id in favoriteIds,
                        isActive = card.effect.id == currentEffect,
                        onClick = { onApply(card) },
                        onToggleFavorite = { onToggleFavorite(card.effect) },
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
    // 精选灯效用产品名（与灯效库卡片一致），点下去拿到的也是同一套推荐观感。
    val curated = CuratedEffects.find(effect.id)
    Box(
        modifier = modifier
            .aspectRatio(1.55f)
            .background(Color(0xFF101218), RoundedCornerShape(20.dp))
            .border(
                width = if (isActive) 1.6.dp else 1.dp,
                brush = if (isActive) {
                    Brush.linearGradient(curated?.gradient?.map { Color(it) } ?: IsleColors.Accent)
                } else {
                    Brush.linearGradient(listOf(IsleColors.Stroke, IsleColors.Stroke))
                },
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
                text = curated?.title ?: effect.nameZh,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = curated?.tagline ?: effect.name.takeIf { effect.hasChineseName }
            if (subtitle != null) {
                Text(
                    text = subtitle,
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
