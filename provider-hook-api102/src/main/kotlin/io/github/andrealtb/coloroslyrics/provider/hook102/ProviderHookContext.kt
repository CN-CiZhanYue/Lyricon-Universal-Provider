/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import android.content.Context
import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderDebugSource
import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderId
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticSink

/**
 * Explicit hook context handed to business installers by the entry/bootstrap. Replaces the Yuki
 * implicit global context. Instances must not be retained across processes or host generations.
 */
class ProviderHookContext(
    val providerId: ProviderId,
    val packageName: String,
    val processName: String,
    val classLoader: ClassLoader,
    /** Actual host [android.app.Application] instance, exposed as Context after attach. */
    val application: Context,
    val hostVersion: String?,
    val runtime: ProviderHookRuntime,
    /** Remote Preferences read side for this Provider; read failures resolve to debug disabled. */
    val debugSource: ProviderDebugSource,
    /** Xposed framework log sink used for diagnostics dual-write. */
    val frameworkSink: DiagnosticSink
)
