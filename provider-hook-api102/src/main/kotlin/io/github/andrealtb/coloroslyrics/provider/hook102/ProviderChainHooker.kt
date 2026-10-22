/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import io.github.libxposed.api.XposedInterface

/**
 * Adapts the business before/after DSL onto one API 102 interceptor.
 *
 * API 102 chain contract honored here:
 * - [XposedInterface.Chain.getArgs] is immutable: arguments are copied once and only the copy is
 *   mutated and passed through [XposedInterface.Chain.proceed];
 * - [XposedInterface.Chain.proceed] is called at most once;
 * - skipping the original means proceed is never called and the replacement value is returned;
 * - host exceptions from proceed are recorded, exposed to after, and rethrown unless after
 *   explicitly overrides the result.
 */
internal class ProviderChainHooker(private val spec: ProviderHookSpec) : XposedInterface.Hooker {

    override fun intercept(chain: XposedInterface.Chain): Any? {
        val executable = chain.executable
        val thisObject = chain.thisObject
        val args = chain.args.toTypedArray()

        val beforeCallback = spec.beforeCallback
        val before = if (beforeCallback != null) {
            ProviderBeforeHook(executable, thisObject, args).also(beforeCallback)
        } else {
            null
        }

        val proceedResult: Any?
        var thrown: Throwable? = null
        if (before?.skipped == true) {
            proceedResult = before.skipResult
        } else {
            val outcome = try {
                if (beforeCallback != null) chain.proceed(args) else chain.proceed()
            } catch (cause: Throwable) {
                thrown = cause
                null
            }
            proceedResult = outcome
        }

        val afterCallback = spec.afterCallback
        if (afterCallback != null) {
            val after = ProviderAfterHook(executable, thisObject, args, thrown, proceedResult)
            afterCallback(after)
            if (after.resultOverridden) {
                return after.result
            }
        }

        thrown?.let { throw it }
        return proceedResult
    }
}
