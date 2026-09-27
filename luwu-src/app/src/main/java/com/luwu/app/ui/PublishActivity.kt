package com.luwu.app.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.luwu.app.R
import com.luwu.app.api.ApiClient
import com.luwu.app.api.Category
import com.luwu.app.util.AppState
import com.luwu.app.util.Prefs
import com.luwu.app.util.Util

class PublishActivity : AppCompatActivity() {

    private var etTitle: EditText? = null
    private var etContent: EditText? = null
    private var etTags: EditText? = null
    private var spinner: Spinner? = null
    private var tvError: TextView? = null
    private var categories = mutableListOf<Category>()
    private var uploading = false
    private val uploadQueue = mutableListOf<Uri>()

    /** 多选图片（系统相册/文件选择器，API 18+ 兼容；串行上传） */
    private val pickImages =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isEmpty()) return@registerForActivityResult
            uploadQueue.clear()
            uploadQueue.addAll(uris)
            uploadNextInQueue()
        }

    /** 选择单个视频 */
    private val pickVideo =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) uploadMedia(uri, isVideo = true)
        }
    // 视频上传走同一 uploadMedia，无需队列

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_publish)

        etTitle = findViewById(R.id.et_title)
        etContent = findViewById(R.id.et_content)
        etTags = findViewById(R.id.et_tags)
        spinner = findViewById(R.id.sp_category)
        tvError = findViewById(R.id.tv_error)

        findViewById<View>(R.id.btn_submit).setOnClickListener { submit() }
        findViewById<View>(R.id.btn_sc_img).setOnClickListener {
            if (!uploading) pickImages.launch("image/*")
            else Util.toast(this, "正在上传，请稍候")
        }
        findViewById<View>(R.id.btn_pick_video).setOnClickListener {
            if (!uploading) pickVideo.launch("video/*")
            else Util.toast(this, "正在上传，请稍候")
        }
        bindShortcodeTools()
        findViewById<View>(R.id.btn_back)?.setOnClickListener { finish() }
        com.luwu.app.util.Util.applyThemeBar(
            findViewById(R.id.top_bar),
            findViewById(R.id.tv_title),
            findViewById(R.id.btn_back),
        )

        if (AppState.pluginAvailable == false) {
            Util.toast(this, "发布功能需要网站启用「陆伍App控制台」插件")
            finish()
            return
        }
        if (!Prefs.isLoggedIn(this)) {
            Util.toast(this, "请先登录再发布")
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        loadCategories()
    }

    private fun loadCategories() {
        ApiClient.get("categories") { json, _ ->
            if (json == null || !json.optBoolean("ok", false)) return@get
            val arr = json.optJSONArray("items") ?: return@get
            categories.clear()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { categories.add(Category.fromJson(it)) }
            }
            val names = categories.map { it.name }.toMutableList()
            if (names.isEmpty()) names.add("默认")
            spinner?.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                names,
            )
        }
    }

    /** 短代码工具栏：向正文插入网站支持的短代码 */
    private fun bindShortcodeTools() {
        val ed = etContent
        fun insert(snippet: String) {
            val et = ed ?: return
            val start = et.selectionStart.coerceAtLeast(0)
            val end = et.selectionEnd.coerceAtLeast(start)
            val text = et.text
            text.replace(start, end, snippet)
            et.setSelection(start + snippet.length)
            et.requestFocus()
        }
        findViewById<View>(R.id.btn_sc_alert).setOnClickListener {
            insert("{alert type=\"info\"}在这里输入提示内容{/alert}")
        }
        findViewById<View>(R.id.btn_sc_quote).setOnClickListener {
            insert("\n> 在这里输入引用内容\n")
        }
        findViewById<View>(R.id.btn_sc_cloud).setOnClickListener {
            insert("{cloud title=\"网盘名称\" type=\"baidu\" url=\"https://pan.baidu.com/\" /}")
        }
        findViewById<View>(R.id.btn_sc_link).setOnClickListener {
            insert("[文字说明](https://链接)")
        }
        findViewById<View>(R.id.btn_sc_code).setOnClickListener {
            insert("\n```\n在这里输入代码\n```\n")
        }
    }

    /**
     * 上传图片/视频到服务器，成功后插入正文（图片 markdown、视频短代码）
     * 多图串行上传，避免同时发起多个大请求
     */
    /** 队列：逐张上传图片 */
    private fun uploadNextInQueue() {
        if (uploadQueue.isEmpty()) return
        val uri = uploadQueue.removeAt(0)
        uploadMedia(uri, isVideo = false) {
            uploadNextInQueue()
        }
    }

    private fun uploadMedia(uri: Uri, isVideo: Boolean, onDone: () -> Unit = {}) {
        if (uploading) {
            Util.toast(this, "正在上传，请稍候")
            return
        }
        uploading = true
        Util.toast(this, if (isVideo) "正在上传视频…" else "正在上传图片…")
        val tmp: java.io.File
        try {
            tmp = copyUriToCache(uri)
        } catch (e: Exception) {
            uploading = false
            onDone()
            Util.toast(this, "读取文件失败")
            return
        }
        if (tmp.length() == 0L) {
            uploading = false
            onDone()
            Util.toast(this, "文件为空，无法上传")
            tmp.delete()
            return
        }
        // 上传前本地预检大小（与服务器限制一致：图片 5MB / 视频 50MB），避免等待后失败
        val maxBytes = if (isVideo) 50L * 1024 * 1024 else 5L * 1024 * 1024
        if (tmp.length() > maxBytes) {
            uploading = false
            onDone()
            tmp.delete()
            Util.toast(this, if (isVideo) "视频大小需在 50MB 以内" else "图片大小需在 5MB 以内")
            return
        }
        ApiClient.upload("upload", tmp.absolutePath, mapOf("type" to if (isVideo) "video" else "image")) { json, err ->
            uploading = false
            tmp.delete()
            if (json == null || !json.optBoolean("ok", false)) {
                onDone()
                Util.toast(this, json?.optString("error", "") ?: err ?: "上传失败")
                return@upload
            }
            val url = json.optString("url", "")
            if (url.isBlank()) {
                onDone()
                Util.toast(this, "上传失败：未返回地址")
                return@upload
            }
            insertMediaLine(url, isVideo)
            Util.toast(this, if (isVideo) "视频已插入正文" else "图片已插入正文")
            onDone()
        }
    }

    /** 把 content:// Uri 拷贝到缓存目录，返回本地文件 */
    private fun copyUriToCache(uri: Uri): java.io.File {
        val name = "upload_" + System.currentTimeMillis() + "_" + (uri.lastPathSegment?.substringAfterLast("/")?.take(30) ?: "file")
        val out = java.io.File(cacheDir, name)
        contentResolver.openInputStream(uri)?.use { ins ->
            java.io.FileOutputStream(out).use { ous -> ins.copyTo(ous) }
        } ?: throw java.io.IOException("无法读取所选文件")
        return out
    }

    /** 图片 → ![描述](url)；视频 → {video url="..." /}（App 与网站前台均支持渲染） */
    private fun insertMediaLine(url: String, isVideo: Boolean) {
        val snippet = if (isVideo) {
            "\n{video url=\"$url\" /}\n"
        } else {
            "\n![图片]( $url )\n"
        }
        val et = etContent ?: return
        val start = et.selectionStart.coerceAtLeast(0)
        val end = et.selectionEnd.coerceAtLeast(start)
        val text = et.text
        text.replace(start, end, snippet)
        et.setSelection(start + snippet.length)
        et.requestFocus()
    }

    private fun submit() {
        val title = etTitle?.text?.toString()?.trim() ?: ""
        val content = etContent?.text?.toString()?.trim() ?: ""
        val tags = etTags?.text?.toString()?.trim() ?: ""
        if (title.isEmpty()) {
            showError("请填写标题")
            return
        }
        if (content.isEmpty()) {
            showError("请填写正文内容")
            return
        }
        val slug = categories.getOrNull(spinner?.selectedItemPosition ?: 0)?.slug ?: ""

        findViewById<View>(R.id.btn_submit).isEnabled = false
        tvError?.visibility = View.GONE
        ApiClient.post("publish", mapOf(
            "title" to title,
            "content" to content,
            "category" to slug,
            "tags" to tags,
        )) { json, err ->
            findViewById<View>(R.id.btn_submit).isEnabled = true
            if (json == null || !json.optBoolean("ok", false)) {
                showError(json?.optString("error", "") ?: err ?: "发布失败")
                return@post
            }
            Util.toast(this, "发布成功")
            finish()
        }
    }

    private fun showError(msg: String) {
        tvError?.text = msg
        tvError?.visibility = View.VISIBLE
    }
}
