package dev.q25.bbos6.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class Bbos6CheckboxMetricsTest {
    @Test
    fun originalQsCellCentersNineteenDpSquare() {
        val box = Bbos6CheckboxMetrics.bounds(widthPx = 24f, heightPx = 26f, density = 1f)
        assertEquals(19f, box.edge, 0.001f)
        assertEquals(2.5f, box.left, 0.001f)
        assertEquals(3.5f, box.top, 0.001f)
    }

    @Test
    fun geometryScalesWithDisplayDensity() {
        val box = Bbos6CheckboxMetrics.bounds(widthPx = 48f, heightPx = 52f, density = 2f)
        assertEquals(38f, box.edge, 0.001f)
        assertEquals(5f, box.left, 0.001f)
        assertEquals(7f, box.top, 0.001f)
    }

    @Test
    fun smallerCanvasUsesAvailableWidthWithoutClipping() {
        val box = Bbos6CheckboxMetrics.bounds(widthPx = 12f, heightPx = 16f, density = 1f)
        assertEquals(12f, box.edge, 0.001f)
        assertEquals(0f, box.left, 0.001f)
        assertEquals(2f, box.top, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun densityMustBePositive() {
        Bbos6CheckboxMetrics.bounds(widthPx = 24f, heightPx = 26f, density = 0f)
    }
}
