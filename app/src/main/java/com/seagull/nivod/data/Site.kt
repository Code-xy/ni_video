package com.seagull.nivod.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 站点抓取层（选题全部基于实测，不是猜的）：
 *
 *  列表 / 首页 / 频道 / 筛选 / 搜索  共用同一套卡片 DOM：
 *    li.qy-mod-li
 *      a.qy-mod-link[href=/voddetail/<id>]
 *      img[data-original=/imgs/small/<id>.jpg]
 *      span.qy-mod-label            <- 角标（如"更新至11集"）
 *      p.main > a.link-txt[title]   <- 标题
 *      p.sub[title]                 <- 副标题（年份/地区/类型）
 *
 *  详情页：/voddetail/<id>，字段走固定 id：#director #actors #region #reso
 *          #postYear #langName #types-label #updateTxt #show-desc，
 *          剧集链接 /vodplay/<id>/ep<N>
 *
 *  取流：/xhr_playinfo/<id>-ep<N>  -> JSON {pdatas:[{playurl,from,name}], more_eps}
 *        实测每个分集返回 12 条 m3u8 线路，全部 200 且无防盗链。
 *
 *  ⚠ 站点没有分页：首页 144 / class 76 / filter 与 search 各 48，page 参数一律无效。
 */
object Site {

    const val BASE = "https://www.nivod.cc/"

    const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    // ⚠ 这里是 Accept 头用的逗号列表，只能当普通字符串。
    // OkHttp 的 toMediaType() 只接受单个 MIME 类型（text/html、application/json; charset=utf-8），
    // 传逗号列表会抛 IllegalArgumentException，而且是在静态初始化里 → ExceptionInInitializerError 直接崩。
    private const val ACCEPT_HTML = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    private const val ACCEPT_JSON = "application/json, text/javascript, */*; q=0.01"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val idRe = Regex("/voddetail/(\\d+)")

    private fun url(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http")) pathOrUrl else BASE + pathOrUrl.removePrefix("/")

    private fun get(pathOrUrl: String, accept: String, ajax: Boolean = false, referer: String? = null): String {
        val b = Request.Builder()
            .url(url(pathOrUrl))
            .header("User-Agent", UA)
            .header("Accept", accept)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Referer", referer ?: BASE)
        if (ajax) b.header("X-Requested-With", "XMLHttpRequest")
        client.newCall(b.build()).execute().use { return it.body?.string().orEmpty() }
    }

    fun fetchDoc(pathOrUrl: String): Document = Jsoup.parse(get(pathOrUrl, ACCEPT_HTML), url(pathOrUrl))

    // ───────── 列表 ─────────

    /** 首页：实测 144 张卡片 */
    fun home(): List<Video> = parseCards(fetchDoc(BASE))

    /** 频道：/class.html?channel=movie —— 实测 76~111 张 */
    fun channel(ch: Channel): List<Video> = parseCards(fetchDoc("class.html?channel=" + ch.slug))

    /** 筛选：/filter.html?channel=X&region=Y&showtype=Z —— 实测 48 张 */
    fun filter(ch: Channel, region: Region, type: ShowType): List<Video> {
        val sb = StringBuilder("filter.html?channel=").append(ch.slug)
        if (region.slug.isNotEmpty()) sb.append("&region=").append(region.slug)
        if (type.slug.isNotEmpty()) sb.append("&showtype=").append(type.slug)
        return parseCards(fetchDoc(sb.toString()))
    }

    /** 搜索：/search.html?keyword=X —— 实测 48 张，无分页 */
    fun search(keyword: String): List<Video> =
        parseCards(fetchDoc("search.html?keyword=" + URLEncoder.encode(keyword, "UTF-8")))

    fun parseCards(doc: Document): List<Video> {
        val out = ArrayList<Video>()
        val seen = HashSet<String>()
        for (li in doc.select("li.qy-mod-li")) {
            val a = li.selectFirst("a.qy-mod-link") ?: continue
            val id = idRe.find(a.attr("href"))?.groupValues?.get(1) ?: continue
            if (!seen.add(id)) continue

            val img = li.selectFirst("img")
            val cover: String = img?.attr("data-original")?.takeIf { it.isNotBlank() }
                ?: img?.attr("src")?.takeIf { it.isNotBlank() }
                ?: "/imgs/small/$id.jpg"

            val titleA = li.selectFirst("a.link-txt")
            val title: String = titleA?.attr("title")?.trim()?.takeIf { it.isNotEmpty() }
                ?: titleA?.text()?.trim().orEmpty()
            if (title.isEmpty()) continue

            val badge = li.selectFirst("span.qy-mod-label")?.text()?.trim().orEmpty()
            val sub: String = li.selectFirst("p.sub")?.attr("title")?.trim()?.takeIf { it.isNotEmpty() }
                ?: li.selectFirst("p.sub")?.text()?.trim().orEmpty()
            val score = li.selectFirst("span.text-score")?.text()?.trim().orEmpty()

            out.add(
                Video(
                    id = id,
                    title = title,
                    cover = url(cover),
                    sub = sub,
                    badge = badge,
                    score = score
                )
            )
        }
        return out
    }

    // ───────── 详情 ─────────

    /** 剧集 slug 排序：ep2 要排在 ep10 前面 */
    fun epNum(slug: String): Int = Regex("(\\d+)").find(slug)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun detail(id: String): Detail {
        val doc = fetchDoc("voddetail/$id")

        fun txt(sel: String): String =
            doc.selectFirst(sel)?.text()?.trim().orEmpty()

        val kw = doc.selectFirst("meta[name='keywords']")?.attr("content").orEmpty()
        val kwParts = kw.split(",").map { it.trim() }
        val pageTitle = doc.selectFirst("title")?.text().orEmpty()
        val title = kwParts.firstOrNull { it.isNotEmpty() }
            ?: pageTitle.substringBefore("_").trim()

        val eps = doc.select("a[href^='/vodplay/']").mapNotNull { a ->
            val m = Regex("/vodplay/" + Regex.escape(id) + "/([A-Za-z0-9_]+)").find(a.attr("href"))
                ?: return@mapNotNull null
            val slug = m.groupValues[1]
            Episode(slug, a.text().trim().ifEmpty { "第" + epNum(slug) + "集" })
        }.distinctBy { it.slug }.sortedBy { epNum(it.slug) }

        return Detail(
            id = id,
            title = title,
            cover = "https://www.nivod.cc/imgs/small/$id.jpg",
            score = txt(".score-label"),
            year = txt("#postYear").ifEmpty { kwParts.getOrNull(1).orEmpty() },
            region = txt("#region").ifEmpty { kwParts.getOrNull(3).orEmpty() },
            types = txt("#types-label"),
            lang = txt("#langName").ifEmpty { kwParts.getOrNull(2).orEmpty() },
            reso = txt("#reso"),
            director = txt("#director"),
            actors = txt("#actors"),
            update = txt("#updateTxt"),
            desc = doc.selectFirst("meta[name='description']")?.attr("content").orEmpty(),
            episodes = eps
        )
    }

    // ───────── 取流 ─────────

    /** /xhr_playinfo/<id>-<epSlug> -> 线路列表 */
    fun playInfo(id: String, epSlug: String): List<Line> {
        val body = get(
            "xhr_playinfo/$id-$epSlug",
            ACCEPT_JSON, ajax = true,
            referer = BASE + "vodplay/$id/$epSlug"
        )
        val out = ArrayList<Line>()
        try {
            val arr = JSONObject(body).optJSONArray("pdatas") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val u = o.optString("playurl")
                if (u.isBlank()) continue
                out.add(Line(o.optString("from"), o.optString("name"), u))
            }
        } catch (e: Exception) {
        }
        return out
    }
}
