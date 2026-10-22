/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import java.lang.reflect.Executable

/**
 * v4.1 hook runtime contract. Business layers depend only on this interface:
 * - [Api102HookRuntime] maps it onto libxposed API 102 inside target player processes;
 * - the test fake dispatches through the same adapter on the JVM.
 */
interface ProviderHookRuntime {
    /**
     * Installs one hook and returns whether the installation was accepted.
     *
     * [id] must be stable and unique within the Provider (recommended shape
     * "<purpose>.<DeclaringClass>#<member>"). Duplicate registrations with the same id are
     * rejected so repeated package callbacks or bootstraps cannot double business callbacks.
     */
    fun hook(executable: Executable, id: String, spec: ProviderHookSpec.() -> Unit): Boolean

    /** Number of hooks accepted by this runtime so far (used for install summaries). */
    val installedHookCount: Int
}
