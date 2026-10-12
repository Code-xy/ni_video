package com.seagull.nivod.data

/** 内存列表缓存：切页面回来不重新联网 */
object ListCache {

    class Entry(val items: List<Video>, val scroll: Int)

    private val map = HashMap<String, Entry>()

    fun get(k: String): Entry? = map[k]

    fun put(k: String, e: Entry) {
        if (map.size >= 12 && !map.containsKey(k)) {
            map.keys.firstOrNull()?.let { map.remove(it) }
        }
        map[k] = e
    }

    fun drop(k: String) { map.remove(k) }

    fun clear() { map.clear() }
}
