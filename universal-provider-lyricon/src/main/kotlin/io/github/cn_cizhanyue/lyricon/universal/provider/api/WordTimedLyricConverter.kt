package io.github.cn_cizhanyue.lyricon.universal.provider.api

import android.util.Base64
import io.github.andrealtb.coloroslyrics.provider.parser.qrc.decrypt.QrcDecrypter
import java.util.Locale

internal object WordTimedLyricConverter {
    val WORD_STAMP = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")
    private val QRC_LINE = Regex("""\[(\d+)\s*,\s*(\d+)]""")
    private val QRC_WORD = Regex("""([^()\r\n]*?)\((\d+)\s*,\s*(\d+)\)""")
    private val LYRIC_CONTENT = Regex("""LyricContent\s*=\s*"([\s\S]*?)"(?=\s*/?>)""")

    fun hasWordTiming(value: String?): Boolean =
        !value.isNullOrBlank() && WORD_STAMP.containsMatchIn(value)

    /**
     * A synthetic `<start>line<end>` wrapper is not real word timing. Bridge
     * treats that as WORD_TIMED with one word and fills the whole line in
     * ~280ms. Real karaoke needs at least two word starts plus a close tag.
     */
    fun hasRealWordTiming(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value.lineSequence().any { line ->
            WORD_STAMP.findAll(line).count() >= 3
        }
    }

    fun convert(value: String?): String? {
        if (value.isNullOrBlank()) return null
        if (hasWordTiming(value)) return value
        val candidates = linkedSetOf(
            value.trim(),
            decodeBase64OrSelf(value).trim(),
            decrypt(value).orEmpty(),
            decrypt(decodeBase64OrSelf(value)).orEmpty()
        ).filter { it.isNotBlank() }
        for (candidate in candidates) {
            if (hasWordTiming(candidate)) return candidate
            convertXmlOrContent(candidate)?.let { return it }
        }
        return null
    }

    private fun convertXmlOrContent(value: String): String? {
        val content = LYRIC_CONTENT.find(value)?.groupValues?.getOrNull(1)
            ?.replace("&quot;", "\"")
            ?.replace("&amp;", "&")
            ?.replace("&lt;", "<")
            ?.replace("&gt;", ">")
            ?.replace("&apos;", "'")
            ?: value
        return convertContent(content)
    }

    private fun convertContent(decoded: String): String? {
        val lines = QRC_LINE.findAll(decoded).toList()
        if (lines.isEmpty()) return null
        return lines.mapIndexedNotNull { index, line ->
            val begin = line.groupValues[1].toLongOrNull() ?: return@mapIndexedNotNull null
            val duration = line.groupValues[2].toLongOrNull() ?: return@mapIndexedNotNull null
            val bodyStart = line.range.last + 1
            val bodyEnd = lines.getOrNull(index + 1)?.range?.first ?: decoded.length
            val body = decoded.substring(bodyStart, bodyEnd).replace("\r", "").replace("\n", "")
            val words = QRC_WORD.findAll(body).toList()
            if (words.isEmpty()) return@mapIndexedNotNull null
            buildString {
                append("[").append(formatMs(begin)).append("]")
                words.forEach { word ->
                    val wordBegin = word.groupValues[2].toLongOrNull() ?: begin
                    append("<").append(formatMs(wordBegin)).append(">").append(word.groupValues[1])
                }
                append("<").append(formatMs(begin + duration)).append(">")
            }
        }.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun decrypt(value: String): String? = QrcDecrypter.decrypt(value.trim())

    private fun decodeBase64OrSelf(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrDefault(value)
    }

    fun formatMs(ms: Long): String = String.format(
        Locale.ROOT, "%02d:%02d.%03d", ms / 60_000, (ms % 60_000) / 1_000, ms % 1_000
    )
}

