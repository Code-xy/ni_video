package com.seagull.nivod.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.seagull.nivod.ChannelActivity
import com.seagull.nivod.MainActivity
import com.seagull.nivod.MineActivity
import com.seagull.nivod.R
import com.seagull.nivod.SearchActivity

/**
 * 底栏。四个页面共用同一份布局（view_bottom_nav.xml），
 * 这里统一做「选中态上色 + 跳转」—— 之前只有图标没文字也没高亮，
 * 用户根本看不出当前在哪个页面。
 */
object Nav {

    const val HOME = 0
    const val CHANNEL = 1
    const val SEARCH = 2
    const val MINE = 3

    fun bind(act: Activity, current: Int, onReselect: (() -> Unit)? = null) {
        val items = listOf(
            Triple(R.id.nav_home, R.id.icon_home, R.id.label_home),
            Triple(R.id.nav_channel, R.id.icon_channel, R.id.label_channel),
            Triple(R.id.nav_search, R.id.icon_search, R.id.label_search),
            Triple(R.id.nav_mine, R.id.icon_mine, R.id.label_mine)
        )
        val on = ContextCompat.getColor(act, R.color.accent)
        val off = ContextCompat.getColor(act, R.color.icon_tint)

        items.forEachIndexed { i, (itemId, iconId, labelId) ->
            val sel = i == current
            val color = if (sel) on else off

            act.findViewById<ImageView>(iconId).apply {
                setColorFilter(color)
                alpha = if (sel) 1f else 0.8f
            }
            act.findViewById<TextView>(labelId).apply {
                setTextColor(color)
                alpha = if (sel) 1f else 0.8f
                setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
            }
            act.findViewById<LinearLayout>(itemId).setOnClickListener {
                if (sel) onReselect?.invoke() else go(act, i)
            }
        }
    }

    private fun go(from: Activity, target: Int) {
        val cls = when (target) {
            CHANNEL -> ChannelActivity::class.java
            SEARCH -> SearchActivity::class.java
            MINE -> MineActivity::class.java
            else -> MainActivity::class.java
        }
        if (from.javaClass == cls) return
        from.startActivity(
            Intent(from, cls).addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
    }
}
