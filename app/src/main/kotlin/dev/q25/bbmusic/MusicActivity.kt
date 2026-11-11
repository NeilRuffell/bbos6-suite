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

