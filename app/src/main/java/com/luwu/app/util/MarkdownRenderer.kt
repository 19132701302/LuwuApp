package com.luwu.app.util

/**
 * 轻量 Markdown → HTML 渲染器（Typecho 正文为 Markdown 源文）
 * 支持：标题 / 粗体 / 斜体 / 行内代码 / 代码块 / 链接 / 图片 / 引用 / 无序有序列表 / 表格 / 分割线
 * 下载类链接（.apk/.zip/.rar 或文字含“下载”）渲染为块状按钮
 */
object MarkdownRenderer {

    // 全文价格提取（供 hide 付费锁定卡展示）
    private var pendingPrice: String = "9.9"

    fun render(raw: String, cid: Int = 0): String {
        if (raw.isBlank()) return ""
        // 扫描全文售价（{hide} 付费锁定卡展示价格用）
        pendingPrice = Regex("""售价\s*(\d+(?:\.\d+)?)""").find(raw)?.groupValues?.get(1) ?: "9.9"
        var text = raw

        // 1. 保护代码块（原文，未转义）
        val codeBlocks = mutableListOf<String>()
        text = text.replace(Regex("```[^`]*```", RegexOption.DOT_MATCHES_ALL)) { m ->
            codeBlocks.add(renderCodeBlock(m.value))
            "\u0000CODE${codeBlocks.size - 1}\u0000"
        }

        // 2. 移除 HTML 注释（<!--markdown--> 等渲染残留）
        text = text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

        // 2.5 Typecho 编辑器产生的标题格式：孤立 "#" 行 + 下一行标题文字（###\n软件介绍）
        //     合并为同行标题（### 软件介绍）；纯孤立 "#" 行（后无标题文字）直接移除
        text = text.replace(
            Regex("(?m)^(#{1,4})\\s*$\\n(?![\\s\\n]|```|!\\[|\\{)(.+)$"),
            "$1 $2"
        )
        text = text.replace(Regex("(?m)^#{1,4}\\s*$\\n?"), "")

        // 3. 保护 Joe 短代码（必须在 HTML 转义之前，避免引号被转义后失配）
        val joes = mutableListOf<String>()
        // 自闭合短代码 {message .../} / {cloud .../} / {paid .../} 等
        text = text.replace(Regex("""\{(?:message|cloud|download|video|paid)[^}]*\}""")) { m ->
            joes.add(renderJoe(m.value))
            "\u0001JOE${joes.size - 1}\u0001"
        }
        // 成对 {alert type="x"}...{/alert} / {message}...{/message} / {tip}...{/tip} / {note}...{/note} 等
        text = text.replace(
            Regex("""\{([a-zA-Z]+)(?:\s+type="([^"]*)")?\}(.*?)\{/\1\}""", RegexOption.DOT_MATCHES_ALL)
        ) { m ->
            joes.add(renderJoePair(m.groupValues[1], m.groupValues[2], m.groupValues[3], cid))
            "\u0001JOE${joes.size - 1}\u0001"
        }
        // 3.5 保护 HTML 形式短代码 <joe-cloud title="..." type="..." url="..."></joe-cloud>
        //     （网站 Joe 主题正文渲染形式；App 侧同样渲染为网盘下载卡片，避免被 HTML 转义成源码文本）
        text = text.replace(
            Regex("""<joe-cloud\s+([^>]*)>\s*</joe-cloud>""", RegexOption.DOT_MATCHES_ALL)
        ) { m ->
            joes.add(renderJoeHtml(m.groupValues[1]))
            "\u0001JOE${joes.size - 1}\u0001"
        }
        // 清理其余未识别的 {xxx} / {/xxx} 残留标签
        text = text.replace(Regex("""\{/?[a-zA-Z]+[^}]*\}"""), "")

        // 4. 转义 HTML（占位符不受影响）
        text = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        // 4.5 合并跨行图片语法（![alt] 与 (url) 分处两行时）
        text = text.replace(
            Regex("""!\[([^\]]*)\]\s*\(\s*([^)\s]+)\s*\)""", RegexOption.DOT_MATCHES_ALL)
        ) { m -> "![${m.groupValues[1]}](${m.groupValues[2]})" }

        // 5. 按行解析
        val lines = text.split("\n")
        val out = StringBuilder()
        var i = 0
        var inUl = false
        var inOl = false
        var inQuote = false
        fun closeLists() {
            if (inUl) { out.append("</ul>"); inUl = false }
            if (inOl) { out.append("</ol>"); inOl = false }
        }
        fun closeQuote() {
            if (inQuote) { out.append("</blockquote>"); inQuote = false }
        }

        fun flushBlock() {
            closeLists(); closeQuote()
        }

        while (i < lines.size) {
            val line = lines[i]

            // 代码块占位
            if (line.startsWith("\u0000CODE")) {
                flushBlock()
                out.append(line.replace("\u0000CODE", "").replace("\u0000", "").let { codeBlocks[it.toInt()] })
                i++; continue
            }
            // 表格：当前行与下一行都是表格行
            if (isTableRow(line) && i + 1 < lines.size && isTableRow(lines[i + 1])) {
                flushBlock()
                val rows = mutableListOf<Pair<String, String>>()
                while (i < lines.size && isTableRow(lines[i])) {
                    rows.add(splitTableRow(lines[i]))
                    i++
                }
                out.append(renderTable(rows))
                continue
            }
            // 分割线
            if (line.matches(Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$"))) {
                flushBlock()
                out.append("<hr/>")
                i++; continue
            }
            // 标题
            val h = Regex("^(#{1,4})\\s+(.*?)\\s*#*\\s*$").find(line)
            if (h != null) {
                flushBlock()
                val level = h.groupValues[1].length
                out.append("<h$level>").append(inline(h.groupValues[2].trimEnd('#').trim())).append("</h$level>")
                i++; continue
            }
            // 引用（注意：HTML 转义后 > 已变为 &gt;）
            if (line.startsWith("&gt;") || line.startsWith(">")) {
                if (!inQuote) { flushBlock(); out.append("<blockquote>"); inQuote = true }
                val body = line.removePrefix("&gt;").removePrefix(">").trim()
                out.append("<p>").append(inline(body)).append("</p>")
                i++; continue
            }
            // 无序列表
            val ul = Regex("^\\s*[-*+](\\s+)(.*)$").find(line)
            if (ul != null) {
                if (!inUl) { closeQuote(); out.append("<ul>"); inUl = true }
                out.append("<li>").append(inline(ul.groupValues[2])).append("</li>")
                i++; continue
            }
            // 有序列表
            val ol = Regex("^\\s*\\d+[\\.、](\\s+)(.*)$").find(line)
            if (ol != null) {
                if (!inOl) { closeQuote(); out.append("<ol>"); inOl = true }
                out.append("<li>").append(inline(ol.groupValues[2])).append("</li>")
                i++; continue
            }
            // 空行
            if (line.isBlank()) {
                flushBlock()
                i++; continue
            }
            // 普通段落
            flushBlock()
            out.append("<p>").append(inline(line.trim())).append("</p>")
            i++
        }
        flushBlock()
        var result = out.toString()
        result = result.replace(Regex("<p>\\u0001JOE(\\d+)\\u0001</p>")) { m ->
            joes.getOrElse(m.groupValues[1].toInt()) { "" }
        }
        result = result.replace(Regex("\\u0001JOE(\\d+)\\u0001")) { m ->
            joes.getOrElse(m.groupValues[1].toInt()) { "" }
        }
        return result
    }

    /** Joe 成对短代码 → 提示块 */
    private fun renderJoePair(tag: String, type: String, content: String, cid: Int = 0): String {
        // hide 付费隐藏块：{hide}...{/hide} → 商业级付费锁定卡（完整支付：价格 + 支付方式 + 立即支付按钮）
        if (tag.lowercase() == "hide") {
            val price = pendingPrice
            val cidAttr = if (cid > 0) " data-cid=\"$cid\"" else ""
            val payBtn = if (cid > 0)
                "<button class=\"paid-btn\" type=\"button\">立即支付 <b>¥$price</b></button>" +
                "<div class=\"paid-tip\">支付成功后自动解锁查看 · 订单可在「我的订单」查询</div>"
            else
                "<div class=\"paid-tip\">该资源为网站旧版付费内容 · 如需App内一键支付，请使用 {paid} 短代码发布</div>"
            return "<div class=\"paid-card\"$cidAttr data-price=\"$price\">" +
                "<div class=\"paid-top\">" +
                "  <span class=\"paid-lock\">🔒</span>" +
                "  <div><b>付费内容已隐藏</b><i>本资源为付费资源，支付后即可解锁查看</i></div>" +
                "</div>" +
                "<div class=\"paid-price\"><span class=\"paid-rmb\">¥</span><b>$price</b></div>" +
                "<div class=\"paid-methods\">" +
                "  <button class=\"pm pm-wx\" type=\"button\" data-m=\"wxpay\"><span class=\"pm-ico\"></span>微信支付</button>" +
                "  <button class=\"pm pm-ali\" type=\"button\" data-m=\"alipay\"><span class=\"pm-ico\"></span>支付宝</button>" +
                "  <button class=\"pm pm-qq\" type=\"button\" data-m=\"qqpay\"><span class=\"pm-ico\"></span>QQ支付</button>" +
                "</div>" +
                payBtn +
                "<div class=\"paid-result\"></div></div>"
        }
        val t = when (tag.lowercase()) {
            "alert", "message" -> type.ifBlank { "info" }
            "success", "tip" -> "success"
            "warning", "warn" -> "warning"
            "error", "danger" -> "error"
            else -> type.ifBlank { "info" }
        }
        val raw = content.trim()
        if (raw.isBlank()) return ""
        // 提示块内同样支持 markdown：换行转 <br> 后走行内渲染（图片/链接/强调）
        val body = inline(raw.replace("\n", "<br>"))
        return "<div class=\"joe-note $t\">" + body + "</div>"
    }

    /** Joe 短代码 → 提示块 / 网盘下载按钮 */
    private fun renderJoe(code: String): String {
        val tag = Regex("""\{([a-zA-Z]+)""").find(code)?.groupValues?.get(1)?.lowercase() ?: ""
        // cloud 网盘短代码：{cloud title="百度网盘" type="baidu" url="..." /}
        if (tag == "cloud") {
            val title = Regex("""title=['"]([^'"]*)['"]""").find(code)?.groupValues?.get(1) ?: "网盘下载"
            val url = Regex("""url=['"]([^'"]*)['"]""").find(code)?.groupValues?.get(1) ?: ""
            if (url.isBlank()) return ""
            val rawType = Regex("""type=['"]([^'"]*)['"]""").find(code)?.groupValues?.get(1)?.lowercase() ?: ""
            return renderCloudCard(title, url, rawType)
        }
        // paid 付费资源短代码：{paid cid="17011" price="9.9" /} → 专业付费墙（下载地址不在正文，支付成功后注入）
        if (tag == "paid") {
            val cid = Regex("""cid=['"](\d+)['"]""").find(code)?.groupValues?.get(1) ?: "0"
            val price = Regex("""price=['"]([^'"]*)['"]""").find(code)?.groupValues?.get(1) ?: "9.9"
            if (cid == "0") return ""
            return "<div class=\"paid-card\" data-cid=\"$cid\" data-price=\"$price\">" +
                "<div class=\"paid-top\">" +
                "  <span class=\"paid-lock\">🔒</span>" +
                "  <div><b>付费资源</b><i>本资源为付费阅读，支付后自动解锁下载地址</i></div>" +
                "</div>" +
                "<div class=\"paid-price\"><span class=\"paid-rmb\">¥</span><b>$price</b></div>" +
                "<div class=\"paid-methods\">" +
                "  <button class=\"pm pm-wx\" type=\"button\" data-m=\"wxpay\"><span class=\"pm-ico\"></span>微信支付</button>" +
                "  <button class=\"pm pm-ali\" type=\"button\" data-m=\"alipay\"><span class=\"pm-ico\"></span>支付宝</button>" +
                "  <button class=\"pm pm-qq\" type=\"button\" data-m=\"qqpay\"><span class=\"pm-ico\"></span>QQ支付</button>" +
                "</div>" +
                "<button class=\"paid-btn\" type=\"button\">立即支付 <b>¥$price</b></button>" +
                "<div class=\"paid-tip\">支付成功后自动显示下载地址 · 订单可在「我的订单」查看</div>" +
                "<div class=\"paid-result\"></div></div>"
        }
        // video 短代码：{video url="..." /} → HTML5 播放器（自适应 16:9）
        if (tag == "video") {
            val url = Regex("""url=['"]([^'"]*)['"]""").find(code)?.groupValues?.get(1) ?: ""
            if (url.isBlank()) return ""
            val poster = Regex("""poster=['"]([^'"]*)['"]""").find(code)?.groupValues?.get(1) ?: ""
            val posterAttr = if (poster.isNotBlank()) " poster=\"$poster\"" else ""
            return "<div style=\"position:relative;width:100%;border-radius:12px;overflow:hidden;margin:12px 0;background:#000;\"><video src=\"$url\"$posterAttr controls preload=\"metadata\" style=\"width:100%;display:block;aspect-ratio:16/9;object-fit:contain;\"></video></div>"
        }
        // message 短代码 → 提示块
        val type = Regex("""type=['"]([^'"]*)['"]""").find(code)?.groupValues?.get(1)?.ifBlank { "info" } ?: "info"
        val content = Regex("""content=['"]([^'"]*)['"]""").find(code)?.groupValues?.get(1) ?: ""
        if (content.isBlank()) return ""
        val body = inline(content.replace("\n", "<br>"))
        return "<div class=\"joe-note $type\">" + body + "</div>"
    }

    /** HTML 形式短代码 <joe-cloud title="..." type="..." url="..."></joe-cloud> → 网盘下载卡片（兜底兼容） */
    private fun renderJoeHtml(attrs: String): String {
        val title = Regex("""title=['"]([^'"]*)['"]""").find(attrs)?.groupValues?.get(1) ?: "网盘下载"
        val url = Regex("""url=['"]([^'"]*)['"]""").find(attrs)?.groupValues?.get(1) ?: ""
        if (url.isBlank()) return ""
        val rawType = Regex("""type=['"]([^'"]*)['"]""").find(attrs)?.groupValues?.get(1)?.lowercase() ?: ""
        return renderCloudCard(title, url, rawType)
    }

    /** 网盘下载卡片（花括号 {cloud} 与 HTML <joe-cloud> 共用） */
    private fun renderCloudCard(title: String, url: String, rawType: String): String {
        val icon = when (rawType) {
            "baidu" -> "百度网盘"
            "lanzou", "lanzoux", "lz" -> "蓝奏云"
            "quark" -> "夸克网盘"
            "aliyun", "alipan" -> "阿里云盘"
            "xunlei" -> "迅雷云盘"
            "weiyun", "tencent", "wy" -> "腾讯微云"
            "123pan" -> "123云盘"
            "tianyi", "ty" -> "天翼云盘"
            "chengtong", "ct" -> "城通网盘"
            "gdrive" -> "Google Drive"
            "onedrive" -> "OneDrive"
            "github" -> "Github 仓库"
            "default", "" -> "网盘"
            else -> "网盘"
        }
        val typeCls = when (rawType) {
            "baidu" -> "dl-baidu"
            "lanzou", "lanzoux", "lz" -> "dl-lanzou"
            "quark" -> "dl-quark"
            "aliyun", "alipan" -> "dl-aliyun"
            "xunlei" -> "dl-xunlei"
            "weiyun", "tencent", "wy" -> "dl-weiyun"
            "123pan" -> "dl-123pan"
            "tianyi", "ty" -> "dl-tianyi"
            "chengtong", "ct" -> "dl-chengtong"
            "gdrive" -> "dl-gdrive"
            "onedrive" -> "dl-onedrive"
            else -> "dl-default"
        }
        val brandChar = when (rawType) {
            "baidu" -> "百"; "lanzou", "lanzoux", "lz" -> "蓝"; "quark" -> "夸"
            "aliyun", "alipan" -> "阿"; "xunlei" -> "迅"; "weiyun", "tencent", "wy" -> "微"
            "123pan" -> "1"; "tianyi", "ty" -> "天"; "chengtong", "ct" -> "城"
            "gdrive" -> "G"; "onedrive" -> "O"; "github" -> "GH"; else -> "云"
        }
        val iconName = when (rawType) {
            "baidu" -> "baidu"; "lanzou", "lanzoux", "lz" -> "lanzou"; "quark" -> "quark"
            "aliyun", "alipan" -> "alipan"; "xunlei" -> "xunlei"; "weiyun", "tencent", "wy" -> "weiyun"
            "123pan" -> "123pan"; "tianyi", "ty" -> "tianyi"; "chengtong", "ct" -> "chengtong"
            "gdrive" -> "gdrive"; "onedrive" -> "onedrive"; else -> ""
        }
        val iconImg = if (iconName.isNotBlank()) {
            "<img src=\"https://www.65gw.com/usr/uploads/icons/netdisk/netdisk_$iconName.png\" onerror=\"this.remove();this.parentElement.style.background='#0D9488'\">"
        } else ""
        return "<div class=\"dlcard $typeCls\" onclick=\"window.location.href='$url'\"><span class=\"dl-logo\"><b>$brandChar</b>$iconImg</span><span class=\"dl-mid\"><b>$title</b><i>$icon · 点击进入下载页</i></span><span class=\"dl-go\">下载</span></div>"
    }

    private fun renderCodeBlock(code: String): String {
        val body = code.removePrefix("```").removeSuffix("```")
            .removePrefix("\\n").trim('\n', ' ')
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return "<pre><code>" + body + "</code></pre>"
    }

    private fun isTableRow(line: String): Boolean =
        line.trim().startsWith("|") && line.trim().endsWith("|") &&
            line.count { it == '|' } >= 3

    private fun splitTableRow(line: String): Pair<String, String> {
        val cells = line.trim().trim('|').split("|").map { it.trim() }
        if (cells.size >= 2 && cells[0].all { it == '-' || it == ':' || it == ' ' }) {
            return "" to "" // 分隔行
        }
        return (cells.getOrNull(0) ?: "") to (cells.getOrNull(1) ?: "")
    }

    private fun renderTable(rows: List<Pair<String, String>>): String {
        val sb = StringBuilder("<table>")
        var first = true
        for ((a, b) in rows) {
            if (a.isEmpty() && b.isEmpty()) continue
            if (first) {
                sb.append("<tr><th>").append(inline(a)).append("</th><th>").append(inline(b)).append("</th></tr>")
                first = false
            } else {
                sb.append("<tr><td>").append(inline(a)).append("</td><td>").append(inline(b)).append("</td></tr>")
            }
        }
        sb.append("</table>")
        return sb.toString()
    }

    private fun inline(raw: String): String {
        var s = raw
        // 图片
        s = Regex("!\\[([^\\]]*)\\]\\(\\s*([^)\\s]+)\\s*\\)").replace(s) { m ->
            val alt = m.groupValues[1]
            val url = m.groupValues[2]
            "<img src=\"$url\" alt=\"${alt.replace("\"", "&quot;")}\"/>"
        }
        // 链接（下载类渲染为按钮）
        s = Regex("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)").replace(s) { m ->
            val label = m.groupValues[1]
            val url = m.groupValues[2]
            val low = url.lowercase()
            val isDl = low.endsWith(".apk") || low.endsWith(".zip") || low.endsWith(".rar") ||
                low.endsWith(".7z") || low.contains("/download") || label.contains("下载")
            if (isDl) "<a class=\"dlbtn\" href=\"$url\">⬇ $label</a>"
            else "<a href=\"$url\">$label</a>"
        }
        // 行内代码
        s = Regex("`([^`]+)`").replace(s) { m -> "<code>" + m.groupValues[1] + "</code>" }
        // 粗体
        s = Regex("\\*\\*(.+?)\\*\\*").replace(s) { m -> "<strong>" + m.groupValues[1] + "</strong>" }
        // 斜体（避免与列表符号误伤，仅处理行中）
        s = Regex("(?<![*\\w])\\*([^*\\n]+)\\*(?![*\\w])").replace(s) { m -> "<em>" + m.groupValues[1] + "</em>" }
        // 裸 URL（非 Markdown 链接）：下载后缀渲染为按钮，其余渲染为可点击链接
        s = Regex("(?<![\"'=])(https?://[^\\s<>()\\u4e00-\\u9fff]+)").replace(s) { m ->
            var url = m.groupValues[1].trimEnd('.', ',', '，', '。', ')', '】', '」')
            val low = url.lowercase()
            val isDl = low.endsWith(".apk") || low.endsWith(".zip") || low.endsWith(".rar") ||
                low.endsWith(".7z") || low.endsWith(".exe") || low.contains("/download")
            if (isDl) "<a class=\"dlbtn\" href=\"$url\">⬇ 点击下载</a>"
            else "<a href=\"$url\">$url</a>"
        }
        return s
    }
}
