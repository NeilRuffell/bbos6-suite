package dev.q25.bbmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Never invent an absolute path for SAF document IDs. */
public final class MusicDocumentPathTest {
    @Test public void primaryStorageUsesReadableLogicalPath() {
        assertEquals("Internal storage/Music/Album/Track.mp3",
                MusicDocumentPath.readableLocation(
                        "com.android.externalstorage.documents",
                        "primary:Music/Album/Track.mp3", "Track.mp3"));
    }

    @Test public void removableVolumeRetainsItsDistinctId() {
        assertEquals("1234-ABCD/Music/Track.flac",
                MusicDocumentPath.readableLocation(
                        "com.android.externalstorage.documents",
                        "1234-ABCD:Music/Track.flac", "Track.flac"));
    }

    @Test public void opaqueAndOtherProvidersDoNotInventPaths() {
        assertNull(MusicDocumentPath.readableLocation(
                "com.android.providers.media.documents", "audio:512", "Track.mp3"));
        assertNull(MusicDocumentPath.readableLocation(
                "com.android.externalstorage.documents", "home:Music/Track.mp3",
                "Track.mp3"));
        assertNull(MusicDocumentPath.readableLocation(
                "com.android.externalstorage.documents", "primary:Music/Different.mp3",
                "Track.mp3"));
    }

    @Test public void invalidPathComponentsAreRejected() {
        assertNull(MusicDocumentPath.readableLocation(
                "com.android.externalstorage.documents", "primary:../Track.mp3",
                "Track.mp3"));
        assertNull(MusicDocumentPath.readableLocation(
                "com.android.externalstorage.documents", "primary:/Track.mp3",
                "Track.mp3"));
    }
}
