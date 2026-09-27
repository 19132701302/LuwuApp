package com.luwu.app.util

import android.content.Context
import android.content.SharedPreferences
import com.luwu.app.api.PostItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地存储：收藏 / 浏览历史 / 搜索历史 / 登录态
 * 全部存于 SharedPreferences，不占服务器资源
 */
object Prefs {

    private const val NAME = "luwu_prefs"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_HISTORY = "history"
    private const val KEY_SEARCH = "search_history"
    private const val KEY_UID = "uid"
    private const val KEY_NAME = "user_name"
    private const val KEY_AVATAR = "user_avatar"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_TOKEN = "token"
    private const val KEY_THEME = "theme_color"
    private const val KEY_LIKED = "liked_posts"
    private const val KEY_AGREED = "privacy_agreed"
    private const val KEY_FONT_ZOOM = "font_zoom"
    private const val KEY_APP_THEME_MODE = "app_theme_mode"
    private const val KEY_THEME_SET = "app_theme_mode_set"
    private const val KEY_SPLASH_DAY = "splash_shown_day"
    private const val KEY_BLOCKED = "blocked_uids"
    private const val KEY_PUSH_ENABLED = "push_enabled"
    private const val KEY_LAST_PUSH_ID = "last_push_id"
    private const val DEFAULT_THEME = "#0D9488"

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /* ---------- 收藏 ---------- */

    fun getFavorites(context: Context): MutableList<PostItem> = readPosts(sp(context), KEY_FAVORITES)

    fun isFavorite(context: Context, id: Int): Boolean {
        val list = getFavorites(context)
        return list.any { it.id == id }
    }

    fun addFavorite(context: Context, post: PostItem): Boolean {
        val list = getFavorites(context)
        if (list.any { it.id == post.id }) return false
        list.add(0, post)
        writePosts(sp(context), KEY_FAVORITES, list)
        return true
    }

    fun removeFavorite(context: Context, id: Int) {
        val list = getFavorites(context)
        list.removeAll { it.id == id }
        writePosts(sp(context), KEY_FAVORITES, list)
    }

    fun clearFavorites(context: Context) {
        sp(context).edit().remove(KEY_FAVORITES).apply()
    }

    /* ---------- 浏览历史 ---------- */

    fun getHistory(context: Context): MutableList<PostItem> = readPosts(sp(context), KEY_HISTORY)

    fun addHistory(context: Context, post: PostItem) {
        val list = getHistory(context)
        list.removeAll { it.id == post.id }
        list.add(0, post)
        while (list.size > 50) list.removeAt(list.size - 1)
        writePosts(sp(context), KEY_HISTORY, list)
    }

    fun clearHistory(context: Context) {
        sp(context).edit().remove(KEY_HISTORY).apply()
    }

    /* ---------- 搜索历史 ---------- */

    fun getSearchHistory(context: Context): MutableList<String> {
        val raw = sp(context).getString(KEY_SEARCH, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) out.add(arr.optString(i))
        return out
    }

    fun addSearchHistory(context: Context, keyword: String) {
        if (keyword.isBlank()) return
        val list = getSearchHistory(context)
        list.removeAll { it == keyword }
        list.add(0, keyword)
        while (list.size > 10) list.removeAt(list.size - 1)
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        sp(context).edit().putString(KEY_SEARCH, arr.toString()).apply()
    }

    fun clearSearchHistory(context: Context) {
        sp(context).edit().remove(KEY_SEARCH).apply()
    }

    /* ---------- 付费解锁缓存：cid → 解锁后的下载区块(Markdown) ---------- */

    private const val KEY_PAID = "luwu_paid_cache"

    fun getPaidUnlocked(context: Context, cid: Int): String? {
        return sp(context).getString("$KEY_PAID:$cid", null)
    }

    fun setPaidUnlocked(context: Context, cid: Int, content: String) {
        sp(context).edit().putString("$KEY_PAID:$cid", content).apply()
    }

    /* ---------- 登录态 ---------- */

    fun setLogin(context: Context, uid: Long, account: String, name: String, avatar: String = "") {
        sp(context).edit()
            .putLong(KEY_UID, uid)
            .putString(KEY_ACCOUNT, account)
            .putString(KEY_NAME, name)
            .putString(KEY_AVATAR, avatar)
            .apply()
    }

    fun getAvatar(context: Context): String = sp(context).getString(KEY_AVATAR, "") ?: ""

    fun setAvatar(context: Context, avatar: String) {
        sp(context).edit().putString(KEY_AVATAR, avatar).apply()
    }

    fun logout(context: Context) {
        sp(context).edit().remove(KEY_UID).remove(KEY_ACCOUNT).remove(KEY_TOKEN).remove(KEY_NAME).remove(KEY_AVATAR).apply()
    }

    fun getUid(context: Context): Long = sp(context).getLong(KEY_UID, -1L)

    fun isLoggedIn(context: Context): Boolean = sp(context).getLong(KEY_UID, -1L) > 0

    /** 被屏蔽用户 uid 列表（本地缓存 + 服务端 api_blocks 同步） */
    fun getBlockedIds(context: Context): Set<Int> {
        val s = sp(context).getString(KEY_BLOCKED, "") ?: ""
        return s.split(",").filter { it.isNotBlank() }.mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun setBlockedIds(context: Context, ids: Set<Int>) {
        sp(context).edit().putString(KEY_BLOCKED, ids.joinToString(",")).apply()
    }

    fun isBlocked(context: Context, uid: Int): Boolean = uid > 0 && uid in getBlockedIds(context)

    fun getUserName(context: Context): String = sp(context).getString(KEY_NAME, "") ?: ""

    fun getAccount(context: Context): String = sp(context).getString(KEY_ACCOUNT, "") ?: ""

    /** 登录 token（服务端签发，30 天有效；不再在本地保存明文密码） */
    fun saveToken(context: Context, token: String) {
        sp(context).edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String = sp(context).getString(KEY_TOKEN, "") ?: ""

    /* ---------- 合规与阅读 ---------- */

    /** 是否已同意用户协议与隐私政策（首次启动确认，商用合规） */
    fun isAgreed(context: Context): Boolean = sp(context).getBoolean(KEY_AGREED, false)

    fun markAgreed(context: Context) {
        sp(context).edit().putBoolean(KEY_AGREED, true).apply()
    }

    /** 详情页字号缩放（100/120/140） */
    fun getFontZoom(context: Context): Int = sp(context).getInt(KEY_FONT_ZOOM, 100)

    fun setFontZoom(context: Context, zoom: Int) {
        sp(context).edit().putInt(KEY_FONT_ZOOM, zoom).apply()
    }

    /* ---------- 全局主题模式（浅色/深色/跟随系统） ---------- */

    fun getAppThemeMode(context: Context): String =
        sp(context).getString(KEY_APP_THEME_MODE, "system") ?: "system"

    fun setAppThemeMode(context: Context, mode: String) {
        sp(context).edit().putString(KEY_APP_THEME_MODE, mode).putBoolean(KEY_THEME_SET, true).apply()
    }

    /** 用户是否显式设置过主题（未设置时采用服务器/站点默认） */
    fun hasSetThemeMode(context: Context): Boolean = sp(context).getBoolean(KEY_THEME_SET, false)

    /* ---------- 开屏广告（每日一次标记 + 下一轮预下载配置） ---------- */

    /** 今天是否已展示过开屏广告 */
    fun isSplashShownToday(context: Context): Boolean {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        return sp(context).getString(KEY_SPLASH_DAY, "") == today
    }

    fun markSplashShownToday(context: Context) {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        sp(context).edit().putString(KEY_SPLASH_DAY, today).apply()
    }

    /* ---------- 开屏广告：下一轮预下载配置（冷启动秒开路径用） ---------- */

    private const val KEY_SPLASH_NEXT_URL = "splash_next_url"
    private const val KEY_SPLASH_NEXT_TARGET = "splash_next_target"
    private const val KEY_SPLASH_NEXT_DURATION = "splash_next_duration"
    private const val KEY_SPLASH_NEXT_SKIP = "splash_next_skip"
    private const val KEY_SPLASH_NEXT_ONCE = "splash_next_once"

    /** 上轮缓存的下一轮开屏配置（url 为空表示无缓存，需走网络拉取） */
    fun getSplashNextUrl(context: Context): String = sp(context).getString(KEY_SPLASH_NEXT_URL, "") ?: ""
    fun getSplashNextTarget(context: Context): String = sp(context).getString(KEY_SPLASH_NEXT_TARGET, "") ?: ""
    fun getSplashNextDuration(context: Context): Int = sp(context).getInt(KEY_SPLASH_NEXT_DURATION, 3)
    fun getSplashNextSkipText(context: Context): String = sp(context).getString(KEY_SPLASH_NEXT_SKIP, "跳过") ?: "跳过"
    fun getSplashNextOnce(context: Context): Boolean = sp(context).getBoolean(KEY_SPLASH_NEXT_ONCE, false)

    /** 保存本轮广告配置，作为下一次冷启动的秒开缓存 */
    fun saveSplashNext(context: Context, url: String, target: String, duration: Int, skipText: String, once: Boolean) {
        sp(context).edit()
            .putString(KEY_SPLASH_NEXT_URL, url)
            .putString(KEY_SPLASH_NEXT_TARGET, target)
            .putInt(KEY_SPLASH_NEXT_DURATION, duration)
            .putString(KEY_SPLASH_NEXT_SKIP, skipText)
            .putBoolean(KEY_SPLASH_NEXT_ONCE, once)
            .apply()
    }

    /* ---------- 主题色（顶部导航颜色，设置页可改） ---------- */

    /** 是否已点赞 */
    fun isLiked(context: Context, id: Int): Boolean =
        sp(context).getStringSet(KEY_LIKED, emptySet())?.contains(id.toString()) ?: false

    /** 记录点赞 */
    fun unmarkLiked(context: Context, id: Int) {
        val set = sp(context).getStringSet(KEY_LIKED, emptySet()) ?: emptySet()
        val next = set.toMutableSet().apply { remove(id.toString()) }
        sp(context).edit().putStringSet(KEY_LIKED, next).apply()
    }

    fun markLiked(context: Context, id: Int) {
        val set = sp(context).getStringSet(KEY_LIKED, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(id.toString())
        sp(context).edit().putStringSet(KEY_LIKED, set).apply()
    }

    /** 点赞数字（本地缓存/显示用） */
    fun getLikeCount(context: Context, id: Int): Int =
        sp(context).getInt("like_$id", 0)

    fun setLikeCount(context: Context, id: Int, count: Int) {
        sp(context).edit().putInt("like_$id", count).apply()
    }

    fun setThemeColor(context: Context, colorHex: String) {
        sp(context).edit().putString(KEY_THEME, colorHex).apply()
    }

    /** 返回解析后的颜色值，异常时回退墨青默认色 */
    fun getThemeColor(context: Context): Int {
        val hex = sp(context).getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        return try {
            android.graphics.Color.parseColor(hex)
        } catch (e: Exception) {
            android.graphics.Color.parseColor(DEFAULT_THEME)
        }
    }

    fun getThemeColorHex(context: Context): String {
        val hex = sp(context).getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        return try {
            android.graphics.Color.parseColor(hex)
            hex
        } catch (e: Exception) {
            DEFAULT_THEME
        }
    }

    /* ---------- 序列化 ---------- */

    private fun readPosts(sp: SharedPreferences, key: String): MutableList<PostItem> {
        val raw = sp.getString(key, "[]") ?: "[]"
        val out = mutableListOf<PostItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(PostItem.fromJson(o))
            }
        } catch (e: Exception) {
            // 数据损坏时重置
        }
        return out
    }

    private fun writePosts(sp: SharedPreferences, key: String, list: List<PostItem>) {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("title", p.title)
            o.put("date", p.date)
            o.put("excerpt", p.excerpt)
            o.put("category", p.category)
            o.put("link", p.link)
            arr.put(o)
        }
        sp.edit().putString(key, arr.toString()).apply()
    }

    /** 推送通知开关（默认开启） */
    fun isPushEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_PUSH_ENABLED, true)
    fun setPushEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_PUSH_ENABLED, enabled).apply()
    }

    /** 已收到推送的最大 ID（本地去重） */
    fun getLastPushId(context: Context): Int = sp(context).getInt(KEY_LAST_PUSH_ID, 0)
    fun setLastPushId(context: Context, id: Int) {
        sp(context).edit().putInt(KEY_LAST_PUSH_ID, id).apply()
    }
}
