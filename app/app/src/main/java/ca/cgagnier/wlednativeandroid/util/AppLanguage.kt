package ca.cgagnier.wlednativeandroid.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** 应用内语言。默认简体中文。 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    Chinese("zh", "简体中文"),
    English("en", "English"),
    French("fr", "Français"),
    German("de", "Deutsch"),
    ;

    companion object {
        val Default = Chinese
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { tag != null && tag.startsWith(it.tag) } ?: Default
    }
}

/**
 * 语言管理：
 * - Android 13+ 使用系统「分应用语言」(LocaleManager)，与系统设置互通；
 * - 更低版本保存在 SharedPreferences，并在 attachBaseContext 中包装 Context。
 */
object AppLanguageManager {
    private const val PREFS = "app_language_prefs"
    private const val KEY = "language"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) return AppLanguage.fromTag(locales[0].language)
        }
        return AppLanguage.fromTag(prefs(context).getString(KEY, null))
    }

    /** 在 Application.onCreate 调用：首次启动时把应用语言设为默认中文。 */
    fun applyOnStartup(context: Context) {
        val stored = prefs(context).getString(KEY, null)
        if (stored == null) prefs(context).edit().putString(KEY, AppLanguage.Default.tag).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(LocaleManager::class.java)
            if (lm.applicationLocales.isEmpty) {
                lm.applicationLocales = LocaleList.forLanguageTags(stored ?: AppLanguage.Default.tag)
            }
        }
    }

    fun set(activity: Activity, language: AppLanguage) {
        prefs(activity).edit().putString(KEY, language.tag).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 系统会自动重建 Activity
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(language.tag)
        } else {
            activity.recreate()
        }
    }

    /** Android 12 及以下：为 Activity / Application 包装语言。 */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = AppLanguage.fromTag(
            base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null),
        )
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
