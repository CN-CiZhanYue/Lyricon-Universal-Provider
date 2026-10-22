/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.diagnostics

object SensitiveFieldRedactor {

    private val TOKEN_PATTERNS = listOf(
        Regex("""(?i)(bearer\s+)[a-z0-9_\-\.]{16,}""") to "$1<REDACTED_TOKEN>",
        Regex("""(?i)(token\s*[:=]\s*)[a-z0-9_\-\.]{16,}""") to "$1<REDACTED_TOKEN>",
        Regex("""(?i)(cookie\s*[:=]\s*)[^\s;]+""") to "$1<REDACTED_COOKIE>",
        Regex("""(?i)(password\s*[:=]\s*)[^\s,]+""") to "$1<REDACTED_PWD>",
        Regex("""/data/user/\d+/[a-zA-Z0-9_\.]+""") to "/data/user/<USER>/<PKG>",
        Regex("""(?i)file://[^\s\"']+""") to "file://<REDACTED_PATH>",
        Regex("""(?i)/storage/(?:emulated/\d+/)?[^\s\"']+""") to "/storage/<REDACTED_PATH>"
    )

    fun redact(message: String): String {
        var result = message
        for ((pattern, replacement) in TOKEN_PATTERNS) {
            result = result.replace(pattern, replacement)
        }
        return result
    }
}
