# 陆伍博客 App 源码

原生 Kotlin 安卓客户端 + Typecho 配套插件 LuwuApp，一套完整的内容社区 App 方案。

## 工程结构
- `app/`：Android 客户端（Kotlin，包名 com.luwu.app，minSdk 21 / targetSdk 34）
- `LuwuApp/`：Typecho 插件（PHP），提供文章/评论/点赞/收藏/关注/消息/反馈/更新等全部 API

## 功能
- 首页：轮播图、分类卡片、信息流广告（可后台配置）、排序筛选
- 分类页：统计卡片 + 分类宫格 + 分类文章
- 文章详情：正文渲染、评论（回复/表情/图片）、点赞、收藏、关注作者、网盘下载短代码
- 发帖：支持短代码（提示框/引用/网盘下载/图片/链接/代码块）
- 我的：收藏、历史、关注、发布、意见反馈、消息中心（一键已读）
- 主题色可自定义，顶部导航颜色全局生效

## 编译方法
1. 安装 JDK 17 + Android SDK（targetSdk 34）
2. 在 `app/build.gradle` 配置你的签名（keystore）
3. 执行 `gradle assembleRelease`
4. 插件目录 `LuwuApp/` 上传到 Typecho 的 `usr/plugins/` 启用即可

## 说明
- 演示 App：陆伍博客（截图见文章）
- 接口前缀默认为 https://www.65gw.com/action/luwu-app，可在 `app/src/main/java/com/luwu/app/api/ApiClient.kt` 中修改
- 请替换为自己的品牌名称与 logo
