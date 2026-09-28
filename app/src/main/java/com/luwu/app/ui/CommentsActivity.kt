package com.luwu.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luwu.app.R
import com.luwu.app.api.ApiClient
import com.luwu.app.util.AppState
import com.luwu.app.util.Prefs
import com.luwu.app.util.Util
import org.json.JSONObject

/** 评论列表 + 发表评论（支持表情/图片/回复） */
class CommentsActivity : AppCompatActivity() {

    private var postId = 0
    private var recycler: RecyclerView? = null
    private var tvEmpty: TextView? = null
    private var etComment: EditText? = null
    private var adapter: CommentAdapter? = null
    private val comments = mutableListOf<CommentItem>()
    private var replyToCoid = 0
    private var replyToName = ""
    private var imgLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null

    data class CommentItem(val coid: Int, val author: String, val parent: String, val date: String, val content: String)

    companion object {
        fun newIntent(context: Context, postId: Int, postTitle: String): Intent {
            return Intent(context, CommentsActivity::class.java)
                .putExtra("post_id", postId)
                .putExtra("post_title", postTitle)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comments)

        postId = intent.getIntExtra("post_id", 0)
        findViewById<TextView>(R.id.tv_title).text = "评论"
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        Util.applyThemeBar(
            findViewById(R.id.top_bar),
            findViewById(R.id.tv_title),
            findViewById(R.id.btn_back),
        )

        recycler = findViewById(R.id.recycler)
        tvEmpty = findViewById(R.id.tv_empty)
        etComment = findViewById(R.id.et_comment)
        recycler?.layoutManager = LinearLayoutManager(this)
        adapter = CommentAdapter()
        adapter?.onLongClick = { item ->
            reportComment(item)
        }
        recycler?.adapter = adapter

        findViewById<View>(R.id.btn_send).setOnClickListener { sendComment() }
        findViewById<View>(R.id.btn_cancel_reply).setOnClickListener { clearReply() }
        findViewById<View>(R.id.btn_emoji).setOnClickListener {
            findViewById<View>(R.id.emoji_panel)?.let {
                it.visibility = if (it.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        findViewById<View>(R.id.btn_img).setOnClickListener { pickCommentImage() }
        bindEmojiPanel()
        // 图片选择器必须在此注册，点击时注册会闪退
        imgLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri == null) return@registerForActivityResult
            val path = Util.uriToPath(this, uri)
            if (path == null) {
                Util.toast(this, "无法读取该图片")
                return@registerForActivityResult
            }
            Util.toast(this, "图片上传中…")
            ApiClient.upload("upload", path) { json, err ->
                if (json == null || !json.optBoolean("ok", false)) {
                    Util.toast(this, err ?: "图片上传失败")
                    return@upload
                }
                val url = json.optString("url", "")
                if (url.isBlank()) {
                    Util.toast(this, "图片地址为空")
                    return@upload
                }
                val cur = etComment?.text?.toString() ?: ""
                val append = if (cur.isEmpty()) "" else " "
                etComment?.setText(cur + append + "![图片](" + url + ")")
                etComment?.setSelection(etComment?.text?.length ?: 0)
                Util.toast(this, "图片已插入")
            }
        }

        loadComments()
    }

    private fun loadComments() {
        if (AppState.pluginAvailable == false) {
            tvEmpty?.text = "评论服务暂不可用"
            tvEmpty?.visibility = View.VISIBLE
            return
        }
        ApiClient.get("comments", mapOf("id" to postId.toString())) { json, err ->
            if (json == null || !json.optBoolean("ok", false)) {
                tvEmpty?.text = err ?: "评论加载失败"
                tvEmpty?.visibility = View.VISIBLE
                return@get
            }
            comments.clear()
            val arr = json.optJSONArray("items") ?: return@get
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                comments.add(
                    CommentItem(
                        o.optInt("coid", 0),
                        o.optString("author", "游客"),
                        o.optString("parent", ""),
                        o.optString("date", ""),
                        o.optString("content", ""),
                    )
                )
            }
            adapter?.submit(comments.toList())
            tvEmpty?.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** 长按评论 → 举报（后端 api_report type=comment） */
    private fun reportComment(item: CommentItem) {
        val input = android.widget.EditText(this)
        input.hint = "请填写举报理由（200字内）"
        input.minLines = 2
        input.setTextColor(resources.getColor(R.color.ink, null))
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("举报该评论（@${item.author}）")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("提交举报") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isBlank()) {
                    Util.toast(this, "请填写举报理由")
                    return@setPositiveButton
                }
                ApiClient.get("report", mapOf(
                    "type" to "comment",
                    "target_id" to item.coid.toString(),
                    "reason" to reason,
                )) { json, err ->
                    if (json != null && json.optBoolean("ok", false)) {
                        Util.toast(this, json.optString("message", "举报已提交"))
                    } else {
                        Util.toast(this, json?.optString("error", "") ?: err ?: "举报提交失败")
                    }
                }
            }
            .show()
    }

    /** 设置回复目标（点评论上的"回复"） */
    private fun setReplyTo(coid: Int, name: String) {
        replyToCoid = coid
        replyToName = name
        findViewById<View>(R.id.reply_bar)?.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_reply_target)?.text = "回复 @$name"
        etComment?.requestFocus()
        etComment?.let { Util.showKeyboard(this, it) }
    }

    private fun clearReply() {
        replyToCoid = 0
        replyToName = ""
        findViewById<View>(R.id.reply_bar)?.visibility = View.GONE
    }

    private fun sendComment() {
        val content = etComment?.text?.toString()?.trim() ?: return
        if (content.isEmpty()) {
            Util.toast(this, "先写点什么再发送")
            return
        }
        val author = Prefs.getUserName(this).ifBlank { "游客" }
        findViewById<View>(R.id.btn_send).isEnabled = false
        val params = mutableMapOf("id" to postId.toString(), "author" to author, "content" to content)
        if (replyToCoid > 0) params["parent"] = replyToCoid.toString()
        ApiClient.post("comment", params) { json, err ->
            findViewById<View>(R.id.btn_send).isEnabled = true
            if (json == null || !json.optBoolean("ok", false)) {
                Util.toast(this, err ?: "发表失败，请稍后再试")
                return@post
            }
            etComment?.text?.clear()
            clearReply()
            Util.toast(this, "评论成功")
            loadComments()
        }
    }

    private fun pickCommentImage() {
        imgLauncher?.launch("image/*")
    }

    private fun bindEmojiPanel() {
        val emojis = listOf(
            "😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😜", "🤔", "😎",
            "😭", "😡", "👍", "👎", "🙏", "👏", "💪", "❤️", "🎉", "🔥",
            "✨", "🌹", "🐶", "🐱"
        )
        val panel = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.emoji_panel) ?: return
        panel.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 6)
        panel.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context).apply {
                    textSize = 22f
                    gravity = android.view.Gravity.CENTER
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (46 * resources.displayMetrics.density).toInt())
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val tv = holder.itemView as TextView
                tv.text = emojis[position]
                tv.setOnClickListener {
                    val cur = etComment?.text?.toString() ?: ""
                    etComment?.setText(cur + emojis[position])
                    etComment?.setSelection(etComment?.text?.length ?: 0)
                }
            }
            override fun getItemCount(): Int = emojis.size
        }
    }

    class CommentAdapter : RecyclerView.Adapter<CommentAdapter.Holder>() {

        private var items: List<CommentItem> = emptyList()
        var onReply: ((CommentItem) -> Unit)? = null
        var onLongClick: ((CommentItem) -> Unit)? = null

        fun submit(list: List<CommentItem>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_comment, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val c = items[position]
            holder.tvAuthor.text = c.author
            holder.tvTime.text = c.date
            if (c.parent.isNotBlank()) {
                holder.tvReplyTo.visibility = View.VISIBLE
                holder.tvReplyTo.text = "回复 @${c.parent}"
            } else {
                holder.tvReplyTo.visibility = View.GONE
            }
            holder.btnReply.setOnClickListener { onReply?.invoke(c) }
            // 评论内容：图片语法 ![x](url) 转小图展示
            val raw = c.content
            val imgRe = Regex("!\\[([^\\]]*)\\]\\(\\s*([^)\\s]+)\\s*\\)")
            val html = imgRe.replace(raw) { m ->
                "<img src=\"${m.groupValues[2]}\" width=\"180\" />"
            }.replace("\n", "<br>")
            holder.tvContent.text = if (html.contains("<img")) {
                android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY, object : android.text.Html.ImageGetter {
                    override fun getDrawable(source: String): android.graphics.drawable.Drawable? {
                        val d = com.luwu.app.util.ImageLoader.fetchDrawable(source) ?: return null
                        val w = 180
                        val h = (w * d.intrinsicHeight / (d.intrinsicWidth.coerceAtLeast(1))).coerceAtLeast(80)
                        d.setBounds(0, 0, w, h)
                        return d
                    }
                }, null)
            } else {
                raw
            }
        }

        override fun getItemCount(): Int = items.size

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val tvAuthor: TextView = v.findViewById(R.id.tv_author)
            val tvTime: TextView = v.findViewById(R.id.tv_time)
            val tvReplyTo: TextView = v.findViewById(R.id.tv_reply_to)
            val btnReply: TextView = v.findViewById(R.id.btn_reply)
            val tvContent: TextView = v.findViewById(R.id.tv_content)

            init {
                itemView.setOnLongClickListener {
                    val item = items.getOrNull(bindingAdapterPosition) ?: return@setOnLongClickListener true
                    onLongClick?.invoke(item)
                    true
                }
            }
        }
    }
}
