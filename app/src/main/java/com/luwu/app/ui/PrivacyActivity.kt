package com.luwu.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.luwu.app.R
import com.luwu.app.util.Util

/** 隐私政策：本地全文展示，无网络请求 */
class PrivacyActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        Util.applyThemeBar(
            findViewById(R.id.top_bar),
            findViewById<TextView>(R.id.tv_title),
            findViewById(R.id.btn_back),
        )
        val wv = findViewById<WebView>(R.id.webview)
        wv.settings.javaScriptEnabled = false
        wv.settings.defaultTextEncodingName = "utf-8"
        val dark = com.luwu.app.util.ThemeManager.isDarkNow(this)
        wv.loadDataWithBaseURL(
            null,
            if (dark) PRIVACY_HTML_DARK else PRIVACY_HTML,
            "text/html", "utf-8", null
        )
    }

    companion object {
        private val PRIVACY_HTML = """
            <!DOCTYPE html><html><head><meta charset='utf-8'>
            <meta name='viewport' content='width=device-width,initial-scale=1'>
            <style>
              body{margin:0;padding:20px 18px 36px;font-family:sans-serif;color:#2B2F36;line-height:1.8;font-size:14.5px;background:#fff}
              h2{font-size:17px;color:#0D9488;margin:22px 0 10px}
              h3{font-size:15px;color:#134E4A;margin:18px 0 8px}
              p{margin:8px 0}
              ul{margin:8px 0;padding-left:20px}
              li{margin:6px 0}
              .meta{color:#94A3B8;font-size:12.5px;border-bottom:1px solid #F1F5F5;padding-bottom:14px;margin-bottom:4px}
              .head h1{font-size:20px;margin:0 0 4px;color:#111}
            </style></head><body>
            <div class="head"><h1>陆伍博客 App 隐私政策</h1>
            <div class="meta">更新日期：2026年9月11日　生效日期：2026年9月11日</div></div>
            <p>感谢你使用陆伍博客（以下简称"本应用"或"我们"）。我们深知个人信息对你的重要性，将按照法律法规要求，采取相应安全保护措施，尽力保护你的个人信息安全可控。请在使用本应用前仔细阅读本隐私政策。</p>
            <h2>一、适用范围</h2>
            <p>本隐私政策适用于陆伍博客 Android 应用及其关联服务（含官网 www.65gw.com）。你使用本应用即视为已阅读并同意本政策的全部内容。</p>
            <h2>二、我们收集的信息</h2>
            <p>我们仅收集提供核心服务所必需的信息，遵循"最小必要"原则：</p>
            <ul>
            <li><b>账号信息</b>：注册/登录时收集你填写的用户名（或邮箱）和密码（密码经单向加密存储，任何人无法明文读取），用于身份认证。</li>
            <li><b>你主动提交的内容</b>：评论、发帖内容；意见反馈（问题标题、内容、联系方式、你主动上传的截图）；头像图片（你主动上传时）。</li>
            <li><b>互动记录</b>：点赞、收藏、关注、评论通知等操作记录，用于提供服务并展示对应状态。</li>
            <li><b>浏览历史</b>：文章浏览历史保存在你手机本地（App 内），仅用于"我的-历史"功能展示，不会上传到服务器。</li>
            <li><b>设备与日志信息</b>：服务器在提供服务过程中自动记录的常规日志（IP 地址、访问时间、访问内容），用于保障服务安全、排查故障。</li>
            </ul>
            <h2>三、我们不收集的信息</h2>
            <p>本应用<b>不收集</b>：通讯录、短信、通话记录、精确位置、相册内容、麦克风、摄像头画面、设备唯一标识符（IMEI/OAID 等），亦无任何形式的行为追踪与画像。</p>
            <h2>四、我们如何使用信息</h2>
            <ul>
            <li>提供、维护和改进服务（登录、展示内容、同步互动状态）；</li>
            <li>处理你的反馈与投诉；</li>
            <li>保障服务与账号安全（识别异常登录、防范恶意行为）；</li>
            <li>法律规定的其他用途。</li>
            </ul>
            <p>我们不会将你的个人信息用于与上述目的无关的用途，如需变更用途，将另行取得你的同意。</p>
            <h2>五、信息的存储</h2>
            <ul>
            <li><b>存储地点</b>：你的个人信息存储于中华人民共和国境内的服务器（由我们自主运营）。</li>
            <li><b>存储期限</b>：账号存续期间持续保存；账号注销后，我们将在合理期限内删除或匿名化处理你的个人信息，法律法规另有规定的除外。</li>
            <li><b>安全措施</b>：采用传输加密（HTTPS）、密码单向加密存储、访问权限控制、日志审计等措施保护你的信息。</li>
            </ul>
            <h2>六、信息共享与对外提供</h2>
            <ul>
            <li>我们不会向任何第三方出售、出租或共享你的个人信息；</li>
            <li>我们不接入任何第三方统计 SDK、广告 SDK 或推送 SDK，你的数据不会因 SDK 而被第三方获取；</li>
            <li>仅在以下情形可能对外提供信息：获得你明确同意；根据法律法规、司法机关或行政机关的强制性要求。</li>
            </ul>
            <h2>七、第三方链接</h2>
            <p>本应用内文章或公告可能包含第三方网站链接（如网盘下载地址）。点击后将离开本应用，第三方网站的隐私保护规则不属于本政策范围，建议你查阅其隐私政策。</p>
            <h2>八、你的权利</h2>
            <p>依据《中华人民共和国个人信息保护法》，你享有以下权利：</p>
            <ul>
            <li><b>查阅、更正</b>：可在"我的"页面查看和修改你的头像等资料；</li>
            <li><b>删除</b>：可删除自己发布的评论、帖子；</li>
            <li><b>撤回同意</b>：可随时卸载应用、删除本地数据；</li>
            <li><b>注销账号</b>：可通过下文联系方式向我们申请注销账号，注销后你的个人信息将被删除或匿名化处理；</li>
            <li><b>投诉举报</b>：有权向有关主管部门投诉举报。</li>
            </ul>
            <h2>九、未成年人保护</h2>
            <p>本应用主要面向成年人提供服务。若你为未满 14 周岁的儿童，请在监护人陪同下使用，并由监护人阅读本政策。若我们发现在未获监护人同意的情况下收集了儿童个人信息，将尽快删除。</p>
            <h2>十、政策更新</h2>
            <p>我们可能适时修订本政策。重大变更（如收集范围、存储方式、共享对象的实质变化）将通过应用内公告等方式显著提示。修订后继续使用本应用即视为同意更新后的政策。</p>
            <h2>十一、联系我们</h2>
            <p>如你对本政策或个人信息保护有任何疑问、意见或建议，或需申请注销账号，可通过以下方式联系我们：</p>
            <ul>
            <li>官网：https://www.65gw.com</li>
            <li>联系 QQ：615806139</li>
            </ul>
            <p>我们将在 15 个工作日内回复。</p>
            </body></html>
        """.trimIndent()

        /** 深色模式版本：仅替换背景/文字/分隔线色，正文一致 */
        private val PRIVACY_HTML_DARK = PRIVACY_HTML
            .replace("background:#fff", "background:#12151A")
            .replace("color:#2B2F36", "color:#D5DBE1")
            .replace("color:#134E4A", "color:#2DD4BF")
            .replace("border-bottom:1px solid #F1F5F5", "border-bottom:1px solid #2A3138")
            .replace("color:#111", "color:#F1F3F5")
    }
}
