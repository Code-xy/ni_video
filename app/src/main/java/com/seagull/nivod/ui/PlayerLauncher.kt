package com.seagull.nivod.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.seagull.nivod.History
import com.seagull.nivod.PlayerActivity
import com.seagull.nivod.data.Episode
import com.seagull.nivod.data.Line
import com.seagull.nivod.data.Site
import com.seagull.nivod.data.Video
import okhttp3.Request
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 取流 + 拉起播放。
 *
 * 站点每个分集给多条线路（实测 3~12 条）且**存活率不一**，
 * 所以先并行探活，优先用能拉到 #EXTM3U 的那条。
 *
 * 两个曾经踩过的坑：
 *  1) 原来等全部线路探活完才返回（latch 计满），死线路会把点击到起播拖到 7s+。
 *     现在改成「等第一条成功就返回」，并用短超时的 probeClient。
 *  2) 原来在后台线程回主线程后直接 launcher.launch()，用户中途退出就崩
 *     （ActivityResultLauncher 此时已注销）。现在加了 isFinishing/isDestroyed 判断 + try/catch。
 */
object PlayerLauncher {

    private val pool = Executors.newFixedThreadPool(6)
    private val probePool = Executors.newFixedThreadPool(12)

    /** 取线路并探活。**阻塞**，请在后台线程调用。返回 (线路列表, 选中下标)，失败时下标为 -1 */
    fun loadLines(id: String, epSlug: String): Pair<List<Line>, Int> {
        val lines: List<Line> = try { Site.playInfo(id, epSlug) } catch (e: Exception) { emptyList() }
        if (lines.isEmpty()) return lines to -1
        return lines to probe(lines)
    }

    fun open(
        act: Activity,
        v: Video,
        epSlug: String,
        episodes: List<Episode>,
        launcher: ActivityResultLauncher<Intent>,
        busy: (Boolean) -> Unit = {},
        onFail: (Video) -> Unit = {}
    ) {
        busy(true)
        pool.execute {
            val (lines, idx) = loadLines(v.id, epSlug)
            act.runOnUiThread {
                busy(false)
                if (lines.isEmpty() || idx < 0) { onFail(v); return@runOnUiThread }
                // ★ 用户可能已经退出这个页面，此时 launcher 已注销，直接 launch 会崩
                if (act.isFinishing || act.isDestroyed) return@runOnUiThread

                val last = History.last(act, v.id)
                val startPos = if (last != null && last.first == epSlug) last.second else 0L

                val i = Intent(act, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_INDEX, idx)
                    putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(lines.map { it.url }))
                    putStringArrayListExtra(
                        PlayerActivity.EXTRA_LABELS,
                        ArrayList(lines.map { "${it.from} · ${it.name}" })
                    )
                    putExtra(PlayerActivity.EXTRA_ID, v.id)
                    putExtra(PlayerActivity.EXTRA_EP, epSlug)
                    putStringArrayListExtra(PlayerActivity.EXTRA_EP_SLUGS, ArrayList(episodes.map { it.slug }))
                    putStringArrayListExtra(PlayerActivity.EXTRA_EP_LABELS, ArrayList(episodes.map { it.label }))
                    putExtra(PlayerActivity.EXTRA_TITLE, v.title)
                    putExtra(PlayerActivity.EXTRA_COVER, v.cover)
                    putExtra(PlayerActivity.EXTRA_SUB, v.sub)
                    putExtra(PlayerActivity.EXTRA_BADGE, v.badge)
                    putExtra(PlayerActivity.EXTRA_SCORE, v.score)
                    putExtra(PlayerActivity.EXTRA_POS, startPos)
                }
                try {
                    launcher.launch(i)
                } catch (e: Exception) {
                    onFail(v)
                }
            }
        }
    }

    /** 并行探活：**等到第一条成功就返回**，最多再给低序号 350ms 追赶；全挂返回 0 */
    private fun probe(lines: List<Line>): Int {
        val n = lines.size
        if (n <= 1) return 0

        val ok = BooleanArray(n)
        val anyOk = CountDownLatch(1)
        val allDone = CountDownLatch(n)

        for (i in 0 until n) {
            val u = lines[i].url
            probePool.execute {
                try {
                    if (reachable(u)) {
                        ok[i] = true
                        anyOk.countDown()
                    }
                } catch (t: Throwable) {
                } finally {
                    allDone.countDown()
                }
            }
        }

        try { anyOk.await(4500, TimeUnit.MILLISECONDS) } catch (e: Exception) { }
        try { allDone.await(350, TimeUnit.MILLISECONDS) } catch (e: Exception) { }

        val hit = ok.indexOfFirst { it }
        return if (hit >= 0) hit else 0
    }

    private fun reachable(url: String): Boolean = try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", Site.UA)
            .header("Referer", Site.BASE)
            .header("Range", "bytes=0-1023")
            .build()
        // 用短超时的 probeClient，别用 Site.client（连接超时 15s）
        Site.probeClient.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            r.code in 200..299 && body.trimStart().startsWith("#EXTM3U")
        }
    } catch (e: Exception) { false }
}
