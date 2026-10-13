package com.seagull.nivod

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewCompat
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import com.seagull.nivod.ui.Insets

/** 网页兜底：需要登录态 / 原生解析失败 / 用户主动要看网页时用 */
class WebActivity : AppCompatActivity() {

    companion object { const val EXTRA_URL = "url" }

    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private lateinit var bar: ProgressBar
    private lateinit var swipe: SwipeRefreshLayout

    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var fileCb: android.webkit.ValueCallback<Array<Uri>>? = null
    private var pageUrl = ""
    private var pageTitle = ""

    private val pool = Executors.newFixedThreadPool(2)
    private val jsSource: String by lazy { readAsset("inject.js") }

    // 站点页面上挂的广告/统计域，直接拦掉
    private val adDomains = listOf(
        "createlouisville.org", "googletagmanager.com", "google-analytics.com",
        "cloudflareinsights.com", "doubleclick.net", "popads.net",
        "propellerads.com", "onclickads.net", "exoclick.com", "adsterra"
    )

    private val playerResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val d = r.data ?: return@registerForActivityResult
            val id = d.getStringExtra(PlayerActivity.RES_ID).orEmpty()
            val ep = d.getStringExtra(PlayerActivity.RES_EP).orEmpty()
            val pos = d.getLongExtra(PlayerActivity.RES_POS, 0L)
            if (id.isNotEmpty() && pos > 3000) {
                History.save(this, com.seagull.nivod.data.Video(id, pageTitle, "", "", "", ""), ep, pos)
            }
        }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val cb = fileCb ?: return@registerForActivityResult
            fileCb = null
            cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        }

    inner class Bridge {
        @JavascriptInterface
        fun play(json: String) = runOnUiThread { handlePlay(json) }

        @JavascriptInterface
        fun found(url: String) { /* 预留：可用于提示"已嗅到地址" */ }

        @JavascriptInterface
        fun toast(msg: String) = runOnUiThread { tip(msg) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_web)

        Insets.pad(findViewById(R.id.root))
        web = findViewById(R.id.web)
        root = findViewById(R.id.root)
        bar = findViewById(R.id.bar)
        swipe = findViewById(R.id.swipe)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = userAgentString + " NiVodApp/1.0"
        }
        web.setBackgroundColor(0xFF0E0E12.toInt())
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        web.addJavascriptInterface(Bridge(), "NiVod")
        WebView.setWebContentsDebuggingEnabled(true)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        swipe.setColorSchemeColors(0xFF3DDC97.toInt())
        swipe.setOnRefreshListener { web.reload() }

        // 不支持时靠 onPageFinished 兜底；inject.js 自带幂等守卫，重复注入无害
        runCatching { WebViewCompat.addDocumentStartJavaScript(web, jsSource, setOf("*")) }

        web.webViewClient = client
        web.webChromeClient = chrome

        val target = intent.getStringExtra(EXTRA_URL) ?: getString(R.string.start_url)
        if (s != null) web.restoreState(s) else web.loadUrl(target)
    }

    private fun readAsset(n: String) = try {
        assets.open(n).bufferedReader().use { it.readText() }
    } catch (e: Exception) { "" }

    private val client = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
            val u = req.url
            if (isAd(u.toString())) return true
            if (u.scheme == "http" || u.scheme == "https") return false
            return try { startActivity(Intent(Intent.ACTION_VIEW, u)); true } catch (e: Exception) { true }
        }

        override fun shouldInterceptRequest(v: WebView, req: WebResourceRequest): WebResourceResponse? {
            if (isAd(req.url.toString())) {
                return WebResourceResponse("text/plain", "utf-8", 204, "No Content",
                    emptyMap<String, String>(), ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun onPageStarted(v: WebView, url: String?, f: Bitmap?) {
            pageUrl = url.orEmpty(); bar.visibility = View.VISIBLE
        }

        override fun onPageFinished(v: WebView, url: String?) {
            bar.visibility = View.GONE
            swipe.isRefreshing = false
            pageUrl = url.orEmpty()
            pageTitle = v.title.orEmpty()
            web.evaluateJavascript(jsSource, null)
        }
    }

    private val chrome = object : WebChromeClient() {
        override fun onProgressChanged(v: WebView, p: Int) {
            bar.progress = p
            bar.visibility = if (p in 1..99) View.VISIBLE else View.GONE
        }

        override fun onReceivedTitle(v: WebView, t: String?) { pageTitle = t.orEmpty() }

        override fun onShowCustomView(view: View, cb: CustomViewCallback) {
            if (customView != null) { cb.onCustomViewHidden(); return }
            customView = view; customCb = cb
            view.setBackgroundColor(0xFF000000.toInt())
            root.addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            web.visibility = View.GONE
        }

        override fun onHideCustomView() {
            customView?.let { root.removeView(it) }
            customView = null
            customCb?.onCustomViewHidden(); customCb = null
            web.visibility = View.VISIBLE
        }

        override fun onShowFileChooser(
            v: WebView,
            cb: android.webkit.ValueCallback<Array<Uri>>,
            params: FileChooserParams
        ): Boolean {
            fileCb?.onReceiveValue(null)
            fileCb = cb
            return try { filePicker.launch(params.createIntent()); true }
            catch (e: Exception) { fileCb = null; false }
        }
    }

    private fun handlePlay(json: String) {
        val url: String
        val title: String
        try {
            val o = JSONObject(json)
            url = o.optString("url")
            title = o.optString("title").ifEmpty { pageTitle }
        } catch (e: Exception) { tip("解析失败"); return }
        if (url.isEmpty()) { tip("没嗅到视频地址"); return }

        val id = Regex("/voddetail/(\\d+)").find(pageUrl)?.groupValues?.get(1).orEmpty()
        val ep = Regex("/vodplay/\\d+/([A-Za-z0-9_]+)").find(pageUrl)?.groupValues?.get(1).orEmpty()

        pool.execute {
            runOnUiThread {
                val i = Intent(this, PlayerActivity::class.java).apply {
                    putStringArrayListExtra(PlayerActivity.EXTRA_URLS, arrayListOf(url))
                    putStringArrayListExtra(PlayerActivity.EXTRA_LABELS, arrayListOf("网页嗅探"))
                    putExtra(PlayerActivity.EXTRA_INDEX, 0)
                    putExtra(PlayerActivity.EXTRA_ID, id)
                    putExtra(PlayerActivity.EXTRA_EP, ep)
                    putExtra(PlayerActivity.EXTRA_TITLE, title)
                    putExtra(PlayerActivity.EXTRA_POS, 0L)
                }
                if (isFinishing || isDestroyed) return@runOnUiThread
                try { playerResult.launch(i) } catch (e: Exception) { tip(e.message ?: "启动播放失败") }
            }
        }
    }

    private fun isAd(s: String): Boolean {
        val l = s.lowercase()
        return adDomains.any { l.contains(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); web.saveState(outState)
    }

    override fun onPause() { web.onPause(); super.onPause() }
    override fun onResume() { web.onResume(); super.onResume() }
    override fun onDestroy() { pool.shutdownNow(); super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (customView != null) { web.webChromeClient?.onHideCustomView(); return }
        if (web.canGoBack()) web.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun tip(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
