package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Leds(
    @SerialName("count") val count: Int? = null,
    @SerialName("pwr") val estimatedPowerUsed: Int? = null,
    @SerialName("fps") val fps: Int? = null,
    @SerialName("maxpwr") val maxPower: Int? = null,
    @SerialName("maxseg") val maxSegment: Int? = null,
    /**
     * 2D matrix dimensions reported by the device (`/json/info` -> `leds.matrix`).
     * 200 OK on 2D-configured devices; absent on 1D strips.
     */
    @SerialName("matrix") val matrix: Matrix? = null,
) {
    /** Number of LEDs as reported by the device. Never hardcode this in the UI. */
    val ledCount: Int get() = count ?: 0

    /** True when the device exposes a real 2D matrix (height > 1). */
    val is2D: Boolean get() = (matrix?.height ?: 1) > 1

    /** Width in pixels; falls back to the raw LED count for 1D devices. */
    val width: Int get() = matrix?.width ?: ledCount.coerceAtLeast(1)

    /** Height in pixels; 1 for 1D devices. */
    val height: Int get() = matrix?.height ?: 1
}

/**
 * 2D matrix geometry as reported by the firmware (`json.cpp` serializes
 * `Segment::maxWidth` / `Segment::maxHeight`).
 */
@Serializable
data class Matrix(
    @SerialName("w") val width: Int = 0,
    @SerialName("h") val height: Int = 0,
) {
    val isValid: Boolean get() = width > 0 && height > 0
}
