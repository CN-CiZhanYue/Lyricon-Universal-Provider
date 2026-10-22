/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.cn_cizhanyue.lyricon.universal.provider.session

import java.util.concurrent.CopyOnWriteArrayList

data class UniversalMonitorSnapshot(
    val listenerConnected: Boolean,
    val notificationAccessGranted: Boolean,
    val selected: ResolvedSession?,
    val sessions: List<ResolvedSession>,
    val selectionRevision: Long,
    val ambiguousPlaying: Boolean
)

fun interface SnapshotListener {
    fun onSnapshot(snapshot: UniversalMonitorSnapshot)
}

class SessionRepository(
    private val resolver: TrackIdentityResolver = TrackIdentityResolver(),
    private val selector: ActiveSessionSelector = ActiveSessionSelector()
) {
    private val sessions = LinkedHashMap<String, ResolvedSession>()
    private val listeners = CopyOnWriteArrayList<SnapshotListener>()
    @Volatile
    var listenerConnected: Boolean = false
        private set
    @Volatile
    var notificationAccessGranted: Boolean = false
        private set

    @Synchronized
    fun setNotificationAccessGranted(granted: Boolean) {
        notificationAccessGranted = granted
        if (!granted) {
            clearAllLocked()
        }
        publishLocked()
    }

    @Synchronized
    fun onListenerConnected() {
        listenerConnected = true
        publishLocked()
    }

    @Synchronized
    fun onListenerDisconnected() {
        listenerConnected = false
        clearAllLocked()
        publishLocked()
    }

    @Synchronized
    fun replaceActiveSessions(observations: List<SessionObservation>, nowElapsedMs: Long) {
        val incomingIds = observations.map { it.sessionInstanceId }.toSet()
        val stale = sessions.keys.filterNot(incomingIds::contains)
        stale.forEach { id ->
            sessions.remove(id)
            resolver.drop(id)
        }
        observations.forEach { observation ->
            sessions[observation.sessionInstanceId] = resolver.observe(observation)
        }
        selector.select(sessions.values.toList(), nowElapsedMs)
        publishLocked()
    }

    @Synchronized
    fun get(sessionInstanceId: String): ResolvedSession? = sessions[sessionInstanceId]

    @Synchronized
    fun upsert(observation: SessionObservation, boundFilter: Set<String>? = null) {
        sessions[observation.sessionInstanceId] = resolver.observe(observation)
        val candidates = if (boundFilter != null && boundFilter.isNotEmpty()) {
            sessions.values.filter { boundFilter.contains(it.descriptor.ownerPackage) }
        } else {
            sessions.values.toList()
        }
        selector.select(candidates, observation.observedAtElapsedMs)
        publishLocked()
    }

    @Synchronized
    fun updateBoundFilter(boundFilter: Set<String>, nowElapsedMs: Long) {
        val candidates = if (boundFilter.isNotEmpty()) {
            sessions.values.filter { boundFilter.contains(it.descriptor.ownerPackage) }
        } else {
            sessions.values.toList()
        }
        selector.select(candidates, nowElapsedMs)
        publishLocked()
    }

    @Synchronized
    fun drop(sessionInstanceId: String, nowElapsedMs: Long) {
        sessions.remove(sessionInstanceId)
        resolver.drop(sessionInstanceId)
        selector.select(sessions.values.toList(), nowElapsedMs)
        publishLocked()
    }

    @Synchronized
    fun snapshot(): UniversalMonitorSnapshot {
        val current = sessions.values.toList()
        val selected = selector.current()?.takeIf { resolved -> current.any { it.descriptor.sessionInstanceId == resolved.descriptor.sessionInstanceId } }
        val playingCount = current.count { it.descriptor.playing }
        return UniversalMonitorSnapshot(
            listenerConnected = listenerConnected,
            notificationAccessGranted = notificationAccessGranted,
            selected = selected,
            sessions = current,
            selectionRevision = selector.selectionRevision,
            ambiguousPlaying = playingCount > 1 && selected == null
        )
    }

    fun addListener(listener: SnapshotListener) {
        listeners.add(listener)
        listener.onSnapshot(snapshot())
    }

    fun removeListener(listener: SnapshotListener) {
        listeners.remove(listener)
    }

    private fun clearAllLocked() {
        sessions.clear()
        resolver.reset()
        selector.reset()
    }

    private fun publishLocked() {
        val current = snapshot()
        listeners.forEach { listener -> listener.onSnapshot(current) }
    }
}
