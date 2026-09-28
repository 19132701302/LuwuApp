package com.luwu.app

import android.app.Application
import com.luwu.app.api.ApiClient
import com.luwu.app.util.ImageLoader
import com.luwu.app.util.Prefs
import com.luwu.app.util.ThemeManager
import java.io.PrintWriter
import java.io.StringWriter

class App : Application() {

    companion object {
        /** 冷启动时间戳（elapsedRealtime），MainActivity 首帧时计算启动耗时 */
        @JvmStatic
        var coldStartTs = 0L
    }

    override fun onCreate() {
        super.onCreate()
        coldStartTs = android.os.SystemClock.elapsedRealtime()
        ApiClient.init(this)
        // 初始化图片磁盘缓存目录（内存/磁盘二级缓存）
        ImageLoader.init(this)
        // 冷启动立即应用主题，避免闪白/闪黑
        ThemeManager.apply(this)
        installCrashHandler()
        // 后台可配置全局默认主题：未显式设置过的用户跟随站点配置
        if (!Prefs.hasSetThemeMode(this)) {
            ThemeManager.syncFromServer(this)
        }
    }

    /** 全局未捕获异常兜底：把崩溃现场写进 SharedPreferences，下次启动弹窗展示 */
    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val log = "线程: ${thread.name}\n异常: ${throwable.javaClass.name}\n${sw}"
                getSharedPreferences("luwu_crash", MODE_PRIVATE)
                    .edit().putString("last_crash", log).apply()
            } catch (_: Throwable) {
            }
            default?.uncaughtException(thread, throwable)
        }
    }
}
