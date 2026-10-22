/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.cn_cizhanyue.lyricon.universal.provider.session

class ActiveSessionSelector(
    private val hysteresisMs: Long = 500L
) {
    private var pinnedSessionId: String? = null
    private var selectedSessionId: String? = null
    private var lastSelected: ResolvedSession? = null
    private var selectedAtElapsedMs: Long = 0L
    var selectionRevision: Long = 0L
        private set

    @Synchronized
    fun current(): ResolvedSession? = lastSelected

    @Synchronized
    fun reset() {
        pinnedSessionId = null
        selectedSessionId = null
        lastSelected = null
        selectedAtElapsedMs = 0L
        selectionRevision += 1L
    }

    @Synchronized
    fun pin(sessionInstanceId: String?) {
        pinnedSessionId = sessionInstanceId
        selectionRevision += 1L
    }

    @Synchronized
    fun select(
        sessions: List<ResolvedSession>,
        nowElapsedMs: Long,
        associatedSessionId: String? = null
    ): ResolvedSession? {
        if (sessions.isEmpty()) {
            selectedSessionId = null
            lastSelected = null
            return null
        }
        val byId = sessions.associateBy { it.descriptor.sessionInstanceId }
        val pinned = pinnedSessionId?.let { byId[it] }
        if (pinned != null) {
            return commit(pinned, nowElapsedMs)
        }

        val playing = sessions.filter { it.descriptor.playing }
        val current = selectedSessionId?.let { byId[it] }
        if (current != null && current.descriptor.playing) {
            return commit(current, nowElapsedMs)
        }
        if (playing.size == 1) {
            val candidate = playing.single()
            if (current != null &&
                current.descriptor.sessionInstanceId != candidate.descriptor.sessionInstanceId &&
                nowElapsedMs - selectedAtElapsedMs < hysteresisMs &&
                (current.descriptor.playing || PlaybackStates.isTransient(current.descriptor.playbackState))
            ) {
                return commit(current, nowElapsedMs, bumpRevision = false)
            }
            return commit(candidate, nowElapsedMs)
        }
        if (playing.size > 1) {
            associatedSessionId?.let { byId[it] }?.let { return commit(it, nowElapsedMs) }
            current?.takeIf { it.descriptor.playing }?.let { return commit(it, nowElapsedMs) }
            return null
        }
        if (current != null) {
            return commit(current, nowElapsedMs, bumpRevision = false)
        }
        associatedSessionId?.let { byId[it] }?.let { return commit(it, nowElapsedMs) }
        return commit(sessions.first(), nowElapsedMs)
    }

    private fun commit(
        session: ResolvedSession,
        nowElapsedMs: Long,
        bumpRevision: Boolean = true
    ): ResolvedSession {
        if (selectedSessionId != session.descriptor.sessionInstanceId && bumpRevision) {
            selectionRevision += 1L
            selectedAtElapsedMs = nowElapsedMs
        }
        selectedSessionId = session.descriptor.sessionInstanceId
        lastSelected = session
        return session
    }
}
