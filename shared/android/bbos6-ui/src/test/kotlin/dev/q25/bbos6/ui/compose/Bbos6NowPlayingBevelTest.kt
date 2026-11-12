package dev.q25.bbos6.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the excessive mode/seek chrome glow seen when
 * their original per-pixel bevel intensity was flattened during Compose port.
 */
class Bbos6NowPlayingBevelTest {
    @Test fun modeBevelFadesFromTopToBottom() {
        val alphas = (0 until 7).map { bbos6ModeBevelAlpha(it, 7) }
        assertTrue(alphas.zipWithNext().all { (top, bottom) -> top >= bottom })
        assertTrue(alphas.first() > alphas.last())
        assertTrue(alphas.last() < 5)
        assertTrue(alphas.all { it in 0..42 })
    }

    @Test fun seekBevelBrightensOnlyNearBottom() {
        val alphas = (0 until 5).map { bbos6SeekBevelAlpha(it, 5) }
        assertTrue(alphas.zipWithNext().all { (top, bottom) -> top <= bottom })
        assertTrue(alphas.first() < 2)
        assertTrue(alphas[2] < alphas.last() / 3)
        assertEquals(52, alphas.last())
    }

    @Test fun bothBevelsHandleSingleRow() {
        assertTrue(bbos6ModeBevelAlpha(0, 1) in 0..42)
        assertEquals(52, bbos6SeekBevelAlpha(0, 1))
    }
}
