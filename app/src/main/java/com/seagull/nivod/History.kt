package com.seagull.nivod

import android.content.Context
import com.seagull.nivod.data.Video
import org.json.JSONArray
import org.json.JSONObject

/** 播放历史：Video + 分集 + 播放位置 */
object History {

    private const val SP = "nivod_history"
    private const val KEY = "items"
    private const val MAX = 300

    data class Item(val video: Video, val epSlug: String, val pos: Long, val ts: Long)

    @Volatile private var cache: Map<String, Pair<String, Long>>? = null

    /** @return 该视频上次看到的分集与位置（毫秒） */
    fun last(ctx: Context, id: String): Pair<String, Long>? {
        val m = cache ?: loadMap(ctx).also { cache = it }
        return m[id]
    }

    fun save(ctx: Context, v: Video, epSlug: String, pos: Long) {
        if (v.id.isEmpty()) return
        val arr = raw(ctx)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") == v.id) continue
            out.put(o)
        }
        out.put(JSONObject().apply {
            put("id", v.id); put("title", v.title); put("cover", v.cover)
            put("sub", v.sub); put("badge", v.badge); put("score", v.score)
            put("ep", epSlug); put("pos", pos)
            put("ts", System.currentTimeMillis())
        })
        val from = (out.length() - MAX).coerceAtLeast(0)
        if (from > 0) {
            val t = JSONArray()
            for (i in from until out.length()) t.put(out.get(i))
            saveJson(ctx, t)
        } else saveJson(ctx, out)
    }

    private fun loadMap(ctx: Context): Map<String, Pair<String, Long>> {
        val arr = raw(ctx)
        val m = HashMap<String, Pair<String, Long>>(arr.length() * 2)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isNotEmpty()) m[id] = o.optString("ep") to o.optLong("pos", 0L)
        }
        return m
    }

    /** 新的在前 */
    fun list(ctx: Context): List<Item> {
        val arr = raw(ctx)
        val out = ArrayList<Item>(arr.length())
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id").isEmpty()) continue
            out.add(
                Item(
                    Video(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        cover = o.optString("cover"),
                        sub = o.optString("sub"),
                        badge = o.optString("badge"),
                        score = o.optString("score")
                    ),
                    o.optString("ep"), o.optLong("pos", 0L), o.optLong("ts", 0L)
                )
            )
        }
        return out
    }

    fun remove(ctx: Context, id: String) {
        val arr = raw(ctx)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") == id) continue
            out.put(o)
        }
        saveJson(ctx, out)
    }

    fun clear(ctx: Context) {
        sp(ctx).edit().remove(KEY).apply()
        cache = null
    }

    private fun saveJson(ctx: Context, arr: JSONArray) {
        sp(ctx).edit().putString(KEY, arr.toString()).apply()
        cache = null
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)

    private fun raw(ctx: Context): JSONArray = try {
        JSONArray(sp(ctx).getString(KEY, "[]"))
    } catch (e: Exception) { JSONArray() }
}
