package com.luwu.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.luwu.app.R
import com.luwu.app.api.ApiClient
import com.luwu.app.util.AppState
import com.luwu.app.util.Prefs
import com.luwu.app.util.Util

class ProfileFragment : Fragment() {

    private var tvName: TextView? = null
    private var tvHint: TextView? = null
    private var tvAvatar: TextView? = null
    private var avatarLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null
    private var btnLogout: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvName = view.findViewById(R.id.tv_login_name)
        tvHint = view.findViewById(R.id.tv_login_hint)
        tvAvatar = view.findViewById(R.id.tv_login_avatar)
        btnLogout = view.findViewById(R.id.btn_logout)
        // 退出登录：清除本地登录态 + 通知服务端删除 token
        btnLogout?.setOnClickListener { doLogout() }
        // 头像选择器：必须在此处注册（点击时再注册会闪退）
        avatarLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) uploadAvatar(uri)
        }

        view.findViewById<View>(R.id.tv_login_avatar).setOnClickListener { handleAvatarTap() }
        view.findViewById<View>(R.id.iv_login_avatar_wrap).setOnClickListener { handleAvatarTap() }
        view.findViewById<View>(R.id.iv_login_avatar).setOnClickListener { handleAvatarTap() }
        view.findViewById<View>(R.id.tv_login_name).setOnClickListener { handleLoginTap() }
        view.findViewById<View>(R.id.menu_favorites).setOnClickListener {
            startActivity(Intent(requireContext(), FavoritesActivity::class.java))
        }
        view.findViewById<View>(R.id.menu_history).setOnClickListener {
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
        }
        view.findViewById<View>(R.id.menu_my_posts).setOnClickListener {
            if (AppState.pluginAvailable == false) {
                Util.toast(requireContext(), "该功能需要安装「陆伍App控制台」插件")
                return@setOnClickListener
            }
            if (Prefs.isLoggedIn(requireContext())) {
                startActivity(Intent(requireContext(), MyPostsActivity::class.java))
            } else {
                Util.toast(requireContext(), "请先登录")
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            }
        }
        view.findViewById<View>(R.id.menu_orders).setOnClickListener {
            if (!Prefs.isLoggedIn(requireContext())) {
                Util.toast(requireContext(), "请先登录")
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                return@setOnClickListener
            }
            startActivity(Intent(requireContext(), OrderActivity::class.java))
        }
        view.findViewById<View>(R.id.menu_publish).setOnClickListener {
            if (AppState.pluginAvailable == false) {
                Util.toast(requireContext(), "发布功能需要安装「陆伍App控制台」插件")
                return@setOnClickListener
            }
            startActivity(Intent(requireContext(), PublishActivity::class.java))
        }
        view.findViewById<View>(R.id.menu_update).setOnClickListener {
            UpdateChecker.check(requireContext(), true)
        }
        view.findViewById<View>(R.id.menu_about).setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
        view.findViewById<View>(R.id.menu_privacy).setOnClickListener {
            startActivity(Intent(requireContext(), PrivacyActivity::class.java))
        }
        view.findViewById<View>(R.id.menu_website).setOnClickListener {
            Util.openBrowser(requireContext(), "https://www.65gw.com")
        }
        view.findViewById<View>(R.id.menu_business).setOnClickListener {
            startActivity(Intent(requireContext(), BusinessCoopActivity::class.java))
        }
        view.findViewById<View>(R.id.menu_settings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        view.findViewById<View>(R.id.menu_feedback).setOnClickListener {
            startActivity(Intent(requireContext(), FeedbackActivity::class.java))
        }
        view.findViewById<View>(R.id.btn_bell)?.setOnClickListener {
            startActivity(Intent(requireContext(), NotifyActivity::class.java))
        }
        view.findViewById<View>(R.id.menu_notify).setOnClickListener {
            startActivity(Intent(requireContext(), NotifyActivity::class.java))
        }
        view.findViewById<View>(R.id.menu_follows).setOnClickListener {
            startActivity(Intent(requireContext(), FollowListActivity::class.java))
        }

        // 顶部头像卡：主题色渐变
        refreshUi()
        // 未读消息角标（评论/赞/关注）
        loadNotifyBadge()
        // 我的页数据卡：关注 / 粉丝 / 获赞
        loadProfileStats()
    }

    private fun handleAvatarTap() {
        if (!Prefs.isLoggedIn(requireContext())) {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            return
        }
        // 更换头像：弹底部菜单
        val items = arrayOf("更换头像", "取消")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("头像")
            .setItems(items) { _, which ->
                if (which == 0) pickAvatarImage()
            }
            .show()
    }

    private fun pickAvatarImage() {
        avatarLauncher?.launch("image/*")
    }

    private fun uploadAvatar(uri: android.net.Uri) {
        val ctx = requireContext()
        val path = com.luwu.app.util.Util.uriToPath(ctx, uri) ?: run {
            Util.toast(ctx, "无法读取该图片")
            return
        }
        Util.toast(ctx, "正在上传头像…")
        ApiClient.upload(
            "avatar",
            path,
            mapOf(),
        ) { json, err ->
            if (json == null || !json.optBoolean("ok", false)) {
                Util.toast(ctx, err ?: "头像上传失败")
                return@upload
            }
            val avatar = json.optString("avatar", "")
            if (avatar.isNotBlank()) {
                Prefs.setAvatar(ctx, avatar)
                refreshAvatarUi()
            loadNotifyBadge()
                Util.toast(ctx, "头像已更新")
            }
        }
    }

    private fun handleLoginTap() {
        if (Prefs.isLoggedIn(requireContext())) {
            startActivity(Intent(requireContext(), PublishActivity::class.java))
        } else {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
        }
    }

    fun onResumeRefresh() {
        view?.post {
            refreshUi()
            // 每次回到前台刷新未读角标
            loadNotifyBadge()
            // 每次回到前台刷新数据卡（关注/粉丝/获赞可能变化）
            loadProfileStats()
        }
    }

    /** 我的页数据卡：关注 / 粉丝 / 获赞（登录后展示，复用 api_user 统计） */
    private fun loadProfileStats() {
        val statsRow = view?.findViewById<View>(R.id.profile_stats) ?: return
        val uid = Prefs.getUid(requireContext())
        if (uid <= 0) {
            statsRow.visibility = View.GONE
            return
        }
        ApiClient.get("user", mapOf("uid" to uid.toString())) { json, _ ->
            val user = json?.optJSONObject("user") ?: return@get
            view?.findViewById<TextView>(R.id.stat_following)?.text = "关注 ${user.optInt("following_count", 0)}"
            view?.findViewById<TextView>(R.id.stat_fans)?.text = "粉丝 ${user.optInt("follower_count", 0)}"
            view?.findViewById<TextView>(R.id.stat_likes)?.text = "获赞 ${user.optInt("likes_received", 0)}"
            statsRow.visibility = View.VISIBLE
        }
    }

    private fun darken(color: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * 0.82f).coerceAtLeast(0f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    /** 退出登录：服务端删除 token + 本地清理登录态 */
    private fun doLogout() {
        val ctx = requireContext()
        val token = Prefs.getToken(ctx)
        if (token.isNotBlank()) {
            ApiClient.post("logout", mapOf("token" to token)) { _, _ -> }
        }
        Prefs.logout(ctx)
        Util.toast(ctx, "已退出登录")
        refreshUi()
    }

    private fun refreshUi() {
        val logged = Prefs.isLoggedIn(requireContext())
        val name = Prefs.getUserName(requireContext())
        tvName?.text = if (logged) name.ifBlank { "已登录" } else "未登录"
        tvHint?.text = if (logged) "点击头像发布文章" else "登录后可发布文章"
        tvAvatar?.text = if (logged) name.take(1).ifBlank { "陆" } else "未"
        btnLogout?.visibility = if (logged) View.VISIBLE else View.GONE
        refreshAvatarUi()
    }

    /** 未读角标：消息入口徽标 + 顶栏铃铛徽标 同步刷新 */
    private fun loadNotifyBadge() {
        val badge = view?.findViewById<TextView>(R.id.tv_notify_badge) ?: return
        val bellBadge = view?.findViewById<TextView>(R.id.tv_bell_badge)
        if (Prefs.getUid(requireContext()) <= 0) {
            badge.visibility = View.GONE
            bellBadge?.visibility = View.GONE
            return
        }
        ApiClient.post("notify", mapOf()) { json, _ ->
            val unread = json?.optInt("unread", 0) ?: 0
            val show = unread > 0
            val text = if (unread > 99) "99+" else unread.toString()
            badge.visibility = if (show) View.VISIBLE else View.GONE
            badge.text = text
            bellBadge?.visibility = badge.visibility
            bellBadge?.text = badge.text
        }
    }

    private fun refreshAvatarUi() {
        val ctx = requireContext()
        val iv = view?.findViewById<android.widget.ImageView>(R.id.iv_login_avatar)
        val tv = tvAvatar
        val avatar = Prefs.getAvatar(ctx)
        if (Prefs.isLoggedIn(ctx) && avatar.isNotBlank()) {
            tv?.visibility = View.GONE
            iv?.visibility = View.VISIBLE
            com.luwu.app.util.ImageLoader.load(avatar, iv ?: return)
        } else {
            iv?.visibility = View.GONE
            tv?.visibility = View.VISIBLE
        }
    }
}
