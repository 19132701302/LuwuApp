package com.luwu.app.util

import com.luwu.app.api.AdItem
import com.luwu.app.api.AdsConfig
import com.luwu.app.api.Category

/** 全局运行态（广告配置等跨页面共享） */
object AppState {
    var ads: AdsConfig? = null

    /** null=未知 true=插件可用 false=插件未装（走 RSS/网页兜底） */
    var pluginAvailable: Boolean? = null

    var announcementChecked = false

    /** 插件未安装时的内置广告（游侠云推广链接） */
    fun defaultAds(): AdsConfig {
        return AdsConfig(
            enabled = true,
            homeTop = listOf(AdItem("免实名免备案 高性能虚拟主机", "https://cloud.uxw.net/aff/NUOZHCHB")),
            feed = listOf(AdItem("极速域名注册 好记又便宜", "https://name.uxw.net")),
            feedEvery = 5,
            category = listOf(AdItem("免实名免备案 高性能虚拟主机", "https://cloud.uxw.net/aff/NUOZHCHB")),
            article = listOf(AdItem("免实名免备案 稳定高速虚拟主机", "https://cloud.uxw.net/aff/NUOZHCHB")),
        )
    }

    /** 插件未安装时的内置分类（与站点导航实际一致：default=网站源码, ys=影视） */
    fun fallbackCategories(): List<Category> {
        return listOf(
            Category(1, "网站源码", "default", 0),
            Category(2, "技术教程", "jsjc", 0),
            Category(3, "绿色软件", "yingyong", 0),
            Category(4, "活动线报", "fulihuodong", 0),
            Category(5, "影视音乐", "yingshi", 0),
            Category(6, "影视", "ys", 0),
            Category(7, "音乐", "yinyue", 0),
            Category(8, "热点资讯", "zixun", 0),
            Category(9, "游戏相关", "youxi", 0),
            Category(10, "网站公告", "gonggao", 0),
        )
    }
}
