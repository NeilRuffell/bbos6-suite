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
}
