package ca.cgagnier.wlednativeandroid.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ca.cgagnier.wlednativeandroid.model.FavoriteEffect
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteEffectDao {
    @Query("SELECT * FROM FavoriteEffect ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FavoriteEffect>>

    @Query("SELECT effectId FROM FavoriteEffect")
    fun observeFavoriteEffectIds(): Flow<List<Int>>

    @Query("SELECT * FROM FavoriteEffect WHERE effectId = :effectId")
    suspend fun findByEffectId(effectId: Int): FavoriteEffect?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEffect): Long

    @Delete
    suspend fun delete(favorite: FavoriteEffect)

    @Query("DELETE FROM FavoriteEffect WHERE effectId = :effectId")
    suspend fun deleteByEffectId(effectId: Int)

    @Query("SELECT COUNT(*) FROM FavoriteEffect")
    suspend fun count(): Int

    @Query("SELECT * FROM FavoriteEffect")
    suspend fun getAll(): List<FavoriteEffect>

    /** 固件灯效重新编号后，把收藏里的旧 fx id 迁移到新编号。 */
    @Query("UPDATE FavoriteEffect SET effectId = :newEffectId WHERE effectId = :oldEffectId")
    suspend fun updateEffectId(oldEffectId: Int, newEffectId: Int)
}
