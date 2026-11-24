package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET/POST /json/cfg` 中 App 会用到的片段。
 *
 * 固件把 POST 上来的 JSON 合并进 cfg.json（只覆盖请求里出现的键），所以这里只声明
 * 需要读写的那几段，其余字段交给 `ignoreUnknownKeys` 忽略，不会误删设备上的其它配置。
 */
@Serializable
data class WledConfig(
    val nw: NetworkConfig? = null,
    val def: BootConfig? = null,
    /** 只写字段：true 表示保存后立刻重启，WiFi 变更需要它才会生效。 */
    val rb: Boolean? = null,
)

@Serializable
data class NetworkConfig(
    /** 固件保存的客户端网络列表，`ins[0]` 是当前生效的那一条。 */
    val ins: List<WifiEntry>? = null,
)

@Serializable
data class WifiEntry(
    val ssid: String? = null,
    val psk: String? = null,
    val bssid: String? = null,
)

@Serializable
data class BootConfig(
    /** 开机加载的预设编号，0 表示不加载预设（走编译期默认灯效）。 */
    @SerialName("ps")
    val presetId: Int? = null,
    /** 上电后是否自动开灯。 */
    @SerialName("on")
    val isOn: Boolean? = null,
    /** 上电后的亮度 0-255。 */
    @SerialName("bri")
    val brightness: Int? = null,
)
