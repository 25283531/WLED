package ca.cgagnier.wlednativeandroid.ui.device

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import ca.cgagnier.wlednativeandroid.ui.create.CustomGraphicFavorite
import ca.cgagnier.wlednativeandroid.ui.effects.AudioSpiral
import ca.cgagnier.wlednativeandroid.ui.effects.BassLava
import ca.cgagnier.wlednativeandroid.ui.effects.CUSTOM_PALETTE_ID_BASE
import ca.cgagnier.wlednativeandroid.ui.effects.CuratedEffects
import ca.cgagnier.wlednativeandroid.ui.effects.EffectCatalog
import ca.cgagnier.wlednativeandroid.ui.effects.EffectInfo
import ca.cgagnier.wlednativeandroid.ui.effects.EffectMood
import ca.cgagnier.wlednativeandroid.ui.effects.EffectTuningPolicy
import ca.cgagnier.wlednativeandroid.ui.effects.EffectTuningPolicy.SOLID_PALETTE_ID
import ca.cgagnier.wlednativeandroid.ui.effects.FourColorPalette
import ca.cgagnier.wlednativeandroid.ui.effects.FrequencyPixels
import ca.cgagnier.wlednativeandroid.ui.effects.PaletteCatalog
import ca.cgagnier.wlednativeandroid.ui.effects.PaletteInfo
import ca.cgagnier.wlednativeandroid.ui.effects.PaletteTemplate
import ca.cgagnier.wlednativeandroid.ui.effects.SonicPulse
import ca.cgagnier.wlednativeandroid.ui.effects.SpectrumLamp
import ca.cgagnier.wlednativeandroid.ui.effects.StageLight
import ca.cgagnier.wlednativeandroid.ui.effects.TriBreath
import ca.cgagnier.wlednativeandroid.ui.effects.TriColorPalette
import ca.cgagnier.wlednativeandroid.ui.isle.ColorPickerSheet
import ca.cgagnier.wlednativeandroid.ui.isle.EffectArtwork
import ca.cgagnier.wlednativeandroid.ui.isle.GlassCard
import ca.cgagnier.wlednativeandroid.ui.isle.GlowCircleButton
import ca.cgagnier.wlednativeandroid.ui.isle.Glyph
import ca.cgagnier.wlednativeandroid.ui.isle.GlyphIcon
import ca.cgagnier.wlednativeandroid.ui.isle.IsleBackdrop
import ca.cgagnier.wlednativeandroid.ui.isle.IsleColors
import ca.cgagnier.wlednativeandroid.ui.isle.IsleSlider
import ca.cgagnier.wlednativeandroid.ui.isle.PRESET_COLORS
import ca.cgagnier.wlednativeandroid.ui.isle.SectionLabel
import ca.cgagnier.wlednativeandroid.ui.isle.StarButton
import ca.cgagnier.wlednativeandroid.ui.isle.device.DeviceAppearance
import ca.cgagnier.wlednativeandroid.ui.isle.hsvToPacked
import ca.cgagnier.wlednativeandroid.ui.isle.packedRgb
import ca.cgagnier.wlednativeandroid.ui.isle.packedToColor
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceAdd.DeviceAdd
import ca.cgagnier.wlednativeandroid.util.CHANNEL_MAX
import ca.cgagnier.wlednativeandroid.util.brightnessChannelToNormalized
import ca.cgagnier.wlednativeandroid.util.channelToNormalized
import ca.cgagnier.wlednativeandroid.util.normalizedToBrightnessChannel
import ca.cgagnier.wlednativeandroid.util.normalizedToChannel
import ca.cgagnier.wlednativeandroid.util.normalizedToPercent
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

private val NIGHTLIGHT_OPTIONS = listOf(5, 10, 15, 30, 60, 120)

/** 尚未在本设备分配过槽位的模板占位 id，不与任何固件调色板 id 冲突。 */
private const val NO_TEMPLATE_SLOT = -1

// 固件里「只取当前颜色槽位」的动态调色板没有固定色标，用段落当前颜色实时拼出预览。
private val RANDOM_CYCLE_PALETTE_ID = PaletteCatalog.byName("Random Cycle")?.id
private val COLORS_1_2_PALETTE_ID = PaletteCatalog.byName("Colors 1&2")?.id
private val COLOR_GRADIENT_PALETTE_ID = PaletteCatalog.byName("Color Gradient")?.id
private val COLORS_ONLY_PALETTE_ID = PaletteCatalog.byName("Colors Only")?.id

/** Random Cycle 由固件随机生成，给一组多色样例作为示意预览。 */
private val RANDOM_CYCLE_PREVIEW = listOf(0xFF4D6D, 0xFFA53C, 0xF2E14B, 0x4CE0B3, 0x4D9BFF, 0xB44DFF)

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

/**
 * 首页（设备）：高频日常主控台。
 *
 * 电源、亮度、三个律动模式 + 睡眠夜灯、2×2 快捷卡片都在一屏内；
 * 多灯同步和定时收进顶部的两个微缩胶囊，点开是底部弹层 —— 它们不是每天都改的参数，不该占据主视觉。
 */
@Composable
fun DeviceScreen(
    modifier: Modifier = Modifier,
    onOpenTextEditor: () -> Unit = {},
    viewModel: DeviceHomeViewModel = koinViewModel(),
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedMac by viewModel.selectedMacAddress.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val mood by viewModel.mood.collectAsStateWithLifecycle()
    val sleepLightSelected by viewModel.sleepLightSelected.collectAsStateWithLifecycle()
    val customGraphics by viewModel.customGraphics.collectAsStateWithLifecycle()
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
    // 纯色 / 明灭 / 循环彩的固件实现各有不参与运算的参数，或只支持多色调色板
    val hideSpeed = remember(currentEffect) { EffectTuningPolicy.hidesSpeed(currentEffect) }
    val hideIntensity = remember(currentEffect) { EffectTuningPolicy.hidesIntensity(currentEffect) }
    val hidePalette = remember(currentEffect) { EffectTuningPolicy.hidesPalette(currentEffect) }
    val hideColorRow = remember(currentEffect) { EffectTuningPolicy.hidesColorRow(currentEffect) }
    val hiddenPaletteIds = remember(currentEffect) { EffectTuningPolicy.hiddenPaletteIds(currentEffect) }
    val hideSolidPalette = remember(currentEffect) { EffectTuningPolicy.hidesSolidPalette(currentEffect) }
    val blinkMode = remember(currentEffect) { EffectTuningPolicy.isBlink(currentEffect) }
    val solidMode = remember(currentEffect) { EffectTuningPolicy.isSolid(currentEffect) }
    val colorsOnlyViaPalette = remember(currentEffect) {
        EffectTuningPolicy.colorsOnlyViaPalette(currentEffect)
    }
    val intensityLabel = remember(currentEffect) { EffectTuningPolicy.intensityLabel(currentEffect) }
    // 手机本地多色模板：创作页制作，设备页调色板预览与调用
    val paletteTemplates by viewModel.paletteTemplates.collectAsStateWithLifecycle()
    val paletteSlotsByDevice by viewModel.paletteSlots.collectAsStateWithLifecycle()
    val deviceMac = device?.device?.macAddress
    val paletteSlots = remember(paletteSlotsByDevice, deviceMac) {
        deviceMac?.let { paletteSlotsByDevice[it] }.orEmpty()
    }

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

    LaunchedEffect(Unit) {
        viewModel.paletteEvents.collect { event ->
            val message = when (event) {
                is PaletteTemplateEvent.Applied ->
                    context.getString(R.string.isle_palette_template_applied, event.name)
                PaletteTemplateEvent.Failed ->
                    context.getString(R.string.isle_palette_template_failed)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val quickCards = remember(favorites, mood, customGraphics) {
        viewModel.quickCards(favorites, mood, customGraphics)
    }
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
                    effectId = currentEffect,
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
                    hideSpeed = hideSpeed,
                    hideIntensity = hideIntensity,
                    hidePalette = hidePalette,
                    hideColorRow = hideColorRow,
                    hiddenPaletteIds = hiddenPaletteIds,
                    hideSolidPalette = hideSolidPalette,
                    blinkMode = blinkMode,
                    solidMode = solidMode,
                    colorsOnlyViaPalette = colorsOnlyViaPalette,
                    intensityLabel = intensityLabel,
                    paletteTemplates = paletteTemplates,
                    paletteSlots = paletteSlots,
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
                    onSelectPaletteTemplate = { template ->
                        device?.let { viewModel.applyPaletteTemplate(it, template) }
                    },
                    onOpenTextEditor = onOpenTextEditor,
                )
            }

            item {
                SectionLabel(
                    text = stringResource(R.string.isle_rhythm_mode),
                    modifier = Modifier.padding(top = 18.dp),
                )
                MoodRow(
                    selected = mood,
                    sleepSelected = sleepLightSelected,
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
                if (quickCards.isEmpty()) {
                    EmptyQuickHint(
                        text = stringResource(R.string.isle_no_graphic_favorites),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                } else {
                    QuickGrid(
                        cards = quickCards,
                        favoriteIds = favoriteIds,
                        currentEffect = currentEffect,
                        currentName = segment?.name,
                        onApplyEffect = { card ->
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
                        onApplyGraphic = { graphic ->
                            val target = device
                            if (target == null) {
                                Toast.makeText(context, R.string.effects_no_device, Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.applyCustomGraphic(target, graphic)
                            }
                        },
                        onRemoveGraphic = viewModel::removeCustomGraphic,
                    )
                }
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
        ColorPickerSheet(
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
        ColorPickerSheet(
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
        ColorPickerSheet(
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
        ColorPickerSheet(
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
        ColorPickerSheet(
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
        ColorPickerSheet(
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
    var local by remember(value) { mutableFloatStateOf(brightnessChannelToNormalized(value)) }
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
                onValueChangeFinished = { onCommit(normalizedToBrightnessChannel(local)) },
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
    effectId: Int?,
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
    hideSpeed: Boolean,
    hideIntensity: Boolean,
    hidePalette: Boolean,
    hideColorRow: Boolean,
    hiddenPaletteIds: Set<Int>,
    hideSolidPalette: Boolean,
    blinkMode: Boolean,
    solidMode: Boolean,
    colorsOnlyViaPalette: Boolean,
    intensityLabel: EffectTuningPolicy.IntensityLabel,
    paletteTemplates: List<PaletteTemplate>,
    paletteSlots: Map<String, Int>,
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
    onSelectPaletteTemplate: (PaletteTemplate) -> Unit,
    onOpenTextEditor: () -> Unit,
) {
    // 复制段落等灯效的有效通道区间只占滑杆前 10%，这里用缩放系数还原成 0..100% 的直觉刻度。
    val sliderScale = remember(effectId) { EffectTuningPolicy.sliderScale(effectId) }
    var localSpeed by remember(speed, sliderScale) {
        mutableFloatStateOf(EffectTuningPolicy.channelToSlider(effectId, speed))
    }
    var localIntensity by remember(intensity, sliderScale) {
        mutableFloatStateOf(EffectTuningPolicy.channelToSlider(effectId, intensity))
    }
    val intensityLabelText = stringResource(
        when (intensityLabel) {
            EffectTuningPolicy.IntensityLabel.WIDTH -> R.string.isle_effect_intensity_width
            EffectTuningPolicy.IntensityLabel.STROBE_COUNT -> R.string.isle_effect_intensity_strobe_count
            EffectTuningPolicy.IntensityLabel.FIREWORK_RATE -> R.string.isle_effect_intensity_firework_rate
            EffectTuningPolicy.IntensityLabel.SOFTNESS -> R.string.isle_effect_intensity_softness
            EffectTuningPolicy.IntensityLabel.DOT_COUNT -> R.string.isle_effect_intensity_dot_count
            EffectTuningPolicy.IntensityLabel.TEXT_Y_OFFSET -> R.string.isle_effect_intensity_text_y
            EffectTuningPolicy.IntensityLabel.SMOOTHNESS -> R.string.isle_effect_intensity_smoothness
            EffectTuningPolicy.IntensityLabel.FRAGMENTS -> R.string.isle_effect_intensity_fragments
            EffectTuningPolicy.IntensityLabel.HUE_SHIFT -> R.string.isle_effect_intensity_hue_shift
            EffectTuningPolicy.IntensityLabel.BALL_COUNT -> R.string.isle_effect_intensity_ball_count
            EffectTuningPolicy.IntensityLabel.DEFAULT -> R.string.isle_effect_intensity
        },
    )
    val speedLabelText = stringResource(
        when (EffectTuningPolicy.speedLabel(effectId)) {
            EffectTuningPolicy.SpeedLabel.BLINK_RATE -> R.string.isle_effect_speed_blink_rate
            EffectTuningPolicy.SpeedLabel.SCROLL_SPEED -> R.string.isle_effect_speed_scroll
            EffectTuningPolicy.SpeedLabel.DURATION_MIN -> R.string.isle_effect_speed_duration
            EffectTuningPolicy.SpeedLabel.SPAWN_CHANCE -> R.string.isle_effect_speed_chance
            EffectTuningPolicy.SpeedLabel.DEFAULT -> R.string.isle_effect_speed
        },
    )
    // 焰火的颜色只由「颜色槽派生」的 2–5 号调色板生效，其余模板下改色块不会有任何效果。
    val showColorRow = !hideColorRow && (!colorsOnlyViaPalette || palette in 2..5)

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
                    blinkMode -> stringResource(
                        R.string.isle_blink_tuning_summary,
                        normalizedToPercent(localSpeed),
                        normalizedToPercent(localIntensity),
                    )
                    hideSpeed && hideIntensity -> stringResource(R.string.isle_color_only_summary)
                    hideSpeed -> stringResource(
                        R.string.isle_effect_tuning_summary_custom_single,
                        intensityLabelText,
                        normalizedToPercent(localIntensity),
                    )
                    hideIntensity -> stringResource(
                        R.string.isle_effect_tuning_summary_custom_single,
                        speedLabelText,
                        normalizedToPercent(localSpeed),
                    )
                    else -> stringResource(
                        R.string.isle_effect_tuning_summary_both,
                        speedLabelText,
                        normalizedToPercent(localSpeed),
                        intensityLabelText,
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
                        if (!hideSpeed) {
                            TuningSlider(
                                label = speedLabelText,
                                value = localSpeed,
                                onValueChange = { localSpeed = it },
                                onCommit = {
                                    onSpeed(EffectTuningPolicy.sliderToChannel(effectId, localSpeed))
                                },
                            )
                        }
                        if (!hideIntensity) {
                            TuningSlider(
                                label = if (blinkMode) {
                                    stringResource(R.string.isle_effect_intensity_blink)
                                } else {
                                    intensityLabelText
                                },
                                value = localIntensity,
                                onValueChange = { localIntensity = it },
                                onCommit = {
                                    onIntensity(EffectTuningPolicy.sliderToChannel(effectId, localIntensity))
                                },
                            )
                            if (blinkMode) {
                                Text(
                                    text = stringResource(R.string.isle_blink_intensity_hint),
                                    color = IsleColors.TextMuted,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }

                        if (EffectTuningPolicy.isScrollingText(effectId)) {
                            Text(
                                text = stringResource(R.string.isle_text_editor_open),
                                color = IsleColors.Cyan,
                                fontSize = 12.5.sp,
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .clickable(onClick = onOpenTextEditor),
                            )
                        }

                        if (showColorRow) {
                            Text(
                                text = stringResource(
                                    if (solidMode || (!hidePalette && palette == SOLID_PALETTE_ID)) {
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
                        }

                        if (!hidePalette) {
                            PalettePicker(
                                selected = palette,
                                primaryPacked = colors.firstOrNull()?.let { packedRgb(it) } ?: 0xFFFFFF,
                                colorsPacked = colors.map { packedRgb(it) },
                                templates = paletteTemplates,
                                templateSlots = paletteSlots,
                                showSolid = !hideSolidPalette,
                                hiddenPaletteIds = hiddenPaletteIds,
                                onSelect = onPalette,
                                onSelectTemplate = onSelectPaletteTemplate,
                                modifier = Modifier.padding(top = 14.dp),
                            )
                        }
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
 * 而不是面对一串编号。固件里「只取当前颜色槽位」的动态调色板没有固定色，
 * 用当前段落的颜色实时拼出预览，避免出现一排灰色空块。
 */
@Composable
private fun PalettePicker(
    selected: Int,
    primaryPacked: Int,
    colorsPacked: List<Int>,
    templates: List<PaletteTemplate>,
    templateSlots: Map<String, Int>,
    showSolid: Boolean,
    hiddenPaletteIds: Set<Int> = emptySet(),
    onSelect: (Int) -> Unit,
    onSelectTemplate: (PaletteTemplate) -> Unit,
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
    // 自定义模板的设备调色板 id：槽位 0 -> 200，槽位 1 -> 199 ……
    // 还没在这台设备上用过的模板也有 id 占位（NO_TEMPLATE_SLOT），先展示预览，点用后才分配真实槽位。
    val templatePalettes = remember(templates, templateSlots) {
        templates.map { template ->
            val slot = templateSlots[template.id]
            template to PaletteInfo(
                id = slot?.let { CUSTOM_PALETTE_ID_BASE - it } ?: NO_TEMPLATE_SLOT,
                name = template.name,
                isStatic = true,
                colors = template.colors,
            )
        }
    }
    // 「*」动态调色板在固件里从当前颜色槽取色，这里用当前段落颜色拼出真实预览。
    val dynamicPreviews = remember(palettes, colorsPacked) {
        val c1 = colorsPacked.getOrElse(0) { 0xFFFFFF }
        val c2 = colorsPacked.getOrElse(1) { 0x000000 }
        val c3 = colorsPacked.getOrElse(2) { c2 }
        palettes.mapNotNull { palette ->
            val preview = when {
                palette.id == RANDOM_CYCLE_PALETTE_ID -> RANDOM_CYCLE_PREVIEW
                palette.id == COLORS_1_2_PALETTE_ID -> listOf(c1, c2)
                palette.id == COLOR_GRADIENT_PALETTE_ID -> listOf(c1, c1, c2, c2)
                palette.id == COLORS_ONLY_PALETTE_ID -> listOf(c1, c2, c3, c1)
                else -> null
            }
            preview?.let { palette.copy(colors = it) }
        }.associateBy { it.id }
    }
    val current = when {
        showSolid && selected == SOLID_PALETTE_ID -> solid
        else -> palettes.firstOrNull { it.id == selected }
            ?: templatePalettes.firstOrNull { it.second.id == selected }?.second
    }
    val visiblePalettes = remember(palettes, dynamicPreviews, hiddenPaletteIds) {
        palettes.filterNot { it.id == SOLID_PALETTE_ID || it.id in hiddenPaletteIds }
            .map { dynamicPreviews[it.id] ?: it }
    }
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

        // 手机本地的多色模板：只有分配过设备槽位（点用过）的才有对应调色板 id
        Text(
            text = stringResource(R.string.isle_palette_templates),
            color = IsleColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (templatePalettes.isEmpty()) {
            Text(
                text = stringResource(R.string.isle_palette_templates_hint),
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                templatePalettes.forEach { (template, info) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(46.dp),
                    ) {
                        PaletteSwatch(
                            palette = info,
                            selected = info.id == selected,
                            onClick = { onSelectTemplate(template) },
                        )
                        Text(
                            text = template.name,
                            color = IsleColors.TextMuted,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showSolid) {
                PaletteSwatch(
                    palette = solid,
                    selected = selected == SOLID_PALETTE_ID,
                    onClick = { onSelect(SOLID_PALETTE_ID) },
                )
            }
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
                // 有固定色标才画渐变；动态调色板已用当前段落颜色补好预览，兜底仍是中性底纹
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
    sleepSelected: Boolean,
    onSelect: (EffectMood) -> Unit,
    onSleepLight: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        EffectMood.entries.forEach { mood ->
            MoodTile(
                glyph = mood.glyph(),
                label = stringResource(mood.labelRes()),
                // 夜灯选中时它下发的纯色会被识别成幻彩极光，这里不能让别的胶囊抢走高亮。
                selected = !sleepSelected && mood == selected,
                onClick = { onSelect(mood) },
                modifier = Modifier.weight(1f),
            )
        }
        // 「睡眠夜灯」不是筛选项：点一下直接切到低亮度暖光，之后颜色与亮度仍可继续调。
        MoodTile(
            glyph = Glyph.Moon,
            label = stringResource(R.string.isle_sleep_light),
            selected = sleepSelected,
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
    currentName: String?,
    onApplyEffect: (QuickCardItem.EffectCard) -> Unit,
    onToggleFavorite: (EffectInfo) -> Unit,
    onApplyGraphic: (CustomGraphicFavorite) -> Unit,
    onRemoveGraphic: (CustomGraphicFavorite) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { card ->
                    when (card) {
                        is QuickCardItem.EffectCard -> QuickCard(
                            effect = card.effect,
                            isFavorite = card.effect.id in favoriteIds,
                            isActive = card.effect.id == currentEffect,
                            onClick = { onApplyEffect(card) },
                            onToggleFavorite = { onToggleFavorite(card.effect) },
                            modifier = Modifier.weight(1f),
                        )
                        // 「动态字幕」下展示用户自己收藏的自定义图文，点击即上传并播放。
                        is QuickCardItem.GraphicCard -> QuickGraphicCard(
                            graphic = card.graphic,
                            isActive = card.graphic.deviceFileName == currentName,
                            onClick = { onApplyGraphic(card.graphic) },
                            onRemove = { onRemoveGraphic(card.graphic) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 快捷区空态提示：例如「动态字幕」下还没有收藏任何自定义图文。 */
@Composable
private fun EmptyQuickHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x0FFFFFFF), RoundedCornerShape(18.dp))
            .border(1.dp, IsleColors.Stroke, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Text(text = text, color = IsleColors.TextMuted, fontSize = 12.sp)
    }
}

/**
 * 自定义图文的快捷卡片。
 *
 * 预览图是收藏时渲染的 PNG（灯珠矩阵栅格），星标表示「已在收藏中」，
 * 点星标即从收藏里移除。
 */
@Composable
private fun QuickGraphicCard(
    graphic: CustomGraphicFavorite,
    isActive: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = remember(graphic.previewPath) {
        BitmapFactory.decodeFile(graphic.previewPath)?.asImageBitmap()
    }
    Box(
        modifier = modifier
            .aspectRatio(1.55f)
            .background(Color(0xFF101218), RoundedCornerShape(20.dp))
            .border(
                width = if (isActive) 1.6.dp else 1.dp,
                brush = if (isActive) {
                    Brush.linearGradient(IsleColors.Accent)
                } else {
                    Brush.linearGradient(listOf(IsleColors.Stroke, IsleColors.Stroke))
                },
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick),
    ) {
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = graphic.name,
                // 灯珠矩阵是竖长的，用 Fit 完整展示，避免 Crop 裁掉大半内容
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlyphIcon(glyph = Glyph.Text, size = 26.dp, tint = IsleColors.Cyan)
            }
        }
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
                text = graphic.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.isle_mood_text),
                color = Color(0xFFB9C0CC),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StarButton(
            selected = true,
            onClick = onRemove,
            size = 24.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
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
                    // 精选色板是 6 位 RGB，补上不透明通道，否则 Color(int) 会变成透明色
                    Brush.linearGradient(
                        curated?.gradient?.map { Color(it or 0xFF000000.toInt()) } ?: IsleColors.Accent,
                    )
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
