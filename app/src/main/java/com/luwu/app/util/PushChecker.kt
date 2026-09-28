package com.luwu.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.luwu.app.MainActivity
import com.luwu.app.R
import com.luwu.app.api.ApiClient
import org.json.JSONObject

/**
 * 后台推送接收器（无第三方推送 SDK，自闭环）
 *
 * App 启动时检查一次，之后每 15 分钟轮询一次 `api=push`；
 * 服务器后台新发的推送会在通知栏展示系统通知，点击后跳转对应文章 / 分类 / 链接。
 */
object PushChecker {

    private const val POLL_INTERVAL_MS = 15 * 60 * 1000L
    private const val CHANNEL_ID = "luwu_push"
    private const val MAX_NOTIFY_PER_ROUND = 3

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    /** 启动轮询（应用 onCreate 调用；重复调用自动忽略） */
    fun start(context: Context) {
        if (running) return
        running = true
        checkNow(context)
        handler.postDelayed(pollTask(context), POLL_INTERVAL_MS)
    }

    private fun pollTask(context: Context) = object : Runnable {
        override fun run() {
            checkNow(context)
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    /** 立即拉取一次推送（供设置页开关测试用） */
    fun checkNow(context: Context) {
        if (!Prefs.isPushEnabled(context)) return
        ApiClient.get("push") { json, err ->
            if (json == null || !json.optBoolean("ok", false)) return@get
            val items = json.optJSONArray("items") ?: return@get
            var last = Prefs.getLastPushId(context)
            var notified = 0
            // items 按 id 降序，从新到旧处理；仅展示未读（id > last）
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                val id = it.optInt("id", 0)
                if (id <= 0 || id <= last) continue
                if (notified >= MAX_NOTIFY_PER_ROUND) break
                notifyPush(context, it)
                if (id > last) last = id
                notified++
            }
            if (last > Prefs.getLastPushId(context)) {
                Prefs.setLastPushId(context, last)
            }
        }
    }

    /** 展示系统通知 */
    private fun notifyPush(context: Context, item: JSONObject) {
        val title = item.optString("title", "陆伍博客")
        val content = item.optString("content", "")
        val targetType = item.optString("target_type", "none")
        val targetId = item.optInt("target_id", 0)
        val targetUrl = item.optString("target_url", "")

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "陆伍推送", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "网站后台推送的重要通知"
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("push_target_type", targetType)
            putExtra("push_target_id", targetId)
            putExtra("push_target_url", targetUrl)
            putExtra("push_from", true)
        }
        val pi = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() and 0x7fffffff).toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content.ifBlank { "陆伍博客有新消息" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.ifBlank { "陆伍博客有新消息" }))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            nm.notify((item.optInt("id", 0)) and 0x7fffffff, notification)
        } catch (_: Exception) {
            // 权限被拒等场景静默跳过
        }
    }
}
