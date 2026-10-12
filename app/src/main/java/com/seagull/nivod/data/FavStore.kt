package com.seagull.nivod.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object FavStore {

    private const val SP = "nivod_fav"
    private const val KEY = "items"
    private const val MAX = 500

    /** @return true = 现在已收藏 */
    fun toggle(ctx: Context, v: Video): Boolean {
        val cur = raw(ctx)
        var removed = false
        val out = JSONArray()
        for (i in 0 until cur.length()) {
            val o = cur.optJSONObject(i) ?: continue
            if (o.optString("id") == v.id) { removed = true; continue }
            out.put(o)
        }
        if (!removed) out.put(toJson(v))
        if (out.length() > MAX) {
            val t = JSONArray()
            for (i in (out.length() - MAX) until out.length()) t.put(out.get(i))
            save(ctx, t)
        } else save(ctx, out)
        return !removed
    }

    fun has(ctx: Context, id: String): Boolean {
        val cur = raw(ctx)
        for (i in 0 until cur.length()) if (cur.optJSONObject(i)?.optString("id") == id) return true
        return false
    }

    /** 新的在前 */
    fun list(ctx: Context): List<Video> {
        val cur = raw(ctx)
        val out = ArrayList<Video>(cur.length())
        for (i in cur.length() - 1 downTo 0) out.add(fromJson(cur.optJSONObject(i) ?: continue))
        return out
    }

    fun remove(ctx: Context, id: String) {
        val cur = raw(ctx)
        val out = JSONArray()
        for (i in 0 until cur.length()) {
            val o = cur.optJSONObject(i) ?: continue
            if (o.optString("id") == id) continue
            out.put(o)
        }
        save(ctx, out)
    }

    fun clear(ctx: Context) { sp(ctx).edit().remove(KEY).apply() }

    private fun toJson(v: Video) = JSONObject().apply {
        put("id", v.id); put("title", v.title); put("cover", v.cover)
        put("sub", v.sub); put("badge", v.badge); put("score", v.score)
        put("ts", System.currentTimeMillis())
    }

    private fun fromJson(o: JSONObject) = Video(
        id = o.optString("id"),
        title = o.optString("title"),
        cover = o.optString("cover"),
        sub = o.optString("sub"),
        badge = o.optString("badge"),
        score = o.optString("score")
    )

    private fun save(ctx: Context, arr: JSONArray) =
        sp(ctx).edit().putString(KEY, arr.toString()).apply()

    private fun sp(ctx: Context) = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)

    private fun raw(ctx: Context): JSONArray = try {
        JSONArray(sp(ctx).getString(KEY, "[]"))
    } catch (e: Exception) { JSONArray() }
}
