package dev.q25.bbmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

/**
 * JVM-only coverage for the Kotlin rules extracted from MusicActivity.
 * Full grouping/artwork parity requires the Q25 authorized SAF catalog.
 */
public final class MusicLibraryGroupPolicyTest {
    @Test public void countLabelsMatchExistingMusicWording() {
        assertEquals("0 Songs", MusicLibraryGroupPolicy.countLabel(0, "Song", "Songs"));
        assertEquals("1 Song", MusicLibraryGroupPolicy.countLabel(1, "Song", "Songs"));
        assertEquals("2 Songs", MusicLibraryGroupPolicy.countLabel(2, "Song", "Songs"));
        assertEquals("1 Item", MusicLibraryGroupPolicy.countLabel(1, "Item", "Items"));
    }

    @Test public void emptyCatalogHasNoGroups() {
        assertTrue(MusicLibraryGroupPolicy.groups(Collections.emptyList(), 1).isEmpty());
        assertTrue(MusicLibraryGroupPolicy.groups(Collections.emptyList(), 2).isEmpty());
        assertTrue(MusicLibraryGroupPolicy.groups(Collections.emptyList(), 3).isEmpty());
    }
}
