/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.cn_cizhanyue.lyricon.universal.provider

import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticEvent
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticHasher
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.StructuredDiagnostics
import io.github.cn_cizhanyue.lyricon.universal.provider.session.PlaybackClockQuality
import io.github.cn_cizhanyue.lyricon.universal.provider.session.ResolvedSession

internal object UniversalDiagnostics {
    const val COMPONENT = "provider/universal"

    fun sessionSelected(session: ResolvedSession?, selectionRevision: Long) {
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = COMPONENT,
                area = "session",
                event = "SESSION_SELECTED",
                session = session?.descriptor?.sessionInstanceId?.let(DiagnosticHasher::sha256),
                generation = session?.descriptor?.trackGeneration,
                trackHash = session?.let { hashTrack(it) },
                reason = "selectionRevision=$selectionRevision polluted=${session?.descriptor?.titlePolluted == true}"
            )
        )
    }

    fun trackChanged(session: ResolvedSession) {
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = COMPONENT,
                area = "identity",
                event = "TRACK_CHANGED",
                session = DiagnosticHasher.sha256(session.descriptor.sessionInstanceId),
                generation = session.descriptor.trackGeneration,
                trackHash = hashTrack(session),
                reason = session.demuxSource
            )
        )
    }

    fun metadataObserved(session: ResolvedSession, source: String) {
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = COMPONENT,
                area = "metadata",
                event = if (source == "before-setMetadata") "METADATA_OBSERVED_BEFORE" else "METADATA_OBSERVED",
                session = DiagnosticHasher.sha256(session.descriptor.sessionInstanceId),
                generation = session.descriptor.trackGeneration,
                trackHash = hashTrack(session),
                reason = "source=$source titlePresent=${!session.descriptor.title.isNullOrBlank()} artistPresent=${!session.descriptor.artist.isNullOrBlank()} albumPresent=${!session.descriptor.album.isNullOrBlank()} durationMs=${session.descriptor.durationMs ?: "unknown"} playing=${session.descriptor.playing} mediaIdHash=${session.descriptor.hostMediaId?.let(DiagnosticHasher::sha256)?.take(12) ?: "none"} playbackMediaIdHash=${session.descriptor.playbackExtrasMediaId?.let(DiagnosticHasher::sha256)?.take(12) ?: "none"} mediaUriHash=${session.descriptor.mediaUri?.let(DiagnosticHasher::sha256)?.take(12) ?: "none"} queueId=${session.descriptor.playbackQueueItemId ?: "none"} positionMs=${session.descriptor.positionMs ?: "unknown"}"
            )
        )
    }

    fun metadataHookEntered(ownerPackage: String, hasMetadata: Boolean, title: String?, artist: String?, album: String?, description: String?) {
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = COMPONENT,
                area = "hook",
                event = "SET_METADATA_HOOK_ENTERED",
                reason = "package=$ownerPackage hasMetadata=$hasMetadata titlePresent=${!title.isNullOrBlank()} artistPresent=${!artist.isNullOrBlank()} albumPresent=${!album.isNullOrBlank()} titleHash=${title?.let(DiagnosticHasher::sha256)?.take(12) ?: "none"} artistHash=${artist?.let(DiagnosticHasher::sha256)?.take(12) ?: "none"} descriptionPresent=${!description.isNullOrBlank()} descriptionHash=${description?.let(DiagnosticHasher::sha256)?.take(12) ?: "none"}"
            )
        )
    }

    fun metadataHookFailed(ownerPackage: String, error: Throwable) {
        StructuredDiagnostics.logError(
            DiagnosticEvent(
                component = COMPONENT,
                area = "hook",
                event = "SET_METADATA_HOOK_FAILED",
                reason = "package=$ownerPackage exception=${error.javaClass.simpleName}"
            )
        )
    }

    fun metadataFiltered(ownerPackage: String, boundCount: Int) {
        StructuredDiagnostics.logWarning(
            DiagnosticEvent(
                component = COMPONENT,
                area = "hook",
                event = "SET_METADATA_FILTERED",
                reason = "package=$ownerPackage boundCount=$boundCount"
            )
        )
    }

   fun fetchStarted(ownerPackage: String, generation: Long, from: String) {
       StructuredDiagnostics.logInfo(
           DiagnosticEvent(
               component = COMPONENT,
               area = "lyrics",
               event = "FETCH_STARTED",
               generation = generation,
                session = "fetch|$ownerPackage|$generation|$from",
                reason = "package=$ownerPackage from=$from"
           )
       )
   }

   fun fetchFinished(ownerPackage: String, generation: Long, source: String, chars: Int) {
       StructuredDiagnostics.logInfo(
           DiagnosticEvent(
               component = COMPONENT,
               area = "lyrics",
               event = "FETCH_FINISHED",
               generation = generation,
               payloadChars = chars,
                session = "fetch|$ownerPackage|$generation|$source",
                reason = "package=$ownerPackage source=$source"
           )
       )
   }

   fun fetchEmpty(ownerPackage: String, generation: Long, reason: String) {
       StructuredDiagnostics.logWarning(
           DiagnosticEvent(
               component = COMPONENT,
               area = "lyrics",
               event = "FETCH_EMPTY",
               generation = generation,
                session = "fetch|$ownerPackage|$generation",
                reason = "package=$ownerPackage $reason"
           )
       )
   }

   fun lyricInfoAttached(ownerPackage: String, generation: Long, path: String) {
       StructuredDiagnostics.logInfo(
           DiagnosticEvent(
               component = COMPONENT,
               area = "lyrics",
               event = "LYRIC_INFO_ATTACHED",
               generation = generation,
                session = "attach|$ownerPackage|$generation|$path",
                reason = "package=$ownerPackage path=$path"
           )
       )
   }

    fun sourceConfigUpdated(priority: List<String>) {
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = COMPONENT,
                area = "sources",
                event = "SOURCE_CONFIG_UPDATED",
                reason = "enabled=${priority.joinToString(",")}" 
            )
        )
    }

    fun listenerCreated() {
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = COMPONENT,
                area = "nls",
                event = "LISTENER_CREATED"
            )
        )
    }
 
    fun bridgeBindingsNotified(count: Int, target: String, ok: Boolean, error: String? = null) {
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = COMPONENT,
                area = "bindings",
                event = if (ok) "BRIDGE_BINDINGS_NOTIFIED" else "BRIDGE_BINDINGS_NOTIFY_FAILED",
                session = target,
                reason = "target=" + target + " count=" + count +
                    if (error.isNullOrBlank()) "" else " error=" + error
            )
        )
    }

    fun playerBindingsReloaded(count: Int, source: String) {
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = COMPONENT,
                area = "bindings",
                event = "PLAYER_BINDINGS_RELOADED",
                reason = "source=" + source + " count=" + count
            )
        )
    }

    fun commandReceiverRegistered(ok: Boolean, error: String? = null) {
        val event = DiagnosticEvent(
            component = COMPONENT,
            area = "bootstrap",
            event = if (ok) "COMMAND_RECEIVER_REGISTERED" else "COMMAND_RECEIVER_REGISTER_FAILED",
            process = "system_server",
            reason = if (error.isNullOrBlank()) "actions=5 user=all" else "error=$error"
        )
        if (ok) StructuredDiagnostics.logInfo(event) else StructuredDiagnostics.logError(event)
    }
   fun listenerState(connected: Boolean, granted: Boolean) {
       StructuredDiagnostics.logInfo(
           DiagnosticEvent(
               component = COMPONENT,
               area = "nls",
               event = if (connected) "LISTENER_CONNECTED" else "LISTENER_DISCONNECTED",
               reason = "notificationAccess=$granted"
           )
       )
   }

    fun cacheLookup(kind: String, generation: Long, hit: Boolean, noLyric: Boolean) {
        pipeline(
            area = "cache",
            event = if (!hit) "CACHE_MISS" else if (noLyric) "CACHE_NEGATIVE" else "CACHE_HIT",
            generation = generation,
            session = "cache|$kind|$generation",
            reason = "kind=$kind hit=$hit noLyric=$noLyric"
        )
    }

    fun sourceAttempt(source: String, generation: Long, priority: String) {
        pipeline(
            area = "sources",
            event = "SOURCE_ATTEMPT",
            generation = generation,
            session = "src|$source|$generation",
            reason = "source=$source priority=$priority"
        )
    }

    fun networkCall(source: String, op: String, generation: Long, httpCode: Int, durationMs: Long, bodyChars: Int, parseOk: Boolean, extra: String = "") {
        pipeline(
            area = "network",
            event = if (httpCode in 200..299) "HTTP_OK" else "HTTP_FAILED",
            generation = generation,
            session = "http|$source|$op|$generation",
            durationMs = durationMs,
            payloadChars = bodyChars,
            reason = "source=$source op=$op http=$httpCode parseOk=$parseOk${if (extra.isBlank()) "" else " $extra"}"
        )
    }

    fun sourceMatch(source: String, generation: Long, candidateCount: Int, bestScore: Double, threshold: Double, accepted: Boolean, keywordMode: String, durationPresent: Boolean) {
        pipeline(
            area = "match",
            event = if (accepted) "MATCH_ACCEPTED" else "MATCH_REJECTED",
            generation = generation,
            session = "match|$source|$generation",
            reason = "source=$source candidates=$candidateCount best=${"%.3f".format(bestScore)} threshold=$threshold keywordMode=$keywordMode durationPresent=$durationPresent"
        )
    }

    fun sourceFailed(source: String, generation: Long, exceptionName: String, durationMs: Long) {
        StructuredDiagnostics.logWarning(
            DiagnosticEvent(
                component = COMPONENT,
                area = "sources",
                event = "SOURCE_FAILED",
                session = "src|$source|$generation",
                generation = generation,
                durationMs = durationMs,
                reason = "source=$source exception=$exceptionName"
            )
        )
    }

    fun sourceEmpty(source: String, generation: Long, durationMs: Long, reason: String) {
        pipeline(
            area = "sources",
            event = "SOURCE_EMPTY",
            generation = generation,
            session = "src|$source|$generation",
            durationMs = durationMs,
            reason = "source=$source $reason"
        )
    }

    fun lyricPayload(source: String, generation: Long, lyricChars: Int, rawChars: Int, hasWordTiming: Boolean, hasTranslation: Boolean, converted: Boolean) {
        pipeline(
            area = "lyrics",
            event = "LYRIC_PAYLOAD",
            generation = generation,
            session = "payload|$source|$generation",
            payloadChars = lyricChars,
            reason = "source=$source rawChars=$rawChars wordTiming=$hasWordTiming translation=$hasTranslation converted=$converted"
        )
    }

    fun lyricInfoComposed(ownerPackage: String, generation: Long, source: String, lyricChars: Int, rawLyricAttached: Boolean, translationAttached: Boolean, wordTimingEnabled: Boolean) {
        pipeline(
            area = "lyrics",
            event = "LYRIC_INFO_COMPOSED",
            generation = generation,
            session = "compose|$ownerPackage|$generation",
            payloadChars = lyricChars,
            reason = "package=$ownerPackage source=$source rawLyricAttached=$rawLyricAttached translationAttached=$translationAttached wordTimingEnabled=$wordTimingEnabled"
        )
    }

    fun rawLyricPolicy(generation: Long, enabled: Boolean, hadRaw: Boolean, hadWordTiming: Boolean, attached: Boolean, wordTimed: Boolean, clockFallback: Boolean) {
        pipeline(
            area = "lyrics",
            event = "RAW_LYRIC_POLICY",
            generation = generation,
            session = "raw|$generation|$attached|$wordTimed|$clockFallback",
            reason = "enabled=$enabled hadRaw=$hadRaw hadWordTiming=$hadWordTiming attached=$attached wordTimed=$wordTimed clockFallback=$clockFallback"
        )
    }

    fun identityGate(session: ResolvedSession) {
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = COMPONENT,
                area = "identity",
                event = "IDENTITY_GATE",
                session = DiagnosticHasher.sha256(session.descriptor.sessionInstanceId),
                generation = session.descriptor.trackGeneration,
                trackHash = hashTrack(session),
                reason = "gate=${session.identityGate} settleAtMs=${session.identitySettleAtElapsedMs ?: "none"}"
            )
        )
    }

    fun identitySettleFailed(ownerPackage: String, error: Throwable) {
        StructuredDiagnostics.logWarning(
            DiagnosticEvent(
                component = COMPONENT,
                area = "identity",
                event = "IDENTITY_SETTLE_FAILED",
                reason = "package=$ownerPackage error=${error.javaClass.simpleName}"
            )
        )
    }

    fun clockQualityChanged(ownerPackage: String, previous: String, stats: PlaybackClockQuality.Stats) {
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = COMPONENT,
                area = "clock",
                event = "CLOCK_QUALITY_CHANGED",
                session = "clock|$ownerPackage|${stats.verdict}",
                reason = "package=$ownerPackage verdict=${stats.verdict} previous=$previous samples=${stats.samples} " +
                    "coarseSamples=${stats.coarseSamples} medianStepMs=${stats.medianJitterMs}"
            )
        )
    }

    private fun pipeline(
        area: String,
        event: String,
        generation: Long,
        session: String,
        reason: String,
        durationMs: Long? = null,
        payloadChars: Int? = null
    ) {
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = COMPONENT,
                area = area,
                event = event,
                session = session,
                generation = generation,
                durationMs = durationMs,
                payloadChars = payloadChars,
                reason = reason
            )
        )
    }

   private fun hashTrack(session: ResolvedSession): String {
        val descriptor = session.descriptor
        return DiagnosticHasher.sha256(
            listOf(
                descriptor.ownerPackage,
                descriptor.hostMediaId.orEmpty(),
                descriptor.title.orEmpty(),
                descriptor.artist.orEmpty(),
                descriptor.durationMs?.toString().orEmpty()
            ).joinToString("|")
        )
    }
}
