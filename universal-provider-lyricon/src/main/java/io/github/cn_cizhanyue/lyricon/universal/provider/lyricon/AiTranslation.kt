package io.github.cn_cizhanyue.lyricon.universal.provider.lyricon

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

object AiTranslation {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun translate(
        lyric: String,
        endpoint: String,
        apiKey: String,
        model: String,
        target: String,
    ): String? {
        if (endpoint.isBlank() || apiKey.isBlank() || model.isBlank()) return null
        val language = target.ifBlank { "中文" }
        val lines = LrcParser.parse(lyric).filter { it.text.isNotBlank() }
        if (lines.isEmpty()) return null
        val translated = ArrayList<String>(lines.size)
        for (batch in lines.chunked(40)) {
            val result = request(endpoint, apiKey, model, language, batch.map { it.text }) ?: return null
            if (result.size != batch.size) return null
            translated.addAll(result)
        }
        if (translated.size != lines.size) return null
        return lines.mapIndexed { index, line ->
            "[" + formatMs(line.begin) + "]" + translated[index]
        }.joinToString("\n")
    }

    private fun request(
        endpoint: String,
        apiKey: String,
        model: String,
        target: String,
        texts: List<String>,
    ): List<String>? {
        val system = """
            You are a precise song-lyric translator.
            Translate each input line into $target.
            Return exactly ${texts.size} lines, one translation per input line, in the same order.
            Never merge, split, skip, reorder or add lines.
            Keep the original punctuation and emotional tone.
            Do not output timestamps, numbering, quotes, Markdown, notes or explanations.
            If a line is instrumental or has no words, return an empty line for it.
        """.trimIndent()
        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", 0.2)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", texts.joinToString("\n")))
            })
        }.toString()
        val request = Request.Builder()
            .url(normalizeEndpoint(endpoint))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val content = JSONObject(response.body?.string() ?: return null)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?: return null
                val cleaned = content.trim()
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
                val rows = cleaned.split('\n').map { row ->
                    row.trim().replace(Regex("^\\d+[.)、:：]\\s*"), "")
                }.toMutableList()
                while (rows.isNotEmpty() && rows.first().isBlank()) rows.removeAt(0)
                while (rows.isNotEmpty() && rows.last().isBlank()) rows.removeAt(rows.lastIndex)
                rows
            }
        }.getOrNull()
    }

    private fun normalizeEndpoint(endpoint: String): String {
        val base = endpoint.trim().trimEnd('/')
        return when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }

    private fun formatMs(ms: Long): String {
        val hours = ms / 3_600_000L
        val minutes = (ms / 60_000L) % 60L
        val seconds = (ms / 1_000L) % 60L
        val millis = ms % 1_000L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d.%03d", hours, minutes, seconds, millis)
        } else {
            String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
        }
    }
}
