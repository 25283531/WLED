// 由 tools/gen_palette_catalog.py 自动生成，请勿手工编辑。
// 数据源: firmware/wled00/FX_fcn.cpp 的 JSON_palette_names（固件 /json/palettes 的同一份数据）
package ca.cgagnier.wlednativeandroid.ui.effects

/**
 * 一个固件调色板。
 *
 * @property id 运行时调色板值，可直接写入 `seg.pal`。
 * @property name 固件显示名。带 `*` 前缀的是「只取当前颜色槽位」的特殊调色板，
 *   不随时间变化 —— 例如 `* Color 1` 就是固件界面上的 "Primary Color"。
 */
data class PaletteInfo(
    val id: Int,
    val name: String,
    val isStatic: Boolean,
) {
    /** 去掉 `*` 前缀后的规范名，便于按语义反查（`* Color 1` -> `Color 1`）。 */
    val canonicalName: String get() = name.removePrefix("*").trim()
}

object PaletteCatalog {
    val all: List<PaletteInfo> = listOf(
        PaletteInfo(0, "Default", false),
        PaletteInfo(1, "* Random Cycle", true),
        PaletteInfo(2, "* Color 1", true),
        PaletteInfo(3, "* Colors 1&2", true),
        PaletteInfo(4, "* Color Gradient", true),
        PaletteInfo(5, "* Colors Only", true),
        PaletteInfo(6, "Party", false),
        PaletteInfo(7, "Cloud", false),
        PaletteInfo(8, "Lava", false),
        PaletteInfo(9, "Ocean", false),
        PaletteInfo(10, "Forest", false),
        PaletteInfo(11, "Rainbow", false),
        PaletteInfo(12, "Rainbow Bands", false),
        PaletteInfo(13, "Sunset", false),
        PaletteInfo(14, "Rivendell", false),
        PaletteInfo(15, "Breeze", false),
        PaletteInfo(16, "Red & Blue", false),
        PaletteInfo(17, "Yellowout", false),
        PaletteInfo(18, "Analogous", false),
        PaletteInfo(19, "Splash", false),
        PaletteInfo(20, "Pastel", false),
        PaletteInfo(21, "Sunset 2", false),
        PaletteInfo(22, "Beach", false),
        PaletteInfo(23, "Vintage", false),
        PaletteInfo(24, "Departure", false),
        PaletteInfo(25, "Landscape", false),
        PaletteInfo(26, "Beech", false),
        PaletteInfo(27, "Sherbet", false),
        PaletteInfo(28, "Hult", false),
        PaletteInfo(29, "Hult 64", false),
        PaletteInfo(30, "Drywet", false),
        PaletteInfo(31, "Jul", false),
        PaletteInfo(32, "Grintage", false),
        PaletteInfo(33, "Rewhi", false),
        PaletteInfo(34, "Tertiary", false),
        PaletteInfo(35, "Fire", false),
        PaletteInfo(36, "Icefire", false),
        PaletteInfo(37, "Cyane", false),
        PaletteInfo(38, "Light Pink", false),
        PaletteInfo(39, "Autumn", false),
        PaletteInfo(40, "Magenta", false),
        PaletteInfo(41, "Magred", false),
        PaletteInfo(42, "Yelmag", false),
        PaletteInfo(43, "Yelblu", false),
        PaletteInfo(44, "Orange & Teal", false),
        PaletteInfo(45, "Tiamat", false),
        PaletteInfo(46, "April Night", false),
        PaletteInfo(47, "Orangery", false),
        PaletteInfo(48, "C9", false),
        PaletteInfo(49, "Sakura", false),
        PaletteInfo(50, "Aurora", false),
        PaletteInfo(51, "Atlantica", false),
        PaletteInfo(52, "C9 2", false),
        PaletteInfo(53, "C9 New", false),
        PaletteInfo(54, "Temperature", false),
        PaletteInfo(55, "Aurora 2", false),
        PaletteInfo(56, "Retro Clown", false),
        PaletteInfo(57, "Candy", false),
        PaletteInfo(58, "Toxy Reaf", false),
        PaletteInfo(59, "Fairy Reaf", false),
        PaletteInfo(60, "Semi Blue", false),
        PaletteInfo(61, "Pink Candy", false),
        PaletteInfo(62, "Red Reaf", false),
        PaletteInfo(63, "Aqua Flash", false),
        PaletteInfo(64, "Yelblu Hot", false),
        PaletteInfo(65, "Lite Light", false),
        PaletteInfo(66, "Red Flash", false),
        PaletteInfo(67, "Blink Red", false),
        PaletteInfo(68, "Red Shift", false),
        PaletteInfo(69, "Red Tide", false),
        PaletteInfo(70, "Candy2", false),
        PaletteInfo(71, "Traffic Light", false),
        PaletteInfo(72, "Fireworks", false),
    )

    private val byId: Map<Int, PaletteInfo> = all.associateBy { it.id }

    fun find(id: Int?): PaletteInfo? = id?.let { byId[it] }

    fun nameOf(id: Int?): String? = find(id)?.name

    /**
     * 按名字反查调色板 id。
     *
     * 与灯效同理：**不要硬编码调色板 id**。固件在调色板数组中间插入一个新项，
     * 后面所有 id 都会前移，写死的数字会静默变成另一个调色板。
     * 先试原名，再试去掉 `*` 的规范名。
     */
    fun byName(name: String): PaletteInfo? {
        val trimmed = name.trim()
        return all.firstOrNull { it.name == trimmed }
            ?: all.firstOrNull { it.canonicalName == trimmed }
    }
}
