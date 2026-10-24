package io.github.cn_cizhanyue.lyricon.universal.provider

import android.content.Context
import android.content.Intent
import android.util.AtomicFile

import org.json.JSONArray
import java.io.File

object UniversalBindingStore {
    private const val PREFS_NAME = "universal_bindings"
    private const val KEY_BOUND_PACKAGES = "bound_packages"
    private const val KEY_FIRST_INIT = "first_init"
    private var lastBindingText: String? = null

    private val DEFAULT_MUSIC_PACKAGES = setOf(
        "com.salt.music",
        "com.apple.android.music",
        "com.spotify.music",
        "remix.myplayer",
        "com.maxmpz.audioplayer",
        "ink.trantor.coneplayer"
    )

    fun getBoundPackages(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_FIRST_INIT, false)) {
            val pm = context.packageManager
            val installed = DEFAULT_MUSIC_PACKAGES.filter { pkg ->
                runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
            }.toSet().let(PlayerBindingPolicy::sanitize)
            prefs.edit()
                .putBoolean(KEY_FIRST_INIT, true)
                .putStringSet(KEY_BOUND_PACKAGES, installed)
                .apply()
            return installed
        }
        val stored = prefs.getStringSet(KEY_BOUND_PACKAGES, emptySet()) ?: emptySet()
        val sanitized = PlayerBindingPolicy.sanitize(stored)
        if (sanitized != stored) {
            prefs.edit().putStringSet(KEY_BOUND_PACKAGES, sanitized).apply()
        }
        return sanitized
    }

    fun saveBoundPackages(context: Context, packages: Set<String>) {
        val appContext = context.applicationContext
        val sanitized = PlayerBindingPolicy.sanitize(packages).toSet()
        UniversalAppIo.execute {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_FIRST_INIT, true)
                .putStringSet(KEY_BOUND_PACKAGES, sanitized)
                .apply()
            syncToSystem(appContext, sanitized)
        }
    }

    fun syncToSystem(context: Context, packages: Set<String>) {
        persistAppBindingFile(context, packages)
        val intent = Intent(UniversalSnapshotStore.ACTION_UPDATE_PLAYER_BINDINGS).apply {
            putStringArrayListExtra(UniversalSnapshotStore.EXTRA_BOUND_PACKAGES, ArrayList(packages))
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        val sent = runCatching { context.sendBroadcast(Intent(intent).setPackage("android")) }
        UniversalDiagnostics.bridgeBindingsNotified(
            packages.size,
            "system_server",
            sent.isSuccess,
            sent.exceptionOrNull()?.javaClass?.simpleName
        )
    }
    fun persistAppBindingFile(context: Context, packages: Set<String>) {
        val sanitized = PlayerBindingPolicy.sanitize(packages)
        runCatching {
            val file = File(context.filesDir, APP_BINDING_FILE_NAME)
            val text = JSONArray(sanitized.sorted()).toString()
            if (text == lastBindingText && file.isFile) return@runCatching
            file.parentFile?.mkdirs()
            val atomic = AtomicFile(file)
            val stream = atomic.startWrite()
            try {
                stream.write(text.toByteArray(Charsets.UTF_8))
                atomic.finishWrite(stream)
                lastBindingText = text
            } catch (error: Throwable) {
                atomic.failWrite(stream)
                throw error
            }
        }
    }

    const val APP_BINDING_FILE_NAME = "universal_bound_packages.json"
}
