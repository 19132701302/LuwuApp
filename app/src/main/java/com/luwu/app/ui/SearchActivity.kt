package com.luwu.app.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luwu.app.R
import com.luwu.app.adapter.PostAdapter
import com.luwu.app.api.ApiClient
import com.luwu.app.api.PostItem
import com.luwu.app.util.AppState
import com.luwu.app.util.Prefs
import com.luwu.app.util.Util
import org.json.JSONArray

class SearchActivity : AppCompatActivity() {

    // 热门搜索：优先取插件 hot_search 接口统计的真实热词，失败/为空时回退本地静态列表
    private var hotKeywords = mutableListOf<String>()
    private val fallbackHotKeywords = listOf(
        "WordPress主题", "网站源码", "免费软件", "Typecho主题", "绿色版", "活动线报",
    )

    private var etKeyword: EditText? = null
    private var recycler: RecyclerView? = null
    private var hotContainer: FlowLayout? = null
    private var historyContainer: FlowLayout? = null
    private var tvClear: TextView? = null
    private var tvHistoryEmpty: TextView? = null
    private var adapter: PostAdapter? = null
    private var field = "title" // title 标题 / content 内容

    // 输入防抖实时搜索：300ms 内停止输入才发起，避免每敲一个字发一次请求
    private var searchSeq = 0
    private val debounceHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        etKeyword = findViewById(R.id.et_keyword)
        recycler = findViewById(R.id.recycler_result)
        hotContainer = findViewById(R.id.hot_container)
        historyContainer = findViewById(R.id.history_container)
        tvClear = findViewById(R.id.tv_clear_history)
        tvHistoryEmpty = findViewById(R.id.tv_history_empty)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        // 一键清空输入：清空后回到热门/历史态
        findViewById<View>(R.id.btn_clear_keyword).setOnClickListener { etKeyword?.setText("") }
        // 搜索页采用商业级白色顶部：不套用主题绿染色（透明状态栏 + 深色图标在白色下显示最佳）
        findViewById<View>(R.id.btn_search).setOnClickListener { doSearch() }
        // 搜索范围切换：标题 / 内容
        val tabTitle = findViewById<android.widget.TextView>(R.id.tab_title)
        val tabContent = findViewById<android.widget.TextView>(R.id.tab_content)
        fun refreshTabs() {
            val on = field == "title"
            tabTitle.background = resources.getDrawable(if (on) R.drawable.bg_sort_on else R.drawable.bg_segment_item_off, null)
            tabTitle.setTextColor(resources.getColor(if (on) android.R.color.white else R.color.ink_2, null))
            tabContent.background = resources.getDrawable(if (!on) R.drawable.bg_sort_on else R.drawable.bg_segment_item_off, null)
            tabContent.setTextColor(resources.getColor(if (!on) android.R.color.white else R.color.ink_2, null))
        }
        tabTitle.setOnClickListener { field = "title"; refreshTabs() }
        tabContent.setOnClickListener { field = "content"; refreshTabs() }
        refreshTabs()
        tvClear?.setOnClickListener {
            Prefs.clearSearchHistory(this)
            renderHistory()
        }

        etKeyword?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                debounceHandler.removeCallbacksAndMessages(null)
                doSearch()
                true
            } else {
                false
            }
        }

        // 输入防抖：停止输入 300ms 后实时搜索（不写入搜索历史）
        etKeyword?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                debounceHandler.removeCallbacksAndMessages(null)
                val q = s?.toString()?.trim().orEmpty()
                // 一键清空按钮：输入非空时显示
                findViewById<View>(R.id.btn_clear_keyword)?.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                if (q.isEmpty()) {
                    searchSeq++
                    adapter?.setPosts(emptyList(), null, 5)
                    adapter?.hasMore = false
                    showEmpty(false)
                    return
                }
                debounceHandler.postDelayed({ liveSearch(q) }, 300)
            }
        })

        recycler?.layoutManager = LinearLayoutManager(this)
        adapter = PostAdapter(
            onPostClick = { startActivity(ArticleDetailActivity.newIntent(this, it)) },
            onAdClick = { Util.openBrowser(this, it) },
        )
        recycler?.adapter = adapter

        renderHistory()
        loadHotKeywords()
    }

    /** 拉取真实热门搜索词（插件 v1.7+）；插件不可用或接口异常时回退静态热词 */
    private fun loadHotKeywords() {
        if (AppState.pluginAvailable == false) {
            hotKeywords = fallbackHotKeywords.toMutableList()
            renderHot()
            return
        }
        ApiClient.get("hot_search") { json, _ ->
            val arr = json?.optJSONArray("items")
            if (arr != null && arr.length() > 0) {
                hotKeywords = mutableListOf<String>().apply {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.optString("word")?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }
            if (hotKeywords.isEmpty()) hotKeywords = fallbackHotKeywords.toMutableList()
            renderHot()
        }
    }

    private fun renderHot() {
        val container = hotContainer ?: return
        container.removeAllViews()
        for (k in hotKeywords) {
            container.addView(chip(k) {
                etKeyword?.setText(it)
                doSearch()
            })
        }
    }

    private fun renderHistory() {
        val container = historyContainer ?: return
        container.removeAllViews()
        val list = Prefs.getSearchHistory(this)
        if (list.isEmpty()) {
            tvClear?.visibility = View.GONE
            tvHistoryEmpty?.visibility = View.VISIBLE
            return
        }
        tvClear?.visibility = View.VISIBLE
        tvHistoryEmpty?.visibility = View.GONE
        for (k in list) {
            container.addView(chip(k) {
                etKeyword?.setText(it)
                doSearch()
            })
        }
    }

    private fun chip(text: String, onClick: (String) -> Unit): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.ink_2))
        tv.textSize = 13.5f
        tv.setBackgroundResource(R.drawable.bg_chip_light)
        val lp = FlowLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        lp.marginEnd = dp(8)
        lp.topMargin = dp(7)
        lp.bottomMargin = dp(7)
        tv.layoutParams = lp
        tv.setPadding(dp(16), dp(8), dp(16), dp(8))
        tv.setOnClickListener { onClick(text) }
        return tv
    }

    /** 实时搜索（防抖触发）：不写历史、不发浏览器兜底 */
    private fun liveSearch(q: String) {
        if (AppState.pluginAvailable == false) return
        search(q, live = true)
    }

    private fun doSearch() {
        val q = etKeyword?.text?.toString()?.trim() ?: ""
        if (q.isEmpty()) {
            Util.toast(this, "请输入关键词")
            return
        }
        Prefs.addSearchHistory(this, q)
        renderHistory()
        // 插件未装 → 打开网站搜索页兜底
        if (AppState.pluginAvailable == false) {
            val url = "https://www.65gw.com/search/" + java.net.URLEncoder.encode(q, "UTF-8") + "/"
            startActivity(WebPageActivity.newIntent(this, url, "搜索：$q"))
            return
        }
        search(q, live = false)
    }

    private fun search(q: String, live: Boolean) {
        val seq = ++searchSeq
        ApiClient.get("search", mapOf("q" to q, "field" to field, "page" to "1", "pageSize" to "20")) { json, err ->
            if (seq != searchSeq) return@get // 过期响应（输入已变化），丢弃
            if (json == null || !json.optBoolean("ok", false)) {
                Util.toast(this, json?.optString("error", "") ?: err ?: "搜索失败")
                return@get
            }
            val arr = json.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<PostItem>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(PostItem.fromJson(it)) }
            }
            adapter?.setPosts(list, null, 5)
            adapter?.hasMore = false
            adapter?.setFooter(false)
            showEmpty(list.isEmpty())
        }
    }

    /** 结果空态：空结果显示占位，与列表互斥 */
    private fun showEmpty(empty: Boolean) {
        findViewById<View>(R.id.empty_view).visibility = if (empty) View.VISIBLE else View.GONE
        findViewById<View>(R.id.recycler_result).visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
