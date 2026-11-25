package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class UserMods(
    // Battery values
    @SerialName("Battery level") val batteryLevel: List<JsonElement>? = null,
    @SerialName("Battery voltage") val batteryVoltage: List<JsonElement>? = null,
    @SerialName("AudioReactive") val audioReactive: AudioReactiveInfo? = null,
) {
    val batteryPercentage: Double?
        get() = (batteryLevel?.firstOrNull() as? JsonPrimitive)?.doubleOrNull

    val batteryVoltageValue: Double?
        get() = (batteryVoltage?.firstOrNull() as? JsonPrimitive)?.doubleOrNull
}

/**
 * `/json/info.u.AudioReactive`：固件按人类可读的字符串数组输出，例如
 * `"Audio Source": ["UDP sound sync", " - receiving"]`。
 *
 * 这里只取 App 需要展示的两项，其余字段忽略。
 */
@Serializable
data class AudioReactiveInfo(
    @SerialName("Audio Source") val audioSource: List<JsonElement>? = null,
    @SerialName("Sound Processing") val soundProcessing: List<JsonElement>? = null,
) {
    val audioSourceText: String?
        get() = audioSource.joinText()

    val soundProcessingText: String?
        get() = soundProcessing.joinText()

    private fun List<JsonElement>?.joinText(): String? = this
        ?.mapNotNull { (it as? JsonPrimitive)?.content }
        ?.joinToString("")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
