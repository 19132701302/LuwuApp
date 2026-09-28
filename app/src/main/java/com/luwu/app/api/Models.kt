package com.luwu.app.api

import org.json.JSONArray
import org.json.JSONObject

/** 文章 */
data class PostItem(
    val id: Int,
    val title: String,
    val date: String,
    val excerpt: String,
    val category: String,
    val link: String,
    val thumb: String = "",
    val likes: Int = 0,
    val commentsNum: Int = 0,
    val authorId: Int = 0,
) {
    companion object {
        fun fromJson(o: JSONObject): PostItem {
            return PostItem(
                id = o.optInt("id", 0),
                title = o.optString("title", ""),
                date = o.optString("date", ""),
                excerpt = o.optString("excerpt", ""),
                category = o.optString("category", ""),
                link = o.optString("link", ""),
                thumb = o.optString("thumb", ""),
                likes = o.optInt("likes", 0),
                commentsNum = o.optInt("commentsNum", 0),
                authorId = o.optInt("authorId", 0),
            )
        }
    }
}

/** 分类 */
data class Category(
    val id: Int,
    val name: String,
    val slug: String,
    val count: Int,
    val icon: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): Category {
            return Category(
                id = o.optInt("id", 0),
                name = o.optString("name", ""),
                slug = o.optString("slug", ""),
                count = o.optInt("count", 0),
                icon = o.optString("icon", ""),
            )
        }
    }
}

/** 广告配置 */
data class AdItem(
    val text: String,
    val link: String,
    val type: String = "text",   // text | image | video
    val img: String = "",
    val video: String = "",
) {
    companion object {
        fun fromJson(a: JSONObject): AdItem {
            return AdItem(
                text = a.optString("text", ""),
                link = a.optString("link", ""),
                type = a.optString("type", "text").ifBlank { "text" },
                img = a.optString("img", ""),
                video = a.optString("video", ""),
            )
        }
    }
}

data class AdsConfig(
    val enabled: Boolean = true,
    val homeTop: List<AdItem> = emptyList(),
    val feed: List<AdItem> = emptyList(),
    val feedEvery: Int = 5,
    val category: List<AdItem> = emptyList(),
    val article: List<AdItem> = emptyList(),
    val homeCats: List<HomeCat> = emptyList(),
) {
    companion object {
        fun fromJson(o: JSONObject): AdsConfig {
            fun parseAds(key: String): List<AdItem> {
                val a = o.opt(key) ?: return emptyList()
                val arr = if (a is JSONArray) a else JSONArray().put(a)
                val list = mutableListOf<AdItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val ad = AdItem.fromJson(obj)
                    // 无效广告忽略：文字型必须有文案，图片/视频型必须有图或文案
                    val okText = ad.type == "text" && ad.text.isNotBlank()
                    val okMedia = ad.type != "text" && (ad.img.isNotBlank() || ad.text.isNotBlank())
                    if (ad.link.isBlank() || !(okText || okMedia)) continue
                    list.add(ad)
                }
                return list
            }
            fun parseCats(): List<HomeCat> {
                val arr = o.optJSONArray("homeCats") ?: return emptyList()
                val list = mutableListOf<HomeCat>()
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    val slug = c.optString("slug", "")
                    if (slug.isBlank()) continue
                    list.add(HomeCat(slug, c.optString("name", slug), c.optString("desc", ""), c.optString("icon", ""), c.optString("color", "")))
                }
                return list
            }
            val enabled = o.optString("enable", "on") != "off"
            return AdsConfig(
                enabled = enabled,
                homeTop = parseAds("homeTop"),
                feed = parseAds("feed"),
                feedEvery = o.optInt("feedEvery", 5).coerceAtLeast(2),
                category = parseAds("category"),
                article = parseAds("article"),
                homeCats = parseCats(),
            )
        }
    }
}

/** 首页分类卡片（后台可配置） */
data class HomeCat(
    val slug: String,
    val name: String,
    val desc: String,
    val icon: String = "",
    val color: String = "",
)

/** 公告 */
data class Announcement(
    val enabled: Boolean,
    val title: String,
    val content: String,
    val link: String,
) {
    companion object {
        fun fromJson(o: JSONObject): Announcement {
            return Announcement(
                enabled = o.optBoolean("enabled", false),
                title = o.optString("title", "网站公告"),
                content = o.optString("content", ""),
                link = o.optString("link", ""),
            )
        }
    }
}

/** 更新信息 */
data class UpdateInfo(
    val enabled: Boolean,
    val versionName: String,
    val versionCode: Int,
    val url: String,
    val changelog: String,
    val force: Boolean,
) {
    companion object {
        fun fromJson(o: JSONObject): UpdateInfo {
            return UpdateInfo(
                enabled = o.optBoolean("enabled", false),
                versionName = o.optString("versionName", ""),
                versionCode = o.optInt("versionCode", 0),
                url = o.optString("url", ""),
                changelog = o.optString("changelog", ""),
                force = o.optBoolean("force", false),
            )
        }
    }
}
