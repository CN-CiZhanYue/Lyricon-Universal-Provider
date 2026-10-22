/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderProcessPolicyTest {

    @Test
    fun scopeOnlyAcceptsAnyProcessOfScopedPackage() {
        val policy = ScopeOnlyProcessPolicy(setOf("com.salt.music"))
        assertTrue(policy.accepts("com.salt.music", "com.salt.music"))
        assertTrue(policy.accepts("com.salt.music", "com.salt.music:player"))
        assertFalse(policy.accepts("com.other.player", "com.other.player"))
    }

    @Test
    fun explicitPolicyRequiresListedProcessName() {
        val policy = ExplicitProcessPolicy(
            packages = setOf("com.tencent.qqmusic"),
            acceptedProcessNames = setOf("com.tencent.qqmusic:QQPlayerService")
        )
        assertTrue(policy.accepts("com.tencent.qqmusic", "com.tencent.qqmusic:QQPlayerService"))
        assertFalse(policy.accepts("com.tencent.qqmusic", "com.tencent.qqmusic"))
        assertFalse(policy.accepts("com.tencent.qqmusic", "com.tencent.qqmusic:OtherService"))
        assertFalse(policy.accepts("com.other", "com.tencent.qqmusic:QQPlayerService"))
    }
}
