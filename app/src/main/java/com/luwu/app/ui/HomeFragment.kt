package com.luwu.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.luwu.app.R
import com.luwu.app.adapter.PostAdapter
import com.luwu.app.api.ApiClient
import com.luwu.app.adapter.BannerAdapter
import com.luwu.app.api.PostItem
import com.luwu.app.MainActivity
import com.luwu.app.util.AppState
import com.luwu.app.util.Prefs
import com.luwu.app.util.Util
import org.json.JSONObject

class HomeFragment : Fragment() {

    private var swipe: SwipeRefreshLayout? = null
    private var recycler: RecyclerView? = null
    private var tvHomeAd: TextView? = null
    private var errorContainer: View? = null
    private var tvErrorMsg: TextView? = null
    private var tvErrorDetail: TextView? = null
    private var adapter: PostAdapter? = null
    private var page = 1
    private var loading = false
    private var hasMore = true
    private var sort = "latest" // latest / hot / comments / likes
    private var bannerRecycler: RecyclerView? = null
    private var bannerDots: LinearLayout? = null
    private var bannerAdapter: BannerAdapter? = null
    private val bannerList = mutableListOf<PostItem>()
    private var bannerIndex = 0
    private val bannerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val bannerRunnable = object : Runnable {
        override fun run() {
            if (bannerList.size > 1 && bannerRecycler != null) {
                bannerIndex = (bannerIndex + 1) % bannerList.size
                bannerRecycler?.smoothScrollToPosition(bannerIndex)
                updateBannerDots()
            }
            bannerHandler.postDelayed(this, 3500L)
        }
    }
    private val posts = mutableListOf<PostItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipe = view.findViewById(R.id.swipe_refresh)
        recycler = view.findViewById(R.id.recycler)
        bannerRecycler = view.findViewById(R.id.banner_recycler)
        bannerDots = view.findViewById(R.id.banner_dots)
        tvHomeAd = view.findViewById(R.id.tv_home_ad)
        errorContainer = view.findViewById(R.id.error_container)
        tvErrorMsg = view.findViewById(R.id.tv_error_msg)
        tvErrorDetail = view.findViewById(R.id.tv_error_detail)

        view.findViewById<View>(R.id.btn_bell)?.setOnClickListener {
            startActivity(Intent(requireContext(), NotifyActivity::class.java))
        }
        view.findViewById<View>(R.id.btn_search).setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        // 顶部头像：点击进入我的/登录
        view.findViewById<View>(R.id.btn_avatar).setOnClickListener {
            (activity as? MainActivity)?.switchToProfile()
        }
        refreshAvatar()
        // 沉浸透明顶部栏：状态栏高度自适应 + 初始透明（浮在轮播图上）
        val topBar = view.findViewById<android.view.ViewGroup>(R.id.top_bar)
        if (topBar != null) {
            val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val sbh = if (resId > 0) resources.getDimensionPixelSize(resId) else dp(26)
            topBar.setPadding(0, sbh, 0, 0)
            topBar.background = null
        }
        bindHomeCats(view)
        view.findViewById<View>(R.id.btn_retry).setOnClickListener {
            hideErrorView()
            refresh()
        }
        view.findViewById<View>(R.id.btn_open_browser).setOnClickListener {
            Util.openBrowser(requireContext(), "https://www.65gw.com")
        }

        swipe?.setOnRefreshListener { refresh() }
        recycler?.layoutManager = LinearLayoutManager(requireContext())
        adapter = PostAdapter(
            onPostClick = { openDetail(it) },
            onAdClick = { Util.openBrowser(requireContext(), it) },
        )
        recycler?.adapter = adapter
        // 内容整体滚动：NestedScrollView 触底加载更多
        val scrollView = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.home_scroll)
        val onBottom = {
            val child = scrollView.getChildAt(0)
            if (child != null) {
                val diff = child.height - scrollView.height - scrollView.scrollY
                if (!loading && hasMore && diff < 300) {
                    loadMore()
                }
            }
        }
        val btnBackTop = view.findViewById<View>(R.id.btn_back_top_home)
        btnBackTop?.setOnClickListener {
            scrollView.smoothScrollTo(0, 0)
        }
        val updateBackTop = {
            val show = scrollView.scrollY > 800
            btnBackTop?.visibility = if (show) View.VISIBLE else View.GONE
        }
        // 沉浸顶栏：顶部透明（露出轮播图）→ 滚动后实底白（不遮挡内容，头条式）
        val updateTopBar = {
            val tb = view.findViewById<View>(R.id.top_bar)
            if (tb != null) {
                if (scrollView.scrollY > 4) {
                    tb.background = resources.getDrawable(R.drawable.bg_top_bar_solid, null)
                    tb.elevation = 2f
                } else {
                    tb.background = null
                    tb.elevation = 0f
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
                onBottom()
                updateBackTop()
                updateTopBar()
            }
        } else {
            scrollView.viewTreeObserver.addOnScrollChangedListener {
                onBottom()
                updateBackTop()
                updateTopBar()
            }
        }

        // 轮播：横向 + 吸附对齐 + 自动播放
        bannerRecycler?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(),
            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
            false,
        )
        bannerAdapter = BannerAdapter(onItemClick = { openDetail(it) })
        bannerRecycler?.adapter = bannerAdapter
        bannerRecycler?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                val pos = lm.findFirstVisibleItemPosition()
                if (pos != RecyclerView.NO_POSITION && pos != bannerIndex) {
                    bannerIndex = pos
                    updateBannerDots()
                }
            }
        })
        bannerHandler.postDelayed(bannerRunnable, 3500L)

        refresh()
    }

    override fun onDestroyView() {
        bannerHandler.removeCallbacks(bannerRunnable)
        super.onDestroyView()
    }

    private fun updateBanner(list: List<PostItem>) {
        val bs = list.take(5).filter { it.thumb.isNotBlank() }
        if (bs.isEmpty()) {
            view?.findViewById<View>(R.id.banner_container)?.visibility = View.GONE
            return
        }
        view?.findViewById<View>(R.id.banner_container)?.visibility = View.VISIBLE
        bannerList.clear()
        bannerList.addAll(bs)
        bannerAdapter?.setItems(bs)
        bannerIndex = 0
        bannerRecycler?.scrollToPosition(0)
        updateBannerDots()
    }

    private fun updateBannerDots() {
        val dots = bannerDots ?: return
        dots.removeAllViews()
        for (i in bannerList.indices) {
            val dot = View(requireContext())
            val size = dp(6)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = dp(5)
            dot.layoutParams = lp
            dot.background = resources.getDrawable(
                if (i == bannerIndex) R.drawable.bg_dot_on else R.drawable.bg_dot_off,
                null,
            )
            dots.addView(dot)
        }
    }

    fun onAdsLoaded() {
        val v = view ?: return
        v.post {
            val ads = AppState.ads ?: return@post
            // 分类卡片：后台配置就绪后重新绑定（修复首页分类一直显示内置默认、后台矢量图标不生效的问题）
            bindHomeCats(v)
            val list = ads.homeTop
            val tv = tvHomeAd ?: return@post
            val iv = v.findViewById<View>(R.id.iv_home_ad) as? android.widget.ImageView ?: return@post
            val container = v.findViewById<View>(R.id.home_ad_container)
            if (!ads.enabled || list.isEmpty()) {
                container?.visibility = View.GONE
                return@post
            }
            // 多广告轮换：随机取一条
            val ad = list[(Math.random() * list.size).toInt().coerceAtMost(list.size - 1)]
            val useImg = ad.type != "text" && ad.img.isNotBlank()
            if (useImg) {
                tv.visibility = View.GONE
                iv.visibility = View.VISIBLE
                container?.visibility = View.VISIBLE
                com.luwu.app.util.ImageLoader.load(ad.img, iv)
                iv.setOnClickListener { Util.openBrowser(requireContext(), ad.link) }
            } else {
                if (ad.text.isBlank()) {
                    container?.visibility = View.GONE
                    return@post
                }
                iv.visibility = View.GONE
                tv.visibility = View.VISIBLE
                container?.visibility = View.VISIBLE
                tv.text = ad.text
                tv.setOnClickListener { Util.openBrowser(requireContext(), ad.link) }
            }
        }
    }

    /** 快捷入口宫格：对应网页端分类导航 */

    /** 排序栏：最新 / 热门 / 点赞最多 */

    private fun refresh() {
        page = 1
        hasMore = true
        posts.clear()
        loadPage()
    }

    /** 排序栏绑定：最新 / 热门 / 评论最多 / 点赞最多 */
    private fun bindSortBar() {
        val ids = listOf(R.id.sort_latest, R.id.sort_hot, R.id.sort_comments, R.id.sort_likes)
        val vals = listOf("latest", "hot", "comments", "likes")
        val tabs = ids.map { view?.findViewById<android.widget.TextView>(it) }
        fun refreshTabs() {
            for (i in ids.indices) {
                val tv = tabs[i] ?: continue
                val on = sort == vals[i]
                tv.background = resources.getDrawable(if (on) R.drawable.bg_sort_on else R.drawable.bg_sort_off, null)
                tv.setTextColor(resources.getColor(if (on) android.R.color.white else R.color.ink_2, null))
            }
        }
        for (i in ids.indices) {
            tabs[i]?.setOnClickListener {
                sort = vals[i]
                refreshTabs()
                refresh()
            }
        }
        refreshTabs()
    }

    private fun loadMore() {
        if (!hasMore) return
        page++
        loadPage()
    }

    /** 渲染 home 接口 JSON（网络与缓存共用） */
    private fun bindFromJson(json: org.json.JSONObject) {
        val arr = json.optJSONArray("items") ?: org.json.JSONObject.NULL
        val blocked = com.luwu.app.util.Prefs.getBlockedIds(requireContext())
        val list = mutableListOf<PostItem>()
        if (arr is org.json.JSONArray) {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { o ->
                    val p = PostItem.fromJson(o)
                    if (p.authorId <= 0 || p.authorId !in blocked) list.add(p)
                }
            }
        }
        hasMore = json.optBoolean("hasMore", false)
        if (page == 1) updateBanner(list)
        posts.addAll(list)
        val ads = AppState.ads
        val ad = if (ads != null && ads.enabled && ads.feed.isNotEmpty())
            ads.feed[(Math.random() * ads.feed.size).toInt().coerceAtMost(ads.feed.size - 1)] else null
        val every = ads?.feedEvery ?: 5
        if (page == 1) {
            adapter?.setPosts(posts, ad, every)
        } else {
            adapter?.appendPosts(list, ad, every)
        }
        adapter?.hasMore = hasMore
        adapter?.setFooter(false)
    }

    private fun loadPage() {
        if (loading) return
        loading = true
        if (page == 1) swipe?.isRefreshing = true
        // 首屏骨架屏
        if (page == 1 && posts.isEmpty()) showSkeleton()

        // 插件不可用 → 直接走 RSS 兜底（网站自带 Feed）
        if (AppState.pluginAvailable == false) {
            loadPageFromRss()
            return
        }

        ApiClient.get("home", mapOf(
            "sort" to sort,
            "page" to page.toString(),
            "pageSize" to "20",
        )) { json, err ->
            swipe?.isRefreshing = false
            loading = false
            if (json == null || !json.optBoolean("ok", false)) {
                // 网络失败 → 优先用离线缓存兜底（弱网/断网可看已加载内容）
                if (page == 1 && posts.isEmpty()) {
                    val cached = com.luwu.app.util.CacheManager.getList(requireContext(), "home_${sort}_1")
                    if (cached != null && cached.optBoolean("ok", false)) {
                        Util.toast(requireContext(), "网络不可用，显示缓存内容")
                        bindFromJson(cached)
                        return@get
                    }
                }
                // 接口不可用 → 判定插件未装，切换兜底
                AppState.pluginAvailable = false
                AppState.ads = AppState.defaultAds()
                onAdsLoaded()
                if (posts.isEmpty()) {
                    loadPageFromRss()
                } else {
                    adapter?.hasMore = false
                    adapter?.setFooter(false)
                }
                return@get
            }
            AppState.pluginAvailable = true
            // 写入离线缓存（首页列表 10 分钟有效）
            com.luwu.app.util.CacheManager.putJson(requireContext(), "home_${sort}_$page", json)
            hideErrorView()
            bindFromJson(json)
        }
    }

    /** RSS 兜底：主 Feed 无分页，一次取回 */
    private fun loadPageFromRss() {
        ApiClient.getRss(null, "") { list, err ->
            swipe?.isRefreshing = false
            loading = false
            if (list == null) {
                if (posts.isEmpty()) showError(err)
                return@getRss
            }
            posts.clear()
            posts.addAll(list)
            hasMore = false
            hideErrorView()
            val ads = AppState.ads
            val ad = if (ads != null && ads.enabled && ads.feed.isNotEmpty())
                ads.feed[(Math.random() * ads.feed.size).toInt().coerceAtMost(ads.feed.size - 1)] else null
            adapter?.setPosts(posts, ad, ads?.feedEvery ?: 5)
            adapter?.hasMore = false
            adapter?.setFooter(false)
        }
    }

    private fun showError(err: String?) {
        view?.post {
            hideSkeleton()
            tvErrorMsg?.text = getString(R.string.error_title)
            tvErrorDetail?.text = err ?: getString(R.string.network_error)
            errorContainer?.visibility = View.VISIBLE
            swipe?.visibility = View.GONE
            recycler?.visibility = View.GONE
        }
    }

    private fun hideErrorView() {
        errorContainer?.visibility = View.GONE
        swipe?.visibility = View.VISIBLE
        recycler?.visibility = View.VISIBLE
        hideSkeleton()
    }

    /** 骨架屏：首屏加载中且列表为空时显示占位，隐藏轮播/分类/广告/列表 */
    private fun showSkeleton() {
        view?.post {
            val sc = view?.findViewById<View>(R.id.skeleton_container) ?: return@post
            if (posts.isNotEmpty()) return@post
            sc.visibility = View.VISIBLE
            view?.findViewById<View>(R.id.banner_container)?.visibility = View.GONE
            view?.findViewById<View>(R.id.home_cats)?.visibility = View.GONE
            view?.findViewById<View>(R.id.home_ad_container)?.visibility = View.GONE
            recycler?.visibility = View.GONE
        }
    }

    private fun hideSkeleton() {
        view?.post {
            val sc = view?.findViewById<View>(R.id.skeleton_container) ?: return@post
            sc.visibility = View.GONE
            view?.findViewById<View>(R.id.banner_container)?.visibility = View.VISIBLE
            view?.findViewById<View>(R.id.home_cats)?.visibility = View.VISIBLE
            recycler?.visibility = View.VISIBLE
            // 重新按广告配置恢复广告位可见性（showSkeleton 会隐藏它，此处必须还原）
            onAdsLoaded()
        }
    }

    private fun openDetail(post: PostItem) {
        startActivity(ArticleDetailActivity.newIntent(requireContext(), post))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun darken(color: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * 0.82f).coerceAtLeast(0f)
        return android.graphics.Color.HSVToColor(hsv)
    }
    /** 分类金刚区（2×2 大图标入口）：后台 homeCats 优先，空则内置默认 4 分类 */
    private fun bindHomeCats(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.home_cats) ?: return
        rv.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
        val conf = AppState.ads?.homeCats
        val cats = if (!conf.isNullOrEmpty()) conf.map { HomeCat(it.slug, it.name, it.desc, it.icon, it.color) } else listOf(
            HomeCat("default", "网站源码", "汇集优质源码资源", "源", "teal"),
            HomeCat("jsjc", "技术教程", "高端技术教程分享", "技", "purple"),
            HomeCat("yingyong", "绿色软件", "丰富软件资源", "软", "blue"),
            HomeCat("fulihuodong", "活动线报", "创意无限，福利不断", "福", "yellow"),
        )
        rv.adapter = HomeCatAdapter(cats) { cat ->
            startActivity(CategoryPostsActivity.newIntent(requireContext(), cat.slug, cat.name))
        }
        bindSortBar()
    }

    data class HomeCat(val slug: String, val name: String, val desc: String, val icon: String = "", val color: String = "")

    class HomeCatAdapter(
        private val list: List<HomeCat>,
        private val onClick: (HomeCat) -> Unit,
    ) : RecyclerView.Adapter<HomeCatAdapter.VH>() {

        class VH(val root: android.view.View) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_home_cat, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val cat = list[position]
            val iv = holder.root.findViewById<android.widget.ImageView>(R.id.iv_cat_icon)
            val tv = holder.root.findViewById<TextView>(R.id.tv_cat_icon)
            val icon = cat.icon
            if (icon.startsWith("http")) {
                // 服务端统一图标 URL：加载图片，失败回退内置矢量图标（不再显示单字）
                iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                iv.clearColorFilter()
                com.luwu.app.util.ImageLoader.load(icon, iv) {
                    val rid = catIconRes(icon)
                    if (rid != 0) {
                        iv.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                        iv.setImageResource(rid)
                        iv.imageTintList = android.content.res.ColorStateList.valueOf(catColor(cat.color))
                        iv.visibility = View.VISIBLE
                        tv.visibility = View.GONE
                    } else {
                        iv.visibility = View.GONE
                        tv.text = cat.name.take(1)
                        tv.setTextColor(catColor(cat.color))
                        tv.visibility = View.VISIBLE
                    }
                }
                iv.visibility = View.VISIBLE
                tv.visibility = View.GONE
            } else {
                val rid = catIconRes(icon)
                if (rid != 0) {
                    iv.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    iv.setImageResource(rid)
                    iv.imageTintList = android.content.res.ColorStateList.valueOf(catColor(cat.color))
                    iv.visibility = View.VISIBLE
                    tv.visibility = View.GONE
                } else {
                    tv.text = icon.ifBlank { cat.name.take(1) }
                    tv.setTextColor(catColor(cat.color))
                    tv.visibility = View.VISIBLE
                    iv.visibility = View.GONE
                }
            }
            holder.root.findViewById<TextView>(R.id.tv_cat_name).text = cat.name
            holder.root.findViewById<TextView>(R.id.tv_cat_desc).text = cat.desc
            holder.root.setOnClickListener { onClick(cat) }
        }

        override fun getItemCount(): Int = list.size
    }

    override fun onResume() {
        super.onResume()
        refreshBellBadge()
    }

    /** 顶栏铃铛未读徽标（99+ 封顶） */
    private fun refreshBellBadge() {
        val badge = view?.findViewById<TextView>(R.id.tv_bell_badge) ?: return
        if (Prefs.getUid(requireContext()) <= 0) {
            badge.visibility = View.GONE
            return
        }
        ApiClient.post("notify", mapOf()) { json, _ ->
            val unread = json?.optInt("unread", 0) ?: 0
            badge.visibility = if (unread > 0) View.VISIBLE else View.GONE
            badge.text = if (unread > 99) "99+" else unread.toString()
        }
    }

    /** 顶部头像：优先服务器头像图片，否则用户名首字 */
    fun refreshAvatar() {
        val ctx = requireContext()
        val tv = view?.findViewById<android.widget.TextView>(R.id.tv_avatar_initial) ?: return
        val iv = view?.findViewById<android.widget.ImageView>(R.id.iv_avatar_initial)
        val avatar = Prefs.getAvatar(ctx)
        if (Prefs.isLoggedIn(ctx) && avatar.isNotBlank()) {
            tv.visibility = View.GONE
            iv?.visibility = View.VISIBLE
            com.luwu.app.util.ImageLoader.load(avatar, iv ?: return)
        } else {
            if (Prefs.isLoggedIn(ctx)) {
                val name = Prefs.getUserName(ctx).ifBlank { Prefs.getAccount(ctx) }
                tv.text = name.take(1).ifBlank { "陆" }
                tv.visibility = View.VISIBLE
                iv?.visibility = View.GONE
            } else {
                // 未登录：显示网站品牌图标（纯图形，避免系统汉字注音显示拼音）
                tv.visibility = View.GONE
                iv?.visibility = View.VISIBLE
                iv?.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                iv?.setImageResource(com.luwu.app.R.drawable.logo_site)
            }
        }
    }


}

/** 分类图标映射：后台 [icon-xxx] 短代码 → 内置图标（兼容 icon- 前缀与后缀） */
private fun catIconRes(icon: String): Int {
    val key = icon.lowercase().removePrefix("icon-")
    return when {
        key.contains("trend") || key.contains("hot") -> com.luwu.app.R.drawable.ic_cat_trend
        key.contains("vip") -> com.luwu.app.R.drawable.ic_cat_vip
        key.contains("wallet") || key.contains("money") -> com.luwu.app.R.drawable.ic_cat_wallet
        key.contains("poster") || key.contains("banner") || key.contains("ad") -> com.luwu.app.R.drawable.ic_cat_poster
        else -> 0
    }
}

/** 分类颜色映射：c-blue / c-purple / c-yellow 等 → 颜色值 */
private fun catColor(color: String): Int = when (color) {
    "blue" -> 0xFF3B5FBF.toInt()
    "purple" -> 0xFF7A4A8C.toInt()
    "yellow" -> 0xFFA4622A.toInt()
    "green" -> 0xFF0F766E.toInt()
    "red" -> 0xFFA4622A.toInt()
    "orange" -> 0xFFA4622A.toInt()
    "pink" -> 0xFF7A4A8C.toInt()
    "teal", "cyan", "green-2" -> 0xFF0F766E.toInt()
    else -> 0xFF0F766E.toInt()
}
