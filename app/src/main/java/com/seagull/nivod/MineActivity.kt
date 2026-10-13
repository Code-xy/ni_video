package com.seagull.nivod

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.seagull.nivod.ui.Insets
import com.seagull.nivod.data.FavStore
import com.seagull.nivod.data.Site
import com.seagull.nivod.data.Video
import com.seagull.nivod.ui.CardMenu
import com.seagull.nivod.ui.VideoAdapter
import com.seagull.nivod.ui.Nav
import android.graphics.Typeface

/** 「我的」：播放历史 + 收藏 */
class MineActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var tabHistory: TextView
    private lateinit var tabFav: TextView
    private lateinit var adapter: VideoAdapter

    private var showFav = false
    private var lastVideo: Video? = null

    private val playerResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val d = r.data ?: return@registerForActivityResult
            val ep = d.getStringExtra(PlayerActivity.RES_EP).orEmpty()
            val pos = d.getLongExtra(PlayerActivity.RES_POS, 0L)
            val v = lastVideo
            if (v != null && pos > 3000) History.save(this, v, ep, pos)
            regenerate()
        }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_mine)

        Insets.pad(findViewById(R.id.root))

        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        tabHistory = findViewById(R.id.tab_history)
        tabFav = findViewById(R.id.tab_fav)

        showFav = s?.getBoolean("showFav", false) ?: false

        adapter = VideoAdapter(
            onClick = { openDetail(it) },
            onLongClick = { onCard(it) }
        )
        list.layoutManager = GridLayoutManager(this, 3)
        list.adapter = adapter
        list.setHasFixedSize(true)
        list.itemAnimator = null

        tabHistory.setOnClickListener { switchTab(false) }
        tabFav.setOnClickListener { switchTab(true) }

        findViewById<ImageButton>(R.id.back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.web).setOnClickListener {
            startActivity(Intent(this, WebActivity::class.java).putExtra(WebActivity.EXTRA_URL, Site.BASE))
        }
        findViewById<ImageButton>(R.id.clear).setOnClickListener { confirmClear() }

        // 明暗外观
        findViewById<TextView>(R.id.theme_system).setOnClickListener { pickTheme(ThemePref.FOLLOW) }
        findViewById<TextView>(R.id.theme_light).setOnClickListener { pickTheme(ThemePref.LIGHT) }
        findViewById<TextView>(R.id.theme_dark).setOnClickListener { pickTheme(ThemePref.DARK) }
        paintThemeChips()

        Nav.bind(this, Nav.MINE)

        switchTab(showFav)
    }

    private fun pickTheme(mode: Int) {
        ThemePref.set(this, mode)
        // 双保险：正常情况下 setDefaultNightMode 会自动重建当前 Activity，
        // 但部分 ROM 不重建，用户就会觉得「必须重启才生效」。
        recreate()
    }

    private fun paintThemeChips() {
        val cur = ThemePref.mode(this)
        val onC = ContextCompat.getColor(this, R.color.on_accent_container)
        val off = ContextCompat.getColor(this, R.color.text_secondary)
        listOf(
            R.id.theme_system to ThemePref.FOLLOW,
            R.id.theme_light to ThemePref.LIGHT,
            R.id.theme_dark to ThemePref.DARK
        ).forEach { (id, mode) ->
            findViewById<TextView>(id).apply {
                val sel = cur == mode
                setBackgroundResource(if (sel) R.drawable.word_bg_on else R.drawable.word_bg)
                setTextColor(if (sel) onC else off)
                setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("showFav", showFav)
    }

    private fun switchTab(fav: Boolean) {
        showFav = fav
        // ★ 这里原来是写死的 0x99FFFFFF —— 浅色模式下白字压白底，收藏 tab 直接隐形
        val tabOn = ContextCompat.getColor(this, R.color.accent)
        val tabOff = ContextCompat.getColor(this, R.color.text_secondary)
        tabHistory.setTextColor(if (!showFav) tabOn else tabOff)
        tabFav.setTextColor(if (showFav) tabOn else tabOff)
        tabHistory.setTypeface(null, if (!showFav) Typeface.BOLD else Typeface.NORMAL)
        tabFav.setTypeface(null, if (showFav) Typeface.BOLD else Typeface.NORMAL)
        regenerate()
    }

    private fun regenerate() {
        val items = if (showFav) FavStore.list(this) else History.list(this).map { it.video }
        adapter.add(items, false)
        empty.text = getString(if (showFav) R.string.empty_fav else R.string.empty_history)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openDetail(v: Video) {
        lastVideo = v
        startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, v.id))
    }

    private fun onCard(v: Video) {
        CardMenu.show(this, v, onPlay = { openDetail(v) }, onRemove = {
            if (showFav) FavStore.remove(this, v.id) else History.remove(this, v.id)
            regenerate()
        })
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_clear)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (showFav) FavStore.clear(this) else History.clear(this)
                regenerate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun tip(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
