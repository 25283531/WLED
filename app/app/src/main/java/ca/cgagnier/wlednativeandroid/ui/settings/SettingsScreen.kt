package ca.cgagnier.wlednativeandroid.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.audio.AudioStreamService
import ca.cgagnier.wlednativeandroid.audio.AudioTransportType
import ca.cgagnier.wlednativeandroid.audio.BleAudioSyncSender
import ca.cgagnier.wlednativeandroid.ui.device.DeviceHomeViewModel
import ca.cgagnier.wlednativeandroid.ui.effects.EffectCatalog
import ca.cgagnier.wlednativeandroid.ui.isle.DeviceTube
import ca.cgagnier.wlednativeandroid.ui.isle.GlassCard
import ca.cgagnier.wlednativeandroid.ui.isle.Glyph
import ca.cgagnier.wlednativeandroid.ui.isle.GlyphIcon
import ca.cgagnier.wlednativeandroid.ui.isle.IsleBackdrop
import ca.cgagnier.wlednativeandroid.ui.isle.IsleColors
import ca.cgagnier.wlednativeandroid.util.AppLanguage
import ca.cgagnier.wlednativeandroid.util.AppLanguageManager
import org.koin.androidx.compose.koinViewModel

/**
 * 设置：智能生态 / 硬件与交互 / 系统与支持。
 *
 * 三块都出现了固件当前没有开放的能力（Matter 桥接、麦克风灵敏度、顶部触控校准）。
 * 这些行仍然保留在界面上，但明确标注「固件未开放」而不是给一个按下去没反应的开关 ——
 * 界面应该如实反映能力边界，而不是假装有。
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: DeviceHomeViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedMac by viewModel.selectedMacAddress.collectAsStateWithLifecycle()
    val device = devices.firstOrNull { it.device.macAddress == selectedMac } ?: devices.firstOrNull()

    val currentLanguage = remember(context) { AppLanguageManager.current(context) }
    val followingSystem = remember(context) { AppLanguageManager.isFollowingSystem(context) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showWifiPage by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showFirmwareDialog by remember { mutableStateOf(false) }

    // 原生固件 OTA：从统一固件通道拉 manifest，用户确认后再写入设备
    val firmwareViewModel: FirmwareUpdateViewModel = koinViewModel()
    val firmwareState by firmwareViewModel.state.collectAsStateWithLifecycle()

    // 「设备型号」连点 3 次提示、5 次进入开发者模式
    var modelTapCount by remember { mutableIntStateOf(0) }
    var lastModelTapAt by remember { mutableLongStateOf(0L) }
    var developerMode by remember(context) { mutableStateOf(isleDeveloperModeEnabled(context)) }

    val audioViewModel: AudioSettingsViewModel = koinViewModel()
    val audioUiState by audioViewModel.uiState.collectAsStateWithLifecycle()
    val streamState by AudioStreamService.state.collectAsStateWithLifecycle()
    val audioAddress = device?.device?.address
    LaunchedEffect(audioAddress) {
        audioAddress?.let { audioViewModel.load(it) }
    }

    fun openWeb(path: String = "") {
        val target = device ?: return
        val url = "http://${target.device.address}/$path"
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, R.string.isle_open_web_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun notYet() {
        Toast.makeText(context, R.string.isle_not_supported_toast, Toast.LENGTH_SHORT).show()
    }

    fun onModelTap() {
        val now = System.currentTimeMillis()
        modelTapCount = if (now - lastModelTapAt > 3000L) 1 else modelTapCount + 1
        lastModelTapAt = now
        if (modelTapCount == 3) {
            Toast.makeText(context, R.string.isle_dev_mode_hint, Toast.LENGTH_SHORT).show()
        } else if (modelTapCount >= 5) {
            modelTapCount = 0
            isleSetDeveloperMode(context, true)
            developerMode = true
            Toast.makeText(context, R.string.isle_dev_mode_enabled, Toast.LENGTH_SHORT).show()
            openWeb()
        }
    }

    fun openFirmwareUpdate() {
        showFirmwareDialog = true
        firmwareViewModel.check(device)
    }

    /**
     * 开机默认灯效的真实状态。
     *
     * 固件里段落的 fx 不随重启持久化（cfg.json 没有 "seg" 数组），所以设备每次开机
     * 落到的灯效只有两种来源：
     *  - `bootps == 0`：不加载预设，段落用编译期的出厂默认灯效（`leds.bootfx`，本产品为 188「极光之舞」）
     *  - `bootps > 0`：加载该编号的预设，开机效果由预设内容决定
     *
     * 这里如实反映设备当前状态，不在 App 侧猜测或缓存。
     */
    fun bootStateLabel(): String? {
        val leds = device?.stateInfo?.info?.leds ?: return null
        val presetId = leds.bootPresetId ?: 0
        if (presetId > 0) {
            return context.getString(R.string.isle_boot_state_preset, presetId)
        }
        val bootFx = leds.defaultEffectId
        val name = bootFx?.let { EffectCatalog.find(it)?.nameZh }
            ?: return context.getString(R.string.isle_boot_state_factory)
        return context.getString(R.string.isle_boot_state_effect, name)
    }

    // T2：手机音频 → WLED。内录需要每次会话重新授权，因此先拿投屏 token 再启动前台服务
    var pendingTransport by remember { mutableStateOf(AudioTransportType.UDP) }

    fun missingNotificationPermission(): String? = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

    // 内录走 AudioRecord，系统依然要求 RECORD_AUDIO；没有它 AudioRecord 会直接初始化失败
    fun missingRecordAudioPermission(): String? = if (
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        Manifest.permission.RECORD_AUDIO
    } else {
        null
    }

    fun bluetoothPermissionRequests(): Array<String> {
        if (!BleAudioSyncSender.isSupported(context)) return emptyArray()
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return required
            .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val host = device?.device?.address
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null && host != null) {
            AudioStreamService.start(context, host, result.resultCode, data, pendingTransport)
        } else {
            Toast.makeText(context, R.string.isle_audio_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    val launchProjection: () -> Unit = {
        val target = device
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (target == null) {
            Toast.makeText(context, R.string.isle_audio_switch_failed_hint, Toast.LENGTH_SHORT).show()
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || manager == null) {
            // Android 10 以下没有内录，蓝牙与直通都无法使用
            Toast.makeText(context, R.string.isle_audio_needs_android10, Toast.LENGTH_SHORT).show()
        } else {
            runCatching { projectionLauncher.launch(manager.createScreenCaptureIntent()) }
                .onFailure {
                    Toast.makeText(context, R.string.isle_audio_permission_denied, Toast.LENGTH_SHORT).show()
                }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.POST_NOTIFICATIONS] == false) {
            Toast.makeText(context, R.string.isle_audio_notification_denied, Toast.LENGTH_SHORT).show()
        }
        if (pendingTransport == AudioTransportType.BLE && !BleAudioSyncSender.hasPermissions(context)) {
            Toast.makeText(context, R.string.isle_audio_ble_permission_denied, Toast.LENGTH_SHORT).show()
        } else if (missingRecordAudioPermission() != null) {
            Toast.makeText(context, R.string.isle_audio_permission_denied, Toast.LENGTH_SHORT).show()
        } else {
            launchProjection()
        }
    }

    val startAudioCapture: () -> Unit = {
        when (audioUiState.source) {
            DeviceAudioSource.BLE -> {
                pendingTransport = AudioTransportType.BLE
                if (!BleAudioSyncSender.isSupported(context)) {
                    Toast.makeText(context, R.string.isle_audio_ble_unsupported, Toast.LENGTH_SHORT).show()
                } else {
                    val required = bluetoothPermissionRequests().toMutableList()
                    missingNotificationPermission()?.let { required.add(it) }
                    missingRecordAudioPermission()?.let { required.add(it) }
                    if (required.isEmpty()) {
                        launchProjection()
                    } else {
                        audioPermissionLauncher.launch(required.toTypedArray())
                    }
                }
            }
            DeviceAudioSource.PLAYBACK -> {
                pendingTransport = AudioTransportType.UDP
                val required = mutableListOf<String>()
                missingNotificationPermission()?.let { required.add(it) }
                missingRecordAudioPermission()?.let { required.add(it) }
                if (required.isEmpty()) {
                    launchProjection()
                } else {
                    audioPermissionLauncher.launch(required.toTypedArray())
                }
            }
            DeviceAudioSource.DEVICE, null -> Unit
        }
    }

    val openAudioPanel: () -> Unit = {
        audioAddress?.let { audioViewModel.load(it) }
        showAudioSheet = true
    }

    val openBatterySettings: () -> Unit = {
        val requested = runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }.isSuccess
        if (!requested) {
            runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    // WiFi 已原生化：直接读设备的扫描结果与配置，不再跳系统浏览器
    val wifiTarget = device
    if (showWifiPage && wifiTarget != null) {
        WifiSettingsPage(
            address = wifiTarget.device.address,
            onBack = { showWifiPage = false },
            modifier = modifier,
        )
        return
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
                text = stringResource(R.string.isle_tab_settings),
                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp),
                color = IsleColors.TextPrimary,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )

            DeviceSummaryCard(
                name = device?.device?.let { it.customName.ifBlank { it.originalName } }
                    ?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.app_name),
                online = device?.isOnline == true,
                version = device?.stateInfo?.info?.version,
                onClick = { openWeb() },
            )

            SettingsGroup(title = stringResource(R.string.isle_settings_ecosystem)) {
                SettingsRow(
                    glyph = Glyph.Sparkle,
                    iconTint = IsleColors.Violet,
                    label = stringResource(R.string.isle_matter),
                    value = stringResource(R.string.isle_not_supported),
                    valueTint = IsleColors.TextMuted,
                    onClick = ::notYet,
                )
                SettingsRow(
                    glyph = Glyph.Wifi,
                    iconTint = IsleColors.Cyan,
                    label = stringResource(R.string.isle_wifi),
                    value = device?.stateInfo?.info?.wifi?.let { wifi ->
                        wifi.channel?.let { "CH $it" }
                    } ?: stringResource(R.string.isle_open_web),
                    onClick = { showWifiPage = true },
                )
            }

            SettingsGroup(title = stringResource(R.string.isle_settings_hardware)) {
                SettingsRow(
                    glyph = Glyph.Music,
                    iconTint = IsleColors.Pink,
                    label = stringResource(R.string.isle_audio_source),
                    value = when (audioUiState.source) {
                        DeviceAudioSource.BLE -> stringResource(R.string.isle_audio_source_ble)
                        DeviceAudioSource.PLAYBACK -> stringResource(R.string.isle_audio_source_passthrough)
                        DeviceAudioSource.DEVICE -> stringResource(R.string.isle_audio_source_device)
                        null -> null
                    },
                    onClick = openAudioPanel,
                )
                DisabledSliderRow(
                    glyph = Glyph.Mic,
                    iconTint = IsleColors.Pink,
                    label = stringResource(R.string.isle_mic),
                )
                SettingsRow(
                    glyph = Glyph.Settings,
                    iconTint = IsleColors.Cyan,
                    label = stringResource(R.string.isle_keys),
                    value = stringResource(R.string.isle_not_supported),
                    valueTint = IsleColors.TextMuted,
                    onClick = ::notYet,
                )
            }

            SettingsGroup(title = stringResource(R.string.isle_settings_system)) {
                SettingsRow(
                    glyph = Glyph.Chip,
                    iconTint = IsleColors.Cyan,
                    label = stringResource(R.string.isle_device_model),
                    value = device?.stateInfo?.info?.product?.takeIf { it.isNotBlank() }
                        ?: device?.device?.originalName?.takeIf { it.isNotBlank() }
                        ?: "—",
                    onClick = ::onModelTap,
                )
                SettingsRow(
                    glyph = Glyph.Power,
                    iconTint = IsleColors.Cyan,
                    label = stringResource(R.string.isle_boot_state),
                    value = bootStateLabel(),
                    onClick = { openWeb("settings") },
                )
                SettingsRow(
                    glyph = Glyph.Chip,
                    iconTint = IsleColors.Violet,
                    label = stringResource(R.string.isle_firmware),
                    value = device?.stateInfo?.info?.version,
                    onClick = ::openFirmwareUpdate,
                )
                if (developerMode) {
                    SettingsRow(
                        glyph = Glyph.Settings,
                        iconTint = IsleColors.TextSecondary,
                        label = stringResource(R.string.isle_dev_mode_open_web),
                        value = stringResource(R.string.isle_dev_mode_on),
                        onClick = { openWeb() },
                    )
                }
                SettingsRow(
                    glyph = Glyph.Text,
                    iconTint = IsleColors.TextSecondary,
                    label = stringResource(R.string.isle_language),
                    value = if (followingSystem) {
                        stringResource(R.string.isle_follow_system)
                    } else {
                        currentLanguage.nativeName
                    },
                    onClick = { showLanguageSheet = true },
                )
            }

            Text(
                text = stringResource(R.string.isle_settings_footer),
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
    }

    if (showLanguageSheet) {
        LanguageSheet(
            current = currentLanguage,
            followingSystem = followingSystem,
            onFollowSystem = {
                activity?.let { AppLanguageManager.followSystem(it) }
                showLanguageSheet = false
            },
            onSelect = { language ->
                activity?.let { AppLanguageManager.set(it, language) }
                showLanguageSheet = false
            },
            onDismiss = { showLanguageSheet = false },
        )
    }

    if (showAudioSheet) {
        AudioSettingsSheet(
            uiState = audioUiState,
            streamState = streamState,
            onSelectSource = { source ->
                // 换来源后旧通道不再有效，先停掉再写 cfg/等设备重启
                if (source != audioUiState.source) {
                    AudioStreamService.stop(context)
                }
                audioViewModel.selectSource(source)
            },
            onStart = startAudioCapture,
            onStop = { AudioStreamService.stop(context) },
            onRetryCapture = startAudioCapture,
            onOpenBatterySettings = openBatterySettings,
            onDismiss = { showAudioSheet = false },
        )
    }

    if (showFirmwareDialog) {
        FirmwareUpdateDialog(
            state = firmwareState,
            onStartUpdate = { firmwareViewModel.startUpdate(device) },
            onRetry = {
                if (firmwareState.release == null) {
                    firmwareViewModel.check(device)
                } else {
                    firmwareViewModel.startUpdate(device)
                }
            },
            onDismiss = {
                firmwareViewModel.reset()
                showFirmwareDialog = false
            },
        )
    }
}

private const val DEV_MODE_PREFS = "isle_developer"
private const val DEV_MODE_KEY = "enabled"

private fun isleDeveloperModeEnabled(context: Context): Boolean =
    context.getSharedPreferences(DEV_MODE_PREFS, Context.MODE_PRIVATE)
        .getBoolean(DEV_MODE_KEY, false)

private fun isleSetDeveloperMode(context: Context, enabled: Boolean) {
    context.getSharedPreferences(DEV_MODE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(DEV_MODE_KEY, enabled)
        .apply()
}

@Composable
private fun DeviceSummaryCard(name: String, online: Boolean, version: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF1A1D25), Color(0xFF12141A))),
                RoundedCornerShape(24.dp),
            )
            .border(1.dp, IsleColors.Stroke, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        // 卡片右侧留一角给灯管，作为"这是哪台灯"的视觉锚点
        DeviceTube(
            colors = emptyList(),
            isOn = online,
            brightness = 0.85f,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .size(width = 96.dp, height = 96.dp),
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = name,
                color = IsleColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 80.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (online) IsleColors.Online else IsleColors.TextMuted, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        if (online) R.string.isle_status_connected else R.string.isle_status_offline,
                    ),
                    color = IsleColors.TextSecondary,
                    fontSize = 11.5.sp,
                )
            }
            if (!version.isNullOrBlank()) {
                Text(
                    text = version,
                    color = IsleColors.TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        color = IsleColors.TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
    )
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, padding = 6.dp) {
        content()
    }
}

@Composable
private fun SettingsRow(
    glyph: Glyph,
    iconTint: Color,
    label: String,
    onClick: () -> Unit,
    value: String? = null,
    valueTint: Color = IsleColors.TextMuted,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph = glyph, size = 18.dp, tint = iconTint)
        Text(
            text = label,
            color = IsleColors.TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        value?.let {
            Text(
                text = it,
                color = valueTint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        GlyphIcon(glyph = Glyph.ChevronRight, size = 14.dp, tint = IsleColors.TextMuted)
    }
}

@Composable
private fun DisabledSliderRow(glyph: Glyph, iconTint: Color, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph = glyph, size = 18.dp, tint = iconTint)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = IsleColors.TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0x1AFFFFFF), RoundedCornerShape(999.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.isle_not_supported_short),
                        color = IsleColors.TextMuted,
                        fontSize = 9.5.sp,
                    )
                }
            }
            // 不做成可拖动的滑条：固件没有这个接口，能拖却不生效比不给更糟
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(4.dp)
                    .background(Color(0x14FFFFFF), RoundedCornerShape(2.dp)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(
    current: AppLanguage,
    followingSystem: Boolean,
    onFollowSystem: () -> Unit,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.isle_language),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            LanguageRow(
                label = stringResource(R.string.isle_follow_system),
                selected = followingSystem,
                onClick = onFollowSystem,
            )
            AppLanguage.entries.forEach { language ->
                LanguageRow(
                    label = language.nativeName,
                    selected = !followingSystem && language == current,
                    onClick = { onSelect(language) },
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 16.dp,
        padding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = if (selected) IsleColors.Cyan else IsleColors.TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                GlyphIcon(glyph = Glyph.Check, size = 16.dp, tint = IsleColors.Cyan)
            }
        }
    }
}
