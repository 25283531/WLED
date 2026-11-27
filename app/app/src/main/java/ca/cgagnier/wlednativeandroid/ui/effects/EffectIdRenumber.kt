package ca.cgagnier.wlednativeandroid.ui.effects

import android.content.Context
import ca.cgagnier.wlednativeandroid.repository.FavoriteEffectDao

/**
 * 固件灯效重新紧凑编号后的一次性收藏迁移。
 *
 * 旧表为 0..117，其中 17 个已下线灯效保留空槽；重新编号后空槽被压掉，编号变为 0..100。
 * 收藏表按 `effectId` 唯一索引，若不迁移会指向错误灯效。这里按旧 id 精确搬迁：
 * - 已下线灯效的收藏直接删除（固件里已不存在）；
 * - 其余按 [OLD_IDS_IN_NEW_ORDER] 的下标映射。
 *
 * 用 SharedPreferences 记录迁移版本，保证只执行一次；重复执行会把新编号当成旧编号再次压缩。
 * 迁移放在 Application 启动的后台协程里，收藏列表的 Flow 会收到最终结果。
 */
object EffectIdRenumber {

    private const val PREFS_NAME = "effect_id_renumber"
    private const val KEY_VERSION = "version"
    private const val CURRENT_VERSION = 1

    /** 迁移前旧效果表长度（0..117）。 */
    private const val OLD_EFFECT_COUNT = 118

    /** 下标即新编号，元素是旧编号。 */
    private val OLD_IDS_IN_NEW_ORDER = intArrayOf(
        0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 15, 16, 17, 18, 21, 23, 25, 26,
        28, 29, 30, 31, 32, 33, 34, 35, 39, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56,
        57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75,
        76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94,
        95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110,
        111, 112, 113, 114, 115, 116, 117,
    )

    suspend fun migrateFavorites(context: Context, dao: FavoriteEffectDao) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_VERSION, 0) >= CURRENT_VERSION) return

        val kept = OLD_IDS_IN_NEW_ORDER.toSet()
        val favorites = dao.getAll()

        // 先删已下线灯效，再按旧编号升序搬迁；升序可保证目标槽位先被腾空，避免唯一索引冲突。
        favorites
            .filter { it.effectId in 0 until OLD_EFFECT_COUNT && it.effectId !in kept }
            .forEach { dao.delete(it) }

        favorites
            .filter { it.effectId in 0 until OLD_EFFECT_COUNT && it.effectId in kept }
            .sortedBy { it.effectId }
            .forEach { favorite ->
                val newId = OLD_IDS_IN_NEW_ORDER.indexOf(favorite.effectId)
                if (newId != favorite.effectId) {
                    dao.updateEffectId(favorite.effectId, newId)
                }
            }

        prefs.edit().putInt(KEY_VERSION, CURRENT_VERSION).apply()
    }
}
