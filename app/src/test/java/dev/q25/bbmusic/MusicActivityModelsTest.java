package dev.q25.bbmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import dev.q25.bbmusic.MusicActivityModels.MenuRow;
import dev.q25.bbmusic.MusicActivityModels.Page;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Java ABI and default-state contract for the extracted Activity models. */
public final class MusicActivityModelsTest {
    @Test public void menuRowsKeepOriginalKindsAndIndices() {
        MenuRow root = MenuRow.root("Albums", 2);
        assertEquals(MusicActivityModels.ROW_MENU, root.kind);
        assertEquals(2, root.menuIndex);
        assertEquals(-1L, root.playlistId);
        assertEquals("Albums", root.title);

        MenuRow now = MenuRow.nowPlaying();
        assertEquals(MusicActivityModels.ROW_NOW_PLAYING, now.kind);
        assertEquals("Now Playing...", now.title);

        MenuRow folder = MenuRow.browserUnavailable("Unavailable");
        assertEquals(MusicActivityModels.ROW_BROWSER_UNAVAILABLE, folder.kind);
        assertNull(folder.song);

        MenuRow message = MenuRow.message("Nothing to show");
        assertEquals(MusicActivityModels.ROW_MESSAGE, message.kind);
    }

    @Test public void pageRowsStayMutableAndEmptyStateCanBeCleared() {
        List<MenuRow> rows = new ArrayList<>();
        Page page = new Page("Music", rows);
        assertSame(rows, page.rows);
        assertNull(page.albumGroup);
        assertEquals(-1L, page.playlistId);
        assertEquals(-1L, page.playlistPickerId);
        assertFalse(page.playlistDirectory);
        page.rows.add(MenuRow.setting("Refresh library"));
        assertEquals(1, page.rows.size());

        page.setEmptyState("Empty playlist", "Add songs", 1, 7);
        assertEquals("Empty playlist", page.emptyTitle);
        assertEquals(1, page.emptyAfterRows);
        assertEquals(7, page.emptyArtworkRes);
        assertTrue(page.showsEmptyState());
        page.rows.add(MenuRow.message("Loading playlist..."));
        assertFalse(page.showsEmptyState());
        page.rows.remove(1);
        assertTrue(page.showsEmptyState());
        page.setEmptyState(null, null, 0);
        assertNull(page.emptyTitle);
        assertNull(page.emptyMessage);
        assertEquals(0, page.emptyArtworkRes);
        assertFalse(page.showsEmptyState());
    }

    @Test public void zeroContentEmptyStateDoesNotConfuseLoadingWithEmptiness() {
        Page empty = new Page("Artists", new ArrayList<>());
        empty.setEmptyState("No Artists.", "No artists found", 0, 42);
        assertTrue(empty.showsEmptyState());
        empty.rows.add(MenuRow.message("Loading library..."));
        assertFalse(empty.showsEmptyState());
        empty.rows.clear();
        assertTrue(empty.showsEmptyState());
        assertEquals(42, empty.emptyArtworkRes);
    }

    @Test public void folderAndPlaylistActionRowsPreserveIdentifiers() {
        MenuRow action = MenuRow.playlistAction("Add songs", "add", 49L);
        assertEquals(MusicActivityModels.ROW_PLAYLIST_ACTION, action.kind);
        assertEquals(49L, action.playlistId);
        assertEquals(-1L, action.playlistEntryId);
        assertEquals("add", action.playlistAction);
        assertTrue(action.songs == null);
    }
}
