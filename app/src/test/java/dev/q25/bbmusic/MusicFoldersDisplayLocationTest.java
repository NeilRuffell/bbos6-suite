package dev.q25.bbmusic;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class MusicFoldersDisplayLocationTest {
    @Test public void primaryVolumeFolderDisplaysOnlyPath() {
        assertEquals("Music", MusicFolders.displayLocation("primary:Music"));
        assertEquals("Music/Albums", MusicFolders.displayLocation("primary:Music/Albums"));
    }

    @Test public void primaryVolumeRootHasReadableLabel() {
        assertEquals("Internal storage", MusicFolders.displayLocation("primary:"));
    }

    @Test public void otherStorageIdentifiersRemainDistinct() {
        assertEquals("12AB-34CD:Music", MusicFolders.displayLocation("12AB-34CD:Music"));
    }

    @Test public void alreadyReadablePathsAndMissingIdsAreSafe() {
        assertEquals("Music/Albums", MusicFolders.displayLocation("Music/Albums"));
        assertEquals("", MusicFolders.displayLocation(""));
        assertEquals("", MusicFolders.displayLocation(null));
    }
}
