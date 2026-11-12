package dev.q25.bbmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import dev.q25.bbmusic.MusicActivityModels.MenuRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import org.junit.Test;

/** Regression coverage for the Activity's Kotlin row/list search boundary. */
public final class MusicLibraryPageRowsTest {
    @Test public void settingsRowsKeepOriginalNamesAndOrder() {
        ArrayList<MenuRow> rows = MusicLibraryPageRows.settingsRows();
        assertEquals(5, rows.size());
        assertEquals(MusicActivityModels.ROW_SETTINGS_HEADER, rows.get(0).kind);
        assertEquals("Music library", rows.get(0).title);
        assertEquals("Music Folders", rows.get(1).title);
        assertEquals("Refresh library", rows.get(2).title);
        assertEquals("Full rescan", rows.get(3).title);
        assertEquals("Auto refresh on launch", rows.get(4).title);
    }

    @Test public void emptyLibraryLeavesRowsEmptyForSharedIllustratedState() {
        List<MenuRow> rows = MusicLibraryPageRows.songRows(Collections.emptyList());
        assertTrue(rows.isEmpty());
        List<MenuRow> groups = MusicLibraryPageRows.groupedRows(Collections.emptyList(), 2);
        assertTrue(groups.isEmpty());
    }

    @Test public void clearingSearchReturnsExistingPageRowsByIdentity() {
        List<MenuRow> rows = new ArrayList<>();
        rows.add(MenuRow.setting("Refresh library"));
        assertSame(rows, MusicLibraryPageRows.searchRows(
                "", false, rows, null, false));
        assertEquals(1, rows.size());
    }

    @Test public void nestedSearchSkipsHeadersAndMatchesTitlesWithoutMutation() {
        List<MenuRow> rows = new ArrayList<>();
        MenuRow header = MenuRow.settingsHeader("Music library");
        MenuRow match = MenuRow.setting("Music Folders");
        rows.add(header);
        rows.add(match);
        rows.add(MenuRow.setting("Full rescan"));
        rows.add(MenuRow.message("Music unavailable"));
        List<MenuRow> found = MusicLibraryPageRows.searchRows(
                "music", false, rows, null, false);
        assertEquals(1, found.size());
        assertSame(match, found.get(0));
        assertEquals(4, rows.size());
        assertSame(header, rows.get(0));
    }

    @Test public void rootSearchUsesCatalogInsteadOfRootMenuLabels() {
        List<MenuRow> root = Collections.singletonList(MenuRow.root("Music", 0));
        List<MenuRow> none = MusicLibraryPageRows.searchRows(
                "music", true, root, Collections.emptyList(), false);
        assertTrue(none.isEmpty());
        List<MenuRow> loading = MusicLibraryPageRows.searchRows(
                "music", true, root, null, true);
        assertEquals(1, loading.size());
        assertEquals("Reading selected music folders...", loading.get(0).title);
        assertEquals(MusicActivityModels.ROW_MESSAGE, loading.get(0).kind);
        assertFalse(loading == root);
    }

    @Test public void folderBrowserExcludesUnverifiedCandidatesAndRepeatedDocumentUris() {
        Set<String> indexed = new HashSet<>();
        indexed.add("content://music/real.mp3");
        Set<String> displayed = new HashSet<>();

        assertTrue(MusicLibraryPageRows.shouldRenderBrowserFile(
                "content://music/real.mp3", indexed, displayed));
        displayed.add("content://music/real.mp3");

        assertFalse(MusicLibraryPageRows.shouldRenderBrowserFile(
                "content://music/real.mp3", indexed, displayed));
        // An MP3-looking file without an indexed, verified stream is not a song row.
        assertFalse(MusicLibraryPageRows.shouldRenderBrowserFile(
                "content://music/sidecar.mp3", indexed, displayed));
    }

    @Test public void browserRowsPreserveLoadingAndEmptyFolderSemantics() {
        MusicActivityModels.Page page =
                new MusicActivityModels.Page("Folder", new ArrayList<>());
        // A folder still loading has no content rows yet.
        assertTrue(MusicLibraryPageRows.browserRows(page, null).isEmpty());
        page.browserEntries = Collections.emptyList();
        List<MenuRow> empty = MusicLibraryPageRows.browserRows(
                page, Collections.emptyList());
        assertTrue(empty.isEmpty());
        assertTrue(page.rows.isEmpty());
    }

}
