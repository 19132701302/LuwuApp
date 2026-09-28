package com.luwu.app.ui

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luwu.app.R
import com.luwu.app.api.ApiClient
import com.luwu.app.api.PostItem
import com.luwu.app.util.AppState
import com.luwu.app.util.Prefs
import com.luwu.app.util.MarkdownRenderer
import com.luwu.app.util.Util
import java.io.File

class ArticleDetailActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PAY = 9101
        private const val REQ_LOGIN = 9102
        private const val REQ_SAVE_IMG = 1001
        fun newIntent(context: Context, post: PostItem): Intent {
            return Intent(context, ArticleDetailActivity::class.java)
                .putExtra("post_id", post.id)
                .putExtra("post_title", post.title)
                .putExtra("post_category", post.category)
                .putExtra("post_date", post.date)
                .putExtra("post_link", post.link)
        }
    }

    private var pendingSaveUrl: String? = null
    private var post: PostItem? = null
    private var webView: WebView? = null
    private var tvFavorite: TextView? = null
    private var tvArticleAd: TextView? = null
    private var tvLike: TextView? = null
    private var currentContent = ""
    private var likeCount = 0
    private var commentCount = 0
    private var adClosed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        webView = findViewById(R.id.webview)
        tvFavorite = findViewById(R.id.btn_favorite)
        tvArticleAd = findViewById(R.id.tv_article_ad)
        tvLike = findViewById(R.id.btn_like)

        val id = intent.getIntExtra("post_id", 0)
        val title = intent.getStringExtra("post_title") ?: ""
        val category = intent.getStringExtra("post_category") ?: ""
        val date = intent.getStringExtra("post_date") ?: ""
        val link = intent.getStringExtra("post_link") ?: ""
        post = PostItem(id, title, date, "", category, link)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_more).setOnClickListener { showMoreMenu(link) }
        findViewById<View>(R.id.btn_favorite).setOnClickListener { toggleFavorite() }
        findViewById<View>(R.id.btn_like).setOnClickListener { toggleLike() }
        findViewById<View>(R.id.btn_comment_input).setOnClickListener { openComments() }
        findViewById<TextView>(R.id.btn_share).apply {
            val shareIcon = androidx.core.graphics.drawable.DrawableCompat.wrap(
                ContextCompat.getDrawable(this@ArticleDetailActivity, R.drawable.ic_share)!!
            ).mutate()
            androidx.core.graphics.drawable.DrawableCompat.setTint(
                shareIcon, ContextCompat.getColor(this@ArticleDetailActivity, R.color.ink_2)
            )
            setCompoundDrawablesWithIntrinsicBounds(null, shareIcon, null, null)
            setOnClickListener { share() }
        }
        Util.applyThemeBar(
            findViewById(R.id.top_bar),
            findViewById(R.id.tv_title),
            findViewById(R.id.btn_back),
        )

        setupWebView()
        renderLoading(title)
        loadDetail(id)
        refreshFavoriteState()
        setupAds()
    }

    private fun setupWebView() {
        val wv = webView ?: return
        wv.settings.textZoom = Prefs.getFontZoom(this)
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        wv.setBackgroundColor(if (com.luwu.app.util.ThemeManager.isDarkNow(this)) 0xFF12151A.toInt() else 0xFFFFFFFF.toInt())
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http")) {
                    // 正文内的链接一律交给系统浏览器打开，App 内保持原生排版，不内嵌源站页面
                    Util.openBrowser(this@ArticleDetailActivity, url)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 页面渲染完成后：隐藏加载进度条、显示返回顶部按钮
                findViewById<View>(R.id.loading_progress)?.visibility = View.GONE
                // 注入滚动监听：实时回传阅读进度百分比（防抖 150ms）
                webView?.evaluateJavascript(
                    "(function(){var t=null;function u(){var d=document.documentElement||document.body;var m=d.scrollHeight-window.innerHeight;var p=m>0?Math.round(window.scrollY/m*100):0;try{luwuApp.luwuProgress(p);}catch(e){}}window.onscroll=function(){if(t){clearTimeout(t)}t=setTimeout(u,150);};u();})()",
                    null
                )
                // 注入图片交互：点击进入多图预览（收集正文图 + 当前位置）、长按保存到相册（600ms）
                webView?.evaluateJavascript(
                    "(function(){function isImg(el){return el&&el.tagName==='IMG'&&el.src;}function onTap(e){var el=e.target;if(isImg(el)){e.preventDefault();try{var urls=[],idx=0,imgs=document.getElementsByTagName('IMG'),i,im;for(i=0;i<imgs.length;i++){im=imgs[i];if(!im.src)continue;var c=im.className||'';if(c.indexOf('avatar')>=0||c.indexOf('rel-thumb')>=0)continue;urls.push(im.src);if(im===el){idx=urls.length-1;}}luwuApp.previewImages(JSON.stringify(urls),idx);}catch(err){}}}function onLong(e){var el=e.target;if(isImg(el)){e.preventDefault();try{luwuApp.saveImage(el.src);}catch(err){}}}var t=null,img=null;document.addEventListener('click',onTap,true);document.addEventListener('touchstart',function(e){var el=e.target;if(isImg(el)){img=el.src;t=setTimeout(function(){try{luwuApp.saveImage(img);}catch(err){}t=null;img=null;},600);}},true);document.addEventListener('touchend',function(){if(t){clearTimeout(t);t=null;}img=null;},true);document.addEventListener('touchmove',function(){if(t){clearTimeout(t);t=null;}img=null;},true);document.addEventListener('contextmenu',onLong,true);})()",
                    null
                )
                // 页面渲染完成后重新注入支付卡脚本：保证 .paid-card 事件绑定不因注入过早而失效
                val id = post?.id ?: 0
                if (id > 0) injectPaidScript(id)
            }
        }
        // 正文内评论区"查看全部"跳转
        wv.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun openComments() {
                runOnUiThread { this@ArticleDetailActivity.openComments() }
            }

            @android.webkit.JavascriptInterface
            fun openUser(uid: Int) {
                runOnUiThread {
                    if (uid > 0) startActivity(UserProfileActivity.newIntent(this@ArticleDetailActivity, uid))
                }
            }

            @android.webkit.JavascriptInterface
            fun openPost(cid: Int, title: String) {
                runOnUiThread {
                    if (cid > 0) {
                        startActivity(
                            ArticleDetailActivity.newIntent(
                                this@ArticleDetailActivity,
                                com.luwu.app.api.PostItem(cid, title, "", "", "", "")
                            )
                        )
                    }
                }
            }

            @android.webkit.JavascriptInterface
            fun toggleFollow(uid: Int) {
                if (uid <= 0) return
                runOnUiThread { this@ArticleDetailActivity.toggleFollow(uid) }
            }

            @android.webkit.JavascriptInterface
            fun unlock(cid: Int, code: String) {
                runOnUiThread { this@ArticleDetailActivity.doUnlock(cid, code) }
            }

            @android.webkit.JavascriptInterface
            fun pay(cid: Int, method: String) {
                runOnUiThread { this@ArticleDetailActivity.doPay(cid, method) }
            }

            @android.webkit.JavascriptInterface
            fun luwuProgress(pct: Int) {
                runOnUiThread {
                }
            }

            @android.webkit.JavascriptInterface
            fun previewImages(urlsJson: String, index: Int) {
                runOnUiThread {
                    val list = mutableListOf<String>()
                    try {
                        val arr = org.json.JSONArray(urlsJson)
                        for (i in 0 until arr.length()) {
                            arr.optString(i).takeIf { it.isNotBlank() }?.let { list.add(it) }
                        }
                    } catch (e: Exception) {
                    }
                    if (list.isNotEmpty()) {
                        this@ArticleDetailActivity.previewImages(list, index.coerceIn(0, list.size - 1))
                    }
                }
            }

            @android.webkit.JavascriptInterface
            fun saveImage(url: String) {
                runOnUiThread {
                    if (url.isNotBlank()) this@ArticleDetailActivity.saveImage(url)
                }
            }
        }, "luwuApp")
    }

    /** 支付方式 → 调网站易支付收银台创建订单 → 打开支付页 → 轮询 → 解锁（订单同步网站后台） */
    private fun doPay(cid: Int, method: String) {
        if (cid <= 0) {
            webView?.evaluateJavascript("window.luwuPaidErr('$cid','参数错误')", null)
            return
        }
        // 未登录 → 跳转登录页，登录完成后返回当前文章页（不自动继续，由用户再次点击支付）
        if (Prefs.getUid(this) <= 0) {
            Util.toast(this, "请先登录后再支付")
            try {
                startActivityForResult(Intent(this, LoginActivity::class.java), REQ_LOGIN)
            } catch (e: Exception) {
                Util.toast(this, "登录页打开失败")
            }
            return
        }
        val m = when (method) {
            "alipay" -> "alipay"
            "qqpay" -> "qqpay"
            else -> "wxpay"
        }
        webView?.evaluateJavascript(
            "window.luwuPaidLoading('$cid','正在创建订单…')", null
        )
        // 复用网站 Joe 收银台创建订单：写入 typecho_orders（后台订单管理可见）
        ApiClient.postRaw(
            "https://www.65gw.com/joe/api/initiate_pay",
            mapOf(
                "cid" to cid.toString(),
                "payment_method" to m,
                "return_url" to "https://www.65gw.com/archives/$cid.html",
            )
        ) { json, err ->
            runOnUiThread {
                if (json == null) {
                    webView?.evaluateJavascript("window.luwuPaidErr('$cid','${err ?: "网络异常"}')", null)
                    return@runOnUiThread
                }
                val code = json.optInt("code", -1)
                if (code != 1) {
                    val msg = json.optString("msg", json.optString("message", "支付通道暂不可用，请联系站长"))
                    webView?.evaluateJavascript("window.luwuPaidErr('$cid','${msg.replace("'", "\\'")}')", null)
                    return@runOnUiThread
                }
                val tradeNo = json.optString("trade_no", "")
                if (tradeNo.isEmpty()) {
                    webView?.evaluateJavascript("window.luwuPaidErr('$cid','订单创建失败')", null)
                    return@runOnUiThread
                }
                // 支付页 URL：优先 payurl 跳转，否则二维码页
                var payUrl = json.optString("url", "")
                if (payUrl.isEmpty()) {
                    val qr = json.optString("qrcode", "")
                    if (qr.isNotEmpty()) payUrl = json.optString("url_qrcode", "")
                }
                if (payUrl.isEmpty()) {
                    webView?.evaluateJavascript("window.luwuPaidErr('$cid','未获取到支付页面')", null)
                    return@runOnUiThread
                }
                openPayPage(payUrl, cid, tradeNo)
            }
        }
    }

    /** 打开支付 WebView 页面；返回后轮询订单状态直至支付完成 */
    private fun openPayPage(payUrl: String, cid: Int, tradeNo: String) {
        try {
            val intent = Intent(this, PayWebViewActivity::class.java)
            intent.putExtra("pay_url", payUrl)
            payPendingTrade = tradeNo
            payPendingCid = cid
            startActivityForResult(intent, REQ_PAY)
        } catch (e: Exception) {
            webView?.evaluateJavascript("window.luwuPaidErr('$cid','打开支付页面失败')", null)
        }
    }

    private var payPendingTrade = ""
    private var payPendingCid = 0

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PAY && payPendingTrade.isNotEmpty()) {
            pollPayStatus(payPendingCid, payPendingTrade, 0)
            payPendingTrade = ""
            payPendingCid = 0
        }
    }

    /** 轮询网站订单状态：已支付则解锁并注入下载内容 */
    private fun pollPayStatus(cid: Int, tradeNo: String, attempt: Int) {
        if (attempt > 30) { // 最多轮询约 60s
            webView?.evaluateJavascript("window.luwuPaidErr('$cid','支付超时，请确认支付结果后重新进入')", null)
            return
        }
        ApiClient.get("pay_status", mapOf("trade_no" to tradeNo)) { json, err ->
            runOnUiThread {
                val paid = json?.optBoolean("paid", false) == true
                if (paid) {
                    unlockByTrade(cid, tradeNo)
                } else {
                    Handler(mainLooper).postDelayed({
                        pollPayStatus(cid, tradeNo, attempt + 1)
                    }, 2000)
                }
            }
        }
    }

    /** 订单已支付 → 取付费内容 → 渲染下载 + 本地缓存 */
    private fun unlockByTrade(cid: Int, tradeNo: String) {
        ApiClient.get("pay_unlock", mapOf("cid" to cid.toString(), "trade_no" to tradeNo, "token" to Prefs.getToken(this))) { json, err ->
            runOnUiThread {
                if (json?.optBoolean("ok", false) == true) {
                    val content = json.optString("content", "")
                    if (content.isNotBlank()) {
                        Prefs.setPaidUnlocked(this, cid, content)
                        val html = MarkdownRenderer.render(content)
                            .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        webView?.evaluateJavascript(
                            "window.luwuPaidOk('$cid',\"$html\")", null
                        )
                    } else {
                        webView?.evaluateJavascript("window.luwuPaidErr('$cid','支付成功但内容为空')", null)
                    }
                } else {
                    val msg = json?.optString("error", "订单校验失败") ?: "订单校验失败"
                    webView?.evaluateJavascript("window.luwuPaidErr('$cid','$msg')", null)
                }
            }
        }
    }

    /** 付费解锁：验证解锁码 → 注入下载区块 → 本地缓存 */
    private fun doUnlock(cid: Int, code: String) {
        if (cid <= 0 || code.isBlank()) {
            webView?.evaluateJavascript("window.luwuPaidErr('$cid','请输入解锁码')", null)
            return
        }
        ApiClient.post("unlock", mapOf("cid" to cid.toString(), "code" to code)) { json, err ->
            runOnUiThread {
                if (json?.optBoolean("ok", false) == true) {
                    val content = json.optString("content", "")
                    if (content.isNotBlank()) {
                        Prefs.setPaidUnlocked(this, cid, content)
                        val html = MarkdownRenderer.render(content)
                            .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        webView?.evaluateJavascript(
                            "window.luwuPaidOk('$cid',\"$html\")", null
                        )
                    } else {
                        webView?.evaluateJavascript("window.luwuPaidErr('$cid','解锁成功但内容为空')", null)
                    }
                } else {
                    val msg = json?.optString("error", err ?: "解锁失败") ?: "解锁失败"
                    webView?.evaluateJavascript("window.luwuPaidErr('$cid','$msg')", null)
                }
            }
        }
    }

    /** 注入付费卡支付脚本：支付方式选择 + 立即支付按钮 + 已解锁缓存自动展开 */
    private fun injectPaidScript(cid: Int) {
        val cached = Prefs.getPaidUnlocked(this, cid)
        val cachedHtml = if (cached != null) {
            MarkdownRenderer.render(cached).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        } else ""
        webView?.evaluateJavascript(
            """
            window.luwuPaidOk = function(cid, html) {
              var cards = document.querySelectorAll('.paid-card');
              for (var i=0;i<cards.length;i++){ var c=cards[i];
                if (c.getAttribute('data-cid')==cid){
                  var card=c;
                  card.querySelector('.paid-methods').style.display='none';
                  card.querySelector('.paid-tip').style.display='none';
                  var btn=card.querySelector('.paid-btn');
                  btn.textContent='✅ 已解锁，查看下载地址'; btn.className='paid-btn paid-done';
                  var r=card.querySelector('.paid-result'); r.innerHTML='<div class="dlcard-wrap">'+html+'</div>';
                }
              }
            };
            window.luwuPaidErr = function(cid, msg) {
              var cards = document.querySelectorAll('.paid-card');
              for (var i=0;i<cards.length;i++){ var c=cards[i];
                if (c.getAttribute('data-cid')==cid){
                  var r=c.querySelector('.paid-result');
                  r.innerHTML='<div class="paid-err">'+msg+'</div>';
                  var btn=c.querySelector('.paid-btn');
                  btn.textContent='重新支付'; btn.className='paid-btn';
                }
              }
            };
            window.luwuPaidLoading = function(cid, msg) {
              var cards = document.querySelectorAll('.paid-card');
              for (var i=0;i<cards.length;i++){ var c=cards[i];
                if (c.getAttribute('data-cid')==cid){
                  var btn=c.querySelector('.paid-btn');
                  btn.textContent=msg || '支付处理中…'; btn.className='paid-btn paid-loading';
                }
              }
            };
            (function(){
              var bindTimer = 0;
              function tryBind(){
                var cards = document.querySelectorAll('.paid-card');
                if (!cards.length) {
                  // HTML 尚未渲染完成：重试等待（最多 10s），解决点击支付/选方式无响应
                  if (bindTimer++ < 50) setTimeout(tryBind, 200);
                  return;
                }
                cards.forEach(function(card){
                  if (card.getAttribute('data-bound')) return;
                  card.setAttribute('data-bound','1');
                  var cid = card.getAttribute('data-cid');
                  var pm = null;
                  card.querySelectorAll('.pm').forEach(function(b){
                    b.addEventListener('click', function(){
                      card.querySelectorAll('.pm').forEach(function(x){ x.classList.remove('active'); });
                      b.classList.add('active');
                      pm = b.getAttribute('data-m');
                    });
                  });
                  var btn = card.querySelector('.paid-btn');
                  btn.addEventListener('click', function(){
                    if (btn.className.indexOf('paid-done')>=0) return;
                    var method = pm || 'wxpay';
                    var tip = card.querySelector('.paid-tip');
                    if (tip) tip.textContent='正在创建订单…请稍候';
                    luwuApp.pay(parseInt(cid), method);
                  });
                });
                var cachedHtml = "$cachedHtml";
                if (cachedHtml) { window.luwuPaidOk('$cid', cachedHtml); }
              }
              tryBind();
            })();
            """, null
        )
    }

    /** 相关推荐模板：正文后、评论区前展示同分类/同作者最新文章（点击经 openPost 桥打开） */
    private fun relatedTemplate(related: org.json.JSONArray?, isDark: Boolean): String {
        if (related == null || related.length() == 0) return ""
        val sb = StringBuilder()
        sb.append("<style>")
        sb.append(".rel-wrap{margin:18px 16px 0;padding:14px 14px 4px;border-top:1px solid ${if (isDark) "#2A3138" else "#EEF0F3"};}")
        sb.append(".rel-title{font-size:15px;font-weight:700;color:${if (isDark) "#F1F3F5" else "#111"};margin-bottom:10px;}")
        sb.append(".rel-item{display:flex;gap:10px;padding:9px 0;align-items:center;border-bottom:1px solid ${if (isDark) "#232A31" else "#F1F5F5"};}")
        sb.append(".rel-thumb{width:64px;height:48px;border-radius:8px;object-fit:cover;background:#E5E7EB;flex-shrink:0;}")
        sb.append(".rel-body{flex:1;min-width:0;}")
        sb.append(".rel-t{font-size:14px;color:${if (isDark) "#E6E8EB" else "#222"};line-height:1.4;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;}")
        sb.append(".rel-m{font-size:11px;color:#9AA0A8;margin-top:3px;}")
        sb.append(".rel-stat{color:#0F766E;}")
        sb.append("</style>")
        sb.append("<div class=\"rel-wrap\"><div class=\"rel-title\">相关推荐</div>")
        for (i in 0 until related.length()) {
            val r = related.optJSONObject(i) ?: continue
            val rid = r.optInt("id", 0)
            if (rid <= 0) continue
            val rtitle = r.optString("title", "")
            val rdate = r.optString("date", "")
            val rlikes = r.optInt("likes", 0)
            val rc = r.optInt("commentsNum", 0)
            val rthumb = r.optString("thumb", "")
            val stats = buildString {
                if (rlikes > 0) append("赞 $rlikes")
                if (rc > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("评论 $rc")
                }
            }
            val thumbHtml = if (rthumb.isNotBlank()) "<img class=\"rel-thumb\" src=\"$rthumb\">" else ""
            val statHtml = if (stats.isNotEmpty()) "<span class=\"rel-stat\">$stats</span>" else ""
            sb.append("<div class=\"rel-item\" onclick=\"luwuApp.openPost($rid,'')\">")
            sb.append(thumbHtml)
            sb.append("<div class=\"rel-body\"><div class=\"rel-t\">$rtitle</div>")
            sb.append("<div class=\"rel-m\">$rdate$statHtml</div></div></div>")
        }
        sb.append("</div>")
        return sb.toString()
    }

    /** 评论区模板：追加到正文 HTML 末尾，由 fillComments 填充 */
    private fun commentsTemplate(isDark: Boolean = false): String {
        return """
            <div id="luwu_cmt" style="margin:24px -16px 0;padding:16px 16px 8px;background:${if (isDark) "#1A1F24" else "#FFFFFF"};border-top:8px solid ${if (isDark) "#2A3138" else "#F4F6F8"}">
              <div style="font-size:16px;font-weight:700;color:${if (isDark) "#F1F3F5" else "#111"};margin-bottom:4px">评论 <span id="luwu_cmt_count" style="color:#0F766E;font-weight:700">0</span></div>
              <div id="luwu_cmt_list"></div>
              <div onclick="luwuApp.openComments()" style="margin:14px 0;padding:11px 0;text-align:center;color:#0F766E;font-size:14px;font-weight:600;background:${if (isDark) "#123F3B" else "#F4FAF9"};border-radius:10px">查看全部评论 →</div>
            </div>
            <script>
            window.luwuFillComments = function(items, total) {
              var n = document.getElementById('luwu_cmt_count'); if (n) n.textContent = total;
              var l = document.getElementById('luwu_cmt_list'); if (!l) return;
              var h = '';
              for (var i = 0; i < items.length; i++) {
                var it = items[i] || {};
                var a = it.author || '游客', t = it.time || '', x = it.text || '';
                h += '<div style="display:flex;gap:10px;padding:12px 0;border-bottom:1px solid ${if (isDark) "#2A3138" else "#F1F5F5"}">'
                   + '<div style="flex:none;width:30px;height:30px;border-radius:50%;background:#E4F2F0;color:#0F766E;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:700">' + a.charAt(0) + '</div>'
                   + '<div style="flex:1;min-width:0"><div style="font-size:13px;font-weight:600;color:${if (isDark) "#E5E7EB" else "#334155"}">' + a + '<span style="color:#94A3B8;font-weight:400;font-size:11px;margin-left:8px">' + t + '</span></div>'
                   + '<div style="font-size:13px;color:${if (isDark) "#AAB2BC" else "#475569"};margin-top:3px;line-height:1.6">' + x + '</div></div></div>';
              }
              l.innerHTML = h || '<div style="color:#94A3B8;font-size:13px;padding:10px 0">暂无评论，来抢沙发</div>';
            };
            window.luwuPendingComments = null;
            function luwuConsumeComments() {
              if (window.luwuPendingComments) {
                window.luwuFillComments(window.luwuPendingComments[0], window.luwuPendingComments[1]);
                window.luwuPendingComments = null;
              }
            }
            document.addEventListener('DOMContentLoaded', luwuConsumeComments);
            </script>
        """.trimIndent()
    }

    /** 填充评论区（最多 3 条 + 数量） */
    private fun fillCommentsScript(items: List<CommentsActivity.CommentItem>, total: Int): String {
        val sb = StringBuilder("window.luwuPendingComments=[JSON.parse('")
        val arr = org.json.JSONArray()
        for (c in items.take(3)) {
            val o = org.json.JSONObject()
            o.put("author", c.author)
            o.put("time", c.date)
            val text = c.content.replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "[图片]").replace("<", "&lt;")
            o.put("text", text)
            arr.put(o)
        }
        sb.append(arr.toString().replace("\\", "\\\\").replace("'", "\\'"))
            .append("'),").append(total)
            .append("];(function(){function f(){if(window.luwuPendingComments){window.luwuFillComments(window.luwuPendingComments[0],window.luwuPendingComments[1]);window.luwuPendingComments=null;}}if(document.readyState!=='loading'){f();}else{document.addEventListener('DOMContentLoaded',f);}})();")
        return sb.toString()
    }

    private fun renderLoading(title: String) {
        val dark = com.luwu.app.util.ThemeManager.isDarkNow(this)
        val bg = if (dark) "#12151A" else "#FFFFFF"
        val fg = if (dark) "#D5DBE1" else "#262626"
        val html = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font-family:sans-serif;margin:0;padding:16px;color:$fg;line-height:1.75;font-size:16px;background:$bg}h1{font-size:20px;line-height:1.45;margin:0 0 6px}.loading{color:#999;font-size:14px;text-align:center;margin-top:80px}</style></head><body><h1>$title</h1><div class='loading'>加载中…</div></body></html>"
        webView?.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    private fun loadDetail(id: Int) {
        // 插件不可用 → 显示原生错误页（不跳源站网页）
        if (AppState.pluginAvailable == false) {
            renderError("接口暂不可用，请稍后重试，或点击右上角『原文』访问网页版")
            return
        }
        // 顶部加载进度条：数据请求期间显示
        findViewById<View>(R.id.loading_progress)?.visibility = View.VISIBLE
        ApiClient.get("article", mapOf("id" to id.toString())) { json, err ->
            findViewById<View>(R.id.loading_progress)?.visibility = View.GONE
            if (json == null || !json.optBoolean("ok", false)) {
                // 网络失败 → 7 天离线缓存兜底（弱网/断网可读已缓存文章）
                val cached = com.luwu.app.util.CacheManager.getDetail(this, "article_$id")
                if (cached != null && cached.optBoolean("ok", false)) {
                    renderDetail(cached, id, offline = true)
                    return@get
                }
                // 接口不可用 → 判定插件未装，显示原生错误页
                AppState.pluginAvailable = false
                AppState.ads = AppState.defaultAds()
                setupAds()
                renderError(err ?: "加载失败")
                return@get
            }
            AppState.pluginAvailable = true
            // 写入 7 天离线缓存
            com.luwu.app.util.CacheManager.putJson(this, "article_$id", json)
            renderDetail(json, id, offline = false)
        }
    }

    /** 渲染文章详情（网络与离线缓存共用；offline 时不请求评论） */
    private fun renderDetail(json: org.json.JSONObject, id: Int, offline: Boolean) {
        run {
            val item = json.optJSONObject("item") ?: run { renderError("文章不存在"); return }
            val title = item.optString("title", "")
            val category = item.optString("category", "")
            val author = item.optString("author", "")
            val authorId = item.optInt("authorId", 0)
            val authorAvatar = item.optString("authorAvatar", "")
            val date = item.optString("date", "")
            val content = item.optString("content", "")
            val link = item.optString("link", "")
            val thumb = item.optString("thumb", "")
            likeCount = item.optInt("likes", Prefs.getLikeCount(this, id))
            commentCount = item.optInt("commentsNum", 0)
            if (likeCount > 0) Prefs.setLikeCount(this, id, likeCount)
            currentContent = content

            val current = post
            post = PostItem(current?.id ?: id, title, date, "", category, link)
            findViewById<TextView>(R.id.tv_title).text = title

            // 作者信息行：头像 + 站点名 + 分类·时间，右侧互动数（头条风格）
            val catLabel = if (category.isBlank()) "资讯" else category
            val dateLabel = if (date.isBlank()) "未知时间" else date
            val offlineHint = if (offline) "<div style=\"padding:8px 14px;margin:10px 0;background:#FEF3C7;border-radius:8px;font-size:12px;color:#92400E;\">⚠ 离线内容（网络不可用时展示的缓存版本）</div>" else ""
            val meta = buildString {
                append(offlineHint)
                val authorName = author.ifBlank { "陆伍博客" }
                val avatarHtml = if (authorAvatar.isNotBlank()) {
                    "<img src=\"$authorAvatar\" style=\"width:100%;height:100%;border-radius:50%;object-fit:cover\">"
                } else {
                    authorName.take(1)
                }
                val click = if (authorId > 0) " onclick=\"luwuApp.openUser($authorId)\"" else ""
                // 头条式作者行：头像 + 作者名 + 分类徽标·时间（去掉关注按钮与统计，顶部保持干净）
                append("<div class=\"avatar\"$click>$avatarHtml</div>")
                append("<div class=\"who\"><b$click>$authorName</b><span><i class=\"cat-pill\">$catLabel</i> · $dateLabel</span></div>")
            }
            // 详情正文：一律用 App 原生排版渲染（无站内导航/无杂项广告），正文缺失时提示查看原文
            if (content.isNotBlank()) {
                val dark = com.luwu.app.util.ThemeManager.isDarkNow(this)
                val html = Util.buildDetailHtml(title, meta, content, thumb, dark) +
                    relatedTemplate(json.optJSONArray("related"), dark) +
                    commentsTemplate(dark)
                webView?.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                if (!offline) injectPaidScript(id)
                if (!offline) loadComments(id)
            } else {
                renderError("正文暂未同步，可点击右上角『原文』查看网页版")
            }

            // 记录浏览历史
            val p = post
            if (p != null) Prefs.addHistory(this, p)
            refreshFavoriteState()
            refreshLikeState()
        }
    }

    /** 加载关注状态（作者行按钮初始样式） */
    private fun loadFollowState(authorId: Int) {
        if (Prefs.getUid(this) <= 0) return
        ApiClient.post("follow", mapOf(
            "target" to authorId.toString(),
            "act" to "check",
        )) { json, _ ->
            val on = json?.optBoolean("following", false) ?: false
            webView?.evaluateJavascript("window.luwuSetFollow($authorId, $on)", null)
        }
    }

    /** 关注 / 取消关注（作者行按钮） */
    private fun toggleFollow(authorId: Int) {
        if (Prefs.getUid(this) <= 0) {
            Util.toast(this, "请先登录")
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        ApiClient.post("follow", mapOf(
            "target" to authorId.toString(),
            "act" to "check",
        )) { json, _ ->
            val following = json?.optBoolean("following", false) ?: false
            ApiClient.post("follow", mapOf(
                "target" to authorId.toString(),
                "act" to if (following) "remove" else "add",
            )) { json2, _ ->
                val now = json2?.optBoolean("following", false) ?: false
                webView?.evaluateJavascript("window.luwuSetFollow($authorId, $now)", null)
                Util.toast(this, if (now) "已关注作者" else "已取消关注")
            }
        }
    }

    /** 详情页正文下方评论预览（最近 3 条） */
    private fun loadComments(id: Int) {
        ApiClient.get("comments", mapOf("id" to id.toString())) { json, _ ->
            if (json == null || !json.optBoolean("ok", false)) return@get
            val total = json.optInt("total", 0)
            val items = mutableListOf<CommentsActivity.CommentItem>()
            val arr = json.optJSONArray("items") ?: return@get
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                items.add(
                    CommentsActivity.CommentItem(
                        o.optInt("coid", 0),
                        o.optString("author", "游客"),
                        o.optString("parent", ""),
                        o.optString("date", ""),
                        o.optString("content", ""),
                    )
                )
            }
            webView?.evaluateJavascript(fillCommentsScript(items, total), null)
        }
    }

    private fun renderError(msg: String) {
        findViewById<View>(R.id.loading_progress)?.visibility = View.GONE
        val html = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font-family:sans-serif;margin:0;padding:60px 24px;color:#888;text-align:center;font-size:14px;line-height:1.8}</style></head><body><div style='font-size:15px;color:#BBB'>页面出错</div><br>$msg</body></html>"
        webView?.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    /** 详情页图片点击：全屏多图预览（横向滑动切换 + 页码指示，点击关闭，长按可保存） */
    private fun previewImages(urls: List<String>, index: Int) {
        if (urls.isEmpty()) return
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ArticleDetailActivity, LinearLayoutManager.HORIZONTAL, false)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val pager = androidx.recyclerview.widget.PagerSnapHelper()
        pager.attachToRecyclerView(rv)
        val tvPage = TextView(this).apply {
            text = "${index + 1}/${urls.size}"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setPadding(dp(14), dp(5), dp(14), dp(5))
            background = android.graphics.drawable.ColorDrawable(0x66000000)
        }
        val lpPage = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            topMargin = dp(48)
        }
        root.addView(rv)
        root.addView(tvPage, lpPage)
        rv.adapter = ImagePagerAdapter(urls, { dialog.dismiss() })
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm = rv.layoutManager as? LinearLayoutManager ?: return
                    val pos = pager.findSnapView(lm)?.let { lm.getPosition(it) }
                    if (pos != null && pos >= 0 && pos < urls.size) tvPage.text = "${pos + 1}/${urls.size}"
                }
            }
        })
        rv.scrollToPosition(index)
        dialog.setContentView(root)
        dialog.show()
    }

    /** 多图预览分页适配器：每页一张正文图，点击关闭、长按保存 */
    private inner class ImagePagerAdapter(
        private val urls: List<String>,
        private val onClose: () -> Unit,
    ) : RecyclerView.Adapter<ImagePagerHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImagePagerHolder {
            val iv = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(0xFF000000.toInt())
                setOnClickListener { onClose() }
            }
            return ImagePagerHolder(iv)
        }

        override fun getItemCount(): Int = urls.size

        override fun onBindViewHolder(h: ImagePagerHolder, position: Int) {
            h.iv.setOnLongClickListener {
                saveImage(urls[position.coerceIn(0, urls.size - 1)])
                true
            }
            com.luwu.app.util.ImageLoader.load(urls[position], h.iv, "preview_$position")
        }
    }

    private class ImagePagerHolder(val iv: ImageView) : RecyclerView.ViewHolder(iv)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** 详情页图片长按：保存到相册（Android 10+ 直接 MediaStore；9 及以下先申请存储权限） */
    private fun saveImage(url: String) {        if (Build.VERSION.SDK_INT >= 29) {
            downloadAndSaveImage(url)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            downloadAndSaveImage(url)
            return
        }
        pendingSaveUrl = url
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQ_SAVE_IMG,
        )
    }

    private fun downloadAndSaveImage(url: String) {
        Thread {
            var ok = false
            var err = ""
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val req = okhttp3.Request.Builder().url(url).build()
                val bytes = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
                if (bytes == null || bytes.isEmpty()) {
                    err = "下载失败"
                } else if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "luwu_${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/陆伍博客")
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri == null) {
                        err = "保存失败"
                    } else {
                        contentResolver.openOutputStream(uri)?.use { os -> os.write(bytes) }
                        ok = true
                    }
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "陆伍博客")
                    dir.mkdirs()
                    val f = File(dir, "luwu_${System.currentTimeMillis()}.jpg")
                    f.writeBytes(bytes)
                    MediaScannerConnection.scanFile(this, arrayOf(f.absolutePath), null, null)
                    ok = true
                }
            } catch (e: Exception) {
                err = e.message ?: "保存失败"
            }
            val success = ok
            runOnUiThread {
                Util.toast(this, if (success) "图片已保存到相册" else "保存失败：$err")
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SAVE_IMG) {
            val url = pendingSaveUrl
            pendingSaveUrl = null
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && url != null) {
                downloadAndSaveImage(url)
            } else {
                Util.toast(this, "未授予存储权限，无法保存图片")
            }
        }
    }

    private fun setupAds() {
        val wrap = findViewById<View>(R.id.article_ad_wrap) ?: return
        if (adClosed) return
        val ads = AppState.ads ?: return
        val list = ads.article
        if (!ads.enabled || list.isEmpty()) return
        val ad = list[(Math.random() * list.size).toInt().coerceAtMost(list.size - 1)]
        val tv = tvArticleAd ?: return
        val iv = findViewById<View>(R.id.iv_article_ad) as? android.widget.ImageView ?: return
        // 关闭按钮：点击后本次详情不再展示广告
        findViewById<View>(R.id.btn_ad_close).setOnClickListener {
            adClosed = true
            wrap.visibility = View.GONE
        }
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
        wrap.visibility = View.VISIBLE
    }

    private fun refreshFavoriteState() {
        val p = post ?: return
        val fav = Prefs.isFavorite(this, p.id)
        val btn = tvFavorite ?: return
        btn.text = if (fav) "已收藏" else "收藏"
        val color = if (fav) ContextCompat.getColor(this, R.color.brand_blue) else ContextCompat.getColor(this, R.color.ink_2)
        btn.setTextColor(color)
        val icon = androidx.core.graphics.drawable.DrawableCompat.wrap(
            ContextCompat.getDrawable(this, if (fav) R.drawable.ic_star_filled else R.drawable.ic_star)!!
        ).mutate()
        androidx.core.graphics.drawable.DrawableCompat.setTint(icon, color)
        btn.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null)
    }

    private fun refreshLikeState() {
        val btn = tvLike ?: return
        val liked = post?.let { Prefs.isLiked(this, it.id) } ?: false
        val color = if (liked) ContextCompat.getColor(this, R.color.brand_blue) else ContextCompat.getColor(this, R.color.ink_2)
        btn.setTextColor(color)
        val icon = androidx.core.graphics.drawable.DrawableCompat.wrap(
            ContextCompat.getDrawable(this, if (liked) R.drawable.ic_like_filled else R.drawable.ic_like)!!
        ).mutate()
        androidx.core.graphics.drawable.DrawableCompat.setTint(icon, color)
        btn.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null)
        btn.text = if (likeCount > 0) "赞 $likeCount" else "点赞"
    }

    private fun toggleLike() {
        val p = post ?: return
        val liked = Prefs.isLiked(this, p.id)
        val action = if (liked) "cancel" else "like"
        ApiClient.get("like", mapOf("id" to p.id.toString(), "action" to action)) { json, err ->
            if (json == null || !json.optBoolean("ok", false)) {
                Util.toast(this, err ?: (if (liked) "取消点赞失败" else "点赞失败，请稍后再试"))
                return@get
            }
            likeCount = json.optInt("likes", likeCount + if (liked) -1 else 1)
            Prefs.setLikeCount(this, p.id, likeCount)
            if (liked) {
                Prefs.unmarkLiked(this, p.id)
                Util.toast(this, "已取消点赞")
            } else {
                Prefs.markLiked(this, p.id)
                Util.toast(this, "感谢点赞")
            }
            refreshLikeState()
        }
    }

    private fun openComments() {
        val p = post ?: return
        startActivity(CommentsActivity.newIntent(this, p.id, p.title))
    }

    override fun onResume() {
        super.onResume()
        refreshFavoriteState()
        refreshLikeState()
        // 返回时刷新评论数
        val id = post?.id ?: return
        ApiClient.get("comments", mapOf("id" to id.toString())) { json, _ ->
            if (json != null && json.optBoolean("ok", false)) {
                commentCount = json.optInt("total", commentCount)
            }
        }
    }

    private fun toggleFavorite() {
        val p = post ?: return
        if (Prefs.isFavorite(this, p.id)) {
            Prefs.removeFavorite(this, p.id)
            Util.toast(this, getString(R.string.favorite_removed))
        } else {
            Prefs.addFavorite(this, p)
            Util.toast(this, getString(R.string.favorite_added))
        }
        refreshFavoriteState()
    }

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }

    /** 更多菜单：字号 / 分享 / 阅读原文 / 复制链接 / 举报文章（顶部右侧单按钮合并） */
    private fun showMoreMenu(link: String) {
        val items = arrayOf("字号调整", "分享文章", "阅读原文", "复制链接", "举报文章")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("更多操作")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> cycleFontSize()
                    1 -> share()
                    2 -> Util.openBrowser(this, link)
                    3 -> {
                        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("link", link))
                        Util.toast(this, "链接已复制")
                    }
                    4 -> reportPost()
                }
            }
            .show()
    }

    /** 举报文章（后端 api_report，匿名可用，IP 频控防刷） */
    private fun reportPost() {
        val input = android.widget.EditText(this)
        input.hint = "请填写举报理由（200字内）"
        input.minLines = 2
        input.setTextColor(resources.getColor(R.color.ink, null))
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("举报该文章")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("提交举报") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isBlank()) {
                    Util.toast(this, "请填写举报理由")
                    return@setPositiveButton
                }
                ApiClient.get("report", mapOf(
                    "type" to "post",
                    "target_id" to (post?.id ?: 0).toString(),
                    "reason" to reason,
                )) { json, err ->
                    if (json != null && json.optBoolean("ok", false)) {
                        Util.toast(this, json.optString("message", "举报已提交"))
                    } else {
                        Util.toast(this, json?.optString("error", "") ?: err ?: "举报提交失败")
                    }
                }
            }
            .show()
    }

    /** 详情页字号调节：100% → 120% → 140% 循环，记忆到本地 */
    private fun cycleFontSize() {
        val cur = Prefs.getFontZoom(this)
        val next = if (cur >= 140) 100 else cur + 20
        Prefs.setFontZoom(this, next)
        webView?.settings?.textZoom = next
        Toast.makeText(this, "字号 ${next}%", Toast.LENGTH_SHORT).show()
    }

    private fun share() {
        val p = post ?: return
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${p.title} ${p.link}")
            }
            startActivity(Intent.createChooser(intent, "分享到"))
        } catch (e: Exception) {
            Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show()
        }
    }
}
