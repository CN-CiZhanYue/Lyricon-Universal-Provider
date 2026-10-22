/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.policy

import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import java.util.concurrent.atomic.AtomicLong

class TrackGenerationPolicy {

    private val currentGeneration = AtomicLong(0L)
    @Volatile
    private var lastTrackIdentity: TrackIdentity? = null

    val generation: Long
        get() = currentGeneration.get()

    val currentTrack: TrackIdentity?
        get() = lastTrackIdentity

    @Synchronized
    fun onTrackObserved(newTrack: TrackIdentity): Long {
        if (TrackIdentityPolicy.hasTrackChanged(lastTrackIdentity, newTrack)) {
            lastTrackIdentity = newTrack
            return currentGeneration.incrementAndGet()
        }
        lastTrackIdentity = lastTrackIdentity?.let {
            TrackIdentityPolicy.mergePreferringFilled(it, newTrack)
        } ?: newTrack
        return currentGeneration.get()
    }

    fun isGenerationValid(gen: Long): Boolean = gen > 0 && gen == currentGeneration.get()

    @Synchronized
    fun reset() {
        lastTrackIdentity = null
        currentGeneration.set(0L)
    }
}
