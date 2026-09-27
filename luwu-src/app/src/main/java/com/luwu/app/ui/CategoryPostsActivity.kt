package com.luwu.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.luwu.app.R
import com.luwu.app.adapter.PostAdapter
import com.luwu.app.api.ApiClient
import com.luwu.app.api.PostItem
import com.luwu.app.util.AppState
import com.luwu.app.util.Util
import org.json.JSONArray

/**
 * 通用列表页：分类文章 / 收藏 / 历史 / 我的发布
 * mode: category | favorites | history | mine
 */
class CategoryPostsActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_SLUG = "slug"
        private const val EXTRA_TITLE = "title"

        fun newIntent(context: Context, slug: String, title: String): Intent {
            return Intent(context, CategoryPostsActivity::class.java)
                .putExtra(EXTRA_MODE, "category")
                .putExtra(EXTRA_SLUG, slug)
                .putExtra(EXTRA_TITLE, title)
        }

        fun modeIntent(context: Context, mode: String, title: String): Intent {
            return Intent(context, CategoryPostsActivity::class.java)
                .putExtra(EXTRA_MODE, mode)
                .putExtra(EXTRA_TITLE, title)
        }
    }

    private var mode = "category"
    private var slug = ""
    private var page = 1
    private var loading = false
    private var hasMore = true
    private var adapter: PostAdapter? = null
    private var sort = "latest" // latest / hot / comments / likes
    private var swipe: SwipeRefreshLayout? = null
    private var tvTopAd: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_posts)

        mode = intent.getStringExtra(EXTRA_MODE) ?: "category"
        slug = intent.getStringExtra(EXTRA_SLUG) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: when (mode) {
            "favorites" -> getString(R.string.my_favorites)
            "history" -> getString(R.string.my_history)
            "mine" -> getString(R.string.my_posts)
            "all" -> "全部文章"
            else -> "分类文章"
        }
        findViewById<TextView>(R.id.tv_title).text = title
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        Util.applyThemeBar(
            findViewById(R.id.top_bar),
            findViewById(R.id.tv_title),
            findViewById(R.id.btn_back),
        )

        swipe = findViewById(R.id.swipe_list)
        // 空态：图标 + 引导按钮
        findViewById<View>(R.id.btn_empty_go_home).setOnClickListener {
            startActivity(Intent(this, com.luwu.app.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        // 排序栏：仅分类/全部模式显示
        val showSort = mode == "category" || mode == "all"
        findViewById<View>(R.id.sort_bar).visibility = if (showSort) View.VISIBLE else View.GONE
        if (showSort) bindSortBar()
        tvTopAd = findViewById(R.id.tv_top_ad)
        swipe?.setOnRefreshListener { refresh() }

        val recycler = findViewById<RecyclerView>(R.id.recycler_list)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = PostAdapter(
            onPostClick = {
                startActivity(ArticleDetailActivity.newIntent(this, it))
            },
            onAdClick = { Util.openBrowser(this, it) },
        )
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                if (!loading && hasMore && lm.findLastVisibleItemPosition() >= lm.itemCount - 2) {
                    loadMore()
                }
            }
        })

        setupTopAd()
        refresh()
    }

    private fun setupTopAd() {
        val ads = AppState.ads ?: return
        val list = ads.category
        if (!ads.enabled || list.isEmpty()) return
        val ad = list[(Math.random() * list.size).toInt().coerceAtMost(list.size - 1)]
        val tv = tvTopAd ?: return
        val iv = findViewById<View>(R.id.iv_top_ad) as? android.widget.ImageView ?: return
        val useImg = ad.type != "text" && ad.img.isNotBlank()
        if (useImg) {
            tv.visibility = View.GONE
            iv.visibility = View.VISIBLE
            com.luwu.app.util.ImageLoader.load(ad.img, iv)
            iv.setOnClickListener { Util.openBrowser(this, ad.link) }
        } else {
            if (ad.text.isBlank()) return
            iv.visibility = View.GONE
            tv.visibility = View.VISIBLE
            tv.text = ad.text
            tv.setOnClickListener { Util.openBrowser(this, ad.link) }
        }
    }

    /** 排序栏绑定：最新 / 热门 / 评论最多 / 点赞最多 */
    private fun bindSortBar() {
        val ids = listOf(R.id.sort_latest, R.id.sort_hot, R.id.sort_comments, R.id.sort_likes)
        val vals = listOf("latest", "hot", "comments", "likes")
        val tabs = ids.map { findViewById<android.widget.TextView>(it) }
        fun refreshTabs() {
            for (i in ids.indices) {
                val on = sort == vals[i]
                tabs[i].background = resources.getDrawable(if (on) R.drawable.bg_sort_on else R.drawable.bg_sort_off, null)
                tabs[i].setTextColor(resources.getColor(if (on) android.R.color.white else R.color.ink_2, null))
            }
        }
        for (i in ids.indices) {
            tabs[i].setOnClickListener {
                sort = vals[i]
                refreshTabs()
                refresh()
            }
        }
        refreshTabs()
    }

    private fun refresh() {
        page = 1
        hasMore = true
        if (mode in listOf("favorites", "history", "mine")) {
            loadLocalOrMine()
            return
        }
        loadPage()
    }

    private fun loadMore() {
        if (!hasMore) return
        page++
        loadPage()
    }

    private fun loadLocalOrMine() {
        when (mode) {
            "favorites" -> {
                swipe?.isRefreshing = false
                val list = com.luwu.app.util.Prefs.getFavorites(this)
                adapter?.setPosts(list, null, 5)
                adapter?.hasMore = false
                adapter?.setFooter(false)
                renderEmpty(list)
            }
            "history" -> {
                swipe?.isRefreshing = false
                val list = com.luwu.app.util.Prefs.getHistory(this)
                adapter?.setPosts(list, null, 5)
                adapter?.hasMore = false
                adapter?.setFooter(false)
                renderEmpty(list)
            }
            "mine" -> loadMine()
        }
    }

    private fun loadMine() {
        if (AppState.pluginAvailable == false) {
            swipe?.isRefreshing = false
            Util.toast(this, "该功能需要安装「陆伍App控制台」插件")
            finish()
            return
        }
        if (!com.luwu.app.util.Prefs.isLoggedIn(this)) {
            swipe?.isRefreshing = false
            Util.toast(this, "请先登录")
            finish()
            return
        }
        swipe?.isRefreshing = true
        ApiClient.post("mine", mapOf()) { json, err ->
            swipe?.isRefreshing = false
            if (json == null || !json.optBoolean("ok", false)) {
                Util.toast(this, json?.optString("error", "") ?: err ?: "加载失败")
                return@post
            }
            val arr = json.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<PostItem>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(PostItem.fromJson(it)) }
            }
            adapter?.setPosts(list, null, 5)
            adapter?.hasMore = false
            adapter?.setFooter(false)
            renderEmpty(list)
        }
    }

    private fun loadPage() {
        if (loading) return
        loading = true
        if (page == 1) swipe?.isRefreshing = true

        // 插件不可用 → 分类走 RSS 兜底
        if (AppState.pluginAvailable == false && mode == "category") {
            loadPageFromRss()
            return
        }

        val api = if (mode == "category") "category" else "home"
        val params = mutableMapOf("page" to page.toString(), "pageSize" to "20")
        if (mode == "category") params["slug"] = slug
        if (mode == "category" || mode == "all") params["sort"] = sort

        ApiClient.get(api, params) { json, err ->
            swipe?.isRefreshing = false
            loading = false
            if (json == null || !json.optBoolean("ok", false)) {
                // 网络失败 → 优先离线缓存兜底（首页/分类已加载内容可看）
                val curCount = adapter?.itemCount ?: 0
                if (page == 1 && curCount <= 1) {
                    val cached = com.luwu.app.util.CacheManager.getList(
                        this,
                        if (mode == "category") "cat_${slug}_${sort}_1" else "all_${sort}_1"
                    )
                    if (cached != null && cached.optBoolean("ok", false)) {
                        Util.toast(this, "网络不可用，显示缓存内容")
                        renderList(cached)
                        return@get
                    }
                }
                if (mode == "category" && page == 1) {
                    AppState.pluginAvailable = false
                    AppState.ads = AppState.defaultAds()
                    setupTopAd()
                    loadPageFromRss()
                } else if (page == 1) {
                    Util.toast(this, err ?: getString(R.string.network_error))
                }
                return@get
            }
            AppState.pluginAvailable = true
            // 写入离线缓存（列表 10 分钟有效）
            com.luwu.app.util.CacheManager.putJson(
                this,
                if (mode == "category") "cat_${slug}_${sort}_$page" else "all_${sort}_$page",
                json
            )
            renderList(json)
        }
    }

    /** 渲染 category/home 接口 JSON（网络与缓存共用） */
    private fun renderList(json: org.json.JSONObject) {
        val arr = json.optJSONArray("items") ?: JSONArray()
        val blocked = com.luwu.app.util.Prefs.getBlockedIds(this)
        val list = mutableListOf<PostItem>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { o ->
                val p = PostItem.fromJson(o)
                if (p.authorId <= 0 || p.authorId !in blocked) list.add(p)
            }
        }
        hasMore = json.optBoolean("hasMore", false)
        if (page == 1) {
            adapter?.setPosts(list, null, 5)
        } else {
            adapter?.appendPosts(list, null, 5)
        }
        adapter?.hasMore = hasMore
        adapter?.setFooter(false)
        if (page == 1) renderEmpty(list)
    }

    /** 空态显示：首屏列表为空时展示，并隐藏列表区域 */
    private fun renderEmpty(list: List<PostItem>?) {
        val show = page == 1 && list.isNullOrEmpty() && !loading
        findViewById<View>(R.id.empty_view).visibility = if (show) View.VISIBLE else View.GONE
        findViewById<View>(R.id.swipe_list).visibility = if (show) View.GONE else View.VISIBLE
    }

    /** RSS 兜底：分类 Feed 一次取回，无分页 */
    private fun loadPageFromRss() {
        val title = findViewById<TextView>(R.id.tv_title).text.toString()
        ApiClient.getRss(slug, title) { list, err ->
            swipe?.isRefreshing = false
            loading = false
            if (list == null) {
                Util.toast(this, err ?: getString(R.string.network_error))
                return@getRss
            }
            hasMore = false
            adapter?.setPosts(list, null, 5)
            adapter?.hasMore = false
            adapter?.setFooter(false)
            renderEmpty(list)
        }
    }
}
