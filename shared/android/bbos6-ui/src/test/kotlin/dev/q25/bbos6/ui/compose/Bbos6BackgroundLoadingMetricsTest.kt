package dev.q25.bbos6.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Bbos6BackgroundLoadingMetricsTest {
    @Test
    fun screenshotSizedStripAndRepeatInterval() {
        assertEquals(14, Bbos6BackgroundLoadingMetrics.HEIGHT_DP)
        assertEquals(10, Bbos6BackgroundLoadingMetrics.STRIPE_SPACING_DP)
        assertEquals(8, Bbos6BackgroundLoadingMetrics.STRIPE_SKEW_DP)
        assertTrue(Bbos6BackgroundLoadingMetrics.PERIOD_MS > 0)
    }
}
