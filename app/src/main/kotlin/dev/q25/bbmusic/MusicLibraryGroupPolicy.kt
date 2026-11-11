package dev.q25.bbmusic

import java.util.Locale

/**
 * App-owned library presentation rules extracted from MusicActivity.java.
 * No SAF traversal, playback, Android View or navigation ownership.
 * Group keys and ordering deliberately preserve the previous Java policies.
 */
internal object MusicLibraryGroupPolicy {
    class Group(@JvmField val label: String) {
        @JvmField val songs = ArrayList<LocalMusicSource.Song>()
    }

    @JvmStatic
    fun groups(songs: List<LocalMusicSource.Song>, category: Int): List<Group> {
        val indexed = LinkedHashMap<String, Group>()
        for (song in songs) {
            // A missing album tag does not produce a browsable album.
            if (category == 2 && !MusicAlbumTag.present(song.album)) continue
            val label = when (category) {
                1 -> song.artist
                2 -> song.album
                else -> song.genre
            }
            val key = groupKey(song, category)
            val group = indexed.getOrPut(key) { Group(label) }
            group.songs.add(song)
        }
        return indexed.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    @JvmStatic
    fun groupKey(song: LocalMusicSource.Song, category: Int): String = when (category) {
        1 -> song.artist.lowercase(Locale.ROOT)
        2 -> if (song.albumId >= 0L) "album:" + song.albumId
             else "album:" + song.album + ":" + song.artist
        else -> song.genre.lowercase(Locale.ROOT)
    }

    @JvmStatic
    fun artworkAlbumKey(song: LocalMusicSource.Song): String =
        if (!MusicAlbumTag.present(song.album)) "untagged:" + song.uri
        else groupKey(song, 2)

    @JvmStatic
    fun countLabel(count: Int, singular: String, plural: String): String =
        "$count " + if (count == 1) singular else plural

    /** Caller supplies the already-trimmed, Locale.ROOT lower-case search term. */
    @JvmStatic
    fun songMatches(song: LocalMusicSource.Song, term: String): Boolean =
        song.title.lowercase(Locale.ROOT).contains(term) ||
            song.artist.lowercase(Locale.ROOT).contains(term) ||
            song.album.lowercase(Locale.ROOT).contains(term) ||
            song.genre.lowercase(Locale.ROOT).contains(term)
    // First album for artist/genre artwork: case-insensitive album, exact
    // album, case-insensitive artist, exact artist, then exact URI.
    private val firstAlbumOrder = compareBy<LocalMusicSource.Song, String>(
        String.CASE_INSENSITIVE_ORDER
    ) { it.album }
        .thenBy { it.album }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.artist }
        .thenBy { it.artist }
        .thenBy { it.uri.toString() }

    /**
     * Matches the original Activity album-art policy without invoking any
     * storage APIs. Only the caller-selected song snapshot is considered.
     */
    @JvmStatic
    fun albumKeyForRow(row: MusicActivityModels.MenuRow): String? {
        if (row.kind == MusicActivityModels.ROW_SONG) {
            return artworkAlbumKey(row.song!!)
        }
        if (row.kind != MusicActivityModels.ROW_GROUP) return null
        val songs = row.songs?.takeIf { it.isNotEmpty() } ?: return null
        return if (row.groupCategory == 2) artworkAlbumKey(songs[0])
        else artworkAlbumKey(java.util.Collections.min(songs, firstAlbumOrder))
    }

    private val artworkTrackOrder = compareBy<LocalMusicSource.Song> {
        if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE
    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        .thenBy { it.uri.toString() }

    /**
     * Preserves insertion-ordered album mapping, track-number ordering and
     * immutable per-album candidate lists from the former Java Activity.
     */
    @JvmStatic
    fun indexAlbumArtworkSources(
        songs: List<LocalMusicSource.Song>?
    ): Map<String, List<android.net.Uri>> {
        if (songs.isNullOrEmpty()) return java.util.Collections.emptyMap()
        val grouped = LinkedHashMap<String, ArrayList<LocalMusicSource.Song>>()
        for (song in songs) {
            grouped.getOrPut(artworkAlbumKey(song)) { ArrayList() }.add(song)
        }
        val indexed = LinkedHashMap<String, List<android.net.Uri>>()
        for ((key, candidates) in grouped) {
            candidates.sortWith(artworkTrackOrder)
            val uris = ArrayList<android.net.Uri>(candidates.size)
            for (song in candidates) uris.add(song.uri)
            indexed[key] = java.util.Collections.unmodifiableList(uris)
        }
        return indexed
    }

    /**
     * Writes the same four counters without affecting the Activity's
     * native list selection, scroll position or display timing.
     */
    @JvmStatic
    fun fillCategoryCounts(songs: List<LocalMusicSource.Song>, counts: IntArray) {
        require(counts.size >= 4) { "Expected four music category counters" }
        counts[0] = songs.size
        for (category in 1..3) {
            val keys = HashSet<String>()
            for (song in songs) {
                if (category == 2 && !MusicAlbumTag.present(song.album)) continue
                keys.add(groupKey(song, category))
            }
            counts[category] = keys.size
        }
    }

}
