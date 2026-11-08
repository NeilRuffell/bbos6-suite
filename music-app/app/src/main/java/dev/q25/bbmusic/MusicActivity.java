package dev.q25.bbmusic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
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
    private LinearLayout libraryShell;
    private MusicAlbumHeaderView albumHeader;
    private EditText searchInput;
    private MusicSearchBarView searchBar;
    private TextView noSearchResults;
    private Page rootPage;
    private Page settingsPage;
    private Page musicFoldersPage;
    private List<MenuRow> visibleRows;
    private boolean searchOpen;
    private int searchOriginalPosition;
    private int searchOriginalTop;
    private TrackpadPressHandler trackpadPress;
    private LocalMusicSource localSource;
    private MusicFolderBrowser folderBrowser;
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

        MenuRow(String title, int kind, int menuIndex, int groupCategory,
                List<LocalMusicSource.Song> songs, LocalMusicSource.Song song,
                Uri folderTree, String folderLocation) {
            this.title = title;
            this.kind = kind;
            this.menuIndex = menuIndex;
            this.groupCategory = groupCategory;
            this.songs = songs;
            this.song = song;
            this.folderTree = folderTree;
            this.folderLocation = folderLocation;
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

    private static final class Page {
        final String name;
        final List<MenuRow> rows;
        // Only a page opened directly from an album group has this header.
        final MenuRow albumGroup;
        // Set only on filesystem pages; entries are cached per visited page
        // so a library refresh can rebind song availability without requerying.
        Uri browserTree;
        List<MusicFolderBrowser.Entry> browserEntries;
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
                () -> { /* Long-press behavior will be specified later. */ });
        localSource = new LocalMusicSource(this);
        folderBrowser = new MusicFolderBrowser(this);
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
        libraryShell.addView(list, new LinearLayout.LayoutParams(
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
        if ("Unknown Album".equalsIgnoreCase(song.album)) {
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
            // Playlist storage is not implemented, hence no playlist children.
            return "0 Items";
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
                Toast.makeText(this, "Playlists are not implemented yet",
                        Toast.LENGTH_SHORT).show();
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
            List<LocalMusicSource.Song> groupSongs = new ArrayList<>(row.songs);
            if (row.groupCategory == 2) {
                // Tagged track numbers provide album ordering where available.
                groupSongs.sort(Comparator.comparingInt(song -> song.trackNumber));
            }
            navigateTo(new Page(row.title, songRows(groupSongs),
                    row.groupCategory == 2 ? row : null));
        } else if (row.kind == ROW_SONG) {
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
        nowPlaying = new MusicNowPlayingView(this, playback);
        safeViewport.addView(nowPlaying, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        nowPlayingVisible = true;
        ensureBackCallback();
        nowPlaying.requestFocus();
        nowPlaying.render();
    }

    private void closeNowPlaying() {
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
        list.post(() -> {
            if (nowPlayingVisible || isFinishing() || isDestroyed()) return;
            list.requestFocus();
            list.setSelectionFromTop(Math.max(0, savedListPosition), savedListTop);
            list.refreshSelection();
        });
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

    private void showPage(Page page) {
        if (searchOpen) closeSearch(false);
        currentPage = page;
        visibleRows = page.rows;
        bindAlbumHeader(page);
        trackpadPress.cancel();
        list.notifyRowsChanged();
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
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) trackpadPress.cancel();
    }

}
