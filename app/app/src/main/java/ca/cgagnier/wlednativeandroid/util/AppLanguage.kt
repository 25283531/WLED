package ca.cgagnier.wlednativeandroid.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * 应用支持的语言。
 *
 * 默认行为是**跟随系统**：系统语言是这四种之一就用系统语言，否则回落到 [Default]（简体中文）。
 * 用户也可以在设置里固定成某一个语言，此时忽略系统设置。
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    Chinese("zh", "简体中文"),
    English("en", "English"),
    French("fr", "Français"),
    German("de", "Deutsch"),
    ;

    companion object {
        /** 系统语言不受支持时的回落语言。 */
        val Default = Chinese

        /** 系统语言到支持语言的映射；不受支持时返回 null。 */
        fun forLanguageCode(code: String?): AppLanguage? =
            entries.firstOrNull { code != null && code.startsWith(it.tag, ignoreCase = true) }

        /** 兼容旧调用：不受支持时回落 [Default]。 */
        fun fromTag(tag: String?): AppLanguage = forLanguageCode(tag) ?: Default

        /** 判断系统的语言代码是否在支持范围内。 */
        fun isSupported(code: String?): Boolean = forLanguageCode(code) != null
    }
}

/**
 * 语言管理：
 * - 默认跟随系统，系统语言不在支持范围内时回落到中文；
 * - 用户显式选择语言后，Android 13+ 走系统「分应用语言」(LocaleManager)，与系统设置互通，
 *   更低版本保存在 SharedPreferences 并在 attachBaseContext 中包装 Context。
 */
object AppLanguageManager {
    private const val PREFS = "app_language_prefs"
    private const val KEY = "language"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 用户显式选择的语言；null 表示跟随系统。 */
    fun preferredLanguage(context: Context): AppLanguage? =
        prefs(context).getString(KEY, null)?.let { AppLanguage.forLanguageCode(it) }

    fun isFollowingSystem(context: Context): Boolean = preferredLanguage(context) == null

    /** 当前实际生效的语言（已解析回落）。 */
    fun current(context: Context): AppLanguage {
        preferredLanguage(context)?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) {
                AppLanguage.forLanguageCode(locales[0].language)?.let { return it }
            }
        }
        return AppLanguage.forLanguageCode(Locale.getDefault().language) ?: AppLanguage.Default
    }

    /**
     * 启动时不再强制写入任何语言：跟随系统时系统会自动选择最匹配的 `values-*`，
     * 不需要（也不应该）把用户的选择固定下来。保留该方法是为了让调用点保持稳定。
     */
    @Suppress("UNUSED_PARAMETER")
    fun applyOnStartup(context: Context) {
        // no-op: 语言跟随系统时无需初始化
    }

    /** 恢复「跟随系统」。 */
    fun followSystem(activity: Activity) {
        prefs(activity).edit().remove(KEY).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
        } else {
            activity.recreate()
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

    /**
     * 为 Activity / Application 包装语言。
     *
     * 只有两种情况需要介入：
     * 1. 用户固定了语言（Android 13+ 已由 LocaleManager 处理，不重复包装）；
     * 2. 跟随系统但系统语言不受支持——此时资源会回落到 `values/`（英文），
     *    这里强制成中文，满足「默认中文」的要求。
     */
    fun wrap(base: Context): Context {
        val preferred = preferredLanguage(base)
        if (preferred != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base

        val locale = when {
            preferred != null -> Locale.forLanguageTag(preferred.tag)
            AppLanguage.isSupported(Locale.getDefault().language) -> return base
            else -> Locale.forLanguageTag(AppLanguage.Default.tag)
        }

        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
