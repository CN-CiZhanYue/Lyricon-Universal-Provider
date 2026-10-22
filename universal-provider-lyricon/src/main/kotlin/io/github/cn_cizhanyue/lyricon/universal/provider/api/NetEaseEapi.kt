package io.github.cn_cizhanyue.lyricon.universal.provider.api

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal object NetEaseEapi {
    private const val KEY = "e82ckenh8dichen8"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.3.203419"

    fun fetchLyric(client: OkHttpClient, songId: Long): JSONObject? {
        val params = JSONObject().apply {
            put("id", songId.toString())
            put("cp", false)
            put("lv", -1)
            put("kv", -1)
            put("tv", -1)
            put("rv", -1)
            put("yv", -1)
            put("ytv", -1)
            put("yrv", -1)
        }
        val encrypted = encrypt("/api/song/lyric/v1", params.toString())
        val body = FormBody.Builder().add("params", encrypted).build()
        val request = Request.Builder()
            .url("https://interface.music.163.com/eapi/song/lyric/v1")
            .post(body)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://music.163.com/")
            .addHeader("Cookie", "os=pc; appver=3.1.3.203419; osver=Microsoft-Windows-10; channel=netease")
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful || text.isBlank()) return null
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (json != null && json.has("lrc")) return json
            val decrypted = decrypt(text)
            return decrypted?.let { runCatching { JSONObject(it) }.getOrNull() }
        }
    }

    private fun encrypt(url: String, jsonData: String): String {
        val digest = md5("nobody" + url + "use" + jsonData + "md5forencrypt")
        val text = url + "-36cd479b6b5-" + jsonData + "-36cd479b6b5-" + digest
        return aesEcb(text, encrypt = true).uppercase()
    }

    private fun decrypt(raw: String): String? {
        val bytes = if (raw.length % 2 == 0 && raw.all { it in "0123456789abcdefABCDEF" }) {
            raw.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } else {
            android.util.Base64.decode(raw, android.util.Base64.DEFAULT)
        }
        return runCatching {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY.toByteArray(), "AES"))
            String(cipher.doFinal(bytes), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun aesEcb(text: String, encrypt: Boolean): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(KEY.toByteArray(), "AES"))
        val out = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return out.joinToString("") { "%02x".format(it) }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

