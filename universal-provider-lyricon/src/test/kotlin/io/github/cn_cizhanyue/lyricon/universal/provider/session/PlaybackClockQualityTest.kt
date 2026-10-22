package io.github.cn_cizhanyue.lyricon.universal.provider.session

import io.github.cn_cizhanyue.lyricon.universal.provider.session.PlaybackClockQuality.Verdict
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PlaybackClockQualityTest {
    private val quality = PlaybackClockQuality()

    /**
     * Publishes [count] PLAYING anchors [intervalMs] apart whose position is the true position
     * floored to [blockMs] (1 = exact), the way a decoder-block counter reports it.
     */
    private fun feed(
        blockMs: Long,
        count: Int,
        firstIndex: Int = 0,
        session: String = "s1",
        generation: Long = 1L,
        intervalMs: Long = 1_003L
    ): Verdict {
        var verdict = quality.verdict(PACKAGE)
        for (index in firstIndex until firstIndex + count) {
            val truePosition = 10_137L + index * intervalMs
            verdict = quality.observe(
                PACKAGE,
                session,
                generation,
                PlaybackStates.PLAYING,
                positionMs = truePosition / blockMs * blockMs,
                updateTimeMs = 500_000L + index * intervalMs,
                speed = 1f
            )
        }
        return verdict
    }

    @Test
    fun `400 ms blocks republished every second are coarse`() {
        // Six anchors give five steps, one short of a decision.
        assertEquals(Verdict.UNKNOWN, feed(blockMs = 400L, count = 6))
        assertEquals(Verdict.COARSE, feed(blockMs = 400L, count = 1, firstIndex = 6))
        assertEquals(6, quality.stats(PACKAGE).coarseSamples)
    }

    @Test
    fun `exact and fine-grained positions are steady`() {
        assertEquals(Verdict.UNKNOWN, feed(blockMs = 1L, count = 8))
        assertEquals(Verdict.STEADY, feed(blockMs = 1L, count = 1, firstIndex = 8))

        val fine = PlaybackClockQuality()
        var verdict = Verdict.UNKNOWN
        for (index in 0 until 9) {
            val truePosition = 10_137L + index * 1_003L
            verdict = fine.observe(PACKAGE, "s1", 1L, PlaybackStates.PLAYING, truePosition / 100L * 100L, 500_000L + index * 1_003L, 1f)
        }
        assertEquals(Verdict.STEADY, verdict)
    }

    @Test
    fun `seeks are discontinuities rather than clock grain`() {
        feed(blockMs = 1L, count = 5)
        // A 30 s jump between two otherwise exact anchors.
        quality.observe(PACKAGE, "s1", 1L, PlaybackStates.PLAYING, 45_000L, 505_015L + 1_003L, 1f)
        var verdict = Verdict.UNKNOWN
        for (step in 1..8) {
            verdict = quality.observe(PACKAGE, "s1", 1L, PlaybackStates.PLAYING, 45_000L + step * 1_003L, 506_018L + step * 1_003L, 1f)
        }
        assertEquals(Verdict.STEADY, verdict)
        assertEquals(0, quality.stats(PACKAGE).coarseSamples)
    }

    @Test
    fun `pauses and track changes re-anchor instead of sampling`() {
        var verdict = Verdict.UNKNOWN
        for (index in 0 until 12) {
            val truePosition = 10_137L + index * 1_003L
            val updateTime = 500_000L + index * 1_003L
            val state = if (index % 2 == 0) PlaybackStates.PLAYING else PlaybackStates.PAUSED
            verdict = quality.observe(PACKAGE, "s1", 1L, state, truePosition / 400L * 400L, updateTime, 1f)
        }
        assertEquals(Verdict.UNKNOWN, verdict)
        assertEquals(0, quality.stats(PACKAGE).samples)

        // A new generation restarts position near zero; that step is never a sample.
        feed(blockMs = 1L, count = 3)
        quality.observe(PACKAGE, "s1", 2L, PlaybackStates.PLAYING, 200L, 600_000L, 1f)
        assertEquals(2, quality.stats(PACKAGE).samples)
    }

    @Test
    fun `verdict survives a recreated session but anchors do not cross sessions`() {
        assertEquals(Verdict.COARSE, feed(blockMs = 400L, count = 7))
        val samples = quality.stats(PACKAGE).samples

        assertEquals(Verdict.COARSE, feed(blockMs = 1L, count = 1, firstIndex = 7, session = "s2"))
        assertEquals(samples, quality.stats(PACKAGE).samples)
        assertEquals(Verdict.UNKNOWN, quality.verdict("other.package"))
    }

    @Test
    fun `a coarse verdict needs a full steady window to recover`() {
        assertEquals(Verdict.COARSE, feed(blockMs = 400L, count = 7))
        // Mixed windows keep the previous verdict instead of flapping.
        assertEquals(Verdict.COARSE, feed(blockMs = 1L, count = 4, firstIndex = 7))
        assertNotEquals(Verdict.STEADY, quality.verdict(PACKAGE))
        assertEquals(Verdict.STEADY, feed(blockMs = 1L, count = 8, firstIndex = 11))
    }

    private companion object {
        const val PACKAGE = "com.example.player"
    }
}
