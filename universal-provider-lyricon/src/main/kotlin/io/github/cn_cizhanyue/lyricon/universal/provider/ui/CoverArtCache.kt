package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.content.Context
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppNetwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object CoverArtCache {
    private const val TARGET_PX = 320
    private val cache = LruCache<String, Bitmap>(64)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    fun peek(url: String): Bitmap? = if (url.isBlank()) null else cache.get(url)

    suspend fun load(context: Context, url: String): Bitmap? = withContext(decodeDispatcher) {
        if (url.isBlank()) return@withContext null
        peek(url)?.let { return@withContext it }
        UniversalAppNetwork.initialize(context)
        val bytes = runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .apply {
                    if (url.contains("126.net") || url.contains("music.163.com")) {
                        header("Referer", "https://music.163.com/")
                    }
                }
                .build()
            client.newCall(request).execute().use { it.body?.bytes() }
        }.getOrNull() ?: return@withContext null
        val bitmap = decodeSampled(bytes) ?: return@withContext null
        bitmap.prepareToDraw()
        cache.put(url, bitmap)
        bitmap
    }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        var sample = 1
        while (width / sample > TARGET_PX || height / sample > TARGET_PX) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
