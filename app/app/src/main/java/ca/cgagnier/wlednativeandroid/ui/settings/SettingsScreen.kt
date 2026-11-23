package ca.cgagnier.wlednativeandroid.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.getValue
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

    /**
     * 开机默认灯效的真实状态。
     *
     * 固件里段落的 fx 不随重启持久化（cfg.json 没有 "seg" 数组），所以设备每次开机
     * 落到的灯效只有两种来源：
     *  - `bootps == 0`：不加载预设，段落用编译期的出厂默认灯效（`leds.bootfx`，本产品为 221「极光之舞」）
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
                    onClick = { openWeb() },
                )
            }

            SettingsGroup(title = stringResource(R.string.isle_settings_hardware)) {
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
                    onClick = { openWeb("update") },
                )
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
