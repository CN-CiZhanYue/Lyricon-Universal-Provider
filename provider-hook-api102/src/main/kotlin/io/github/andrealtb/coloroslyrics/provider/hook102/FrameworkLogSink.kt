/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticSink
import io.github.libxposed.api.XposedInterface

/**
 * Writes diagnostics into the Xposed framework log through the current module instance.
 * DiagnosticSink levels already match android.util.Log priorities. Static legacy
 * API-82 static log calls are forbidden in v4.1.
 */
class FrameworkLogSink(private val module: XposedInterface) : DiagnosticSink {

    override fun log(level: Int, tag: String, message: String, throwable: Throwable?) {
        runCatching {
            if (throwable != null) {
                module.log(level, tag, message, throwable)
            } else {
                module.log(level, tag, message)
            }
        }
    }
}
