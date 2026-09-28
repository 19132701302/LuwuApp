package com.luwu.app.ui

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
import com.luwu.app.util.ImageLoader
import com.luwu.app.util.Prefs
import com.luwu.app.util.Util
import org.json.JSONObject

/** 我的关注：关注的作者列表 */
class FollowListActivity : AppCompatActivity() {

    private var recycler: RecyclerView? = null
    private var tvEmpty: TextView? = null
    private var adapter: FollowAdapter? = null
    private val items = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_follow_list)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        Util.applyThemeBar(
            findViewById(R.id.top_bar),
            findViewById(R.id.tv_title),
            findViewById(R.id.btn_back),
        )
        recycler = findViewById(R.id.recycler)
        tvEmpty = findViewById(R.id.tv_empty)
        recycler?.layoutManager = LinearLayoutManager(this)
        adapter = FollowAdapter { o ->
            val uid = o.optInt("uid", 0)
            if (uid > 0) startActivity(UserProfileActivity.newIntent(this, uid, o.optString("name", "")))
        }
        recycler?.adapter = adapter
        load()
    }

    private fun load() {
        if (Prefs.getUid(this) <= 0) {
            tvEmpty?.text = "请先登录"
            tvEmpty?.visibility = View.VISIBLE
            return
        }
        ApiClient.post("follow", mapOf()) { json, err ->
            if (json == null || !json.optBoolean("ok", false)) {
                tvEmpty?.text = err ?: "加载失败"
                tvEmpty?.visibility = View.VISIBLE
                return@post
            }
            items.clear()
            val arr = json.optJSONArray("items") ?: return@post
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { items.add(it) }
            }
            adapter?.submit(items)
            tvEmpty?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    class FollowAdapter(
        private val onItem: (JSONObject) -> Unit
    ) : RecyclerView.Adapter<FollowAdapter.Holder>() {

        private var items: List<JSONObject> = emptyList()

        fun submit(list: List<JSONObject>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_follow, parent, false)
            val h = Holder(v)
            // 「主页」按钮：与整卡点击一致，进入作者主页（修复按钮无响应）
            h.btnGo.setOnClickListener { onItem(items[h.bindingAdapterPosition]) }
            return h
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val o = items[position]
            val name = o.optString("name", "作者")
            holder.tvName.text = name
            holder.tvMeta.text = "发布 ${o.optInt("posts", 0)} 篇文章"
            val avatar = o.optString("avatar", "")
            if (avatar.isNotBlank()) {
                holder.ivAvatar.visibility = View.VISIBLE
                ImageLoader.load(avatar, holder.ivAvatar, onError = {
                    holder.tvAvatar.text = name.take(1)
                })
            } else {
                holder.tvAvatar.text = name.take(1)
            }
            holder.itemView.setOnClickListener { onItem(o) }
        }

        override fun getItemCount(): Int = items.size

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tv_name)
            val tvMeta: TextView = v.findViewById(R.id.tv_meta)
            val tvAvatar: TextView = v.findViewById(R.id.tv_avatar)
            val ivAvatar: ImageView = v.findViewById(R.id.iv_avatar)
            val btnGo: TextView = v.findViewById(R.id.btn_go)
        }
    }
}
