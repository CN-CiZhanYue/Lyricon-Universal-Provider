/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import java.lang.reflect.Executable

/**
 * JVM test runtime. Keeps registration order and dispatches through the same
 * [ProviderChainHooker] used in production, so fixture tests exercise identical adapter logic.
 */
internal class FakeHookRuntime : ProviderHookRuntime {

    private data class Registration(val executable: Executable, val hooker: ProviderChainHooker)

    private val registrations = LinkedHashMap<String, Registration>()

    override val installedHookCount: Int
        get() = registrations.size

    override fun hook(executable: Executable, id: String, spec: ProviderHookSpec.() -> Unit): Boolean {
        if (registrations.containsKey(id)) return false
        registrations[id] = Registration(executable, ProviderChainHooker(ProviderHookSpec().apply(spec)))
        return true
    }

    fun hookCountFor(executable: Executable): Int = registrations.values.count { it.executable == executable }

    /**
     * Invokes all hooks registered for [executable] in registration order, then [original].
     * Equivalent to a single-module, single-executable framework chain.
     */
    fun invoke(
        executable: Executable,
        thisObject: Any?,
        args: Array<Any?>,
        original: (Array<Any?>) -> Any?
    ): Any? {
        val hookers = registrations.values.filter { it.executable == executable }.map { it.hooker }
        if (hookers.isEmpty()) return original(args)
        return dispatch(hookers, 0, executable, thisObject, args, original)
    }

    private fun dispatch(
        hookers: List<ProviderChainHooker>,
        index: Int,
        executable: Executable,
        thisObject: Any?,
        args: Array<Any?>,
        original: (Array<Any?>) -> Any?
    ): Any? {
        val chain = FakeXposedChain(executable, thisObject, args) { proceedArgs ->
            if (index + 1 < hookers.size) {
                dispatch(hookers, index + 1, executable, thisObject, proceedArgs, original)
            } else {
                original(proceedArgs)
            }
        }
        return hookers[index].intercept(chain)
    }
}
