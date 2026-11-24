package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.Serializable

/**
 * `GET /json/net` 的响应：设备扫描到的 WiFi 列表。
 *
 * 固件的扫描是异步的：扫描进行中返回空数组，扫描完成才带 `networks`，随后固件会再起一轮
 * 扫描。所以调用方需要轮询，直到列表非空。
 */
@Serializable
data class NetworkScan(
    val networks: List<ScannedNetwork>? = null,
)

@Serializable
data class ScannedNetwork(
    val ssid: String? = null,
    val bssid: String? = null,
    /** 信号强度，dBm。 */
    val rssi: Int? = null,
    val channel: Int? = null,
    /** 固件 `WiFi.encryptionType`：0 表示开放网络。 */
    val enc: Int? = null,
)
