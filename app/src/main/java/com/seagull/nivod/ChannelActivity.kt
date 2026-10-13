package com.seagull.nivod

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.seagull.nivod.data.Channel
import com.seagull.nivod.data.ListCache
import com.seagull.nivod.data.Region
import com.seagull.nivod.data.ShowType
import com.seagull.nivod.data.Site
import com.seagull.nivod.data.Video
import com.seagull.nivod.ui.CardMenu
import com.seagull.nivod.ui.Nav
import com.seagull.nivod.ui.VideoAdapter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import com.seagull.nivod.ui.Insets
import android.util.TypedValue

/**
 * 频道 + 筛选。
 * 站点没有分页，浏览靠 region × showtype 组合 —— 但把 30 多个胶囊摊三行太挤，
 * 所以改成：顶部一排频道 tab（等分 + 下划线指示），地区和类型收进「筛选」弹窗。
 */
class ChannelActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var bar: ProgressBar
    private lateinit var adapter: VideoAdapter
    private lateinit var head: TextView
    private lateinit var subtitle: TextView
    private lateinit var filterBtn: TextView
    private lateinit var tabRow: LinearLayout

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
        subtitle = findViewById(R.id.subtitle)
        filterBtn = findViewById(R.id.filterBtn)
        tabRow = findViewById(R.id.tabRow)

        adapter = VideoAdapter(
            onClick = { openDetail(it) },
            onLongClick = { CardMenu.show(this, it, onPlay = { openDetail(it) }) }
        )
        list.layoutManager = GridLayoutManager(this, 3)
        list.adapter = adapter
        list.setHasFixedSize(true)
        list.itemAnimator = null

        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipe.setOnRefreshListener { ListCache.drop(key()); load() }

        findViewById<ImageButton>(R.id.back).setOnClickListener { finish() }
        filterBtn.setOnClickListener { showFilterDialog() }

        buildTabs()
        paintTabs()
        paintFilterBtn()

        Nav.bind(this, Nav.CHANNEL)

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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /**
     * 代码里取主题属性要先用 TypedValue 解析。
     * 直接 ContextCompat.getDrawable(this, android.R.attr.xxx) 会拿属性 ID 当资源 ID 用，
     * 抛 Resources$NotFoundException（android.R.attr.selectableItemBackgroundBorderless = 0x0101045c）。
     */
    private fun rippleBg(): android.graphics.drawable.Drawable? {
        val tv = TypedValue()
        return if (theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, tv, true
            ) && tv.resourceId != 0
        ) ContextCompat.getDrawable(this, tv.resourceId) else null
    }


    // ───── 频道 tab（等分 + 下划线指示，取代横向滚动胶囊）─────

    private fun buildTabs() {
        tabRow.removeAllViews()
        Channel.values().forEach { ch ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                isClickable = true
                background = rippleBg()
            }
            val tv = TextView(this).apply {
                text = ch.label
                gravity = Gravity.CENTER
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            val underline = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(2))
                setBackgroundColor(ContextCompat.getColor(this@ChannelActivity, R.color.accent))
            }
            col.addView(tv)
            col.addView(underline)
            col.setOnClickListener {
                if (channel == ch) return@setOnClickListener
                channel = ch
                paintTabs()
                afterFilterChange()
            }
            tabRow.addView(col)
        }
    }

    private fun paintTabs() {
        for (i in 0 until tabRow.childCount) {
            val col = tabRow.getChildAt(i) as LinearLayout
            val tv = col.getChildAt(0) as TextView
            val underline = col.getChildAt(1)
            val on = Channel.values()[i] == channel
            tv.setTextColor(ContextCompat.getColor(this, if (on) R.color.accent else R.color.text_secondary))
            tv.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            underline.visibility = if (on) View.VISIBLE else View.INVISIBLE
        }
        head.text = channel.label
        subtitle.text = listOf(channel.label, region.label, type.label)
            .distinct().joinToString(" · ")
    }

    // ───── 筛选弹窗（地区 + 类型，等分三列，松快得多）─────

    private fun showFilterDialog() {
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }
        val scroll = ScrollView(this).apply { addView(holder) }

        fun sectionTitle(t: String) = TextView(this).apply {
            text = t
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@ChannelActivity, R.color.text_tertiary))
            setPadding(dp(4), dp(14), 0, dp(2))
        }

        // 用 var 持有重建函数，避免局部函数互相前向引用
        // （局部函数 Kotlin 不做提升，之前叫 build() 还撞上了标准库的 build(builder) 重载）
        var refresh: () -> Unit = {}

        fun chipGrid(labels: List<String>, cur: Int, onPick: (Int) -> Unit): LinearLayout {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            var row: LinearLayout? = null
            labels.forEachIndexed { i, label ->
                if (i % 3 == 0) {
                    row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    box.addView(row)
                }
                val tv = TextView(this).apply {
                    text = label
                    gravity = Gravity.CENTER
                    textSize = 13f
                    layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                        setMargins(dp(3), dp(3), dp(3), dp(3))
                    }
                    setBackgroundResource(R.drawable.word_bg)
                }
                paintChip(tv, i == cur)
                tv.setOnClickListener { onPick(i); refresh() }
                row!!.addView(tv)
            }
            return box
        }

        refresh = {
            holder.removeAllViews()
            holder.addView(sectionTitle(getString(R.string.filter_region)))
            holder.addView(
                chipGrid(Region.values().map { it.label }, Region.values().indexOf(region)) { i ->
                    region = Region.values()[i]
                    afterFilterChange()
                }
            )
            holder.addView(sectionTitle(getString(R.string.filter_type)))
            holder.addView(
                chipGrid(ShowType.values().map { it.label }, ShowType.values().indexOf(type)) { i ->
                    type = ShowType.values()[i]
                    afterFilterChange()
                }
            )
        }
        refresh()

        AlertDialog.Builder(this)
            .setTitle(R.string.filter_title)
            .setView(scroll)
            .setPositiveButton(R.string.filter_done, null)
            .setNeutralButton(R.string.filter_reset) { _, _ ->
                region = Region.ALL
                type = ShowType.ALL
                paintFilterBtn()
                afterFilterChange()
                refresh()
            }
            .show()
    }

    private fun paintChip(tv: TextView, on: Boolean) {
        tv.setTextColor(ContextCompat.getColor(this, if (on) R.color.accent else R.color.text_secondary))
        tv.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
        tv.alpha = if (on) 1f else 0.85f
    }

    private fun paintFilterBtn() {
        val n = (if (region != Region.ALL) 1 else 0) + (if (type != ShowType.ALL) 1 else 0)
        filterBtn.text = if (n == 0) getString(R.string.filter) else "${getString(R.string.filter)} · $n"
        filterBtn.setTextColor(ContextCompat.getColor(this, if (n > 0) R.color.accent else R.color.text_secondary))
    }

    private fun afterFilterChange() {
        paintFilterBtn()
        paintTabs()
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
