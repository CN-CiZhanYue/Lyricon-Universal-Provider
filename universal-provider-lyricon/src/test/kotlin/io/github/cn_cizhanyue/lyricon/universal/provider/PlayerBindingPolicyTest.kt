package io.github.cn_cizhanyue.lyricon.universal.provider

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerBindingPolicyTest {
    @Test
    fun blocksOnlyKuwo() {
        assertTrue(PlayerBindingPolicy.isBlocked("cn.kuwo.player"))
        assertEquals("禁用（需专项适配）", PlayerBindingPolicy.blockReason("cn.kuwo.player"))
        assertFalse(PlayerBindingPolicy.isBlocked("com.tencent.qqmusic"))
        assertFalse(PlayerBindingPolicy.isBlocked("com.kugou.android.lite"))
        assertNull(PlayerBindingPolicy.blockReason("com.heytap.music"))
    }

    @Test
    fun sanitizeRemovesBlockedKeepOthers() {
        val cleaned = PlayerBindingPolicy.sanitize(
            setOf("com.salt.music", "com.netease.cloudmusic", "cn.kuwo.player", "com.spotify.music")
        )
        assertEquals(setOf("com.salt.music", "com.netease.cloudmusic", "com.spotify.music"), cleaned)
    }
}
