/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.util.concurrent.ConcurrentHashMap

/**
 * Production runtime inside target player processes. Maps [ProviderHookRuntime] onto the current
 * module's libxposed API 102 surface with stable ids and protective exception handling.
 */
internal class Api102HookRuntime(
    private val module: XposedInterface,
    private val idPrefix: String
) : ProviderHookRuntime {

    private val installedIds = ConcurrentHashMap.newKeySet<String>()

    override val installedHookCount: Int
        get() = installedIds.size

    override fun hook(executable: Executable, id: String, spec: ProviderHookSpec.() -> Unit): Boolean {
        val qualifiedId = "$idPrefix:$id"
        if (!installedIds.add(qualifiedId)) {
            return false
        }
        val hookSpec = ProviderHookSpec().apply(spec)
        module.hook(executable)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .setId(qualifiedId)
            .intercept(ProviderChainHooker(hookSpec))
        return true
    }
}
