/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.cn_cizhanyue.lyricon.universal.provider.session

import kotlin.math.abs

/**
 * Read-only estimate of how steadily a player's PlaybackState anchors advance.
 *
 * Consumers extrapolate `position + (now - updateTime) * speed` between anchors, so every new
 * anchor re-bases the lyric clock by `position - predicted`. A player that reports a coarse
 * decoder or buffer counter (for example 400 ms blocks republished every second) moves that
 * clock by hundreds of milliseconds on each update, which word-level fill shows as twitching.
 *
 * Verdicts are keyed by owner package so a recreated session keeps what was learned, while
 * anchors are only compared within one session, generation and speed. Nothing here changes
 * the PlaybackState that SystemUI receives.
 */
class PlaybackClockQuality(
    private val windowSize: Int = 8,
    private val coarseMinCount: Int = 6,
    private val steadyMaxCoarseCount: Int = 1,
    private val jitterThresholdMs: Long = 90L
) {
    companion object {
        /** A larger step is a seek or restart, not clock grain. */
        const val DISCONTINUITY_MS = 1_500L
        private const val MAX_ANCHOR_GAP_MS = 60_000L
    }

    enum class Verdict { UNKNOWN, STEADY, COARSE }

    data class Stats(val verdict: Verdict, val samples: Int, val coarseSamples: Int, val medianJitterMs: Long)

    private data class Anchor(
        val sessionId: String,
        val generation: Long,
        val positionMs: Long,
        val updateTimeMs: Long,
        val speed: Float
    )

    private class Track {
        var anchor: Anchor? = null
        val jitter = ArrayDeque<Long>()
        var verdict = Verdict.UNKNOWN
    }

    private val tracks = HashMap<String, Track>()

    @Synchronized
    fun verdict(ownerPackage: String): Verdict = tracks[ownerPackage]?.verdict ?: Verdict.UNKNOWN

    @Synchronized
    fun stats(ownerPackage: String): Stats {
        val track = tracks[ownerPackage] ?: return Stats(Verdict.UNKNOWN, 0, 0, 0L)
        val sorted = track.jitter.sorted()
        return Stats(
            verdict = track.verdict,
            samples = sorted.size,
            coarseSamples = sorted.count { it >= jitterThresholdMs },
            medianJitterMs = sorted.getOrNull(sorted.size / 2) ?: 0L
        )
    }

    /** Records one PlaybackState anchor and returns the package verdict afterwards. */
    @Synchronized
    fun observe(
        ownerPackage: String,
        sessionId: String,
        generation: Long,
        state: Int?,
        positionMs: Long,
        updateTimeMs: Long,
        speed: Float
    ): Verdict {
        val track = tracks.getOrPut(ownerPackage) { Track() }
        val moving = state == PlaybackStates.PLAYING && speed > 0f && !speed.isNaN() &&
            !speed.isInfinite() && positionMs >= 0L && updateTimeMs > 0L
        if (!moving) {
            track.anchor = null
            return track.verdict
        }
        val anchor = Anchor(sessionId, generation, positionMs, updateTimeMs, speed)
        val previous = track.anchor
        // An identical PlaybackState published again is not a new anchor.
        if (previous == anchor) return track.verdict
        track.anchor = anchor
        if (previous == null || previous.sessionId != sessionId ||
            previous.generation != generation || previous.speed != speed
        ) {
            return track.verdict
        }
        val elapsed = updateTimeMs - previous.updateTimeMs
        if (elapsed <= 0L || elapsed > MAX_ANCHOR_GAP_MS) return track.verdict
        val step = abs(positionMs - previous.positionMs - (elapsed * speed).toLong())
        if (step > DISCONTINUITY_MS) return track.verdict
        track.jitter.addLast(step)
        while (track.jitter.size > windowSize) track.jitter.removeFirst()
        val coarse = track.jitter.count { it >= jitterThresholdMs }
        track.verdict = when {
            coarse >= coarseMinCount -> Verdict.COARSE
            track.jitter.size >= windowSize && coarse <= steadyMaxCoarseCount -> Verdict.STEADY
            else -> track.verdict
        }
        return track.verdict
    }
}
