package ca.cgagnier.wlednativeandroid.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
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
}
