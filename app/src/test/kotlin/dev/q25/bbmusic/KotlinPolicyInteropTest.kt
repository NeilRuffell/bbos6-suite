package dev.q25.bbmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinPolicyInteropTest {
    @Test fun albumTagAndTrackpadRemainExact() {
        assertEquals("", MusicAlbumTag.normalize(null))
        assertFalse(MusicAlbumTag.present("  "))
        assertTrue(MusicAlbumTag.requiresLegacyVerification("Unknown Album"))
        val pad = TrackpadPressState()
        assertTrue(pad.press(23))
        assertFalse(pad.press(66))
        assertTrue(pad.fireLongPress())
        assertFalse(pad.release(23))
        assertFalse(pad.isHeld())
    }

    @Test fun progressDoesNotEscapeDuration() {
        assertEquals(0, MediaSeekPolicy.toProgress(20, 0))
        assertEquals(1000, MediaSeekPolicy.toProgress(12000, 10000))
        assertEquals(10000L, MediaSeekPolicy.fromProgress(2000, 10000))
        assertEquals("-0:01", MediaSeekPolicy.formatRemainingTime(10000, 9000))
    }

    @Test fun deletedQueueAndPathPoliciesRetainBounds() {
        assertEquals(1, MusicDeletedQueueIndex.afterFiltering(2, booleanArrayOf(true, false, true)))
        assertEquals(-1, MusicDeletedQueueIndex.afterFiltering(0, booleanArrayOf(false)))
        assertEquals(null, MusicPlaylistPaths.resolve("/storage", null, "somewhere/track.mp3"))
        assertFalse(MusicFilePolicy.isAudio("example.m3u", "audio/mpeg"))
    }
}
