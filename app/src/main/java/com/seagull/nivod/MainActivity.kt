package com.seagull.nivod

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.seagull.nivod.ui.Insets
import com.seagull.nivod.data.ListCache
import com.seagull.nivod.data.Site
import com.seagull.nivod.data.Video
import com.seagull.nivod.ui.CardMenu
import com.seagull.nivod.ui.VideoAdapter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** 首页：站点 / 的 72 张卡片（无分页，一次拉完） */
class MainActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var bar: ProgressBar
    private lateinit var adapter: VideoAdapter

    private val pool = Executors.newFixedThreadPool(2)
    private val busy = AtomicBoolean(false)
    private val KEY = "home"

    private val playerResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val d = r.data ?: return@registerForActivityResult
            val id = d.getStringExtra(PlayerActivity.RES_ID).orEmpty()
            val ep = d.getStringExtra(PlayerActivity.RES_EP).orEmpty()
            val pos = d.getLongExtra(PlayerActivity.RES_POS, 0L)
            val v = lastVideo
            if (v != null && id.isNotEmpty() && pos > 3000) {
                History.save(this, v, ep, pos)
                adapter.notifyDataSetChanged()
            }
        }
    private var lastVideo: Video? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)

        Insets.pad(findViewById(R.id.root))

        list = findViewById(R.id.list)
        swipe = findViewById(R.id.swipe)
        bar = findViewById(R.id.bar)

        adapter = VideoAdapter(
            onClick = { openDetail(it) },
            onLongClick = { CardMenu.show(this, it, onPlay = { openDetail(it) }) }
        )
        list.layoutManager = GridLayoutManager(this, 3)
        list.adapter = adapter
        list.setHasFixedSize(true)
        list.itemAnimator = null

        swipe.setColorSchemeColors(0xFF3DDC97.toInt())
        swipe.setOnRefreshListener {
            ListCache.drop(KEY)
            load()
        }

        findViewById<ImageButton>(R.id.nav_home).setOnClickListener { list.scrollToPosition(0) }
        findViewById<ImageButton>(R.id.nav_channel).setOnClickListener {
            startActivity(Intent(this, ChannelActivity::class.java))
        }
        findViewById<ImageButton>(R.id.nav_search).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        findViewById<ImageButton>(R.id.nav_mine).setOnClickListener {
            startActivity(Intent(this, MineActivity::class.java))
        }

        val cached = ListCache.get(KEY)
        if (cached != null && cached.items.isNotEmpty()) {
            adapter.add(cached.items, false)
            list.scrollToPosition(cached.scroll)
        } else {
            load()
        }
    }

    override fun onPause() {
        if (adapter.size() > 0) {
            val lm = list.layoutManager as? GridLayoutManager
            ListCache.put(KEY, ListCache.Entry(adapter.items(), lm?.findFirstVisibleItemPosition() ?: 0))
        }
        super.onPause()
    }

    override fun onDestroy() { pool.shutdownNow(); super.onDestroy() }

    private fun load() {
        if (!busy.compareAndSet(false, true)) return
        bar.visibility = View.VISIBLE
        pool.execute {
            val res: List<Video>? = try { Site.home() } catch (e: Exception) { null }
            runOnUiThread {
                busy.set(false)
                bar.visibility = View.GONE
                swipe.isRefreshing = false
                if (res == null) { tip(getString(R.string.load_fail)); return@runOnUiThread }
                adapter.add(res, false)
            }
        }
    }

    private fun openDetail(v: Video) {
        lastVideo = v
        startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, v.id))
    }

    private fun tip(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
