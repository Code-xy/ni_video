package com.seagull.nivod.ui

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 系统栏内边距。
 *
 * targetSdk 35 下窗口是 edge-to-edge 的（内容从 y=0 开始画），
 * 不处理就会被状态栏/挖孔/导航栏压住。这里统一把 systemBars inset 变成 padding。
 *
 * 若窗口本身已经 fitsSystemWindows（非 edge-to-edge），传下来的 inset 会是 0，
 * 这里加 0 等于无操作 —— 所以两种情况下都安全。
 */
object Insets {

    fun pad(root: View, top: Boolean = true, bottom: Boolean = true, ime: Boolean = false) {
        val baseTop = root.paddingTop
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeBottom =
                if (ime) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
            v.setPadding(
                v.paddingLeft,
                if (top) baseTop + bars.top else v.paddingTop,
                v.paddingRight,
                if (bottom) baseBottom + maxOf(bars.bottom, imeBottom) else v.paddingBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /** 给某个控件加「基础外边距 + 状态栏高度」的上边距（播放页叠加条这种固定高度的控件用） */
    fun marginTop(v: View, baseDp: Int) {
        val base = (baseDp * v.resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = view.layoutParams
            if (lp is ViewGroup.MarginLayoutParams) {
                lp.topMargin = base + bars.top
                view.layoutParams = lp
            }
            insets
        }
        ViewCompat.requestApplyInsets(v)
    }
}
