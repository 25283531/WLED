package ca.cgagnier.wlednativeandroid.repository.migrations

import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.repository.UserPreferences
import ca.cgagnier.wlednativeandroid.test.TestJson
import com.diffplug.selfie.Selfie.expectSelfie
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserPreferencesV1ToV2Test {

    private val migration = UserPreferencesV1ToV2()

    @Test
    fun shouldMigrate_whenVersionOneOrLower_returnsTrue() = runTest {
        assertTrue(migration.shouldMigrate(UserPreferences(version = 1)))
        assertTrue(migration.shouldMigrate(UserPreferences(version = 0)))
    }

    @Test
    fun shouldMigrate_whenVersionTwo_returnsFalse() = runTest {
        assertFalse(migration.shouldMigrate(UserPreferences(version = 2)))
    }

    // 显式 Unit 返回类型，避免 DiskSelfie 的返回类型推断干扰 JUnit 的方法签名检查
    @Test
    fun migrate_resetsSyncFlagAndBumpsVersionTo2(): Unit = runTest {
        val oldPrefs = UserPreferences(
            version = 1,
            theme = ThemeSettings.Dark,
            selectedDeviceAddress = "192.168.1.10",
            multiLampSyncEnabled = true,
        )

        val migrated = migration.migrate(oldPrefs)

        expectSelfie(TestJson.preferences.encodeToString(migrated)).toMatchDisk()
    }
}
