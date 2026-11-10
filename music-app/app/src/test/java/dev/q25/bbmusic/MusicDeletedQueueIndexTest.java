package dev.q25.bbmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/** Regression coverage for retained playback after permanent SAF deletion. */
public final class MusicDeletedQueueIndexTest {
    @Test public void removingLaterTrackPreservesCurrentSelection() {
        assertEquals(1, MusicDeletedQueueIndex.afterFiltering(1,
                new boolean[]{true, true, false, true}));
    }

    @Test public void removingEarlierTrackAdjustsIndex() {
        assertEquals(0, MusicDeletedQueueIndex.afterFiltering(1,
                new boolean[]{false, true, true}));
    }

    @Test public void removingCurrentPrefersNextSurvivor() {
        assertEquals(1, MusicDeletedQueueIndex.afterFiltering(1,
                new boolean[]{true, false, true}));
    }

    @Test public void removingLastCurrentSelectsPrevious() {
        assertEquals(0, MusicDeletedQueueIndex.afterFiltering(2,
                new boolean[]{true, false, false}));
    }

    @Test public void removingAllTracksHasNoCurrentSong() {
        assertEquals(-1, MusicDeletedQueueIndex.afterFiltering(0,
                new boolean[]{false, false}));
    }

    @Test public void invalidSelectionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MusicDeletedQueueIndex.afterFiltering(-1,
                        new boolean[]{true}));
        assertThrows(IllegalArgumentException.class,
                () -> MusicDeletedQueueIndex.afterFiltering(1,
                        new boolean[]{true}));
    }
}
