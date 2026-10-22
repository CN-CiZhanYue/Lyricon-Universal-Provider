package io.github.cn_cizhanyue.lyricon.universal.provider.api

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal object NetEaseSearchResponseParser {
    fun songs(root: JSONObject): JSONArray {
        val result = root.optJSONObject("result") ?: return JSONArray()
        return result.optJSONArray("songs") ?: JSONArray()
    }

    fun title(song: JSONObject): String = song.optString("name")

    fun songId(song: JSONObject): Long = song.optLong("id")

    fun artist(song: JSONObject): String {
        val artists = song.optJSONArray("ar") ?: song.optJSONArray("artists")
        return artists?.optJSONObject(0)?.optString("name").orEmpty()
    }

    fun album(song: JSONObject): String {
        val album = song.optJSONObject("al") ?: song.optJSONObject("album")
        return album?.optString("name").orEmpty()
    }

    fun durationMs(song: JSONObject): Long {
        val dt = song.optLong("dt")
        if (dt > 0L) return dt
        return song.optLong("duration")
    }

    fun coverUrl(song: JSONObject): String {
        val album = song.optJSONObject("al") ?: song.optJSONObject("album")
        val direct = listOf(
            album?.optString("picUrl"),
            album?.optString("blurPicUrl"),
            album?.optString("img1v1Url"),
            song.optString("albumPic")
        ).firstOrNull { !it.isNullOrBlank() && it != "null" }.orEmpty()
        if (direct.isNotBlank()) return toHttps(direct)
        val picId = album?.optString("pic_str").orEmpty().ifBlank {
            val pic = album?.optLong("pic") ?: 0L
            val legacy = album?.optLong("picId") ?: 0L
            (if (pic > 0L) pic else legacy).takeIf { it > 0L }?.toString().orEmpty()
        }
        if (picId.isBlank() || picId == "0") return ""
        return "https://p1.music.126.net/" + encryptPicId(picId) + "/" + picId + ".jpg"
    }

    private fun toHttps(url: String): String =
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

    private fun encryptPicId(id: String): String {
        val magic = "3go8&$8*3*3h0k(2)2".toByteArray(Charsets.US_ASCII)
        val mixed = id.toByteArray(Charsets.US_ASCII)
        for (index in mixed.indices) {
            mixed[index] = (mixed[index].toInt() xor magic[index % magic.size].toInt()).toByte()
        }
        val md5 = MessageDigest.getInstance("MD5").digest(mixed)
        return Base64.encodeToString(md5, Base64.NO_WRAP).replace('/', '_').replace('+', '-')
    }
}
