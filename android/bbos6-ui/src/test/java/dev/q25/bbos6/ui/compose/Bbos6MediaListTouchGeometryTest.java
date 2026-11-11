package dev.q25.bbos6.ui.compose;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class Bbos6MediaListTouchGeometryTest {
    @Test public void onlyTrailingActionReceivesComposeTouch() {
        // 300x68dp row, 76x36dp action, 10dp from right, centered vertically.
        assertTrue(Bbos6MediaListTouchGeometry.hitTrailingAction(
                215f, 34f, 300, 68, 1f));
        assertFalse(Bbos6MediaListTouchGeometry.hitTrailingAction(
                100f, 34f, 300, 68, 1f));
        assertFalse(Bbos6MediaListTouchGeometry.hitTrailingAction(
                215f, 5f, 300, 68, 1f));
        assertFalse(Bbos6MediaListTouchGeometry.hitTrailingAction(
                299f, 34f, 300, 68, 1f));
    }
    @Test public void hitboxUsesDensityAndRejectsInvalidGeometry() {
        assertTrue(Bbos6MediaListTouchGeometry.hitTrailingAction(
                430f, 68f, 600, 136, 2f));
        assertFalse(Bbos6MediaListTouchGeometry.hitTrailingAction(
                100f, 68f, 600, 136, 2f));
        assertFalse(Bbos6MediaListTouchGeometry.hitTrailingAction(
                430f, 68f, 600, 136, 0f));
    }
}
