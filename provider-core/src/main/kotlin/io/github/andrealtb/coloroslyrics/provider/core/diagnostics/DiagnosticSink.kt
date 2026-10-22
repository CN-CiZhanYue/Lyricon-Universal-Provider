/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.diagnostics

import android.util.Log

interface DiagnosticSink {
    fun log(level: Int, tag: String, message: String, throwable: Throwable? = null)

    companion object {
        const val LEVEL_DEBUG = 3
        const val LEVEL_INFO = 4
        const val LEVEL_WARN = 5
        const val LEVEL_ERROR = 6
    }
}

class LogcatSink : DiagnosticSink {
    override fun log(level: Int, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            DiagnosticSink.LEVEL_DEBUG -> Log.d(tag, message, throwable)
            DiagnosticSink.LEVEL_INFO -> Log.i(tag, message, throwable)
            DiagnosticSink.LEVEL_WARN -> Log.w(tag, message, throwable)
            DiagnosticSink.LEVEL_ERROR -> Log.e(tag, message, throwable)
            else -> Log.d(tag, message, throwable)
        }
    }
}
