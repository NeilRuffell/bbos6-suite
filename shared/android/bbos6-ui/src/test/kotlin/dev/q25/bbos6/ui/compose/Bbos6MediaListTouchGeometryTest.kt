package dev.q25.bbos6.ui.compose

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Bbos6MediaListTouchGeometryTest {
    @Test fun onlyTrailingActionReceivesComposeTouch() {
        assertTrue(Bbos6MediaListTouchGeometry.hitTrailingAction(215f, 34f, 300, 68, 1f))
        assertFalse(Bbos6MediaListTouchGeometry.hitTrailingAction(100f, 34f, 300, 68, 1f))
        assertFalse(Bbos6MediaListTouchGeometry.hitTrailingAction(215f, 5f, 300, 68, 1f))
        assertFalse(Bbos6MediaListTouchGeometry.hitTrailingAction(299f, 34f, 300, 68, 1f))
    }

    @Test fun hitboxUsesDensityAndRejectsInvalidGeometry() {
        assertTrue(Bbos6MediaListTouchGeometry.hitTrailingAction(430f, 68f, 600, 136, 2f))
        assertFalse(Bbos6MediaListTouchGeometry.hitTrailingAction(100f, 68f, 600, 136, 2f))
        assertFalse(Bbos6MediaListTouchGeometry.hitTrailingAction(430f, 68f, 600, 136, 0f))
    }
}
