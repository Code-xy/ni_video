package com.seagull.nivod

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.seagull.nivod.ui.Insets
import com.seagull.nivod.data.Channel
import com.seagull.nivod.data.ListCache
import com.seagull.nivod.data.Region
import com.seagull.nivod.data.ShowType
import com.seagull.nivod.data.Site
import com.seagull.nivod.data.Video
import com.seagull.nivod.ui.CardMenu
import com.seagull.nivod.ui.VideoAdapter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 频道 + 筛选。
 * 实测站点没有分页（filter/search 各恒定 48 条），
 * 所以这里用 region × showtype 组合来翻找内容 —— 这才是站点真正的浏览入口。
 */
class ChannelActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var bar: ProgressBar
    private lateinit var adapter: VideoAdapter
    private lateinit var head: TextView

    private val pool = Executors.newFixedThreadPool(2)
    private val busy = AtomicBoolean(false)

    private var channel: Channel = Channel.MOVIE
    private var region: Region = Region.ALL
    private var type: ShowType = ShowType.ALL

    private val playerResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val d = r.data ?: return@registerForActivityResult
            val pos = d.getLongExtra(PlayerActivity.RES_POS, 0L)
            val ep = d.getStringExtra(PlayerActivity.RES_EP).orEmpty()
            val v = lastVideo
            if (v != null && pos > 3000) { History.save(this, v, ep, pos); adapter.notifyDataSetChanged() }
        }
    private var lastVideo: Video? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_channel)

        Insets.pad(findViewById(R.id.root))

        list = findViewById(R.id.list)
        swipe = findViewById(R.id.swipe)
        bar = findViewById(R.id.bar)
        head = findViewById(R.id.head)

        adapter = VideoAdapter(
            onClick = { openDetail(it) },
            onLongClick = { CardMenu.show(this, it, onPlay = { openDetail(it) }) }
        )
        list.layoutManager = GridLayoutManager(this, 3)
        list.adapter = adapter
        list.setHasFixedSize(true)
        list.itemAnimator = null

        swipe.setColorSchemeColors(0xFF3DDC97.toInt())
        swipe.setOnRefreshListener { ListCache.drop(key()); load() }

        findViewById<ImageButton>(R.id.back).setOnClickListener { finish() }

        buildChips(findViewById(R.id.channelRow), Channel.values().map { it.label to it.slug }) { i ->
            channel = Channel.values()[i]; afterFilterChange()
        }
        buildChips(findViewById(R.id.regionRow), Region.values().map { it.label to it.slug }) { i ->
            region = Region.values()[i]; afterFilterChange()
        }
        buildChips(findViewById(R.id.typeRow), ShowType.values().map { it.label to it.slug }) { i ->
            type = ShowType.values()[i]; afterFilterChange()
        }
        paintChips()

        val cached = ListCache.get(key())
        if (cached != null && cached.items.isNotEmpty()) {
            adapter.add(cached.items, false)
            list.scrollToPosition(cached.scroll)
        } else load()
    }

    override fun onPause() {
        if (adapter.size() > 0) {
            val lm = list.layoutManager as? GridLayoutManager
            ListCache.put(key(), ListCache.Entry(adapter.items(), lm?.findFirstVisibleItemPosition() ?: 0))
        }
        super.onPause()
    }

    override fun onDestroy() { pool.shutdownNow(); super.onDestroy() }

    private fun key() = "ch_${channel.name}_${region.name}_${type.name}"

    // ───── 标签 ─────

    private fun buildChips(row: LinearLayout, data: List<Pair<String, String>>, onPick: (Int) -> Unit) {
        row.removeAllViews()
        for ((i, d) in data.withIndex()) {
            val tv = TextView(this)
            tv.text = d.first
            tv.setTextSize(13f)
            tv.gravity = android.view.Gravity.CENTER
            tv.setPadding(36, 0, 36, 0)
            tv.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(6, 4, 6, 4) }
            tv.setBackgroundResource(R.drawable.word_bg)
            tv.setOnClickListener { onPick(i); paintChips() }
            row.addView(tv)
        }
    }

    private fun paintChips() {
        paintRow(findViewById(R.id.channelRow), Channel.values().indexOf(channel))
        paintRow(findViewById(R.id.regionRow), Region.values().indexOf(region))
        paintRow(findViewById(R.id.typeRow), ShowType.values().indexOf(type))
        head.text = channel.label
    }

    private fun paintRow(row: LinearLayout, active: Int) {
        for (i in 0 until row.childCount) {
            val tv = row.getChildAt(i) as? TextView ?: continue
            val on = i == active
            tv.setTextColor(if (on) 0xFF3DDC97.toInt() else 0xAAFFFFFF.toInt())
            tv.alpha = if (on) 1f else 0.65f
        }
    }

    private fun afterFilterChange() {
        ListCache.drop(key())
        adapter.add(emptyList(), false)
        load()
    }

    // ───── 加载 ─────

    private fun load() {
        if (!busy.compareAndSet(false, true)) return
        bar.visibility = View.VISIBLE
        val ch = channel; val rg = region; val tp = type
        pool.execute {
            val res: List<Video>? = try {
                if (rg == Region.ALL && tp == ShowType.ALL) Site.channel(ch)
                else Site.filter(ch, rg, tp)
            } catch (e: Exception) { null }
            runOnUiThread {
                busy.set(false)
                bar.visibility = View.GONE
                swipe.isRefreshing = false
                when {
                    res == null -> tip(getString(R.string.load_fail))
                    res.isEmpty() -> { adapter.add(emptyList(), false); tip(getString(R.string.no_result)) }
                    else -> adapter.add(res, false)
                }
            }
        }
    }

    private fun openDetail(v: Video) {
        lastVideo = v
        startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, v.id))
    }

    private fun tip(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
