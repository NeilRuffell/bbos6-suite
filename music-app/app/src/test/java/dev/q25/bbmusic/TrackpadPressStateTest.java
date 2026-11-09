package dev.q25.bbmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Hardware press state must never also click after a long press. */
public final class TrackpadPressStateTest {
    private static final int CONFIRM = 23;
    private static final int OTHER_KEY = 66;

    @Test public void shortPressActivatesExactlyOnceOnRelease() {
        TrackpadPressState state = new TrackpadPressState();
        assertTrue(state.press(CONFIRM));
        assertFalse(state.press(CONFIRM));
        assertTrue(state.release(CONFIRM));
        assertFalse(state.release(CONFIRM));
    }

    @Test public void heldPressFiresLongOnlyOnceAndNeverClicks() {
        TrackpadPressState state = new TrackpadPressState();
        assertTrue(state.press(CONFIRM));
        assertTrue(state.fireLongPress());
        assertFalse(state.fireLongPress());
        assertFalse(state.release(CONFIRM));
        assertFalse(state.fireLongPress());
    }

    @Test public void canceledHoldNeverActivates() {
        TrackpadPressState state = new TrackpadPressState();
        assertTrue(state.press(CONFIRM));
        state.reset();
        assertFalse(state.fireLongPress());
        assertFalse(state.release(CONFIRM));
    }

    @Test public void mismatchedKeyUpNeverActivates() {
        TrackpadPressState state = new TrackpadPressState();
        assertTrue(state.press(CONFIRM));
        assertFalse(state.release(OTHER_KEY));
        assertFalse(state.isHeld());
    }

    @Test public void newPressAfterLongPressWorksNormally() {
        TrackpadPressState state = new TrackpadPressState();
        assertTrue(state.press(CONFIRM));
        assertTrue(state.fireLongPress());
        assertFalse(state.release(CONFIRM));
        assertTrue(state.press(CONFIRM));
        assertTrue(state.release(CONFIRM));
    }
}
