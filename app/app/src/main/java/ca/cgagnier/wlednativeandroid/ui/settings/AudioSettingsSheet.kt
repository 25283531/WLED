package ca.cgagnier.wlednativeandroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.audio.AudioStreamState
import ca.cgagnier.wlednativeandroid.audio.CaptureSource
import ca.cgagnier.wlednativeandroid.ui.isle.GlassCard
import ca.cgagnier.wlednativeandroid.ui.isle.Glyph
import ca.cgagnier.wlednativeandroid.ui.isle.GlyphIcon
import ca.cgagnier.wlednativeandroid.ui.isle.IsleColors

/**
 * 设备页「音频来源」面板。
 *
 * 来源决定设备端行为：选设备麦克风时不启动手机采集；选 APP 音频时由前台服务
 * 采集手机声音并单播到设备，划掉任务/锁屏都继续发送。
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

            AudioSourceRow(
                label = stringResource(R.string.isle_audio_source_device),
                selected = uiState.source == DeviceAudioSource.DEVICE,
                enabled = !uiState.switching,
                onClick = { onSelectSource(DeviceAudioSource.DEVICE) },
            )
            AudioSourceRow(
                label = stringResource(R.string.isle_audio_source_app),
                selected = uiState.source == DeviceAudioSource.APP,
                enabled = !uiState.switching,
                onClick = { onSelectSource(DeviceAudioSource.APP) },
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

            val active = streamState is AudioStreamState.Active || streamState is AudioStreamState.AskingDowngrade
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
                    enabled = uiState.source == DeviceAudioSource.APP &&
                        !uiState.switching &&
                        !uiState.rebootPending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(text = stringResource(R.string.isle_audio_start))
                }
            }

            OutlinedButton(
                onClick = onRetryCapture,
                enabled = uiState.source == DeviceAudioSource.APP,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.isle_audio_retry_capture))
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

/**
 * 内录静音后的用户确认流程。
 *
 * 第一次询问"是否在播放"，第二次确认"是否改用麦克风"；
 * 只有两次都点确认，才会调用 [onConfirmSwitch]。后台由通知先问，第二次确认统一进 APP 完成。
 */
@Composable
fun AudioDowngradeDialogs(
    streamState: AudioStreamState,
    onKeepCapturing: () -> Unit,
    onConfirmSwitch: () -> Unit,
) {
    if (streamState !is AudioStreamState.AskingDowngrade) {
        return
    }
    var confirming by remember(streamState) { mutableStateOf(false) }
    if (!confirming) {
        AlertDialog(
            onDismissRequest = onKeepCapturing,
            title = { Text(stringResource(R.string.isle_audio_ask_title)) },
            text = { Text(stringResource(R.string.isle_audio_ask_body)) },
            confirmButton = {
                TextButton(onClick = { confirming = true }) {
                    Text(stringResource(R.string.isle_audio_ask_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onKeepCapturing) {
                    Text(stringResource(R.string.isle_audio_ask_no))
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onKeepCapturing,
            title = { Text(stringResource(R.string.isle_audio_confirm_title)) },
            text = { Text(stringResource(R.string.isle_audio_confirm_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmSwitch) {
                    Text(stringResource(R.string.isle_audio_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onKeepCapturing) {
                    Text(stringResource(R.string.isle_audio_cancel))
                }
            },
        )
    }
}

/**
 * 内录（A）启动失败时的降级询问。
 *
 * 只处理「投屏授权被拒/采集起不来」两种情况；用户同意后由调用方用麦克风重新启动，
 * 不同意则结束本次采集，不保留失败状态。
 */
@Composable
fun AudioUnavailableDialog(
    streamState: AudioStreamState,
    onUseMicrophone: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val failed = streamState as? AudioStreamState.Failed ?: return
    if (failed.source != CaptureSource.PLAYBACK) return
    val host = failed.host?.takeIf { it.isNotBlank() } ?: return
    if (failed.failure != AudioStreamState.Failed.Failure.PERMISSION &&
        failed.failure != AudioStreamState.Failed.Failure.CAPTURE_START
    ) {
        return
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.isle_audio_unavailable_title)) },
        text = { Text(stringResource(R.string.isle_audio_unavailable_body)) },
        confirmButton = {
            TextButton(onClick = { onUseMicrophone(host) }) {
                Text(stringResource(R.string.isle_audio_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.isle_audio_cancel))
            }
        },
    )
}

@Composable
private fun AudioSourceRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        cornerRadius = 16.dp,
        padding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = when {
                    !enabled -> IsleColors.TextMuted
                    selected -> IsleColors.Cyan
                    else -> IsleColors.TextPrimary
                },
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
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
        state.source == CaptureSource.MICROPHONE -> stringResource(R.string.isle_audio_status_mic)
        else -> stringResource(R.string.isle_audio_status_playback)
    }
    is AudioStreamState.AskingDowngrade -> stringResource(R.string.isle_audio_status_asking)
    is AudioStreamState.Failed -> stringResource(R.string.isle_audio_status_failed)
}
