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
    @SerialName("Audio Source") val audioSource: List<JsonElement>? = null,
    @SerialName("Sound Processing") val soundProcessing: List<JsonElement>? = null,
) {
    val batteryPercentage: Double?
        get() = (batteryLevel?.firstOrNull() as? JsonPrimitive)?.doubleOrNull

    val batteryVoltageValue: Double?
        get() = (batteryVoltage?.firstOrNull() as? JsonPrimitive)?.doubleOrNull

    /**
     * `/json/info.u["Audio Source"]`，例如 `["UDP sound sync", " - receiving"]`。
     *
     * 固件把每一项拆成多个字符串片段（有的还带 HTML），这里只拼接纯文本。
     */
    val audioSourceText: String?
        get() = audioSource.joinText()

    /** `/json/info.u["Sound Processing"]`，正常运行时是 `["running"]`。 */
    val soundProcessingText: String?
        get() = soundProcessing.joinText()

    private fun List<JsonElement>?.joinText(): String? = this
        ?.mapNotNull { (it as? JsonPrimitive)?.content }
        ?.joinToString("")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
