package com.luwu.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * 简易流式换行布局：子 View 按宽度自动换行（标签云/搜索热词/历史词条专用）
 * 商业 App 热门搜索/搜索历史的标准呈现方式
 */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    private val lines = mutableListOf<MutableList<View>>()
    private val lineHeights = mutableListOf<Int>()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val padL = paddingLeft
        val padR = paddingRight
        val padT = paddingTop
        val padB = paddingBottom
        val childWidthSpec = MeasureSpec.makeMeasureSpec(
            width - padL - padR,
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.AT_MOST else MeasureSpec.getMode(widthMeasureSpec),
        )
        lines.clear()
        lineHeights.clear()
        var line = mutableListOf<View>()
        var lineW = 0
        var lineH = 0
        var totalH = padT + padB
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            measureChild(child, childWidthSpec, heightMeasureSpec)
            val lp = child.layoutParams as LayoutParams
            val cw = child.measuredWidth + lp.leftMargin + lp.rightMargin
            val ch = child.measuredHeight + lp.topMargin + lp.bottomMargin
            if (lineW + cw > width - padL - padR && line.isNotEmpty()) {
                lines.add(line)
                lineHeights.add(lineH)
                totalH += lineH
                line = mutableListOf()
                lineW = 0
                lineH = 0
            }
            line.add(child)
            lineW += cw
            lineH = maxOf(lineH, ch)
        }
        if (line.isNotEmpty()) {
            lines.add(line)
            lineHeights.add(lineH)
            totalH += lineH
        }
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(totalH, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val padL = paddingLeft
        val padT = paddingTop
        var y = padT
        for ((idx, line) in lines.withIndex()) {
            var x = padL
            for (child in line) {
                val lp = child.layoutParams as LayoutParams
                x += lp.leftMargin
                child.layout(x, y + lp.topMargin, x + child.measuredWidth, y + lp.topMargin + child.measuredHeight)
                x += child.measuredWidth + lp.rightMargin
            }
            y += lineHeights[idx]
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = LayoutParams(context, attrs)

    override fun generateDefaultLayoutParams(): LayoutParams = LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    class LayoutParams : MarginLayoutParams {
        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
        constructor(w: Int, h: Int) : super(w, h)
    }
}
