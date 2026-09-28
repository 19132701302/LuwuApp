package com.luwu.app.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.luwu.app.R
import com.luwu.app.util.Util

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        com.luwu.app.util.Util.applyThemeBar(
            findViewById(R.id.top_bar),
            findViewById(R.id.tv_title),
            findViewById(R.id.btn_back),
        )
        findViewById<View>(R.id.btn_website).setOnClickListener {
            Util.openBrowser(this, "https://www.65gw.com")
        }

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "3.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "3.0"
        }
        findViewById<TextView>(R.id.tv_version).text = "版本 $version"
        loadChangelog(version)
        // 冷启动性能统计：最近一次 + 平均（保留最近 10 次）
        try {
            val sp = getSharedPreferences("luwu_perf", MODE_PRIVATE)
            val cur = sp.getString("cold_ms", "") ?: ""
            val list = (if (cur.isBlank()) emptyList() else cur.split(","))
                .mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }
            if (list.isNotEmpty()) {
                val avg = list.average().toLong()
                findViewById<TextView>(R.id.tv_perf).text =
                    "冷启动：最近 ${list.last()}ms · 平均 ${avg}ms"
            }
        } catch (_: Exception) {}
    }

    /** 拉取更新日志并渲染（后台可配） */
    private fun loadChangelog(currentVersion: String) {
        com.luwu.app.api.ApiClient.get("update") { json, _ ->
            if (json == null || !json.optBoolean("ok", false)) return@get
            val container = findViewById<android.widget.LinearLayout>(R.id.changelog_container) ?: return@get
            val arr = json.optJSONArray("changelogList") ?: return@get
            if (arr.length() == 0) return@get
            val density = resources.displayMetrics.density
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ver = o.optString("ver", "")
                val date = o.optString("date", "")
                val content = o.optString("content", "")
                if (ver.isBlank()) continue
                // 行：左侧圆点 + 版本/日期 + 内容
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.TOP
                    setPadding(0, 8.dp(), 0, 8.dp())
                }
                val dot = View(this).apply {
                    setBackgroundResource(R.drawable.bg_dot_red)
                    layoutParams = LinearLayout.LayoutParams(
                        (8 * density).toInt(), (8 * density).toInt()
                    ).apply { topMargin = (5 * density).toInt(); marginEnd = (12 * density).toInt() }
                }
                val body = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val head = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val isCurrent = ver == currentVersion || ver == "v$currentVersion"
                val tvVer = TextView(this).apply {
                    text = if (isCurrent) "$ver · 当前版本" else ver
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(resources.getColor(if (isCurrent) R.color.brand_blue else R.color.ink, null))
                }
                val tvDate = TextView(this).apply {
                    text = date
                    textSize = 11f
                    setTextColor(resources.getColor(R.color.ink_3, null))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        gravity = android.view.Gravity.END
                    }
                }
                head.addView(tvVer)
                head.addView(tvDate)
                body.addView(head)
                val tvContent = TextView(this).apply {
                    text = content
                    textSize = 12.5f
                    setLineSpacing((2 * density).toFloat(), 1f)
                    setTextColor(resources.getColor(R.color.ink_2, null))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = (3 * density).toInt()
                    }
                }
                body.addView(tvContent)
                row.addView(dot)
                row.addView(body)
                container.addView(row)
                if (i < arr.length() - 1) {
                    val div = View(this).apply {
                        setBackgroundColor(resources.getColor(R.color.line, null))
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (0.5f * density).toInt())
                    }
                    container.addView(div)
                }
            }
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
