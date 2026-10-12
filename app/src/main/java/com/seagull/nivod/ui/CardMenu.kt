package com.seagull.nivod.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.seagull.nivod.R
import com.seagull.nivod.WebActivity
import com.seagull.nivod.data.FavStore
import com.seagull.nivod.data.Site
import com.seagull.nivod.data.Video

/** 长按卡片的操作菜单（收藏入口） */
object CardMenu {

    fun detailUrl(id: String) = Site.BASE + "voddetail/" + id

    fun show(act: Activity, v: Video, onPlay: () -> Unit, onRemove: (() -> Unit)? = null) {
        val fav = FavStore.has(act, v.id)
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()

        labels.add(act.getString(R.string.play_now)); acts.add { onPlay() }
        labels.add(act.getString(if (fav) R.string.mn_unfav else R.string.mn_fav)); acts.add {
            val now = FavStore.toggle(act, v)
            Toast.makeText(act, act.getString(if (now) R.string.fav_added else R.string.fav_removed), Toast.LENGTH_SHORT).show()
        }
        if (onRemove != null) {
            labels.add(act.getString(R.string.mn_remove_hist)); acts.add { onRemove() }
        }
        labels.add(act.getString(R.string.mn_web)); acts.add { openWeb(act, v.id) }
        labels.add(act.getString(R.string.mn_copy)); acts.add { copy(act, v.id) }

        AlertDialog.Builder(act)
            .setTitle(v.title)
            .setItems(labels.toTypedArray()) { _, i -> acts[i]() }
            .show()
    }

    fun openWeb(act: Activity, id: String) {
        act.startActivity(
            Intent(act, WebActivity::class.java).putExtra(WebActivity.EXTRA_URL, detailUrl(id))
        )
    }

    private fun copy(act: Activity, id: String) {
        val u = detailUrl(id)
        (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("url", u))
        Toast.makeText(act, act.getString(R.string.copied) + ": " + u, Toast.LENGTH_SHORT).show()
    }
}
