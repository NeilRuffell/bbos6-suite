package dev.q25.bbmusic

import android.net.Uri

/**
 * Music-specific row presentation values for the canonical native ListView.
 * Shared BBOS6 Compose helpers still own actual styling, touch and focus.
 * No Activity lifecycle, page navigation, media control or SAF access here.
 */
internal object MusicLibraryRowPolicy {
    private const val MENU_FOLDERS = 7

    /** Canonical BBOS6 asset mappings, including indexed Add Playlist 335.png. */
    @JvmStatic
    fun menuIconForRow(row: MusicActivityModels.MenuRow): Int {
        when (row.kind) {
            MusicActivityModels.ROW_NOW_PLAYING -> return R.drawable.bbos6_menu_now_playing // 341.png
            MusicActivityModels.ROW_PLAYLIST -> return R.drawable.bbos6_menu_playlists // 337.png
            MusicActivityModels.ROW_PLAYLIST_ACTION ->
                return if (row.playlistAction == "choose-add")
                    R.drawable.bbos6_menu_add_playlist // 335.png
                else 0
            MusicActivityModels.ROW_FOLDER,
            MusicActivityModels.ROW_BROWSER_ROOT,
            MusicActivityModels.ROW_BROWSER_DIRECTORY,
            MusicActivityModels.ROW_BROWSER_UNAVAILABLE ->
                return R.drawable.bbos6_menu_folders // 112.png
        }
        if (row.kind != MusicActivityModels.ROW_MENU) return 0
        return when (row.menuIndex) {
            0 -> R.drawable.bbos6_menu_all_songs       // 350.png
            1 -> R.drawable.bbos6_menu_artists         // 345.png
            2 -> R.drawable.bbos6_menu_albums          // 351.png
            3 -> R.drawable.bbos6_menu_genres          // 348.png
            4 -> R.drawable.bbos6_menu_playlists       // 337.png
            5 -> R.drawable.bbos6_menu_shuffle_songs   // 339.png
            6 -> R.drawable.bbos6_menu_settings        // 3412.png
            MENU_FOLDERS -> R.drawable.bbos6_menu_folders // 112.png
            else -> 0
        }
    }

    @JvmStatic
    fun childCountLabel(
        row: MusicActivityModels.MenuRow,
        localSongCount: Int?,
        loading: Boolean,
        categoryCounts: IntArray,
        playlistCount: Int
    ): String? {
        if (row.kind == MusicActivityModels.ROW_GROUP) {
            return MusicLibraryGroupPolicy.countLabel(row.songs!!.size, "Song", "Songs")
        }
        if (row.kind != MusicActivityModels.ROW_MENU) return null
        if (row.menuIndex < 4) {
            if (localSongCount == null) return if (loading) "Loading..." else "Unavailable"
            val count = if (row.menuIndex == 0) localSongCount
                        else categoryCounts[row.menuIndex]
            return if (row.menuIndex == 0)
                MusicLibraryGroupPolicy.countLabel(count, "Song", "Songs")
            else MusicLibraryGroupPolicy.countLabel(count, "Item", "Items")
        }
        if (row.menuIndex == 4) {
            return MusicLibraryGroupPolicy.countLabel(playlistCount, "Item", "Items")
        }
        if (row.menuIndex == MENU_FOLDERS) return null
        // Shuffle Songs and Settings have no child-count subtitle.
        return null
    }

    /**
     * Produces the original immutable row snapshot. The Activity passes
     * its already-authorized artwork index and current page flags. Native
     * ListView remains the only adapter/scroll/navigation owner.
     */
    @JvmStatic
    fun presentation(
        row: MusicActivityModels.MenuRow,
        pageHasAlbumHeader: Boolean,
        artworkSources: Map<String, List<Uri>>,
        localSongCount: Int?,
        loading: Boolean,
        categoryCounts: IntArray,
        playlistCount: Int,
        autoRefreshOnLaunch: Boolean
    ): MusicListView.RowData {
        val albumTrack = pageHasAlbumHeader && row.kind == MusicActivityModels.ROW_SONG
        val hasArtwork = !albumTrack &&
            (row.kind == MusicActivityModels.ROW_GROUP ||
                row.kind == MusicActivityModels.ROW_SONG)
        val trackNumber = if (albumTrack) {
            val number = row.song!!.trackNumber
            if (number > 0) number.toString() else "--"
        } else null
        val albumKey = if (hasArtwork) MusicLibraryGroupPolicy.albumKeyForRow(row) else null
        val candidates = if (albumKey == null) null else artworkSources[albumKey]
        val albumFallback =
            row.kind == MusicActivityModels.ROW_GROUP && row.groupCategory == 2
        val subtitle = when (row.kind) {
            MusicActivityModels.ROW_SONG -> row.song!!.artist
            MusicActivityModels.ROW_PLAYLIST ->
                MusicLibraryGroupPolicy.countLabel(row.groupCategory, "Song", "Songs")
            MusicActivityModels.ROW_PLAYLIST_MISSING -> "Track unavailable"
            MusicActivityModels.ROW_FOLDER -> row.folderLocation
            MusicActivityModels.ROW_BROWSER_ROOT ->
                MusicFolders.displayLocation(row.folderLocation)
            MusicActivityModels.ROW_BROWSER_DIRECTORY -> "Folder"
            MusicActivityModels.ROW_BROWSER_UNAVAILABLE -> "Access unavailable"
            MusicActivityModels.ROW_BROWSER_FILE -> "Not in music library"
            MusicActivityModels.ROW_SETTING ->
                if (row.title == "Auto refresh on launch") {
                    if (autoRefreshOnLaunch) "On" else "Off"
                } else {
                    childCountLabel(row, localSongCount, loading, categoryCounts, playlistCount)
                }
            else -> childCountLabel(row, localSongCount, loading, categoryCounts, playlistCount)
        }
        val duration = if (
            row.kind == MusicActivityModels.ROW_SONG && row.song!!.durationMs > 0L
        ) MediaSeekPolicy.formatTime(row.song!!.durationMs) else null

        return MusicListView.RowData(
            row.title,
            subtitle,
            row.kind != MusicActivityModels.ROW_MESSAGE &&
                row.kind != MusicActivityModels.ROW_SETTINGS_HEADER &&
                row.kind != MusicActivityModels.ROW_BROWSER_FILE &&
                row.kind != MusicActivityModels.ROW_BROWSER_UNAVAILABLE,
            hasArtwork,
            albumFallback,
            menuIconForRow(row),
            trackNumber,
            duration,
            if (albumKey == null) null else "album:$albumKey",
            candidates,
            row.kind == MusicActivityModels.ROW_FOLDER,
            row.kind == MusicActivityModels.ROW_SETTINGS_HEADER
        )
    }
}
