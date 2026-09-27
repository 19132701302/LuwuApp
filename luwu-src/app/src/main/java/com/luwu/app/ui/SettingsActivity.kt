package com.luwu.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.luwu.app.R
import com.luwu.app.util.Prefs
import com.luwu.app.util.Util

/**
 * 设置：顶部导航颜色选择，选择后立即生效
 */
class SettingsActivity : AppCompatActivity() {

    private val colors = arrayOf(
        "#0D9488" to "墨青",
        "#2563EB" to "湛蓝",
        "#7C3AED" to "黛紫",
        "#E11D48" to "绯红",
        "#EA580C" to "琥珀",
        "#16A34A" to "松绿",
        "#1E3A5F" to "藏青",
        "#1F2937" to "碳黑",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        buildThemeModeRow()

        val row1 = findViewById<LinearLayout>(R.id.color_row1)
        val row2 = findViewById<LinearLayout>(R.id.color_row2)
        for (i in colors.indices) {
            val cell = buildColorCell(colors[i].first, colors[i].second)
            (if (i < 4) row1 else row2).addView(cell)
        }
        refreshSelection()

        findViewById<View>(R.id.btn_restore).setOnClickListener {
            Prefs.setThemeColor(this, "#0D9488")
            applyTopBar(Prefs.getThemeColorHex(this))
            refreshSelection()
            Util.toast(this, getString(R.string.theme_saved))
        }

        // 推送通知开关（立即生效 + 手动测试）
        findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.sw_push).apply {
            isChecked = Prefs.isPushEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setPushEnabled(this@SettingsActivity, checked)
                if (checked) {
                    com.luwu.app.util.PushChecker.checkNow(this@SettingsActivity)
                    Util.toast(this@SettingsActivity, "推送已开启，正在检查新消息")
                } else {
                    Util.toast(this@SettingsActivity, "已关闭推送通知")
                }
            }
        }

        // 检查更新（手动触发）
        findViewById<View>(R.id.btn_check_update).setOnClickListener {
            UpdateChecker.check(this, manual = true)
        }

        // 清除缓存（WebView 缓存 + 应用缓存目录）
        findViewById<View>(R.id.btn_clear_cache).setOnClickListener { clearCache() }

        applyTopBar(Prefs.getThemeColorHex(this))

        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        findViewById<TextView>(R.id.tv_settings_version).text = "v" + ver
    }

    private fun clearCache() {
        val sizeBefore = Util.getCacheSize(this)
        try {
            val wv = android.webkit.WebView(this)
            wv.clearCache(true)
            wv.destroy()
        } catch (_: Exception) {}
        try {
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        } catch (_: Exception) {}
        com.luwu.app.util.CacheManager.clear(this)
        Util.toast(this, if (sizeBefore > 1) "已清除缓存 ${sizeBefore} MB" else "已清除缓存")
    }

    /** 主题模式：浅色 / 深色 / 跟随系统 三选一 */
    private fun buildThemeModeRow() {
        val row = findViewById<LinearLayout>(R.id.theme_mode_row) ?: return
        val modes = listOf(
            "light" to "浅色",
            "dark" to "深色",
            "system" to "跟随系统",
        )
        for ((mode, modeName) in modes) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            cell.setPadding(0, dp(4), 0, dp(4))

            val dot = TextView(this)
            dot.text = if (mode == "light") "☀" else if (mode == "dark") "🌙" else "⚙"
            dot.textSize = 20f
            dot.gravity = Gravity.CENTER
            dot.layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))

            val modeLabel = TextView(this)
            modeLabel.text = modeName
            modeLabel.textSize = 12f
            modeLabel.setTextColor(resources.getColor(R.color.ink_3, null))
            modeLabel.gravity = Gravity.CENTER
            cell.addView(dot, LinearLayout.LayoutParams(dp(52), dp(52)))
            cell.addView(modeLabel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

            cell.tag = mode
            cell.setOnClickListener {
                com.luwu.app.util.ThemeManager.switch(this, mode)
                refreshThemeModeRow()
                applyTopBar(com.luwu.app.util.Prefs.getThemeColorHex(this))
                Util.toast(this, when (mode) { "light" -> "已切换浅色模式"; "dark" -> "已切换深色模式"; else -> "已跟随系统" })
            }
            row.addView(cell)
        }
        refreshThemeModeRow()
    }

    private fun refreshThemeModeRow() {
        val row = findViewById<LinearLayout>(R.id.theme_mode_row) ?: return
        val current = com.luwu.app.util.Prefs.getAppThemeMode(this)
        for (i in 0 until row.childCount) {
            val cell = row.getChildAt(i) as LinearLayout
            val mode = cell.tag as String
            val selected = mode == current
            val dot = cell.getChildAt(0) as TextView
            dot.setBackgroundResource(R.drawable.bg_color_dot)
            val gd = dot.background.mutate() as GradientDrawable
            gd.setColor(if (selected) resources.getColor(R.color.brand_blue, null) else resources.getColor(R.color.chip_bg, null))
            val modeLabel = cell.getChildAt(1) as TextView
            modeLabel.setTextColor(if (selected) resources.getColor(R.color.brand_blue, null) else resources.getColor(R.color.ink_3, null))
        }
    }

    private fun buildColorCell(hex: String, name: String): View {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER_HORIZONTAL
        cell.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val dot = ImageView(this)
        dot.setBackgroundResource(R.drawable.bg_color_dot)
        (dot.background.mutate() as GradientDrawable).setColor(Color.parseColor(hex))
        cell.addView(dot, LinearLayout.LayoutParams(dp(52), dp(52)))

        val label = TextView(this)
        label.text = name
        label.textSize = 12f
        label.setTextColor(resources.getColor(R.color.ink_3, null))
        label.gravity = Gravity.CENTER
        cell.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        cell.tag = hex
        cell.setOnClickListener {
            Prefs.setThemeColor(this, hex)
            applyTopBar(hex)
            refreshSelection()
            Util.toast(this, getString(R.string.theme_saved))
        }
        return cell
    }

    private fun refreshSelection() {
        val current = Prefs.getThemeColorHex(this)
        val rows = listOf(
            findViewById<LinearLayout>(R.id.color_row1),
            findViewById<LinearLayout>(R.id.color_row2),
        )
        for (row in rows) {
            for (i in 0 until row.childCount) {
                val cell = row.getChildAt(i)
                val dot = (cell as LinearLayout).getChildAt(0) as ImageView
                val gd = dot.background.mutate() as GradientDrawable
                val hex = cell.tag as String
                gd.setStroke(if (hex == current) dp(3) else 0, Color.parseColor(hex))
            }
        }
    }

    private fun applyTopBar(hex: String) {
        findViewById<View>(R.id.top_bar).setBackgroundColor(Color.parseColor(hex))
        val dark = isDark(hex)
        findViewById<TextView>(R.id.tv_title).setTextColor(if (dark) Color.WHITE else Color.parseColor("#1F2937"))
        findViewById<ImageView>(R.id.btn_back).setColorFilter(
            if (dark) Color.WHITE else Color.parseColor("#374151")
        )
    }

    private fun isDark(hex: String): Boolean {
        val c = Color.parseColor(hex)
        val lum = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
        return lum < 150
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
