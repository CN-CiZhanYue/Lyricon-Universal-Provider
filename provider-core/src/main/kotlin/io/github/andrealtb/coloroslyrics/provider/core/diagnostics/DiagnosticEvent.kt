/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.diagnostics

import io.github.andrealtb.coloroslyrics.provider.core.mode.RuntimeMode

data class DiagnosticEvent(
    val component: String,
    val area: String,
    val event: String,
    val mode: RuntimeMode? = null,
    val process: String? = null,
    val session: String? = null,
    val generation: Long? = null,
    val trackHash: String? = null,
    val hostVersion: String? = null,
    val providerVersion: String? = null,
    val reason: String? = null,
    val durationMs: Long? = null,
    val queueDepth: Int? = null,
    val payloadChars: Int? = null,
    val parcelBytes: Int? = null,
    val message: String? = null
)

internal object DiagnosticEventFormatter {
    fun format(level: String, event: DiagnosticEvent): String = buildString {
        append("[CLL]")
        appendField("level", level)
        appendField("component", event.component)
        appendField("area", event.area)
        appendField("event", event.event)
        event.mode?.let { appendField("mode", it.name) }
        event.process?.let { appendField("process", it) }
        event.session?.let { appendField("session", it) }
        event.generation?.let { appendField("generation", it.toString()) }
        event.trackHash?.let { appendField("track", it) }
        event.hostVersion?.let { appendField("hostVersion", it) }
        event.providerVersion?.let { appendField("providerVersion", it) }
        event.reason?.let { appendField("reason", it) }
        event.durationMs?.let { appendField("durationMs", it.toString()) }
        event.queueDepth?.let { appendField("queueDepth", it.toString()) }
        event.payloadChars?.let { appendField("payloadChars", it.toString()) }
        event.parcelBytes?.let { appendField("parcelBytes", it.toString()) }
        event.message?.let { appendField("message", it) }
    }

    private fun StringBuilder.appendField(name: String, value: String) {
        append(' ')
        append(name)
        append('=')
        append(quoteIfNeeded(value))
    }

    private fun quoteIfNeeded(value: String): String {
        if (value.isNotEmpty() && value.none { it.isWhitespace() || it == '=' || it == '"' }) return value
        return buildString(value.length + 2) {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\r', '\n' -> append(' ')
                    else -> append(character)
                }
            }
            append('"')
        }
    }
}
