package dev.q25.bbmusic

import android.content.Context
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
    /**
     * Home Folders roots: show saved SAF entries even when grants have been
     * revoked. Readability always comes from MusicFolders.selectedTrees().
     */
    @JvmStatic
    fun folderRootRows(context: Context): ArrayList<MusicActivityModels.MenuRow> {
        val rows = ArrayList<MusicActivityModels.MenuRow>()
        val authorized = MusicFolders.selectedTrees(context).mapTo(HashSet()) { it.toString() }
        val saved = MusicFolders.savedTrees(context)
        for (tree in saved) {
            val name = MusicFolders.displayName(context, tree)
            if (!MusicFilePolicy.isVisibleDirectory(name)) continue
            if (tree.toString() in authorized) {
                rows.add(
                    MusicActivityModels.MenuRow.browserRoot(
                        name, tree, MusicFolders.documentId(tree)
                    )
                )
            } else {
                rows.add(MusicActivityModels.MenuRow.browserUnavailable(name))
            }
        }
        if (rows.isEmpty()) {
            rows.add(
                MusicActivityModels.MenuRow.message(
                    if (saved.isEmpty()) "No folders selected" else "No visible folders"
                )
            )
        }
        return rows
    }

    /**
     * Settings > Music Folders keeps saved roots regardless of current read
     * authorization, preserving the original 'Access unavailable' subtitle.
     */
    @JvmStatic
    fun musicFolderSettingsRows(context: Context): ArrayList<MusicActivityModels.MenuRow> {
        val rows = arrayListOf(MusicActivityModels.MenuRow.setting("Add folders"))
        val authorized = MusicFolders.selectedTrees(context).mapTo(HashSet()) { it.toString() }
        for (tree in MusicFolders.savedTrees(context)) {
            val location = MusicFolders.displayLocation(MusicFolders.documentId(tree))
            rows.add(
                MusicActivityModels.MenuRow.folder(
                    MusicFolders.displayName(context, tree),
                    if (tree.toString() in authorized) location
                    else "Access unavailable - $location",
                    tree
                )
            )
        }
        return rows
    }

    /**
     * View-only representation of one already-authorized SAF folder response.
     * Does not enumerate SAF, grant permissions or change the selected tree.
     */
    @JvmStatic
    fun browserRows(
        page: MusicActivityModels.Page,
        localSongs: List<LocalMusicSource.Song>?
    ): ArrayList<MusicActivityModels.MenuRow> {
        val rows = ArrayList<MusicActivityModels.MenuRow>()
        val entries = page.browserEntries ?: return rows
        val indexed = HashMap<String, LocalMusicSource.Song>()
        if (localSongs != null) {
            for (song in localSongs) {
                if (song.sourceTree == page.browserTree) {
                    indexed[song.uri.toString()] = song
                }
            }
        }
        for (entry in entries) {
            if (entry.directory) {
                rows.add(
                    MusicActivityModels.MenuRow.browserDirectory(
                        entry.name, page.browserTree!!, entry.documentId
                    )
                )
            } else {
                rows.add(
                    MusicActivityModels.MenuRow.browserFile(
                        entry.name, indexed[entry.uri.toString()]
                    )
                )
            }
        }
        if (rows.isEmpty()) rows.add(MusicActivityModels.MenuRow.message("Empty folder"))
        return rows
    }

}
