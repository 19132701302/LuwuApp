package com.luwu.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 轻量图片加载：OkHttp 下载 + 采样解码 + 内存/磁盘二级缓存 + 预下载
 *
 * v7.6 商业级升级：
 *  1) inSampleSize 采样解码：按目标尺寸降采样，内存占用与解码耗时大幅下降
 *     （开屏 1080×2340 全量解码约 10MB/张 → 按屏幕采样后约 2.5MB/张）
 *  2) 磁盘 LRU 缓存：进程退出后仍可命中，次日冷启动零网络等待
 *  3) prefetch 预下载：后台把下一轮广告图片提前落到磁盘，开屏秒现
 *  兼容性：新增 reqW/reqH 带默认值，旧调用点（2 参数 / onError 具名 / 尾随 lambda）全部不变
 */
object ImageLoader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, Bitmap>()

    private const val DISK_MAX_FILES = 60
    private const val DISK_MAX_BYTES = 60L * 1024 * 1024 // 60MB

    @Volatile
    private var diskDir: File? = null

    /** App.onCreate 时调用一次，初始化磁盘缓存目录 */
    fun init(context: Context) {
        if (diskDir == null) {
            synchronized(this) {
                if (diskDir == null) {
                    diskDir = File(context.cacheDir, "luwu_img_cache").apply { mkdirs() }
                }
            }
        }
    }

    private fun diskFile(url: String): File? {
        val dir = diskDir ?: return null
        return File(dir, md5(url) + ".img")
    }

    private fun md5(s: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            s.hashCode().toString()
        }
    }

    /** 同步取图片 Drawable（评论小图用），失败返回 null；命中磁盘缓存时零网络 */
    fun fetchDrawable(url: String): android.graphics.drawable.Drawable? {
        return try {
            val bmp = loadBitmap(url, 0, 0)
            if (bmp == null) null else android.graphics.drawable.BitmapDrawable(null, bmp)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 异步加载图片到 ImageView；失败时静默（显示占位背景），可传入 onError 兜底
     * @param reqW/reqH 目标显示尺寸（像素），>0 时按此采样解码；列表小图与开屏大图都建议传
     */
    fun load(
        url: String,
        target: ImageView,
        tag: Any = url,
        reqW: Int = 0,
        reqH: Int = 0,
        onError: (() -> Unit)? = null
    ) {
        if (url.isBlank()) {
            onError?.invoke()
            return
        }
        target.tag = tag
        cache[url]?.let {
            target.setImageBitmap(it)
            return
        }
        Thread {
            val bmp = loadBitmap(url, reqW, reqH)
            if (bmp != null) {
                cache[url] = bmp
                target.post {
                    // 只更新仍对应同一请求的 ImageView，避免列表复用串图
                    if (target.tag == tag) target.setImageBitmap(bmp)
                }
            } else {
                target.post { if (target.tag == tag) onError?.invoke() }
            }
        }.start()
    }

    /** 主加载链路：内存缓存 → 磁盘缓存 → 网络下载（成功后写磁盘） */
    private fun loadBitmap(url: String, reqW: Int, reqH: Int): Bitmap? {
        // 1) 内存缓存
        cache[url]?.let { return it }
        // 2) 磁盘缓存
        diskFile(url)?.takeIf { it.exists() }?.let { file ->
            val bmp = try { decodeSampled(file.readBytes(), reqW, reqH) } catch (e: Exception) { null }
            if (bmp != null) {
                touchDisk(file)
                return bmp
            }
            file.delete()
        }
        // 3) 网络下载
        return try {
            val req = Request.Builder().url(url).header("User-Agent", "LuWuApp/3.2 Android").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                val bytes = body.bytes()
                val bmp = decodeSampled(bytes, reqW, reqH)
                if (bmp != null) writeDisk(url, bytes)
                bmp
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 采样解码：先读边界，按目标尺寸计算 inSampleSize，只解码需要的像素。
     * 未指定目标尺寸时按原图解码（头像等小图场景，保留透明通道）。
     */
    private fun decodeSampled(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        if (reqW <= 0 || reqH <= 0) {
            return try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (e: Exception) { null }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            // 默认 ARGB_8888：开屏广告含渐变/文字，避免 RGB_565 色带；内存已由采样控制
        }
        return try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) } catch (e: Exception) { null }
    }

    /* ---------- 磁盘缓存（简单 LRU） ---------- */

    private fun writeDisk(url: String, bytes: ByteArray) {
        val file = diskFile(url) ?: return
        try {
            file.writeBytes(bytes)
            trimDisk()
        } catch (_: Exception) {
        }
    }

    private fun touchDisk(file: File) {
        try { file.setLastModified(System.currentTimeMillis()) } catch (_: Exception) {}
    }

    /** 超出文件数/容量时删除最旧文件 */
    private fun trimDisk() {
        val dir = diskDir ?: return
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        var count = files.size
        for (f in files) {
            if (count <= DISK_MAX_FILES && total <= DISK_MAX_BYTES) break
            total -= f.length()
            count--
            f.delete()
        }
    }

    /** 预下载：只落磁盘不显示，供下一轮开屏广告秒开（WiFi 场景调用） */
    fun prefetch(url: String): Boolean {
        if (url.isBlank()) return false
        if (diskFile(url)?.exists() == true) return true
        return try {
            val req = Request.Builder().url(url).header("User-Agent", "LuWuApp/3.2 Android").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val bytes = resp.body?.bytes() ?: return false
                writeDisk(url, bytes)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun clear() {
        cache.clear()
        diskDir?.listFiles()?.forEach { it.delete() }
    }
}
