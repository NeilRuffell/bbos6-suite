package dev.q25.bbmusic

import android.app.Activity
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
import dev.q25.bbos6.ui.compose.Bbos6ListSelectionComposeBridge
import dev.q25.bbos6.ui.compose.Bbos6ListSelectionItem
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
                        "playlist-add-songs", "Add songs", android.R.drawable.ic_menu_add
                    ),
                    Bbos6ContextMenuItem.resource(
                        "playlist-rename", "Rename playlist", android.R.drawable.ic_menu_edit
                    ),
                    Bbos6ContextMenuItem.resource(
                        "playlist-export", "Export M3U8", android.R.drawable.ic_menu_save
                    )
                )
                menu.addGroup(
                    Bbos6ContextMenuItem.resource(
                        "playlist-delete", "Delete playlist", android.R.drawable.ic_menu_delete
                    )
                )
                menu.initiallySelected("playlist-add-songs")
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
                "playlist-add-songs" -> openPlaylistSongPicker(row.playlistId)
                "playlist-rename" -> renamePlaylist(row.playlistId)
                "playlist-export" -> pickPlaylistExport(row.playlistId)
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
                updateEmptyState()
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
                    val page = Page("Music Folders", buildMusicFolderRows())
                    page.setEmptyState(
                        "No Music Folders.",
                        "Select Add folders to choose where Music looks for songs.",
                        1, R.drawable.bbos6_menu_folders
                    )
                    musicFoldersPage = page
                    navigateTo(page)
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
                            "by selecting Add Playlist",
                        rows.size, R.drawable.bbos6_playlist_empty
                    )
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
            page.setEmptyState(null, null, 0)
            when {
                error != null -> rows.add(MenuRow.message("Unable to load playlist songs"))
                items.isNullOrEmpty() -> page.setEmptyState(
                    "Empty playlist",
                    "Long-press the playlist name in Playlists to add songs.",
                    rows.size, R.drawable.bbos6_playlist_empty
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

    private fun createPlaylist(initialSongs: List<LocalMusicSource.Song>?) {
        askPlaylistName("Create playlist", "", Consumer { name ->
            playlists.create(name) { id, error ->
                if (error != null || id == null) {
                    playlistFailure(error)
                } else {
                    playlistChanged()
                    if (!initialSongs.isNullOrEmpty()) {
                        addSongsToPlaylist(id, initialSongs)
                    } else {
                        playlistNotice("Playlist created")
                    }
                }
            }
        })
    }

    private fun renamePlaylist(id: Long) {
        playlists.list { items, error ->
            if (error != null || items == null) {
                playlistFailure(error)
                return@list
            }
            for (item in items) {
                if (item.id != id) continue
                askPlaylistName("Rename playlist", item.name, Consumer { name ->
                    playlists.rename(id, name) { changed, failure ->
                        if (failure != null || changed != true) {
                            playlistFailure(failure)
                        } else {
                            if (currentPage.playlistId == id) currentPage.name = name.trim()
                            playlistNotice("Playlist renamed")
                            playlistChanged()
                        }
                    }
                })
                return@list
            }
            playlistFailure("Playlist no longer exists")
        }
    }

    private fun confirmPlaylistDelete(id: Long, name: String) {
        showButtonConfirmation(
            "Delete playlist?",
            "Delete \"$name\"? The audio files will not be deleted.",
            "Delete",
            Runnable {
                playlists.delete(id) { removed, error ->
                    if (error != null || removed != true) {
                        playlistFailure(error)
                    } else {
                        if (currentPage.playlistId == id) navigateBack()
                        playlistNotice("Playlist deleted")
                        playlistChanged()
                    }
                }
            }
        )
    }

    private fun addSongsToPlaylist(id: Long, songs: List<LocalMusicSource.Song>?) {
        if (songs.isNullOrEmpty()) {
            playlistNotice("No songs to add")
            return
        }
        playlists.add(id, ArrayList(songs)) { added, error ->
            if (error != null || added != true) {
                playlistFailure(error)
            } else {
                playlistNotice(
                    MusicLibraryGroupPolicy.countLabel(songs.size, "song", "songs") +
                        " added to playlist"
                )
                playlistChanged()
            }
        }
    }

    private fun choosePlaylist(selected: List<LocalMusicSource.Song>?) {
        if (selected.isNullOrEmpty()) return
        val songs = ArrayList(selected)
        playlists.list { items, error ->
            if (error != null || items == null) {
                playlistFailure(error)
                return@list
            }
            if (isFinishing || isDestroyed) return@list
            val choices = ArrayList<Bbos6ListSelectionItem>(items.size + 1)
            for (item in items) {
                choices.add(Bbos6ListSelectionItem(item.id.toString(), item.name))
            }
            choices.add(Bbos6ListSelectionItem("new", "New playlist..."))
            buttonPopupDialog?.dismiss()
            buttonPopupDialog = null
            val shown = Bbos6ListSelectionComposeBridge.show(
                this, "Add to playlist", choices
            ) { choiceId ->
                if (!isFinishing && !isDestroyed) {
                    if (choiceId == "new") createPlaylist(songs)
                    else choiceId.toLongOrNull()?.let { addSongsToPlaylist(it, songs) }
                }
            }
            buttonPopupDialog = shown
            shown.setOnDismissListener {
                if (buttonPopupDialog === shown) buttonPopupDialog = null
            }
        }
    }

    private fun movePlaylistEntry(row: MenuRow, direction: Int) {
        playlists.move(row.playlistId, row.playlistEntryId, direction) { moved, error ->
            if (error != null) playlistFailure(error)
            else if (moved != true) playlistNotice("Already at the end of the playlist")
            refreshAllPlaylistPages()
        }
    }

    private fun removePlaylistEntry(row: MenuRow) {
        playlists.remove(row.playlistId, row.playlistEntryId) { removed, error ->
            if (error != null || removed != true) {
                playlistFailure(error)
            } else {
                playlistNotice("Removed from playlist (file preserved)")
                playlistChanged()
            }
        }
    }

    private fun openPlaylistSongPicker(playlistId: Long) {
        val songs = localSongs
        if (songs == null) {
            playlistNotice("Music library is loading")
            return
        }
        val page = Page("Add songs", songRows(songs).toMutableList())
        page.setEmptyState(
            "No Songs.",
            "Add a music folder in Settings to make songs available.",
            0, R.drawable.bbos6_menu_all_songs
        )
        page.playlistPickerId = playlistId
        navigateTo(page)
    }

    private fun playPlaylist(shuffle: Boolean) {
        val queue = ArrayList<LocalMusicSource.Song>()
        for (item in currentPage.rows) {
            if (item.kind == ROW_SONG && item.song != null) queue.add(item.song)
        }
        if (queue.isEmpty()) {
            playlistNotice("No available songs in playlist")
            return
        }
        openNowPlaying()
        playback.playSongs(
            queue, if (shuffle) Random().nextInt(queue.size) else 0, shuffle
        )
    }

    private fun showAddPlaylistPopup() {
        showButtonPopup(
            Bbos6PopupButtonList.builder()
                .message("Please select how you would like to add a playlist:")
                .addButton("new", "New Playlist")
                .addButton("import", "Import Playlist")
                .initiallySelected("new")
                .onAction { actionId ->
                    if (!isFinishing && !isDestroyed) {
                        when (actionId) {
                            "new" -> createPlaylist(emptyList())
                            "import" -> pickPlaylistImport()
                        }
                    }
                }
        )
    }

    private fun activatePlaylistAction(row: MenuRow) {
        when (row.playlistAction) {
            "choose-add" -> showAddPlaylistPopup()
            "create" -> createPlaylist(emptyList())
            "import" -> pickPlaylistImport()
            "play" -> playPlaylist(false)
            "shuffle" -> playPlaylist(true)
        }
    }

    @Suppress("DEPRECATION")
    private fun pickPlaylistImport() {
        if (localSongs == null) {
            playlistNotice("Load the music library before importing")
            return
        }
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        picker.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        try {
            startActivityForResult(picker, IMPORT_PLAYLIST_REQUEST)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Playlist import picker unavailable", exception)
            playlistFailure("Playlist file picker unavailable")
        }
    }

    private fun playlistDocumentName(document: Uri): String {
        try {
            contentResolver.query(
                document, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.let { return it }
            }
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Cannot read playlist document name", exception)
        }
        return "Imported playlist.m3u8"
    }

    private fun readImportedPlaylist(document: Uri) {
        val catalog = localSongs
        if (catalog == null) {
            playlistFailure("Music library is unavailable")
            return
        }
        val filename = playlistDocumentName(document)
        val lower = filename.lowercase(Locale.ROOT)
        if (!lower.endsWith(".m3u8") && !lower.endsWith(".m3u")) {
            playlistFailure("Select an M3U or M3U8 playlist file")
            return
        }
        val name = filename.replaceFirst(Regex("(?i)\\.m3u8?$"), "").trim()
        val snapshot = ArrayList(catalog)
        playlistTransfer.read(document, snapshot) { imported, error ->
            if (error != null || imported == null) {
                playlistFailure(error)
                return@read
            }
            playlists.importSongs(if (name.isEmpty()) "Imported playlist" else name, imported.songs) {
                id, failure ->
                if (failure != null || id == null) {
                    playlistFailure(failure)
                } else {
                    playlistChanged()
                    playlistNotice(
                        "Imported " + imported.songs.size + " songs; " +
                            imported.unmatched + " unmatched"
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun pickPlaylistExport(id: Long) {
        val catalog = localSongs
        if (catalog == null) {
            playlistFailure("Music library is unavailable")
            return
        }
        val snapshot = ArrayList(catalog)
        playlists.list { items, error ->
            if (error != null || items == null) {
                playlistFailure(error)
                return@list
            }
            for (item in items) {
                if (item.id != id) continue
                val filename = item.name.replace(Regex("[/\\\\:*?\"<>|]"), "_") + ".m3u8"
                playlists.entries(id) { entries, failure ->
                    if (failure != null || entries == null) {
                        playlistFailure(failure)
                        return@entries
                    }
                    playlistTransfer.checkExport(entries, snapshot) { valid, reason ->
                        if (reason != null || valid != true) {
                            playlistFailure(reason)
                        } else {
                            val picker = Intent(Intent.ACTION_CREATE_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("audio/x-mpegurl")
                            picker.putExtra(Intent.EXTRA_TITLE, filename)
                            picker.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                            try {
                                exportingPlaylistId = id
                                startActivityForResult(picker, EXPORT_PLAYLIST_REQUEST)
                            } catch (exception: RuntimeException) {
                                exportingPlaylistId = -1L
                                Log.w(TAG, "Playlist export picker unavailable", exception)
                                playlistFailure("Playlist export picker unavailable")
                            }
                        }
                    }
                }
                return@list
            }
            playlistFailure("Playlist no longer exists")
        }
    }

    private fun writeExportedPlaylist(id: Long, document: Uri) {
        val catalog = localSongs
        if (catalog == null) {
            playlistFailure("Music library is unavailable")
            return
        }
        val snapshot = ArrayList(catalog)
        playlists.entries(id) { items, error ->
            if (error != null || items == null) {
                playlistFailure(error)
                return@entries
            }
            playlistTransfer.write(document, items, snapshot) { count, failure ->
                if (failure != null) playlistFailure(failure)
                else playlistNotice("Exported " + count + " songs to M3U8")
            }
        }
    }

    private fun buildSettingsRows(): ArrayList<MenuRow> = MusicLibraryPageRows.settingsRows()

    private fun autoRefreshEnabled(): Boolean =
        getSharedPreferences(MUSIC_SETTINGS_PREFS, MODE_PRIVATE)
            .getBoolean(AUTO_REFRESH_PREF, true)

    private fun confirmFullRescan() {
        showButtonConfirmation(
            "Full rescan library?",
            "Recheck every audio file in your selected " +
                "music folders? This may take longer than Refresh library.",
            "Rescan",
            Runnable { startLocalScan(true, true, true) }
        )
    }

    private fun openFolders() {
        val page = Page("Folders", MusicLibraryPageRows.folderRootRows(this))
        page.setEmptyState(
            "No Folders.",
            "Add music folders in Settings to browse your music.",
            0, R.drawable.bbos6_menu_folders
        )
        navigateTo(page)
    }

    private fun openBrowserDirectory(row: MenuRow) {
        val tree = row.folderTree ?: return
        val directoryId = row.folderLocation ?: return
        val page = Page(row.title, ArrayList<MenuRow>())
        page.browserTree = tree
        page.setEmptyState(
            "Empty folder",
            "No indexed music or subfolders are available here.",
            0, R.drawable.bbos6_menu_folders
        )
        page.rows.add(MenuRow.message("Loading folder..."))
        navigateTo(page)
        folderBrowser.listChildren(tree, directoryId, object : MusicFolderBrowser.Listener {
            override fun onLoaded(entries: List<MusicFolderBrowser.Entry>) {
                page.browserEntries = entries
                page.rows.clear()
                page.rows.addAll(browserRows(page))
                refreshBrowserPage(page)
            }
            override fun onError(message: String) {
                page.rows.clear()
                page.rows.add(MenuRow.message(message))
                refreshBrowserPage(page)
            }
        })
    }

    private fun browserRows(page: Page): List<MenuRow> =
        MusicLibraryPageRows.browserRows(page, localSongs)

    private fun refreshBrowserPage(page: Page) {
        if (currentPage !== page) return
        if (searchOpen) {
            updateSearchResults()
            return
        }
        val selected = list.selectedItemPosition
        val top = list.selectedView?.top ?: 0
        visibleRows = page.rows
        list.notifyRowsChanged()
        updateEmptyState()
        list.post {
            if (currentPage === page && !searchOpen && !nowPlayingVisible &&
                !isFinishing && !isDestroyed) {
                list.requestFocus()
                list.setSelectionFromTop(
                    maxOf(0, minOf(selected, page.rows.size - 1)), top
                )
                list.refreshSelection()
            }
        }
    }

    private fun refreshBrowsedSongs() {
        for (page in backStack) {
            if (page.browserEntries == null) continue
            page.rows.clear()
            page.rows.addAll(browserRows(page))
        }
        if (currentPage.browserEntries != null) {
            currentPage.rows.clear()
            currentPage.rows.addAll(browserRows(currentPage))
            refreshBrowserPage(currentPage)
        }
    }

    private fun buildMusicFolderRows(): ArrayList<MenuRow> =
        MusicLibraryPageRows.musicFolderSettingsRows(this)

    private fun refreshMusicFolderRows() {
        val page = musicFoldersPage ?: return
        page.rows.clear()
        page.rows.addAll(buildMusicFolderRows())
        if (currentPage !== page) return
        if (searchOpen) {
            updateSearchResults()
            return
        }
        val selected = maxOf(0, minOf(list.selectedItemPosition, page.rows.size - 1))
        val top = list.selectedView?.top ?: 0
        visibleRows = page.rows
        list.notifyRowsChanged()
        updateEmptyState()
        list.post {
            if (currentPage === page && !searchOpen && !isFinishing && !isDestroyed) {
                list.requestFocus()
                list.setSelectionFromTop(selected, top)
                list.refreshSelection()
            }
        }
    }

    private fun confirmFolderRemoval(row: MenuRow) {
        val tree = row.folderTree ?: return
        showButtonConfirmation(
            "Remove music folder?",
            "Remove \"" + row.title + "\" from the music library?\n\n" +
                "The files on your device will not be deleted.",
            "Remove",
            Runnable { removeMusicFolder(tree) }
        )
    }

    private fun removeMusicFolder(tree: Uri) {
        try {
            if (!MusicFolders.remove(this, tree)) {
                refreshMusicFolderRows()
                Toast.makeText(this, "Folder already removed", Toast.LENGTH_SHORT).show()
                return
            }
            localSource.invalidate()
            localSource.forgetRemovedTree(tree, MusicFolders.selectedTrees(this).isEmpty())
            loading = false
            pendingMenu = -1
            pendingShuffleStart = false
            localSongs = null
            albumArtworkSources = emptyMap()
            list.clearArtworkCache()
            refreshMusicFolderRows()
            if (MusicFolders.selectedTrees(this).isEmpty()) {
                localSongs = emptyList()
                updateLocalCategoryCounts()
            } else {
                startLocalScan(false, true)
            }
            Toast.makeText(this, "Music folder removed", Toast.LENGTH_SHORT).show()
        } catch (exception: IllegalStateException) {
            Log.w(TAG, "Cannot remove selected music folder", exception)
            Toast.makeText(this, "Unable to remove music folder", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateNowPlayingHomeRow() {
        if (!::rootPage.isInitialized || !::list.isInitialized) return
        val shouldShow = playback.hasCurrentTrack()
        val isShown = rootPage.rows.isNotEmpty() && rootPage.rows[0].kind == ROW_NOW_PLAYING
        if (shouldShow == isShown) return
        val offset = if (shouldShow) 1 else -1
        if (shouldShow) rootPage.rows.add(0, MenuRow.nowPlaying())
        else rootPage.rows.removeAt(0)
        rootPage.selected = maxOf(0, rootPage.selected + offset)
        if (currentPage !== rootPage) return
        if (searchOpen) {
            searchOriginalPosition = maxOf(0, searchOriginalPosition + offset)
            if (searchInput.text.toString().trim().isNotEmpty()) return
        }
        if (nowPlayingVisible) {
            savedListPosition = maxOf(0, savedListPosition + offset)
            list.notifyRowsChanged()
            return
        }
        val selected = list.selectedItemPosition
        val nextSelection = maxOf(0, selected + offset)
        val top = list.selectedView?.top ?: 0
        list.notifyRowsChanged()
        list.post {
            if (currentPage === rootPage && !nowPlayingVisible &&
                !isFinishing && !isDestroyed &&
                (!searchOpen || searchInput.text.toString().trim().isEmpty())) {
                list.setSelectionFromTop(nextSelection, top)
                list.refreshSelection()
            }
        }
    }

    private fun shuffleAllSongs() {
        if (MusicFolders.selectedTrees(this).isEmpty()) {
            Toast.makeText(this, "Add a music folder in Settings first", Toast.LENGTH_LONG).show()
            return
        }
        val songs = localSongs
        if (songs == null) {
            pendingShuffleStart = true
            startLocalScan(true)
            return
        }
        if (songs.isEmpty()) {
            Toast.makeText(this, "No songs in selected folders", Toast.LENGTH_SHORT).show()
            return
        }
        openNowPlaying()
        playback.playSongs(songs, Random().nextInt(songs.size), true)
    }

    private fun openNowPlaying() {
        if (nowPlayingVisible) return
        savedListPosition = list.selectedItemPosition
        savedListTop = list.selectedView?.top ?: 0
        val shown = MusicNowPlayingView(
            this, playback, object : MusicNowPlayingView.MetadataNavigation {
                override fun albumFor(item: MediaItem): CharSequence? {
                    for (song in localSongs.orEmpty()) {
                        if (item.mediaId == song.uri.toString()) return song.album
                    }
                    return item.mediaMetadata.albumTitle
                }
                override fun openArtist() = openCurrentTrackGroup(1)
                override fun openAlbum() = openCurrentTrackGroup(2)
            }
        )
        nowPlaying = shown
        nowPlayingVisible = true
        ensureBackCallback()
        appShell.showNowPlaying(shown) {
            if (nowPlayingVisible && nowPlaying === shown) {
                shown.requestFocus()
                shown.render()
            }
        }
    }

    private fun closeNowPlaying() = closeNowPlaying(true)

    private fun closeNowPlaying(restoreListPosition: Boolean) {
        if (!nowPlayingVisible) return
        nowPlayingVisible = false
        nowPlaying?.dispose()
        nowPlaying = null
        appShell.showLibrary {
            if (restoreListPosition) {
                list.post {
                    if (!nowPlayingVisible && !isFinishing && !isDestroyed) {
                        list.requestFocus()
                        list.setSelectionFromTop(maxOf(0, savedListPosition), savedListTop)
                        list.refreshSelection()
                    }
                }
            }
        }
        if (backStack.isEmpty() && !searchOpen) unregisterNestedBack()
    }

    private fun openCurrentTrackGroup(category: Int) {
        if (!nowPlayingVisible || category !in 1..2) return
        val playing = playback.controller()?.currentMediaItem ?: return
        if (localSongs == null) {
            Toast.makeText(this, "Music library is still loading", Toast.LENGTH_SHORT).show()
            return
        }
        val categoryRows = groupedRows(category)
        var target: MenuRow? = null
        var targetPosition = -1
        for ((index, candidate) in categoryRows.withIndex()) {
            if (candidate.kind != ROW_GROUP || candidate.songs == null) continue
            if (candidate.songs.any { playing.mediaId == it.uri.toString() }) {
                target = candidate
                targetPosition = index
                break
            }
        }
        if (target == null) {
            Toast.makeText(
                this, "This track is not in the current music library", Toast.LENGTH_SHORT
            ).show()
            return
        }
        var rootPosition = -1
        for ((index, row) in rootPage.rows.withIndex()) {
            if (row.kind == ROW_MENU && row.menuIndex == category) {
                rootPosition = index
                break
            }
        }
        if (rootPosition < 0) return
        if (searchOpen) closeSearch(false)
        closeNowPlaying(false)
        pendingMenu = -1
        backStack.clear()
        rootPage.selected = rootPosition
        rootPage.selectedTop = 0
        val categoryPage = Page(MENU[category], categoryRows.toMutableList())
        categoryPage.selected = targetPosition
        categoryPage.selectedTop = 0
        backStack.push(rootPage)
        backStack.push(categoryPage)
        ensureBackCallback()
        showPage(pageForGroup(target))
    }

    override fun onStart() {
        super.onStart()
        playback.connect()
    }

    @Suppress("DEPRECATION")
    private fun showFolderPicker() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
        try {
            startActivityForResult(picker, ADD_FOLDER_REQUEST)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Cannot open Android folder picker", exception)
            Toast.makeText(this, "Folder picker unavailable", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            IMPORT_PLAYLIST_REQUEST -> {
                if (resultCode == RESULT_OK) data?.data?.let { readImportedPlaylist(it) }
                return
            }
            EXPORT_PLAYLIST_REQUEST -> {
                val id = exportingPlaylistId
                exportingPlaylistId = -1L
                if (id >= 0L && resultCode == RESULT_OK) {
                    data?.data?.let { writeExportedPlaylist(id, it) }
                }
                return
            }
            AUTHORIZE_DELETE_FOLDER_REQUEST -> {
                onDeleteFolderAuthorization(resultCode, data)
                return
            }
        }
        if (requestCode != ADD_FOLDER_REQUEST || resultCode != RESULT_OK) return
        val selectedTree = data?.data ?: return
        if (!DocumentsContract.isTreeUri(selectedTree)) {
            Toast.makeText(this, "Invalid folder selection", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val authorizedBefore = MusicFolders.selectedTrees(this).contains(selectedTree)
            contentResolver.takePersistableUriPermission(
                selectedTree, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val added = MusicFolders.add(this, selectedTree)
            val accessRestored =
                !authorizedBefore && MusicFolders.selectedTrees(this).contains(selectedTree)
            if (added || accessRestored) {
                localSource.invalidate()
                localSongs = null
                albumArtworkSources = emptyMap()
                list.clearArtworkCache()
                loading = false
                pendingMenu = -1
                startLocalScan(false, true)
                Log.i(
                    TAG, "Selected music folder added; total=" +
                        MusicFolders.selectedTrees(this).size
                )
            }
            refreshMusicFolderRows()
            Toast.makeText(
                this, when {
                    added -> "Music folder added"
                    accessRestored -> "Music folder access restored"
                    else -> "Folder already selected"
                }, Toast.LENGTH_SHORT
            ).show()
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Cannot persist selected folder", exception)
            Toast.makeText(this, "Unable to save folder access", Toast.LENGTH_LONG).show()
        }
    }

    private fun openLocalCategory(menuIndex: Int) {
        pendingMenu = menuIndex
        if (MusicFolders.selectedTrees(this).isEmpty()) {
            pendingMenu = -1
            navigateTo(categoryPage(menuIndex, emptyList(), true))
            return
        }
        if (localSongs != null) showLocalCategory(menuIndex)
        else if (!loading) startLocalScan(true)
    }

    private fun startLocalScan(showFeedback: Boolean) {
        startLocalScan(showFeedback, false, false)
    }

    private fun startLocalScan(showFeedback: Boolean, refresh: Boolean) {
        startLocalScan(showFeedback, refresh, false)
    }

    private fun startLocalScan(
        showFeedback: Boolean, refresh: Boolean, fullRescan: Boolean
    ) {
        if (loading) {
            if (showFeedback) Toast.makeText(
                this, "Library scan already in progress", Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (MusicFolders.selectedTrees(this).isEmpty()) {
            localSongs = emptyList()
            updateLocalCategoryCounts()
            return
        }
        loading = true
        if (!refresh) localSongs = null
        list.refreshVisibleRows()
        if (showFeedback) Toast.makeText(
            this, when {
                fullRescan -> "Rescanning all music..."
                refresh -> "Checking music library..."
                else -> "Loading music library..."
            }, Toast.LENGTH_SHORT
        ).show()
        val listener = object : LocalMusicSource.Listener {
            override fun onLoaded(
                songs: List<LocalMusicSource.Song>, summary: LocalMusicSource.ScanSummary
            ) {
                loading = false
                localSongs = songs
                if (refresh && summary.artworkMayHaveChanged()) list.clearArtworkCache()
                updateAlbumArtworkSources()
                updateLocalCategoryCounts()
                refreshBrowsedSongs()
                if (nowPlayingVisible) nowPlaying?.render()
                if (searchOpen) updateSearchResults()
                Log.i(TAG, "Selected-folder music ready: " + songs.size + " tracks")
                if (showFeedback && refresh) {
                    Toast.makeText(this@MusicActivity, summary.message(), Toast.LENGTH_LONG).show()
                }
                if (pendingShuffleStart) {
                    pendingShuffleStart = false
                    shuffleAllSongs()
                }
                val menu = pendingMenu
                pendingMenu = -1
                if (menu >= 0 && backStack.isEmpty()) showLocalCategory(menu)
                if (startupAutoRefreshPending) {
                    startupAutoRefreshPending = false
                    if (!refresh && !summary.scanned && autoRefreshEnabled()) {
                        startLocalScan(false, true)
                    }
                }
            }

            override fun onError(reason: String) {
                loading = false
                startupAutoRefreshPending = false
                pendingMenu = -1
                pendingShuffleStart = false
                list.refreshVisibleRows()
                if (searchOpen) updateSearchResults()
                Toast.makeText(this@MusicActivity, reason, Toast.LENGTH_LONG).show()
            }
        }
        when {
            fullRescan -> localSource.fullRescan(listener)
            refresh -> localSource.refresh(listener)
            else -> localSource.load(listener)
        }
    }

    private fun showLocalCategory(category: Int) {
        val songs = localSongs ?: return
        if (category !in 0..3) return
        val rows = if (category == 0) songRows(songs) else groupedRows(category)
        navigateTo(categoryPage(category, rows, false))
        pendingMenu = -1
    }

    private fun categoryPage(category: Int, rows: List<MenuRow>, noFolders: Boolean): Page {
        val title = when (category) {
            0 -> "No Songs."
            1 -> "No Artists."
            2 -> "No Albums."
            else -> "No Genres."
        }
        val message = if (noFolders) {
            "Add music folders in Settings to browse your music."
        } else {
            "No ${MENU[category].lowercase(Locale.ROOT)} found in selected music folders."
        }
        return Page(MENU[category], rows.toMutableList()).apply {
            setEmptyState(
                title, message, 0,
                MusicLibraryRowPolicy.menuIconForRow(MenuRow.root(MENU[category], category))
            )
        }
    }

    private fun pageForGroup(group: MenuRow): Page {
        val songs = ArrayList(group.songs!!)
        if (group.groupCategory == 2) {
            songs.sortWith(compareBy { it.trackNumber })
        }
        return Page(
            group.title, songRows(songs).toMutableList(),
            if (group.groupCategory == 2) group else null
        ).apply {
            setEmptyState(
                "No Songs.", "There are no songs available in this group.", 0,
                MusicLibraryRowPolicy.menuIconForRow(
                    MenuRow.root(MENU[group.groupCategory], group.groupCategory)
                )
            )
        }
    }

    private fun songRows(songs: List<LocalMusicSource.Song>): List<MenuRow> =
        MusicLibraryPageRows.songRows(songs)

    private fun groupedRows(category: Int): List<MenuRow> =
        MusicLibraryPageRows.groupedRows(localSongs!!, category)

    private fun navigateTo(next: Page) {
        val previous = currentPage
        if (searchOpen) {
            previous.selected = searchOriginalPosition
            previous.selectedTop = searchOriginalTop
            closeSearch(false)
        } else {
            previous.selected = list.selectedItemPosition
            previous.selectedTop = list.selectedView?.top ?: 0
        }
        backStack.push(previous)
        ensureBackCallback()
        showPage(next)
    }

    private fun bindAlbumHeader(page: Page) {
        val album = page.albumGroup
        val albumTracks = album?.songs
        if (album == null || albumTracks.isNullOrEmpty()) {
            albumHeader.clear()
            albumHeader.visibility = View.GONE
            return
        }
        val first = albumTracks[0]
        val key = MusicLibraryGroupPolicy.albumKeyForRow(album)
        val candidates = if (key == null) null else albumArtworkSources[key]
        albumHeader.bind(
            album.title, first.artist, first.genre,
            albumTracks.size, if (key == null) null else "album:$key", candidates
        )
        albumHeader.visibility = View.VISIBLE
    }

    private fun updateEmptyState() {
        if (!::emptyState.isInitialized || !::currentPage.isInitialized ||
            searchOpen || nowPlayingVisible || !currentPage.showsEmptyState()) {
            if (::emptyState.isInitialized) emptyState.visibility = View.GONE
            return
        }
        val page = currentPage
        emptyState.setContent(page.emptyTitle, page.emptyMessage)
        emptyState.setArtworkResource(page.emptyArtworkRes)
        val reservedRows = maxOf(0, page.emptyAfterRows)
        val top = dp(reservedRows * MusicListView.ROW_HEIGHT_DP) +
            maxOf(0, reservedRows - 1) * list.dividerHeight
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        params.topMargin = top
        emptyState.layoutParams = params
        emptyState.visibility = View.VISIBLE
    }

    private fun showPage(page: Page) {
        if (searchOpen) closeSearch(false)
        currentPage = page
        visibleRows = page.rows
        bindAlbumHeader(page)
        trackpadPress.cancel()
        list.notifyRowsChanged()
        updateEmptyState()
        list.post {
            if (currentPage === page && !searchOpen && !isFinishing && !isDestroyed) {
                list.requestFocus()
                list.setSelectionFromTop(maxOf(0, page.selected), page.selectedTop)
                list.refreshSelection()
            }
        }
    }

    private fun openSearch() {
        if (searchOpen) return
        searchOriginalPosition = list.selectedItemPosition
        searchOriginalTop = list.selectedView?.top ?: 0
        searchOpen = true
        pendingMenu = -1
        searchBar.visibility = View.VISIBLE
        updateEmptyState()
        ensureBackCallback()
    }

    private fun closeSearch(restorePosition: Boolean) {
        if (!searchOpen) return
        searchOpen = false
        searchBar.visibility = View.GONE
        noSearchResults.visibility = View.GONE
        searchInput.setText("")
        visibleRows = currentPage.rows
        list.notifyRowsChanged()
        updateEmptyState()
        if (backStack.isEmpty() && !nowPlayingVisible) unregisterNestedBack()
        if (restorePosition) {
            val page = currentPage
            val index = searchOriginalPosition
            val top = searchOriginalTop
            list.post {
                if (!searchOpen && !nowPlayingVisible && currentPage === page &&
                    !isFinishing && !isDestroyed) {
                    list.requestFocus()
                    list.setSelectionFromTop(maxOf(0, index), top)
                    list.refreshSelection()
                }
            }
        }
    }

    private fun updateSearchResults() {
        if (!searchOpen) return
        val term = searchInput.text.toString().trim().lowercase(Locale.ROOT)
        visibleRows = MusicLibraryPageRows.searchRows(
            term, currentPage === rootPage, currentPage.rows, localSongs, loading
        )
        noSearchResults.visibility = if (term.isNotEmpty() && visibleRows.isEmpty())
            View.VISIBLE else View.GONE
        list.notifyRowsChanged()
        list.setSelectionFromTop(0, 0)
        list.refreshSelection()
    }

    /**
     * Q25 physical-keyboard type-to-search without stealing ListView focus.
     * DPAD, ALT/Shift and system modifier shortcuts retain original behavior.
     */
    private fun handleSearchKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (searchOpen && (code == KeyEvent.KEYCODE_DEL ||
                code == KeyEvent.KEYCODE_FORWARD_DEL)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val value = searchInput.text
                val length = value.length
                var start = maxOf(0, searchInput.selectionStart)
                var end = maxOf(0, searchInput.selectionEnd)
                start = minOf(start, length)
                end = minOf(end, length)
                if (start != end) {
                    value.delete(minOf(start, end), maxOf(start, end))
                } else if (code == KeyEvent.KEYCODE_DEL && start > 0) {
                    val from = Character.offsetByCodePoints(value.toString(), start, -1)
                    value.delete(from, start)
                } else if (code == KeyEvent.KEYCODE_FORWARD_DEL && start < length) {
                    val to = Character.offsetByCodePoints(value.toString(), start, 1)
                    value.delete(start, to)
                }
            }
            return true
        }
        if (event.metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON) != 0) {
            return false
        }
        val character = event.unicodeChar
        if (!Character.isValidCodePoint(character) || character < 32 ||
            Character.isISOControl(character)) return false
        if (!searchOpen && Character.isWhitespace(character)) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            openSearch()
            val value = searchInput.text
            val length = value.length
            val start = maxOf(0, minOf(length, searchInput.selectionStart))
            val end = maxOf(0, minOf(length, searchInput.selectionEnd))
            val from = minOf(start, end)
            value.replace(from, maxOf(start, end), String(Character.toChars(character)))
            searchInput.setSelection(from + Character.charCount(character))
        }
        return true
    }

    private fun ensureBackCallback() {
        if (nestedBackCallback != null) return
        val callback = OnBackInvokedCallback { navigateBack() }
        nestedBackCallback = callback
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback
        )
    }

    private fun navigateBack() {
        if (nowPlayingVisible) {
            closeNowPlaying()
            return
        }
        if (searchOpen) {
            closeSearch(true)
            return
        }
        if (backStack.isEmpty()) return
        val previous = backStack.pop()
        if (backStack.isEmpty()) unregisterNestedBack()
        showPage(previous)
    }

    private fun unregisterNestedBack() {
        val callback = nestedBackCallback ?: return
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        nestedBackCallback = null
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (nowPlayingVisible || searchOpen || backStack.isNotEmpty()) navigateBack()
        else super.onBackPressed()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (!TrackpadPressHandler.isConfirmKey(code) &&
            event.action == KeyEvent.ACTION_DOWN) {
            trackpadPress.cancel()
        }
        if (TrackpadPressHandler.isConfirmKey(code)) return trackpadPress.handle(event)
        if (nowPlayingVisible) {
            if (code == KeyEvent.KEYCODE_DPAD_UP ||
                code == KeyEvent.KEYCODE_DPAD_DOWN ||
                code == KeyEvent.KEYCODE_DPAD_LEFT ||
                code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.action == KeyEvent.ACTION_DOWN) nowPlaying?.directional(code)
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        if (handleSearchKey(event)) return true
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            return list.handleVerticalKey(event)
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) trackpadPress.cancel()
        return super.dispatchTouchEvent(event)
    }

    override fun onStop() {
        trackpadPress.cancel()
        propertiesRequestSerial++
        deleteConfirmationSerial++
        contextMenuDialog?.dismiss()
        contextMenuDialog = null
        propertiesDialog?.dismiss()
        propertiesDialog = null
        buttonPopupDialog?.dismiss()
        buttonPopupDialog = null
        if (isFinishing) playback.pauseForExit()
        playback.disconnect()
        super.onStop()
    }

    override fun onDestroy() {
        unregisterNestedBack()
        nowPlaying?.dispose()
        playback.disconnect()
        list.dispose()
        localSource.close()
        folderBrowser.close()
        trackPropertiesReader.close()
        trackDeletion.close()
        playlists.close()
        playlistTransfer.close()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) trackpadPress.cancel()
    }
}
