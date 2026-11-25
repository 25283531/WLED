package ca.cgagnier.wlednativeandroid.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.audio.AudioStreamState
import ca.cgagnier.wlednativeandroid.audio.AudioTransportType
import ca.cgagnier.wlednativeandroid.ui.isle.GlassCard
import ca.cgagnier.wlednativeandroid.ui.isle.Glyph
import ca.cgagnier.wlednativeandroid.ui.isle.GlyphIcon
import ca.cgagnier.wlednativeandroid.ui.isle.IsleColors

/**
 * 设备页「音频来源」面板。
 *
 * 三种来源：
 * - 设备麦克风：手机不发送任何音频；
 * - 蓝牙：内录 → BLE GATT；
 * - 直通：内录 → UDP。
 *
 * 蓝牙与直通都依赖 Android 10 的内录能力，低版本系统上置灰并说明原因。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsSheet(
    uiState: AudioSettingsViewModel.UiState,
    streamState: AudioStreamState,
    onSelectSource: (DeviceAudioSource) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetryCapture: () -> Unit,
    onOpenBatterySettings: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.isle_audio_source),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 2.dp),
            )

            val android10Missing = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            AudioSourceRow(
                label = stringResource(R.string.isle_audio_source_device),
                selected = uiState.source == DeviceAudioSource.DEVICE,
                enabled = !uiState.switching,
                onClick = { onSelectSource(DeviceAudioSource.DEVICE) },
            )
            AudioSourceRow(
                label = stringResource(R.string.isle_audio_source_ble),
                description = when {
                    uiState.bleSupported -> null
                    android10Missing -> stringResource(R.string.isle_audio_needs_android10)
                    else -> stringResource(R.string.isle_audio_ble_unsupported)
                },
                selected = uiState.source == DeviceAudioSource.BLE,
                enabled = uiState.bleSupported && !uiState.switching,
                onClick = { onSelectSource(DeviceAudioSource.BLE) },
            )
            AudioSourceRow(
                label = stringResource(R.string.isle_audio_source_passthrough),
                description = if (uiState.playbackSupported) {
                    null
                } else {
                    stringResource(R.string.isle_audio_needs_android10)
                },
                selected = uiState.source == DeviceAudioSource.PLAYBACK,
                enabled = uiState.playbackSupported && !uiState.switching,
                onClick = { onSelectSource(DeviceAudioSource.PLAYBACK) },
            )

            if (uiState.switching || uiState.rebootPending) {
                Text(
                    text = stringResource(R.string.isle_audio_switch_pending),
                    color = IsleColors.Cyan,
                    fontSize = 12.sp,
                )
            }
            if (uiState.switchFailed) {
                Text(
                    text = stringResource(R.string.isle_audio_switch_failed),
                    color = IsleColors.Pink,
                    fontSize = 12.sp,
                )
                Text(
                    text = stringResource(R.string.isle_audio_switch_failed_hint),
                    color = IsleColors.TextMuted,
                    fontSize = 11.sp,
                )
            }

            AudioStatusCard(
                streamState = streamState,
                deviceAudioStatus = uiState.deviceAudioStatus,
                modifier = Modifier.padding(top = 4.dp),
            )

            val canStream = when (uiState.source) {
                DeviceAudioSource.BLE -> uiState.bleSupported
                DeviceAudioSource.PLAYBACK -> uiState.playbackSupported
                else -> false
            } && !uiState.switching && !uiState.rebootPending
            val active = streamState is AudioStreamState.Active
            if (active) {
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(text = stringResource(R.string.isle_audio_stop))
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = canStream,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(text = stringResource(R.string.isle_audio_start))
                }
            }

            OutlinedButton(
                onClick = onRetryCapture,
                enabled = canStream,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.isle_audio_restart))
            }

            Text(
                text = stringResource(R.string.isle_audio_hint),
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenBatterySettings)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.isle_audio_battery_hint),
                    color = IsleColors.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                GlyphIcon(glyph = Glyph.ChevronRight, size = 14.dp, tint = IsleColors.TextMuted)
            }
        }
    }
}

@Composable
private fun AudioSourceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String? = null,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        cornerRadius = 16.dp,
        padding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = when {
                        !enabled -> IsleColors.TextMuted
                        selected -> IsleColors.Cyan
                        else -> IsleColors.TextPrimary
                    },
                    fontSize = 14.sp,
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        color = IsleColors.TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (selected) {
                GlyphIcon(glyph = Glyph.Check, size = 16.dp, tint = IsleColors.Cyan)
            }
        }
    }
}

@Composable
private fun AudioStatusCard(
    streamState: AudioStreamState,
    deviceAudioStatus: String?,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxWidth(), cornerRadius = 18.dp, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.isle_audio_status),
                color = IsleColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = statusText(streamState),
                color = when {
                    streamState is AudioStreamState.Active && streamState.deviceUnreachable -> IsleColors.Pink
                    streamState is AudioStreamState.Active -> IsleColors.Online
                    else -> IsleColors.TextPrimary
                },
                fontSize = 13.sp,
            )
        }
        if (!deviceAudioStatus.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.isle_audio_device_status, deviceAudioStatus),
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun statusText(state: AudioStreamState): String = when (state) {
    AudioStreamState.Idle -> stringResource(R.string.isle_audio_status_idle)
    is AudioStreamState.Active -> when {
        state.deviceUnreachable -> stringResource(R.string.isle_audio_status_unreachable)
        state.transport == AudioTransportType.BLE -> stringResource(R.string.isle_audio_status_ble)
        else -> stringResource(R.string.isle_audio_status_passthrough)
    }
    is AudioStreamState.Failed -> stringResource(R.string.isle_audio_status_failed)
}
