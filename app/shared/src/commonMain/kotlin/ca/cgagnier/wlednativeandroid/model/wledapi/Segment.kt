package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Segment(
    @SerialName("id") val id: Int? = null,
    @SerialName("start") val start: Int? = null,
    @SerialName("stop") val stop: Int? = null,
    @SerialName("len") val length: Int? = null,
    @SerialName("grp") val grouping: Int? = null,
    @SerialName("spc") val spacing: Int? = null,
    @SerialName("on") val isOn: Boolean? = null,
    @SerialName("bri") val brightness: Int? = null,
    @SerialName("col") val colors: List<List<Int>>? = null,
    @SerialName("fx") val effect: Int? = null,
    @SerialName("sx") val effectSpeed: Int? = null,
    @SerialName("ix") val effectIntensity: Int? = null,
    @SerialName("pal") val palette: Int? = null,
    @SerialName("sel") val isSelected: Boolean? = null,
    @SerialName("rev") val isReversed: Boolean? = null,
    @SerialName("mi") val isMirrored: Boolean? = null,
    /** Segment name. Doubles as the text shown by the 2D "Scrolling Text" effect. */
    @SerialName("n") val name: String? = null,
    /** Effect custom parameter 1 (0-255). Meaning depends on the effect. */
    @SerialName("c1") val custom1: Int? = null,
    /** Effect custom parameter 2 (0-255). */
    @SerialName("c2") val custom2: Int? = null,
    /** Effect custom parameter 3 (0-31, 5 bit). E.g. text rotation for Scrolling Text. */
    @SerialName("c3") val custom3: Int? = null,
    @SerialName("o1") val check1: Boolean? = null,
    @SerialName("o2") val check2: Boolean? = null,
    @SerialName("o3") val check3: Boolean? = null,
)
