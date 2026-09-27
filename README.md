# 陆伍App（LuwuApp）v9.2 全量源码

> 🌐 **官方网站**：https://www.65gw.com —— 你的互联网资源库（软件/源码/影视/资讯每日更新）
> 📱 **App 最新版下载与更新说明**：https://www.65gw.com/archives/17052.html
> 📦 **APK 直链**：https://aka.doubaocdn.com/s/QdfNSFyEEY

**陆伍博客** 旗下原生 Android App 全量源码，配套 Typecho 后台控制台插件，开箱即用。

- **App 客户端**：`luwu-src/` — Kotlin 原生 Android（包名 com.luwu.app），v9.2 / 版本代码 64
- **配套插件**：`LuwuApp_plugin/` — Typecho 插件 LuwuApp v1.8（后台控制台：公告/广告/更新/推送/热词统计/付费解锁/举报管理）

## 构建 App

```bash
cd luwu-src
export JAVA_HOME=/path/to/jdk17
export PATH=$JAVA_HOME/bin:$PATH
gradle assembleRelease --no-daemon
# 产物: app/build/outputs/apk/release/app-release.apk
```

签名 jks：`luwu-src/luwu-release.jks`（storePassword / keyPassword: LuwuApp2026!，alias: luwu）

## 安装插件

将 `LuwuApp_plugin/` 上传至 Typecho `usr/plugins/` 目录，后台启用即可（LuwuApp v1.8，支持搜索热词真实统计）。

## 主要功能

- **App**：开屏广告、首页/分类/搜索/详情、发布文章、收藏、个人中心、付费解锁、深色模式、底部导航（商业级 5 槽等宽）、搜索热词（真实统计）、互动通知
- **插件**：数据看板、广告开关（首页/信息流/分类页/内容页/开屏）、公告、推送、App 更新面板、热词统计、付费解锁、举报管理

## 站点推广

- 🌐 官网：https://www.65gw.com
- 🚀 App 更新文章：https://www.65gw.com/archives/17052.html
- 📥 APK 下载：https://aka.doubaocdn.com/s/QdfNSFyEEY
- 💬 资源交流：软件/源码/影视/资讯一站式资源库，每日更新
