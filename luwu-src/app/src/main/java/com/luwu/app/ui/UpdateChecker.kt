package com.luwu.app.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.luwu.app.R
import com.luwu.app.api.ApiClient
import com.luwu.app.api.UpdateInfo
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * App 版本更新：检测 → 弹窗 → 自建下载（实时进度条/网速）→ 自动拉起安装
 * 修复旧版「提示下载失败但实际已下载成功」的误报：只有真正失败才提示
 */
object UpdateChecker {

    private var downloading = false

    fun check(context: Context, manual: Boolean = false) {
        ApiClient.get("update") { json, err ->
            if (json == null || !json.optBoolean("ok", false)) {
                if (manual) Toast.makeText(context, err ?: "检查更新失败", Toast.LENGTH_SHORT).show()
                return@get
            }
            val info = UpdateInfo.fromJson(json)
            if (!info.enabled || info.url.isBlank()) {
                if (manual) Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                return@get
            }
            val current = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            } catch (e: Exception) {
                return@get
            }
            if (info.versionCode <= current) {
                if (manual) Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                return@get
            }
            showUpdateDialog(context, info)
        }
    }

    private fun showUpdateDialog(context: Context, info: UpdateInfo) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("发现新版本 v${info.versionName}")
        builder.setMessage(if (info.changelog.isBlank()) "有新版本可用，是否更新？" else info.changelog)
        builder.setPositiveButton("立即更新") { _, _ -> download(context, info) }
        if (!info.force) {
            builder.setNegativeButton("稍后再说", null)
        } else {
            builder.setCancelable(false)
        }
        builder.show()
    }

    private fun download(context: Context, info: UpdateInfo) {
        if (downloading) {
            Toast.makeText(context, "正在下载中，请稍候…", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("正在下载 v${info.versionName}")
            .setCancelable(false)
            .create()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_update_progress, null)
        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val tvPercent = view.findViewById<TextView>(R.id.tv_percent)
        val tvSpeed = view.findViewById<TextView>(R.id.tv_speed)
        dialog.setView(view)
        dialog.show()
        downloading = true

        val target = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "luwu-app-${info.versionName}.apk"
        )
        Executors.newSingleThreadExecutor().execute {
            var success = false
            var errorMsg = "网络异常"
            try {
                val conn = URL(info.url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                val total = conn.contentLength.toLong().coerceAtLeast(0L)
                val input = conn.inputStream
                val out = FileOutputStream(target)
                val buf = ByteArray(8192)
                var read: Int
                var done = 0L
                var lastTick = System.currentTimeMillis()
                var lastBytes = 0L
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    done += read
                    val now = System.currentTimeMillis()
                    if (now - lastTick > 400) {
                        val speed = (done - lastBytes) * 1000 / (now - lastTick)
                        lastTick = now
                        lastBytes = done
                        updateUi(progress, tvPercent, tvSpeed, done, total, speed)
                    }
                }
                out.flush()
                out.close()
                input.close()
                success = target.length() > 0
            } catch (e: Exception) {
                errorMsg = e.message ?: "网络异常"
            }
            val ok = success
            Handler(Looper.getMainLooper()).post {
                downloading = false
                try { if (dialog.isShowing) dialog.dismiss() } catch (e: Exception) {}
                if (ok) {
                    Toast.makeText(context, "下载完成，正在安装…", Toast.LENGTH_SHORT).show()
                    installApk(context, target)
                } else {
                    target.delete()
                    AlertDialog.Builder(context)
                        .setTitle("下载失败")
                        .setMessage("下载未完成，请检查网络后重试。\n原因：$errorMsg")
                        .setPositiveButton("重试") { _, _ -> download(context, info) }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        }
    }

    private fun updateUi(progress: ProgressBar?, tvPercent: TextView?, tvSpeed: TextView?, done: Long, total: Long, speed: Long) {
        Handler(Looper.getMainLooper()).post {
            val pct = if (total > 0) (done * 100 / total).toInt() else 0
            progress?.max = 100
            progress?.progress = pct
            tvPercent?.text = if (total > 0) "$pct%" else "${done / 1024 / 1024}MB"
            tvSpeed?.text = if (speed > 0) "${speed / 1024} KB/s" else "连接中…"
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "com.luwu.app.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "安装失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
