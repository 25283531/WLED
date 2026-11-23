package ca.cgagnier.wlednativeandroid.ui.control

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.components.DeviceSelector
import ca.cgagnier.wlednativeandroid.ui.components.deviceSpecSummary
import ca.cgagnier.wlednativeandroid.ui.effects.EffectCatalog
import ca.cgagnier.wlednativeandroid.ui.effects.packRgb
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

private const val MAX_CHANNEL_VALUE = 255

/** 颜色快捷选项（主色）。 */
private val COLOR_PRESETS = listOf(
    0xB71C1C, 0xE65100, 0xFB8C00, 0xFDD835,
    0x43A047, 0x00ACC1, 0x1E88E5, 0x8E24AA,
    0xE91E63, 0xF5F5F5,
)

/** 定时关灯的可选时长（分钟）。 */
private val TIMER_MINUTES = listOf(5, 10, 15, 30, 60, 120)

/**
 * 控制页。取代原先内嵌的固件网页，全部为原生控件。
 */
@Composable
fun ControlScreen(
    modifier: Modifier = Modifier,
    pinnedDeviceMac: String? = null,
    applyStatusBarPadding: Boolean = true,
    viewModel: ControlViewModel = koinViewModel(),
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedMac by viewModel.selectedDeviceMac.collectAsStateWithLifecycle()
    val syncEnabled by viewModel.syncEnabled.collectAsStateWithLifecycle()

    LaunchedEffect(pinnedDeviceMac) {
        if (pinnedDeviceMac != null && pinnedDeviceMac != selectedMac) {
            viewModel.selectDevice(pinnedDeviceMac)
        }
    }

    val effectiveMac = pinnedDeviceMac ?: selectedMac
    val selectedDevice = devices.firstOrNull { it.device.macAddress == effectiveMac && it.isOnline }
    val onlineCount = devices.count { it.isOnline }

    LaunchedEffect(devices, effectiveMac) {
        if (pinnedDeviceMac == null && selectedDevice == null) {
            devices.firstOrNull { it.isOnline }?.let { viewModel.selectDevice(it.device.macAddress) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (pinnedDeviceMac == null) {
                    Text(
                        text = stringResource(R.string.control_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Text(
                    text = deviceSpecSummary(selectedDevice?.stateInfo?.info?.leds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pinnedDeviceMac == null) {
                DeviceSelector(
                    devices = devices,
                    selected = selectedDevice,
                    onSelect = { viewModel.selectDevice(it.device.macAddress) },
                )
            }
        }

        if (selectedDevice == null) {
            EmptyControlState()
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PowerAndBrightnessCard(
                device = selectedDevice,
                onTogglePower = { viewModel.setPower(selectedDevice, it) },
                onBrightnessCommit = { viewModel.setBrightness(selectedDevice, it) },
            )

            EffectsQuickRow(
                currentEffectId = selectedDevice.stateInfo?.state?.segment?.firstOrNull()?.effect,
                onSelect = { viewModel.setEffect(selectedDevice, it) },
            )

            SegmentTuningCard(
                device = selectedDevice,
                onSpeedCommit = { viewModel.setSpeed(selectedDevice, it) },
                onIntensityCommit = { viewModel.setIntensity(selectedDevice, it) },
            )

            ColorCard(
                device = selectedDevice,
                onPick = { viewModel.setPrimaryColor(selectedDevice, it) },
            )

            SyncCard(
                enabled = syncEnabled,
                onlineCount = onlineCount,
                onToggle = viewModel::setSyncEnabled,
            )

            NightlightCard(
                device = selectedDevice,
                onStart = { minutes -> viewModel.startNightlight(selectedDevice, minutes) },
                onCancel = { viewModel.cancelNightlight(selectedDevice) },
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmptyControlState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.control_no_device),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.control_no_device_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PowerAndBrightnessCard(
    device: DeviceWithState,
    onTogglePower: (Boolean) -> Unit,
    onBrightnessCommit: (Int) -> Unit,
) {
    val state = device.stateInfo?.state
    val isOn = state?.isOn == true
    val remoteBrightness = state?.brightness ?: 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(if (isOn) R.string.control_power_on else R.string.control_power_off),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "${remoteBrightness * PERCENT_MAX / MAX_CHANNEL_VALUE}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(checked = isOn, onCheckedChange = onTogglePower)
            }
            SliderWithCommit(
                value = remoteBrightness,
                max = MAX_CHANNEL_VALUE,
                enabled = isOn,
                label = stringResource(R.string.control_brightness),
                onCommit = onBrightnessCommit,
            )
        }
    }
}

@Composable
private fun EffectsQuickRow(currentEffectId: Int?, onSelect: (Int) -> Unit) {
    val effects = remember { EffectCatalog.popular }
    Column {
        val currentName = EffectCatalog.nameOf(currentEffectId)
        Text(
            text = if (currentName != null) {
                stringResource(R.string.control_current_effect, currentName)
            } else {
                stringResource(R.string.control_effect)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(effects, key = { it.id }) { effect ->
                FilterChip(
                    selected = effect.id == currentEffectId,
                    onClick = { onSelect(effect.id) },
                    label = { Text(effect.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

@Composable
private fun SegmentTuningCard(
    device: DeviceWithState,
    onSpeedCommit: (Int) -> Unit,
    onIntensityCommit: (Int) -> Unit,
) {
    val segment = device.stateInfo?.state?.segment?.firstOrNull()
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.control_tuning),
                style = MaterialTheme.typography.titleMedium,
            )
            SliderWithCommit(
                value = segment?.effectSpeed ?: 0,
                max = MAX_CHANNEL_VALUE,
                enabled = true,
                label = stringResource(R.string.control_speed),
                onCommit = onSpeedCommit,
            )
            SliderWithCommit(
                value = segment?.effectIntensity ?: 0,
                max = MAX_CHANNEL_VALUE,
                enabled = true,
                label = stringResource(R.string.control_intensity),
                onCommit = onIntensityCommit,
            )
        }
    }
}

@Composable
private fun ColorCard(device: DeviceWithState, onPick: (Int) -> Unit) {
    val primary = device.stateInfo?.state?.segment?.firstOrNull()?.colors?.firstOrNull()
    val primaryPacked = primary?.packRgb()

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.control_color),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                COLOR_PRESETS.forEach { packed ->
                    ColorDot(
                        packed = packed,
                        selected = primaryPacked == packed,
                        onClick = { onPick(packed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorDot(packed: Int, selected: Boolean, onClick: () -> Unit) {
    val outline = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xFF000000.toInt() or packed))
            .border(if (selected) 3.dp else 1.dp, outline, CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SyncCard(enabled: Boolean, onlineCount: Int, onToggle: (Boolean) -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.control_sync),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.control_sync_desc, onlineCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun NightlightCard(
    device: DeviceWithState,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val nightlight = device.stateInfo?.state?.nightlight
    val isActive = nightlight?.isOn == true
    var showDialog by remember { mutableStateOf(false) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.control_timer),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isActive) {
                    stringResource(R.string.control_timer_remaining, formatRemaining(nightlight?.remainingTime))
                } else {
                    stringResource(R.string.control_timer_idle)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showDialog = true }) {
                    Text(stringResource(R.string.control_timer_set))
                }
                if (isActive) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.control_timer_cancel))
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.control_timer_set)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TIMER_MINUTES.forEach { minutes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    showDialog = false
                                    onStart(minutes)
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        ) {
                            Text(text = stringResource(R.string.control_timer_minutes, minutes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * 带本地拖拽状态的滑块。
 *
 * 拖动过程中只更新本地值，松手才下发，避免每移动一像素就发一条 WebSocket 消息；
 * 未拖动时跟随设备回报值，多端操作也能同步。
 */
@Composable
private fun SliderWithCommit(
    value: Int,
    max: Int,
    enabled: Boolean,
    label: String,
    onCommit: (Int) -> Unit,
) {
    var sliderValue by remember(value, enabled) { mutableFloatStateOf(value.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!isDragging) sliderValue = value.toFloat()
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = "${(sliderValue * PERCENT_MAX / max).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                sliderValue = it
            },
            onValueChangeFinished = {
                isDragging = false
                onCommit(sliderValue.roundToInt())
            },
            valueRange = 0f..max.toFloat(),
            enabled = enabled,
        )
    }
}

/** 夜灯剩余时间按分钟显示；设备还没回报时给一个占位。 */
@Composable
private fun formatRemaining(remainingSeconds: Long?): String {
    if (remainingSeconds == null || remainingSeconds <= 0) {
        return stringResource(R.string.control_timer_pending)
    }
    val minutes = (remainingSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    return stringResource(R.string.control_timer_minutes, minutes.toInt())
}

private const val PERCENT_MAX = 100
private const val SECONDS_PER_MINUTE = 60L
