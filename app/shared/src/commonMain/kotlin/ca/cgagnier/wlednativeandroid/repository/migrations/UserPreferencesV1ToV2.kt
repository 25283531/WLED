package ca.cgagnier.wlednativeandroid.repository.migrations

import androidx.datastore.core.DataMigration
import ca.cgagnier.wlednativeandroid.repository.UserPreferences

/**
 * V1 -> V2：新增「多灯同步开关」。
 *
 * 之前这个开关只存在于设备页 ViewModel 的内存状态里，App 重启即失效。
 * 现在落到 UserPreferences 持久化，设备页与灯效库读的是同一个值。
 *
 * 迁移时保持关闭 —— 与旧行为一致（旧版本重启后本来就是关的）。
 */
class UserPreferencesV1ToV2 : DataMigration<UserPreferences> {
    override suspend fun cleanUp() {
        // No cleanup required for V1 to V2 migration
    }

    override suspend fun migrate(currentData: UserPreferences): UserPreferences = currentData.copy(
        multiLampSyncEnabled = false,
        version = 2,
    )

    override suspend fun shouldMigrate(currentData: UserPreferences): Boolean = currentData.version <= 1
}
