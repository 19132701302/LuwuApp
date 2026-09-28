package com.luwu.app.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.luwu.app.api.ApiClient

/**
 * 全局主题管理器：浅色 / 深色 / 跟随系统
 * - 本地保存用户选择（KEY_APP_THEME_MODE）
 * - 启动时应用；跟随系统时监听系统变化
 * - 登录用户切换后同步到服务器（api_save_user_theme）
 */
object ThemeManager {

    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    const val MODE_SYSTEM = "system"

    /** 应用主题模式（在 setContentView 之前调用） */
    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(toDelegateMode(Prefs.getAppThemeMode(context)))
    }

    fun toDelegateMode(mode: String): Int {
        return when (mode) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    /** 切换主题（本地立即生效；登录用户同步服务器） */
    fun switch(context: Context, mode: String) {
        Prefs.setAppThemeMode(context, mode)
        apply(context)
        syncToServer(context, mode)
    }

    /** 登录后拉取云端偏好（若本地未显式设置过则采用服务器/站点默认） */
    fun syncFromServer(context: Context) {
        val token = Prefs.getToken(context)
        ApiClient.get("theme_config", if (token.isBlank()) emptyMap() else mapOf("token" to token)) { json, _ ->
            if (json == null || !json.optBoolean("ok", false)) return@get
            val effective = json.optString("userTheme", "system")
            if (effective.isNotBlank() && !Prefs.hasSetThemeMode(context)) {
                Prefs.setAppThemeMode(context, effective)
                apply(context)
            }
        }
    }

    /** 用户切换时同步服务器 */
    private fun syncToServer(context: Context, mode: String) {
        val token = Prefs.getToken(context)
        if (token.isBlank()) return
        ApiClient.post("save_user_theme", mapOf("token" to token, "mode" to mode)) { _, _ -> }
    }

    /** 判断当前是否为深色（状态栏/顶栏反白用） */
    fun isDarkNow(context: Context): Boolean {
        val mode = Prefs.getAppThemeMode(context)
        return when (mode) {
            MODE_DARK -> true
            MODE_LIGHT -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
