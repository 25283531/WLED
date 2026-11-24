package ca.cgagnier.wlednativeandroid.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.wledapi.ScannedNetwork
import ca.cgagnier.wlednativeandroid.ui.isle.GlassCard
import ca.cgagnier.wlednativeandroid.ui.isle.Glyph
import ca.cgagnier.wlednativeandroid.ui.isle.GlyphIcon
import ca.cgagnier.wlednativeandroid.ui.isle.IsleBackdrop
import ca.cgagnier.wlednativeandroid.ui.isle.IsleColors
import org.koin.androidx.compose.koinViewModel

/**
 * WiFi 原生页：读当前网络、扫描附近热点、连到新网络。
 *
 * 连接是「写配置 + 重启设备」：固件保存 `nw.ins[0]` 后自行重启，App 只负责把配置送到并
 * 明确告诉用户接下来会发生什么（设备会短暂离线，IP 可能变化）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiSettingsPage(
    address: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WifiSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingNetwork by remember { mutableStateOf<ScannedNetwork?>(null) }

    BackHandler(onBack = onBack)
    LaunchedEffect(address) { viewModel.load(address) }

    IsleBackdrop(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            PageHeader(title = stringResource(R.string.isle_wifi), onBack = onBack)

            CurrentNetworkCard(ssid = uiState.currentSsid)

            if (uiState.rebootPending) {
                NoticeCard(
                    title = stringResource(R.string.isle_wifi_connect_submitted),
                    body = stringResource(R.string.isle_wifi_reboot_hint),
                    tint = IsleColors.Cyan,
                )
            }

            when (uiState.error) {
                WifiSettingsViewModel.UiState.Error.SCAN -> NoticeCard(
                    title = stringResource(R.string.isle_wifi_scan_failed),
                    body = stringResource(R.string.isle_wifi_scan_failed_hint),
                    tint = IsleColors.Pink,
                )

                WifiSettingsViewModel.UiState.Error.CONNECT -> NoticeCard(
                    title = stringResource(R.string.isle_wifi_connect_failed),
                    body = stringResource(R.string.isle_wifi_connect_failed_hint),
                    tint = IsleColors.Pink,
                )

                null -> Unit
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.isle_wifi_nearby),
                    color = IsleColors.TextSecondary,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = IsleColors.Cyan,
                        strokeWidth = 1.6.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.isle_wifi_scanning),
                        color = IsleColors.TextMuted,
                        fontSize = 11.5.sp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.isle_wifi_rescan),
                        color = IsleColors.Cyan,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { viewModel.rescan() }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                    )
                }
            }

            when {
                uiState.networks.isNotEmpty() -> uiState.networks.forEach { network ->
                    NetworkRow(
                        network = network,
                        isCurrent = network.ssid == uiState.currentSsid,
                        enabled = uiState.connectingSsid == null,
                        onClick = {
                            if (network.enc == OPEN_NETWORK_ENC) {
                                viewModel.connect(network.ssid.orEmpty(), "")
                            } else {
                                pendingNetwork = network
                            }
                        },
                    )
                }

                !uiState.scanning && uiState.error == null -> Text(
                    text = stringResource(R.string.isle_wifi_no_networks),
                    color = IsleColors.TextMuted,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )

                else -> Unit
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    val network = pendingNetwork
    if (network != null) {
        ConnectSheet(
            network = network,
            submitting = uiState.connectingSsid != null,
            onDismiss = { pendingNetwork = null },
            onConnect = { password ->
                viewModel.connect(network.ssid.orEmpty(), password)
                pendingNetwork = null
            },
        )
    }
}

/** 固件 `WiFi.encryptionType == 0` 表示开放网络。 */
private const val OPEN_NETWORK_ENC = 0

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0x14FFFFFF), CircleShape)
                .border(1.dp, IsleColors.Stroke, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = Glyph.ChevronRight,
                modifier = Modifier.rotate(180f),
                size = 16.dp,
                tint = IsleColors.TextPrimary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp),
            color = IsleColors.TextPrimary,
        )
    }
}

@Composable
private fun CurrentNetworkCard(ssid: String?) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 14.dp) {
        Text(
            text = stringResource(R.string.isle_wifi_current_network),
            color = IsleColors.TextMuted,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(glyph = Glyph.Wifi, size = 18.dp, tint = IsleColors.Cyan)
            Spacer(Modifier.width(8.dp))
            Text(
                text = ssid ?: stringResource(R.string.isle_wifi_unknown),
                color = IsleColors.TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NoticeCard(title: String, body: String, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column {
            Text(text = title, color = tint, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            Text(
                text = body,
                color = IsleColors.TextSecondary,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun NetworkRow(
    network: ScannedNetwork,
    isCurrent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val ssid = network.ssid.orEmpty()
    val details = listOfNotNull(
        network.channel?.let { stringResource(R.string.isle_wifi_channel, it) },
        network.rssi?.let { stringResource(R.string.isle_wifi_signal, it) },
    ).joinToString(" · ")
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(enabled = enabled, onClick = onClick),
        cornerRadius = 16.dp,
        padding = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(
                glyph = Glyph.Wifi,
                size = 18.dp,
                tint = if (isCurrent) IsleColors.Cyan else IsleColors.TextSecondary,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ssid,
                    color = IsleColors.TextPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = details,
                    color = IsleColors.TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (isCurrent) {
                Text(
                    text = stringResource(R.string.isle_wifi_connected),
                    color = IsleColors.Cyan,
                    fontSize = 11.sp,
                )
            } else {
                Text(
                    text = if (network.enc == OPEN_NETWORK_ENC) {
                        stringResource(R.string.isle_wifi_open_network)
                    } else {
                        stringResource(R.string.isle_wifi_secured)
                    },
                    color = IsleColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectSheet(
    network: ScannedNetwork,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit,
) {
    var password by remember(network.ssid) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF14161C),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.isle_wifi_connect_title, network.ssid.orEmpty()),
                color = IsleColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.isle_wifi_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(48.dp)
                    .background(IsleColors.Cyan.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .border(1.dp, IsleColors.Cyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable(enabled = !submitting) { onConnect(password) },
                contentAlignment = Alignment.Center,
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = IsleColors.Cyan,
                        strokeWidth = 1.8.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.isle_wifi_connect),
                        color = IsleColors.Cyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Text(
                text = stringResource(R.string.isle_wifi_reboot_hint),
                color = IsleColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
