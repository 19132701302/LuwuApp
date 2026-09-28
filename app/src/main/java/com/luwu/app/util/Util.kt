package com.luwu.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/** 通用工具 */
object Util {

    /** 主题色是否为深色（用于决定顶栏文字/图标反白） */
    fun isDarkTheme(color: Int): Boolean {
        val lum = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        return lum < 150
    }

    /** 把主题色应用到顶栏：背景 + 标题/返回图标反白（跟随设置页选择） */
    fun applyThemeBar(
        topBar: View?,
        title: TextView? = null,
        back: ImageView? = null,
        logo1: TextView? = null,
        logo2: TextView? = null,
    ) {
        val ctx = topBar?.context ?: title?.context ?: back?.context ?: return
        val color = Prefs.getThemeColor(ctx)
        topBar?.setBackgroundColor(color)
        val dark = isDarkColor(color)
        val ink = if (dark) Color.WHITE else Color.parseColor("#1F2937")
        val sub = if (dark) Color.WHITE else Color.parseColor("#374151")
        title?.setTextColor(ink)
        back?.setColorFilter(sub)
        logo1?.setTextColor(if (dark) Color.WHITE else color)
        logo2?.setTextColor(ink)
    }

    /** 亮度判断：深色背景用白字，浅色背景用深字 */
    fun isDarkColor(c: Int): Boolean {
        val lum = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
        return lum < 150
    }

    /** 相册 Uri → 本地文件路径（ContentResolver 复制到缓存） */
    fun uriToPath(context: Context, uri: android.net.Uri): String? {
        return try {
            val name = "upload_" + System.currentTimeMillis() + ".jpg"
            val file = java.io.File(context.cacheDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** 打开外部浏览器（广告与站外链接都走系统浏览器，保证推广转化） */
    fun openBrowser(context: Context, url: String) {
        if (url.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            toast(context, "无法打开链接")
        }
    }

    /** 应用缓存大小（MB，向下取整） */
    fun getCacheSize(context: Context): Long {
        var total = 0L
        try {
            val dirs = arrayOf(context.cacheDir, context.codeCacheDir)
            for (d in dirs) {
                d?.listFiles()?.forEach { total += dirSize(it) }
            }
        } catch (_: Exception) {}
        return total / (1024 * 1024)
    }

    private fun dirSize(file: java.io.File): Long {
        var size = 0L
        if (file.isFile) return file.length()
        file.listFiles()?.forEach { size += dirSize(it) }
        return size
    }

    fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    /** 弹出软键盘 */
    fun showKeyboard(context: Context, view: android.view.View) {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Exception) {}
    }

    /** 文章正文里的 Joe 短代码转成可读 HTML（{message}/{alert}/{success} 等） */
    fun renderContent(raw: String): String {
        var s = raw
        // {message type="info" content="..." /} -> 提示块
        s = Regex("""\{message\s+type="([^"]*)"\s+content="([^"]*)"[^}]*?/\}""").replace(s) { m ->
            val type = m.groupValues[1].ifBlank { "info" }
            val text = m.groupValues[2]
            """<div class="joe-note $type">$text</div>"""
        }
        // {alert type="info"}...{/alert} -> 引用块
        s = Regex("""\{alert\s+type="([^"]*)"\}(.*?)\{/alert\}""", setOf(RegexOption.DOT_MATCHES_ALL)).replace(s) { m ->
            val type = m.groupValues[1].ifBlank { "info" }
            """<blockquote class="joe-alert $type">${m.groupValues[2]}</blockquote>"""
        }
        // 单标签提示 {success/error/warning/info/note}...{/xx}
        for (t in listOf("success", "error", "warning", "info", "note")) {
            s = Regex("""\{$t\}(.*?)\{/$t\}""", setOf(RegexOption.DOT_MATCHES_ALL)).replace(s) { m ->
                """<div class="joe-note $t">${m.groupValues[1]}</div>"""
            }
        }
        // 清理残余短代码
        s = Regex("""\{[^}]+\}""").replace(s, "")
        return s
    }

    /** 详情页 HTML 包装（含样式） */
    /** 信息流卡片互动数文案：赞 N · 评论 N（均为 0 时显示空） */
    fun statsText(post: com.luwu.app.api.PostItem): String {
        val parts = mutableListOf<String>()
        if (post.likes > 0) parts.add("赞 ${post.likes}")
        if (post.commentsNum > 0) parts.add("评论 ${post.commentsNum}")
        return parts.joinToString(" · ")
    }

    fun buildDetailHtml(title: String, meta: String, content: String, thumb: String = "", isDark: Boolean = false, cid: Int = 0): String {
        // Markdown 渲染（内部含 Joe 短代码保护）
        val body = MarkdownRenderer.render(content, cid)
        // 正文首图与封面相同时不再重复插入封面（文章顶部只保留一张图）
        val firstImg = Regex("""!\[[^\]]*\]\(([^)\s"]+)\)""").find(content)?.groupValues?.get(1)
        val cover = if (thumb.isNotBlank() && firstImg != thumb.trim()) {
            "<img class=\"cover\" src=\"$thumb\" alt=\"cover\"/>"
        } else {
            ""
        }
        return """
            <!DOCTYPE html><html><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
            <style>
              :root{
                --bg:#FFFFFF;--text:#2A2D34;--head:#111318;--line:#F0F2F4;--name:#1F2937;
                --soft:#F6F8FA;--code-bg:#F0F1F4;--quote-bg:#F4FBF9;--quote-text:#5A5F6A;
                --card-bg:#fff;--card-border:#EEF0F3;--muted:#9CA3AF;--td-border:#E8EAEE;--th-bg:#F7F8FA;
                --note-bg:#F2F3F5;--paid-bg:#fff;--pm-bg:#FAFAFA;--pm-border:#E5E7EB;--pm-text:#374151;--pm-active-bg:#FFF5F5;--err-bg:#FEF2F2;--err-border:#FECACA
              }
              :root[data-dark="1"]{
                --bg:#12151A;--text:#D5DBE1;--head:#F1F3F5;--line:#2A3138;--name:#E5E7EB;
                --soft:#1A1F24;--code-bg:#232A31;--quote-bg:#132A27;--quote-text:#AAB2BC;
                --card-bg:#1A1F24;--card-border:#2A3138;--muted:#6B7280;--td-border:#2A3138;--th-bg:#232A31;
                --note-bg:#232A31;--paid-bg:#1A1F24;--pm-bg:#232A31;--pm-border:#2A3138;--pm-text:#AAB2BC;--pm-active-bg:#3A1416;--err-bg:#3A1416;--err-border:#5F2020
              }
              body{font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;margin:0;padding:18px 16px 32px;color:var(--text);line-height:1.85;font-size:16.5px;background:var(--bg);-webkit-font-smoothing:antialiased}
              h1{font-size:21px;line-height:1.5;margin:0 0 4px;color:var(--head);font-weight:700;letter-spacing:.2px}
              .meta{display:flex;align-items:center;gap:10px;margin:12px 0 16px;padding-bottom:14px;border-bottom:1px solid var(--line)}
              .meta .avatar{width:34px;height:34px;border-radius:50%;background:linear-gradient(135deg,#0F766E,#2DD4BF);color:#fff;display:flex;align-items:center;justify-content:center;font-size:14px;font-weight:700;flex:none}
              .meta .who{flex:1;min-width:0}
              .meta .who b{display:block;font-size:13.5px;color:var(--name)}
              .meta .who span{display:flex;align-items:center;font-size:11.5px;color:#9CA3AF;margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
              .cat-pill{display:inline-block;background:#E6F5F3;color:#0D9488;border-radius:4px;padding:1px 7px;font-size:10.5px;font-weight:600;font-style:normal;margin-right:5px;flex:none}
              .meta .stats{font-size:11.5px;color:#9CA3AF;white-space:nowrap}
              .cover{display:block;width:100%;height:auto;max-height:220px;object-fit:cover;border-radius:14px;margin:4px 0 14px;box-shadow:0 2px 10px rgba(0,0,0,.08)}
              .dlcard{display:flex;align-items:center;gap:12px;background:var(--card-bg);border:1px solid var(--card-border);border-radius:16px;padding:14px;margin:14px 0;box-shadow:0 4px 16px rgba(15,23,42,.06);cursor:pointer;transition:transform .12s,box-shadow .12s}
              .dlcard:active{transform:scale(.98);box-shadow:0 2px 6px rgba(15,23,42,.08)}
              .dl-logo{width:50px;height:50px;border-radius:14px;background:var(--card-bg);color:#fff;display:flex;align-items:center;justify-content:center;font-size:19px;font-weight:800;flex:none;border:1px solid var(--card-border);position:relative;overflow:hidden}
              .dl-logo img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;border-radius:13px}
              .dl-mid{flex:1;display:flex;flex-direction:column;min-width:0;gap:4px}
              .dl-mid b{font-size:14.5px;font-weight:600;color:var(--head);line-height:1.35;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
              .dl-mid i{font-style:normal;font-size:11px;color:var(--muted);letter-spacing:.3px}
              .dl-go{flex:none;display:inline-flex;align-items:center;gap:3px;background:#0D9488;color:#fff;font-size:12.5px;font-weight:600;border-radius:12px;padding:9px 13px;box-shadow:0 4px 10px rgba(13,148,136,.28)}
              .dl-go::after{content:"›";font-size:15px;line-height:1}
              .dlcard.dl-baidu .dl-logo,.dlcard.dl-baidu .dl-go{background:linear-gradient(135deg,#3B82F6,#1F6BFF);box-shadow:0 4px 10px rgba(31,107,255,.30)}.dlcard.dl-baidu .dl-mid i{color:#1F6BFF}
              .dlcard.dl-lanzou .dl-logo,.dlcard.dl-lanzou .dl-go{background:linear-gradient(135deg,#4096FF,#1677FF);box-shadow:0 4px 10px rgba(22,119,255,.30)}.dlcard.dl-lanzou .dl-mid i{color:#1677FF}
              .dlcard.dl-quark .dl-logo,.dlcard.dl-quark .dl-go{background:linear-gradient(135deg,#5B8FFF,#3880FF);box-shadow:0 4px 10px rgba(56,128,255,.30)}.dlcard.dl-quark .dl-mid i{color:#3880FF}
              .dlcard.dl-aliyun .dl-logo,.dlcard.dl-aliyun .dl-go{background:linear-gradient(135deg,#FF8A3D,#FF6A00);box-shadow:0 4px 10px rgba(255,106,0,.30)}.dlcard.dl-aliyun .dl-mid i{color:#FF6A00}
              .dlcard.dl-xunlei .dl-logo,.dlcard.dl-xunlei .dl-go{background:linear-gradient(135deg,#12A869,#0E7A4D);box-shadow:0 4px 10px rgba(14,122,77,.30)}.dlcard.dl-xunlei .dl-mid i{color:#0E7A4D}
              .dlcard.dl-weiyun .dl-logo,.dlcard.dl-weiyun .dl-go{background:linear-gradient(135deg,#5B9BFF,#3E82F7);box-shadow:0 4px 10px rgba(62,130,247,.30)}.dlcard.dl-weiyun .dl-mid i{color:#3E82F7}
              .dlcard.dl-123pan .dl-logo,.dlcard.dl-123pan .dl-go{background:linear-gradient(135deg,#4A8BFF,#2A6FFF);box-shadow:0 4px 10px rgba(42,111,255,.30)}.dlcard.dl-123pan .dl-mid i{color:#2A6FFF}
              .dlcard.dl-tianyi .dl-logo,.dlcard.dl-tianyi .dl-go{background:linear-gradient(135deg,#2E8BEF,#0A6FDE);box-shadow:0 4px 10px rgba(10,111,222,.30)}.dlcard.dl-tianyi .dl-mid i{color:#0A6FDE}
              .dlcard.dl-chengtong .dl-logo,.dlcard.dl-chengtong .dl-go{background:linear-gradient(135deg,#A55EEA,#8E44AD);box-shadow:0 4px 10px rgba(142,68,173,.30)}.dlcard.dl-chengtong .dl-mid i{color:#8E44AD}
              .dlcard.dl-gdrive .dl-logo,.dlcard.dl-gdrive .dl-go{background:linear-gradient(135deg,#5E97F6,#4285F4);box-shadow:0 4px 10px rgba(66,133,244,.30)}.dlcard.dl-gdrive .dl-mid i{color:#4285F4}
              .dlcard.dl-onedrive .dl-logo,.dlcard.dl-onedrive .dl-go{background:linear-gradient(135deg,#3A73FF,#0B5BFF);box-shadow:0 4px 10px rgba(11,91,255,.30)}.dlcard.dl-onedrive .dl-mid i{color:#0B5BFF}
              .dlcard.dl-default .dl-logo,.dlcard.dl-default .dl-go{background:linear-gradient(135deg,#14B8A6,#0D9488);box-shadow:0 4px 10px rgba(13,148,136,.28)}.dlcard.dl-default .dl-mid i{color:#0D9488}
              h1,h2,h3,h4{margin:20px 0 8px}
              h2{font-size:19px}h3{font-size:17px}h4{font-size:16px}
              p{margin:12px 0}
              img{max-width:100%;height:auto;border-radius:10px;margin:6px 0;box-shadow:0 1px 6px rgba(0,0,0,.06)}
              a{color:#0F766E;text-decoration:none}
              h2,h3,h4{color:var(--head);line-height:1.5;margin:20px 0 8px}
              blockquote{border-left:4px solid #0F766E;margin:14px 0;padding:10px 14px;color:var(--quote-text);background:var(--quote-bg);border-radius:0 8px 8px 0}
              pre{background:var(--soft);padding:14px;border-radius:10px;overflow-x:auto;font-size:13.5px;line-height:1.6}
              code{background:var(--code-bg);padding:2px 7px;border-radius:5px;font-size:13.5px;color:#0B5A52}
              pre code{background:none;padding:0;color:inherit}
              table{border-collapse:collapse;width:100%;font-size:14px;margin:12px 0}
              td,th{border:1px solid var(--td-border);padding:8px 12px}
              th{background:var(--th-bg)}
              .joe-note{border-radius:10px;padding:12px 16px;margin:14px 0;font-size:14.5px;background:var(--note-bg);border-left:4px solid #9AA0A8}
              .joe-note.info{background:#E4F2F0;color:#134E4A;border-color:#0F766E}
              .joe-note.success{background:#F0FAF0;color:#2F7D32;border-color:#66BB6A}
              .joe-note.error{background:#FEF1F0;color:#C62828;border-color:#EF5350}
              .joe-note.warning{background:#FFF8E6;color:#B26A00;border-color:#FFC53D}
              .paid-hidden{display:flex;align-items:center;gap:12px;border-radius:12px;padding:16px;margin:14px 0;background:var(--paid-bg);border:1px dashed var(--line);text-align:left}
              .paid-hidden .paid-hidden-lock{font-size:24px;flex:none}
              .paid-hidden b{display:block;font-size:14.5px;color:var(--name)}
              .paid-hidden i{display:block;margin-top:2px;font-size:12.5px;color:var(--ink-3);font-style:normal}
              .joe-alert{border-left:4px solid #0F766E;background:#E4F2F0;border-radius:0 8px 8px 0}
              .joe-alert.success{border-color:#66BB6A;background:#F0FAF0}
              .joe-alert.error{border-color:#EF5350;background:#FEF1F0}
              .joe-alert.warning{border-color:#FFC53D;background:#FFF8E6}
              hr{border:none;border-top:1px solid var(--line);margin:20px 0}
              ul,ol{padding-left:22px}
              li{margin:5px 0}
              .paid-card{position:relative;overflow:hidden;border-radius:18px;margin:18px 0;background:var(--paid-bg);border:1px solid var(--line);box-shadow:0 8px 24px rgba(15,118,110,.10),0 2px 6px rgba(0,0,0,.04);text-align:center;padding-bottom:20px}
              .paid-card::before{content:"";position:absolute;top:0;left:0;right:0;height:5px;background:linear-gradient(90deg,#0F766E,#14B8A6 55%,#2DD4BF)}
              .paid-card::after{content:"付 费 资 源";position:absolute;top:0;right:0;background:linear-gradient(135deg,#0F766E,#14B8A6);color:#fff;font-size:10px;font-weight:700;letter-spacing:1px;padding:5px 12px 6px 14px;border-radius:0 0 0 12px}
              .paid-top{display:flex;align-items:center;gap:12px;text-align:left;padding:22px 18px 14px}
              .paid-lock{width:46px;height:46px;flex:none;border-radius:14px;background:linear-gradient(135deg,#0F766E,#14B8A6);color:#fff;display:flex;align-items:center;justify-content:center;font-size:22px;box-shadow:0 6px 14px rgba(15,118,110,.32),inset 0 1px 0 rgba(255,255,255,.25)}
              .paid-top b{display:block;font-size:16.5px;font-weight:800;color:var(--name)}
              .paid-top i{display:block;font-size:12px;font-style:normal;color:#9CA3AF;margin-top:3px}
              .paid-price{font-size:13px;color:#6B7280;margin:2px 0 4px;letter-spacing:1px;display:flex;align-items:baseline;justify-content:center;gap:2px}
              .paid-price .paid-rmb{font-size:15px;font-weight:800;color:#0F766E}
              .paid-price b{font-size:34px;font-weight:800;color:#0F766E;letter-spacing:0;line-height:1.1}
              .paid-price-desc{font-size:11px;color:#9CA3AF;margin-bottom:2px}
              .paid-methods{display:flex;gap:9px;padding:14px 18px 0}
              .pm{flex:1;border:1.5px solid var(--pm-border);background:var(--pm-bg);border-radius:13px;padding:10px 0;font-size:13px;font-weight:600;color:var(--pm-text);display:flex;flex-direction:row;align-items:center;justify-content:center;gap:7px;cursor:pointer;transition:all .18s ease}
              .pm .pm-ico{width:27px;height:27px;flex:none;background-size:27px 27px;background-position:center;background-repeat:no-repeat}
              .pm:active{transform:scale(.96)}
              .pm-wx .pm-ico{background-image:url("data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAzMiAzMiI+PGNpcmNsZSBjeD0iMTYiIGN5PSIxNiIgcj0iMTYiIGZpbGw9IiMwN0MxNjAiLz48cGF0aCBmaWxsPSIjZmZmIiBkPSJNMTYgOC4yYy00LjYgMC04LjMgMy4yLTguMyA3LjIgMCAyLjMgMS4yIDQuMyAzLjEgNS42bC0uOCAyLjcgMy4xLTEuNWMuOS4yIDEuOS40IDIuOS40IDQuNiAwIDguMy0zLjIgOC4zLTcuMnMtMy43LTcuMi04LjMtNy4yeiIvPjxjaXJjbGUgY3g9IjExLjYiIGN5PSIxNS40IiByPSIxLjMiIGZpbGw9IiMwN0MxNjAiLz48Y2lyY2xlIGN4PSIyMC40IiBjeT0iMTUuNCIgcj0iMS4zIiBmaWxsPSIjMDdDMTYwIi8+PC9zdmc+")}
              .pm-ali .pm-ico{background-image:url("data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAzMiAzMiI+PGNpcmNsZSBjeD0iMTYiIGN5PSIxNiIgcj0iMTYiIGZpbGw9IiMxNjc3RkYiLz48dGV4dCB4PSIxNiIgeT0iMjIiIGZvbnQtZmFtaWx5PSJBcmlhbCxIZWx2ZXRpY2Esc2Fucy1zZXJpZiIgZm9udC1zaXplPSIxNiIgZm9udC13ZWlnaHQ9ImJvbGQiIGZpbGw9IiNmZmYiIHRleHQtYW5jaG9yPSJtaWRkbGUiPuaUrzwvdGV4dD48L3N2Zz4=")}
              .pm-qq .pm-ico{background-image:url("data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAzMiAzMiI+PGNpcmNsZSBjeD0iMTYiIGN5PSIxNiIgcj0iMTYiIGZpbGw9IiMxMkI3RjUiLz48cGF0aCBmaWxsPSIjZmZmIiBkPSJNMTYgOC41Yy0yLjggMC01IDIuMy01IDUuMiAwIDEgLjMgMS45LjguMi43LS4yIDEuNi0uOCAyLjgtMS42IDMuOC0uNC41LS4yIDEuMi40IDEuNC42LjIgMS4zIDAgMS45LS40LjkuNSAyIC44IDMuNS44czIuNi0uMyAzLjUtLjhjLjYuNCAxLjMuNiAxLjkuNC42LS4yLjgtLjkuNC0xLjQtLjgtMS0xLjQtMi4yLTEuNi0zLjguNS0uOC44LTEuNy44LTIuNyAwLTIuOS0yLjItNS4yLTUtNS4yeiIvPjxjaXJjbGUgY3g9IjEzLjQiIGN5PSIxMy4yIiByPSIxIiBmaWxsPSIjMTJCN0Y1Ii8+PGNpcmNsZSBjeD0iMTguNiBjeT0iMTMuMiIgcj0iMSIgZmlsbD0iIzEyQjdGNSIvPjwvc3ZnPg==")}
              .pm.active{border-color:#0F766E;background:linear-gradient(135deg,#0F766E,#0D9488);color:#fff;box-shadow:0 4px 12px rgba(15,118,110,.24);transform:translateY(-1px)}
              .pm.active .pm-ico{filter:drop-shadow(0 2px 3px rgba(0,0,0,.18))}
              .pm.active::after{content:"✓";font-size:11px;font-weight:800;margin-left:1px}
              .paid-btn{width:calc(100% - 36px);margin:14px 18px 0;border:none;border-radius:14px;padding:14px 0;font-size:15.5px;font-weight:800;letter-spacing:.5px;color:#fff;background:linear-gradient(135deg,#0F766E,#0D9488 60%,#14B8A6);box-shadow:0 6px 16px rgba(15,118,110,.28),inset 0 1px 0 rgba(255,255,255,.18);cursor:pointer;transition:all .18s ease}
              .paid-btn b{font-size:15.5px}
              .paid-btn:active{transform:scale(.97)}
              .paid-btn.paid-done{background:linear-gradient(135deg,#12B76A,#16A34A);box-shadow:0 6px 16px rgba(18,183,106,.28);cursor:default}
              .paid-btn.paid-loading{background:linear-gradient(135deg,#9CA1A8,#88909A);box-shadow:none}
              .paid-tip{font-size:11.5px;color:#9CA3AF;line-height:1.7;padding:0 18px;margin-top:11px}
              .paid-result{margin-top:12px;text-align:left;padding:0 18px}
              .paid-err{background:var(--err-bg);border:1px solid var(--err-border);color:#DC2626;border-radius:10px;padding:10px 14px;font-size:13px;margin-top:10px}
              .paid-badge{display:inline-block;background:#FEF3C7;color:#B45309;border-radius:999px;padding:2px 10px;font-size:11px;font-weight:600;margin-top:6px}
              .dlcard-wrap{background:var(--pm-bg);border:1px solid var(--pm-border);border-radius:14px;padding:16px}
              .dlcard-wrap a.dl{display:block;background:linear-gradient(135deg,#0F766E,#14B8A6);color:#fff;text-decoration:none;border-radius:12px;padding:12px 16px;text-align:center;font-size:15px;font-weight:700;margin-bottom:10px;box-shadow:0 4px 12px rgba(15,118,110,.22)}
            </style></head>
            <body data-dark="${if (isDark) 1 else 0}"><h1>$title</h1><div class="meta">$meta</div>$cover$body</body></html>
        """.trimIndent()
    }
}