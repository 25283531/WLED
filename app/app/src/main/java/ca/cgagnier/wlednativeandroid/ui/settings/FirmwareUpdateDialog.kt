package ca.cgagnier.wlednativeandroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.isle.GlassCard
import ca.cgagnier.wlednativeandroid.ui.isle.Glyph
import ca.cgagnier.wlednativeandroid.ui.isle.GlyphIcon
import ca.cgagnier.wlednativeandroid.ui.isle.IsleColors

/**
 * 原生固件升级弹窗：展示新版本号与更新内容，由用户决定是否更新。
 *
 * 升级过程复用设备自己的 `/update` 接口，不再跳转固件 web 升级页。
 */
@Composable
fun FirmwareUpdateDialog(
    state: FirmwareUpdateState,
    onStartUpdate: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (state.canDismiss) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = state.canDismiss,
            dismissOnClickOutside = state.canDismiss,
        ),
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, padding = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(glyph = Glyph.Chip, size = 18.dp, tint = IsleColors.Violet)
                Text(
                    text = stringResource(R.string.isle_fw_dialog_title),
                    color = IsleColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            when (state.phase) {
                FirmwareUpdatePhase.Idle, FirmwareUpdatePhase.Checking -> CheckingContent()
                FirmwareUpdatePhase.UpToDate -> UpToDateContent(state)
                FirmwareUpdatePhase.Available -> AvailableContent(state)
                FirmwareUpdatePhase.Downloading -> DownloadingContent(state)
                FirmwareUpdatePhase.Installing -> InstallingContent()
                FirmwareUpdatePhase.Done -> DoneContent()
                FirmwareUpdatePhase.Failed -> FailedContent(state)
            }

            Spacer(Modifier.height(18.dp))
            DialogActions(
                state = state,
                onStartUpdate = onStartUpdate,
                onRetry = onRetry,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun CheckingContent() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.isle_fw_checking),
            color = IsleColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun UpToDateContent(state: FirmwareUpdateState) {
    Text(
        text = stringResource(R.string.isle_fw_up_to_date),
        color = IsleColors.TextPrimary,
        fontSize = 15.sp,
    )
    state.currentVersion?.let {
        VersionLine(label = stringResource(R.string.isle_fw_current_version), version = it)
    }
}

@Composable
private fun AvailableContent(state: FirmwareUpdateState) {
    val release = state.release
    state.currentVersion?.let {
        VersionLine(label = stringResource(R.string.isle_fw_current_version), version = it)
    }
    release?.version?.let {
        VersionLine(
            label = stringResource(R.string.isle_fw_new_version),
            version = it,
            highlight = true,
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.isle_fw_notes_title),
        color = IsleColors.TextSecondary,
        fontSize = 12.sp,
    )
    val notes = release?.notes?.takeIf { it.isNotBlank() }
    Text(
        text = notes ?: stringResource(R.string.isle_fw_notes_empty),
        color = IsleColors.TextPrimary,
        fontSize = 13.sp,
        modifier = Modifier
            .padding(top = 6.dp)
            .heightIn(max = 180.dp)
            .verticalScroll(rememberScrollState()),
    )
}

@Composable
private fun DownloadingContent(state: FirmwareUpdateState) {
    Text(
        text = stringResource(R.string.isle_fw_downloading, state.progress),
        color = IsleColors.TextPrimary,
        fontSize = 14.sp,
    )
    LinearProgressIndicator(
        progress = { state.progress / 100f },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
    state.release?.version?.let {
        VersionLine(label = stringResource(R.string.isle_fw_new_version), version = it)
    }
}

@Composable
private fun InstallingContent() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.isle_fw_installing),
            color = IsleColors.TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
    Text(
        text = stringResource(R.string.isle_fw_installing_hint),
        color = IsleColors.TextMuted,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun DoneContent() {
    Text(
        text = stringResource(R.string.isle_fw_done),
        color = IsleColors.TextPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    )
    Text(
        text = stringResource(R.string.isle_fw_done_hint),
        color = IsleColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun FailedContent(state: FirmwareUpdateState) {
    val message = when (state.failure) {
        FirmwareFailure.NoDevice -> stringResource(R.string.isle_fw_failure_no_device)
        FirmwareFailure.DeviceOffline -> stringResource(R.string.isle_fw_failure_offline)
        FirmwareFailure.ModelMismatch -> stringResource(R.string.isle_fw_failure_model)
        FirmwareFailure.Check -> stringResource(R.string.isle_fw_failure_check)
        FirmwareFailure.Download -> stringResource(R.string.isle_fw_failure_download)
        FirmwareFailure.Checksum -> stringResource(R.string.isle_fw_failure_checksum)
        FirmwareFailure.Upload -> stringResource(R.string.isle_fw_failure_upload)
        null -> stringResource(R.string.isle_fw_failure_check)
    }
    Text(text = message, color = IsleColors.TextPrimary, fontSize = 14.sp)
    state.detail?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = it,
            color = IsleColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun VersionLine(label: String, version: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = IsleColors.TextSecondary, fontSize = 12.sp)
        Text(
            text = version,
            color = if (highlight) IsleColors.Cyan else IsleColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (highlight) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun DialogActions(
    state: FirmwareUpdateState,
    onStartUpdate: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state.phase) {
            FirmwareUpdatePhase.Downloading, FirmwareUpdatePhase.Installing -> Unit

            FirmwareUpdatePhase.Available -> {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.isle_fw_later))
                }
                Button(onClick = onStartUpdate) {
                    Text(stringResource(R.string.isle_fw_update_now))
                }
            }

            FirmwareUpdatePhase.Failed -> {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.isle_fw_close))
                }
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.isle_fw_retry))
                }
            }

            FirmwareUpdatePhase.Checking -> Unit

            else -> {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.isle_fw_close))
                }
            }
        }
    }
}
