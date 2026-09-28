package com.luwu.app.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.luwu.app.util.Prefs
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 陆伍 App 数据客户端
 * 对接网站 Typecho 插件 LuwuApp 的 JSON 接口 /action/luwu-app
 */
object ApiClient {

    const val BASE = "https://www.65gw.com/action/luwu-app"

    private lateinit var appContext: Context

    /** 在 Application.onCreate 中调用，初始化全局上下文 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 已登录时自动附带 token（登录态由 token 维持，不再传输明文密码） */
    private fun authParams(): Map<String, String> {
        return if (::appContext.isInitialized && Prefs.isLoggedIn(appContext)) {
            mapOf("token" to Prefs.getToken(appContext))
        } else {
            emptyMap()
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val main = Handler(Looper.getMainLooper())

    /** 组装 URL，参数按 UTF-8 编码 */
    private fun buildUrl(api: String, params: Map<String, String>): String {
        val sb = StringBuilder(BASE).append("?api=").append(api)
        for ((k, v) in params) {
            sb.append('&').append(k).append('=').append(URLEncoder.encode(v, "UTF-8"))
        }
        return sb.toString()
    }

    /** GET 请求：json 为 null 时 err 有值（404 视为插件未装） */
    fun get(api: String, params: Map<String, String> = emptyMap(), onResult: (JSONObject?, String?) -> Unit) {
        Thread {
            try {
                val req = Request.Builder()
                    .url(buildUrl(api, params + authParams()))
                    .header("User-Agent", "LuWuApp/3.1 Android")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    if (!resp.isSuccessful || body == null) {
                        postMain { onResult(null, describeHttp(resp.code)) }
                        return@use
                    }
                    val json = JSONObject(body)
                    postMain { onResult(json, null) }
                }
            } catch (e: Exception) {
                postMain { onResult(null, describeError(e)) }
            }
        }.start()
    }

    /** POST 表单请求 */
    fun post(api: String, form: Map<String, String>, onResult: (JSONObject?, String?) -> Unit) {
        Thread {
            try {
                val fb = FormBody.Builder()
                for ((k, v) in (form + authParams())) fb.add(k, v)
                val req = Request.Builder()
                    .url(buildUrl(api, emptyMap()))
                    .header("User-Agent", "LuWuApp/3.1 Android")
                    .post(fb.build())
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    if (!resp.isSuccessful || body == null) {
                        postMain { onResult(null, describeHttp(resp.code)) }
                        return@use
                    }
                    val json = JSONObject(body)
                    postMain { onResult(json, null) }
                }
            } catch (e: Exception) {
                postMain { onResult(null, describeError(e)) }
            }
        }.start()
    }

    /** POST 到任意完整 URL（不拼接 BASE；用于网站 Joe 收银台支付接口） */
    fun postRaw(url: String, form: Map<String, String>, onResult: (JSONObject?, String?) -> Unit) {
        Thread {
            try {
                val fb = FormBody.Builder()
                for ((k, v) in form) fb.add(k, v)
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LuWuApp/3.1 Android")
                    .header("Referer", "https://www.65gw.com/")
                    .post(fb.build())
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    if (!resp.isSuccessful || body == null) {
                        postMain { onResult(null, describeHttp(resp.code)) }
                        return@use
                    }
                    val json = JSONObject(body)
                    postMain { onResult(json, null) }
                }
            } catch (e: Exception) {
                postMain { onResult(null, describeError(e)) }
            }
        }.start()
    }

    /** 上传图片（头像 / 评论配图）：multipart 字段 file + 附加表单 */
    fun upload(api: String, filePath: String, form: Map<String, String> = emptyMap(), onResult: (JSONObject?, String?) -> Unit) {
        Thread {
            try {
                val file = java.io.File(filePath)
                if (!file.exists() || !file.isFile) {
                    postMain { onResult(null, "图片文件不存在") }
                    return@Thread
                }
                val ext = file.extension.lowercase()
                val contentType = when (ext) {
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    "mp4" -> "video/mp4"
                    "webm" -> "video/webm"
                    "mov" -> "video/quicktime"
                    else -> "image/jpeg"
                }
                val mb = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                // 流式上传：大文件不再整体读入内存（修复大视频 OOM / 发布页上传崩溃）
                mb.addFormDataPart("file", file.name, file.asRequestBody(contentType.toMediaTypeOrNull()))
                for ((k, v) in (form + authParams())) mb.addFormDataPart(k, v)
                val req = Request.Builder()
                    .url(buildUrl(api, emptyMap()))
                    .header("User-Agent", "LuWuApp/3.1 Android")
                    .post(mb.build())
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    if (!resp.isSuccessful || body == null) {
                        postMain { onResult(null, describeHttp(resp.code)) }
                        return@use
                    }
                    val json = JSONObject(body)
                    postMain { onResult(json, null) }
                }
            } catch (e: Exception) {
                postMain { onResult(null, describeError(e)) }
            }
        }.start()
    }

    /** 上传多张图片（反馈截图）：file0 / file1 / file2 */
    fun uploadMulti(api: String, files: List<String>, form: Map<String, String> = emptyMap(), onResult: (JSONObject?, String?) -> Unit) {
        Thread {
            try {
                val mb = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                for ((i, path) in files.withIndex()) {
                    val file = java.io.File(path)
                    if (!file.exists()) continue
                    val ext = file.extension.lowercase()
                    val contentType = when (ext) {
                        "png" -> "image/png"
                        "gif" -> "image/gif"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }
                    mb.addFormDataPart("file$i", file.name, okhttp3.RequestBody.create(contentType.toMediaTypeOrNull(), file))
                }
                for ((k, v) in form) mb.addFormDataPart(k, v)
                val req = Request.Builder()
                    .url(buildUrl(api, emptyMap()))
                    .header("User-Agent", "LuWuApp/3.1 Android")
                    .post(mb.build())
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    if (!resp.isSuccessful || body == null) {
                        postMain { onResult(null, describeHttp(resp.code)) }
                        return@use
                    }
                    val json = JSONObject(body)
                    postMain { onResult(json, null) }
                }
            } catch (e: Exception) {
                postMain { onResult(null, describeError(e)) }
            }
        }.start()
    }

    /** 把 HTTP 状态码转成看得懂的原因 */
    private fun describeHttp(code: Int): String = when (code) {
        404 -> "接口未就绪（404）"
        500, 502, 503 -> "网站服务异常（$code）"
        else -> "网络请求失败（$code）"
    }

    /** 把异常转成看得懂的原因 */
    private fun describeError(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "无法连接服务器，请检查网络"
        is java.net.ConnectException -> "连接超时，网站可能暂时不可达"
        is java.net.SocketTimeoutException -> "连接超时，请检查网络后重试"
        is javax.net.ssl.SSLException -> "安全连接失败"
        else -> "网络异常：${e.message ?: "请检查网络"}"
    }

    private fun postMain(block: () -> Unit) {
        main.post(block)
    }

    /* ================= RSS 兜底数据源（插件未安装时使用网站自带 Feed） ================= */

    /** 拉取并解析 RSS：主 feed 或分类 feed，返回文章列表 */
    fun getRss(categorySlug: String?, categoryName: String, onResult: (List<PostItem>?, String?) -> Unit) {
        val url = if (categorySlug.isNullOrBlank()) {
            "https://www.65gw.com/feed/"
        } else {
            "https://www.65gw.com/feed/category/${categorySlug}/"
        }
        Thread {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LuWuApp/3.1 Android")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    if (!resp.isSuccessful || body == null) {
                        postMain { onResult(null, describeHttp(resp.code)) }
                        return@use
                    }
                    val list = parseRss(body, categoryName)
                    postMain { onResult(list, null) }
                }
            } catch (e: Exception) {
                postMain { onResult(null, describeError(e)) }
            }
        }.start()
    }

    /** 用正则解析 Typecho RSS（结构与实测站点一致，不依赖平台类库） */
    private fun parseRss(xml: String, categoryName: String): List<PostItem> {
        val out = mutableListOf<PostItem>()
        val itemRegex = Regex("<item>(.*?)</item>", setOf(RegexOption.DOT_MATCHES_ALL))
        for (m in itemRegex.findAll(xml)) {
            val it = m.groupValues[1]
            val title = tagText(it, "title").trim()
            val link = tagText(it, "link").trim()
            val pubDate = tagText(it, "pubDate").trim()
            val desc = tagText(it, "description").trim()

            var excerpt = desc
                .removePrefix("<![CDATA[").removeSuffix("]]>")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\{[^{}]*\\}"), " ")            // 完整短代码 {…} 直接删
                .replace(Regex("\\{[^{}]*?content=[\"']"), "")  // 被截断短代码：去前缀、留正文
                .replace(Regex("\\s+"), " ")
                .trim()
            if (excerpt.length > 100) excerpt = excerpt.substring(0, 100) + "…"

            val id = Regex("archives/(\\d+)").find(link)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            out.add(PostItem(id, title, parseRssDate(pubDate), excerpt, categoryName, link))
        }
        return out
    }

    private fun tagText(xml: String, tag: String): String {
        val m = Regex("<$tag[^>]*>(.*?)</$tag>", setOf(RegexOption.DOT_MATCHES_ALL)).find(xml)
        return m?.groupValues?.get(1)?.trim() ?: ""
    }

    /** "Thu, 10 Sep 2026 18:10:06 +0800" -> "2026-09-10" */
    private fun parseRssDate(pubDate: String): String {
        if (pubDate.isBlank()) return ""
        return try {
            val fmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH)
            val date = fmt.parse(pubDate)
            if (date != null) {
                val out = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH)
                out.format(date)
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}
