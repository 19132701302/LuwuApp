package com.luwu.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luwu.app.R
import com.luwu.app.api.ApiClient
import com.luwu.app.api.PostItem
import com.luwu.app.util.Prefs
import com.luwu.app.util.Util
import org.json.JSONObject

/** 消息中心：评论我的文章 / 回复我的评论 */
class NotifyActivity : AppCompatActivity() {

    private var recycler: RecyclerView? = null
    private var tvEmpty: TextView? = null
    private var adapter: NotifyAdapter? = null
    private val items = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notify)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_read_all).setOnClickListener { markAllRead() }
        Util.applyThemeBar(
            findViewById(R.id.top_bar),
            findViewById(R.id.tv_title),
            findViewById(R.id.btn_back),
        )
        recycler = findViewById(R.id.recycler)
        tvEmpty = findViewById(R.id.tv_empty)
        recycler?.layoutManager = LinearLayoutManager(this)
        adapter = NotifyAdapter { obj ->
            val cid = obj.optInt("cid", 0)
            if (cid > 0) {
                startActivity(
                    ArticleDetailActivity.newIntent(
                        this,
                        com.luwu.app.api.PostItem(cid, obj.optString("title", "文章详情"), "", "", "", "")
                    )
                )
            } else {
                // 关注通知：打开对方主页
                val fromUid = obj.optInt("from_uid", 0)
                if (fromUid > 0) {
                    startActivity(UserProfileActivity.newIntent(this, fromUid, obj.optString("author", "")))
                }
            }
        }
        recycler?.adapter = adapter
        load()
    }

    private fun load() {
        if (Prefs.getUid(this) <= 0) {
            tvEmpty?.text = "请先登录后再查看消息"
            tvEmpty?.visibility = View.VISIBLE
            return
        }
        ApiClient.post("notify", mapOf()) { json, err ->
            if (json == null || !json.optBoolean("ok", false)) {
                tvEmpty?.text = err ?: "消息加载失败"
                tvEmpty?.visibility = View.VISIBLE
                return@post
            }
            items.clear()
            val arr = json.optJSONArray("items") ?: JSONObject().optJSONArray("items")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { items.add(it) }
                }
            }
            adapter?.submit(items)
            tvEmpty?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** 一键全部已读：通知接口标已读后刷新 */
    private fun markAllRead() {
        if (Prefs.getUid(this) <= 0) {
            android.widget.Toast.makeText(this, "请先登录", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        ApiClient.post("notify", mapOf("act" to "read")) { json, _ ->
            if (json != null && json.optBoolean("ok", false)) {
                android.widget.Toast.makeText(this, "已全部标记为已读", android.widget.Toast.LENGTH_SHORT).show()
                load()
            } else {
                android.widget.Toast.makeText(this, "操作失败，请稍后重试", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    class NotifyAdapter(
        private val onItem: (JSONObject) -> Unit
    ) : RecyclerView.Adapter<NotifyAdapter.Holder>() {

        private var items: List<JSONObject> = emptyList()

        fun submit(list: List<JSONObject>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notify, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val o = items[position]
            val type = o.optString("type", "comment")
            holder.tvTypeIcon.text = when (type) {
                "like" -> "⭐"
                "follow" -> "👤"
                "reply" -> "💬"
                else -> "💬"
            }
            holder.tvTypeLabel.text = when (type) {
                "like" -> "赞了我的文章"
                "follow" -> "关注了我"
                "reply" -> "回复了我的评论"
                else -> "评论了我的文章"
            }
            holder.tvTitle.text = if (type == "follow") o.optString("author", "用户") else o.optString("title", "文章")
            val content = o.optString("content", "")
            holder.tvContent.text = if (type == "follow") "点击查看 TA 的主页" else "${o.optString("author", "游客")}：$content"
            holder.tvTime.text = o.optString("time", "")
            holder.dotUnread.visibility = if (o.optInt("read", 0) == 0) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onItem(o) }
        }

        override fun getItemCount(): Int = items.size

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val tvTypeIcon: TextView = v.findViewById(R.id.tv_type_icon)
            val tvTypeLabel: TextView = v.findViewById(R.id.tv_type_label)
            val tvTitle: TextView = v.findViewById(R.id.tv_title)
            val tvContent: TextView = v.findViewById(R.id.tv_content)
            val tvTime: TextView = v.findViewById(R.id.tv_time)
            val dotUnread: View = v.findViewById(R.id.dot_unread)
        }
    }
}
