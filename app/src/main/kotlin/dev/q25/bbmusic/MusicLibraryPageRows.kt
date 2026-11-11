package dev.q25.bbmusic

import java.util.Locale

/**
 * App-specific row construction for the Music Activity.
 *
 * The native Activity continues to own page navigation, searching/editing
 * events, selected index, ListView refresh and focus. Never mutate source
 * page rows while producing a filtered search view.
 */
internal object MusicLibraryPageRows {
    @JvmStatic
    fun settingsRows(): ArrayList<MusicActivityModels.MenuRow> = arrayListOf(
        MusicActivityModels.MenuRow.settingsHeader("Music library"),
        MusicActivityModels.MenuRow.setting("Music Folders"),
        MusicActivityModels.MenuRow.setting("Refresh library"),
        MusicActivityModels.MenuRow.setting("Full rescan"),
        MusicActivityModels.MenuRow.setting("Auto refresh on launch")
    )

    @JvmStatic
    fun songRows(songs: List<LocalMusicSource.Song>): ArrayList<MusicActivityModels.MenuRow> {
        val rows = ArrayList<MusicActivityModels.MenuRow>(songs.size)
        for (song in songs) rows.add(MusicActivityModels.MenuRow.song(song))
        if (rows.isEmpty()) rows.add(MusicActivityModels.MenuRow.message("No local music found"))
        return rows
    }

    @JvmStatic
    fun groupedRows(
        songs: List<LocalMusicSource.Song>,
        category: Int
    ): ArrayList<MusicActivityModels.MenuRow> {
        val groups = MusicLibraryGroupPolicy.groups(songs, category)
        val rows = ArrayList<MusicActivityModels.MenuRow>(groups.size)
        for (group in groups) {
            rows.add(MusicActivityModels.MenuRow.group(group.label, category, group.songs))
        }
        if (rows.isEmpty()) rows.add(MusicActivityModels.MenuRow.message("No local music found"))
        return rows
    }

    /**
     * The Activity supplies a trimmed Locale.ROOT-lowercased search term.
     * An empty term MUST return the existing page list object, rather than
     * a copy; active page state and selection handling remain native.
     */
    @JvmStatic
    fun searchRows(
        term: String,
        rootPage: Boolean,
        pageRows: List<MusicActivityModels.MenuRow>,
        localSongs: List<LocalMusicSource.Song>?,
        loading: Boolean
    ): List<MusicActivityModels.MenuRow> {
        if (term.isEmpty()) return pageRows
        val matches = ArrayList<MusicActivityModels.MenuRow>()
        if (rootPage) {
            // Root queries only the authorized indexed song snapshot.
            if (localSongs != null) {
                for (song in localSongs) {
                    if (MusicLibraryGroupPolicy.songMatches(song, term)) {
                        matches.add(MusicActivityModels.MenuRow.song(song))
                    }
                }
            }
            if (localSongs == null && loading) {
                matches.add(MusicActivityModels.MenuRow.message("Reading selected music folders..."))
            }
        } else {
            for (row in pageRows) {
                if (row.kind == MusicActivityModels.ROW_MESSAGE ||
                    row.kind == MusicActivityModels.ROW_SETTINGS_HEADER
                ) continue
                if (row.title.lowercase(Locale.ROOT).contains(term) ||
                    (row.kind == MusicActivityModels.ROW_SONG &&
                        MusicLibraryGroupPolicy.songMatches(row.song!!, term))
                ) {
                    matches.add(row)
                }
            }
        }
        return matches
    }
}
