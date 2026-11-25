package ca.cgagnier.wlednativeandroid.ui.settings

import android.content.Context

/**
 * 记住每台灯选择的音频来源。
 *
 * 设备 cfg 只能区分「本地麦克风」和「接收网络音频」，蓝牙与直通在固件看来完全一样，
 * 所以这两种模式必须由 App 按设备地址单独记住，否则重进页面只会显示成直通。
 */
class AudioSourcePreferenceStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun sourceFor(address: String): DeviceAudioSource? {
        val stored = preferences.getString(address, null) ?: return null
        return DeviceAudioSource.entries.firstOrNull { it.name == stored }
    }

    fun save(address: String, source: DeviceAudioSource) {
        preferences.edit().putString(address, source.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "audio_source_prefs"
    }
}
