package dev.q25.bbos6.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class Bbos6NowPlayingRailTest {
    @Test fun railProgressClampsToBothEndpoints() {
        assertEquals(0f, bbos6NowPlayingRailFraction(-40, 1000), 0f)
        assertEquals(0f, bbos6NowPlayingRailFraction(0, 1000), 0f)
        assertEquals(0.5f, bbos6NowPlayingRailFraction(500, 1000), 0f)
        assertEquals(1f, bbos6NowPlayingRailFraction(1000, 1000), 0f)
        assertEquals(1f, bbos6NowPlayingRailFraction(1100, 1000), 0f)
    }

    @Test fun unknownDurationCannotProduceFilledRail() {
        assertEquals(0f, bbos6NowPlayingRailFraction(500, 0), 0f)
        assertEquals(0f, bbos6NowPlayingRailFraction(500, -1), 0f)
    }
}
