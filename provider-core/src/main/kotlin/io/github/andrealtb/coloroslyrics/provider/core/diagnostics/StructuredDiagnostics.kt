/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.diagnostics

import io.github.andrealtb.coloroslyrics.provider.core.mode.RuntimeMode
import java.util.concurrent.CopyOnWriteArrayList

object StructuredDiagnostics {

    private const val DEFAULT_TAG = "ColorOSLiveLyrics"

    private val sinks = CopyOnWriteArrayList<DiagnosticSink>().apply {
        add(LogcatSink())
    }

    private val throttler = DiagnosticThrottler(windowMillis = 3000L)

    var isDebugEnabled: Boolean = false

    fun configure(debugEnabled: Boolean, additionalSinks: List<DiagnosticSink> = emptyList()) {
        sinks.clear()
        sinks.add(LogcatSink())
        additionalSinks.forEach(::addSink)
        throttler.clear()
        isDebugEnabled = debugEnabled
    }

    /** API 102 entries inject their framework sink; null keeps logcat-only diagnostics. */
    fun configureForRuntime(
        mode: RuntimeMode,
        debugEnabled: Boolean,
        frameworkSink: DiagnosticSink?
    ) {
        val frameworkSinks = if (mode == RuntimeMode.ROOT_MODULE && frameworkSink != null) {
            listOf(frameworkSink)
        } else {
            emptyList()
        }
        configure(debugEnabled, frameworkSinks)
    }

    fun addSink(sink: DiagnosticSink) {
        if (!sinks.contains(sink)) {
            sinks.add(sink)
        }
    }

    fun removeSink(sink: DiagnosticSink) {
        sinks.remove(sink)
    }

    fun logDebug(event: DiagnosticEvent, tag: String = DEFAULT_TAG, throwable: Throwable? = null) {
        if (!isDebugEnabled) return
        log(DiagnosticSink.LEVEL_DEBUG, "DEBUG", event, tag, throwable)
    }

    fun logInfo(event: DiagnosticEvent, tag: String = DEFAULT_TAG, throwable: Throwable? = null) {
        log(DiagnosticSink.LEVEL_INFO, "INFO", event, tag, throwable)
    }

    fun logWarning(event: DiagnosticEvent, tag: String = DEFAULT_TAG, throwable: Throwable? = null) {
        log(DiagnosticSink.LEVEL_WARN, "WARN", event, tag, throwable)
    }

    fun logError(event: DiagnosticEvent, tag: String = DEFAULT_TAG, throwable: Throwable? = null) {
        log(DiagnosticSink.LEVEL_ERROR, "ERROR", event, tag, throwable)
    }

    private fun log(
        level: Int,
        levelName: String,
        event: DiagnosticEvent,
        tag: String,
        throwable: Throwable?
    ) {
        val throttleKey = "${event.component}|${event.area}|${event.event}|${event.session.orEmpty()}"
        if (!throttler.shouldLog(throttleKey)) return
        val formatted = DiagnosticEventFormatter.format(levelName, event)
        dispatch(level, tag, SensitiveFieldRedactor.redact(formatted), throwable)
    }

    private fun dispatch(level: Int, tag: String, message: String, throwable: Throwable?) {
        for (sink in sinks) {
            runCatching {
                sink.log(level, tag, message, throwable)
            }
        }
    }

    internal fun resetForTesting() {
        sinks.clear()
        sinks.add(LogcatSink())
        throttler.clear()
        isDebugEnabled = false
    }
}
