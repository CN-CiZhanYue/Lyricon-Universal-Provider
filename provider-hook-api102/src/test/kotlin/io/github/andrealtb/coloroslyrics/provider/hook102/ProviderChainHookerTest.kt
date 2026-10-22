/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Executable

class ProviderChainHookerTest {

    private val concat: Executable = String::class.java.getDeclaredMethod("concat", String::class.java)

    private fun intercept(
        spec: ProviderHookSpec.() -> Unit,
        args: Array<Any?>,
        original: (Array<Any?>) -> Any?
    ): Pair<Any?, FakeXposedChain> {
        val hooker = ProviderChainHooker(ProviderHookSpec().apply(spec))
        val chain = FakeXposedChain(concat, "receiver", args, original)
        return hooker.intercept(chain) to chain
    }

    @Test
    fun beforeReadsArgsAndOriginalRunsUnchanged() {
        var observed: String? = null
        val (result, chain) = intercept(
            spec = { before { observed = args.getOrNull(0) as? String } },
            args = arrayOf("a"),
            original = { arr -> (arr[0] as String) + "-original" }
        )
        assertEquals("a", observed)
        assertEquals("a-original", result)
        assertEquals(1, chain.proceedCallCount)
    }

    @Test
    fun beforeRewritesArgsThroughCopiedArray() {
        val (result, chain) = intercept(
            spec = { before { args[0] = "patched" } },
            args = arrayOf("original"),
            original = { arr -> arr[0] }
        )
        assertEquals("patched", result)
        assertEquals(arrayOf<Any?>("patched").toList(), chain.lastProceedArgs?.toList())
    }

    @Test
    fun afterReadsAndReplacesResult() {
        val (result, _) = intercept(
            spec = { after { result = (result as String).uppercase() } },
            args = arrayOf("x"),
            original = { "done" }
        )
        assertEquals("DONE", result)
    }

    @Test
    fun resultNullSkipsOriginal() {
        var originalInvoked = false
        val (result, chain) = intercept(
            spec = { before { resultNull() } },
            args = arrayOf("x"),
            original = { originalInvoked = true; "ignored" }
        )
        assertNull(result)
        assertTrue(!originalInvoked)
        assertEquals(0, chain.proceedCallCount)
    }

    @Test
    fun beforeResultAssignmentSkipsOriginalWithValue() {
        var originalInvoked = false
        val (result, chain) = intercept(
            spec = { before { result = "skip-value" } },
            args = arrayOf("x"),
            original = { originalInvoked = true; "ignored" }
        )
        assertEquals("skip-value", result)
        assertTrue(!originalInvoked)
        assertEquals(0, chain.proceedCallCount)
    }

    @Test
    fun hostExceptionPropagatesWithoutAfter() {
        val hostError = IllegalStateException("host-failed")
        val hooker = ProviderChainHooker(ProviderHookSpec())
        val chain = FakeXposedChain(concat, null, arrayOf("x")) { throw hostError }
        try {
            hooker.intercept(chain)
            fail("expected host exception to propagate")
        } catch (error: Throwable) {
            assertSame(hostError, error)
        }
    }

    @Test
    fun afterSeesHostThrowableAndExceptionStillPropagates() {
        val hostError = IllegalStateException("host-failed")
        var seen: Throwable? = null
        val hooker = ProviderChainHooker(
            ProviderHookSpec().apply { after { seen = throwable } }
        )
        val chain = FakeXposedChain(concat, null, arrayOf("x")) { throw hostError }
        try {
            hooker.intercept(chain)
            fail("expected host exception to propagate")
        } catch (error: Throwable) {
            assertSame(hostError, error)
        }
        assertSame(hostError, seen)
    }

    @Test
    fun afterResultOverrideSuppressesHostException() {
        val hooker = ProviderChainHooker(
            ProviderHookSpec().apply { after { result = "recovered" } }
        )
        val chain = FakeXposedChain(concat, null, arrayOf("x")) { throw IllegalStateException("boom") }
        assertEquals("recovered", hooker.intercept(chain))
    }

    @Test
    fun emptySpecPassesThrough() {
        val (result, chain) = intercept(
            spec = {},
            args = arrayOf("p"),
            original = { arr -> (arr[0] as String) + "q" }
        )
        assertEquals("pq", result)
        assertEquals(1, chain.proceedCallCount)
    }
}
