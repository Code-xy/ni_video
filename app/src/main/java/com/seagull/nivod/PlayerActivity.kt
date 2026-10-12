package com.seagull.nivod

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.seagull.nivod.data.FavStore
import com.seagull.nivod.data.Site
import com.seagull.nivod.data.Video
import com.seagull.nivod.ui.PlayerLauncher
import com.shuyu.gsyvideoplayer.utils.OrientationUtils
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import java.util.concurrent.Executors

/**
 * 播放页。
 * - 多线路：站点每分集给 3~12 条，launcher 已探活选好，这里可随时手动换
 * - 选集 / 上一集 / 下一集 / 选集正序倒序
 * - 倍速 / 收藏 / 亮度音量手势 / 旋转全屏
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "idx"
        const val EXTRA_URLS = "urls"
        const val EXTRA_LABELS = "labels"
        const val EXTRA_ID = "id"
        const val EXTRA_EP = "ep"
        const val EXTRA_EP_SLUGS = "ep_slugs"
        const val EXTRA_EP_LABELS = "ep_labels"
        const val EXTRA_TITLE = "title"
        const val EXTRA_COVER = "cover"
        const val EXTRA_SUB = "sub"
        const val EXTRA_BADGE = "badge"
        const val EXTRA_SCORE = "score"
        const val EXTRA_POS = "pos"

        const val RES_POS = "res_pos"
        const val RES_ID = "res_id"
        const val RES_EP = "res_ep"

        private const val PREFS = "nivod_prefs"
        private const val K_HINT = "gesture_hint_shown"

        // GSYVideoView.CURRENT_STATE_* （那些常量声明在类上、不在公开接口里，这里本地定义避免引用问题）
        private const val STATE_PLAYING = 2      // CURRENT_STATE_PLAYING
        private const val STATE_BUFFERING = 3    // CURRENT_STATE_PLAYING_BUFFERING_START
        private const val STATE_PAUSE = 5        // CURRENT_STATE_PAUSE

        private const val OVERLAY_SHOW_MS = 3000L
    }

    private lateinit var player: StandardGSYVideoPlayer
    private lateinit var bar: ProgressBar
    private lateinit var speedBtn: TextView
    private lateinit var favBtn: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnEps: ImageButton
    private lateinit var overlay: LinearLayout

    /** 叠加操作条的自动淡出 */
    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideOverlayTask = Runnable { maybeHideOverlay() }

    private var orientationUtils: OrientationUtils? = null
    private val pool = Executors.newFixedThreadPool(2)

    private var urls: ArrayList<String> = arrayListOf()
    private var labels: ArrayList<String> = arrayListOf()
    private var idx = 0

    private var epSlugs: ArrayList<String> = arrayListOf()
    private var epLabels: ArrayList<String> = arrayListOf()
    private var epIdx = 0
    private var sortDesc = false
    private var switching = false

    private var videoId = ""
    private var epSlug = ""
    private var title = ""
    private var cover = ""
    private var sub = ""
    private var badge = ""
    private var score = ""

    private val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIdx = 2

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_player)

        player = findViewById(R.id.player)
        bar = findViewById(R.id.bar)
        speedBtn = findViewById(R.id.btn_speed)
        favBtn = findViewById(R.id.btn_fav)
        btnPrev = findViewById(R.id.btn_prev)
        btnNext = findViewById(R.id.btn_next)
        btnEps = findViewById(R.id.btn_eps)
        overlay = findViewById(R.id.actions)
        player.keepScreenOn = true

        urls = intent.getStringArrayListExtra(EXTRA_URLS) ?: arrayListOf()
        labels = intent.getStringArrayListExtra(EXTRA_LABELS) ?: arrayListOf()
        idx = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        epSlugs = intent.getStringArrayListExtra(EXTRA_EP_SLUGS) ?: arrayListOf()
        epLabels = intent.getStringArrayListExtra(EXTRA_EP_LABELS) ?: arrayListOf()
        videoId = intent.getStringExtra(EXTRA_ID).orEmpty()
        epSlug = intent.getStringExtra(EXTRA_EP).orEmpty()
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifEmpty { "播放中" }
        cover = intent.getStringExtra(EXTRA_COVER).orEmpty()
        sub = intent.getStringExtra(EXTRA_SUB).orEmpty()
        badge = intent.getStringExtra(EXTRA_BADGE).orEmpty()
        score = intent.getStringExtra(EXTRA_SCORE).orEmpty()

        epIdx = epSlugs.indexOf(epSlug).let { if (it >= 0) it else 0 }
        if (epSlugs.isEmpty() && epSlug.isNotEmpty()) epSlugs.add(epSlug)

        if (urls.isEmpty()) { finish(); return }

        setupPlayer(intent.getLongExtra(EXTRA_POS, 0L))
        setupOrientation()
        setupOverlay()
        updateEpUi()
        showOverlay()          // 进来先亮 3 秒，之后自动淡出
        hintOnce()
    }

    private fun currentUrl() = urls.getOrElse(idx) { "" }

    private fun setupPlayer(pos: Long) {
        player.setIsTouchWiget(true)
        player.setIsTouchWigetFull(true)
        player.setDismissControlTime(3000)
        player.setShowFullAnimation(true)
        player.setRotateViewAuto(false)
        player.setLockLand(false)

        player.setUp(currentUrl(), false, null, title)
        player.setMapHeadData(hashMapOf("Referer" to Site.BASE, "User-Agent" to Site.UA))
        player.startPlayLogic()
        if (pos > 0) runCatching { player.seekTo(pos) }

        player.getTitleTextView().setOnLongClickListener { showMore(); true }
    }

    private fun setupOrientation() {
        val ou = OrientationUtils(this, player)
        ou.setEnable(true)
        orientationUtils = ou
        player.getFullscreenButton()?.apply {
            visibility = View.VISIBLE
            setOnClickListener { ou.resolveByClick() }
        }
        player.getBackButton()?.setOnClickListener { handleBack() }
    }

    private fun inLandscape() = (orientationUtils?.getIsLand() ?: 0) > 0

    private fun handleBack() {
        if (inLandscape()) { orientationUtils?.backToProtVideo(); return }
        finishWithResult()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = handleBack()

    // ───── 叠加操作条 ─────

    private fun setupOverlay() {
        paintSpeed()
        speedBtn.setOnClickListener {
            speedIdx = (speedIdx + 1) % speeds.size
            applySpeed()
        }

        paintFav(FavStore.has(this, videoId))
        favBtn.setOnClickListener {
            val now = FavStore.toggle(this, currentVideo())
            paintFav(now)
            tip(getString(if (now) R.string.fav_added else R.string.fav_removed))
        }

        findViewById<ImageButton>(R.id.btn_lines).setOnClickListener { showLines() }

        btnPrev.setOnClickListener {
            if (epIdx <= 0) tip(getString(R.string.no_prev_ep)) else switchEpisode(epIdx - 1)
        }
        btnNext.setOnClickListener {
            if (epIdx >= epSlugs.size - 1) tip(getString(R.string.no_next_ep)) else switchEpisode(epIdx + 1)
        }
        btnEps.setOnClickListener { showEpisodes() }
    }

    private fun updateEpUi() {
        val multi = epSlugs.size > 1
        btnPrev.visibility = if (multi) View.VISIBLE else View.GONE
        btnNext.visibility = if (multi) View.VISIBLE else View.GONE
        btnEps.visibility = if (multi) View.VISIBLE else View.GONE
        player.getTitleTextView().text =
            if (multi) "$title   " + getString(R.string.ep_now, epIdx + 1, epSlugs.size) else title
    }

    private fun currentVideo() = Video(
        id = videoId, title = title, cover = cover, sub = sub, badge = badge, score = score
    )

    private fun fmtSpeed(f: Float): String {
        var t = "%.2f".format(f)
        while (t.endsWith("0")) t = t.dropLast(1)
        if (t.endsWith(".")) t += "0"
        return t + "×"
    }

    private fun paintSpeed() { speedBtn.text = fmtSpeed(speeds[speedIdx]) }

    private fun applySpeed() {
        val sp = speeds[speedIdx]
        runCatching { player.setSpeedPlaying(sp, false) }
        paintSpeed()
        tip(getString(R.string.speed_now, fmtSpeed(sp)))
    }

    private fun paintFav(on: Boolean) {
        favBtn.alpha = if (on) 1f else 0.45f
        favBtn.setColorFilter(if (on) 0xFF3DDC97.toInt() else 0xFFCCCCCC.toInt())
    }

    // ───── 线路 ─────

    private fun showLines() {
        if (urls.size <= 1) { tip(getString(R.string.only_one_line)); return }
        val items = Array(urls.size) { i ->
            val name = labels.getOrElse(i) { "线路 ${i + 1}" }
            if (i == idx) "● $name" else "　$name"
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.line_switch) + " (${idx + 1}/${urls.size})")
            .setItems(items) { _, which -> switchLine(which) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun switchLine(which: Int) {
        if (which == idx) return
        val at = player.currentPositionWhenPlaying
        idx = which
        reload(currentUrl(), at)
        tip(getString(R.string.line_now, idx + 1, urls.size))
    }

    // ───── 选集 ─────

    private fun showEpisodes() {
        if (epSlugs.size <= 1) { tip(getString(R.string.only_one_line)); return }
        val order = if (sortDesc) epSlugs.indices.reversed().toList() else epSlugs.indices.toList()
        val items = Array(order.size) { p ->
            val i = order[p]
            val label = epLabels.getOrElse(i) { "第${i + 1}集" }
            if (i == epIdx) "● $label" else "　$label"
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ep_pick) + " (${epIdx + 1}/${epSlugs.size})")
            .setItems(items) { _, p -> switchEpisode(order[p]) }
            .setNeutralButton(if (sortDesc) R.string.ep_order_asc else R.string.ep_order_desc) { _, _ ->
                sortDesc = !sortDesc
                showEpisodes()          // 重新弹出，顺序已变
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun switchEpisode(target: Int) {
        if (switching || target !in epSlugs.indices) return
        switching = true
        bar.visibility = View.VISIBLE
        saveProgress()                              // 先把当前这集进度存了

        val nextSlug = epSlugs[target]
        pool.execute {
            val (lines, pick) = PlayerLauncher.loadLines(videoId, nextSlug)
            runOnUiThread {
                switching = false
                bar.visibility = View.GONE
                if (lines.isEmpty()) { tip(getString(R.string.parse_fail)); return@runOnUiThread }

                epIdx = target
                epSlug = nextSlug
                urls = ArrayList(lines.map { it.url })
                labels = ArrayList(lines.map { "${it.from} · ${it.name}" })
                idx = pick

                val hist = History.last(this, videoId)
                val startPos = if (hist != null && hist.first == nextSlug) hist.second else 0L
                reload(currentUrl(), startPos)
                updateEpUi()
            }
        }
    }

    /** 重新起播（换线路/换集都走这里） */
    private fun reload(url: String, pos: Long) {
        player.release()
        player.setUp(url, false, null, title)
        player.setMapHeadData(hashMapOf("Referer" to Site.BASE, "User-Agent" to Site.UA))
        player.startPlayLogic()
        if (pos > 0) runCatching { player.seekTo(pos) }
    }

    private fun saveProgress() {
        val ms = player.currentPositionWhenPlaying
        if (ms > 3000 && videoId.isNotEmpty() && epSlug.isNotEmpty()) {
            History.save(this, currentVideo(), epSlug, ms)
        }
    }

    // ───── 叠加条自动显隐 ─────

    /** 任何触摸都让叠加条重新出现，3 秒后淡出；暂停/缓冲时保留，免得想操作却找不到按钮 */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) showOverlay()
        return super.dispatchTouchEvent(ev)
    }

    private fun showOverlay() {
        overlay.animate().cancel()
        if (overlay.visibility != View.VISIBLE) {
            overlay.alpha = 0f
            overlay.visibility = View.VISIBLE
        }
        overlay.animate().alpha(1f).setDuration(150).start()
        uiHandler.removeCallbacks(hideOverlayTask)
        uiHandler.postDelayed(hideOverlayTask, OVERLAY_SHOW_MS)
    }

    private fun maybeHideOverlay() {
        val st = runCatching { player.currentState }.getOrDefault(0)
        when (st) {
            STATE_PLAYING -> hideOverlay()
            STATE_PAUSE, STATE_BUFFERING -> uiHandler.postDelayed(hideOverlayTask, 1500)
            else -> hideOverlay()
        }
    }

    private fun hideOverlay() {
        if (overlay.visibility != View.VISIBLE) return
        overlay.animate().cancel()
        overlay.animate().alpha(0f).setDuration(250)
            .withEndAction { overlay.visibility = View.GONE }
            .start()
    }

    // ───── 更多 ─────

    private fun showMore() {
        val items = arrayOf(
            getString(R.string.ep_pick),
            getString(R.string.mn_lines),
            getString(R.string.mn_speed),
            getString(if (FavStore.has(this, videoId)) R.string.mn_unfav else R.string.mn_fav),
            getString(R.string.mn_web)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.mn_more)
            .setItems(items) { _, i ->
                when (i) {
                    0 -> showEpisodes()
                    1 -> showLines()
                    2 -> applySpeed()
                    3 -> {
                        val now = FavStore.toggle(this, currentVideo())
                        paintFav(now)
                        tip(getString(if (now) R.string.fav_added else R.string.fav_removed))
                    }
                    4 -> startActivity(
                        Intent(this, WebActivity::class.java)
                            .putExtra(WebActivity.EXTRA_URL, Site.BASE + "vodplay/$videoId/$epSlug")
                    )
                }
            }
            .show()
    }

    private fun hintOnce() {
        val sp = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (sp.getBoolean(K_HINT, false)) return
        sp.edit().putBoolean(K_HINT, true).apply()
        Toast.makeText(this, R.string.gesture_hint, Toast.LENGTH_LONG).show()
    }

    // ───── 生命周期 ─────

    private fun finishWithResult() {
        saveProgress()
        val i = Intent()
        i.putExtra(RES_POS, player.currentPositionWhenPlaying)
        i.putExtra(RES_ID, videoId)
        i.putExtra(RES_EP, epSlug)
        setResult(RESULT_OK, i)
        finish()
    }

    override fun onPause() { super.onPause(); runCatching { player.onVideoPause() }; saveProgress() }
    override fun onResume() { super.onResume(); runCatching { player.onVideoResume() } }

    override fun onDestroy() {
        super.onDestroy()
        pool.shutdownNow()
        uiHandler.removeCallbacksAndMessages(null)
        runCatching { orientationUtils?.releaseListener() }
        runCatching { player.release() }
    }

    private fun tip(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
