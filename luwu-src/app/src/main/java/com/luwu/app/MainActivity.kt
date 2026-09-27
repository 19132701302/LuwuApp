package com.luwu.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.luwu.app.api.Announcement
import com.luwu.app.api.ApiClient
import com.luwu.app.ui.AnnouncementDialog
import com.luwu.app.ui.CategoryFragment
import com.luwu.app.ui.FavoritesFragment
import com.luwu.app.ui.HomeFragment
import com.luwu.app.ui.ProfileFragment
import com.luwu.app.ui.PrivacyActivity
import com.luwu.app.ui.PublishActivity
import com.luwu.app.util.Prefs
import com.luwu.app.util.PushChecker
import com.luwu.app.ui.UpdateChecker
import com.luwu.app.util.AppState

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private var homeFragment: HomeFragment? = null
    private var categoryFragment: CategoryFragment? = null
    private var profileFragment: ProfileFragment? = null
    private var favoriteFragment: FavoritesFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        com.luwu.app.util.ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 状态栏跟随主题色
        val theme = com.luwu.app.util.Prefs.getThemeColor(this)
        window.statusBarColor = theme
        if (com.luwu.app.util.Util.isDarkTheme(theme)) {
            window.decorView.systemUiVisibility = 0
        }

        bottomNav = findViewById(R.id.bottom_nav)
        // 底部导航选中色跟随设置页主题色
        try {
            val theme = Prefs.getThemeColor(this)
            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(),
            )
            val colors = intArrayOf(theme, androidx.core.content.ContextCompat.getColor(this, R.color.nav_unselected))
            bottomNav.itemIconTintList = android.content.res.ColorStateList(states, colors)
            bottomNav.itemTextColor = android.content.res.ColorStateList(states, colors)
        } catch (_: Exception) {}
        bottomNav.setOnItemSelectedListener { item ->
            // 中间槽位（空白占位）：点击与发帖按钮一致，直接打开发布页
            if (item.itemId == R.id.nav_publish) {
                startActivity(android.content.Intent(this, PublishActivity::class.java))
                return@setOnItemSelectedListener false
            }
            switchTab(item.itemId)
            true
        }
        // 中间发帖按钮：独立于底部导航，点击打开发布页
        findViewById<View>(R.id.btn_publish_fab).setOnClickListener {
            startActivity(android.content.Intent(this, PublishActivity::class.java))
        }

        // 首屏只加载首页；分类/我的首次点击时创建。
        // 进程回收/旋转重建后 FragmentManager 自动恢复实例，用 tag 找回即可
        if (savedInstanceState == null) {
            homeFragment = HomeFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, homeFragment!!, "home")
                .commit()
        } else {
            homeFragment = supportFragmentManager.findFragmentByTag("home") as? HomeFragment
            categoryFragment = supportFragmentManager.findFragmentByTag("category") as? CategoryFragment
            profileFragment = supportFragmentManager.findFragmentByTag("profile") as? ProfileFragment
            favoriteFragment = supportFragmentManager.findFragmentByTag("favorite") as? FavoritesFragment
        }

        bottomNav.selectedItemId = R.id.nav_home

        // 推送通知：启动轮询 + Android 13+ 运行时权限
        PushChecker.start(this)
        requestNotificationPermissionIfNeeded()

        handlePushIntent(intent)
        checkPrivacyAgreement()

        // 冷启动耗时埋点：首帧后记录（启动 → 首页可交互），保留最近 10 次，关于页展示
        findViewById<android.view.View>(android.R.id.content)?.post {
            val elapsed = android.os.SystemClock.elapsedRealtime() - App.coldStartTs
            if (elapsed in 200..60000) {
                try {
                    val sp = getSharedPreferences("luwu_perf", MODE_PRIVATE)
                    val cur = sp.getString("cold_ms", "") ?: ""
                    val list = (if (cur.isBlank()) emptyList() else cur.split(","))
                        .mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }.toMutableList()
                    list.add(elapsed)
                    while (list.size > 10) list.removeAt(0)
                    sp.edit().putString("cold_ms", list.joinToString(",")).apply()
                } catch (_: Exception) {}
            }
        }
    }

    /** Android 13（API 33+）动态申请通知权限 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runOnUiThread {
                    try {
                        requestPermissions(
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            9001
                        )
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /** 从通知进入：解析跳转目标 */
    private fun handlePushIntent(intent: Intent?) {
        if (intent == null) return
        if (!intent.getBooleanExtra("push_from", false)) return
        val targetType = intent.getStringExtra("push_target_type") ?: "none"
        val targetId = intent.getIntExtra("push_target_id", 0)
        val targetUrl = intent.getStringExtra("push_target_url") ?: ""
        when (targetType) {
            "post" -> {
                if (targetId > 0) {
                    startActivity(
                        android.content.Intent(this, com.luwu.app.ui.ArticleDetailActivity::class.java)
                            .putExtra("post_id", targetId)
                            .putExtra("post_title", "推送文章")
                    )
                }
            }
            "category" -> {
                if (targetId > 0) openCategoryById(targetId)
            }
            "url" -> {
                if (targetUrl.isNotBlank()) {
                    startActivity(com.luwu.app.ui.WebPageActivity.newIntent(this, targetUrl, "陆伍推送"))
                }
            }
        }
    }

    /** 分类 ID → slug（App 分类接口实时查），再打开分类文章页 */
    private fun openCategoryById(mid: Int) {
        ApiClient.get("categories") { json, _ ->
            if (json == null || !json.optBoolean("ok", false)) return@get
            val arr = json.optJSONArray("items") ?: return@get
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                if (it.optInt("mid", 0) == mid || it.optInt("id", 0) == mid) {
                    val slug = it.optString("slug", "")
                    val name = it.optString("name", "分类")
                    if (slug.isNotBlank()) {
                        startActivity(com.luwu.app.ui.CategoryPostsActivity.newIntent(this, slug, name))
                    }
                    return@get
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushIntent(intent)
    }

    /** 首次启动：用户协议与隐私政策确认（商用合规必需，不同意则退出） */
    private fun checkPrivacyAgreement() {
        if (Prefs.isAgreed(this)) {
            loadLaunchData()
            showLastCrashIfAny()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("用户协议与隐私政策")
            .setMessage("欢迎使用陆伍博客 App。\n\n在使用前，请仔细阅读《用户协议》与《隐私政策》。点击“同意并继续”即表示您已阅读并同意全部条款；点击“不同意”将退出应用。")
            .setPositiveButton("同意并继续") { _, _ ->
                Prefs.markAgreed(this)
                loadLaunchData()
                showLastCrashIfAny()
            }
            .setNegativeButton("不同意") { _, _ -> finish() }
            .setNeutralButton("查看隐私政策") { d, _ ->
                d.dismiss()
                startActivity(android.content.Intent(this, PrivacyActivity::class.java))
                checkPrivacyAgreement()
            }
            .setCancelable(false)
            .show()
    }

    private fun switchTab(id: Int) {
        val fm = supportFragmentManager
        val ft = fm.beginTransaction()
        when (id) {
            R.id.nav_home -> {
                val home = homeFragment ?: fm.findFragmentByTag("home") as? HomeFragment ?: return
                ft.show(home)
                categoryFragment?.let { if (it.isAdded) ft.hide(it) }
                profileFragment?.let { if (it.isAdded) ft.hide(it) }
                favoriteFragment?.let { if (it.isAdded) ft.hide(it) }
            }
            R.id.nav_category -> {
                val home = homeFragment ?: fm.findFragmentByTag("home") as? HomeFragment ?: return
                var category = categoryFragment ?: fm.findFragmentByTag("category") as? CategoryFragment
                if (category == null) {
                    category = CategoryFragment()
                    categoryFragment = category
                }
                if (category.isAdded) {
                    ft.show(category)
                } else {
                    ft.add(R.id.fragment_container, category, "category")
                }
                ft.hide(home)
                profileFragment?.let { if (it.isAdded) ft.hide(it) }
                favoriteFragment?.let { if (it.isAdded) ft.hide(it) }
            }
            R.id.nav_favorite -> {
                val home = homeFragment ?: fm.findFragmentByTag("home") as? HomeFragment ?: return
                var favorite = favoriteFragment ?: fm.findFragmentByTag("favorite") as? FavoritesFragment
                if (favorite == null) {
                    favorite = FavoritesFragment()
                    favoriteFragment = favorite
                }
                if (favorite.isAdded) {
                    ft.show(favorite)
                } else {
                    ft.add(R.id.fragment_container, favorite, "favorite")
                }
                ft.hide(home)
                categoryFragment?.let { if (it.isAdded) ft.hide(it) }
                profileFragment?.let { if (it.isAdded) ft.hide(it) }
            }
            R.id.nav_profile -> {
                val home = homeFragment ?: fm.findFragmentByTag("home") as? HomeFragment ?: return
                var profile = profileFragment ?: fm.findFragmentByTag("profile") as? ProfileFragment
                if (profile == null) {
                    profile = ProfileFragment()
                    profileFragment = profile
                }
                if (profile.isAdded) {
                    ft.show(profile)
                } else {
                    ft.add(R.id.fragment_container, profile, "profile")
                }
                ft.hide(home)
                categoryFragment?.let { if (it.isAdded) ft.hide(it) }
                favoriteFragment?.let { if (it.isAdded) ft.hide(it) }
            }
        }
        ft.commitAllowingStateLoss()
    }

    /** 顶部头像点击：跳到"我的"tab（首页头像入口） */
    fun switchToProfile() {
        bottomNav.selectedItemId = R.id.nav_profile
    }

    /** 上次崩溃日志（由 App 全局异常捕获写入），弹窗给用户看，便于反馈问题 */
    private fun showLastCrashIfAny() {
        val sp = getSharedPreferences("luwu_crash", MODE_PRIVATE)
        val last = sp.getString("last_crash", null) ?: return
        sp.edit().remove("last_crash").apply()
        try {
            AlertDialog.Builder(this)
                .setTitle("上次运行遇到问题")
                .setMessage("已记录错误信息，请把这段内容发给开发者：\n\n${last.take(1500)}")
                .setPositiveButton("知道了", null)
                .show()
        } catch (_: Exception) {
        }
    }

    /** 启动时加载：公告（只弹一次）+ 检查更新 + 广告配置 */
    private fun loadLaunchData() {
        // 广告配置（全局共享）；接口不可用 = 插件未装，切换为 RSS 兜底 + 内置广告
        ApiClient.get("ads") { json, _ ->
            if (json != null && json.optBoolean("ok", false)) {
                AppState.pluginAvailable = true
                AppState.ads = com.luwu.app.api.AdsConfig.fromJson(json)
            } else {
                AppState.pluginAvailable = false
                AppState.ads = AppState.defaultAds()
            }
            (homeFragment ?: supportFragmentManager.findFragmentByTag("home") as? HomeFragment)
                ?.onAdsLoaded()
        }
        // 公告弹窗：每次进程启动一次
        if (!AppState.announcementChecked) {
            AppState.announcementChecked = true
            ApiClient.get("announcement") { json, _ ->
                if (json != null) {
                    val ann = Announcement.fromJson(json)
                    if (ann.enabled && ann.content.isNotBlank()) {
                        AnnouncementDialog.show(this, ann)
                    }
                }
            }
        }
        // 检查更新
        UpdateChecker.check(this)
    }

    override fun onResume() {
        super.onResume()
        (profileFragment ?: supportFragmentManager.findFragmentByTag("profile") as? ProfileFragment)
            ?.onResumeRefresh()
        refreshProfileBadge()
    }

    /** 「我的」tab 未读角标：与铃铛同源（消息中心 unread），登录后显示 */
    private fun refreshProfileBadge() {
        val nav = bottomNav ?: return
        val badge = nav.getOrCreateBadge(R.id.nav_profile)
        badge.backgroundColor = androidx.core.content.ContextCompat.getColor(this, R.color.brand_red)
        badge.badgeTextColor = androidx.core.content.ContextCompat.getColor(this, R.color.white)
        if (Prefs.getUid(this) <= 0) {
            badge.isVisible = false
            return
        }
        ApiClient.post("notify", mapOf()) { json, _ ->
            val unread = json?.optInt("unread", 0) ?: 0
            if (unread > 0) {
                badge.number = unread.coerceAtMost(999)
                badge.isVisible = true
            } else {
                badge.isVisible = false
            }
        }
    }
}
