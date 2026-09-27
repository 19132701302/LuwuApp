package com.luwu.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import com.luwu.app.R
import com.luwu.app.api.Announcement
import com.luwu.app.util.Prefs
import com.luwu.app.util.Util

/** 开屏公告弹窗（品牌化卡片） */
object AnnouncementDialog {

    fun show(context: Context, ann: Announcement) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_announcement)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        // 主题色渐变头部条（背景：整卡主题色渐变）
        val themeColor = Prefs.getThemeColor(context)
        val grad = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(themeColor, darken(themeColor)),
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.findViewById<TextView>(R.id.tv_ann_title).text =
            ann.title.ifBlank { "网站公告" }
        dialog.findViewById<TextView>(R.id.tv_ann_content).text =
            ann.content.ifBlank { "暂无内容" }

        val linkBtn = dialog.findViewById<TextView>(R.id.btn_ann_link)
        val okBtn = dialog.findViewById<TextView>(R.id.btn_ann_ok)
        // 按钮跟随主题色
        linkBtn.background = GradientDrawable().apply {
            cornerRadius = 23f * context.resources.displayMetrics.density
            colors = intArrayOf(themeColor, darken(themeColor))
        }
        okBtn.setTextColor(themeColor)
        (okBtn.background.mutate() as? GradientDrawable)?.setStroke(
            (1.5f * context.resources.displayMetrics.density).toInt(), themeColor
        )

        if (ann.link.isNotBlank()) {
            linkBtn.visibility = View.VISIBLE
            linkBtn.setOnClickListener {
                dialog.dismiss()
                Util.openBrowser(context, ann.link)
            }
        }
        okBtn.setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }

        dialog.show()
        val win = dialog.window ?: return
        val dm = context.resources.displayMetrics
        win.setLayout((dm.widthPixels * 0.86f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        win.setGravity(Gravity.CENTER)
    }

    private fun darken(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * 0.82f).coerceAtLeast(0f)
        return Color.HSVToColor(hsv)
    }
}
