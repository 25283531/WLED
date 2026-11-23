package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState

/**
 * 顶部设备选择器：控制页、灯效页共用。
 *
 * 只列在线设备——离线设备发不了指令，列出来只会让人点了没反应。
 */
@Composable
fun DeviceSelector(
    devices: List<DeviceWithState>,
    selected: DeviceWithState?,
    onSelect: (DeviceWithState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onlineDevices = remember(devices) { devices.filter { it.isOnline } }
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = onlineDevices.size > 1) { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected?.let { deviceName(it.device) }
                        ?: stringResource(R.string.device_selector_no_device),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp),
                )
                if (onlineDevices.size > 1) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = stringResource(R.string.device_selector_switch),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            onlineDevices.forEach { device ->
                DropdownMenuItem(
                    text = { Text(deviceName(device.device)) },
                    onClick = {
                        expanded = false
                        onSelect(device)
                    },
                )
            }
        }
    }
}

/**
 * 设备规格摘要，例如「207 颗 · 9×23 蛇形」。
 *
 * 尺寸全部来自设备自述（`/json/info` 的 `leds.count` 与 `leds.matrix`），没有任何硬编码，
 * 因此换硬件形态不需要改 App。
 */
@Composable
fun deviceSpecSummary(leds: Leds?): String {
    if (leds == null || leds.ledCount <= 0) return stringResource(R.string.device_spec_unknown)
    if (!leds.is2D) return stringResource(R.string.device_spec_led_count, leds.ledCount)
    return stringResource(
        R.string.device_spec_matrix,
        leds.ledCount,
        leds.width,
        leds.height,
    )
}
