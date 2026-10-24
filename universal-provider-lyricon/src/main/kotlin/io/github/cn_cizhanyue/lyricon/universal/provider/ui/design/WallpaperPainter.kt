package io.github.cn_cizhanyue.lyricon.universal.provider.ui.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppIo
import kotlinx.coroutines.withContext

object BackgroundImageCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    suspend fun load(context: Context, path: String?): ImageBitmap? = withContext(UniversalAppIo.dispatcher) {
        if (path.isNullOrBlank()) return@withContext null
        cache.get(path)?.let { return@withContext it.asImageBitmap() }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        var sample = 1
        while (options.outWidth / sample > 2048 || options.outHeight / sample > 2048) sample *= 2
        val bitmap = runCatching {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            })
        }.getOrNull() ?: return@withContext null
        cache.put(path, bitmap)
        bitmap.asImageBitmap()
    }
}

fun DrawScope.drawWallpaper(design: AppDesign, custom: ImageBitmap?) {
    if (custom != null) {
        drawImage(custom, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))
        drawRect(Color.Black.copy(alpha = if (design.dark) 0.42f else 0.22f))
        return
    }
    drawRect(
        Brush.linearGradient(
            listOf(design.colors.background, design.colors.surfaceHigh),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
    )
    val c = design.colors
    drawCircle(
        Brush.radialGradient(listOf(c.wallpaperA.copy(alpha = 0.34f), Color.Transparent), Offset(size.width * 0.15f, size.height * 0.1f), size.width * 0.75f),
        radius = size.width * 0.75f,
        center = Offset(size.width * 0.15f, size.height * 0.1f),
    )
    drawCircle(
        Brush.radialGradient(listOf(c.wallpaperB.copy(alpha = 0.24f), Color.Transparent), Offset(size.width * 0.95f, size.height * 0.32f), size.width * 0.65f),
        radius = size.width * 0.65f,
        center = Offset(size.width * 0.95f, size.height * 0.32f),
    )
    drawCircle(
        Brush.radialGradient(listOf(c.wallpaperC.copy(alpha = 0.2f), Color.Transparent), Offset(size.width * 0.7f, size.height * 0.9f), size.width * 0.8f),
        radius = size.width * 0.8f,
        center = Offset(size.width * 0.7f, size.height * 0.9f),
    )
}
