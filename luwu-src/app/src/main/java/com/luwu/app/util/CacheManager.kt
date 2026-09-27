package com.luwu.app.util

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 轻量离线缓存：首页/分类列表 + 文章详情
 * - 列表缓存 10 分钟（弱网秒开）
 * - 详情缓存 7 天（离线阅读）
 * - 与设置页「清除缓存」联动（存于 cacheDir/luwu_cache）
 */
object CacheManager {

    private const val DIR = "luwu_cache"
    private const val LIST_TTL = 10 * 60 * 1000L
    private const val DETAIL_TTL = 7 * 24 * 3600 * 1000L

    private fun file(context: Context, key: String): File =
        File(File(context.cacheDir, DIR).apply { mkdirs() }, key + ".json")

    fun putJson(context: Context, key: String, json: JSONObject) {
        try {
            file(context, key).writeText(json.toString())
        } catch (_: Exception) {}
    }

    /** 读取缓存：未过期返回 JSON，过期/损坏返回 null */
    private fun getJson(context: Context, key: String, ttl: Long): JSONObject? {
        return try {
            val f = file(context, key)
            if (!f.exists()) return null
            if (System.currentTimeMillis() - f.lastModified() > ttl) {
                f.delete()
                return null
            }
            JSONObject(f.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun getList(context: Context, key: String): JSONObject? = getJson(context, key, LIST_TTL)

    fun getDetail(context: Context, key: String): JSONObject? = getJson(context, key, DETAIL_TTL)

    /** 设置页清除缓存时顺带清理（cacheDir 递归删除已覆盖，此处仅兜底） */
    fun clear(context: Context) {
        try {
            File(context.cacheDir, DIR).deleteRecursively()
        } catch (_: Exception) {}
    }
}
