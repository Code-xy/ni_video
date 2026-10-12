package com.seagull.nivod.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.seagull.nivod.History
import com.seagull.nivod.PlayerActivity
import com.seagull.nivod.data.Line
import com.seagull.nivod.data.Site
import com.seagull.nivod.data.Video
import okhttp3.Request
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 取流 + 拉起播放。
 * 站点每个分集会给多条线路（实测 3~12 条不等），**且存活率不一样**
 * （实测某电影 5 条里挂了 1 条），所以这里先并行探活，优先用能拉到 #EXTM3U 的那条。
 */
object PlayerLauncher {

    private val pool = Executors.newFixedThreadPool(6)

    fun open(
        act: Activity,
        v: Video,
        epSlug: String,
        launcher: ActivityResultLauncher<Intent>,
        busy: (Boolean) -> Unit = {},
        onFail: (Video) -> Unit = {}
    ) {
        busy(true)
        pool.execute {
            val lines: List<Line> = try { Site.playInfo(v.id, epSlug) } catch (e: Exception) { emptyList() }
            val idx = if (lines.isEmpty()) -1 else probe(lines)
            act.runOnUiThread {
                busy(false)
                if (lines.isEmpty() || idx < 0) { onFail(v); return@runOnUiThread }

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
                    putExtra(PlayerActivity.EXTRA_TITLE, v.title)
                    putExtra(PlayerActivity.EXTRA_COVER, v.cover)
                    putExtra(PlayerActivity.EXTRA_SUB, v.sub)
                    putExtra(PlayerActivity.EXTRA_BADGE, v.badge)
                    putExtra(PlayerActivity.EXTRA_SCORE, v.score)
                    putExtra(PlayerActivity.EXTRA_POS, startPos)
                }
                launcher.launch(i)
            }
        }
    }

    /** 并行探活：返回第一条能拉到 m3u8 的线路下标；全挂则退回 0（让播放器自己再试） */
    private fun probe(lines: List<Line>): Int {
        val n = lines.size
        val ok = BooleanArray(n)
        val latch = CountDownLatch(n)
        for (i in 0 until n) {
            val url = lines[i].url
            pool.execute {
                ok[i] = reachable(url)
                latch.countDown()
            }
        }
        try { latch.await(7, TimeUnit.SECONDS) } catch (e: Exception) { }
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
        Site.client.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            r.code in 200..299 && body.trimStart().startsWith("#EXTM3U")
        }
    } catch (e: Exception) { false }
}
