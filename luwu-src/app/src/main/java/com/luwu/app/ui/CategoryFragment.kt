package com.luwu.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.luwu.app.MainActivity
import com.luwu.app.R
import com.luwu.app.adapter.BannerAdapter
import com.luwu.app.api.ApiClient
import com.luwu.app.api.Category
import com.luwu.app.api.PostItem
import com.luwu.app.util.AppState
import com.luwu.app.util.Prefs
import com.luwu.app.util.Util

/** 分类页：轮播 + 站点统计 + 全部分类（8 个父分类，4 行 2 列） */
class CategoryFragment : Fragment() {

    private var recycler: RecyclerView? = null
    private var swipe: SwipeRefreshLayout? = null
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_category, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipe = view.findViewById(R.id.swipe_category)
        recycler = view.findViewById(R.id.recycler_category)
        bannerRecycler = view.findViewById(R.id.banner_recycler)
        bannerDots = view.findViewById(R.id.banner_dots)

        // 顶栏：头像 / 搜索（与首页一致）
        view.findViewById<View>(R.id.btn_avatar).setOnClickListener {
            (activity as? MainActivity)?.switchToProfile()
        }
        refreshAvatar()
        view.findViewById<View>(R.id.btn_bell)?.setOnClickListener {
            startActivity(Intent(requireContext(), NotifyActivity::class.java))
        }
        view.findViewById<View>(R.id.btn_search).setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        // 轮播
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

        swipe?.setOnRefreshListener { load() }
        load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bannerHandler.removeCallbacks(bannerRunnable)
    }

    private fun refreshAvatar() {
        val tv = view?.findViewById<TextView>(R.id.tv_avatar_initial) ?: return
        val iv = view?.findViewById<android.widget.ImageView>(R.id.iv_logo)
        if (Prefs.isLoggedIn(requireContext())) {
            val name = Prefs.getUserName(requireContext()).ifBlank { Prefs.getAccount(requireContext()) }
            tv.text = name.take(1).ifBlank { "陆" }
            tv.visibility = View.VISIBLE
            iv?.visibility = View.GONE
        } else {
            // 未登录：显示品牌 Logo 图（纯图形，避免系统汉字注音显示拼音）
            tv.visibility = View.GONE
            iv?.visibility = View.VISIBLE
        }
    }

    private fun load() {
        swipe?.isRefreshing = true
        // 轮播数据：复用首页信息流前几张
        ApiClient.get("home", mapOf("page" to "1", "pageSize" to "10", "sort" to "latest")) { json, _ ->
            if (json != null && json.optBoolean("ok", false)) {
                val arr = json.optJSONArray("items")
                val list = mutableListOf<PostItem>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { list.add(PostItem.fromJson(it)) }
                    }
                }
                updateBanner(list.take(5))
            }
            loadStats()
            loadCategories()
        }
    }

    private fun loadStats() {
        ApiClient.get("stats") { json, _ ->
            swipe?.isRefreshing = false
            if (json != null && json.optBoolean("ok", false)) {
                view?.findViewById<TextView>(R.id.tv_stat_today)?.text = json.optLong("today", 0).toString()
                view?.findViewById<TextView>(R.id.tv_stat_total)?.text = json.optLong("total", 0).toString()
                view?.findViewById<TextView>(R.id.tv_stat_members)?.text = json.optLong("members", 0).toString()
            }
        }
    }

    private fun loadCategories() {
        if (AppState.pluginAvailable == false) {
            swipe?.isRefreshing = false
            renderList(AppState.fallbackCategories())
            return
        }
        ApiClient.get("categories") { json, err ->
            swipe?.isRefreshing = false
            if (json == null || !json.optBoolean("ok", false)) {
                Util.toast(requireContext(), err ?: getString(R.string.network_error))
                return@get
            }
            val arr = json.optJSONArray("items") ?: return@get
            val list = mutableListOf<Category>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(Category.fromJson(it)) }
            }
            renderList(list)
        }
    }

    private fun renderList(list: List<Category>) {
        recycler?.layoutManager = GridLayoutManager(requireContext(), 2)
        recycler?.adapter = CategoryAdapter(list) { cat ->
            startActivity(CategoryPostsActivity.newIntent(requireContext(), cat.slug, cat.name))
        }
    }

    private fun updateBanner(list: List<PostItem>) {
        if (list.isEmpty()) {
            view?.findViewById<View>(R.id.banner_container)?.visibility = View.GONE
            return
        }
        view?.findViewById<View>(R.id.banner_container)?.visibility = View.VISIBLE
        bannerList.clear()
        bannerList.addAll(list)
        bannerAdapter?.setItems(list)
        bannerRecycler?.scrollToPosition(0)
        bannerIndex = 0
        updateBannerDots()
    }

    private fun updateBannerDots() {
        val dots = bannerDots ?: return
        dots.removeAllViews()
        for (i in bannerList.indices) {
            val dot = View(requireContext())
            val size = (6 * resources.displayMetrics.density).toInt()
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = (5 * resources.displayMetrics.density).toInt()
            dot.layoutParams = lp
            dot.background = resources.getDrawable(
                if (i == bannerIndex) R.drawable.bg_dot_on else R.drawable.bg_dot_off,
                null,
            )
            dots.addView(dot)
        }
    }

    private fun openDetail(post: PostItem) {
        startActivity(ArticleDetailActivity.newIntent(requireContext(), post))
    }

    class CategoryAdapter(
        private val list: List<Category>,
        private val onClick: (Category) -> Unit,
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val cat = list[position]
            holder.name.text = cat.name
            holder.count.text = "${cat.count}篇文章"
            val iconUrl = cat.icon
            if (!iconUrl.isNullOrBlank()) {
                holder.img.visibility = View.VISIBLE
                holder.tv.visibility = View.GONE
                com.luwu.app.util.ImageLoader.load(iconUrl, holder.img) {
                    // 加载失败回退首字
                    holder.img.visibility = View.GONE
                    holder.tv.visibility = View.VISIBLE
                    holder.tv.text = cat.name.take(1)
                }
            } else {
                holder.img.visibility = View.GONE
                holder.tv.visibility = View.VISIBLE
                holder.tv.text = cat.name.take(1)
            }
            holder.root.setOnClickListener { onClick(cat) }
        }

        override fun getItemCount(): Int = list.size

        class VH(val root: View) : RecyclerView.ViewHolder(root) {
            val name: TextView = root.findViewById(R.id.tv_cat_name)
            val count: TextView = root.findViewById(R.id.tv_cat_count)
            val img: android.widget.ImageView = root.findViewById(R.id.iv_icon)
            val tv: TextView = root.findViewById(R.id.tv_icon)
        }
    }
}
