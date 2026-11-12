package dev.q25.bbmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import dev.q25.bbmusic.MusicActivityModels.MenuRow;

import java.util.Collections;
import org.junit.Test;

/** Existing menu-row appearance and metadata, without Android rendering. */
public final class MusicLibraryRowPolicyTest {
    @Test public void originalMenuIconMappingsAreUnchanged() {
        assertEquals(R.drawable.bbos6_menu_all_songs,
                MusicLibraryRowPolicy.menuIconForRow(MenuRow.root("All Songs", 0)));
        assertEquals(R.drawable.bbos6_menu_artists,
                MusicLibraryRowPolicy.menuIconForRow(MenuRow.root("Artists", 1)));
        assertEquals(R.drawable.bbos6_menu_albums,
                MusicLibraryRowPolicy.menuIconForRow(MenuRow.root("Albums", 2)));
        assertEquals(R.drawable.bbos6_menu_genres,
                MusicLibraryRowPolicy.menuIconForRow(MenuRow.root("Genres", 3)));
        assertEquals(R.drawable.bbos6_menu_playlists,
                MusicLibraryRowPolicy.menuIconForRow(MenuRow.root("Playlists", 4)));
        assertEquals(R.drawable.bbos6_menu_shuffle_songs,
                MusicLibraryRowPolicy.menuIconForRow(MenuRow.root("Shuffle Songs", 5)));
        assertEquals(R.drawable.bbos6_menu_settings,
                MusicLibraryRowPolicy.menuIconForRow(MenuRow.root("Settings", 6)));
        assertEquals(R.drawable.bbos6_menu_folders,
                MusicLibraryRowPolicy.menuIconForRow(MenuRow.root("Folders", 7)));
        assertEquals(R.drawable.bbos6_menu_now_playing,
                MusicLibraryRowPolicy.menuIconForRow(MenuRow.nowPlaying()));
        MusicLibraryDatabase.PlaylistSummary playlist =
                new MusicLibraryDatabase.PlaylistSummary();
        playlist.id = 42L;
        playlist.name = "Favorites";
        playlist.songCount = 3;
        assertEquals(R.drawable.bbos6_menu_playlists,
                MusicLibraryRowPolicy.menuIconForRow(MenuRow.playlist(playlist)));
        assertEquals(R.drawable.bbos6_menu_add_playlist,
                MusicLibraryRowPolicy.menuIconForRow(
                        MenuRow.playlistAction("Add Playlist", "choose-add", -1L)));
        assertEquals(R.drawable.bbos6_menu_play_all,
                MusicLibraryRowPolicy.menuIconForRow(
                        MenuRow.playlistAction("Play all", "play", 42L)));
        assertEquals(R.drawable.bbos6_menu_shuffle_songs,
                MusicLibraryRowPolicy.menuIconForRow(
                        MenuRow.playlistAction("Shuffle", "shuffle", 42L)));
        assertEquals(0, MusicLibraryRowPolicy.menuIconForRow(
                MenuRow.playlistAction("Other", "unknown", 42L)));
        assertEquals(0, MusicLibraryRowPolicy.menuIconForRow(
                MenuRow.settingsHeader("Music library")));
    }

    @Test public void countWordingMatchesNativeRows() {
        int[] counts = {21, 7, 4, 6};
        assertEquals("Loading...", MusicLibraryRowPolicy.childCountLabel(
                MenuRow.root("All Songs", 0), null, true, counts, 3));
        assertEquals("Unavailable", MusicLibraryRowPolicy.childCountLabel(
                MenuRow.root("All Songs", 0), null, false, counts, 3));
        assertEquals("21 Songs", MusicLibraryRowPolicy.childCountLabel(
                MenuRow.root("All Songs", 0), 21, false, counts, 3));
        assertEquals("7 Items", MusicLibraryRowPolicy.childCountLabel(
                MenuRow.root("Artists", 1), 21, false, counts, 3));
        assertEquals("4 Items", MusicLibraryRowPolicy.childCountLabel(
                MenuRow.root("Albums", 2), 21, false, counts, 3));
        assertEquals("3 Items", MusicLibraryRowPolicy.childCountLabel(
                MenuRow.root("Playlists", 4), 21, false, counts, 3));
        assertNull(MusicLibraryRowPolicy.childCountLabel(
                MenuRow.root("Folders", 7), 21, false, counts, 3));
        assertNull(MusicLibraryRowPolicy.childCountLabel(
                MenuRow.root("Settings", 6), 21, false, counts, 3));
    }

    @Test public void settingsAndDisabledRowsRetainNativeRowFlags() {
        int[] counts = {0, 0, 0, 0};
        MenuRow setting = MenuRow.setting("Auto refresh on launch");
        MusicListView.RowData on = MusicLibraryRowPolicy.presentation(
                setting, false, Collections.emptyMap(), 0, false, counts, 0, true);
        MusicListView.RowData off = MusicLibraryRowPolicy.presentation(
                setting, false, Collections.emptyMap(), 0, false, counts, 0, false);
        assertEquals("On", on.subtitle);
        assertEquals("Off", off.subtitle);
        assertTrue(on.enabled);
        assertEquals(0, on.menuIconRes);

        MusicListView.RowData header = MusicLibraryRowPolicy.presentation(
                MenuRow.settingsHeader("Music library"), false,
                Collections.emptyMap(), 0, false, counts, 0, false);
        assertFalse(header.enabled);
        assertTrue(header.sectionHeader);

        MusicListView.RowData missing = MusicLibraryRowPolicy.presentation(
                MenuRow.playlistMissing("Missing song", 8L, 9L), false,
                Collections.emptyMap(), 0, false, counts, 1, false);
        assertTrue(missing.enabled);
        assertEquals("Track unavailable", missing.subtitle);

        MusicListView.RowData message = MusicLibraryRowPolicy.presentation(
                MenuRow.message("No local music found"), false,
                Collections.emptyMap(), 0, false, counts, 0, false);
        assertFalse(message.enabled);
        assertFalse(message.showArtwork);
    }
}
