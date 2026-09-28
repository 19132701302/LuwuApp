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

/** 通用网页浏览页（搜索兜底等） */
class WebPageActivity : AppCompatActivity() {

    companion object {
        fun newIntent(context: Context, url: String, title: String): Intent {
            return Intent(context, WebPageActivity::class.java)
                .putExtra("url", url)
                .putExtra("title", title)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webpage)

        val url = intent.getStringExtra("url") ?: run { finish(); return }
        findViewById<TextView>(R.id.tv_title).text = intent.getStringExtra("title") ?: "浏览"
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        com.luwu.app.util.Util.applyThemeBar(
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
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url?.toString() ?: return false
                if (u.startsWith("http")) {
                    if (u.contains("65gw.com") && !u.contains("/admin")) {
                        return false
                    }
                    Util.openBrowser(this@WebPageActivity, u)
                    return true
                }
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
