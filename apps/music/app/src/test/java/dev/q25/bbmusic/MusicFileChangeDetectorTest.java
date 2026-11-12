package dev.q25.bbmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class MusicFileChangeDetectorTest {
    @Test public void matchingFingerprintReusesMetadata() {
        assertTrue(MusicFileChangeDetector.unchanged(
                12000, 1000, "track.mp3", 12000, 1000, "track.mp3"));
    }
    @Test public void changedSizeRequiresRecheck() {
        assertFalse(MusicFileChangeDetector.unchanged(
                12000, 1000, "track.mp3", 12001, 1000, "track.mp3"));
    }
    @Test public void changedTimestampRequiresRecheck() {
        assertFalse(MusicFileChangeDetector.unchanged(
                12000, 1000, "track.mp3", 12000, 2000, "track.mp3"));
    }
    @Test public void changedNameRequiresRecheck() {
        assertFalse(MusicFileChangeDetector.unchanged(
                12000, 1000, "track.mp3", 12000, 1000, "renamed.mp3"));
    }
    @Test public void unknownSizeOrTimeRequiresRecheck() {
        assertFalse(MusicFileChangeDetector.unchanged(
                -1, -1, "track.mp3", 12000, 1000, "track.mp3"));
        assertFalse(MusicFileChangeDetector.unchanged(
                12000, 1000, "track.mp3", -1, 1000, "track.mp3"));
        assertFalse(MusicFileChangeDetector.unchanged(
                12000, 1000, "track.mp3", 12000, -1, "track.mp3"));
        assertFalse(MusicFileChangeDetector.unchanged(
                12000, 1000, "track.mp3", 12000, 0, "track.mp3"));
    }
}
