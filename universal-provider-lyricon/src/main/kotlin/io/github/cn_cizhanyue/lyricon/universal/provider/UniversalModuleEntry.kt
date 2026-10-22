package io.github.cn_cizhanyue.lyricon.universal.provider

import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserHandle
import android.os.SystemClock
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticEvent
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.StructuredDiagnostics
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticHasher
import io.github.cn_cizhanyue.lyricon.universal.provider.session.ActiveSessionSelector
import io.github.cn_cizhanyue.lyricon.universal.provider.session.BluetoothLyricDemux
import io.github.cn_cizhanyue.lyricon.universal.provider.session.PlaybackClockQuality
import io.github.cn_cizhanyue.lyricon.universal.provider.session.PlaybackStates
import io.github.cn_cizhanyue.lyricon.universal.provider.session.RawSessionMetadata
import io.github.cn_cizhanyue.lyricon.universal.provider.session.ResolvedSession
import io.github.cn_cizhanyue.lyricon.universal.provider.session.SessionObservation
import io.github.cn_cizhanyue.lyricon.universal.provider.session.SessionRepository
import io.github.cn_cizhanyue.lyricon.universal.provider.session.TrackIdentityResolver
import io.github.cn_cizhanyue.lyricon.universal.provider.session.UniversalMonitorSnapshot
import io.github.cn_cizhanyue.lyricon.universal.provider.cache.UniversalLyricCache
import io.github.cn_cizhanyue.lyricon.universal.provider.lyricon.LyriconBridge
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import org.json.JSONObject

class UniversalModuleEntry : XposedModule() {
    companion object {
        private val DEFAULT_BOUND_PACKAGES = setOf(
            "com.salt.music",
            "com.apple.android.music",
            "com.spotify.music",
            "remix.myplayer",
            "com.maxmpz.audioplayer",
            "ink.trantor.coneplayer"
        )
        private const val BINDINGS_FILE_PATH = "/data/system/universal_bindings.json"
        private const val APP_BINDINGS_FILE_PATH =
            "/data/user/0/io.github.cn_cizhanyue.lyricon.universal.provider/files/universal_bound_packages.json"
    }

    private val repository = SessionRepository(
        resolver = TrackIdentityResolver(),
        selector = ActiveSessionSelector()
    )
    @Volatile
    private var latestSnapshotText: String? = null
    @Volatile
    private var lastBroadcastSnapshotText: String? = null
    @Volatile
    private var lastBroadcastArtworkRevision = 0L
    private val snapshotLock = Any()
    /**
     * Recent bound-player tracks with their latest lyric status, sent with every snapshot. Kept
     * here because the app misses broadcasts while it is frozen or not running. Written under
     * [snapshotLock].
     */
    @Volatile
    private var recentHistory = ""
    private val configExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "UniversalConfigWriter").apply { isDaemon = true }
    }
    @Volatile
    private var receiverRegistered = false
    private var lastSelectedInstanceId: String? = null
    private var lastGeneration: Long? = null
    private val isOverriding = ThreadLocal<Boolean>()
    @Volatile
    private var boundPackages: Set<String> = emptySet()
    @Volatile
    private var cachedMetadataField: Field? = null
    private var cachedPlaybackField: Field? = null
    @Volatile
    private var cachedLockField: Field? = null
    @Volatile
    private var cachedPushMetadataUpdateMethod: Method? = null
    private val realLyricsByPackage = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val activeRecords = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "UniversalLyricWorker").apply { isDaemon = true }
    }
    /**
     * SystemUI creates its media card asynchronously after the first
     * MediaSession callback.  A metadata push that happens before that card
     * exists can be missed, which is why a cold-start track used to require a
     * manual skip.  Keep a tiny, bounded replay queue for the freshly fetched
     * payload; each replay is generation guarded and therefore cannot leak a
     * previous song into the current session.
     */
    private val metadataReplayExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "UniversalLyricMetadataReplay").apply { isDaemon = true }
    }
    private val aggregator by lazy { io.github.cn_cizhanyue.lyricon.universal.provider.api.LyricsSourceAggregator() }
    @Volatile
    private var sourcePriority: List<io.github.cn_cizhanyue.lyricon.universal.provider.api.LyricsSourceAggregator.SourceId> =
        io.github.cn_cizhanyue.lyricon.universal.provider.LyricsSourceConfigStore.decode(loadSourceConfig()).mapNotNull {
            runCatching { io.github.cn_cizhanyue.lyricon.universal.provider.api.LyricsSourceAggregator.SourceId.valueOf(it) }.getOrNull()
        }
    @Volatile
    private var wordTimingEnabled: Boolean = loadWordTimingEnabled()
    private var networkEnabled: Boolean = loadNetworkEnabled()
    @Volatile
    private var translationEnabled: Boolean = loadTranslationEnabled()
    @Volatile
    private var rawLyricEnabled: Boolean = loadRawLyricEnabled()
    @Volatile
    private var debugEnabled: Boolean = loadDebugEnabled()
    @Volatile
    private var clockLineFallbackEnabled: Boolean = loadClockLineFallbackEnabled()
    /** Read-only PlaybackState grain per player; coarse clocks publish line-timed rawLyric. */
    private val clockQuality = PlaybackClockQuality()
    private val scheduledIdentitySettle = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val fetchBackoffUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private data class ArtworkSnapshot(
        val source: java.lang.ref.WeakReference<android.graphics.Bitmap>,
        val sourceGenerationId: Int,
        val bitmap: android.graphics.Bitmap,
        val revision: Long
    )
    private val nextArtworkRevision = java.util.concurrent.atomic.AtomicLong(SystemClock.elapsedRealtimeNanos())
    private val artworkByPackage = java.util.concurrent.ConcurrentHashMap<String, ArtworkSnapshot>()

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        StructuredDiagnostics.configure(debugEnabled = debugEnabled)
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = UniversalDiagnostics.COMPONENT,
                area = "bootstrap",
                event = "MODULE_LOADED",
                process = param.processName,
                reason = "framework=${frameworkName} api=${apiVersion}"
            )
        )
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val classLoader = param.classLoader
        val stubClass = classLoader.loadClass("com.android.server.media.MediaSessionRecord\$SessionStub")
        val recordClass = classLoader.loadClass("com.android.server.media.MediaSessionRecord")
        val setMetadata = stubClass.getDeclaredMethod(
            "setMetadata",
            MediaMetadata::class.java,
            java.lang.Long.TYPE,
            String::class.java
        )
        val setPlaybackState = stubClass.getDeclaredMethod(
            "setPlaybackState",
            PlaybackState::class.java
        )
        // ColorOS 17 (Android 17) refactored SessionStub: the record is now held through the
        // field "mRecord" as a WeakReference; older builds used the synthetic outer field
        // "this$0" (a direct reference). Resolve by known names first, then by any field
        // typed as the record, and unwrap the weak reference when reading.
        val recordField = arrayOf("this\$0", "mRecord").firstNotNullOfOrNull { name ->
            runCatching { stubClass.getDeclaredField(name) }.getOrNull()
        } ?: stubClass.declaredFields.firstOrNull { it.type.name == recordClass.name }
        ?: error("SessionStub record reference field not found")
        recordField.isAccessible = true
        fun recordOf(stub: Any?): Any? {
            if (stub == null) return null
            val raw = recordField.get(stub) ?: return null
            return if (raw is java.lang.ref.WeakReference<*>) raw.get() else raw
        }
        val packageField = fieldOrNull(recordClass, "mPackageName")
        val userField = fieldOrNull(recordClass, "mUserId")
        val metadataField = fieldOrNull(recordClass, "mMetadata")
        val playbackField = fieldOrNull(recordClass, "mPlaybackState")
        val contextField = fieldOrNull(recordClass, "mContext")
        val lockField = fieldOrNull(recordClass, "mLock")
        val pushMetadataUpdateMethod = runCatching {
            recordClass.getDeclaredMethod("pushMetadataUpdate").apply { isAccessible = true }
        }.getOrNull()
        val pushSessionDestroyedMethod = runCatching {
            recordClass.getDeclaredMethod("pushSessionDestroyed").apply { isAccessible = true }
        }.getOrNull()
        boundPackages = loadBoundPackages()
        repository.updateBoundFilter(boundPackages, SystemClock.elapsedRealtime())
        currentSystemContext()?.let(::ensureRequestReceiver)
        currentSystemContext()?.let { UniversalBridgeBindingNotifier.notify(it, boundPackages) }
        hook(setMetadata)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .setId("universal:setMetadata")
            .intercept { chain ->
                val stub = chain.thisObject
                val record = recordOf(stub) ?: return@intercept chain.proceed()
                val ownerPackage = packageField?.get(record) as? String ?: return@intercept chain.proceed()
                val commandContext = (contextField?.get(record) as? Context) ?: currentSystemContext()
                commandContext?.let(::ensureRequestReceiver)
                val incoming = chain.getArg(0) as? MediaMetadata
                UniversalDiagnostics.metadataHookEntered(
                    ownerPackage,
                    incoming != null,
                    incoming?.getString(MediaMetadata.METADATA_KEY_TITLE),
                    incoming?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    incoming?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                    chain.getArg(2) as? String
                )
                if (!boundPackages.contains(ownerPackage)) {
                    refreshBindingsFromDisk()
                    if (!boundPackages.contains(ownerPackage)) {
                        UniversalDiagnostics.metadataFiltered(ownerPackage, boundPackages.size)
                        return@intercept chain.proceed()
                    }
                }
                cachedMetadataField = metadataField
                cachedPlaybackField = playbackField
                cachedLockField = lockField
                cachedPushMetadataUpdateMethod = pushMetadataUpdateMethod
                activeRecords[ownerPackage] = record

                if (incoming != null) {
                    try {
                        val rewritten = enrichMetadataBeforeSet(
                            record,
                            ownerPackage,
                            incoming,
                            chain.getArg(2) as? String,
                            userField,
                            playbackField,
                            contextField
                        )
                        if (rewritten != null) {
                            val newArgs = chain.args.toTypedArray()
                            newArgs[0] = rewritten
                            return@intercept chain.proceed(newArgs)
                        }
                    } catch (error: Throwable) {
                        UniversalDiagnostics.metadataHookFailed(ownerPackage, error)
                    }
                }
                chain.proceed()
            }
        hookAfter(setPlaybackState, "setPlaybackState") {
            val record = recordOf(it) ?: return@hookAfter
            publish(record, packageField, userField, metadataField, playbackField, contextField, lockField, pushMetadataUpdateMethod)
        }
        if (pushSessionDestroyedMethod != null) {
            hookAfter(pushSessionDestroyedMethod, "pushSessionDestroyed") {
                val record = it
                val owner = packageField?.get(record) as? String
                if (owner != null) LyriconBridge.release(owner)
                val sessionId = System.identityHashCode(record).toString(16)
                repository.drop(sessionId, SystemClock.elapsedRealtime())
            }
        }
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = UniversalDiagnostics.COMPONENT,
                area = "bootstrap",
                event = "SYSTEM_SERVER_HOOKED",
                process = "system_server",
                reason = "record=${recordClass.name}"
            )
        )
    }

    private fun hookAfter(method: Method, id: String, block: (Any?) -> Unit) {
        hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .setId("universal:$id")
            .intercept(AfterHooker(block))
    }

    private class AfterHooker(private val block: (Any?) -> Unit) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            block(chain.thisObject)
            return result
        }
    }

    private fun publish(
        stub: Any,
        packageField: Field?,
        userField: Field?,
        metadataField: Field?,
        playbackField: Field?,
        contextField: Field?,
        lockField: Field?,
        pushMetadataUpdateMethod: Method?
    ) {
        val record = stub
        val ownerPackage = packageField?.get(record) as? String ?: return
        cachedMetadataField = metadataField
        cachedPlaybackField = playbackField
        cachedLockField = lockField
        cachedPushMetadataUpdateMethod = pushMetadataUpdateMethod
        activeRecords[ownerPackage] = record

        val context = (contextField?.get(record) as? Context) ?: currentSystemContext()
        context?.let(::ensureRequestReceiver)

        // GATE CHECK: If package is not in user's bound player list, completely ignore it.
        // This prevents video apps (Bilibili, YouTube) from being tracked or hijacking lyrics!
        if (!boundPackages.contains(ownerPackage)) {
            return
        }

        val metadata = metadataField?.get(record) as? MediaMetadata
        metadata?.let { rememberArtwork(ownerPackage, it) }
        val playback = playbackField?.get(record) as? PlaybackState
        LyriconBridge.updatePlayback(ownerPackage, playback)
        val state = playback?.state
        val observation = SessionObservation(
            userId = (userField?.get(record) as? Int) ?: 0,
            ownerPackage = ownerPackage,
            sessionInstanceId = System.identityHashCode(record).toString(16),
            raw = RawSessionMetadata(
                mediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                displayTitle = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                albumArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                displaySubtitle = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                ,playbackQueueItemId = playback?.activeQueueItemId?.takeIf { it >= 0L }
                ,playbackExtrasMediaId = playbackExtrasMediaId(playback)
                ,mediaUri = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
                ,positionMs = playback?.position
            ),
            playbackState = state,
            playing = PlaybackStates.isPlaying(state),
            observedAtElapsedMs = SystemClock.elapsedRealtime()
        )
        repository.setNotificationAccessGranted(true)
        repository.onListenerConnected()
        repository.upsert(observation, boundPackages)
        val snapshot = publishSnapshot(context)

        val recordSession = repository.get(observation.sessionInstanceId)
        if (recordSession != null) {
            UniversalDiagnostics.metadataObserved(recordSession, "after-setPlaybackState")
            noteIdentityGate(recordSession)
            syncSessionLyrics(record, recordSession)
            if (playback != null) observeClockQuality(record, recordSession, playback)
        }
        logSelection(snapshot)
    }

    /** Drops other generations' payloads, re-attaches the current one, and fetches when missing. */
    private fun syncSessionLyrics(record: Any, session: ResolvedSession) {
        val ownerPackage = session.descriptor.ownerPackage
        val currentGen = session.descriptor.trackGeneration
        realLyricsByPackage.keys.filter { it.startsWith("$ownerPackage|") && it != "$ownerPackage|$currentGen" }.forEach {
            realLyricsByPackage.remove(it)
        }
        overlayMetadata(record, session, cachedMetadataField, cachedLockField, cachedPushMetadataUpdateMethod)

        val title = session.descriptor.title
        if (realLyricsByPackage["$ownerPackage|$currentGen"].isNullOrBlank() && !title.isNullOrBlank()) {
            triggerBackgroundFetch(
                ownerPackage,
                title,
                session.descriptor.artist.orEmpty(),
                session.descriptor.album,
                session.descriptor.durationMs,
                currentGen
            )
        }
    }

    private fun logSelection(snapshot: UniversalMonitorSnapshot) {
        val selected = snapshot.selected ?: return
        if (lastSelectedInstanceId != selected.descriptor.sessionInstanceId) {
            UniversalDiagnostics.sessionSelected(selected, snapshot.selectionRevision)
            lastSelectedInstanceId = selected.descriptor.sessionInstanceId
            lastGeneration = selected.descriptor.trackGeneration
        } else if (lastGeneration != selected.descriptor.trackGeneration) {
            UniversalDiagnostics.trackChanged(selected)
            lastGeneration = selected.descriptor.trackGeneration
        }
    }

    /**
     * A held partial identity change (see [TrackIdentityResolver]) must still land when the player
     * sends no further callback, so re-observe the stored payload once its settle window ends.
     */
    private fun noteIdentityGate(session: ResolvedSession) {
        if (session.identityGate != TrackIdentityResolver.GATE_STANDARD) {
            UniversalDiagnostics.identityGate(session)
        }
        val settleAt = session.identitySettleAtElapsedMs ?: return
        val sessionId = session.descriptor.sessionInstanceId
        if (scheduledIdentitySettle.put(sessionId, settleAt) == settleAt) return
        val delayMs = (settleAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L) + 50L
        metadataReplayExecutor.schedule(
            { settleIdentity(session.descriptor.ownerPackage, sessionId, settleAt) },
            delayMs,
            java.util.concurrent.TimeUnit.MILLISECONDS
        )
    }

    private fun settleIdentity(ownerPackage: String, sessionId: String, settleAt: Long) {
        runCatching {
            scheduledIdentitySettle.remove(sessionId, settleAt)
            val latest = repository.get(sessionId) ?: return
            // A newer payload already resolved or restarted the hold.
            if (latest.identitySettleAtElapsedMs != settleAt) return
            repository.upsert(latest.observation.copy(observedAtElapsedMs = SystemClock.elapsedRealtime()), boundPackages)
            val settled = repository.get(sessionId) ?: return
            noteIdentityGate(settled)
            activeRecords[ownerPackage]?.let { syncSessionLyrics(it, settled) }
            logSelection(publishSnapshot(null))
        }.onFailure { error -> UniversalDiagnostics.identitySettleFailed(ownerPackage, error) }
    }

    private fun observeClockQuality(record: Any, session: ResolvedSession, playback: PlaybackState) {
        val ownerPackage = session.descriptor.ownerPackage
        val before = clockQuality.verdict(ownerPackage)
        val after = clockQuality.observe(
            ownerPackage = ownerPackage,
            sessionId = session.descriptor.sessionInstanceId,
            generation = session.descriptor.trackGeneration,
            state = playback.state,
            positionMs = playback.position,
            updateTimeMs = playback.lastPositionUpdateTime,
            speed = playback.playbackSpeed
        )
        if (after == before) return
        UniversalDiagnostics.clockQualityChanged(ownerPackage, before.name, clockQuality.stats(ownerPackage))
        // Downgrade the current track at once; a steadier clock returns to word timing from the
        // next track, so a verdict change cannot flip one track back and forth.
        if (after == PlaybackClockQuality.Verdict.COARSE && clockLineFallbackEnabled && wordTimingEnabled) {
            realLyricsByPackage.replaceAll { key, value ->
                if (key.substringBefore('|') == ownerPackage) publishedPayload(value, ownerPackage) else value
            }
            overlayMetadata(record, session, cachedMetadataField, cachedLockField, cachedPushMetadataUpdateMethod, force = true)
        }
        publishSnapshot(null)
    }

    private fun clockLineFallbackActive(ownerPackage: String): Boolean =
        clockLineFallbackEnabled && clockQuality.verdict(ownerPackage) == PlaybackClockQuality.Verdict.COARSE

    private fun enrichMetadataBeforeSet(
        record: Any,
        ownerPackage: String,
        metadata: MediaMetadata,
        metadataDescription: String?,
        userField: Field?,
        playbackField: Field?,
        contextField: Field?
    ): MediaMetadata? {
        rememberArtwork(ownerPackage, metadata)
        val playback = playbackField?.get(record) as? PlaybackState
        val state = playback?.state
        val observation = SessionObservation(
            userId = (userField?.get(record) as? Int) ?: 0,
            ownerPackage = ownerPackage,
            sessionInstanceId = System.identityHashCode(record).toString(16),
            raw = RawSessionMetadata(
                mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                displayTitle = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                albumArtist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                displaySubtitle = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                ,playbackQueueItemId = playback?.activeQueueItemId?.takeIf { it >= 0L }
                ,playbackExtrasMediaId = playbackExtrasMediaId(playback)
                ,mediaUri = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
                ,positionMs = playback?.position
            ),
            playbackState = state,
            playing = PlaybackStates.isPlaying(state),
            observedAtElapsedMs = SystemClock.elapsedRealtime()
        )
        repository.setNotificationAccessGranted(true)
        repository.onListenerConnected()
        repository.upsert(observation, boundPackages)

        val context = (contextField?.get(record) as? Context) ?: currentSystemContext()
        context?.let(::ensureRequestReceiver)

        val recordSession = repository.get(observation.sessionInstanceId)
        if (recordSession == null) {
            publishSnapshot(context)
            return null
        }
        UniversalDiagnostics.metadataObserved(recordSession, "before-setMetadata")
        noteIdentityGate(recordSession)
        val currentGen = recordSession.descriptor.trackGeneration

        realLyricsByPackage.keys.filter { it.startsWith("$ownerPackage|") && it != "$ownerPackage|$currentGen" }.forEach {
            realLyricsByPackage.remove(it)
        }

        // During Salt cold start the resolver can temporarily reject the
        // stored title as Bluetooth lyric churn even though this incoming
        // metadata already contains the clean track identity. Use it as a
        // guarded lookup fallback so the first song does not require a skip.
        val descriptionParts = metadataDescription?.split(",").orEmpty().map { it.trim() }
        val descriptionTitle = descriptionParts.firstOrNull()
            ?.takeIf { it.isNotEmpty() && !BluetoothLyricDemux.looksLikeLyricTitle(it) }
        val descriptionArtist = descriptionParts.getOrNull(1)?.takeIf { it.isNotEmpty() }
        val incomingTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.trim()?.takeIf { it.isNotEmpty() && !BluetoothLyricDemux.looksLikeLyricTitle(it) }
        val incomingArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.trim()?.takeIf { it.isNotEmpty() }
        val recovered = if (recordSession.descriptor.title == null && incomingTitle == null) {
            recoverIdentityFromCache(
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                recordSession.descriptor.artist ?: incomingArtist,
                recordSession.descriptor.album ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                recordSession.descriptor.durationMs
            )
        } else null
        val title = incomingTitle ?: recordSession.descriptor.title ?: recovered?.first ?: descriptionTitle
        val artist = incomingArtist ?: recordSession.descriptor.artist ?: descriptionArtist
        val album = recordSession.descriptor.album
        val durationMs = recordSession.descriptor.durationMs
        val cacheKey = "$ownerPackage|$currentGen"

        var realJson = realLyricsByPackage[cacheKey]
        if (realJson.isNullOrBlank() && !title.isNullOrBlank()) {
            val manualKey = bindingKey(title, artist.orEmpty())
            val manual = loadManualBinding(manualKey)
            if (!manual.isNullOrBlank()) {
                realJson = updateGeneration(manual, currentGen, ownerPackage)
                realLyricsByPackage[cacheKey] = realJson
            } else {
                val cached = loadCachedLyric(title, artist.orEmpty())
                if (!cached.isNullOrBlank()) {
                    realJson = updateGeneration(cached, currentGen, ownerPackage)
                    realLyricsByPackage[cacheKey] = realJson
                } else {
                    triggerBackgroundFetch(ownerPackage, title, artist.orEmpty(), album, durationMs, currentGen)
                }
            }
        }
        // Publish after the lookup so a cache hit is not reported as pending.
        publishSnapshot(context)

        LyriconBridge.placeholder(ownerPackage, title, artist, durationMs, playback, currentGen, observation.sessionInstanceId)

        if (!realJson.isNullOrBlank() && LyriconBridge.payloadTitleMatches(realJson, title)) {
            UniversalDiagnostics.lyricInfoAttached(ownerPackage, currentGen, "lyricon-push")
            LyriconBridge.publish(ownerPackage, realJson, title, artist, durationMs, playback, currentGen, wordTimingEnabled, observation.sessionInstanceId)
            return null
        }
        if (!realJson.isNullOrBlank()) {
            realLyricsByPackage.remove("$ownerPackage|$currentGen")
        }
        return null
    }

    private fun overlayMetadata(
        record: Any,
        session: ResolvedSession,
        metadataField: Field?,
        lockField: Field?,
        pushMetadataUpdateMethod: Method?,
        force: Boolean = false
    ) {
        val ownerPackage = session.descriptor.ownerPackage
        if (!boundPackages.contains(ownerPackage)) {
            return
        }
        val gen = session.descriptor.trackGeneration
        val realJson = realLyricsByPackage["$ownerPackage|$gen"]
        if (realJson.isNullOrBlank()) {
            return
        }
        if (!LyriconBridge.payloadTitleMatches(realJson, session.descriptor.title)) {
            return
        }
        val playback = cachedPlaybackField?.get(record) as? PlaybackState
        LyriconBridge.publish(
            ownerPackage,
            realJson,
            session.descriptor.title,
            session.descriptor.artist,
            session.descriptor.durationMs,
            playback,
            gen,
            wordTimingEnabled,
            session.descriptor.sessionInstanceId,
        )
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = UniversalDiagnostics.COMPONENT,
                area = "lyricon",
                event = "LYRICON_PUSH",
                session = session.descriptor.sessionInstanceId,
                generation = gen,
                reason = "bytes=${realJson.length}"
            )
        )
    }


    private fun ensureRequestReceiver(context: Context) {
        if (receiverRegistered) return
        synchronized(this) {
            if (receiverRegistered) return
            try {
                val filter = IntentFilter().apply {
                    addAction(UniversalSnapshotStore.ACTION_REQUEST_SNAPSHOT)
                    addAction(UniversalSnapshotStore.ACTION_UPDATE_PLAYER_BINDINGS)
                    addAction(UniversalSnapshotStore.ACTION_INJECT_REAL_LYRIC)
                    addAction(UniversalSnapshotStore.ACTION_UPDATE_SOURCE_CONFIG)
                    addAction(UniversalSnapshotStore.ACTION_CLEAR_CACHE)
                }
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        when (intent?.action) {
                            UniversalSnapshotStore.ACTION_REQUEST_SNAPSHOT -> {
                                publishSnapshot(context, force = true)
                            }
                            UniversalSnapshotStore.ACTION_UPDATE_PLAYER_BINDINGS -> {
                                val list = intent.getStringArrayListExtra(UniversalSnapshotStore.EXTRA_BOUND_PACKAGES)
                                if (list != null) {
                                    boundPackages = PlayerBindingPolicy.sanitize(list.toSet())
                                    persistBoundPackages(boundPackages)
                                    repository.updateBoundFilter(boundPackages, SystemClock.elapsedRealtime())
                                    UniversalBridgeBindingNotifier.notify(context, boundPackages)
                                    publishSnapshot(context)
                                    StructuredDiagnostics.logInfo(
                                        DiagnosticEvent(
                                            component = UniversalDiagnostics.COMPONENT,
                                            area = "bindings",
                                            event = "PLAYER_BINDINGS_UPDATED",
                                            reason = "count=${boundPackages.size}"
                                        )
                                    )
                                } else {
                                    StructuredDiagnostics.logWarning(
                                        DiagnosticEvent(
                                            component = UniversalDiagnostics.COMPONENT,
                                            area = "bindings",
                                            event = "PLAYER_BINDINGS_UPDATE_EMPTY",
                                            reason = "missing extra_bound_packages"
                                        )
                                    )
                                }
                            }
                            UniversalSnapshotStore.ACTION_INJECT_REAL_LYRIC -> {
                                val pkg = intent.getStringExtra(UniversalSnapshotStore.EXTRA_OWNER_PACKAGE) ?: return
                                val gen = intent.getLongExtra(UniversalSnapshotStore.EXTRA_GENERATION, -1L)
                                val lyricInfo = intent.getStringExtra(UniversalSnapshotStore.EXTRA_LYRIC_INFO) ?: return
                                val isManual = intent.getBooleanExtra("extra_is_manual", false)
                                val trackKey = intent.getStringExtra("extra_track_key")
                                if (isManual && !trackKey.isNullOrBlank()) {
                                    saveManualBinding(trackKey, lyricInfo)
                                    val payload = runCatching { JSONObject(lyricInfo) }.getOrNull()
                                    saveCachedLyric(
                                        payload?.optString("songName").orEmpty().ifBlank { trackKey.substringBefore("|") },
                                        payload?.optString("artist").orEmpty().ifBlank { trackKey.substringAfter("|", "") },
                                        lyricInfo
                                    )
                                }
                                val cacheKey = if (gen > 0) "$pkg|$gen" else pkg
                                realLyricsByPackage[cacheKey] = publishedPayload(lyricInfo, pkg)
                                val activeRecord = activeRecords[pkg]
                                val activeSession = repository.snapshot().sessions.find { it.descriptor.ownerPackage == pkg }
                                if (activeRecord != null && activeSession != null && (gen <= 0 || activeSession.descriptor.trackGeneration == gen)) {
                                    overlayMetadata(activeRecord, activeSession, cachedMetadataField, cachedLockField, cachedPushMetadataUpdateMethod, force = true)
                                }
                                publishSnapshot(context)
                            }
                            UniversalSnapshotStore.ACTION_UPDATE_SOURCE_CONFIG -> {
                                val encoded = intent.getStringExtra(UniversalSnapshotStore.EXTRA_SOURCE_PRIORITY) ?: return
                                val priority = LyricsSourceConfigStore.decode(encoded).mapNotNull {
                                    runCatching { io.github.cn_cizhanyue.lyricon.universal.provider.api.LyricsSourceAggregator.SourceId.valueOf(it) }.getOrNull()
                                }
                                val wordTiming = intent.getBooleanExtra(UniversalSnapshotStore.EXTRA_WORD_TIMING_ENABLED, wordTimingEnabled)
                                val translation = intent.getBooleanExtra(UniversalSnapshotStore.EXTRA_TRANSLATION_ENABLED, translationEnabled)
                                val rawLyric = intent.getBooleanExtra(UniversalSnapshotStore.EXTRA_RAW_LYRIC_ENABLED, rawLyricEnabled)
                                val debug = intent.getBooleanExtra(UniversalSnapshotStore.EXTRA_DEBUG_ENABLED, debugEnabled)
                                val clockFallback = intent.getBooleanExtra(
                                    UniversalSnapshotStore.EXTRA_CLOCK_LINE_FALLBACK_ENABLED,
                                    clockLineFallbackEnabled
                                )
                                val priorityChanged = priority != sourcePriority
                                val wordTimingChanged = wordTiming != wordTimingEnabled
                                val translationChanged = translation != translationEnabled
                                val rawLyricChanged = rawLyric != rawLyricEnabled
                                val debugChanged = debug != debugEnabled
                                val network = intent.getBooleanExtra(UniversalSnapshotStore.EXTRA_NETWORK_ENABLED, networkEnabled)
                                val networkChanged = network != networkEnabled
                                val clockFallbackChanged = clockFallback != clockLineFallbackEnabled
                                sourcePriority = priority
                                wordTimingEnabled = wordTiming
                                translationEnabled = translation
                                rawLyricEnabled = rawLyric
                                debugEnabled = debug
                                clockLineFallbackEnabled = clockFallback
                                networkEnabled = network
                                // Only a processing downgrade may rewrite the in-memory payloads;
                                // upgrades apply from the full cached payload when the next track loads.
                                if ((wordTimingChanged && !wordTiming) || (rawLyricChanged && !rawLyric) ||
                                    (translationChanged && !translation) || (clockFallbackChanged && clockFallback)) {
                                    realLyricsByPackage.replaceAll { key, value -> publishedPayload(value, key.substringBefore('|')) }
                                }
                                if (debugChanged) {
                                    StructuredDiagnostics.configure(debugEnabled = debug)
                                }
                                configExecutor.execute {
                                    if (priorityChanged) persistSourceConfig(priority)
                                    if (wordTimingChanged) persistWordTimingEnabled(wordTiming)
                                    if (translationChanged) persistTranslationEnabled(translation)
                                    if (rawLyricChanged) persistRawLyricEnabled(rawLyric)
                                    if (debugChanged) persistDebugEnabled(debug)
                                    if (clockFallbackChanged) persistClockLineFallbackEnabled(clockFallback)
                                    if (networkChanged) persistNetworkEnabled(network)
                                }
                                UniversalDiagnostics.sourceConfigUpdated(sourcePriority.map { it.name })
                                // The home screen shows whether the current player is on the clock fallback.
                                publishSnapshot(context)
                            }
                            UniversalSnapshotStore.ACTION_CLEAR_CACHE -> {
                                val cleared = UniversalLyricCache.clear()
                                realLyricsByPackage.clear()
                                val ctx = currentSystemContext()
                                val snap = latestSnapshotText ?: format(null, 0, 0)
                                latestSnapshotText = upsertCachedSongs(snap)
                                if (ctx != null) {
                                    sendSnapshotBroadcast(ctx, latestSnapshotText ?: snap, cacheCleared = cleared.songs)
                                }
                            }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
                receiverRegistered = true
                UniversalDiagnostics.commandReceiverRegistered(true)
                requestPlayerBindingsSync(context)
            } catch (error: Throwable) {
                UniversalDiagnostics.commandReceiverRegistered(false, error.javaClass.simpleName)
            }
        }
    }

    private fun requestPlayerBindingsSync(context: Context) {
        val intent = Intent(UniversalSnapshotStore.ACTION_REQUEST_PLAYER_BINDINGS_SYNC).apply {
            setPackage(BuildConfig.APPLICATION_ID)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            addFlags(0x01000000)
        }
        val sent = UniversalBridgeBindingNotifier.sendSafely(context, intent)
        UniversalDiagnostics.bridgeBindingsNotified(
            boundPackages.size,
            "provider-bindings-request",
            sent.isSuccess,
            sent.exceptionOrNull()?.javaClass?.simpleName
        )
    }

    /**
     * Formats the live session and lyric state, so a finished fetch reaches the UI without waiting
     * for the player's next callback. Serialized: a slower thread cannot broadcast an older status.
     */
    private fun publishSnapshot(context: Context?, force: Boolean = false): UniversalMonitorSnapshot =
        synchronized(snapshotLock) {
            val snapshot = repository.snapshot()
            val descriptor = snapshot.selected?.descriptor
            val lyricPayload = descriptor?.let { lyricPayloadFor(it.ownerPackage, it.trackGeneration) }
            if (descriptor != null && boundPackages.contains(descriptor.ownerPackage)) {
                UniversalLyricHistory.row(
                    descriptor.title,
                    descriptor.artist,
                    lyricSourceOf(lyricPayload),
                    lyricStatusOf(lyricPayload)
                )?.let { recentHistory = UniversalLyricHistory.moveToTop(recentHistory, it) }
            }
            val formatted = format(
                snapshot.selected,
                snapshot.sessions.size,
                snapshot.selectionRevision,
                lyricPayload
            )
            latestSnapshotText = formatted
            (context ?: currentSystemContext())?.let { sendSnapshotBroadcast(it, formatted, force = force) }
            snapshot
        }

    private fun sendSnapshotBroadcast(context: Context, text: String, cacheCleared: Int? = null, force: Boolean = false) {
        runCatching {
            val owner = text.lineSequence().firstOrNull { it.startsWith("package=") }?.substringAfter('=')
            val artwork = owner?.let(artworkByPackage::get)
            val artworkRevision = artwork?.revision ?: 0L
            if (!force && cacheCleared == null && text == lastBroadcastSnapshotText &&
                artworkRevision == lastBroadcastArtworkRevision) {
                return@runCatching
            }
            val intent = Intent(UniversalSnapshotStore.ACTION_SNAPSHOT_UPDATE).apply {
                setPackage(BuildConfig.APPLICATION_ID)
                putExtra(UniversalSnapshotStore.EXTRA_SNAPSHOT, text)
                val count = text.lineSequence().firstOrNull { it.startsWith("cachedSongs=") }
                    ?.substringAfter('=')?.toIntOrNull() ?: 0
                putExtra(UniversalSnapshotStore.EXTRA_CACHED_SONGS, count)
                artwork?.let { putExtra(UniversalSnapshotStore.EXTRA_ARTWORK, it.bitmap) }
                putExtra(UniversalSnapshotStore.EXTRA_ARTWORK_REVISION, artworkRevision)
                if (cacheCleared != null) {
                    putExtra(UniversalSnapshotStore.EXTRA_CACHE_CLEARED, cacheCleared)
                }
                addFlags(0x00000020)
            }
            val allUser = runCatching {
                Class.forName("android.os.UserHandle").getDeclaredField("ALL").get(null) as? UserHandle
            }.getOrNull()
            if (allUser != null) {
                context.sendBroadcastAsUser(intent, allUser)
            } else {
                context.sendBroadcast(intent)
            }
            lastBroadcastSnapshotText = text
            lastBroadcastArtworkRevision = artworkRevision
        }
    }

    private fun rememberArtwork(ownerPackage: String, metadata: MediaMetadata) {
        val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: run {
                artworkByPackage.remove(ownerPackage)
                return
            }
        artworkByPackage.compute(ownerPackage) { _, previous ->
            if (previous != null && previous.source.get() === bitmap &&
                previous.sourceGenerationId == bitmap.generationId) {
                return@compute previous
            }
            val longest = maxOf(bitmap.width, bitmap.height)
            val preview = if (longest > 256) {
                val scale = 256f / longest.toFloat()
                android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap
            // Re-parcelled metadata can carry identical pixels in a new Bitmap object.
            // Compare only the bounded preview here, never in the app's UI callback.
            if (previous != null && runCatching { previous.bitmap.sameAs(preview) }.getOrDefault(false)) {
                return@compute previous.copy(
                    source = java.lang.ref.WeakReference(bitmap),
                    sourceGenerationId = bitmap.generationId
                )
            }
            ArtworkSnapshot(
                java.lang.ref.WeakReference(bitmap),
                bitmap.generationId,
                preview,
                nextArtworkRevision.incrementAndGet()
            )
        }
    }

    private fun loadBoundPackages(): Set<String> {
        val appBindings = readBindingFile(APP_BINDINGS_FILE_PATH)
        if (appBindings != null) {
            persistBoundPackages(appBindings)
            return appBindings
        }
        val systemBindings = readBindingFile(BINDINGS_FILE_PATH)
        if (!systemBindings.isNullOrEmpty()) {
            return systemBindings
        }
        return PlayerBindingPolicy.sanitize(DEFAULT_BOUND_PACKAGES)
    }

    private fun readBindingFile(path: String): Set<String>? {
        val file = java.io.File(path)
        if (!file.isFile) return null
        return runCatching {
            val array = org.json.JSONArray(file.readText())
            val set = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                set.add(array.getString(i))
            }
            PlayerBindingPolicy.sanitize(set)
        }.getOrNull()
    }

    private fun refreshBindingsFromDisk(): Boolean {
        val loaded = loadBoundPackages()
        if (loaded == boundPackages) return false
        boundPackages = loaded
        persistBoundPackages(loaded)
        repository.updateBoundFilter(loaded, SystemClock.elapsedRealtime())
        currentSystemContext()?.let { UniversalBridgeBindingNotifier.notify(it, loaded) }
        UniversalDiagnostics.playerBindingsReloaded(loaded.size, "disk")
        return true
    }

    private fun persistBoundPackages(packages: Set<String>) {
        runCatching {
            val array = org.json.JSONArray(PlayerBindingPolicy.sanitize(packages))
            java.io.File(BINDINGS_FILE_PATH).writeText(array.toString())
        }
    }

    private fun currentSystemContext(): Context? {
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
            activityThreadClass.getDeclaredMethod("getSystemContext").invoke(currentActivityThread) as? Context
        }.getOrNull()
    }

    private fun lyricPayloadFor(ownerPackage: String, generation: Long): JSONObject? =
        (realLyricsByPackage["$ownerPackage|$generation"] ?: realLyricsByPackage[ownerPackage])
            ?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun lyricSourceOf(payload: JSONObject?): String =
        payload?.optString("source").orEmpty().ifBlank { "none" }

    private fun lyricStatusOf(payload: JSONObject?): String = when {
        payload == null -> "pending"
        payload.optBoolean("noLyric") -> "noLyric"
        payload.optString("lyric").isNotBlank() -> "success"
        else -> "failed"
    }

    private fun format(
        selected: ResolvedSession?,
        sessionCount: Int,
        selectionRevision: Long,
        lyricPayload: JSONObject? = selected?.descriptor?.let { lyricPayloadFor(it.ownerPackage, it.trackGeneration) }
    ): String {
        val descriptor = selected?.descriptor
        val isBound = boundPackages.contains(descriptor?.ownerPackage)
        return buildString {
            append("source=system_server\n")
            append("writeBack=true network=false\n")
            append("boundPlayer=").append(isBound).append('\n')
            append("boundCount=").append(boundPackages.size).append('\n')
            append("selectionRevision=").append(selectionRevision).append('\n')
            append("sessions=").append(sessionCount).append('\n')
            append("package=").append(descriptor?.ownerPackage ?: "(none)").append('\n')
            append("title=").append(descriptor?.title ?: "(missing)").append('\n')
            append("artist=").append(descriptor?.artist ?: "(missing)").append('\n')
            append("album=").append(descriptor?.album ?: "(missing)").append('\n')
            append("durationMs=").append(descriptor?.durationMs?.toString() ?: "unknown").append('\n')
            append("generation=").append(descriptor?.trackGeneration ?: 0).append('\n')
            append("titlePolluted=").append(descriptor?.titlePolluted ?: false).append('\n')
            append("rawTitle=").append(descriptor?.rawTitle ?: "(missing)").append('\n')
            append("demux=").append(selected?.demuxSource ?: "none").append('\n')
            append("playbackState=").append(descriptor?.playbackState ?: -1).append('\n')
            append("lyricSource=").append(lyricSourceOf(lyricPayload)).append('\n')
            append("lyricStatus=").append(lyricStatusOf(lyricPayload)).append('\n')
            append("lyricClockFallback=").append(
                descriptor != null && wordTimingEnabled && rawLyricEnabled && clockLineFallbackActive(descriptor.ownerPackage)
            ).append('\n')
            append("cachedSongs=").append(UniversalLyricCache.getCacheCount()).append('\n')
            append(UniversalLyricHistory.snapshotLines(recentHistory))
        }
    }

    private fun fieldOrNull(type: Class<*>, name: String): Field? =
        runCatching { type.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()

    private fun triggerBackgroundFetch(
        ownerPackage: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        generation: Long
    ) {
        // Bluetooth projection changes TITLE every few seconds. Fetches are
        // keyed by the resolved session generation instead of display text,
        // with a short retry backoff for an empty/failed response.
        val fetchKey = "$ownerPackage|$generation"
        val now = SystemClock.elapsedRealtime()
        val previousBackoff = fetchBackoffUntil[fetchKey] ?: 0L
        if (now < previousBackoff) return
        fetchBackoffUntil[fetchKey] = now + 5_000L

        backgroundExecutor.execute {
            runCatching {
                android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.LAX)
                val manualKey = bindingKey(title, artist)
                val manual = loadManualBinding(manualKey)
                if (!manual.isNullOrBlank()) {
                    val negative = runCatching { JSONObject(manual).optBoolean("noLyric", false) }.getOrDefault(false)
                    UniversalDiagnostics.cacheLookup("manual", generation, hit = true, noLyric = negative)
                    UniversalDiagnostics.fetchStarted(ownerPackage, generation, "manual-cache")
                    if (negative) {
                        return@execute
                    }
                    val updated = updateGeneration(manual, generation, ownerPackage)
                    applyFetchedLyric(ownerPackage, generation, updated, title, artist)
                    fetchBackoffUntil.remove(fetchKey)
                    UniversalDiagnostics.fetchFinished(ownerPackage, generation, "manual-cache", updated.length)
                    return@execute
                }
                UniversalDiagnostics.cacheLookup("manual", generation, hit = false, noLyric = false)

                val cached = loadCachedLyric(title, artist)
                if (!cached.isNullOrBlank()) {
                    val negative = runCatching { JSONObject(cached).optBoolean("noLyric", false) }.getOrDefault(false)
                    UniversalDiagnostics.cacheLookup("auto", generation, hit = true, noLyric = negative)
                    UniversalDiagnostics.fetchStarted(ownerPackage, generation, "auto-cache")
                    if (negative) {
                        return@execute
                    }
                    val updated = updateGeneration(cached, generation, ownerPackage)
                    applyFetchedLyric(ownerPackage, generation, updated, title, artist)
                    fetchBackoffUntil.remove(fetchKey)
                    UniversalDiagnostics.fetchFinished(ownerPackage, generation, "auto-cache", updated.length)
                    return@execute
                }
                UniversalDiagnostics.cacheLookup("auto", generation, hit = false, noLyric = false)

                if (!networkEnabled) {
                    UniversalDiagnostics.fetchFinished(ownerPackage, generation, "network-disabled", 0)
                    return@execute
                }
                UniversalDiagnostics.fetchStarted(
                    ownerPackage,
                    generation,
                    "network priority=" + sourcePriority.joinToString(",") { it.name } +
                        " titleHash=" + DiagnosticHasher.sha256(title).take(12) +
                        " artistHash=" + DiagnosticHasher.sha256(artist).take(12) +
                        " durationMs=" + (durationMs?.toString() ?: "unknown") +
                        " wordTiming=" + wordTimingEnabled
                )
                val query = io.github.cn_cizhanyue.lyricon.universal.provider.api.TrackQuery(
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    generation = generation
                )
                val report = aggregator.fetchFirst(query, sourcePriority)
                val result = report.result
                val instrumental = result != null && io.github.cn_cizhanyue.lyricon.universal.provider.api.SongMatchScorer.isInstrumentalOrCreditsOnly(result.lyric)
                val usable = result != null && result.lyric.isNotBlank() && !instrumental
                val outcome = NoLyricPolicy.decide(
                    hasUsableLyric = usable,
                    instrumental = instrumental,
                    transientFailure = report.transientFailure && !usable
                )
                if (outcome == NoLyricPolicy.Outcome.PUBLISH_LYRICS && result != null) {
                    val plainLyric = UniversalLyricPayloadPolicy.stripWordTiming(result.lyric)
                    val alignedTranslation = run {
                        val primary = io.github.cn_cizhanyue.lyricon.universal.provider.api.StructuredLyrics.parseLrc(plainLyric)
                        val trans = io.github.cn_cizhanyue.lyricon.universal.provider.api.StructuredLyrics.parseLrc(result.translationLyric)
                        io.github.cn_cizhanyue.lyricon.universal.provider.api.StructuredLyrics.toTranslationLrc(
                            io.github.cn_cizhanyue.lyricon.universal.provider.api.StructuredLyrics.merge(primary, trans)
                        ).takeIf { it.isNotBlank() }
                    }
                    // Cache the complete payload; publishing applies the processing switches, so
                    // turning a switch back on does not need a cache clear.
                    val json = JSONObject().apply {
                        put("songName", result.title)
                        put("artist", result.artist)
                        put("album", album.orEmpty())
                        put("durationMs", durationMs ?: 0L)
                        put("songId", "universal-$generation")
                        put("lyricType", 0)
                        put("id", "")
                        put("noLyric", false)
                        put("lyric", plainLyric)
                        if (!result.rawLyric.isNullOrBlank()) {
                            put("rawLyric", result.rawLyric)
                        }
                        if (!alignedTranslation.isNullOrBlank()) {
                            put("transLyric", alignedTranslation)
                            put("translationLyric", alignedTranslation)
                            put("translationLanguageTag", java.util.Locale.getDefault().toLanguageTag())
                        }
                        put("provider", "io.github.cn_cizhanyue.lyricon.universal.provider")
                        put("source", result.source)
                        put("sessionGeneration", generation)
                        put("remark", "[CLLUniversal]")
                    }.toString()

                    saveCachedLyric(title, artist, json)
                    val published = applyFetchedLyric(ownerPackage, generation, json, title, artist)
                    fetchBackoffUntil.remove(fetchKey)
                    UniversalDiagnostics.fetchFinished(ownerPackage, generation, result.source, json.length)
                    UniversalDiagnostics.lyricInfoComposed(
                        ownerPackage = ownerPackage,
                        generation = generation,
                        source = result.source,
                        lyricChars = result.lyric.length,
                        rawLyricAttached = published.rawAttached,
                        translationAttached = translationEnabled && !alignedTranslation.isNullOrBlank(),
                        wordTimingEnabled = wordTimingEnabled
                    )
                } else {
                    val rejectReason = when {
                        report.transientFailure -> "transient:" + report.reason
                        instrumental -> "instrumentalOrCreditsOnly source=" + (result?.source ?: "none")
                        else -> "miss:" + report.reason
                    }
                    UniversalDiagnostics.fetchEmpty(ownerPackage, generation, rejectReason)
                    if (outcome == NoLyricPolicy.Outcome.AUTO_NEGATIVE) {
                        val reason = NoLyricPolicy.autoReason(instrumental)
                        val noLyricJson = NoLyricPolicy.payloadJson(title, artist, reason)
                        saveCachedLyric(title, artist, noLyricJson)
                        applyFetchedLyric(ownerPackage, generation, noLyricJson, title, artist)
                    } else {
                        fetchBackoffUntil.remove(fetchKey)
                    }
                }
            }.onFailure { error ->
                UniversalDiagnostics.fetchEmpty(
                    ownerPackage,
                    generation,
                    "exception=${error.javaClass.simpleName}"
                )
                fetchBackoffUntil.remove(fetchKey)
            }
        }
    }

    private fun playbackExtrasMediaId(state: PlaybackState?): String? {
        val extras = state?.extras ?: return null
        val keys = arrayOf(
            "androidx.media.PlaybackStateCompat.Extras.KEY_MEDIA_ID",
            "android.media.playback.extra.MEDIA_ID",
            "media_id"
        )
        return keys.firstNotNullOfOrNull { key ->
            extras.getString(key)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun updateGeneration(jsonStr: String, generation: Long, ownerPackage: String): String {
        return runCatching {
            val json = JSONObject(jsonStr)
            json.put("sessionGeneration", generation)
            json.put("songId", "universal-$generation")
            applyWordTimingPolicy(json, ownerPackage)
            json.toString()
        }.getOrDefault(jsonStr)
    }

    private fun applyFetchedLyric(
        ownerPackage: String,
        generation: Long,
        json: String,
        title: String,
        artist: String
    ): UniversalLyricPayloadPolicy.Result {
        val payload = JSONObject(json)
        val published = applyWordTimingPolicy(payload, ownerPackage)
        realLyricsByPackage["$ownerPackage|$generation"] = payload.toString()
        replayMetadataForGeneration(ownerPackage, generation)
        // The track may already have been skipped, so its row takes the result where it stands.
        UniversalLyricHistory.row(title, artist, lyricSourceOf(payload), lyricStatusOf(payload))?.let { row ->
            synchronized(snapshotLock) { recentHistory = UniversalLyricHistory.replaceInPlace(recentHistory, row) }
        }
        publishSnapshot(null)
        // The first callback can race SystemUI's notification/card creation.
        // Replay after the normal load window as a fail-open compatibility
        // measure for ColorOS builds that ignore pushMetadataUpdate early.
        listOf(250L, 750L, 1500L).forEach { delayMs ->
            metadataReplayExecutor.schedule(
                { replayMetadataForGeneration(ownerPackage, generation) },
                delayMs,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
        }
        return published
    }

    private fun replayMetadataForGeneration(ownerPackage: String, generation: Long) {
        val record = activeRecords[ownerPackage] ?: return
        val session = repository.snapshot().sessions.firstOrNull {
            it.descriptor.ownerPackage == ownerPackage && it.descriptor.trackGeneration == generation
        } ?: return
        if (realLyricsByPackage["$ownerPackage|$generation"].isNullOrBlank()) return
        overlayMetadata(
            record,
            session,
            cachedMetadataField,
            cachedLockField,
            cachedPushMetadataUpdateMethod,
            force = true
        )
    }

    private fun loadCachedLyric(key: String): String? {
        val file = java.io.File("/data/system/universal_lyric_cache.json")
        if (!file.isFile) return null
        return runCatching {
            JSONObject(file.readText()).optString(key).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun loadSourceConfig(): String = runCatching {
        java.io.File("/data/system/universal_source_config.json").takeIf { it.isFile }?.readText()
    }.getOrNull() ?: LyricsSourceConfigStore.encode(listOf("QQ", "NETEASE", "APPLE_MUSIC", "SHARED_CACHE"))

    private fun persistSourceConfig(priority: List<io.github.cn_cizhanyue.lyricon.universal.provider.api.LyricsSourceAggregator.SourceId>) {
        runCatching { java.io.File("/data/system/universal_source_config.json").writeText(LyricsSourceConfigStore.encode(priority.map { it.name })) }
    }

    private fun loadNetworkEnabled(): Boolean = runCatching {
        java.io.File("/data/system/lyricon_network.enabled").readText().trim() != "0"
    }.getOrDefault(true)

    private fun persistNetworkEnabled(enabled: Boolean) {
        runCatching { java.io.File("/data/system/lyricon_network.enabled").writeText(if (enabled) "1" else "0") }
    }

    private fun loadWordTimingEnabled(): Boolean = runCatching {
        java.io.File("/data/system/lyricon_word_timing.enabled").readText().trim() != "0"
    }.getOrDefault(true)

    private fun persistWordTimingEnabled(enabled: Boolean) {
        runCatching { java.io.File("/data/system/lyricon_word_timing.enabled").writeText(if (enabled) "1" else "0") }
    }

    private fun loadRawLyricEnabled(): Boolean = runCatching {
        java.io.File("/data/system/universal_raw_lyric.enabled").readText().trim() != "0"
    }.getOrDefault(true)

    private fun persistRawLyricEnabled(enabled: Boolean) {
        runCatching { java.io.File("/data/system/universal_raw_lyric.enabled").writeText(if (enabled) "1" else "0") }
    }

    private fun loadDebugEnabled(): Boolean = runCatching {
        java.io.File("/data/system/universal_debug.enabled").readText().trim() == "1"
    }.getOrDefault(false)

    private fun persistDebugEnabled(enabled: Boolean) {
        runCatching { java.io.File("/data/system/universal_debug.enabled").writeText(if (enabled) "1" else "0") }
    }

    private fun loadTranslationEnabled(): Boolean = runCatching {
        java.io.File("/data/system/universal_translation.enabled").readText().trim() != "0"
    }.getOrDefault(true)

    private fun persistTranslationEnabled(enabled: Boolean) {
        runCatching { java.io.File("/data/system/universal_translation.enabled").writeText(if (enabled) "1" else "0") }
    }

    private fun loadClockLineFallbackEnabled(): Boolean = runCatching {
        java.io.File("/data/system/universal_clock_line_fallback.enabled").readText().trim() != "0"
    }.getOrDefault(true)

    private fun persistClockLineFallbackEnabled(enabled: Boolean) {
        runCatching { java.io.File("/data/system/universal_clock_line_fallback.enabled").writeText(if (enabled) "1" else "0") }
    }

    private fun bindingKey(title: String, artist: String): String =
        "${title.trim()}|${artist.trim()}".lowercase()

    private fun applyWordTimingPolicy(json: JSONObject, ownerPackage: String): UniversalLyricPayloadPolicy.Result {
        // A coarse player clock turns word fill into twitching; publish line timing for it instead.
        val clockFallback = wordTimingEnabled && clockLineFallbackActive(ownerPackage)
        val result = UniversalLyricPayloadPolicy.apply(
            json,
            wordTiming = wordTimingEnabled && !clockFallback,
            rawLyric = rawLyricEnabled,
            translation = translationEnabled
        )
        UniversalDiagnostics.rawLyricPolicy(
            generation = json.optLong("sessionGeneration"),
            enabled = wordTimingEnabled,
            hadRaw = result.hadRaw,
            hadWordTiming = result.hadWordTiming,
            attached = result.rawAttached,
            wordTimed = result.wordTimed,
            clockFallback = clockFallback
        )
        return result
    }

    private fun publishedPayload(value: String, ownerPackage: String): String = runCatching {
        JSONObject(value).also { applyWordTimingPolicy(it, ownerPackage) }.toString()
    }.getOrDefault(value)

    private fun recoverIdentityFromCache(
        rawTitle: String?,
        artist: String?,
        album: String?,
        durationMs: Long?
    ): Pair<String, String>? {
        val needle = normalizeLyricText(rawTitle)
        if (needle.length < 8) return null
        val file = java.io.File("/data/system/universal_lyric_cache.json")
        if (!file.isFile) return null
        return runCatching {
            val root = JSONObject(file.readText())
            val matches = mutableListOf<Pair<String, String>>()
            root.keys().forEach { key ->
                if (UniversalLyricCache.identityFromKey(key) == null) return@forEach
                val payload = runCatching { JSONObject(root.optString(key)) }.getOrNull() ?: return@forEach
                if (payload.optBoolean("noLyric", false)) return@forEach
                val candidateArtist = payload.optString("artist")
                val candidateAlbum = payload.optString("album")
                if (!sameIdentityText(artist, candidateArtist)) return@forEach
                if (!album.isNullOrBlank() && candidateAlbum.isNotBlank() && !sameIdentityText(album, candidateAlbum)) return@forEach
                val candidateDuration = payload.optLong("durationMs").takeIf { it > 0L }
                if (durationMs != null && candidateDuration != null && kotlin.math.abs(durationMs - candidateDuration) > 2_500L) return@forEach
                val lyricText = normalizeLyricText(payload.optString("lyric"))
                if (lyricText.contains(needle)) {
                    val songName = payload.optString("songName")
                    if (songName.isNotBlank()) matches += songName to candidateArtist
                }
            }
            matches.distinct().singleOrNull()
        }.getOrNull()
    }

    private fun normalizeLyricText(value: String?): String = value.orEmpty().lowercase()
        .replace(Regex("[\\[<]\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?[\\]>]"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private fun sameIdentityText(left: String?, right: String?): Boolean =
        left?.trim()?.equals(right?.trim(), ignoreCase = true) == true

    /** Callers publish the snapshot once the payload is applied, which also carries the new count. */
    private fun saveCachedLyric(title: String, artist: String, jsonStr: String) {
        UniversalLyricCache.put(title, artist, jsonStr)
    }

    private fun loadCachedLyric(title: String, artist: String): String? =
        UniversalLyricCache.get(title, artist)

    private fun upsertCachedSongs(text: String): String {
        val count = UniversalLyricCache.getCacheCount()
        val lines = text.lines().filterNot { it.startsWith("cachedSongs=") }
        return (lines + ("cachedSongs=" + count)).joinToString("\n").trimEnd() + "\n"
    }

    private fun loadManualBinding(key: String): String? {
        val file = java.io.File("/data/system/universal_manual_bindings.json")
        if (!file.isFile) return null
        return runCatching {
            JSONObject(file.readText()).optString(key).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun saveManualBinding(key: String, jsonStr: String) {
        runCatching {
            val file = java.io.File("/data/system/universal_manual_bindings.json")
            val obj = if (file.isFile) JSONObject(file.readText()) else JSONObject()
            obj.put(key, jsonStr)
            file.writeText(obj.toString())
        }
    }

    private fun clearCache(): Int {
        return UniversalLyricCache.clear().songs
    }
}
