package com.seagull.nivod

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.seagull.nivod.data.FavStore
import com.seagull.nivod.data.Site
import com.seagull.nivod.data.Video
import com.shuyu.gsyvideoplayer.utils.OrientationUtils
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer

/**
 * 播放页。
 * 站点每个分集给多条线路（实测 3~12 条），launcher 已探活并优先选了可用的一条，
 * 这里提供手动切换；点「线路」能看到全部并随时换。
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "idx"
        const val EXTRA_URLS = "urls"
        const val EXTRA_LABELS = "labels"
        const val EXTRA_ID = "id"
        const val EXTRA_EP = "ep"
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
    }

    private lateinit var player: StandardGSYVideoPlayer
    private var orientationUtils: OrientationUtils? = null
    private lateinit var speedBtn: TextView
    private lateinit var favBtn: ImageButton

    private var urls: ArrayList<String> = arrayListOf()
    private var labels: ArrayList<String> = arrayListOf()
    private var idx = 0
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
        speedBtn = findViewById(R.id.btn_speed)
        favBtn = findViewById(R.id.btn_fav)
        player.keepScreenOn = true

        urls = intent.getStringArrayListExtra(EXTRA_URLS) ?: arrayListOf()
        labels = intent.getStringArrayListExtra(EXTRA_LABELS) ?: arrayListOf()
        idx = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        videoId = intent.getStringExtra(EXTRA_ID).orEmpty()
        epSlug = intent.getStringExtra(EXTRA_EP).orEmpty()
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifEmpty { "播放中" }
        cover = intent.getStringExtra(EXTRA_COVER).orEmpty()
        sub = intent.getStringExtra(EXTRA_SUB).orEmpty()
        badge = intent.getStringExtra(EXTRA_BADGE).orEmpty()
        score = intent.getStringExtra(EXTRA_SCORE).orEmpty()

        if (urls.isEmpty()) { finish(); return }

        setupPlayer(intent.getLongExtra(EXTRA_POS, 0L))
        setupOrientation()
        setupOverlay()
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
        // 站点 CDN 实测无防盗链，带头只为以后加保护时不用改代码
        player.setMapHeadData(hashMapOf("Referer" to Site.BASE, "User-Agent" to Site.UA))
        player.startPlayLogic()
        if (pos > 0) runCatching { player.seekTo(pos) }

        player.getTitleTextView().text = title
        player.getTitleTextView().setOnLongClickListener { showMore(); true }
    }

    private fun setupOrientation() {
        val ou = OrientationUtils(this, player)
        ou.setEnable(true)                 // 跟随设备旋转自动进出全屏
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

    /** 线路选择：把站点给的全部线路列出来，当前那条标出来 */
    private fun showLines() {
        if (urls.size <= 1) { tip(getString(R.string.only_one_line)); return }
        val items = Array(urls.size) { i ->
            val name = labels.getOrElse(i) { "线路 ${i + 1}" }
            if (i == idx) "● $name" else "　$name"
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.line_switch) + " (${idx + 1}/${urls.size})")
            .setItems(items) { _, which -> switchTo(which) }
            .show()
    }

    private fun switchTo(which: Int) {
        if (which == idx) return
        val at = player.currentPositionWhenPlaying
        idx = which
        player.release()
        player.setUp(currentUrl(), false, null, title)
        player.setMapHeadData(hashMapOf("Referer" to Site.BASE, "User-Agent" to Site.UA))
        player.startPlayLogic()
        if (at > 0) runCatching { player.seekTo(at) }
        tip(getString(R.string.line_now, idx + 1, urls.size))
    }

    private fun showMore() {
        val items = arrayOf(
            getString(R.string.mn_lines),
            getString(R.string.mn_speed),
            getString(if (FavStore.has(this, videoId)) R.string.mn_unfav else R.string.mn_fav),
            getString(R.string.mn_web)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.mn_more)
            .setItems(items) { _, i ->
                when (i) {
                    0 -> showLines()
                    1 -> applySpeed()
                    2 -> {
                        val now = FavStore.toggle(this, currentVideo())
                        paintFav(now)
                        tip(getString(if (now) R.string.fav_added else R.string.fav_removed))
                    }
                    3 -> startActivity(
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

    private fun finishWithResult() {
        val i = Intent()
        i.putExtra(RES_POS, player.currentPositionWhenPlaying)
        i.putExtra(RES_ID, videoId)
        i.putExtra(RES_EP, epSlug)
        setResult(RESULT_OK, i)
        finish()
    }

    override fun onPause() { super.onPause(); runCatching { player.onVideoPause() } }
    override fun onResume() { super.onResume(); runCatching { player.onVideoResume() } }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { orientationUtils?.releaseListener() }
        runCatching { player.release() }
    }

    private fun tip(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
