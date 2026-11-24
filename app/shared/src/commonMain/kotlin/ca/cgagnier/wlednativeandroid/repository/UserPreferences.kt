package ca.cgagnier.wlednativeandroid.repository

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val selectedDeviceAddress: String = "",
    val hasMigratedSharedPref: Boolean = false,
    val theme: ThemeSettings = ThemeSettings.Auto,
    val automaticDiscovery: Boolean = true,
    val version: Int = 2,
    val showOfflineLast: Boolean = true,
    val sendCrashData: Boolean = false,
    val sendPerformanceData: Boolean = false,
    val lastUpdateCheckDate: Long = 0L,
    val dateLastWritten: Long = 0L,
    val showHiddenDevices: Boolean = false,
    val lastChangelogVersionSeen: String = "",

    /**
     * 多灯同步开关。
     *
     * 这是**设备级**偏好：一次开启后，设备页和灯效库的批量下发都跟着它走。
     * 以前它只存在于设备页 ViewModel 的内存状态里，App 一重启就丢，
     * 而且灯效库根本读不到 —— 开关与实际行为对不上。
     */
    val multiLampSyncEnabled: Boolean = false,
)
