package com.luwu.app.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.luwu.app.R
import com.luwu.app.api.ApiClient
import com.luwu.app.util.Util

/** 意见反馈：标题 + 内容 + 联系方式 + 截图（最多 3 张） */
class FeedbackActivity : AppCompatActivity() {

    private val imgPaths = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        Util.applyThemeBar(
            findViewById(R.id.top_bar),
            findViewById(R.id.tv_title),
            findViewById(R.id.btn_back),
        )

        findViewById<View>(R.id.btn_submit).setOnClickListener { submit() }
        renderImgs()
    }

    /** 渲染截图缩略图 + 添加按钮 */
    private fun renderImgs() {
        val container = findViewById<LinearLayout>(R.id.img_container) ?: return
        container.removeAllViews()
        for ((i, path) in imgPaths.withIndex()) {
            val wrap = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(96.dp(), 96.dp()).apply {
                    marginEnd = 10.dp()
                }
            }
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(96.dp(), 96.dp())
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_input_gray)
            }
            try {
                iv.setImageURI(Uri.fromFile(java.io.File(path)))
            } catch (_: Exception) {}
            val del = TextView(this).apply {
                text = "删除"
                textSize = 10f
                gravity = android.view.Gravity.CENTER
                setTextColor(0xFFFF3B30.toInt())
                layoutParams = LinearLayout.LayoutParams(96.dp(), 26.dp())
            }
            del.setOnClickListener {
                imgPaths.removeAt(i)
                renderImgs()
            }
            wrap.addView(iv)
            wrap.addView(del)
            container.addView(wrap)
        }
        if (imgPaths.size < 3) {
            val add = TextView(this).apply {
                text = "+"
                textSize = 26f
                gravity = android.view.Gravity.CENTER
                setTextColor(0xFF9CA3AF.toInt())
                setBackgroundResource(R.drawable.bg_input_gray)
                layoutParams = LinearLayout.LayoutParams(96.dp(), 96.dp())
            }
            add.setOnClickListener { pickImage() }
            container.addView(add)
        }
    }

    private fun pickImage() {
        val launcher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val path = Util.uriToPath(this, uri)
                if (path != null) {
                    imgPaths.add(path)
                    renderImgs()
                } else {
                    Util.toast(this, "无法读取该图片")
                }
            }
        }
        launcher.launch("image/*")
    }

    private fun submit() {
        val title = findViewById<EditText>(R.id.et_title)?.text?.toString()?.trim() ?: ""
        val content = findViewById<EditText>(R.id.et_content)?.text?.toString()?.trim() ?: ""
        val contact = findViewById<EditText>(R.id.et_contact)?.text?.toString()?.trim() ?: ""
        if (title.isEmpty()) {
            Util.toast(this, "请填写反馈标题")
            return
        }
        if (content.isEmpty()) {
            Util.toast(this, "请填写反馈内容")
            return
        }
        findViewById<View>(R.id.btn_submit).isEnabled = false
        Util.toast(this, "正在提交…")
        ApiClient.uploadMulti(
            "feedback",
            imgPaths,
            mapOf("title" to title, "content" to content, "contact" to contact),
        ) { json, err ->
            findViewById<View>(R.id.btn_submit).isEnabled = true
            if (json == null || !json.optBoolean("ok", false)) {
                Util.toast(this, err ?: "提交失败，请稍后再试")
                return@uploadMulti
            }
            Util.toast(this, "反馈已提交，感谢您的建议")
            finish()
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
