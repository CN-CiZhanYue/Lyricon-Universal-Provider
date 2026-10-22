package io.github.cn_cizhanyue.lyricon.universal.provider

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UniversalBridgeBindingNotifierTest {
    @Test
    fun contractMatchesBridgeWithoutProviderApplicationId() {
        assertEquals(
            "io.github.andrealtb.universallyrics.action.PLAYER_BINDINGS_CHANGED",
            UniversalBridgeBindingNotifier.ACTION
        )
        assertEquals("universal-player-provider", UniversalBridgeBindingNotifier.PROVIDER_IDENTITY)
        assertEquals("extra_bound_packages", UniversalBridgeBindingNotifier.EXTRA_BOUND_PACKAGES)
        assertEquals("extra_provider_identity", UniversalBridgeBindingNotifier.EXTRA_PROVIDER_IDENTITY)
        assertFalse(UniversalBridgeBindingNotifier.ACTION.contains("coloroslyrics.provider"))
    }

    @Test
    fun payloadDropsOnlyKuwo() {
        val extras = UniversalBridgeBindingNotifier.boundPackageList(
            setOf("com.salt.music", "com.tencent.qqmusic", "cn.kuwo.player", "remix.myplayer")
        )
        assertEquals(listOf("com.salt.music", "com.tencent.qqmusic", "remix.myplayer").sorted(), extras.sorted())
    }
}

