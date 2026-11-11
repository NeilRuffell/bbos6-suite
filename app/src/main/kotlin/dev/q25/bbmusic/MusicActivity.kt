package dev.q25.bbmusic

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import dev.q25.bbos6.ui.Bbos6ContextMenu
import dev.q25.bbos6.ui.Bbos6ContextMenuItem
import dev.q25.bbos6.ui.Bbos6PopupButtonList
import dev.q25.bbos6.ui.Bbos6TextField
import dev.q25.bbos6.ui.Bbos6PropertiesPopup
import dev.q25.bbos6.ui.compose.Bbos6EmptyStateComposeView
import dev.q25.bbos6.ui.compose.Bbos6SearchEmptyFeedbackView
import dev.q25.bbmusic.MusicActivityModels.MenuRow
import dev.q25.bbmusic.MusicActivityModels.Page
import dev.q25.bbmusic.MusicActivityModels.DeleteRequest
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.Locale
import java.util.Random
import java.util.function.Consumer
import java.util.function.BooleanSupplier

/**
 * Music's Activity/navigation controller. UI surfaces and music policies are
 * delegated to their existing Kotlin and canonical BBOS6 shared components.
 * Native Android ListView remains the Q25 touch/keyboard/trackpad focus owner.
 */
class MusicActivity : ComponentActivity() {
    private companion object {
        const val TAG = "BBMusic"
        const val ADD_FOLDER_REQUEST = 6102
        const val AUTHORIZE_DELETE_FOLDER_REQUEST = 6103
        const val IMPORT_PLAYLIST_REQUEST = 6104
        const val EXPORT_PLAYLIST_REQUEST = 6105
        const val MUSIC_SETTINGS_PREFS = "bbmusic_settings"
        const val AUTO_REFRESH_PREF = "auto_refresh_on_launch"
        const val MENU_FOLDERS = 7
        val MENU = arrayOf(
            "All Songs", "Artists", "Albums", "Genres",
            "Playlists", "Shuffle Songs", "Settings", "Folders"
        )
        const val ROW_MENU = MusicActivityModels.ROW_MENU
        const val ROW_SONG = MusicActivityModels.ROW_SONG
        const val ROW_GROUP = MusicActivityModels.ROW_GROUP
        const val ROW_MESSAGE = MusicActivityModels.ROW_MESSAGE
        const val ROW_SETTING = MusicActivityModels.ROW_SETTING
        const val ROW_FOLDER = MusicActivityModels.ROW_FOLDER
        const val ROW_NOW_PLAYING = MusicActivityModels.ROW_NOW_PLAYING
        const val ROW_SETTINGS_HEADER = MusicActivityModels.ROW_SETTINGS_HEADER
        const val ROW_BROWSER_DIRECTORY = MusicActivityModels.ROW_BROWSER_DIRECTORY
        const val ROW_BROWSER_FILE = MusicActivityModels.ROW_BROWSER_FILE
        const val ROW_BROWSER_UNAVAILABLE = MusicActivityModels.ROW_BROWSER_UNAVAILABLE
        const val ROW_BROWSER_ROOT = MusicActivityModels.ROW_BROWSER_ROOT
        const val ROW_PLAYLIST = MusicActivityModels.ROW_PLAYLIST
        const val ROW_PLAYLIST_ACTION = MusicActivityModels.ROW_PLAYLIST_ACTION
        const val ROW_PLAYLIST_MISSING = MusicActivityModels.ROW_PLAYLIST_MISSING
    }

    private lateinit var list: MusicListView
    private lateinit var listViewport: FrameLayout
    private lateinit var emptyState: Bbos6EmptyStateComposeView
    private lateinit var libraryShell: LinearLayout
    private lateinit var albumHeader: MusicAlbumHeaderView
    private lateinit var searchInput: Bbos6TextField
    private lateinit var searchBar: MusicSearchBarView
    private lateinit var noSearchResults: Bbos6SearchEmptyFeedbackView
    private lateinit var rootPage: Page
    private var settingsPage: Page? = null
    private var musicFoldersPage: Page? = null
    private var playlistsPage: Page? = null
    private lateinit var playlists: MusicPlaylists
    private lateinit var playlistTransfer: MusicPlaylistTransfer
    private var playlistCount = 0
    private var exportingPlaylistId = -1L
    private var visibleRows: List<MenuRow> = emptyList()
    private var searchOpen = false
    private var searchOriginalPosition = 0
    private var searchOriginalTop = 0
    private lateinit var trackpadPress: TrackpadPressHandler
    private lateinit var localSource: LocalMusicSource
    private lateinit var folderBrowser: MusicFolderBrowser
    private lateinit var trackPropertiesReader: MusicTrackPropertiesReader
    private lateinit var trackDeletion: MusicTrackDeletion
    private var pendingDelete: DeleteRequest? = null
    private var deletingTracks = false
    private var deleteConfirmationSerial = 0
    private var propertiesDialog: Dialog? = null
    private var contextMenuDialog: Dialog? = null
    private var buttonPopupDialog: Dialog? = null
    private var propertiesRequestSerial = 0
    private var albumArtworkSources: Map<String, List<Uri>> = emptyMap()
    private lateinit var playback: MusicPlaybackClient
    private lateinit var appShell: MusicAppShellView
    private var nowPlaying: MusicNowPlayingView? = null
    private var nowPlayingVisible = false
    private var savedListPosition = 0
    private var savedListTop = 0
    private var pendingShuffleStart = false
    private var localSongs: List<LocalMusicSource.Song>? = null
    private val localCategoryCounts = IntArray(4)
    private var loading = false
    private var startupAutoRefreshPending = true
    private var pendingMenu = -1
    private val backStack = ArrayDeque<Page>()
    private lateinit var currentPage: Page
    private var nestedBackCallback: OnBackInvokedCallback? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        trackpadPress = TrackpadPressHandler(
            Runnable { activateSelected() },
            Runnable { longPressSelected() },
            BooleanSupplier { supportsSelectedLongPress() }
        )
        localSource = LocalMusicSource(this)
        folderBrowser = MusicFolderBrowser(this)
        trackPropertiesReader = MusicTrackPropertiesReader(this)
        trackDeletion = MusicTrackDeletion(this)
        playlists = MusicPlaylists(this)
        playlistTransfer = MusicPlaylistTransfer(this)
        refreshPlaylistCount()
        playback = MusicPlaybackClient(this, object : MusicPlaybackClient.Listener {
            override fun onPlaybackChanged() {
                updateNowPlayingHomeRow()
                if (nowPlayingVisible) nowPlaying?.render()
            }
            override fun onPlaybackError(message: String) {
                Toast.makeText(this@MusicActivity, message, Toast.LENGTH_LONG).show()
            }
        })
        val rootRows = ArrayList<MenuRow>()
        for (index in 0 until MENU.size - 1) {
            rootRows.add(MenuRow.root(MENU[index], index))
            if (index == 4) rootRows.add(MenuRow.root(MENU[MENU_FOLDERS], MENU_FOLDERS))
        }
        currentPage = Page("Music", rootRows)
        rootPage = currentPage
        visibleRows = currentPage.rows

        list = MusicListView(this, object : MusicListView.DataSource {
            override fun size() = visibleRows.size
            override fun rowAt(position: Int) = rowPresentation(visibleRows[position])
            override fun activate(position: Int) = this@MusicActivity.activate(position)
            override fun longPress(position: Int) = longPressRow(position, false)
            override fun supportsLongPress(position: Int) = supportsRowLongPress(position)
        })
        libraryShell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        albumHeader = MusicAlbumHeaderView(this, list)
        albumHeader.visibility = View.GONE
        libraryShell.addView(
            albumHeader,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(MusicAlbumHeaderView.heightDp()))
        )
        searchInput = Bbos6TextField(this).apply {
            setSingleLine(true)
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setShowSoftInputOnFocus(false)
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(value: Editable?) {
                if (searchOpen) updateSearchResults()
            }
        })
        searchBar = MusicSearchBarView(this, searchInput)
        searchBar.visibility = View.GONE
        libraryShell.addView(
            searchBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(MusicSearchBarView.heightDp()))
        )
        noSearchResults = Bbos6SearchEmptyFeedbackView(this, "No search results found")
        noSearchResults.visibility = View.GONE
        libraryShell.addView(
            noSearchResults, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        listViewport = FrameLayout(this)
        listViewport.addView(
            list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        emptyState = Bbos6EmptyStateComposeView(this)
        emptyState.visibility = View.GONE
        listViewport.addView(
            emptyState, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        libraryShell.addView(
            listViewport, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        appShell = MusicAppShellView(this, libraryShell)
        setContentView(appShell)
        list.post {
            if (!isFinishing && !isDestroyed) {
                list.requestFocus()
                list.setSelectionFromTop(0, 0)
                list.refreshSelection()
            }
        }
        if (MusicFolders.selectedTrees(this).isEmpty()) {
            localSongs = emptyList()
            startupAutoRefreshPending = false
        } else {
            startLocalScan(false)
        }
    }

    private fun dp(value: Float): Int = Math.round(resources.displayMetrics.density * value)
    private fun dp(value: Int): Int = dp(value.toFloat())

    private fun rowPresentation(row: MenuRow): MusicListView.RowData =
        MusicLibraryRowPolicy.presentation(
            row, currentPage.albumGroup != null, albumArtworkSources,
            localSongs?.size, loading, localCategoryCounts, playlistCount,
            row.kind == ROW_SETTING && row.title == "Auto refresh on launch" && autoRefreshEnabled()
        )

    private fun updateAlbumArtworkSources() {
        albumArtworkSources = MusicLibraryGroupPolicy.indexAlbumArtworkSources(localSongs)
    }

    private fun updateLocalCategoryCounts() {
        val songs = localSongs ?: return
        MusicLibraryGroupPolicy.fillCategoryCounts(songs, localCategoryCounts)
        list.refreshVisibleRows()
    }

    private fun activateSelected() {
        if (nowPlayingVisible) nowPlaying?.activate()
        else activate(list.selectedItemPosition)
    }

    private fun supportsRowLongPress(position: Int): Boolean {
        if (nowPlayingVisible || position !in visibleRows.indices) return false
        val row = visibleRows[position]
        if (!rowPresentation(row).enabled || currentPage.playlistPickerId >= 0L) return false
        return when (row.kind) {
            ROW_SONG -> row.song != null
            ROW_GROUP -> row.groupCategory in 1..3
            ROW_PLAYLIST, ROW_PLAYLIST_MISSING,
            ROW_FOLDER, ROW_BROWSER_ROOT, ROW_BROWSER_DIRECTORY -> true
            else -> false
        }
    }

    private fun supportsSelectedLongPress(): Boolean =
        supportsRowLongPress(list.selectedItemPosition)

    private fun longPressSelected() {
        longPressRow(list.selectedItemPosition, true)
    }

    private fun longPressRow(position: Int, hardware: Boolean) {
        if (!supportsRowLongPress(position)) return
        if (hardware) list.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val row = visibleRows[position]
        if (row.kind == ROW_SONG && row.song == null) return
        if (row.kind != ROW_SONG && row.kind != ROW_GROUP &&
            row.kind != ROW_PLAYLIST && row.kind != ROW_PLAYLIST_MISSING) return
        showMusicContextMenu(row)
    }

    private fun showMusicContextMenu(row: MenuRow) {
        val page = currentPage
        contextMenuDialog?.dismiss()
        propertiesRequestSerial++
        val menu = Bbos6ContextMenu.builder()
        when (row.kind) {
            ROW_PLAYLIST -> {
                menu.addGroup(
                    Bbos6ContextMenuItem.resource(
                        "playlist-rename", "Rename playlist", android.R.drawable.ic_menu_edit
                    ),
                    Bbos6ContextMenuItem.resource(
                        "playlist-delete", "Delete playlist", android.R.drawable.ic_menu_delete
                    )
                )
                menu.initiallySelected("playlist-rename")
            }
            ROW_PLAYLIST_MISSING -> {
                menu.addGroup(
                    Bbos6ContextMenuItem.resource("move-up", "Move up", android.R.drawable.arrow_up_float),
                    Bbos6ContextMenuItem.resource("move-down", "Move down", android.R.drawable.arrow_down_float),
                    Bbos6ContextMenuItem.resource(
                        "remove-from-playlist", "Remove from playlist",
                        android.R.drawable.ic_menu_close_clear_cancel
                    )
                )
                menu.initiallySelected("remove-from-playlist")
            }
            ROW_SONG -> {
                menu.addGroup(
                    Bbos6ContextMenuItem.resource(
                        "properties", "Properties", android.R.drawable.ic_menu_info_details
                    ),
                    Bbos6ContextMenuItem.resource(
                        "add-to-playlist", "Add to playlist", android.R.drawable.ic_menu_add
                    )
                )
                if (row.playlistEntryId >= 0L) {
                    menu.addGroup(
                        Bbos6ContextMenuItem.resource(
                            "move-up", "Move up", android.R.drawable.arrow_up_float
                        ),
                        Bbos6ContextMenuItem.resource(
                            "move-down", "Move down", android.R.drawable.arrow_down_float
                        ),
                        Bbos6ContextMenuItem.resource(
                            "remove-from-playlist", "Remove from playlist",
                            android.R.drawable.ic_menu_close_clear_cancel
                        )
                    )
                }
                menu.addGroup(
                    Bbos6ContextMenuItem.resource(
                        "delete", "Delete audio file", android.R.drawable.ic_menu_delete
                    )
                )
                menu.initiallySelected("properties")
            }
            else -> {
                menu.addGroup(
                    Bbos6ContextMenuItem.resource(
                        "add-to-playlist", "Add to playlist", android.R.drawable.ic_menu_add
                    ),
                    Bbos6ContextMenuItem.resource(
                        "delete", "Delete audio files", android.R.drawable.ic_menu_delete
                    )
                )
                menu.initiallySelected("add-to-playlist")
            }
        }
        contextMenuDialog = menu.onAction { item ->
            if (isFinishing || isDestroyed || nowPlayingVisible ||
                currentPage !== page || !visibleRows.contains(row)) return@onAction
            when (item.id()) {
                "properties" -> if (row.kind == ROW_SONG) showTrackProperties(row)
                "add-to-playlist" ->
                    choosePlaylist(if (row.kind == ROW_SONG) listOf(row.song!!) else row.songs!!)
                "move-up" -> movePlaylistEntry(row, -1)
                "move-down" -> movePlaylistEntry(row, 1)
                "remove-from-playlist" -> removePlaylistEntry(row)
                "playlist-rename" -> renamePlaylist(row.playlistId)
                "playlist-delete" -> confirmPlaylistDelete(row.playlistId, row.title)
                "delete" -> if (row.kind == ROW_SONG || row.kind == ROW_GROUP) confirmDelete(row)
            }
        }.show(this)
        val shownMenu = contextMenuDialog
        shownMenu?.setOnDismissListener {
            if (contextMenuDialog === shownMenu) contextMenuDialog = null
        }
    }

    private fun confirmDelete(row: MenuRow) {
        if (deletingTracks || pendingDelete != null) {
            Toast.makeText(this, "A deletion is already in progress", Toast.LENGTH_SHORT).show()
            return
        }
        val snapshot = localSongs
        if (loading || snapshot == null) {
            Toast.makeText(this, "Finish loading the library first", Toast.LENGTH_SHORT).show()
            return
        }
        val authorizedSongs = HashMap<String, LocalMusicSource.Song>()
        for (song in snapshot) authorizedSongs[song.uri.toString()] = song
        val selected = LinkedHashMap<String, LocalMusicSource.Song>()
        if (row.kind == ROW_SONG && row.song != null) {
            val song = authorizedSongs[row.song.uri.toString()]
            if (song != null) selected[song.uri.toString()] = song
        } else if (row.kind == ROW_GROUP && row.songs != null &&
            row.groupCategory in 1..3) {
            for (member in row.songs) {
                val song = authorizedSongs[member.uri.toString()]
                if (song != null) selected[song.uri.toString()] = song
            }
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, "No indexed tracks to delete", Toast.LENGTH_SHORT).show()
            return
        }
        val description = if (row.kind == ROW_SONG) {
            "track \"" + row.title + "\""
        } else {
            val type = when (row.groupCategory) {
                1 -> "artist"
                2 -> "album"
                else -> "genre"
            }
            type + " \"" + row.title + "\""
        }
        val request = DeleteRequest(currentPage, ArrayList(selected.values), description)
        val confirmation = ++deleteConfirmationSerial
        if (row.kind == ROW_SONG) {
            trackDeletion.loadDisplayName(request.songs[0]) { filename ->
                if (confirmation != deleteConfirmationSerial || isFinishing || isDestroyed ||
                    nowPlayingVisible || currentPage !== request.page ||
                    !visibleRows.contains(row)) return@loadDisplayName
                showDeleteConfirmation(
                    request,
                    "Are you sure you want to delete " +
                        (filename ?: "this track") + " permanently from storage?"
                )
            }
        } else {
            val count = request.songs.size
            showDeleteConfirmation(
                request, "Are you sure you want to delete all $count" +
                    (if (count == 1) " song" else " songs") +
                    " from $description permanently from storage?"
            )
        }
    }

    private fun showButtonPopup(popup: Bbos6PopupButtonList.Builder) {
        buttonPopupDialog?.dismiss()
        buttonPopupDialog = null
        val shown = popup.show(this)
        buttonPopupDialog = shown
        shown.setOnDismissListener {
            if (buttonPopupDialog === shown) buttonPopupDialog = null
        }
    }

    private fun showButtonConfirmation(
        title: String, message: String, confirmLabel: String, onConfirm: Runnable
    ) {
        showButtonPopup(
            Bbos6PopupButtonList.builder()
                .title(title)
                .message(message)
                .addButton("cancel", "Cancel")
                .addButton("confirm", confirmLabel)
                .initiallySelected("cancel")
                .onAction { actionId ->
                    if (actionId == "confirm" && !isFinishing && !isDestroyed) {
                        onConfirm.run()
                    }
                }
        )
    }

    private fun showDeleteConfirmation(request: DeleteRequest, message: String) {
        showButtonConfirmation(
            "Delete audio files?", message, "Delete", Runnable { authorizeAndDelete(request) }
        )
    }

    private fun authorizeAndDelete(request: DeleteRequest) {
        if (isFinishing || isDestroyed || deletingTracks || nowPlayingVisible ||
            currentPage !== request.page) return
        val authorized = MusicFolders.selectedTrees(this)
        for (song in request.songs) {
            if (!authorized.contains(song.sourceTree)) {
                Toast.makeText(this, "Music folder access was removed", Toast.LENGTH_LONG).show()
                pendingDelete = null
                return
            }
            if (!MusicFolders.hasWriteGrant(this, song.sourceTree)) {
                pendingDelete = request
                launchDeleteFolderPicker(song.sourceTree)
                return
            }
        }
        pendingDelete = null
        beginDelete(request)
    }

    private fun launchDeleteFolderPicker(tree: Uri) {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
        try {
            picker.putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildDocumentUriUsingTree(
                    tree, DocumentsContract.getTreeDocumentId(tree)
                )
            )
            Toast.makeText(
                this, "Select the same music folder to allow deletion", Toast.LENGTH_LONG
            ).show()
            @Suppress("DEPRECATION")
            startActivityForResult(picker, AUTHORIZE_DELETE_FOLDER_REQUEST)
        } catch (exception: RuntimeException) {
            pendingDelete = null
            Log.w(TAG, "Cannot request Android write access", exception)
            Toast.makeText(this, "Android folder picker unavailable", Toast.LENGTH_LONG).show()
        }
    }

    private fun onDeleteFolderAuthorization(resultCode: Int, data: Intent?) {
        val request = pendingDelete
        pendingDelete = null
        val chosen = data?.data
        if (request == null || resultCode != RESULT_OK || chosen == null) return
        var needed: Uri? = null
        for (song in request.songs) {
            if (!MusicFolders.hasWriteGrant(this, song.sourceTree)) {
                needed = song.sourceTree
                break
            }
        }
        if (needed == null || needed != chosen) {
            Toast.makeText(this, "Select the original music folder", Toast.LENGTH_LONG).show()
            return
        }
        val granted = data.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        if (granted and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0) {
            Toast.makeText(this, "Write access was not granted", Toast.LENGTH_LONG).show()
            return
        }
        try {
            contentResolver.takePersistableUriPermission(chosen, granted)
            if (!MusicFolders.hasWriteGrant(this, chosen)) {
                throw SecurityException("Missing persisted write grant")
            }
            authorizeAndDelete(request)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to retain selected folder write access", exception)
            Toast.makeText(this, "Unable to authorize deletion", Toast.LENGTH_LONG).show()
        }
    }

    private fun beginDelete(request: DeleteRequest) {
        if (deletingTracks) return
        deletingTracks = true
        localSource.invalidate()
        loading = false
        Toast.makeText(
            this, "Deleting " + request.songs.size + " songs...", Toast.LENGTH_SHORT
        ).show()
        trackDeletion.delete(request.songs) { result ->
            deletingTracks = false
            if (result.deletedCount() > 0) {
                playback.removeDeletedSongs(result.deletedIds)
                reconcileDeletedSongs(result.deletedIds)
                list.clearArtworkCache()
                updateAlbumArtworkSources()
                updateLocalCategoryCounts()
                refreshBrowsedSongs()
                refreshAllPlaylistPages()
                startLocalScan(false, true)
            }
            var message = "Deleted " + result.deletedCount() +
                (if (result.deletedCount() == 1) " song" else " songs")
            if (result.failed > 0) {
                message += "; " + result.failed + " could not be deleted"
            }
            Toast.makeText(
                this, message,
                if (result.failed > 0) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun reconcileDeletedSongs(deletedIds: Set<String>) {
        localSongs = localSongs?.filterNot { it.uri.toString() in deletedIds }
        val pages = HashSet(backStack)
        pages.add(currentPage)
        for (page in pages) {
            if (page === rootPage || page === settingsPage || page === musicFoldersPage) continue
            val browserEntries = page.browserEntries
            if (browserEntries != null) {
                page.browserEntries = browserEntries.filterNot { it.uri.toString() in deletedIds }
                page.rows.clear()
                page.rows.addAll(browserRows(page))
            } else {
                page.rows.removeAll { row ->
                    when {
                        row.kind == ROW_SONG && row.song != null ->
                            row.song.uri.toString() in deletedIds
                        row.kind == ROW_GROUP && row.songs != null -> {
                            // Groups originally hold mutable song arrays.
                            (row.songs as MutableList<LocalMusicSource.Song>)
                                .removeAll { it.uri.toString() in deletedIds }
                            row.songs.isEmpty()
                        }
                        else -> false
                    }
                }
                if (page.rows.isEmpty()) {
                    page.rows.add(MenuRow.message("No local music found"))
                }
            }
        }
        if (!nowPlayingVisible) {
            bindAlbumHeader(currentPage)
            if (searchOpen) {
                updateSearchResults()
            } else {
                visibleRows = currentPage.rows
                val selected = list.selectedItemPosition
                val top = list.selectedView?.top ?: 0
                list.notifyRowsChanged()
                list.post {
                    if (!nowPlayingVisible && !searchOpen && !isFinishing && !isDestroyed) {
                        list.requestFocus()
                        list.setSelectionFromTop(
                            maxOf(0, minOf(selected, visibleRows.size - 1)), top
                        )
                        list.refreshSelection()
                    }
                }
            }
        }
    }

    private fun showTrackProperties(row: MenuRow) {
        val request = ++propertiesRequestSerial
        val page = currentPage
        val song = row.song ?: return
        trackPropertiesReader.load(song) { items ->
            if (request != propertiesRequestSerial || isFinishing || isDestroyed ||
                nowPlayingVisible || page !== currentPage || !visibleRows.contains(row)
            ) return@load
            if (items.isEmpty()) {
                Toast.makeText(this, "Track properties unavailable", Toast.LENGTH_SHORT).show()
                return@load
            }
            if (propertiesDialog?.isShowing == true) propertiesDialog?.dismiss()
            propertiesDialog = Bbos6PropertiesPopup.show(this, items)
        }
    }

    private fun activate(position: Int) {
        if (position !in visibleRows.indices) return
        val row = visibleRows[position]
        when (row.kind) {
            ROW_NOW_PLAYING -> {
                if (playback.hasCurrentTrack()) openNowPlaying()
            }
            ROW_MENU -> when (row.menuIndex) {
                in 0..3 -> openLocalCategory(row.menuIndex)
                4 -> openPlaylistDirectory()
                5 -> shuffleAllSongs()
                6 -> {
                    settingsPage = Page("Settings", buildSettingsRows())
                    navigateTo(settingsPage!!)
                }
                MENU_FOLDERS -> openFolders()
            }
            ROW_SETTING -> when (row.title) {
                "Music Folders" -> {
                    musicFoldersPage = Page("Music Folders", buildMusicFolderRows())
                    navigateTo(musicFoldersPage!!)
                }
                "Refresh library" -> startLocalScan(true, true)
                "Full rescan" -> confirmFullRescan()
                "Auto refresh on launch" -> {
                    val enabled = !autoRefreshEnabled()
                    getSharedPreferences(MUSIC_SETTINGS_PREFS, MODE_PRIVATE).edit()
                        .putBoolean(AUTO_REFRESH_PREF, enabled).apply()
                    list.refreshVisibleRows()
                }
                "Add folders" -> showFolderPicker()
            }
            ROW_FOLDER -> confirmFolderRemoval(row)
            ROW_BROWSER_DIRECTORY, ROW_BROWSER_ROOT -> openBrowserDirectory(row)
            ROW_GROUP -> navigateTo(pageForGroup(row))
            ROW_PLAYLIST -> openPlaylist(row)
            ROW_PLAYLIST_ACTION -> activatePlaylistAction(row)
            ROW_PLAYLIST_MISSING ->
                playlistNotice("Track unavailable in selected music folders")
            ROW_SONG -> {
                if (currentPage.playlistPickerId >= 0L) {
                    addSongsToPlaylist(currentPage.playlistPickerId, listOf(row.song!!))
                    return
                }
                val queue = ArrayList<LocalMusicSource.Song>()
                var startIndex = -1
                for (item in visibleRows) {
                    if (item.kind != ROW_SONG) continue
                    if (item === row) startIndex = queue.size
                    item.song?.let { queue.add(it) }
                }
                if (startIndex >= 0) {
                    openNowPlaying()
                    playback.playSongs(queue, startIndex, false)
                }
            }
        }
    }

    private fun refreshPlaylistCount() {
        playlists.list { items, error ->
            if (error != null || items == null) return@list
            playlistCount = items.size
            if (::list.isInitialized) list.refreshVisibleRows()
        }
    }

    private fun refreshPlaylistDirectory() {
        val page = playlistsPage ?: return
        playlists.list { items, error ->
            if (isFinishing || isDestroyed || page !== playlistsPage) return@list
            val rows = ArrayList<MenuRow>()
            rows.add(MenuRow.playlistAction("Add Playlist", "choose-add", -1L))
            page.setEmptyState(null, null, 0)
            when {
                error != null -> rows.add(MenuRow.message("Unable to load playlists"))
                items.isNullOrEmpty() -> {
                    page.setEmptyState(
                        "No Playlists.",
                        "You can import or create playlists for music on your device " +
                            "by selecting Add Playlist", rows.size
                    )
                    page.emptyArtworkRes = R.drawable.bbos6_playlist_empty
                }
                else -> {
                    for (item in items) rows.add(MenuRow.playlist(item))
                    playlistCount = items.size
                    if (::list.isInitialized) list.refreshVisibleRows()
                }
            }
            page.rows.clear()
            page.rows.addAll(rows)
            if (currentPage === page) refreshBrowserPage(page)
        }
    }

    private fun openPlaylistDirectory() {
        val rows = arrayListOf(
            MenuRow.playlistAction("Add Playlist", "choose-add", -1L),
            MenuRow.message("Loading playlists...")
        )
        playlistsPage = Page("Playlists", rows).also { it.playlistDirectory = true }
        navigateTo(playlistsPage!!)
        refreshPlaylistDirectory()
    }

    private fun openPlaylist(row: MenuRow) {
        val page = Page(row.title, ArrayList<MenuRow>())
        page.playlistId = row.playlistId
        page.rows.add(MenuRow.message("Loading playlist..."))
        navigateTo(page)
        refreshPlaylistDetail(page)
    }

    private fun refreshPlaylistDetail(page: Page) {
        if (page.playlistId < 0L) return
        playlists.entries(page.playlistId) { items, error ->
            if (isFinishing || isDestroyed) return@entries
            if (currentPage !== page && !backStack.contains(page)) return@entries
            val rows = ArrayList<MenuRow>()
            val id = page.playlistId
            rows.add(MenuRow.playlistAction("Play all", "play", id))
            rows.add(MenuRow.playlistAction("Shuffle", "shuffle", id))
            rows.add(MenuRow.playlistAction("Add songs", "add", id))
            rows.add(MenuRow.playlistAction("Rename playlist", "rename", id))
            rows.add(MenuRow.playlistAction("Export M3U8", "export", id))
            rows.add(MenuRow.playlistAction("Delete playlist", "delete", id))
            page.setEmptyState(null, null, 0)
            when {
                error != null -> rows.add(MenuRow.message("Unable to load playlist songs"))
                items.isNullOrEmpty() -> page.setEmptyState(
                    "Empty playlist", "Add songs to this playlist to start listening.", rows.size
                )
                localSongs == null -> rows.add(MenuRow.message("Loading music library..."))
                else -> {
                    val indexed = HashMap<String, LocalMusicSource.Song>()
                    for (song in localSongs!!) indexed[song.uri.toString()] = song
                    for (entry in items) {
                        val song = indexed[entry.uri]
                        rows.add(
                            if (song == null)
                                MenuRow.playlistMissing(entry.title, id, entry.id)
                            else MenuRow.playlistSong(song, id, entry.id)
                        )
                    }
                }
            }
            page.rows.clear()
            page.rows.addAll(rows)
            if (currentPage === page) refreshBrowserPage(page)
        }
    }

    private fun refreshAllPlaylistPages() {
        if (playlistsPage != null) refreshPlaylistDirectory()
        val pages = HashSet(backStack)
        pages.add(currentPage)
        for (page in pages) {
            if (page.playlistId >= 0L) refreshPlaylistDetail(page)
        }
    }

    private fun playlistChanged() {
        refreshPlaylistCount()
        refreshAllPlaylistPages()
    }

    private fun playlistNotice(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun playlistFailure(error: String?) {
        Toast.makeText(
            this, error ?: "Playlist operation failed", Toast.LENGTH_LONG
        ).show()
    }

    private fun askPlaylistName(
        title: String, original: String, onName: Consumer<String>
    ) {
        showButtonPopup(
            Bbos6PopupButtonList.builder()
                .title(title)
                .textInput(original, "Playlist name")
                .horizontalButtons()
                .addButton("cancel", "Cancel")
                .addButton("save", "Save")
                .initiallySelected("cancel")
                .onTextAction { actionId, text ->
                    if (actionId == "save" && !isFinishing && !isDestroyed) {
                        onName.accept(text)
                    }
                }
        )
    }

