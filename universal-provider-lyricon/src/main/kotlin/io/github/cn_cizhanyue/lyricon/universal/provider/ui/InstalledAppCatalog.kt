package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Immutable
data class AppItemUi(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val userLabel: String = "",
    val isPrimaryUser: Boolean = true,
)

object InstalledAppCatalog {
    @Volatile
    private var cached: List<AppItemUi> = emptyList()

    fun peek(): List<AppItemUi> = cached

    suspend fun load(context: Context, force: Boolean = false): List<AppItemUi> = withContext(UniversalImageWorkers.dispatcher) {
        if (!force) {
            val hit = cached
            if (hit.isNotEmpty()) return@withContext hit
        }
        val pm = context.applicationContext.packageManager
        val jobContext = currentCoroutineContext()
        val launcher = context.getSystemService(LauncherApps::class.java)
        val items = runCatching {
            launcher.profiles.flatMap { user ->
                launcher.getActivityList(null, user).map { activity ->
                    jobContext.ensureActive()
                    val info = activity.applicationInfo
                    AppItemUi(
                        label = activity.label.toString(),
                        packageName = info.packageName,
                        isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        userLabel = user.toString(),
                        isPrimaryUser = user == Process.myUserHandle(),
                    )
                }
            }.distinctBy { it.packageName + "|" + it.userLabel }
        }.getOrElse {
            pm.getInstalledApplications(0).map { info ->
                jobContext.ensureActive()
                AppItemUi(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.packageName,
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
        }.sortedBy { it.label.lowercase() }
        cached = items
        items
    }
}

object AppIconCache {
    private val maxMemoryKb = Runtime.getRuntime().maxMemory() / 1024
    private val loadSemaphore = Semaphore(2)
    private val cache = object : LruCache<String, Bitmap>((maxMemoryKb / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    private fun key(packageName: String, sizePx: Int): String = "$packageName:$sizePx"

    fun peek(packageName: String, sizePx: Int): Bitmap? =
        cache.get(key(packageName, sizePx))?.takeIf { !it.isRecycled }

    suspend fun load(pm: PackageManager, packageName: String, sizePx: Int): Bitmap? {
        val cacheKey = key(packageName, sizePx)
        peek(packageName, sizePx)?.let { return it }
        return loadSemaphore.withPermit {
            peek(packageName, sizePx)?.let { return@withPermit it }
            withContext(UniversalImageWorkers.dispatcher) {
                val bitmap = runCatching {
                    pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
                }.getOrNull() ?: return@withContext null
                currentCoroutineContext().ensureActive()
                val renderBitmap = runCatching {
                    bitmap.copy(Bitmap.Config.HARDWARE, false)?.also { bitmap.recycle() }
                }.getOrNull() ?: bitmap.also { it.prepareToDraw() }
                cache.put(cacheKey, renderBitmap)
                renderBitmap
            }
        }
    }
}
