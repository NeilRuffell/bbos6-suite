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
    @Test public void emptyCatalogProducesNoArtworkSourcesAndZeroCategoryCounts() {
        assertTrue(MusicLibraryGroupPolicy.indexAlbumArtworkSources(null).isEmpty());
        assertTrue(MusicLibraryGroupPolicy.indexAlbumArtworkSources(
                Collections.emptyList()).isEmpty());

        int[] counts = {9, 8, 7, 6};
        MusicLibraryGroupPolicy.fillCategoryCounts(Collections.emptyList(), counts);
        assertEquals(0, counts[0]);
        assertEquals(0, counts[1]);
        assertEquals(0, counts[2]);
        assertEquals(0, counts[3]);
    }

    @Test public void playbackOrderingOnlyAcceptsGroupRows() {
        assertTrue(MusicLibraryGroupPolicy.orderedGroupSongs(
                MusicActivityModels.MenuRow.setting("Refresh library")).isEmpty());
        assertTrue(MusicLibraryGroupPolicy.orderedGroupSongs(
                MusicActivityModels.MenuRow.group("Empty album", 2, Collections.emptyList())
        ).isEmpty());
        assertTrue(MusicLibraryGroupPolicy.orderedGroupSongs(
                MusicActivityModels.MenuRow.group("Empty artist", 1, Collections.emptyList())
        ).isEmpty());
    }

    @Test public void nonMediaRowsDoNotResolveArtworkKeys() {
        assertEquals(null, MusicLibraryGroupPolicy.albumKeyForRow(
                MusicActivityModels.MenuRow.setting("Refresh library")));
        assertEquals(null, MusicLibraryGroupPolicy.albumKeyForRow(
                MusicActivityModels.MenuRow.group("Empty", 2, Collections.emptyList())));
    }

}
