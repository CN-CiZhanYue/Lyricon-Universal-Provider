package io.github.cn_cizhanyue.lyricon.universal.provider.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class UnisonTranslationSource(private val client: OkHttpClient) {
    private val timedLine = Regex("^\\[(\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?)](.*)$")
    private val inlineTime = Regex("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>")

    fun translate(lrc: String, targetLanguage: String): String? = runCatching {
        if (targetLanguage.isBlank()) return null
        val rows = lrc.lines().mapNotNull { line ->
            val match = timedLine.matchEntire(line.trim()) ?: return@mapNotNull null
            val text = match.groupValues[2].replace(inlineTime, "").trim()
            if (text.isBlank()) null else match.groupValues[1] to text
        }
        if (rows.isEmpty()) return null
        val body = JSONObject().apply {
            put("lines", JSONArray(rows.map { it.second }))
            put("to", targetLanguage)
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val response = client.newCall(Request.Builder().url("https://unison.boidu.dev/translate").post(body).build()).execute()
        if (!response.isSuccessful) return null
        val translated = JSONObject(response.body?.string().orEmpty()).optJSONArray("lines") ?: return null
        buildString {
            for (index in rows.indices) {
                val item = translated.optJSONObject(index) ?: continue
                // romanization is intentionally ignored by contract.
                val text = item.optString("translation").takeIf { it.isNotBlank() && it != "null" } ?: continue
                append('[').append(rows[index].first).append(']').append(text).append('\n')
            }
        }.takeIf { it.isNotBlank() }
    }.getOrNull()
}
