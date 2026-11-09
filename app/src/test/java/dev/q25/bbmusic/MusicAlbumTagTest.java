package dev.q25.bbmusic;

import org.junit.Test;
import static org.junit.Assert.*;

public class MusicAlbumTagTest {
    @Test public void absentAlbumIsEmptyAndNotBrowseable() {
        assertEquals("", MusicAlbumTag.normalize(null));
        assertEquals("", MusicAlbumTag.normalize(""));
        assertEquals("", MusicAlbumTag.normalize("  \t "));
        assertFalse(MusicAlbumTag.present(MusicAlbumTag.normalize(null)));
    }

    @Test public void literalUnknownAlbumTagIsPreservedAndBrowseable() {
        assertEquals("Unknown Album", MusicAlbumTag.normalize(" Unknown Album "));
        assertTrue(MusicAlbumTag.present("Unknown Album"));
        assertTrue(MusicAlbumTag.present("unknown album"));
    }

    @Test public void onlyAmbiguousLegacyValueNeedsRechecking() {
        assertTrue(MusicAlbumTag.requiresLegacyVerification("Unknown Album"));
        assertFalse(MusicAlbumTag.requiresLegacyVerification(""));
        assertFalse(MusicAlbumTag.requiresLegacyVerification("unknown album"));
        assertFalse(MusicAlbumTag.requiresLegacyVerification("Actual Album"));
    }
}
