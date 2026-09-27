package com.luwu.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luwu.app.R
import com.luwu.app.api.ApiClient
import com.luwu.app.api.PostItem
import com.luwu.app.util.ImageLoader
import com.luwu.app.util.Prefs
import com.luwu.app.util.Util
import org.json.JSONArray

/** 作者主页：资料卡 + TA 发布的文章 */
class UserProfileActivity : AppCompatActivity() {

    private var recycler: RecyclerView? = null
    private var tvEmpty: TextView? = null
    private var adapter: UserPostsAdapter? = null
    private val posts = mutableListOf<PostItem>()
    private var authorId = 0

    companion object {
        fun newIntent(context: Context, authorId: Int, authorName: String = ""): Intent {
            return Intent(context, UserProfileActivity::class.java)
                .putExtra("uid", authorId)
                .putExtra("name", authorName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        authorId = intent.getIntExtra("uid", 0)
        val name = intent.getStringExtra("name") ?: ""
        if (name.isNotBlank()) findViewById<TextView>(R.id.tv_nick).text = name
        findViewById<TextView>(R.id.tv_title).text = if (name.isBlank()) "作者主页" else name
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_follow).setOnClickListener { toggleFollow() }
        findViewById<View>(R.id.btn_block).setOnClickListener { toggleBlock() }
        Util.applyThemeBar(
            findViewById(R.id.top_bar),
            findViewById(R.id.tv_title),
            findViewById(R.id.btn_back),
        )

        recycler = findViewById(R.id.recycler)
        tvEmpty = findViewById(R.id.tv_empty)
        recycler?.layoutManager = LinearLayoutManager(this)
        adapter = UserPostsAdapter { post ->
            startActivity(ArticleDetailActivity.newIntent(this, post))
        }
        recycler?.adapter = adapter

        load()
        checkFollow()
    }

    private fun checkFollow() {
        if (Prefs.getUid(this) <= 0) {
            findViewById<TextView>(R.id.btn_follow).visibility = View.GONE
            findViewById<TextView>(R.id.btn_block).visibility = View.GONE
            return
        }
        // 同步服务端屏蔽列表 → 本地 Prefs
        ApiClient.get("blocks", mapOf()) { json, _ ->
            val ids = json?.optJSONArray("ids")
            if (ids != null) {
                val set = mutableSetOf<Int>()
                for (i in 0 until ids.length()) set.add(ids.optInt(i, 0))
                Prefs.setBlockedIds(this, set.filter { it > 0 }.toSet())
                renderBlock(authorId in Prefs.getBlockedIds(this))
            }
        }
        ApiClient.post("follow", mapOf(
            "target" to authorId.toString(),
            "act" to "check",
        )) { json, _ ->
            renderFollow(json?.optBoolean("following", false) ?: false)
        }
    }

    /** 屏蔽 / 取消屏蔽该作者 */
    private fun toggleBlock() {
        if (Prefs.getUid(this) <= 0) {
            Util.toast(this, "请先登录")
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        if (authorId <= 0) return
        val blocked = authorId in Prefs.getBlockedIds(this)
        val act = if (blocked) "unblock" else "block"
        ApiClient.get("block", mapOf(
            "action" to act,
            "target_id" to authorId.toString(),
        )) { json, err ->
            if (json == null || !json.optBoolean("ok", false)) {
                Util.toast(this, json?.optString("error", "") ?: err ?: "操作失败")
                return@get
            }
            val nowBlocked = json.optBoolean("blocked", !blocked)
            val set = Prefs.getBlockedIds(this).toMutableSet()
            if (nowBlocked) set.add(authorId) else set.remove(authorId)
            Prefs.setBlockedIds(this, set)
            renderBlock(nowBlocked)
            Util.toast(this, if (nowBlocked) "已屏蔽该作者，其内容将不再显示" else "已取消屏蔽")
        }
    }

    private fun renderBlock(on: Boolean) {
        val btn = findViewById<TextView>(R.id.btn_block) ?: return
        btn.text = if (on) "已屏蔽" else "屏蔽"
        btn.setBackgroundResource(if (on) R.drawable.bg_chip_gray else R.drawable.bg_button_secondary)
        btn.setTextColor(resources.getColor(if (on) R.color.ink_2 else R.color.white, null))
    }

    private fun toggleFollow() {
        if (Prefs.getUid(this) <= 0) {
            Util.toast(this, "请先登录")
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        val following = findViewById<TextView>(R.id.btn_follow).text.toString().contains("已关注")
        ApiClient.post("follow", mapOf(
            "target" to authorId.toString(),
            "act" to if (following) "remove" else "add",
        )) { json, _ ->
            val now = json?.optBoolean("following", false) ?: !following
            renderFollow(now)
            Util.toast(this, if (now) "已关注作者" else "已取消关注")
        }
    }

    private fun renderFollow(on: Boolean) {
        val btn = findViewById<TextView>(R.id.btn_follow) ?: return
        btn.text = if (on) "已关注" else "＋ 关注"
        btn.setBackgroundResource(if (on) R.drawable.bg_chip_gray else R.drawable.bg_button_primary)
        btn.setTextColor(resources.getColor(if (on) R.color.ink_2 else R.color.white, null))
    }

    private fun load() {
        ApiClient.get("user", mapOf("uid" to authorId.toString())) { json, err ->
            if (json == null || !json.optBoolean("ok", false)) {
                tvEmpty?.text = err ?: "加载失败"
                tvEmpty?.visibility = View.VISIBLE
                return@get
            }
            val user = json.optJSONObject("user")
            if (user != null) {
                val nick = user.optString("name", "")
                findViewById<TextView>(R.id.tv_nick).text = nick
                findViewById<TextView>(R.id.tv_title).text = nick
                val joined = user.optString("joined", "")
                val avatar = user.optString("avatar", "")
                if (avatar.isNotBlank()) {
                    ImageLoader.load(avatar, findViewById(R.id.iv_avatar), onError = {
                        findViewById<TextView>(R.id.tv_avatar).text = nick.take(1)
                    })
                    findViewById<ImageView>(R.id.iv_avatar).visibility = View.VISIBLE
                } else {
                    findViewById<TextView>(R.id.tv_avatar).text = nick.take(1)
                }
                findViewById<TextView>(R.id.tv_meta).text =
                    if (joined.isNotBlank()) "$joined 加入 · ${user.optInt("posts_count", 0)} 篇内容" else "${user.optInt("posts_count", 0)} 篇内容"
                // 数据卡：关注 / 粉丝 / 获赞
                findViewById<TextView>(R.id.stat_following).text = "关注 ${user.optInt("following_count", 0)}"
                findViewById<TextView>(R.id.stat_fans).text = "粉丝 ${user.optInt("follower_count", 0)}"
                findViewById<TextView>(R.id.stat_likes).text = "获赞 ${user.optInt("likes_received", 0)}"
            }
            val arr = json.optJSONArray("items") ?: JSONArray()
            posts.clear()
            val blocked = Prefs.getBlockedIds(this)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { o ->
                    val p = PostItem.fromJson(o)
                    if (p.authorId <= 0 || p.authorId !in blocked) posts.add(p)
                }
            }
            adapter?.submit(posts)
            tvEmpty?.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    class UserPostsAdapter(
        private val onItem: (PostItem) -> Unit
    ) : RecyclerView.Adapter<UserPostsAdapter.Holder>() {

        private var items: List<PostItem> = emptyList()

        fun submit(list: List<PostItem>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_post, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val p = items[position]
            holder.tvTitle.text = p.title
            holder.tvCategory.text = p.category.ifBlank { "资讯" }
            holder.tvDate.text = p.date
            val stats = mutableListOf<String>()
            if (p.likes > 0) stats.add("赞 ${p.likes}")
            if (p.commentsNum > 0) stats.add("评论 ${p.commentsNum}")
            holder.tvStats.text = stats.joinToString(" · ")
            holder.tvExcerpt.text = p.excerpt.replace(Regex("#+\\s*"), "")
                .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "[图]")
            if (p.thumb.isNotBlank()) {
                holder.ivCover.visibility = View.VISIBLE
                ImageLoader.load(p.thumb, holder.ivCover)
            } else {
                holder.ivCover.visibility = View.GONE
            }
            holder.itemView.setOnClickListener { onItem(p) }
        }

        override fun getItemCount(): Int = items.size

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tv_title)
            val tvCategory: TextView = v.findViewById(R.id.tv_category)
            val tvDate: TextView = v.findViewById(R.id.tv_date)
            val tvStats: TextView = v.findViewById(R.id.tv_stats)
            val tvExcerpt: TextView = v.findViewById(R.id.tv_excerpt)
            val ivCover: ImageView = v.findViewById(R.id.iv_cover)
        }
    }
}
