/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.config

import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.StructuredDiagnostics
import io.github.andrealtb.coloroslyrics.provider.core.mode.RuntimeMode
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderDebugConfigTest {

    @After
    fun tearDown() {
        StructuredDiagnostics.resetForTesting()
    }

    @Test
    fun registryContainsEveryMigrationProviderExactlyOnce() {
        assertEquals(
            setOf(
                "netease", "qq", "kugou", "kuwo", "spotify", "lx", "poweramp",
                "salt", "cone", "qishui", "musicfree", "gramophone", "symfonium", "metrolist", "apple"
            ),
            ProviderId.entries.map { it.configKey }.toSet()
        )
        assertEquals(15, ProviderId.entries.size)
    }

    @Test
    fun everyProviderDefaultsClosedInEveryRuntimeMode() {
        RuntimeMode.entries.forEach { mode ->
            ProviderId.entries.forEach { provider ->
                assertFalse(ProviderDebugConfig.resolve(mode, provider))
            }
        }
    }

    @Test
    fun rootSourceIsSelectedForAnActiveLsposedModule() {
        val rootSource = MapSource(mapOf(ProviderId.SALT to true))

        assertTrue(
            ProviderDebugConfig.resolve(RuntimeMode.ROOT_MODULE, ProviderId.SALT, rootSource)
        )
        assertEquals(1, rootSource.readCount)
    }

    @Test
    fun providerSettingsRemainIndependent() {
        val rootSource = MapSource(mapOf(ProviderId.SALT to true, ProviderId.APPLE to false))
        assertTrue(ProviderDebugConfig.resolve(RuntimeMode.ROOT_MODULE, ProviderId.SALT, rootSource))
        assertFalse(ProviderDebugConfig.resolve(RuntimeMode.ROOT_MODULE, ProviderId.APPLE, rootSource))
        assertFalse(ProviderDebugConfig.resolve(RuntimeMode.ROOT_MODULE, ProviderId.NETEASE, rootSource))
    }

    @Test
    fun prefsNameIsProviderScoped() {
        assertEquals("cone_provider_debug_prefs", ProviderDebugConfig.prefsName(ProviderId.CONE))
        assertEquals("salt_provider_debug_prefs", ProviderDebugConfig.prefsName(ProviderId.SALT))
        assertEquals("kuwo_provider_debug_prefs", ProviderDebugConfig.prefsName(ProviderId.KUWO))
        assertEquals("lx_provider_debug_prefs", ProviderDebugConfig.prefsName(ProviderId.LX))
        assertEquals("poweramp_provider_debug_prefs", ProviderDebugConfig.prefsName(ProviderId.POWERAMP))
        assertEquals("metrolist_provider_debug_prefs", ProviderDebugConfig.prefsName(ProviderId.METROLIST))
        assertEquals("kugou_provider_debug_prefs", ProviderDebugConfig.prefsName(ProviderId.KUGOU))
        assertEquals("qq_provider_debug_prefs", ProviderDebugConfig.prefsName(ProviderId.QQ))
        assertEquals("netease_provider_debug_prefs", ProviderDebugConfig.prefsName(ProviderId.NETEASE))
        assertEquals("apple_provider_debug_prefs", ProviderDebugConfig.prefsName(ProviderId.APPLE))
    }

    @Test
    fun resolveDetailedDistinguishesUserOffFromUnavailableSource() {
        val disabled = ProviderDebugSource { false }
        assertEquals(
            ProviderDebugResolution(false, "disabled"),
            ProviderDebugConfig.resolveDetailed(RuntimeMode.ROOT_MODULE, ProviderId.CONE, disabled)
        )
        assertEquals(
            ProviderDebugResolution(false, "disabled:source-unavailable"),
            ProviderDebugConfig.resolveDetailed(RuntimeMode.ROOT_MODULE, ProviderId.CONE, null)
        )
        val throwing = ProviderDebugSource { error("unavailable") }
        val failed = ProviderDebugConfig.resolveDetailed(
            RuntimeMode.ROOT_MODULE,
            ProviderId.CONE,
            throwing
        )
        assertFalse(failed.enabled)
        assertEquals("disabled:read-IllegalStateException", failed.reason)
        assertEquals(
            ProviderDebugResolution(false, "disabled:mode-unknown"),
            ProviderDebugConfig.resolveDetailed(RuntimeMode.UNKNOWN, ProviderId.CONE, disabled)
        )
    }

    @Test
    fun enabledRootSourceReportsEnabledReason() {
        val source = ProviderDebugSource { true }
        assertEquals(
            ProviderDebugResolution(true, "enabled"),
            ProviderDebugConfig.resolveDetailed(RuntimeMode.ROOT_MODULE, ProviderId.CONE, source)
        )
        assertTrue(ProviderDebugConfig.resolve(RuntimeMode.ROOT_MODULE, ProviderId.CONE, source))
    }

    @Test
    fun applyDiagnosticsUnknownModeDoesNotEnableDebug() {
        val resolution = ProviderDebugConfig.applyDiagnostics(
            RuntimeMode.UNKNOWN,
            ProviderId.CONE,
            ProviderDebugSource { true }
        )
        assertEquals("disabled:mode-unknown", resolution.reason)
        assertFalse(resolution.enabled)
        assertFalse(StructuredDiagnostics.isDebugEnabled)
    }

    @Test
    fun unavailableRootSourceAndUnknownModeFailClosed() {
        val throwing = ProviderDebugSource { error("unavailable") }
        assertFalse(ProviderDebugConfig.resolve(RuntimeMode.UNKNOWN, ProviderId.SALT, throwing))
        assertFalse(ProviderDebugConfig.resolve(RuntimeMode.ROOT_MODULE, ProviderId.SALT, throwing))
    }

    private class MapSource(private val values: Map<ProviderId, Boolean>) : ProviderDebugSource {
        var readCount = 0

        override fun read(provider: ProviderId): Boolean? {
            readCount++
            return values[provider]
        }
    }
}
