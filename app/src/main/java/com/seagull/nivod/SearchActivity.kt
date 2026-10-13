package com.seagull.nivod

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.seagull.nivod.ui.Insets
import com.seagull.nivod.data.Site
import com.seagull.nivod.data.Video
import com.seagull.nivod.ui.CardMenu
import com.seagull.nivod.ui.VideoAdapter
import com.seagull.nivod.ui.WordAdapter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** 搜索：/search.html?keyword=X，实测恒定 48 条且无分页 */
class SearchActivity : AppCompatActivity() {

    private lateinit var et: EditText
    private lateinit var wordsRv: RecyclerView
    private lateinit var resultRv: RecyclerView
    private lateinit var bar: ProgressBar
    private lateinit var label: TextView
    private lateinit var empty: TextView
    private lateinit var wordAdapter: WordAdapter
    private lateinit var videoAdapter: VideoAdapter

    private val pool = Executors.newFixedThreadPool(2)
    private val busy = AtomicBoolean(false)

    private val playerResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val d = r.data ?: return@registerForActivityResult
            val pos = d.getLongExtra(PlayerActivity.RES_POS, 0L)
            val ep = d.getStringExtra(PlayerActivity.RES_EP).orEmpty()
            val v = lastVideo
            if (v != null && pos > 3000) { History.save(this, v, ep, pos); videoAdapter.notifyDataSetChanged() }
        }
    private var lastVideo: Video? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_search)

        Insets.pad(findViewById(R.id.root), ime = true)

        et = findViewById(R.id.et)
        wordsRv = findViewById(R.id.words)
        resultRv = findViewById(R.id.results)
        bar = findViewById(R.id.bar)
        label = findViewById(R.id.label)
        empty = findViewById(R.id.empty)

        wordAdapter = WordAdapter { w -> et.setText(w); et.setSelection(w.length); doSearch(w) }
        wordsRv.layoutManager = GridLayoutManager(this, 3)
        wordsRv.adapter = wordAdapter

        videoAdapter = VideoAdapter(
            onClick = { openDetail(it) },
            onLongClick = { CardMenu.show(this, it, onPlay = { openDetail(it) }) }
        )
        resultRv.layoutManager = GridLayoutManager(this, 3)
        resultRv.adapter = videoAdapter
        resultRv.setHasFixedSize(true)
        resultRv.itemAnimator = null

        findViewById<ImageButton>(R.id.back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.go).setOnClickListener { doSearch(et.text.toString()) }
        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(et.text.toString()); true } else false
        }

        showWords()
    }

    override fun onDestroy() { pool.shutdownNow(); super.onDestroy() }

    private fun showWords() {
        val hist = SearchStore.list(this)
        wordAdapter.submit(hist)
        label.text = getString(R.string.hist_and_hot)
        wordsRv.visibility = View.VISIBLE
        resultRv.visibility = View.GONE
        empty.visibility = View.GONE
    }

    private fun doSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        SearchStore.add(this, q)
        try {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(et.windowToken, 0)
        } catch (e: Exception) {}
        et.clearFocus()

        wordsRv.visibility = View.GONE
        resultRv.visibility = View.VISIBLE
        empty.visibility = View.GONE
        label.text = "「$q」"
        videoAdapter.add(emptyList(), false)

        if (!busy.compareAndSet(false, true)) return
        bar.visibility = View.VISIBLE
        pool.execute {
            val res: List<Video>? = try { Site.search(q) } catch (e: Exception) { null }
            runOnUiThread {
                busy.set(false)
                bar.visibility = View.GONE
                if (res == null) { tip(getString(R.string.load_fail)); return@runOnUiThread }
                if (res.isEmpty()) {
                    empty.text = getString(R.string.no_result)
                    empty.visibility = View.VISIBLE
                    return@runOnUiThread
                }
                videoAdapter.add(res, false)
            }
        }
    }

    private fun openDetail(v: Video) {
        lastVideo = v
        startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, v.id))
    }

    private fun tip(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
