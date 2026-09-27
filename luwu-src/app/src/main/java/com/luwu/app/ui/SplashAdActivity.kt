package com.luwu.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.luwu.app.MainActivity
import com.luwu.app.R
import com.luwu.app.api.ApiClient
import com.luwu.app.util.ImageLoader
import com.luwu.app.util.Prefs
import com.luwu.app.util.ThemeManager
import com.luwu.app.util.Util
import org.json.JSONObject

/**
 * 开屏广告页（App 启动入口）v7.6 商业级升级
 *
 * 双路径启动：
 *  A. 秒开路径：上轮已缓存配置+图片 → 立即本地渲染（零网络，磁盘缓存命中）→ 后台刷新配置/预下载下一张
 *  B. 冷启动路径：品牌加载层兜底（本地资源，零网络）→ 拉取配置 → 采样解码展示 → 缓存配置供下次秒开
 *
 * 商业级合规与体验：
 *  - 显著"广告"角标（左上角）
 *  - 倒计时+跳过合并胶囊（minHeight≥36dp，周边隐形安全区）
 *  - 点击区域限定在 CTA 按钮（不再整图可点）
 *  - 底部品牌区（logo 名称 + 标语）
 *  - 图片 inSampleSize 采样解码 + 磁盘缓存 + WiFi 预下载
 *
 * 兜底策略：倒计时结束 / 图片加载失败 / 网络超时 / 最长等待时间 任一触发即无条件进入首页
 */
class SplashAdActivity : AppCompatActivity() {

    private var countdown = 3
    private var skipText = "跳过"
    private var finished = false
    private var adEnable = false
    private var adImage = ""
    private var adTarget = ""
    private var oncePerDay = false
    private val handler = Handler(Looper.getMainLooper())

    private var btnSkip: TextView? = null
    private var btnCta: TextView? = null
    private var tvBadge: TextView? = null
    private var ivAd: ImageView? = null
    private var loading: View? = null
    private var brandZone: View? = null

    /** 兜底：无论广告/网络状态如何，最迟 duration + 5 秒后强制进入首页 */
    private var forceEnterRunnable: Runnable? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_ad)

        btnSkip = findViewById(R.id.btn_splash_skip)
        btnCta = findViewById(R.id.btn_splash_cta)
        tvBadge = findViewById(R.id.tv_ad_badge)
        ivAd = findViewById(R.id.iv_splash_ad)
        loading = findViewById(R.id.splash_loading)
        brandZone = findViewById(R.id.splash_brand)

        // 跳过按钮：一键关闭（合规），立即进入首页
        btnSkip?.setOnClickListener {
            report("skip")
            enterMain()
        }
        // CTA 限定点击区域：仅此处可点击跳转（合规），点击后进落地页并进首页
        btnCta?.setOnClickListener {
            if (!adEnable) return@setOnClickListener
            report("click")
            if (adTarget.isNotBlank()) {
                try {
                    Util.openBrowser(this, adTarget)
                } catch (_: Exception) {
                }
            }
            enterMain()
        }

        // 秒开路径：上轮预下载的配置+图片直接本地渲染，不等网络
        if (Prefs.getSplashNextUrl(this).isNotBlank()) {
            applyCachedConfig()
            return
        }
        // 冷启动路径：拉取后台配置
        loadConfig()
    }

    /** 秒开路径：应用缓存配置并展示，随后后台刷新 */
    private fun applyCachedConfig() {
        adEnable = true
        adImage = Prefs.getSplashNextUrl(this)
        adTarget = Prefs.getSplashNextTarget(this)
        countdown = Prefs.getSplashNextDuration(this).coerceIn(1, 10)
        skipText = Prefs.getSplashNextSkipText(this)
        oncePerDay = Prefs.getSplashNextOnce(this)

        // 每日一次：今天已展示过则直接进首页
        if (oncePerDay && Prefs.isSplashShownToday(this)) {
            finishToMain()
            return
        }
        if (oncePerDay) {
            Prefs.markSplashShownToday(this)
        }
        btnSkip?.text = "$countdown $skipText"
        showAd()
        // 后台刷新配置：广告已更换则换图；并预下载下一张
        refreshConfig()
    }

    /** 冷启动路径：拉取配置 → 展示 */
    private fun loadConfig() {
        ApiClient.get("splash_ad") { json, _ ->
            if (json == null || !json.optBoolean("ok", false)) {
                finishToMain()
                return@get
            }
            val enable = json.optBoolean("enable", false)
            if (!enable) {
                finishToMain()
                return@get
            }
            adEnable = true
            adImage = json.optString("image_url", "")
            adTarget = json.optString("target_url", "")
            countdown = json.optInt("duration", 3).coerceIn(1, 10)
            skipText = json.optString("skip_text", "跳过")
            oncePerDay = json.optBoolean("once_per_day", false)

            // 每日一次：今天已展示过则直接进首页
            if (oncePerDay && Prefs.isSplashShownToday(this)) {
                finishToMain()
                return@get
            }
            if (oncePerDay) {
                Prefs.markSplashShownToday(this)
            }
            btnSkip?.text = "$countdown $skipText"
            showAd()
            // 本次展示的图片已由 ImageLoader 落磁盘缓存，存配置供下次冷启动秒开
            saveNextConfig()
        }
    }

    /** 秒开路径的后台刷新：广告变更则换图；WiFi 下预下载下一张 */
    private fun refreshConfig() {
        ApiClient.get("splash_ad") { json, _ ->
            if (finished) return@get
            if (json == null || !json.optBoolean("ok", false)) return@get
            val enable = json.optBoolean("enable", false)
            val newImage = json.optString("image_url", "")
            if (!enable || newImage.isBlank()) return@get
            if (newImage != adImage) {
                // 后台配置已更换：换新图展示
                adImage = newImage
                adTarget = json.optString("target_url", "")
                countdown = json.optInt("duration", 3).coerceIn(1, 10)
                skipText = json.optString("skip_text", "跳过")
                oncePerDay = json.optBoolean("once_per_day", false)
                btnSkip?.text = "$countdown $skipText"
                showAd()
            }
            saveNextConfig()
            // WiFi 下预下载下一张（当前图已磁盘缓存，此操作保证下次冷启动零网络）
            if (isWifi(this)) {
                Thread { ImageLoader.prefetch(newImage) }.start()
            }
        }
    }

    private fun saveNextConfig() {
        Prefs.saveSplashNext(this, adImage, adTarget, countdown, skipText, oncePerDay)
    }

    private fun showAd() {
        if (adImage.isBlank()) {
            finishToMain()
            return
        }
        report("show")
        // 广告就绪：隐藏品牌加载层，显示角标/品牌区/跳过/CTA
        loading?.visibility = View.GONE
        btnSkip?.visibility = View.VISIBLE
        tvBadge?.visibility = View.VISIBLE
        brandZone?.visibility = View.VISIBLE
        btnCta?.visibility = if (adTarget.isNotBlank()) View.VISIBLE else View.GONE

        // 按屏幕尺寸采样解码（inSampleSize），显著降低内存与解码耗时
        val dm = resources.displayMetrics
        ImageLoader.load(adImage, ivAd!!, "splash_$adImage", dm.widthPixels, dm.heightPixels, onError = {
            // 图片加载失败（网络/磁盘均不可用）不阻塞：直接进首页
            enterMain()
        })
        startCountdown()

        // 绝对兜底：无论倒计时/图片加载是否正常，最迟 duration+5 秒强制进首页
        forceEnterRunnable?.let { handler.removeCallbacks(it) }
        val guard = Runnable { enterMain() }
        forceEnterRunnable = guard
        handler.postDelayed(guard, (countdown + 5) * 1000L)
    }

    private fun startCountdown() {
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, 1000)
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (finished) return
            countdown--
            if (countdown <= 0) {
                enterMain()
                return
            }
            btnSkip?.text = "$countdown $skipText"
            handler.postDelayed(this, 1000)
        }
    }

    private fun report(event: String) {
        try {
            ApiClient.get("ad_log", mapOf("event" to event)) { _, _ -> }
        } catch (_: Exception) {
        }
    }

    /** 广告流程正常结束：进入首页（直接执行跳转，避免二次 finished 检查拦截 startActivity） */
    private fun enterMain() {
        if (finished) return
        finished = true
        handler.removeCallbacks(tickRunnable)
        forceEnterRunnable?.let { handler.removeCallbacks(it) }
        gotoMain()
    }

    private fun finishToMain() {
        if (finished) return
        finished = true
        handler.removeCallbacks(tickRunnable)
        forceEnterRunnable?.let { handler.removeCallbacks(it) }
        gotoMain()
    }

    private fun gotoMain() {
        try {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        } catch (_: Exception) {
        }
        finish()
    }

    /** WiFi 判断：预下载只在 WiFi 下进行，避免消耗用户流量 */
    private fun isWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps != null) {
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
        @Suppress("DEPRECATION")
        val info = cm.activeNetworkInfo
        return info?.type == ConnectivityManager.TYPE_WIFI
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
