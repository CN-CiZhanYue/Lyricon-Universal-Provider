/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

/**
 * JVM stand-in for the framework chain. Records proceed invocations so tests can assert the
 * API 102 contract (immutable args, single proceed, skip behavior).
 */
internal class FakeXposedChain(
    private val executable: Executable,
    private val thisObject: Any?,
    initialArgs: Array<Any?>,
    private val proceedAction: (Array<Any?>) -> Any?
) : XposedInterface.Chain {

    var proceedCallCount = 0
        private set
    var lastProceedArgs: Array<Any?>? = null
        private set
    private var currentArgs: Array<Any?> = initialArgs

    override fun getExecutable(): Executable = executable

    override fun getThisObject(): Any? = thisObject

    override fun getArgs(): MutableList<Any?> = currentArgs.toMutableList()

    override fun getArg(index: Int): Any? = currentArgs[index]

    override fun proceed(): Any? {
        proceedCallCount++
        lastProceedArgs = currentArgs
        return proceedAction(currentArgs)
    }

    override fun proceed(args: Array<Any?>): Any? {
        proceedCallCount++
        currentArgs = args
        lastProceedArgs = args
        return proceedAction(args)
    }

    override fun proceedWith(thisObject: Any): Any? = proceed()

    override fun proceedWith(thisObject: Any, args: Array<Any?>): Any? = proceed(args)
}
