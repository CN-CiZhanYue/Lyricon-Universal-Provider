/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.diagnostics

class DiagnosticThrottler(
    private val windowMillis: Long = 5000L,
    private val maxEntries: Int = 512
) {
    private val lastLoggedTimes = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > maxEntries.coerceAtLeast(1)
    }

    @Synchronized
    fun shouldLog(key: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val lastTime = lastLoggedTimes[key]
        if (lastTime == null || nowMillis - lastTime >= windowMillis) {
            lastLoggedTimes[key] = nowMillis
            return true
        }
        return false
    }

    @Synchronized
    fun clear() {
        lastLoggedTimes.clear()
    }

    @Synchronized
    internal fun entryCount(): Int = lastLoggedTimes.size
}
