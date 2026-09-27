package com.luwu.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.luwu.app.R
import com.luwu.app.api.ApiClient
import com.luwu.app.util.Util

/** 商务合作：原生页面（不套网页） */
class BusinessCoopActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_business)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        Util.applyThemeBar(
            findViewById(R.id.top_bar),
            findViewById(R.id.tv_title),
            findViewById(R.id.btn_back),
        )

        findViewById<View>(R.id.tv_qq).setOnClickListener { copyQq("615806139") }

        loadBiz()
    }

    /** 从后台拉取商务合作内容（失败用本地默认） */
    private fun loadBiz() {
        ApiClient.get("biz") { json, _ ->
            if (json == null || !json.optBoolean("ok", false)) return@get
            val intro = json.optString("intro", "")
            if (intro.isNotBlank()) {
                findViewById<TextView>(R.id.tv_biz_intro)?.text = intro
            }
            // 价格行
            val container = findViewById<android.widget.LinearLayout>(R.id.price_container) ?: return@get
            container.removeAllViews()
            val arr = json.optJSONArray("plans")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name", "")
                    val price = o.optString("price", "")
                    if (name.isBlank()) continue
                    val row = android.widget.LinearLayout(this).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(0, 9.dp(), 0, 9.dp())
                    }
                    val tvName = TextView(this).apply {
                        text = name
                        textSize = 14f
                        setTextColor(resources.getColor(R.color.ink, null))
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val tvPrice = TextView(this).apply {
                        text = price
                        textSize = 15f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(resources.getColor(R.color.brand_blue, null))
                    }
                    row.addView(tvName)
                    row.addView(tvPrice)
                    container.addView(row)
                    if (i < arr.length() - 1) {
                        val divider = View(this).apply {
                            setBackgroundColor(resources.getColor(R.color.line, null))
                            layoutParams = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, (0.5f * resources.displayMetrics.density).toInt())
                        }
                        container.addView(divider)
                    }
                }
            }
            val extra = json.optString("extra", "")
            val tvExtra = findViewById<TextView>(R.id.tv_biz_extra)
            if (extra.isNotBlank()) {
                tvExtra?.text = extra
                tvExtra?.visibility = View.VISIBLE
            }
            val qq = json.optString("qq", "615806139")
            findViewById<TextView>(R.id.tv_qq)?.text = qq
            findViewById<TextView>(R.id.tv_qq)?.setOnClickListener { copyQq(qq) }
            val notice = json.optString("notice", "")
            if (notice.isNotBlank()) {
                findViewById<TextView>(R.id.tv_biz_notice)?.text = notice.replace("\n", "\n")
            }
        }
    }

    private fun copyQq(qq: String) {
        val clip = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clip.setPrimaryClip(android.content.ClipData.newPlainText("qq", qq))
        Util.toast(this, "QQ 已复制，去 QQ 添加 $qq")
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
