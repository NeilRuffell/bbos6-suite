package dev.q25.bbmusic

import android.net.Uri
import java.util.ArrayList
import java.util.Collections

/**
 * App-owned native ListView row and page state.
 *
 * Only MusicActivity owns navigation and selected-page state. These data
 * carriers preserve the old Java field and static-factory ABI while the
 * Activity itself is migrated to Kotlin in verified increments.
 */
internal object MusicActivityModels {
    const val ROW_MENU = 0
    const val ROW_SONG = 1
    const val ROW_GROUP = 2
    const val ROW_MESSAGE = 3
    const val ROW_SETTING = 4
    const val ROW_FOLDER = 5
    const val ROW_NOW_PLAYING = 6
    const val ROW_SETTINGS_HEADER = 7
    const val ROW_BROWSER_DIRECTORY = 8
    const val ROW_BROWSER_FILE = 9
    const val ROW_BROWSER_UNAVAILABLE = 10
    const val ROW_BROWSER_ROOT = 11
    const val ROW_PLAYLIST = 12
    const val ROW_PLAYLIST_ACTION = 13
    const val ROW_PLAYLIST_MISSING = 14

    class MenuRow private constructor(
        @JvmField val title: String,
        @JvmField val kind: Int,
        @JvmField val menuIndex: Int,
        @JvmField val groupCategory: Int,
        @JvmField val songs: List<LocalMusicSource.Song>?,
        @JvmField val song: LocalMusicSource.Song?,
        @JvmField val folderTree: Uri?,
        @JvmField val folderLocation: String?,
        @JvmField val playlistId: Long = -1L,
        @JvmField val playlistEntryId: Long = -1L,
        @JvmField val playlistAction: String? = null
    ) {
        companion object {
            @JvmStatic
            fun root(name: String, index: Int): MenuRow =
                MenuRow(name, ROW_MENU, index, -1, null, null, null, null)

            @JvmStatic
            fun nowPlaying(): MenuRow =
                MenuRow("Now Playing...", ROW_NOW_PLAYING, -1, -1, null, null, null, null)

            @JvmStatic
            fun group(
                name: String,
                category: Int,
                songs: List<LocalMusicSource.Song>
            ): MenuRow =
                MenuRow(name, ROW_GROUP, -1, category, songs, null, null, null)

            @JvmStatic
            fun song(song: LocalMusicSource.Song): MenuRow =
                MenuRow(song.title, ROW_SONG, -1, -1, null, song, null, null)

            @JvmStatic
            fun playlist(summary: MusicLibraryDatabase.PlaylistSummary): MenuRow =
                MenuRow(
                    summary.name, ROW_PLAYLIST, -1, summary.songCount,
                    null, null, null, null, summary.id
                )

            @JvmStatic
            fun playlistAction(title: String, action: String, playlistId: Long): MenuRow =
                MenuRow(
                    title, ROW_PLAYLIST_ACTION, -1, -1,
                    null, null, null, null, playlistId, -1L, action
                )

            @JvmStatic
            fun playlistSong(
                song: LocalMusicSource.Song,
                playlistId: Long,
                entryId: Long
            ): MenuRow =
                MenuRow(
                    song.title, ROW_SONG, -1, -1,
                    null, song, null, null, playlistId, entryId
                )

            @JvmStatic
            fun playlistMissing(title: String, playlistId: Long, entryId: Long): MenuRow =
                MenuRow(
                    title, ROW_PLAYLIST_MISSING, -1, -1,
                    null, null, null, null, playlistId, entryId
                )

            @JvmStatic
            fun message(message: String): MenuRow =
                MenuRow(message, ROW_MESSAGE, -1, -1, null, null, null, null)

            @JvmStatic
            fun setting(title: String): MenuRow =
                MenuRow(title, ROW_SETTING, -1, -1, null, null, null, null)

            @JvmStatic
            fun settingsHeader(title: String): MenuRow =
                MenuRow(title, ROW_SETTINGS_HEADER, -1, -1, null, null, null, null)

            @JvmStatic
            fun folder(name: String, location: String, tree: Uri): MenuRow =
                MenuRow(name, ROW_FOLDER, -1, -1, null, null, tree, location)

            @JvmStatic
            fun browserRoot(name: String, tree: Uri, documentId: String): MenuRow =
                MenuRow(name, ROW_BROWSER_ROOT, -1, -1, null, null, tree, documentId)

            @JvmStatic
            fun browserDirectory(name: String, tree: Uri, documentId: String): MenuRow =
                MenuRow(name, ROW_BROWSER_DIRECTORY, -1, -1, null, null, tree, documentId)

            @JvmStatic
            fun browserUnavailable(name: String): MenuRow =
                MenuRow(name, ROW_BROWSER_UNAVAILABLE, -1, -1, null, null, null, null)

            @JvmStatic
            fun browserFile(filename: String, song: LocalMusicSource.Song?): MenuRow =
                MenuRow(
                    filename, if (song == null) ROW_BROWSER_FILE else ROW_SONG,
                    -1, -1, null, song, null, null
                )
        }
    }

    class Page @JvmOverloads constructor(
        @JvmField var name: String,
        @JvmField val rows: MutableList<MenuRow>,
        @JvmField val albumGroup: MenuRow? = null
    ) {
        // Only filesystem pages have a browser tree and cached entries.
        @JvmField var browserTree: Uri? = null
        @JvmField var browserEntries: List<MusicFolderBrowser.Entry>? = null
        @JvmField var playlistDirectory: Boolean = false
        @JvmField var playlistId: Long = -1L
        @JvmField var playlistPickerId: Long = -1L
        @JvmField var emptyTitle: String? = null
        @JvmField var emptyMessage: String? = null
        @JvmField var emptyArtworkRes: Int = 0
        @JvmField var emptyAfterRows: Int = 0
        @JvmField var selected: Int = 0
        @JvmField var selectedTop: Int = 0

        fun setEmptyState(title: String, message: String?, afterRows: Int) {
            emptyTitle = title
            emptyMessage = message
            emptyAfterRows = afterRows
            // Original numbered artwork is only supplied once identified.
            emptyArtworkRes = 0
        }
    }

    /** Immutable snapshot of a user-confirmed file deletion operation. */
    class DeleteRequest(
        @JvmField val page: Page,
        songs: List<LocalMusicSource.Song>,
        @JvmField val description: String
    ) {
        @JvmField val songs: List<LocalMusicSource.Song> =
            Collections.unmodifiableList(ArrayList(songs))
    }
}
