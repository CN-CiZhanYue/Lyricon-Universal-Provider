/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import java.lang.reflect.Executable

/**
 * Hook installation spec. Business code registers before/after callbacks through this DSL.
 *
 * Semantics are aligned with the 4.0 Yuki usage so business hooks stay behavior-compatible:
 * - before may read or modify args; modifications are passed to the original call as a copied
 *   argument array because API 102 chains expose immutable argument lists;
 * - assigning result in before (including [ProviderBeforeHook.resultNull]) skips the original
 *   executable and returns the assigned value;
 * - after may read args, the original result and the original exception, and may replace the
 *   result; replacing the result suppresses the original exception;
 * - an original exception that after does not override is rethrown to the caller unchanged.
 */
class ProviderHookSpec internal constructor() {
    internal var beforeCallback: (ProviderBeforeHook.() -> Unit)? = null
    internal var afterCallback: (ProviderAfterHook.() -> Unit)? = null

    fun before(block: ProviderBeforeHook.() -> Unit) {
        beforeCallback = block
    }

    fun after(block: ProviderAfterHook.() -> Unit) {
        afterCallback = block
    }
}

class ProviderBeforeHook internal constructor(
    val executable: Executable,
    val instanceOrNull: Any?,
    /** Mutable copy of the call arguments; writes are forwarded to the original call. */
    val args: Array<Any?>
) {
    internal var skipped: Boolean = false
        private set
    internal var skipResult: Any? = null
        private set

    /** Skips the original executable and returns [result] to the caller. */
    fun skipWithResult(result: Any?) {
        skipped = true
        skipResult = result
    }

    /** Skips the original executable and returns null (4.0 resultNull() equivalent). */
    fun resultNull() {
        skipWithResult(null)
    }

    /** Yuki-compatible form: assigning result in before skips the original executable. */
    var result: Any?
        get() = skipResult
        set(value) = skipWithResult(value)
}

class ProviderAfterHook internal constructor(
    val executable: Executable,
    val instanceOrNull: Any?,
    val args: Array<Any?>,
    /** Non-null when the original executable or an earlier interceptor threw. */
    val throwable: Throwable?,
    initialResult: Any?
) {
    /** Assigning a value replaces the call result and suppresses [throwable] if present. */
    var result: Any? = initialResult
        set(value) {
            field = value
            resultOverridden = true
        }

    internal var resultOverridden: Boolean = false
        private set
}
