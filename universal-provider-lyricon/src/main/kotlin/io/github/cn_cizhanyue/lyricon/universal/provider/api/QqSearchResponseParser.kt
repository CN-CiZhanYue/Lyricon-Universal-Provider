package io.github.cn_cizhanyue.lyricon.universal.provider.api

import org.json.JSONArray
import org.json.JSONObject

internal object QqSearchResponseParser {
    fun songArray(root: JSONObject): JSONArray {
        val bodies = listOfNotNull(
            root.optJSONObject("req_0")?.optJSONObject("data")?.optJSONObject("body"),
            root.optJSONObject("req")?.optJSONObject("data")?.optJSONObject("body"),
            root.optJSONObject("data")?.optJSONObject("body")
        )
        for (body in bodies) {
            val list = body.optJSONObject("song")?.optJSONArray("list")
            if (list != null && list.length() > 0) return list
            val items = body.optJSONArray("item_song")
            if (items != null && items.length() > 0) return items
        }
        return JSONArray()
    }

    fun title(item: JSONObject): String =
        item.optString("title").ifBlank { item.optString("name") }

    fun mid(item: JSONObject): String =
        item.optString("mid").ifBlank { item.optString("songmid") }

    fun songId(item: JSONObject): Long {
        val id = item.optLong("id")
        return if (id != 0L) id else item.optLong("songid")
    }

    fun artist(item: JSONObject): String =
        item.optJSONArray("singer")?.optJSONObject(0)?.optString("name").orEmpty()

    fun album(item: JSONObject): String {
        val album = item.optJSONObject("album") ?: return ""
        return album.optString("name").ifBlank { album.optString("title") }
    }

    fun durationMs(item: JSONObject): Long {
        val intervalSec = item.optLong("interval")
        if (intervalSec > 0L) return intervalSec * 1000L
        val intervalMs = item.optLong("interval_ms")
        return if (intervalMs > 0L) intervalMs else 0L
    }

    fun coverUrl(item: JSONObject): String {
        val album = item.optJSONObject("album") ?: return ""
        val pmid = album.optString("pmid").ifBlank { album.optString("mid") }
        if (pmid.isBlank()) return ""
        return "https://y.gtimg.cn/music/photo_new/T002R300x300M000$pmid.jpg"
    }
}
