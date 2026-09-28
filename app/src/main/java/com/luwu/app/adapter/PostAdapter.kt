package com.luwu.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.luwu.app.R
import com.luwu.app.api.PostItem

/** 信息流行：文章 / 广告 / 加载中 */
sealed class FeedRow {
    data class Post(val item: PostItem, val hero: Boolean = false) : FeedRow()
    data class Ad(val item: com.luwu.app.api.AdItem) : FeedRow()
    object Loading : FeedRow()
    object End : FeedRow()
}

class PostAdapter(
    private val onPostClick: (PostItem) -> Unit,
    private val onAdClick: (String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = mutableListOf<FeedRow>()
    var hasMore = false

    fun setPosts(posts: List<PostItem>, ad: com.luwu.app.api.AdItem?, every: Int) {
        rows.clear()
        if (ad != null) {
            var i = 0
            for ((idx, p) in posts.withIndex()) {
                val hero = false
                rows.add(FeedRow.Post(p, hero))
                i++
                if (i % every == 0) rows.add(FeedRow.Ad(ad))
            }
        } else {
            for ((idx, p) in posts.withIndex()) {
                rows.add(FeedRow.Post(p, idx == 0 && p.thumb.isNotBlank()))
            }
        }
        notifyDataSetChanged()
    }

    fun appendPosts(posts: List<PostItem>, ad: com.luwu.app.api.AdItem?, every: Int) {
        val start = rows.size
        var i = 0
        for (p in posts) {
            rows.add(FeedRow.Post(p))
            i++
            if (ad != null && i % every == 0) rows.add(FeedRow.Ad(ad))
        }
        notifyItemRangeInserted(start, rows.size - start)
    }

    fun setFooter(loading: Boolean) {
        val hasLoading = rows.any { it is FeedRow.Loading }
        val hasEnd = rows.any { it is FeedRow.End }
        if (loading && !hasLoading && !hasEnd) {
            rows.add(FeedRow.Loading)
            notifyItemInserted(rows.size - 1)
        } else if (!loading) {
            rows.removeAll { it is FeedRow.Loading }
            if (!hasMore && !hasEnd) {
                rows.add(FeedRow.End)
                notifyItemInserted(rows.size - 1)
            } else {
                notifyDataSetChanged()
            }
        }
    }

    override fun getItemViewType(position: Int): Int = when (val row = rows[position]) {
        is FeedRow.Post -> 1
        is FeedRow.Ad -> 2
        is FeedRow.Loading -> 3
        is FeedRow.End -> 4
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            1 -> PostHolder(inflater.inflate(R.layout.item_post, parent, false))
            5 -> HeroHolder(inflater.inflate(R.layout.item_post_hero, parent, false))
            2 -> AdHolder(inflater.inflate(R.layout.item_ad, parent, false))
            3 -> FooterHolder(inflater.inflate(R.layout.item_loading, parent, false))
            else -> FooterHolder(inflater.inflate(R.layout.item_loading, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is FeedRow.Post -> (holder as PostHolder).bind(row.item)
            is FeedRow.Ad -> (holder as AdHolder).bind(row.item)
            is FeedRow.Loading -> (holder as FooterHolder).bind(true)
            is FeedRow.End -> (holder as FooterHolder).bind(false)
        }
    }

    override fun getItemCount(): Int = rows.size

    inner class HeroHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val tvTitle: TextView = v.findViewById(R.id.tv_title)
        private val tvCategory: TextView = v.findViewById(R.id.tv_category)
        private val tvExcerpt: TextView = v.findViewById(R.id.tv_excerpt)
        private val tvDate: TextView = v.findViewById(R.id.tv_date)
        private val tvStats: TextView = v.findViewById(R.id.tv_stats)
        private val ivCover: android.widget.ImageView = v.findViewById(R.id.iv_cover)

        fun bind(item: PostItem) {
            tvTitle.text = item.title
            tvCategory.text = item.category.ifBlank { "资讯" }
            tvExcerpt.text = item.excerpt.ifBlank { "点击查看全文" }
            tvDate.text = item.date
            renderStats(item, tvStats)
            if (item.thumb.isNotBlank()) {
                com.luwu.app.util.ImageLoader.load(item.thumb, ivCover)
            }
            itemView.setOnClickListener { onPostClick(item) }
        }
    }

    inner class PostHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val tvTitle: TextView = v.findViewById(R.id.tv_title)
        private val tvCategory: TextView = v.findViewById(R.id.tv_category)
        private val tvExcerpt: TextView = v.findViewById(R.id.tv_excerpt)
        private val tvDate: TextView = v.findViewById(R.id.tv_date)
        private val tvStats: TextView = v.findViewById(R.id.tv_stats)
        private val ivCover: android.widget.ImageView = v.findViewById(R.id.iv_cover)

        fun bind(item: PostItem) {
            tvTitle.text = item.title
            tvCategory.text = item.category.ifBlank { "资讯" }
            tvExcerpt.text = item.excerpt.ifBlank { "点击查看全文" }
            tvDate.text = item.date
            renderStats(item, tvStats)
            if (item.thumb.isNotBlank()) {
                ivCover.visibility = View.VISIBLE
                com.luwu.app.util.ImageLoader.load(item.thumb, ivCover)
            } else {
                ivCover.visibility = View.GONE
            }
            itemView.setOnClickListener { onPostClick(item) }
        }
    }

    /** 列表卡片展示互动数据：赞 / 评论 */
    private fun renderStats(item: PostItem, tvStats: TextView) {
        val sb = StringBuilder()
        if (item.likes > 0) sb.append("赞 ${item.likes}")
        if (item.commentsNum > 0) {
            if (sb.isNotEmpty()) sb.append(" · ")
            sb.append("评论 ${item.commentsNum}")
        }
        tvStats.text = sb.toString()
        tvStats.visibility = if (sb.isEmpty()) View.GONE else View.VISIBLE
    }

    inner class AdHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val tvText: TextView = v.findViewById(R.id.tv_ad_text)
        private val ivCover: android.widget.ImageView = v.findViewById(R.id.iv_ad_cover)
        private val llVideo: View = v.findViewById(R.id.ll_ad_video)

        fun bind(ad: com.luwu.app.api.AdItem) {
            tvText.text = ad.text
            when (ad.type) {
                "image" -> {
                    tvText.visibility = if (ad.text.isBlank()) View.GONE else View.VISIBLE
                    ivCover.visibility = if (ad.img.isNotBlank()) View.VISIBLE else View.GONE
                    llVideo.visibility = View.GONE
                    if (ad.img.isNotBlank()) com.luwu.app.util.ImageLoader.load(ad.img, ivCover)
                }
                "video" -> {
                    tvText.visibility = if (ad.text.isBlank()) View.GONE else View.VISIBLE
                    ivCover.visibility = if (ad.img.isNotBlank()) View.VISIBLE else View.GONE
                    llVideo.visibility = View.VISIBLE
                    if (ad.img.isNotBlank()) com.luwu.app.util.ImageLoader.load(ad.img, ivCover)
                }
                else -> {
                    tvText.visibility = View.VISIBLE
                    ivCover.visibility = View.GONE
                    llVideo.visibility = View.GONE
                }
            }
            itemView.setOnClickListener {
                if (ad.type == "video" && ad.video.isNotBlank()) {
                    com.luwu.app.util.Util.openBrowser(itemView.context, ad.video)
                } else {
                    com.luwu.app.util.Util.openBrowser(itemView.context, ad.link)
                }
            }
        }
    }

    inner class FooterHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.tv_footer)

        fun bind(loading: Boolean) {
            tv.text = if (loading) "加载中…" else "没有更多了"
        }
    }
}
