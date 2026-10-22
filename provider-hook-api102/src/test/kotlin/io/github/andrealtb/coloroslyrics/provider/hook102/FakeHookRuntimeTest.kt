/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Executable

class FakeHookRuntimeTest {

    private val concat: Executable = String::class.java.getDeclaredMethod("concat", String::class.java)

    @Test
    fun duplicateIdIsRejectedAndCallbackRunsOnce() {
        val runtime = FakeHookRuntime()
        var callbackCount = 0
        assertTrue(runtime.hook(concat, "salt.test") { before { callbackCount++ } })
        assertFalse(runtime.hook(concat, "salt.test") { before { callbackCount++ } })
        assertEquals(1, runtime.installedHookCount)
        runtime.invoke(concat, null, arrayOf("a")) { it[0] }
        assertEquals(1, callbackCount)
    }

    @Test
    fun multipleDistinctHooksChainInRegistrationOrder() {
        val runtime = FakeHookRuntime()
        val order = mutableListOf<String>()
        runtime.hook(concat, "first") { before { order.add("first") } }
        runtime.hook(concat, "second") { before { order.add("second") } }
        assertEquals(2, runtime.hookCountFor(concat))
        runtime.invoke(concat, null, arrayOf("a")) { it[0] }
        assertEquals(listOf("first", "second"), order)
    }
}
