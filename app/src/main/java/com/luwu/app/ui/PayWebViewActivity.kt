package com.luwu.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.luwu.app.R
import com.luwu.app.util.Util

/**
 * 安全支付页：内嵌加载易支付收银台/二维码页面。
 * 支付完成后易支付回跳 callback.php（redirect_url），检测到即自动关闭返回文章页。
 */
class PayWebViewActivity : AppCompatActivity() {

    companion object {
        private val DONE_MARKERS = arrayOf("callback.php", "redirect_url", "pay_result", "payresult", "notify")

        fun newIntent(context: Context, url: String): Intent {
            return Intent(context, PayWebViewActivity::class.java)
                .putExtra("pay_url", url)
        }

        fun isPayDoneUrl(url: String): Boolean {
            for (m in DONE_MARKERS) {
                if (url.contains(m)) return true
            }
            return false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webpage)

        val url = intent.getStringExtra("pay_url") ?: run { finish(); return }
        findViewById<TextView>(R.id.tv_title).text = "安全支付"
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        Util.applyThemeBar(
            findViewById(R.id.top_bar),
            findViewById(R.id.tv_title),
            findViewById(R.id.btn_back),
        )

        val wv = findViewById<WebView>(R.id.webview)
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.allowFileAccess = false
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url?.toString() ?: return false
                if (!u.startsWith("http")) return false
                // 支付完成回跳 → 自动关闭（订单状态由文章页轮询确认）
                if (isPayDoneUrl(u) && (u.contains("65gw.com") || u.contains("joe"))) {
                    finish()
                    return true
                }
                // 易支付内部页面（微信/支付宝收银台、跳转）一律在 App 内加载
                return false
            }
        }
        wv.loadUrl(url)
    }

    override fun onBackPressed() {
        val wv = findViewById<WebView>(R.id.webview)
        if (wv.canGoBack()) {
            wv.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        findViewById<WebView>(R.id.webview).destroy()
        super.onDestroy()
    }
}
