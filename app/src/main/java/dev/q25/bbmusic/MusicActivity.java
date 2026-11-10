package dev.q25.bbmusic;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import dev.q25.bbos6.ui.Bbos6ContextMenu;
import dev.q25.bbos6.ui.Bbos6ContextMenuItem;
import dev.q25.bbos6.ui.Bbos6EmptyStateView;
import dev.q25.bbos6.ui.Bbos6PropertiesPopup;

import androidx.media3.common.MediaItem;
import androidx.media3.session.MediaController;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Standalone BBOS6 Music main menu.
 *
 * Mirrors the native ListView + BaseAdapter, visible-row selection styling,
 * and one-item trackpad navigation used by BB UI's Quick Settings panel,
 * without importing or depending on BB UI, Vector or SystemUI.
 *
 * Local music is read only from user-selected SAF folder trees.
 * Symfonium is never required or started.
 * The UI uses the same recycled ListView row on every browse page.
 */
public final class MusicActivity extends Activity {
    private static final String TAG = "BBMusic";
    private static final int ADD_FOLDER_REQUEST = 6102;
    private static final int AUTHORIZE_DELETE_FOLDER_REQUEST = 6103;
    private static final int IMPORT_PLAYLIST_REQUEST = 6104;
    private static final int EXPORT_PLAYLIST_REQUEST = 6105;
    private static final String MUSIC_SETTINGS_PREFS = "bbmusic_settings";
    private static final String AUTO_REFRESH_PREF = "auto_refresh_on_launch";
    private static final int ROW_MENU = 0;
    private static final int ROW_SONG = 1;
    private static final int ROW_GROUP = 2;
    private static final int ROW_MESSAGE = 3;
    private static final int ROW_SETTING = 4;
    private static final int ROW_FOLDER = 5;
    private static final int ROW_NOW_PLAYING = 6;
    private static final int ROW_SETTINGS_HEADER = 7;
    private static final int ROW_BROWSER_DIRECTORY = 8;
    private static final int ROW_BROWSER_FILE = 9;
    private static final int ROW_BROWSER_UNAVAILABLE = 10;
    private static final int ROW_BROWSER_ROOT = 11;
    private static final int ROW_PLAYLIST = 12;
    private static final int ROW_PLAYLIST_ACTION = 13;
    private static final int ROW_PLAYLIST_MISSING = 14;
    // Existing menu indices 0-6 are reserved for their original actions.
    private static final int MENU_FOLDERS = 7;
    private static final String[] MENU = {
            "All Songs",
            "Artists",
            "Albums",
            "Genres",
            "Playlists",
            "Shuffle Songs",
            "Settings",
            "Folders"
    };

    private MusicListView list;
    private FrameLayout listViewport;
    private Bbos6EmptyStateView emptyState;
    private LinearLayout libraryShell;
    private MusicAlbumHeaderView albumHeader;
    private EditText searchInput;
    private MusicSearchBarView searchBar;
    private TextView noSearchResults;
    private Page rootPage;
    private Page settingsPage;
    private Page musicFoldersPage;
    private Page playlistsPage;
    private MusicPlaylists playlists;
    private MusicPlaylistTransfer playlistTransfer;
    private int playlistCount;
    private long exportingPlaylistId = -1L;
    private List<MenuRow> visibleRows;
    private boolean searchOpen;
    private int searchOriginalPosition;
    private int searchOriginalTop;
    private TrackpadPressHandler trackpadPress;
    private LocalMusicSource localSource;
    private MusicFolderBrowser folderBrowser;
    private MusicTrackPropertiesReader trackPropertiesReader;
    private MusicTrackDeletion trackDeletion;
    private DeleteRequest pendingDelete;
    private boolean deletingTracks;
    private int deleteConfirmationSerial;
    private Dialog propertiesDialog;
    private Dialog contextMenuDialog;
    private int propertiesRequestSerial;
    private Map<String, List<Uri>> albumArtworkSources = Collections.emptyMap();
    private MusicPlaybackClient playback;
    private FrameLayout safeViewport;
    private MusicNowPlayingView nowPlaying;
    private boolean nowPlayingVisible;
    private int savedListPosition;
    private int savedListTop;
    private boolean pendingShuffleStart;
    private List<LocalMusicSource.Song> localSongs;
    private final int[] localCategoryCounts = new int[4];
    private boolean loading;
    private boolean startupAutoRefreshPending = true;
    private int pendingMenu = -1;
    private final ArrayDeque<Page> backStack = new ArrayDeque<>();
    private Page currentPage;
    private OnBackInvokedCallback nestedBackCallback;
    private static final class MenuRow {
        final String title;
        final int kind;
        final int menuIndex;
        final int groupCategory;
        final List<LocalMusicSource.Song> songs;
        final LocalMusicSource.Song song;
        final Uri folderTree;
        final String folderLocation;
        final long playlistId;
        final long playlistEntryId;
        final String playlistAction;

        MenuRow(String title, int kind, int menuIndex, int groupCategory,
                List<LocalMusicSource.Song> songs, LocalMusicSource.Song song,
                Uri folderTree, String folderLocation) {
            this(title, kind, menuIndex, groupCategory, songs, song,
                    folderTree, folderLocation, -1L, -1L, null);
        }

        MenuRow(String title, int kind, int menuIndex, int groupCategory,
                List<LocalMusicSource.Song> songs, LocalMusicSource.Song song,
                Uri folderTree, String folderLocation,
                long playlistId, long playlistEntryId, String playlistAction) {
            this.title = title;
            this.kind = kind;
            this.menuIndex = menuIndex;
            this.groupCategory = groupCategory;
            this.songs = songs;
            this.song = song;
            this.folderTree = folderTree;
            this.folderLocation = folderLocation;
            this.playlistId = playlistId;
            this.playlistEntryId = playlistEntryId;
            this.playlistAction = playlistAction;
        }

        static MenuRow root(String name, int index) {
            return new MenuRow(name, ROW_MENU, index, -1, null, null, null, null);
        }

        static MenuRow nowPlaying() {
            return new MenuRow("Now Playing...", ROW_NOW_PLAYING, -1, -1,
                    null, null, null, null);
        }

        static MenuRow group(String name, int category,
                List<LocalMusicSource.Song> songs) {
            return new MenuRow(name, ROW_GROUP, -1, category, songs, null, null, null);
        }

        static MenuRow song(LocalMusicSource.Song song) {
            return new MenuRow(song.title, ROW_SONG, -1, -1, null, song, null, null);
        }

        static MenuRow playlist(MusicLibraryDatabase.PlaylistSummary summary) {
            return new MenuRow(summary.name, ROW_PLAYLIST, -1, summary.songCount,
                    null, null, null, null, summary.id, -1L, null);
        }

        static MenuRow playlistAction(String title, String action, long playlistId) {
            return new MenuRow(title, ROW_PLAYLIST_ACTION, -1, -1,
                    null, null, null, null, playlistId, -1L, action);
        }

        static MenuRow playlistSong(LocalMusicSource.Song song,
                long playlistId, long entryId) {
            return new MenuRow(song.title, ROW_SONG, -1, -1,
                    null, song, null, null, playlistId, entryId, null);
        }

        static MenuRow playlistMissing(String title, long playlistId, long entryId) {
            return new MenuRow(title, ROW_PLAYLIST_MISSING, -1, -1,
                    null, null, null, null, playlistId, entryId, null);
        }

        static MenuRow message(String message) {
            return new MenuRow(message, ROW_MESSAGE, -1, -1, null, null, null, null);
        }

        static MenuRow setting(String title) {
            return new MenuRow(title, ROW_SETTING, -1, -1, null, null, null, null);
        }

        static MenuRow settingsHeader(String title) {
            return new MenuRow(title, ROW_SETTINGS_HEADER, -1, -1,
                    null, null, null, null);
        }

        static MenuRow folder(String name, String location, Uri tree) {
            return new MenuRow(name, ROW_FOLDER, -1, -1,
                    null, null, tree, location);
        }

        static MenuRow browserRoot(String name, Uri tree, String documentId) {
            return new MenuRow(name, ROW_BROWSER_ROOT, -1, -1,
                    null, null, tree, documentId);
        }

        static MenuRow browserDirectory(String name, Uri tree, String documentId) {
            return new MenuRow(name, ROW_BROWSER_DIRECTORY, -1, -1,
                    null, null, tree, documentId);
        }

        static MenuRow browserUnavailable(String name) {
            return new MenuRow(name, ROW_BROWSER_UNAVAILABLE, -1, -1,
                    null, null, null, null);
        }

        static MenuRow browserFile(String filename, LocalMusicSource.Song song) {
            return new MenuRow(filename, song == null ? ROW_BROWSER_FILE : ROW_SONG,
                    -1, -1, null, song, null, null);
        }
    }

    /** Snapshot of exactly the documents in the user-confirmed operation. */
    private static final class DeleteRequest {
        final Page page;
        final List<LocalMusicSource.Song> songs;
        final String description;

        DeleteRequest(Page page, List<LocalMusicSource.Song> songs, String description) {
            this.page = page;
            this.songs = Collections.unmodifiableList(new ArrayList<>(songs));
            this.description = description;
        }
    }

    private static final class Page {
        String name;
        final List<MenuRow> rows;
        // Only a page opened directly from an album group has this header.
        final MenuRow albumGroup;
        // Set only on filesystem pages; entries are cached per visited page
        // so a library refresh can rebind song availability without requerying.
        Uri browserTree;
        List<MusicFolderBrowser.Entry> browserEntries;
        boolean playlistDirectory;
        long playlistId = -1L;
        long playlistPickerId = -1L;
        String emptyTitle;
        String emptyMessage;
        int emptyArtworkRes;
        int emptyAfterRows;
        void setEmptyState(String title, String message, int afterRows) {
            emptyTitle = title;
            emptyMessage = message;
            emptyAfterRows = afterRows;
            // Original BBOS6 artwork is supplied only after positive identification.
            emptyArtworkRes = 0;
        }
        int selected;
        int selectedTop;

        Page(String name, List<MenuRow> rows) {
            this(name, rows, null);
        }

        Page(String name, List<MenuRow> rows, MenuRow albumGroup) {
            this.name = name;
            this.rows = rows;
            this.albumGroup = albumGroup;
        }
    }

    private static final class Group {
        final String label;
        final ArrayList<LocalMusicSource.Song> songs = new ArrayList<>();
        Group(String label) { this.label = label; }
    }

    // Artist/genre representative: alphabetical first album title, with
    // deterministic tie-breaking for identical album names.
    private static final Comparator<LocalMusicSource.Song> FIRST_ALBUM =
            Comparator.comparing((LocalMusicSource.Song song) -> song.album,
                    String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(song -> song.album)
                    .thenComparing(song -> song.artist, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(song -> song.artist)
                    .thenComparing(song -> song.uri.toString());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        trackpadPress = new TrackpadPressHandler(this::activateSelected,
                this::longPressSelected, this::supportsSelectedLongPress);
        localSource = new LocalMusicSource(this);
        folderBrowser = new MusicFolderBrowser(this);
        trackPropertiesReader = new MusicTrackPropertiesReader(this);
        trackDeletion = new MusicTrackDeletion(this);
        playlists = new MusicPlaylists(this);
        playlistTransfer = new MusicPlaylistTransfer(this);
        refreshPlaylistCount();
        playback = new MusicPlaybackClient(this, new MusicPlaybackClient.Listener() {
            @Override public void onPlaybackChanged() {
                updateNowPlayingHomeRow();
                if (nowPlayingVisible && nowPlaying != null) nowPlaying.render();
            }

            @Override public void onPlaybackError(String message) {
                Toast.makeText(MusicActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        ArrayList<MenuRow> rootRows = new ArrayList<>();
        for (int index = 0; index < MENU.length - 1; index++) {
            rootRows.add(MenuRow.root(MENU[index], index));
            // Preserve existing menu indices and place Folders with library
            // categories instead of changing Shuffle/Settings routing.
            if (index == 4) rootRows.add(MenuRow.root(MENU[MENU_FOLDERS], MENU_FOLDERS));
        }
        currentPage = new Page("Music", rootRows);
        rootPage = currentPage;
        visibleRows = currentPage.rows;

        list = new MusicListView(this, new MusicListView.DataSource() {
            @Override public int size() {
                return visibleRows.size();
            }

            @Override public MusicListView.RowData rowAt(int position) {
                return rowPresentation(visibleRows.get(position));
            }

            @Override public void activate(int position) {
                MusicActivity.this.activate(position);
            }

            @Override public void longPress(int position) {
                longPressRow(position, false);
            }

            @Override public boolean supportsLongPress(int position) {
                return supportsRowLongPress(position);
            }
        });

        // Reserve the standard system-window insets at the outer container.
        // The ListView itself retains its unmodified scroll/focus behavior.
        // On edge-to-edge Android versions, this keeps rows and their selected
        // backgrounds beneath the visible status bar without fixed offsets.
        safeViewport = new FrameLayout(this);
        safeViewport.setBackgroundColor(list.canvasColor());
        safeViewport.setFitsSystemWindows(true);
        safeViewport.setClipChildren(true);
        safeViewport.setClipToPadding(true);
        // Persistent shell: showing a search bar never rebuilds the list,
        // its adapter, artwork cache, or focus/trackpad machinery.
        libraryShell = new LinearLayout(this);
        libraryShell.setOrientation(LinearLayout.VERTICAL);
        libraryShell.setBackgroundColor(Color.BLACK);

        // Album detail: directly below the status bar and flush to the
        // left edge, without adding a header adapter row or disturbing
        // native song selection indices.
        albumHeader = new MusicAlbumHeaderView(this, list);
        albumHeader.setVisibility(View.GONE);
        libraryShell.addView(albumHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(MusicAlbumHeaderView.heightDp())));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(16);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchInput.setShowSoftInputOnFocus(false);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value,
                    int start, int count, int after) { }

            @Override public void onTextChanged(CharSequence value,
                    int start, int before, int count) { }

            @Override public void afterTextChanged(Editable value) {
                if (searchOpen) updateSearchResults();
            }
        });
        // BBOS6 visual chrome around the same native search input. The
        // EditText, its TextWatcher and all keyboard logic remain intact.
        searchBar = new MusicSearchBarView(this, searchInput);
        searchBar.setVisibility(View.GONE);
        libraryShell.addView(searchBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(MusicSearchBarView.heightDp())));
        // Empty search feedback is not a ListView row: it never receives
        // the row selector, participates in navigation, or adds a divider.
        noSearchResults = new TextView(this);
        noSearchResults.setText("No search results found");
        noSearchResults.setTextSize(15);
        noSearchResults.setTextColor(Color.WHITE);
        noSearchResults.setGravity(Gravity.CENTER);
        noSearchResults.setPadding(dp(16), dp(20), dp(16), dp(20));
        noSearchResults.setFocusable(false);
        noSearchResults.setVisibility(View.GONE);
        libraryShell.addView(noSearchResults, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // Keep native list ownership and scrolling unchanged. The shared
        // empty-state view is an independent, non-focusable sibling, never
        // a ListView adapter row or DPAD/trackpad selection.
        listViewport = new FrameLayout(this);
        listViewport.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        emptyState = new Bbos6EmptyStateView(this);
        emptyState.setVisibility(View.GONE);
        listViewport.addView(emptyState, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        libraryShell.addView(listViewport, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        safeViewport.addView(libraryShell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(safeViewport);

        // Keep native list navigation in charge: no custom scroll amounts.
        list.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            list.requestFocus();
            list.setSelectionFromTop(0, 0);
            list.refreshSelection();
        });

        // Derive home-menu counts from precisely the selected folder trees.
        // No folder grants means zero music queries and zero children.
        if (MusicFolders.selectedTrees(this).isEmpty()) {
            localSongs = Collections.emptyList();
            startupAutoRefreshPending = false;
        } else {
            startLocalScan(false);
        }
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private MusicListView.RowData rowPresentation(MenuRow row) {
        // The BBOS6 album detail page has its cover in the album header.
        // Its tracks show the tagged track number instead of repeated art.
        boolean albumTrack = currentPage.albumGroup != null
                && row.kind == ROW_SONG;
        boolean hasArtwork = !albumTrack
                && (row.kind == ROW_GROUP || row.kind == ROW_SONG);
        String trackNumber = albumTrack
                ? (row.song.trackNumber > 0
                ? Integer.toString(row.song.trackNumber) : "--") : null;
        String albumKey = hasArtwork ? albumKeyForRow(row) : null;
        List<Uri> candidates = albumKey == null
                ? null : albumArtworkSources.get(albumKey);
        boolean albumFallback = row.kind == ROW_GROUP && row.groupCategory == 2;
        // BBOS6 Songs: the artist occupies the second line and the tagged
        // duration sits at the far right of the title line. Existing menu
        // counts and folder locations keep their original subtitle policies.
        String subtitle = row.kind == ROW_SONG ? row.song.artist
                : row.kind == ROW_PLAYLIST
                        ? countLabel(row.groupCategory, "Song", "Songs")
                : row.kind == ROW_PLAYLIST_MISSING ? "Track unavailable"
                : row.kind == ROW_FOLDER ? row.folderLocation
                : row.kind == ROW_BROWSER_ROOT
                        ? MusicFolders.displayLocation(row.folderLocation)
                : row.kind == ROW_BROWSER_DIRECTORY ? "Folder"
                : row.kind == ROW_BROWSER_UNAVAILABLE ? "Access unavailable"
                : row.kind == ROW_BROWSER_FILE ? "Not in music library"
                : row.kind == ROW_SETTING && "Auto refresh on launch".equals(row.title)
                        ? (autoRefreshEnabled() ? "On" : "Off")
                        : childCountLabel(row);
        String duration = row.kind == ROW_SONG && row.song.durationMs > 0L
                ? MediaSeekPolicy.formatTime(row.song.durationMs) : null;
        return new MusicListView.RowData(
                row.title, subtitle,
                row.kind != ROW_MESSAGE && row.kind != ROW_SETTINGS_HEADER
                        && row.kind != ROW_BROWSER_FILE
                        && row.kind != ROW_BROWSER_UNAVAILABLE,
                hasArtwork, albumFallback,
                menuIconForRow(row), trackNumber, duration,
                albumKey == null ? null : "album:" + albumKey,
                candidates, row.kind == ROW_FOLDER,
                row.kind == ROW_SETTINGS_HEADER);
    }

    /**
     * Original BBOS6 menu icons identified from supplied screenshots.
     * The source PNGs remain in /assets/bbos6/icons/ and are reused as-is.
     * Settings uses the verified BBOS6 Options wrench icon (3412.png).
     */
    private static int menuIconForRow(MenuRow row) {
        if (row.kind == ROW_NOW_PLAYING) {
            return R.drawable.bbos6_menu_now_playing; // 341.png
        }
        // All configured folder roots and recursively browsed directories
        // share the same verified original BBOS6 112.png icon, including
        // unavailable roots and removable Settings > Music Folders entries.
        if (row.kind == ROW_FOLDER || row.kind == ROW_BROWSER_ROOT
                || row.kind == ROW_BROWSER_DIRECTORY
                || row.kind == ROW_BROWSER_UNAVAILABLE) {
            return R.drawable.bbos6_menu_folders; // 112.png
        }
        if (row.kind != ROW_MENU) return 0;
        switch (row.menuIndex) {
            case 0: return R.drawable.bbos6_menu_all_songs;      // 350.png
            case 1: return R.drawable.bbos6_menu_artists;        // 345.png
            case 2: return R.drawable.bbos6_menu_albums;         // 351.png
            case 3: return R.drawable.bbos6_menu_genres;         // 348.png
            case 4: return R.drawable.bbos6_menu_playlists;      // 337.png
            case 5: return R.drawable.bbos6_menu_shuffle_songs; // 339.png
            case 6: return R.drawable.bbos6_menu_settings;      // 3412.png
            case MENU_FOLDERS: return R.drawable.bbos6_menu_folders; // 112.png
            default: return 0;
        }
    }

    private String albumKeyForRow(MenuRow row) {
        if (row.kind == ROW_SONG) return artworkAlbumKey(row.song);
        if (row.kind != ROW_GROUP || row.songs == null || row.songs.isEmpty()) {
            return null;
        }
        if (row.groupCategory == 2) {
            // The album entry always displays its own album art.
            return artworkAlbumKey(row.songs.get(0));
        }
        // Artists and genres take the artwork of their first alphabetical
        // album. Missing art on that album never selects a later album.
        return artworkAlbumKey(Collections.min(row.songs, FIRST_ALBUM));
    }

    private static String artworkAlbumKey(LocalMusicSource.Song song) {
        // No album tag means no verified album association. Such tracks must
        // not borrow a different untagged song's embedded image.
        if (!MusicAlbumTag.present(song.album)) {
            return "untagged:" + song.uri;
        }
        return groupKey(song, 2);
    }

    /**
     * Index artwork candidates from the selected-folder scan only.
     * All songs in an album share one cover. If its first track has no
     * embedded image, the loader tries the remaining tracks OF THAT ALBUM.
     */
    private void updateAlbumArtworkSources() {
        if (localSongs == null || localSongs.isEmpty()) {
            albumArtworkSources = Collections.emptyMap();
            return;
        }
        Map<String, ArrayList<LocalMusicSource.Song>> byAlbum = new LinkedHashMap<>();
        for (LocalMusicSource.Song song : localSongs) {
            String albumKey = artworkAlbumKey(song);
            byAlbum.computeIfAbsent(albumKey, unused -> new ArrayList<>()).add(song);
        }
        Map<String, List<Uri>> indexed = new LinkedHashMap<>();
        Comparator<LocalMusicSource.Song> trackOrder =
                Comparator.comparingInt((LocalMusicSource.Song song) ->
                        song.trackNumber > 0 ? song.trackNumber : Integer.MAX_VALUE)
                        .thenComparing(song -> song.title, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(song -> song.uri.toString());
        for (Map.Entry<String, ArrayList<LocalMusicSource.Song>> album
                : byAlbum.entrySet()) {
            album.getValue().sort(trackOrder);
            ArrayList<Uri> uris = new ArrayList<>();
            for (LocalMusicSource.Song song : album.getValue()) {
                uris.add(song.uri);
            }
            indexed.put(album.getKey(), Collections.unmodifiableList(uris));
        }
        albumArtworkSources = indexed;
    }

    private String childCountLabel(MenuRow row) {
        if (row.kind == ROW_GROUP) {
            return countLabel(row.songs.size(), "Song", "Songs");
        }
        if (row.kind != ROW_MENU) return null;
        if (row.menuIndex < 4) {
            if (localSongs == null) return loading ? "Loading..." : "Unavailable";
            int count = row.menuIndex == 0
                    ? localSongs.size() : localCategoryCounts[row.menuIndex];
            return row.menuIndex == 0
                    ? countLabel(count, "Song", "Songs")
                    : countLabel(count, "Item", "Items");
        }
        if (row.menuIndex == 4) {
            return countLabel(playlistCount, "Item", "Items");
        }
        if (row.menuIndex == MENU_FOLDERS) return null;
        // Shuffle Songs and Settings have no child-count subtitle.
        return null;
    }

    private static String countLabel(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private void updateLocalCategoryCounts() {
        if (localSongs == null) return;
        localCategoryCounts[0] = localSongs.size();
        for (int category = 1; category <= 3; category++) {
            Set<String> keys = new HashSet<>();
            for (LocalMusicSource.Song song : localSongs) {
                if (category == 2 && !MusicAlbumTag.present(song.album)) continue;
                keys.add(groupKey(song, category));
            }
            localCategoryCounts[category] = keys.size();
        }
        // No notifyDataSetChanged(): a count update must not reset focus
        // or the scroll position of the user's selected row.
        list.refreshVisibleRows();
    }

    private void activateSelected() {
        if (nowPlayingVisible) {
            if (nowPlaying != null) nowPlaying.activate();
        } else {
            activate(list.getSelectedItemPosition());
        }
    }

    /** Only actual music/library entries expose long-press gestures. */
    private boolean supportsRowLongPress(int position) {
        if (nowPlayingVisible || position < 0 || position >= visibleRows.size()) {
            return false;
        }
        MenuRow row = visibleRows.get(position);
        if (!rowPresentation(row).enabled) return false;
        if (currentPage.playlistPickerId >= 0) return false;
        switch (row.kind) {
            case ROW_SONG:
                return row.song != null;
            case ROW_GROUP:
                return row.groupCategory >= 1 && row.groupCategory <= 3;
            case ROW_PLAYLIST:
            case ROW_PLAYLIST_MISSING:
                return true;
            case ROW_FOLDER:
            case ROW_BROWSER_ROOT:
            case ROW_BROWSER_DIRECTORY:
                return true;
            default:
                // Home, Settings, unavailable files and all other controls.
                return false;
        }
    }

    private boolean supportsSelectedLongPress() {
        return supportsRowLongPress(list.getSelectedItemPosition());
    }

    /** Touch and trackpad share the same eligible-row action channel. */
    private void longPressSelected() {
        longPressRow(list.getSelectedItemPosition(), true);
    }

    private void longPressRow(int position, boolean hardware) {
        if (!supportsRowLongPress(position)) return;
        if (hardware) list.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        MenuRow row = visibleRows.get(position);
        // Folders have no delete action. Songs, artists, albums and genres
        // share the same BBOS6 menu renderer and confirmation pipeline.
        if (row.kind == ROW_SONG && row.song == null) return;
        if (row.kind != ROW_SONG && row.kind != ROW_GROUP
                && row.kind != ROW_PLAYLIST
                && row.kind != ROW_PLAYLIST_MISSING) return;
        showMusicContextMenu(row);
    }

    /**
     * Track actions live here, never in the app-neutral BBOS6 menu widget.
     * Keep the selected MenuRow so Properties resolves the touched track,
     * not whichever row is selected after the dialog has been dismissed.
     */
    private void showMusicContextMenu(MenuRow row) {
        final Page page = currentPage;
        if (contextMenuDialog != null) contextMenuDialog.dismiss();
        // Dismiss any pending metadata result from a previous long press.
        propertiesRequestSerial++;
        Bbos6ContextMenu.Builder menu = Bbos6ContextMenu.builder();
        if (row.kind == ROW_PLAYLIST) {
            menu.addGroup(
                    Bbos6ContextMenuItem.resource("playlist-rename",
                            "Rename playlist", android.R.drawable.ic_menu_edit),
                    Bbos6ContextMenuItem.resource("playlist-delete",
                            "Delete playlist", android.R.drawable.ic_menu_delete));
            menu.initiallySelected("playlist-rename");
        } else if (row.kind == ROW_PLAYLIST_MISSING) {
            menu.addGroup(
                    Bbos6ContextMenuItem.resource("move-up",
                            "Move up", android.R.drawable.arrow_up_float),
                    Bbos6ContextMenuItem.resource("move-down",
                            "Move down", android.R.drawable.arrow_down_float),
                    Bbos6ContextMenuItem.resource("remove-from-playlist",
                            "Remove from playlist",
                            android.R.drawable.ic_menu_close_clear_cancel));
            menu.initiallySelected("remove-from-playlist");
        } else if (row.kind == ROW_SONG) {
            menu.addGroup(
                    Bbos6ContextMenuItem.resource("properties",
                            "Properties", android.R.drawable.ic_menu_info_details),
                    Bbos6ContextMenuItem.resource("add-to-playlist",
                            "Add to playlist", android.R.drawable.ic_menu_add));
            if (row.playlistEntryId >= 0) {
                menu.addGroup(
                        Bbos6ContextMenuItem.resource("move-up",
                                "Move up", android.R.drawable.arrow_up_float),
                        Bbos6ContextMenuItem.resource("move-down",
                                "Move down", android.R.drawable.arrow_down_float),
                        Bbos6ContextMenuItem.resource("remove-from-playlist",
                                "Remove from playlist", android.R.drawable.ic_menu_close_clear_cancel));
            }
            menu.addGroup(Bbos6ContextMenuItem.resource(
                    "delete", "Delete audio file", android.R.drawable.ic_menu_delete));
            menu.initiallySelected("properties");
        } else {
            menu.addGroup(
                    Bbos6ContextMenuItem.resource("add-to-playlist",
                            "Add to playlist", android.R.drawable.ic_menu_add),
                    Bbos6ContextMenuItem.resource("delete",
                            "Delete audio files", android.R.drawable.ic_menu_delete));
            menu.initiallySelected("add-to-playlist");
        }
        contextMenuDialog = menu.onAction(item -> {
                    if (isFinishing() || isDestroyed() || nowPlayingVisible
                            || currentPage != page || !visibleRows.contains(row)) {
                        return;
                    }
                    switch (item.id()) {
                        case "properties":
                            if (row.kind == ROW_SONG) showTrackProperties(row);
                            break;
                        case "add-to-playlist":
                            choosePlaylist(row.kind == ROW_SONG
                                    ? Collections.singletonList(row.song) : row.songs);
                            break;
                        case "move-up":
                            movePlaylistEntry(row, -1);
                            break;
                        case "move-down":
                            movePlaylistEntry(row, 1);
                            break;
                        case "remove-from-playlist":
                            removePlaylistEntry(row);
                            break;
                        case "playlist-rename":
                            renamePlaylist(row.playlistId);
                            break;
                        case "playlist-delete":
                            confirmPlaylistDelete(row.playlistId, row.title);
                            break;
                        case "delete":
                            if (row.kind == ROW_SONG || row.kind == ROW_GROUP) {
                                confirmDelete(row);
                            }
                            break;
                        default:
                            break;
                    }
                })
                .show(this);
        Dialog shownMenu = contextMenuDialog;
        shownMenu.setOnDismissListener(dialog -> {
            if (contextMenuDialog == shownMenu) contextMenuDialog = null;
        });
    }

    /**
     * Confirm the permanent removal of selected audio files, never folders.
     * Group membership is taken from the existing indexed Music browser.
     */
    private void confirmDelete(MenuRow row) {
        if (deletingTracks || pendingDelete != null) {
            Toast.makeText(this, "A deletion is already in progress",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (loading || localSongs == null) {
            Toast.makeText(this, "Finish loading the library first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        HashMap<String, LocalMusicSource.Song> authorizedSongs = new HashMap<>();
        for (LocalMusicSource.Song song : localSongs) {
            authorizedSongs.put(song.uri.toString(), song);
        }
        LinkedHashMap<String, LocalMusicSource.Song> selected = new LinkedHashMap<>();
        if (row.kind == ROW_SONG && row.song != null) {
            LocalMusicSource.Song song = authorizedSongs.get(row.song.uri.toString());
            if (song != null) selected.put(song.uri.toString(), song);
        } else if (row.kind == ROW_GROUP && row.songs != null
                && row.groupCategory >= 1 && row.groupCategory <= 3) {
            for (LocalMusicSource.Song member : row.songs) {
                LocalMusicSource.Song song =
                        authorizedSongs.get(member.uri.toString());
                if (song != null) selected.put(song.uri.toString(), song);
            }
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, "No indexed tracks to delete",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String description;
        if (row.kind == ROW_SONG) {
            description = "track \"" + row.title + "\"";
        } else {
            String type = row.groupCategory == 1 ? "artist"
                    : row.groupCategory == 2 ? "album" : "genre";
            description = type + " \"" + row.title + "\"";
        }
        DeleteRequest request = new DeleteRequest(currentPage,
                new ArrayList<>(selected.values()), description);
        final int confirmation = ++deleteConfirmationSerial;
        if (row.kind == ROW_SONG) {
            // The indexed title may differ from the filename. Read the
            // provider's actual COLUMN_DISPLAY_NAME on the worker thread.
            trackDeletion.loadDisplayName(request.songs.get(0), filename -> {
                if (confirmation != deleteConfirmationSerial
                        || isFinishing() || isDestroyed() || nowPlayingVisible
                        || currentPage != request.page
                        || !visibleRows.contains(row)) return;
                showDeleteConfirmation(request,
                        "Are you sure you want to delete "
                                + (filename == null ? "this track" : filename)
                                + " permanently from storage?");
            });
        } else {
            int count = request.songs.size();
            showDeleteConfirmation(request,
                    "Are you sure you want to delete all " + count
                            + (count == 1 ? " song" : " songs")
                            + " from " + description
                            + " permanently from storage?");
        }
    }

    private void showDeleteConfirmation(DeleteRequest request, String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete",
                        (dialog, which) -> authorizeAndDelete(request))
                .show();
    }

    /**
     * Existing user-selected trees were deliberately granted read-only access.
     * Android's native picker is used to upgrade *the same* tree only when
     * deletion requires it. No broad file or device storage permissions.
     */
    private void authorizeAndDelete(DeleteRequest request) {
        if (isFinishing() || isDestroyed() || deletingTracks
                || nowPlayingVisible || currentPage != request.page) return;
        List<Uri> authorized = MusicFolders.selectedTrees(this);
        for (LocalMusicSource.Song song : request.songs) {
            if (!authorized.contains(song.sourceTree)) {
                Toast.makeText(this, "Music folder access was removed",
                        Toast.LENGTH_LONG).show();
                pendingDelete = null;
                return;
            }
            if (!MusicFolders.hasWriteGrant(this, song.sourceTree)) {
                pendingDelete = request;
                launchDeleteFolderPicker(song.sourceTree);
                return;
            }
        }
        pendingDelete = null;
        beginDelete(request);
    }

    private void launchDeleteFolderPicker(Uri tree) {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        picker.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        try {
            picker.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    DocumentsContract.buildDocumentUriUsingTree(
                            tree, DocumentsContract.getTreeDocumentId(tree)));
            Toast.makeText(this, "Select the same music folder to allow deletion",
                    Toast.LENGTH_LONG).show();
            startActivityForResult(picker, AUTHORIZE_DELETE_FOLDER_REQUEST);
        } catch (RuntimeException exception) {
            pendingDelete = null;
            Log.w(TAG, "Cannot request Android write access", exception);
            Toast.makeText(this, "Android folder picker unavailable",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void onDeleteFolderAuthorization(int resultCode, Intent data) {
        DeleteRequest request = pendingDelete;
        pendingDelete = null;
        if (request == null || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri chosen = data.getData();
        Uri needed = null;
        for (LocalMusicSource.Song song : request.songs) {
            if (!MusicFolders.hasWriteGrant(this, song.sourceTree)) {
                needed = song.sourceTree;
                break;
            }
        }
        // Do not add, scan, or authorize a different folder on this path.
        if (needed == null || !needed.equals(chosen)) {
            Toast.makeText(this, "Select the original music folder",
                    Toast.LENGTH_LONG).show();
            return;
        }
        int granted = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((granted & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {
            Toast.makeText(this, "Write access was not granted",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(chosen, granted);
            if (!MusicFolders.hasWriteGrant(this, chosen)) {
                throw new SecurityException("Missing persisted write grant");
            }
            authorizeAndDelete(request);
        } catch (SecurityException | IllegalArgumentException exception) {
            Log.w(TAG, "Unable to retain selected folder write access", exception);
            Toast.makeText(this, "Unable to authorize deletion",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void beginDelete(DeleteRequest request) {
        if (deletingTracks) return;
        deletingTracks = true;
        // Existing scans cannot publish a stale snapshot while documents
        // are being removed. The worker also invalidates Room's scan marker.
        localSource.invalidate();
        loading = false;
        Toast.makeText(this, "Deleting " + request.songs.size() + " songs...",
                Toast.LENGTH_SHORT).show();
        trackDeletion.delete(request.songs, result -> {
            deletingTracks = false;
            if (result.deletedCount() > 0) {
                playback.removeDeletedSongs(result.deletedIds);
                reconcileDeletedSongs(result.deletedIds);
                list.clearArtworkCache();
                updateAlbumArtworkSources();
                updateLocalCategoryCounts();
                refreshBrowsedSongs();
                refreshAllPlaylistPages();
                // Native SAF enumeration reconciles provider-side changes,
                // and preserves all failed deletions in the catalog.
                startLocalScan(false, true);
            }
            String message = "Deleted " + result.deletedCount()
                    + (result.deletedCount() == 1 ? " song" : " songs");
            if (result.failed > 0) {
                message += "; " + result.failed + " could not be deleted";
            }
            Toast.makeText(this, message,
                    result.failed > 0 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        });
    }

    /** Reconcile the in-memory pages immediately, before the SAF rescan. */
    private void reconcileDeletedSongs(Set<String> deletedIds) {
        if (localSongs != null) {
            ArrayList<LocalMusicSource.Song> surviving = new ArrayList<>();
            for (LocalMusicSource.Song song : localSongs) {
                if (!deletedIds.contains(song.uri.toString())) surviving.add(song);
            }
            localSongs = surviving;
        }
        HashSet<Page> pages = new HashSet<>(backStack);
        pages.add(currentPage);
        for (Page page : pages) {
            if (page == rootPage || page == settingsPage
                    || page == musicFoldersPage) continue;
            if (page.browserEntries != null) {
                ArrayList<MusicFolderBrowser.Entry> entries = new ArrayList<>();
                for (MusicFolderBrowser.Entry entry : page.browserEntries) {
                    if (!deletedIds.contains(entry.uri.toString())) entries.add(entry);
                }
                page.browserEntries = entries;
                page.rows.clear();
                page.rows.addAll(browserRows(page));
            } else {
                page.rows.removeIf(row -> {
                    if (row.kind == ROW_SONG && row.song != null) {
                        return deletedIds.contains(row.song.uri.toString());
                    }
                    if (row.kind == ROW_GROUP && row.songs != null) {
                        row.songs.removeIf(song ->
                                deletedIds.contains(song.uri.toString()));
                        return row.songs.isEmpty();
                    }
                    return false;
                });
                if (page.rows.isEmpty()) {
                    page.rows.add(MenuRow.message("No local music found"));
                }
            }
        }
        if (!nowPlayingVisible) {
            bindAlbumHeader(currentPage);
            if (searchOpen) {
                updateSearchResults();
            } else {
                visibleRows = currentPage.rows;
                int selected = list.getSelectedItemPosition();
                View selectedView = list.getSelectedView();
                int top = selectedView == null ? 0 : selectedView.getTop();
                list.notifyRowsChanged();
                list.post(() -> {
                    if (nowPlayingVisible || searchOpen || isFinishing()
                            || isDestroyed()) return;
                    list.requestFocus();
                    list.setSelectionFromTop(Math.max(0,
                            Math.min(selected, visibleRows.size() - 1)), top);
                    list.refreshSelection();
                });
            }
        }
    }

    private void showTrackProperties(MenuRow row) {
        final int request = ++propertiesRequestSerial;
        final Page page = currentPage;
        trackPropertiesReader.load(row.song, items -> {
            // A slow SAF query must not display the previous track's popup
            // after Back, new navigation, searching or a newer long press.
            if (request != propertiesRequestSerial || isFinishing() || isDestroyed()
                    || nowPlayingVisible || page != currentPage
                    || !visibleRows.contains(row)) return;
            if (items.isEmpty()) {
                Toast.makeText(this, "Track properties unavailable",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (propertiesDialog != null && propertiesDialog.isShowing()) {
                propertiesDialog.dismiss();
            }
            propertiesDialog = Bbos6PropertiesPopup.show(this, items);
        });
    }

    private void activate(int position) {
        if (position < 0 || position >= visibleRows.size()) return;
        MenuRow row = visibleRows.get(position);
        if (row.kind == ROW_NOW_PLAYING) {
            // A shortcut to the existing Media3 session, not a play command.
            // In particular, selecting it must never resume a paused track.
            if (playback.hasCurrentTrack()) openNowPlaying();
        } else if (row.kind == ROW_MENU) {
            if (row.menuIndex < 4) {
                openLocalCategory(row.menuIndex);
            } else if (row.menuIndex == 4) {
                openPlaylistDirectory();
            } else if (row.menuIndex == 5) {
                shuffleAllSongs();
            } else if (row.menuIndex == 6) {
                settingsPage = new Page("Settings", buildSettingsRows());
                navigateTo(settingsPage);
            } else if (row.menuIndex == MENU_FOLDERS) {
                openFolders();
            }
        } else if (row.kind == ROW_SETTING) {
            if ("Music Folders".equals(row.title)) {
                musicFoldersPage = new Page("Music Folders", buildMusicFolderRows());
                navigateTo(musicFoldersPage);
            } else if ("Refresh library".equals(row.title)) {
                startLocalScan(true, true);
            } else if ("Full rescan".equals(row.title)) {
                confirmFullRescan();
            } else if ("Auto refresh on launch".equals(row.title)) {
                boolean enabled = !autoRefreshEnabled();
                getSharedPreferences(MUSIC_SETTINGS_PREFS, MODE_PRIVATE).edit()
                        .putBoolean(AUTO_REFRESH_PREF, enabled).apply();
                list.refreshVisibleRows();
            } else if ("Add folders".equals(row.title)) {
                showFolderPicker();
            }
        } else if (row.kind == ROW_FOLDER) {
            confirmFolderRemoval(row);
        } else if (row.kind == ROW_BROWSER_DIRECTORY
                || row.kind == ROW_BROWSER_ROOT) {
            openBrowserDirectory(row);
        } else if (row.kind == ROW_GROUP) {
            navigateTo(pageForGroup(row));
        } else if (row.kind == ROW_PLAYLIST) {
            openPlaylist(row);
        } else if (row.kind == ROW_PLAYLIST_ACTION) {
            activatePlaylistAction(row);
        } else if (row.kind == ROW_PLAYLIST_MISSING) {
            playlistNotice("Track unavailable in selected music folders");
        } else if (row.kind == ROW_SONG) {
            if (currentPage.playlistPickerId >= 0L) {
                addSongsToPlaylist(currentPage.playlistPickerId,
                        Collections.singletonList(row.song));
                return;
            }
            // Play this track within its currently displayed folder-scoped
            // song list, preserving the order for native Previous/Next.
            ArrayList<LocalMusicSource.Song> queue = new ArrayList<>();
            int startIndex = -1;
            for (MenuRow item : visibleRows) {
                if (item.kind != ROW_SONG) continue;
                if (item == row) startIndex = queue.size();
                queue.add(item.song);
            }
            if (startIndex >= 0) {
                openNowPlaying();
                playback.playSongs(queue, startIndex, false);
            }
        }
    }


    /** Home playlist count uses the same Room tables as the Playlists page. */
    private void refreshPlaylistCount() {
        playlists.list((items, error) -> {
            if (error != null || items == null) return;
            playlistCount = items.size();
            if (list != null) list.refreshVisibleRows();
        });
    }

    private void refreshPlaylistDirectory() {
        final Page page = playlistsPage;
        if (page == null) return;
        playlists.list((items, error) -> {
            if (isFinishing() || isDestroyed() || page != playlistsPage) return;
            ArrayList<MenuRow> rows = new ArrayList<>();
            rows.add(MenuRow.playlistAction("Create playlist", "create", -1));
            rows.add(MenuRow.playlistAction("Import M3U8", "import", -1));
            page.setEmptyState(null, null, 0);
            if (error != null) {
                rows.add(MenuRow.message("Unable to load playlists"));
            } else if (items == null || items.isEmpty()) {
                page.setEmptyState("No playlists.",
                        "You can create playlists from music on your device "
                                + "by selecting Create playlist.", rows.size());
            } else {
                for (MusicLibraryDatabase.PlaylistSummary item : items) {
                    rows.add(MenuRow.playlist(item));
                }
                playlistCount = items.size();
                if (list != null) list.refreshVisibleRows();
            }
            page.rows.clear();
            page.rows.addAll(rows);
            if (currentPage == page) refreshBrowserPage(page);
        });
    }

    private void openPlaylistDirectory() {
        ArrayList<MenuRow> rows = new ArrayList<>();
        rows.add(MenuRow.playlistAction("Create playlist", "create", -1));
        rows.add(MenuRow.playlistAction("Import M3U8", "import", -1));
        rows.add(MenuRow.message("Loading playlists..."));
        playlistsPage = new Page("Playlists", rows);
        playlistsPage.playlistDirectory = true;
        navigateTo(playlistsPage);
        refreshPlaylistDirectory();
    }

    private void openPlaylist(MenuRow row) {
        Page page = new Page(row.title, new ArrayList<>());
        page.playlistId = row.playlistId;
        page.rows.add(MenuRow.message("Loading playlist..."));
        navigateTo(page);
        refreshPlaylistDetail(page);
    }

    private void refreshPlaylistDetail(Page page) {
        if (page == null || page.playlistId < 0) return;
        playlists.entries(page.playlistId, (items, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (currentPage != page && !backStack.contains(page)) return;
            ArrayList<MenuRow> rows = new ArrayList<>();
            long id = page.playlistId;
            rows.add(MenuRow.playlistAction("Play all", "play", id));
            rows.add(MenuRow.playlistAction("Shuffle", "shuffle", id));
            rows.add(MenuRow.playlistAction("Add songs", "add", id));
            rows.add(MenuRow.playlistAction("Rename playlist", "rename", id));
            rows.add(MenuRow.playlistAction("Export M3U8", "export", id));
            rows.add(MenuRow.playlistAction("Delete playlist", "delete", id));
            page.setEmptyState(null, null, 0);
            if (error != null) {
                rows.add(MenuRow.message("Unable to load playlist songs"));
            } else if (items == null || items.isEmpty()) {
                page.setEmptyState("Empty playlist",
                        "Add songs to this playlist to start listening.", rows.size());
            } else if (localSongs == null) {
                rows.add(MenuRow.message("Loading music library..."));
            } else {
                Map<String, LocalMusicSource.Song> indexed = new HashMap<>();
                for (LocalMusicSource.Song song : localSongs) {
                    indexed.put(song.uri.toString(), song);
                }
                for (MusicLibraryDatabase.PlaylistEntryRow entry : items) {
                    LocalMusicSource.Song song = indexed.get(entry.uri);
                    rows.add(song == null
                            ? MenuRow.playlistMissing(entry.title, id, entry.id)
                            : MenuRow.playlistSong(song, id, entry.id));
                }
            }
            page.rows.clear();
            page.rows.addAll(rows);
            if (currentPage == page) refreshBrowserPage(page);
        });
    }

    private void refreshAllPlaylistPages() {
        if (playlistsPage != null) refreshPlaylistDirectory();
        HashSet<Page> pages = new HashSet<>(backStack);
        pages.add(currentPage);
        for (Page page : pages) {
            if (page.playlistId >= 0) refreshPlaylistDetail(page);
        }
    }

    private void playlistChanged() {
        refreshPlaylistCount();
        refreshAllPlaylistPages();
    }

    private void playlistNotice(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void playlistFailure(String error) {
        Toast.makeText(this, error == null ? "Playlist operation failed" : error,
                Toast.LENGTH_LONG).show();
    }

    private void askPlaylistName(String title, String original,
            Consumer<String> onName) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(original);
        input.setSelectAllOnFocus(true);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(20), dp(8), dp(20), 0);
        holder.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(holder)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save",
                        (dialog, which) -> onName.accept(input.getText().toString()))
                .show();
    }

    private void createPlaylist(List<LocalMusicSource.Song> initialSongs) {
        askPlaylistName("Create playlist", "", name -> {
            playlists.create(name, (id, error) -> {
                if (error != null || id == null) {
                    playlistFailure(error);
                    return;
                }
                playlistChanged();
                if (initialSongs != null && !initialSongs.isEmpty()) {
                    addSongsToPlaylist(id, initialSongs);
                } else {
                    playlistNotice("Playlist created");
                }
            });
        });
    }

    private void renamePlaylist(long id) {
        playlists.list((items, error) -> {
            if (error != null || items == null) {
                playlistFailure(error);
                return;
            }
            for (MusicLibraryDatabase.PlaylistSummary item : items) {
                if (item.id != id) continue;
                askPlaylistName("Rename playlist", item.name, name ->
                        playlists.rename(id, name, (changed, failure) -> {
                            if (failure != null || !Boolean.TRUE.equals(changed)) {
                                playlistFailure(failure);
                            } else {
                                if (currentPage.playlistId == id) {
                                    currentPage.name = name.trim();
                                }
                                playlistNotice("Playlist renamed");
                                playlistChanged();
                            }
                        }));
                return;
            }
            playlistFailure("Playlist no longer exists");
        });
    }

    private void confirmPlaylistDelete(long id, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Delete playlist?")
                .setMessage("Delete \"" + name
                        + "\"? The audio files will not be deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) ->
                        playlists.delete(id, (removed, error) -> {
                            if (error != null || !Boolean.TRUE.equals(removed)) {
                                playlistFailure(error);
                                return;
                            }
                            if (currentPage.playlistId == id) navigateBack();
                            playlistNotice("Playlist deleted");
                            playlistChanged();
                        }))
                .show();
    }

    private void addSongsToPlaylist(long id, List<LocalMusicSource.Song> songs) {
        if (songs == null || songs.isEmpty()) {
            playlistNotice("No songs to add");
            return;
        }
        playlists.add(id, new ArrayList<>(songs), (added, error) -> {
            if (error != null || !Boolean.TRUE.equals(added)) {
                playlistFailure(error);
            } else {
                playlistNotice(countLabel(songs.size(), "song", "songs")
                        + " added to playlist");
                playlistChanged();
            }
        });
    }

    /** A single shared action for tracks and entire artist/album/genre groups. */
    private void choosePlaylist(List<LocalMusicSource.Song> selected) {
        if (selected == null || selected.isEmpty()) return;
        List<LocalMusicSource.Song> songs = new ArrayList<>(selected);
        playlists.list((items, error) -> {
            if (error != null || items == null) {
                playlistFailure(error);
                return;
            }
            String[] choices = new String[items.size() + 1];
            for (int i = 0; i < items.size(); i++) choices[i] = items.get(i).name;
            choices[items.size()] = "New playlist...";
            new AlertDialog.Builder(this)
                    .setTitle("Add to playlist")
                    .setItems(choices, (dialog, index) -> {
                        if (index == items.size()) createPlaylist(songs);
                        else addSongsToPlaylist(items.get(index).id, songs);
                    }).show();
        });
    }

    private void movePlaylistEntry(MenuRow row, int direction) {
        playlists.move(row.playlistId, row.playlistEntryId, direction,
                (moved, error) -> {
                    if (error != null) playlistFailure(error);
                    else if (!Boolean.TRUE.equals(moved)) {
                        playlistNotice("Already at the end of the playlist");
                    }
                    refreshAllPlaylistPages();
                });
    }

    private void removePlaylistEntry(MenuRow row) {
        playlists.remove(row.playlistId, row.playlistEntryId, (removed, error) -> {
            if (error != null || !Boolean.TRUE.equals(removed)) {
                playlistFailure(error);
            } else {
                playlistNotice("Removed from playlist (file preserved)");
                playlistChanged();
            }
        });
    }

    private void openPlaylistSongPicker(long playlistId) {
        if (localSongs == null) {
            playlistNotice("Music library is loading");
            return;
        }
        Page page = new Page("Add songs", songRows(localSongs));
        page.playlistPickerId = playlistId;
        navigateTo(page);
    }

    private void playPlaylist(boolean shuffle) {
        ArrayList<LocalMusicSource.Song> queue = new ArrayList<>();
        for (MenuRow item : currentPage.rows) {
            if (item.kind == ROW_SONG && item.song != null) queue.add(item.song);
        }
        if (queue.isEmpty()) {
            playlistNotice("No available songs in playlist");
            return;
        }
        openNowPlaying();
        playback.playSongs(queue, shuffle ? new Random().nextInt(queue.size()) : 0,
                shuffle);
    }

    private void activatePlaylistAction(MenuRow row) {
        switch (row.playlistAction) {
            case "create":
                createPlaylist(Collections.emptyList());
                break;
            case "import":
                pickPlaylistImport();
                break;
            case "play":
                playPlaylist(false);
                break;
            case "shuffle":
                playPlaylist(true);
                break;
            case "add":
                openPlaylistSongPicker(row.playlistId);
                break;
            case "rename":
                renamePlaylist(row.playlistId);
                break;
            case "export":
                pickPlaylistExport(row.playlistId);
                break;
            case "delete":
                confirmPlaylistDelete(row.playlistId, currentPage.name);
                break;
            default:
                break;
        }
    }

    private void pickPlaylistImport() {
        if (localSongs == null) {
            playlistNotice("Load the music library before importing");
            return;
        }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        picker.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        try {
            startActivityForResult(picker, IMPORT_PLAYLIST_REQUEST);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Playlist import picker unavailable", exception);
            playlistFailure("Playlist file picker unavailable");
        }
    }

    private String playlistDocumentName(Uri document) {
        try (Cursor cursor = getContentResolver().query(document,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Cannot read playlist document name", exception);
        }
        return "Imported playlist.m3u8";
    }

    private void readImportedPlaylist(Uri document) {
        if (localSongs == null) {
            playlistFailure("Music library is unavailable");
            return;
        }
        String filename = playlistDocumentName(document);
        if (filename == null) filename = "Imported playlist.m3u8";
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".m3u8") && !lower.endsWith(".m3u")) {
            playlistFailure("Select an M3U or M3U8 playlist file");
            return;
        }
        final String name = filename.replaceFirst("(?i)\\.m3u8?$", "").trim();
        List<LocalMusicSource.Song> snapshot = new ArrayList<>(localSongs);
        playlistTransfer.read(document, snapshot, (imported, error) -> {
            if (error != null || imported == null) {
                playlistFailure(error);
                return;
            }
            playlists.importSongs(name.isEmpty() ? "Imported playlist" : name,
                    imported.songs, (id, failure) -> {
                        if (failure != null || id == null) {
                            playlistFailure(failure);
                            return;
                        }
                        playlistChanged();
                        playlistNotice("Imported " + imported.songs.size()
                                + " songs; " + imported.unmatched + " unmatched");
                    });
        });
    }

    private void pickPlaylistExport(long id) {
        if (localSongs == null) {
            playlistFailure("Music library is unavailable");
            return;
        }
        List<LocalMusicSource.Song> snapshot = new ArrayList<>(localSongs);
        playlists.list((items, error) -> {
            if (error != null || items == null) {
                playlistFailure(error);
                return;
            }
            for (MusicLibraryDatabase.PlaylistSummary item : items) {
                if (item.id != id) continue;
                String filename = item.name.replaceAll("[/\\\\:*?\"<>|]", "_")
                        + ".m3u8";
                playlists.entries(id, (entries, failure) -> {
                    if (failure != null || entries == null) {
                        playlistFailure(failure);
                        return;
                    }
                    playlistTransfer.checkExport(entries, snapshot, (valid, reason) -> {
                        if (reason != null || !Boolean.TRUE.equals(valid)) {
                            playlistFailure(reason);
                            return;
                        }
                        Intent picker = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("audio/x-mpegurl");
                        picker.putExtra(Intent.EXTRA_TITLE, filename);
                        picker.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                        try {
                            exportingPlaylistId = id;
                            startActivityForResult(picker, EXPORT_PLAYLIST_REQUEST);
                        } catch (RuntimeException exception) {
                            exportingPlaylistId = -1L;
                            Log.w(TAG, "Playlist export picker unavailable", exception);
                            playlistFailure("Playlist export picker unavailable");
                        }
                    });
                });
                return;
            }
            playlistFailure("Playlist no longer exists");
        });
    }

    private void writeExportedPlaylist(long id, Uri document) {
        if (localSongs == null) {
            playlistFailure("Music library is unavailable");
            return;
        }
        List<LocalMusicSource.Song> snapshot = new ArrayList<>(localSongs);
        playlists.entries(id, (items, error) -> {
            if (error != null || items == null) {
                playlistFailure(error);
                return;
            }
            playlistTransfer.write(document, items, snapshot, (count, failure) -> {
                if (failure != null) playlistFailure(failure);
                else playlistNotice("Exported " + count + " songs to M3U8");
            });
        });
    }

    /** Each settings section uses a shared, non-selectable header row. */
    private ArrayList<MenuRow> buildSettingsRows() {
        ArrayList<MenuRow> rows = new ArrayList<>();
        rows.add(MenuRow.settingsHeader("Music library"));
        rows.add(MenuRow.setting("Music Folders"));
        rows.add(MenuRow.setting("Refresh library"));
        rows.add(MenuRow.setting("Full rescan"));
        rows.add(MenuRow.setting("Auto refresh on launch"));
        return rows;
    }

    private boolean autoRefreshEnabled() {
        return getSharedPreferences(MUSIC_SETTINGS_PREFS, MODE_PRIVATE)
                .getBoolean(AUTO_REFRESH_PREF, true);
    }

    private void confirmFullRescan() {
        new AlertDialog.Builder(this)
                .setTitle("Full rescan library?")
                .setMessage("Recheck every audio file in your selected "
                        + "music folders? This may take longer than Refresh library.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Rescan",
                        (dialog, which) -> startLocalScan(true, true, true))
                .show();
    }

    /** Each configured SAF root appears in Folders, including revoked roots. */
    private void openFolders() {
        ArrayList<MenuRow> rows = new ArrayList<>();
        Set<String> authorized = new HashSet<>();
        for (Uri tree : MusicFolders.selectedTrees(this)) {
            authorized.add(tree.toString());
        }
        List<Uri> saved = MusicFolders.savedTrees(this);
        for (Uri tree : saved) {
            String name = MusicFolders.displayName(this, tree);
            if (!MusicFilePolicy.isVisibleDirectory(name)) continue;
            if (authorized.contains(tree.toString())) {
                rows.add(MenuRow.browserRoot(name, tree,
                        MusicFolders.documentId(tree)));
            } else {
                rows.add(MenuRow.browserUnavailable(name));
            }
        }
        if (rows.isEmpty()) rows.add(MenuRow.message(
                saved.isEmpty() ? "No folders selected" : "No visible folders"));
        navigateTo(new Page("Folders", rows));
    }

    /** Open exactly one directory beneath a persisted, authorized SAF tree. */
    private void openBrowserDirectory(MenuRow row) {
        if (row.folderTree == null || row.folderLocation == null) return;
        Page page = new Page(row.title, new ArrayList<>());
        page.browserTree = row.folderTree;
        page.rows.add(MenuRow.message("Loading folder..."));
        navigateTo(page);
        folderBrowser.listChildren(row.folderTree, row.folderLocation,
                new MusicFolderBrowser.Listener() {
                    @Override public void onLoaded(List<MusicFolderBrowser.Entry> entries) {
                        page.browserEntries = entries;
                        page.rows.clear();
                        page.rows.addAll(browserRows(page));
                        refreshBrowserPage(page);
                    }

                    @Override public void onError(String message) {
                        page.rows.clear();
                        page.rows.add(MenuRow.message(message));
                        refreshBrowserPage(page);
                    }
                });
    }

    /** Folder structure is read directly from SAF; playback uses the index. */
    private List<MenuRow> browserRows(Page page) {
        ArrayList<MenuRow> rows = new ArrayList<>();
        if (page.browserEntries == null) return rows;
        Map<String, LocalMusicSource.Song> indexed = new HashMap<>();
        if (localSongs != null) {
            for (LocalMusicSource.Song song : localSongs) {
                if (song.sourceTree.equals(page.browserTree)) {
                    indexed.put(song.uri.toString(), song);
                }
            }
        }
        for (MusicFolderBrowser.Entry entry : page.browserEntries) {
            if (entry.directory) {
                rows.add(MenuRow.browserDirectory(entry.name,
                        page.browserTree, entry.documentId));
            } else {
                rows.add(MenuRow.browserFile(entry.name,
                        indexed.get(entry.uri.toString())));
            }
        }
        if (rows.isEmpty()) rows.add(MenuRow.message("Empty folder"));
        return rows;
    }

    /** Shared ListView stays mounted and retains selection/scroll on updates. */
    private void refreshBrowserPage(Page page) {
        if (currentPage != page) return;
        if (searchOpen) {
            updateSearchResults();
            return;
        }
        int selected = list.getSelectedItemPosition();
        View selectedView = list.getSelectedView();
        int top = selectedView == null ? 0 : selectedView.getTop();
        visibleRows = page.rows;
        list.notifyRowsChanged();
        updateEmptyState();
        list.post(() -> {
            if (currentPage != page || searchOpen || nowPlayingVisible
                    || isFinishing() || isDestroyed()) return;
            list.requestFocus();
            list.setSelectionFromTop(Math.max(0,
                    Math.min(selected, page.rows.size() - 1)), top);
            list.refreshSelection();
        });
    }

    /** A completed metadata scan can make previously listed files playable. */
    private void refreshBrowsedSongs() {
        for (Page page : backStack) {
            if (page.browserEntries == null) continue;
            page.rows.clear();
            page.rows.addAll(browserRows(page));
        }
        if (currentPage.browserEntries != null) {
            currentPage.rows.clear();
            currentPage.rows.addAll(browserRows(currentPage));
            refreshBrowserPage(currentPage);
        }
    }

    /** Folder management is a nested page; only saved SAF trees are listed. */
    private ArrayList<MenuRow> buildMusicFolderRows() {
        ArrayList<MenuRow> rows = new ArrayList<>();
        rows.add(MenuRow.setting("Add folders"));
        Set<String> authorized = new HashSet<>();
        for (Uri tree : MusicFolders.selectedTrees(this)) {
            authorized.add(tree.toString());
        }
        for (Uri tree : MusicFolders.savedTrees(this)) {
            String location = MusicFolders.displayLocation(
                    MusicFolders.documentId(tree));
            if (!authorized.contains(tree.toString())) {
                location = "Access unavailable - " + location;
            }
            rows.add(MenuRow.folder(MusicFolders.displayName(this, tree),
                    location, tree));
        }
        return rows;
    }

    /** Refresh the existing submenu in place after an add or remove. */
    private void refreshMusicFolderRows() {
        if (musicFoldersPage == null) return;
        musicFoldersPage.rows.clear();
        musicFoldersPage.rows.addAll(buildMusicFolderRows());
        if (currentPage != musicFoldersPage) return;
        if (searchOpen) {
            updateSearchResults();
            return;
        }
        int selected = Math.max(0, Math.min(list.getSelectedItemPosition(),
                musicFoldersPage.rows.size() - 1));
        View selectedView = list.getSelectedView();
        int top = selectedView == null ? 0 : selectedView.getTop();
        visibleRows = musicFoldersPage.rows;
        list.notifyRowsChanged();
        list.post(() -> {
            if (currentPage != musicFoldersPage || searchOpen
                    || isFinishing() || isDestroyed()) return;
            list.requestFocus();
            list.setSelectionFromTop(selected, top);
            list.refreshSelection();
        });
    }

    private void confirmFolderRemoval(MenuRow row) {
        if (row.folderTree == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Remove music folder?")
                .setMessage("Remove \"" + row.title
                        + "\" from the music library?\n\n"
                        + "The files on your device will not be deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove",
                        (dialog, which) -> removeMusicFolder(row.folderTree))
                .show();
    }

    private void removeMusicFolder(Uri tree) {
        try {
            if (!MusicFolders.remove(this, tree)) {
                refreshMusicFolderRows();
                Toast.makeText(this, "Folder already removed",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            // Drop in-flight scans and cached song/artwork data from the old
            // allowlist before scanning the remaining authorized trees.
            localSource.invalidate();
            localSource.forgetRemovedTree(tree,
                    MusicFolders.selectedTrees(this).isEmpty());
            loading = false;
            pendingMenu = -1;
            pendingShuffleStart = false;
            localSongs = null;
            albumArtworkSources = Collections.emptyMap();
            list.clearArtworkCache();
            refreshMusicFolderRows();
            if (MusicFolders.selectedTrees(this).isEmpty()) {
                localSongs = Collections.emptyList();
                updateLocalCategoryCounts();
            } else {
                startLocalScan(false, true);
            }
            Toast.makeText(this, "Music folder removed",
                    Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Cannot remove selected music folder", exception);
            Toast.makeText(this, "Unable to remove music folder",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Add/remove a single home-row shortcut as the existing Media3 session
     * gains or loses a playable track. Keep the stable home-menu indices
     * unchanged, and preserve the original selected row after index shifts.
     *
     * The root Page.rows list is shared with Back navigation. No second
     * adapter, page reconstruction, or extra playback controller is needed.
     */
    private void updateNowPlayingHomeRow() {
        if (rootPage == null || list == null) return;
        boolean shouldShow = playback.hasCurrentTrack();
        boolean isShown = !rootPage.rows.isEmpty()
                && rootPage.rows.get(0).kind == ROW_NOW_PLAYING;
        if (shouldShow == isShown) return;

        int offset = shouldShow ? 1 : -1;
        if (shouldShow) {
            rootPage.rows.add(0, MenuRow.nowPlaying());
        } else {
            rootPage.rows.remove(0);
        }
        // When home is on the Back stack, its saved selected row represents
        // the same menu entry despite inserting/removing the new first row.
        rootPage.selected = Math.max(0, rootPage.selected + offset);
        if (currentPage != rootPage) return;

        if (searchOpen) {
            searchOriginalPosition = Math.max(0, searchOriginalPosition + offset);
            // Home search results are songs, not home menu rows. Only the
            // empty query uses rootPage.rows as the visible adapter source.
            if (!searchInput.getText().toString().trim().isEmpty()) return;
        }

        if (nowPlayingVisible) {
            // Root list is detached while Now Playing is open. Restore the
            // same selected entry when Back reattaches it.
            savedListPosition = Math.max(0, savedListPosition + offset);
            list.notifyRowsChanged();
            return;
        }

        int selected = list.getSelectedItemPosition();
        int nextSelection = Math.max(0, selected + offset);
        View selectedView = list.getSelectedView();
        int top = selectedView == null ? 0 : selectedView.getTop();
        list.notifyRowsChanged();
        list.post(() -> {
            if (currentPage != rootPage || nowPlayingVisible || isFinishing()
                    || isDestroyed()) return;
            if (searchOpen && !searchInput.getText().toString().trim().isEmpty()) {
                return;
            }
            list.setSelectionFromTop(nextSelection, top);
            list.refreshSelection();
        });
    }

    private void shuffleAllSongs() {
        if (MusicFolders.selectedTrees(this).isEmpty()) {
            Toast.makeText(this, "Add a music folder in Settings first",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (localSongs == null) {
            pendingShuffleStart = true;
            startLocalScan(true);
            return;
        }
        if (localSongs.isEmpty()) {
            Toast.makeText(this, "No songs in selected folders",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        openNowPlaying();
        int index = new Random().nextInt(localSongs.size());
        playback.playSongs(localSongs, index, true);
    }

    private void openNowPlaying() {
        if (nowPlayingVisible) return;
        savedListPosition = list.getSelectedItemPosition();
        View selectedRow = list.getSelectedView();
        savedListTop = selectedRow == null ? 0 : selectedRow.getTop();
        safeViewport.removeView(libraryShell);
        nowPlaying = new MusicNowPlayingView(this, playback,
                new MusicNowPlayingView.MetadataNavigation() {
                    @Override public CharSequence albumFor(MediaItem item) {
                        // A retained queue may predate our no-fallback album
                        // policy. Prefer the refreshed catalog when available.
                        if (localSongs != null) {
                            for (LocalMusicSource.Song song : localSongs) {
                                if (item.mediaId.equals(song.uri.toString())) {
                                    return song.album;
                                }
                            }
                        }
                        return item.mediaMetadata.albumTitle;
                    }

                    @Override public void openArtist() {
                        openCurrentTrackGroup(1);
                    }

                    @Override public void openAlbum() {
                        openCurrentTrackGroup(2);
                    }
                });
        safeViewport.addView(nowPlaying, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        nowPlayingVisible = true;
        ensureBackCallback();
        nowPlaying.requestFocus();
        nowPlaying.render();
    }

    private void closeNowPlaying() {
        closeNowPlaying(true);
    }

    private void closeNowPlaying(boolean restoreListPosition) {
        if (!nowPlayingVisible) return;
        nowPlayingVisible = false;
        if (nowPlaying != null) {
            safeViewport.removeView(nowPlaying);
            nowPlaying.dispose();
            nowPlaying = null;
        }
        safeViewport.addView(libraryShell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (backStack.isEmpty() && !searchOpen) unregisterNestedBack();
        if (restoreListPosition) {
            list.post(() -> {
                if (nowPlayingVisible || isFinishing() || isDestroyed()) return;
                list.requestFocus();
                list.setSelectionFromTop(Math.max(0, savedListPosition), savedListTop);
                list.refreshSelection();
            });
        }
    }

    /**
     * Metadata links enter the normal library hierarchy, rather than
     * retaining Now Playing as a parent page. For example:
     * Music -> Albums -> [current album] -> Back -> Albums -> Back -> Music.
     *
     * Match by the current queue item's exact SAF media ID, not by label:
     * different albums can share a title and artist display names can repeat.
     */
    private void openCurrentTrackGroup(int category) {
        if (!nowPlayingVisible || (category != 1 && category != 2)) return;
        MediaController controller = playback.controller();
        MediaItem playing = controller == null ? null : controller.getCurrentMediaItem();
        if (playing == null) return;
        if (localSongs == null) {
            Toast.makeText(this, "Music library is still loading",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Use the same group builder and same group-page constructor as
        // Home -> Artists / Albums. The original active queue stays intact.
        List<MenuRow> categoryRows = groupedRows(category);
        MenuRow target = null;
        int targetPosition = -1;
        for (int i = 0; i < categoryRows.size() && target == null; i++) {
            MenuRow candidate = categoryRows.get(i);
            if (candidate.kind != ROW_GROUP || candidate.songs == null) continue;
            for (LocalMusicSource.Song song : candidate.songs) {
                if (playing.mediaId.equals(song.uri.toString())) {
                    target = candidate;
                    targetPosition = i;
                    break;
                }
            }
        }
        if (target == null) {
            Toast.makeText(this, "This track is not in the current music library",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int rootPosition = -1;
        for (int i = 0; i < rootPage.rows.size(); i++) {
            MenuRow row = rootPage.rows.get(i);
            if (row.kind == ROW_MENU && row.menuIndex == category) {
                rootPosition = i;
                break;
            }
        }
        if (rootPosition < 0) return;

        // Now Playing is a detached presentation, not a parent in the
        // library's Back stack. Discard the prior browsing route and create
        // precisely the breadcrumb pages reached by normal menu traversal.
        if (searchOpen) closeSearch(false);
        closeNowPlaying(false);
        pendingMenu = -1;
        backStack.clear();
        rootPage.selected = rootPosition;
        rootPage.selectedTop = 0;
        Page categoryPage = new Page(MENU[category], categoryRows);
        categoryPage.selected = targetPosition;
        categoryPage.selectedTop = 0;
        backStack.push(rootPage);
        backStack.push(categoryPage);
        ensureBackCallback();
        showPage(pageForGroup(target));
    }

    @Override protected void onStart() {
        super.onStart();
        playback.connect();
    }

    private void showFolderPicker() {
        // Folder choice is made by Android's trusted document picker. Only
        // the explicitly selected tree, not device-wide storage, is granted.
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        picker.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        try {
            startActivityForResult(picker, ADD_FOLDER_REQUEST);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Cannot open Android folder picker", exception);
            Toast.makeText(this, "Folder picker unavailable", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMPORT_PLAYLIST_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                readImportedPlaylist(data.getData());
            }
            return;
        }
        if (requestCode == EXPORT_PLAYLIST_REQUEST) {
            long id = exportingPlaylistId;
            exportingPlaylistId = -1L;
            if (id >= 0 && resultCode == RESULT_OK
                    && data != null && data.getData() != null) {
                writeExportedPlaylist(id, data.getData());
            }
            return;
        }
        if (requestCode == AUTHORIZE_DELETE_FOLDER_REQUEST) {
            onDeleteFolderAuthorization(resultCode, data);
            return;
        }
        if (requestCode != ADD_FOLDER_REQUEST || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri selectedTree = data.getData();
        if (!DocumentsContract.isTreeUri(selectedTree)) {
            Toast.makeText(this, "Invalid folder selection",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            boolean authorizedBefore =
                    MusicFolders.selectedTrees(this).contains(selectedTree);
            // Persist read permission only; BB Music never modifies files.
            getContentResolver().takePersistableUriPermission(selectedTree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            boolean added = MusicFolders.add(this, selectedTree);
            boolean accessRestored = !authorizedBefore
                    && MusicFolders.selectedTrees(this).contains(selectedTree);
            if (added || accessRestored) {
                // Invalidate old lists and stop accepting any pending scan.
                // New scans can read only the updated authorized allowlist.
                localSource.invalidate();
                localSongs = null;
                albumArtworkSources = Collections.emptyMap();
                list.clearArtworkCache();
                loading = false;
                pendingMenu = -1;
                startLocalScan(false, true);
                Log.i(TAG, "Selected music folder added; total="
                        + MusicFolders.selectedTrees(this).size());
            }
            refreshMusicFolderRows();
            Toast.makeText(this, added ? "Music folder added"
                    : accessRestored ? "Music folder access restored"
                    : "Folder already selected", Toast.LENGTH_SHORT).show();
        } catch (SecurityException | IllegalStateException exception) {
            Log.w(TAG, "Cannot persist selected folder", exception);
            Toast.makeText(this, "Unable to save folder access",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openLocalCategory(int menuIndex) {
        pendingMenu = menuIndex;
        if (MusicFolders.selectedTrees(this).isEmpty()) {
            pendingMenu = -1;
            ArrayList<MenuRow> message = new ArrayList<>();
            message.add(MenuRow.message("No folders selected"));
            navigateTo(new Page(MENU[menuIndex], message));
            return;
        }
        if (localSongs != null) {
            showLocalCategory(menuIndex);
        } else if (!loading) {
            startLocalScan(true);
        }
    }

    private void startLocalScan(boolean showFeedback) {
        startLocalScan(showFeedback, false);
    }

    private void startLocalScan(boolean showFeedback, boolean refresh) {
        startLocalScan(showFeedback, refresh, false);
    }

    private void startLocalScan(boolean showFeedback, boolean refresh,
            boolean fullRescan) {
        if (loading) {
            if (showFeedback) {
                Toast.makeText(this, "Library scan already in progress",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (MusicFolders.selectedTrees(this).isEmpty()) {
            localSongs = Collections.emptyList();
            updateLocalCategoryCounts();
            return;
        }
        loading = true;
        // Keep the previous snapshot browseable while a refresh runs.
        if (!refresh) localSongs = null;
        list.refreshVisibleRows();
        if (showFeedback) {
            Toast.makeText(this, fullRescan ? "Rescanning all music..."
                    : refresh ? "Checking music library..."
                    : "Loading music library...", Toast.LENGTH_SHORT).show();
        }
        LocalMusicSource.Listener listener = new LocalMusicSource.Listener() {
            @Override public void onLoaded(List<LocalMusicSource.Song> songs,
                    LocalMusicSource.ScanSummary summary) {
                loading = false;
                localSongs = songs;
                if (refresh && summary.artworkMayHaveChanged()) {
                    list.clearArtworkCache();
                }
                updateAlbumArtworkSources();
                updateLocalCategoryCounts();
                refreshBrowsedSongs();
                // A resumed Media3 queue can carry pre-migration metadata.
                // Rebind Now Playing from the newly verified local catalog.
                if (nowPlayingVisible && nowPlaying != null) nowPlaying.render();
                if (searchOpen) updateSearchResults();
                Log.i(TAG, "Selected-folder music ready: " + songs.size()
                        + " tracks");
                if (showFeedback && refresh) {
                    Toast.makeText(MusicActivity.this, summary.message(),
                            Toast.LENGTH_LONG).show();
                }
                if (pendingShuffleStart) {
                    pendingShuffleStart = false;
                    shuffleAllSongs();
                }
                int menu = pendingMenu;
                pendingMenu = -1;
                if (menu >= 0 && backStack.isEmpty()) showLocalCategory(menu);

                // Show cached tracks first, then quietly perform one scan.
                // Do not duplicate a scan already required by the initial load.
                if (startupAutoRefreshPending) {
                    startupAutoRefreshPending = false;
                    if (!refresh && !summary.scanned && autoRefreshEnabled()) {
                        startLocalScan(false, true);
                    }
                }
            }

            @Override public void onError(String reason) {
                loading = false;
                startupAutoRefreshPending = false;
                pendingMenu = -1;
                pendingShuffleStart = false;
                list.refreshVisibleRows();
                if (searchOpen) updateSearchResults();
                Toast.makeText(MusicActivity.this, reason, Toast.LENGTH_LONG).show();
            }
        };
        if (fullRescan) {
            localSource.fullRescan(listener);
        } else if (refresh) {
            localSource.refresh(listener);
        } else {
            localSource.load(listener);
        }
    }

    private void showLocalCategory(int category) {
        if (localSongs == null || category < 0 || category > 3) return;
        List<MenuRow> rows;
        switch (category) {
            case 0:
                rows = songRows(localSongs);
                break;
            case 1:
            case 2:
            case 3:
                rows = groupedRows(category);
                break;
            default:
                return;
        }
        navigateTo(new Page(MENU[category], rows));
        pendingMenu = -1;
    }

    /** Shared detail page for normal group activation and metadata links. */
    private Page pageForGroup(MenuRow group) {
        List<LocalMusicSource.Song> songs = new ArrayList<>(group.songs);
        if (group.groupCategory == 2) {
            // Preserve the existing album track ordering and header.
            songs.sort(Comparator.comparingInt(song -> song.trackNumber));
        }
        return new Page(group.title, songRows(songs),
                group.groupCategory == 2 ? group : null);
    }

    private List<MenuRow> songRows(List<LocalMusicSource.Song> songs) {
        ArrayList<MenuRow> rows = new ArrayList<>(songs.size());
        for (LocalMusicSource.Song song : songs) rows.add(MenuRow.song(song));
        if (rows.isEmpty()) rows.add(MenuRow.message("No local music found"));
        return rows;
    }

    private List<MenuRow> groupedRows(int category) {
        // All grouping is read-only; no secondary music scanner is required.
        Map<String, Group> groups = new LinkedHashMap<>();
        for (LocalMusicSource.Song song : localSongs) {
            // A missing album tag does not define a browsable album.
            if (category == 2 && !MusicAlbumTag.present(song.album)) continue;
            String label = category == 1 ? song.artist
                    : category == 2 ? song.album : song.genre;
            String key = groupKey(song, category);
            Group group = groups.get(key);
            if (group == null) {
                group = new Group(label);
                groups.put(key, group);
            }
            group.songs.add(song);
        }

        ArrayList<Group> sorted = new ArrayList<>(groups.values());
        sorted.sort(Comparator.comparing(group -> group.label,
                String.CASE_INSENSITIVE_ORDER));
        ArrayList<MenuRow> rows = new ArrayList<>(sorted.size());
        for (Group group : sorted) {
            rows.add(MenuRow.group(group.label, category, group.songs));
        }
        if (rows.isEmpty()) rows.add(MenuRow.message("No local music found"));
        return rows;
    }

    private static String groupKey(LocalMusicSource.Song song, int category) {
        if (category == 1) return song.artist.toLowerCase(Locale.ROOT);
        if (category == 2) {
            return song.albumId >= 0 ? "album:" + song.albumId
                    : "album:" + song.album + ":" + song.artist;
        }
        return song.genre.toLowerCase(Locale.ROOT);
    }

    private void navigateTo(Page next) {
        Page previous = currentPage;
        if (searchOpen) {
            // The visible index is a filtered index. Restore the original
            // source-page position when navigating Back to this page.
            previous.selected = searchOriginalPosition;
            previous.selectedTop = searchOriginalTop;
            closeSearch(false);
        } else {
            previous.selected = list.getSelectedItemPosition();
            View selectedView = list.getSelectedView();
            previous.selectedTop = selectedView == null ? 0 : selectedView.getTop();
        }
        backStack.push(previous);
        ensureBackCallback();
        showPage(next);
    }

    private void bindAlbumHeader(Page page) {
        MenuRow album = page.albumGroup;
        if (album == null || album.songs == null || album.songs.isEmpty()) {
            albumHeader.clear();
            albumHeader.setVisibility(View.GONE);
            return;
        }
        LocalMusicSource.Song first = album.songs.get(0);
        String key = albumKeyForRow(album);
        List<Uri> candidates = key == null ? null : albumArtworkSources.get(key);
        albumHeader.bind(album.title, first.artist, first.genre,
                album.songs.size(), key == null ? null : "album:" + key, candidates);
        albumHeader.setVisibility(View.VISIBLE);
    }

    /**
     * Empty content belongs to the viewport, NOT the ListView adapter.
     * Reserve the actual action-row height before centering the shared view;
     * hardware focus and touch scrolling remain owned by MusicListView.
     * Other pages may opt in by setting Page.setEmptyState(...).
     */
    private void updateEmptyState() {
        if (emptyState == null || currentPage == null || searchOpen
                || nowPlayingVisible || currentPage.emptyTitle == null) {
            if (emptyState != null) emptyState.setVisibility(View.GONE);
            return;
        }
        Page page = currentPage;
        emptyState.setContent(page.emptyTitle, page.emptyMessage);
        emptyState.setArtworkResource(page.emptyArtworkRes);
        int reservedRows = Math.max(0, page.emptyAfterRows);
        int top = dp(reservedRows * MusicListView.ROW_HEIGHT_DP)
                + Math.max(0, reservedRows - 1) * list.getDividerHeight();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.topMargin = top;
        emptyState.setLayoutParams(params);
        emptyState.setVisibility(View.VISIBLE);
    }

    private void showPage(Page page) {
        if (searchOpen) closeSearch(false);
        currentPage = page;
        visibleRows = page.rows;
        bindAlbumHeader(page);
        trackpadPress.cancel();
        list.notifyRowsChanged();
        updateEmptyState();
        list.post(() -> {
            if (currentPage != page || searchOpen
                    || isFinishing() || isDestroyed()) return;
            list.requestFocus();
            list.setSelectionFromTop(Math.max(0, page.selected), page.selectedTop);
            list.refreshSelection();
        });
    }

    /**
     * Type-to-search works from both the library root and nested pages.
     * Search never changes the underlying page rows or opens extra storage.
     */
    private void openSearch() {
        if (searchOpen) return;
        searchOriginalPosition = list.getSelectedItemPosition();
        View selectedView = list.getSelectedView();
        searchOriginalTop = selectedView == null ? 0 : selectedView.getTop();
        searchOpen = true;
        // A new text search supersedes an unfinished menu navigation.
        pendingMenu = -1;
        searchBar.setVisibility(View.VISIBLE);
        updateEmptyState();
        ensureBackCallback();
        // Leave focus on the native ListView: blue selection, hardware
        // arrows, and Enter continue to work on search results.
    }

    private void closeSearch(boolean restorePosition) {
        if (!searchOpen) return;
        searchOpen = false;
        searchBar.setVisibility(View.GONE);
        noSearchResults.setVisibility(View.GONE);
        searchInput.setText("");
        visibleRows = currentPage.rows;
        list.notifyRowsChanged();
        updateEmptyState();
        if (backStack.isEmpty() && !nowPlayingVisible) unregisterNestedBack();
        if (restorePosition) {
            final Page page = currentPage;
            final int index = searchOriginalPosition;
            final int top = searchOriginalTop;
            list.post(() -> {
                if (searchOpen || nowPlayingVisible || currentPage != page
                        || isFinishing() || isDestroyed()) return;
                list.requestFocus();
                list.setSelectionFromTop(Math.max(0, index), top);
                list.refreshSelection();
            });
        }
    }

    private void updateSearchResults() {
        if (!searchOpen) return;
        String term = searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (term.isEmpty()) {
            visibleRows = currentPage.rows;
        } else {
            ArrayList<MenuRow> matches = new ArrayList<>();
            if (currentPage == rootPage) {
                // Typing on the main menu searches the full selected-folder
                // catalog, not merely the seven menu item labels.
                if (localSongs != null) {
                    for (LocalMusicSource.Song song : localSongs) {
                        if (songMatches(song, term)) matches.add(MenuRow.song(song));
                    }
                }
                if (localSongs == null && loading) {
                    matches.add(MenuRow.message("Reading selected music folders..."));
                }
            } else {
                // Within Artists, Albums, Genres or a song list, narrow only
                // the current page. The original Page.rows stay unchanged.
                for (MenuRow row : currentPage.rows) {
                    if (row.kind == ROW_MESSAGE || row.kind == ROW_SETTINGS_HEADER) continue;
                    if (row.title.toLowerCase(Locale.ROOT).contains(term)
                            || (row.kind == ROW_SONG && songMatches(row.song, term))) {
                        matches.add(row);
                    }
                }
            }
            visibleRows = matches;
        }
        noSearchResults.setVisibility(!term.isEmpty() && visibleRows.isEmpty()
                ? View.VISIBLE : View.GONE);
        list.notifyRowsChanged();
        list.setSelectionFromTop(0, 0);
        list.refreshSelection();
    }

    private static boolean songMatches(LocalMusicSource.Song song, String term) {
        return song.title.toLowerCase(Locale.ROOT).contains(term)
                || song.artist.toLowerCase(Locale.ROOT).contains(term)
                || song.album.toLowerCase(Locale.ROOT).contains(term)
                || song.genre.toLowerCase(Locale.ROOT).contains(term);
    }

    /**
     * Hardware printable keys edit the field without stealing ListView focus.
     * Supports Q25 ALT/Shift characters, Backspace and cursor selections.
     * A touch tap can still focus the native EditText for paste/editing.
     */
    private boolean handleSearchKey(KeyEvent event) {
        int code = event.getKeyCode();
        if (searchOpen && (code == KeyEvent.KEYCODE_DEL
                || code == KeyEvent.KEYCODE_FORWARD_DEL)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                Editable value = searchInput.getText();
                int length = value.length();
                int start = Math.max(0, searchInput.getSelectionStart());
                int end = Math.max(0, searchInput.getSelectionEnd());
                start = Math.min(start, length);
                end = Math.min(end, length);
                if (start != end) {
                    value.delete(Math.min(start, end), Math.max(start, end));
                } else if (code == KeyEvent.KEYCODE_DEL && start > 0) {
                    int from = Character.offsetByCodePoints(value.toString(), start, -1);
                    value.delete(from, start);
                } else if (code == KeyEvent.KEYCODE_FORWARD_DEL && start < length) {
                    int to = Character.offsetByCodePoints(value.toString(), start, 1);
                    value.delete(start, to);
                }
            }
            return true;
        }
        // Ctrl/Meta shortcuts must remain available to Android.
        if ((event.getMetaState()
                & (KeyEvent.META_CTRL_ON | KeyEvent.META_META_ON)) != 0) return false;
        int character = event.getUnicodeChar();
        if (!Character.isValidCodePoint(character) || character < 32
                || Character.isISOControl(character)) return false;
        if (!searchOpen && Character.isWhitespace(character)) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            openSearch();
            Editable value = searchInput.getText();
            int length = value.length();
            int start = Math.max(0, Math.min(length, searchInput.getSelectionStart()));
            int end = Math.max(0, Math.min(length, searchInput.getSelectionEnd()));
            int from = Math.min(start, end);
            value.replace(from, Math.max(start, end),
                    new String(Character.toChars(character)));
            searchInput.setSelection(from + Character.charCount(character));
        }
        // Consume the matching key-up as well: no second character is sent
        // to the ListView or a temporarily focused EditText.
        return true;
    }

    private void ensureBackCallback() {
        if (nestedBackCallback != null) return;
        nestedBackCallback = this::navigateBack;
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, nestedBackCallback);
    }

    private void navigateBack() {
        if (nowPlayingVisible) {
            closeNowPlaying();
            return;
        }
        if (searchOpen) {
            closeSearch(true);
            return;
        }
        if (backStack.isEmpty()) return;
        Page previous = backStack.pop();
        if (backStack.isEmpty()) unregisterNestedBack();
        showPage(previous);
    }

    private void unregisterNestedBack() {
        if (nestedBackCallback == null) return;
        getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                nestedBackCallback);
        nestedBackCallback = null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (nowPlayingVisible || searchOpen || !backStack.isEmpty()) navigateBack();
        else super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (!TrackpadPressHandler.isConfirmKey(code)
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            trackpadPress.cancel();
        }
        if (TrackpadPressHandler.isConfirmKey(code)) {
            return trackpadPress.handle(event);
        }
        if (nowPlayingVisible) {
            if (code == KeyEvent.KEYCODE_DPAD_UP
                    || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_LEFT
                    || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && nowPlaying != null) {
                    nowPlaying.directional(code);
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        // Physical keyboard type-to-search requires no menu or shortcut.
        // Keep system modifier shortcuts and media/trackpad keys untouched.
        if (handleSearchKey(event)) return true;
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            return list.handleVerticalKey(event);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            trackpadPress.cancel();
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onStop() {
        trackpadPress.cancel();
        propertiesRequestSerial++;
        deleteConfirmationSerial++;
        if (contextMenuDialog != null) {
            contextMenuDialog.dismiss();
            contextMenuDialog = null;
        }
        if (propertiesDialog != null) {
            propertiesDialog.dismiss();
            propertiesDialog = null;
        }
        // Finishing via root Back is a close, not background navigation.
        // Let the service retain the position before disconnecting.
        if (isFinishing()) playback.pauseForExit();
        playback.disconnect();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        unregisterNestedBack();
        if (nowPlaying != null) nowPlaying.dispose();
        playback.disconnect();
        list.dispose();
        localSource.close();
        folderBrowser.close();
        trackPropertiesReader.close();
        trackDeletion.close();
        playlists.close();
        playlistTransfer.close();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) trackpadPress.cancel();
    }

}
