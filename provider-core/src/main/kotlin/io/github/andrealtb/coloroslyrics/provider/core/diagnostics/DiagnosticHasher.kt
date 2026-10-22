/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.diagnostics

import java.security.MessageDigest
import java.net.URI
import java.util.Locale

object DiagnosticHasher {
    fun sha256(value: String, prefixLength: Int = 12): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "sha256:${hex.take(prefixLength.coerceIn(8, hex.length))}"
    }

    fun describeUri(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return "none"
        val parsed = runCatching { URI(raw) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase(Locale.ROOT)
            ?: raw.substringBefore(':', "unknown").lowercase(Locale.ROOT)
        val host = parsed?.host.orEmpty()
        return "scheme=$scheme host=${if (host.isBlank()) "none" else sha256(host)} value=${sha256(raw)}"
    }
}
