package ca.cgagnier.wlednativeandroid.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import ca.cgagnier.wlednativeandroid.shared.currentTimeMillis

/**
 * A user-favorited effect recipe.
 *
 * A favorite stores the full effect configuration (not just the effect id) so that recalling it
 * restores the look the user saved, including speed, intensity, palette and up to three colors.
 *
 * Colors are packed `0xRRGGBB` integers to keep the row a flat, converter-free record; the UI is
 * responsible for unpacking them into the channel list the WLED API expects.
 *
 * One favorite per effect: [effectId] is uniquely indexed, so re-saving an effect replaces it.
 */
@Entity(
    tableName = "FavoriteEffect",
    indices = [Index(value = ["effectId"], unique = true)],
)
data class FavoriteEffect(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** WLED runtime effect id (`seg.fx`). */
    val effectId: Int,

    /** Effect name captured at save time, used for display without querying the device. */
    val effectName: String,

    /** `seg.sx` (0-255), null when the device default was used. */
    val speed: Int? = null,

    /** `seg.ix` (0-255). */
    val intensity: Int? = null,

    /** `seg.pal` (palette id). */
    val palette: Int? = null,

    /** Primary color, packed `0xRRGGBB`. */
    val color1: Int? = null,

    /** Secondary color, packed `0xRRGGBB`. */
    val color2: Int? = null,

    /** Tertiary color, packed `0xRRGGBB`. */
    val color3: Int? = null,

    /** Sort key for the favorites list, most recent first. */
    val createdAt: Long = currentTimeMillis(),
) {
    /** Colors as the flat `0xRRGGBB` list the WLED API accepts, skipping unset entries. */
    val packedColors: List<Int> get() = listOfNotNull(color1, color2, color3)

    /** Colors expanded back into the three-component form `seg.col` expects. */
    val unpackedColors: List<List<Int>> get() = packedColors.map { rgb ->
        listOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
    }
}

/**
 * 把收藏记录里的参数写回段落。
 *
 * 只覆盖收藏时**确实存下来的**字段：没存过的（比如当时设备就没上色）保持现状，
 * 避免用默认值把用户当前的调色覆盖掉。
 *
 * 收藏的语义是「连同当时的观感一起存下来」，所以恢复时必须把参数一起恢复，
 * 否则收藏就退化成一个书签。
 */
fun Segment.restoreFrom(favorite: FavoriteEffect?): Segment {
    if (favorite == null) return this
    var restored = this
    favorite.speed?.let { restored = restored.copy(effectSpeed = it) }
    favorite.intensity?.let { restored = restored.copy(effectIntensity = it) }
    favorite.palette?.let { restored = restored.copy(palette = it) }
    if (favorite.unpackedColors.isNotEmpty()) {
        restored = restored.copy(colors = favorite.unpackedColors)
    }
    return restored
}
