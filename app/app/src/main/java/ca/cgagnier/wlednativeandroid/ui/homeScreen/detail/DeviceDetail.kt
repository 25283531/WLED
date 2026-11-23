package ca.cgagnier.wlednativeandroid.ui.homeScreen.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.components.DeviceInfoTwoRows
import ca.cgagnier.wlednativeandroid.ui.control.ControlScreen

/**
 * 设备详情：原生控制界面。
 *
 * 原实现内嵌固件网页（WebView），这里改为原生控件；固件网页仍然保留，
 * 通过标题栏的「网页设置」用系统浏览器打开，作为调试/极客入口。
 */
@Composable
fun DeviceDetail(
    device: DeviceWithState,
    onItemEdit: (DeviceWithState) -> Unit,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            DeviceDetailAppBar(
                device = device,
                canNavigateBack = canNavigateBack,
                navigateUp = navigateUp,
                editItem = { onItemEdit(device) },
                openWebSettings = {
                    val url = device.device.getDeviceUrl()
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .clip(shape = MaterialTheme.shapes.large),
        ) {
            ControlScreen(
                pinnedDeviceMac = device.device.macAddress,
                applyStatusBarPadding = false,
            )
        }
    }
}

@Composable
fun DeviceDetailAppBar(
    device: DeviceWithState,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    editItem: () -> Unit,
    openWebSettings: () -> Unit,
) {
    TopAppBar(
        title = {
            DeviceInfoTwoRows(device = device, nameMaxLines = 1)
        },
        navigationIcon = {
            AnimatedVisibility(visible = canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.description_back_button),
                    )
                }
            }
        },
        actions = {
            TextButton(onClick = openWebSettings) {
                Text(
                    text = stringResource(R.string.device_web_settings),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (!device.isAPMode) {
                IconButton(onClick = editItem) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.edit_device),
                    )
                }
            }
        },
    )
}
