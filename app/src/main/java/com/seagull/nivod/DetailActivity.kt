package com.seagull.nivod

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import coil.load
import com.seagull.nivod.ui.Insets
import com.seagull.nivod.data.Detail
import com.seagull.nivod.data.Episode
import com.seagull.nivod.data.FavStore
import com.seagull.nivod.data.Site
import com.seagull.nivod.data.Video
import com.seagull.nivod.ui.CardMenu
import com.seagull.nivod.ui.PlayerLauncher
import java.util.concurrent.Executors

/**
 * 详情页：元数据 + 选集。
 * 选集用代码铺行（每行 5 个），避免 ScrollView 套 RecyclerView 的嵌套滚动坑。
 */
class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "id"
        private const val COLUMNS = 5
    }

    private lateinit var bar: ProgressBar
    private lateinit var cover: android.widget.ImageView
    private lateinit var title: TextView
    private lateinit var score: TextView
    private lateinit var meta1: TextView
    private lateinit var meta2: TextView
    private lateinit var meta3: TextView
    private lateinit var desc: TextView
    private lateinit var epBox: LinearLayout
    private lateinit var head: TextView
    private lateinit var favBtn: ImageButton

    private val pool = Executors.newFixedThreadPool(2)
    private var detail: Detail? = null
    private var selectedEp: String = ""

    private val playerResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val d = r.data ?: return@registerForActivityResult
            val ep = d.getStringExtra(PlayerActivity.RES_EP).orEmpty()
            val pos = d.getLongExtra(PlayerActivity.RES_POS, 0L)
            val dt = detail
            if (dt != null && pos > 3000) {
                History.save(this, toVideo(dt), ep, pos)
            }
        }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_detail)

        Insets.pad(findViewById(R.id.root))

        bar = findViewById(R.id.bar)
        cover = findViewById(R.id.cover)
        title = findViewById(R.id.title)
        score = findViewById(R.id.score)
        meta1 = findViewById(R.id.meta1)
        meta2 = findViewById(R.id.meta2)
        meta3 = findViewById(R.id.meta3)
        desc = findViewById(R.id.desc)
        epBox = findViewById(R.id.epBox)
        head = findViewById(R.id.head)
        favBtn = findViewById(R.id.fav)

        val id = intent.getStringExtra(EXTRA_ID).orEmpty()

        findViewById<ImageButton>(R.id.back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.web).setOnClickListener {
            startActivity(Intent(this, WebActivity::class.java)
                .putExtra(WebActivity.EXTRA_URL, CardMenu.detailUrl(id)))
        }
        findViewById<android.widget.Button>(R.id.play).setOnClickListener { playSelected() }

        head.text = getString(R.string.loading)
        load(id)
    }

    override fun onDestroy() { pool.shutdownNow(); super.onDestroy() }

    private fun toVideo(d: Detail) = Video(
        id = d.id, title = d.title, cover = d.cover,
        sub = listOf(d.year, d.region).filter { it.isNotBlank() }.joinToString(" · "),
        badge = d.update, score = d.score
    )

    private fun load(id: String) {
        bar.visibility = View.VISIBLE
        pool.execute {
            val d: Detail? = try { Site.detail(id) } catch (e: Exception) { null }
            runOnUiThread {
                bar.visibility = View.GONE
                if (d == null) { tip(getString(R.string.load_fail)); head.text = ""; return@runOnUiThread }
                detail = d
                bind(d)
            }
        }
    }

    private fun bind(d: Detail) {
        head.text = d.title
        title.text = d.title
        cover.load(d.cover) { crossfade(true); placeholder(R.drawable.ph_thumb) }

        score.text = if (d.score.isBlank()) "" else "评分 ${d.score}"
        score.visibility = if (d.score.isBlank()) View.GONE else View.VISIBLE

        meta1.text = listOf(d.year, d.region, d.types).filter { it.isNotBlank() }.joinToString(" · ")
        meta2.text = if (d.director.isBlank()) "" else "导演：${d.director}"
        meta3.text = buildString {
            if (d.actors.isNotBlank()) append("主演：").append(d.actors)
            if (d.lang.isNotBlank()) { if (isNotEmpty()) append("\n"); append("语言：").append(d.lang) }
            if (d.reso.isNotBlank()) { if (isNotEmpty()) append("　"); append(d.reso) }
            if (d.update.isNotBlank()) { if (isNotEmpty()) append("　"); append(d.update) }
        }
        desc.text = d.desc
        desc.visibility = if (d.desc.isBlank()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.descLabel).visibility = desc.visibility

        paintFav()

        // 上次看到哪一集就默认选哪一集
        val last = History.last(this, d.id)
        selectedEp = last?.first?.takeIf { it.isNotBlank() && d.episodes.any { e -> e.slug == it } }
            ?: d.episodes.firstOrNull()?.slug.orEmpty()

        buildEpisodes(d.episodes)
    }

    /** 每行 5 个铺出来 */
    private fun buildEpisodes(eps: List<Episode>) {
        epBox.removeAllViews()
        if (eps.isEmpty()) {
            epBox.visibility = View.GONE
            return
        }
        epBox.visibility = View.VISIBLE
        var row: LinearLayout? = null
        for ((i, e) in eps.withIndex()) {
            if (i % COLUMNS == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                epBox.addView(row)
            }
            val tv = TextView(this)
            tv.text = e.label
            tv.setTextSize(13f)
            tv.gravity = Gravity.CENTER
            tv.setPadding(0, 22, 0, 22)
            tv.setBackgroundResource(R.drawable.word_bg)
            tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(4, 4, 4, 4) }
            tv.isSelected = e.slug == selectedEp
            tv.setTextColor(if (e.slug == selectedEp) 0xFF3DDC97.toInt() else 0xDDDDDDDD.toInt())
            tv.setOnClickListener {
                selectedEp = e.slug
                buildEpisodes(eps)
                playSelected()
            }
            row?.addView(tv)
        }
    }

    private fun playSelected() {
        val d = detail ?: return
        if (selectedEp.isBlank()) { tip(getString(R.string.no_result)); return }
        PlayerLauncher.open(
            this, toVideo(d), selectedEp, d.episodes,
            playerResult, busy = ::setBusy, onFail = ::onPlayFail
        )
    }

    private fun onPlayFail(v: Video) {
        tip(getString(R.string.parse_fail))
        CardMenu.openWeb(this, v.id)
    }

    private fun setBusy(b: Boolean) { bar.visibility = if (b) View.VISIBLE else View.GONE }

    private fun paintFav() {
        val d = detail ?: return
        val on = FavStore.has(this, d.id)
        favBtn.alpha = if (on) 1f else 0.45f
        favBtn.setColorFilter(ContextCompat.getColor(this, if (on) R.color.accent else R.color.icon_tint))
        favBtn.setOnClickListener {
            val now = FavStore.toggle(this, toVideo(d))
            favBtn.alpha = if (now) 1f else 0.45f
            favBtn.setColorFilter(ContextCompat.getColor(this, if (now) R.color.accent else R.color.icon_tint))
            tip(getString(if (now) R.string.fav_added else R.string.fav_removed))
        }
    }

    private fun tip(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
