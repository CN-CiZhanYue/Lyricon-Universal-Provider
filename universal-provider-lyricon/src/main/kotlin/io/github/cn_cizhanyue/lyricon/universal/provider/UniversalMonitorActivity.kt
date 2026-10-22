package io.github.cn_cizhanyue.lyricon.universal.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import io.github.cn_cizhanyue.lyricon.universal.provider.cache.UniversalLyricCache
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.Screen
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.UniversalMainApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UniversalMonitorActivity : ComponentActivity() {
    private val snapshotMap = mutableStateOf<Map<String, String>>(emptyMap())
    private val artworkState = mutableStateOf<Bitmap?>(null)
    private val cachedSongs = mutableIntStateOf(0)
    private var receiverRegistered = false
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var snapshotRevision = 0L
    private var artworkRevision: Long? = null

    private val snapshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra(UniversalSnapshotStore.EXTRA_SNAPSHOT) ?: return
            // The manifest receiver is the sole disk writer, including while the UI is closed.
            snapshotRevision++
            val map = UniversalSnapshotUi.parse(text)
            if (map != snapshotMap.value) {
                snapshotMap.value = map
            }
            val count = intent.getIntExtra(UniversalSnapshotStore.EXTRA_CACHED_SONGS, -1)
            if (count >= 0 && count != cachedSongs.intValue) cachedSongs.intValue = count
            val artwork = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UniversalSnapshotStore.EXTRA_ARTWORK, Bitmap::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UniversalSnapshotStore.EXTRA_ARTWORK) as? Bitmap
            }
            val revision = if (intent.hasExtra(UniversalSnapshotStore.EXTRA_ARTWORK_REVISION)) {
                intent.getLongExtra(UniversalSnapshotStore.EXTRA_ARTWORK_REVISION, 0L)
            } else null
            if (revision == null || revision != artworkRevision || artwork == null) {
                artworkState.value = artwork
                artworkRevision = revision
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        // Keep the app surface eligible for the device's high-refresh mode.
        // This is only a preference; the system may still lower refresh rate
        // when the app is backgrounded or the display policy requires it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                preferredRefreshRate = 120f
            }
        }
        val initialScreenName = intent.getStringExtra("extra_screen")
        val initialScreen = when (initialScreenName) {
            "players" -> Screen.PLAYERS
            "search" -> Screen.SEARCH
            "settings" -> Screen.SETTINGS
            else -> Screen.HOME
        }

        setContent {
            UniversalMainApp(
                initialScreen = initialScreen,
                snapshotMap = snapshotMap.value,
                artworkBitmap = artworkState.value,
                cachedSongsCount = cachedSongs.intValue,
                onRefreshSnapshot = {
                    applyLocalSnapshot()
                    requestSnapshot()
                    Toast.makeText(this@UniversalMonitorActivity, getString(io.github.cn_cizhanyue.lyricon.universal.provider.R.string.toast_session_refreshed), Toast.LENGTH_SHORT).show()
                },
                onClearCache = {
                    sendBroadcast(Intent(UniversalSnapshotStore.ACTION_CLEAR_CACHE).apply {
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        addFlags(0x01000000)
                    })
                },
                onConfirmNoLyric = {
                    val curTitle = snapshotMap.value["title"].orEmpty().takeUnless { it == "(missing)" }.orEmpty()
                    val curArtist = snapshotMap.value["artist"].orEmpty().takeUnless { it == "(missing)" }.orEmpty()
                    val curPkg = snapshotMap.value["package"].orEmpty().takeUnless { it == "(none)" }.orEmpty()
                    val curGen = snapshotMap.value["generation"]?.toLongOrNull() ?: 1L
                    if (curTitle.isBlank()) return@UniversalMainApp
                    val json = NoLyricPolicy.payloadJson(curTitle, curArtist, NoLyricPolicy.REASON_USER)
                    UniversalLyricCache.putManualBinding(this@UniversalMonitorActivity, curTitle, curArtist, json)
                    sendBroadcast(Intent(UniversalSnapshotStore.ACTION_INJECT_REAL_LYRIC).apply {
                        putExtra(UniversalSnapshotStore.EXTRA_OWNER_PACKAGE, curPkg)
                        putExtra(UniversalSnapshotStore.EXTRA_GENERATION, curGen)
                        putExtra(UniversalSnapshotStore.EXTRA_LYRIC_INFO, json)
                        putExtra("extra_is_manual", true)
                        putExtra("extra_track_key", "${curTitle.trim()}|${curArtist.trim()}".lowercase())
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        addFlags(0x01000000)
                    })
                    Toast.makeText(this@UniversalMonitorActivity, "已确认无词，锁屏将保持大封面", Toast.LENGTH_SHORT).show()
                }
            )
        }
        registerSnapshotReceiver()
    }

    override fun onResume() {
        super.onResume()
        applyLocalSnapshot()
        val appContext = applicationContext
        UniversalAppIo.execute {
            UniversalBindingStore.syncToSystem(appContext, UniversalBindingStore.getBoundPackages(appContext))
            requestSnapshot()
        }
    }

    override fun onDestroy() {
        uiScope.cancel()
        unregisterSnapshotReceiver()
        super.onDestroy()
    }

    private fun registerSnapshotReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(UniversalSnapshotStore.ACTION_SNAPSHOT_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(snapshotReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(snapshotReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterSnapshotReceiver() {
        if (!receiverRegistered) return
        runCatching { unregisterReceiver(snapshotReceiver) }
        receiverRegistered = false
    }

    private fun requestSnapshot() {
        sendBroadcast(Intent(UniversalSnapshotStore.ACTION_REQUEST_SNAPSHOT).apply {
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            addFlags(0x01000000)
        })
    }

    private fun applyLocalSnapshot() {
        val revision = ++snapshotRevision
        uiScope.launch {
            val map = withContext(UniversalAppIo.dispatcher) {
                UniversalSnapshotStore.read()?.let(UniversalSnapshotUi::parse)
            } ?: return@launch
            // A disk read started before a live broadcast must not restore an older track.
            if (revision != snapshotRevision) return@launch
            val previous = snapshotMap.value
            if (map["package"] != previous["package"] || map["generation"] != previous["generation"]) {
                artworkState.value = null
                artworkRevision = null
            }
            if (map != previous) snapshotMap.value = map
            map["cachedSongs"]?.toIntOrNull()?.let {
                if (it != cachedSongs.intValue) cachedSongs.intValue = it
            }
        }
    }

}
