/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.mode

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeModeResolverTest {

    @Before
    fun setUp() {
        RuntimeModeResolver.resetForTesting()
    }

    @Test
    fun resolvesToUnknownAndFailsClosedWhenNoLsposedEntryIsActive() {
        val resolution = RuntimeModeResolver.resolve(null)
        assertEquals(RuntimeMode.UNKNOWN, resolution.mode)
        assertFalse(resolution.mode.isSupported)
    }

    @Test
    fun resolvesToRootModuleWhenXposedHookActive() {
        RuntimeModeResolver.notifyXposedHookActive()
        val resolution = RuntimeModeResolver.resolve(null)
        assertEquals(RuntimeMode.ROOT_MODULE, resolution.mode)
        assertTrue(resolution.mode.isSupported)
        assertEquals("xposed:hook-active", resolution.markerSource)
    }

}
