package dev.q25.bbmusic

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * App-private ordered playlist repository. Existing Room schema, transactions,
 * URI allowlist, callback ordering and single-thread executor are unchanged.
 * No files are copied or moved; playlist entries are exact SAF URI strings.
 */
@JvmSuppressWildcards
internal class MusicPlaylists(context: Context) {
    fun interface Callback<T> {
        fun onComplete(value: T?, error: String?)
    }

    private companion object {
        const val TAG = "BBMusic-Playlists"

        fun validName(raw: String?): String {
            val name = raw?.trim { it <= ' ' } ?: ""
            require(name.isNotEmpty()) { "Playlist name required" }
            require(name.length <= 128) { "Playlist name is too long" }
            return name
        }
    }

    private val context = context.applicationContext
    private val database = MusicLibraryDatabase.open(this.context)
    private val dao = database.playlistDao()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    private fun <T> submit(operation: Callable<T>, callback: Callback<T>?) {
        if (closed) return
        worker.execute {
            var value: T? = null
            var error: String? = null
            try {
                value = operation.call()
            } catch (exception: Exception) {
                Log.e(TAG, "Playlist operation failed", exception)
                error = exception.message ?: "Playlist operation failed"
            }
            val result = value
            val failure = error
            if (callback != null) {
                main.post {
                    if (!closed) callback.onComplete(result, failure)
                }
            }
        }
    }

    fun list(callback: Callback<List<MusicLibraryDatabase.PlaylistSummary>>?) {
        submit(Callable { dao.playlists() }, callback)
    }

    fun entries(
        playlistId: Long,
        callback: Callback<List<MusicLibraryDatabase.PlaylistEntryRow>>?
    ) {
        submit(Callable { dao.entries(playlistId) }, callback)
    }

    private fun availableName(requested: String?): String {
        val base = validName(requested)
        var candidate = base
        var suffix = 2
        while (dao.nameCount(candidate, -1L) > 0) {
            val ending = " (" + suffix++ + ")"
            candidate = base.substring(0, minOf(base.length, 128 - ending.length)) +
                ending
        }
        return candidate
    }

    fun create(name: String?, callback: Callback<Long>?) {
        submit(Callable {
            val verified = validName(name)
            if (dao.nameCount(verified, -1L) > 0) {
                throw IllegalArgumentException("A playlist with that name exists")
            }
            val row = MusicLibraryDatabase.PlaylistRow()
            row.name = verified
            dao.create(row)
        }, callback)
    }

    fun rename(id: Long, name: String?, callback: Callback<Boolean>?) {
        submit(Callable {
            val verified = validName(name)
            if (dao.nameCount(verified, id) > 0) {
                throw IllegalArgumentException("A playlist with that name exists")
            }
            dao.rename(id, verified) != 0
        }, callback)
    }

    fun delete(id: Long, callback: Callback<Boolean>?) {
        submit(Callable { dao.delete(id) != 0 }, callback)
    }

    private fun authorized(songs: List<LocalMusicSource.Song?>?): List<LocalMusicSource.Song> {
        val trees = HashSet<Uri>(MusicFolders.selectedTrees(context))
        val accepted = ArrayList<LocalMusicSource.Song>()
        if (songs == null) return accepted
        for (song in songs) {
            if (song != null && song.uri != null &&
                song.sourceTree in trees && song.uri.scheme == "content") {
                accepted.add(song)
            }
        }
        return accepted
    }

    private fun insertSongs(
        playlistId: Long, songs: List<LocalMusicSource.Song?>?
    ) {
        if (dao.playlist(playlistId) == null) {
            throw IllegalArgumentException("Playlist no longer exists")
        }
        val verified = authorized(songs)
        if (songs == null || verified.size != songs.size) {
            throw SecurityException("Music folder access changed; refresh your library")
        }
        var position = dao.nextPosition(playlistId)
        for (song in verified) {
            val row = MusicLibraryDatabase.PlaylistEntryRow()
            row.playlistId = playlistId
            row.uri = song.uri.toString()
            row.title = song.title
            row.position = position++
            dao.add(row)
        }
    }

    /** Room transaction keeps all writes or no writes on grant failure. */
    fun add(
        playlistId: Long, songs: List<LocalMusicSource.Song?>?,
        callback: Callback<Boolean>?
    ) {
        submit(Callable {
            database.runInTransaction(Runnable { insertSongs(playlistId, songs) })
            true
        }, callback)
    }

    /** Import creates a uniquely named playlist atomically with its entries. */
    fun importSongs(
        suggestedName: String?, songs: List<LocalMusicSource.Song?>?,
        callback: Callback<Long>?
    ) {
        submit(Callable {
            var created = -1L
            database.runInTransaction(Runnable {
                val playlist = MusicLibraryDatabase.PlaylistRow()
                playlist.name = availableName(suggestedName)
                created = dao.create(playlist)
                insertSongs(created, songs)
            })
            created
        }, callback)
    }

    fun remove(playlistId: Long, entryId: Long, callback: Callback<Boolean>?) {
        submit(Callable { dao.remove(playlistId, entryId) != 0 }, callback)
    }

    /** Swap stable positions, not row identities, inside one transaction. */
    fun move(
        playlistId: Long, entryId: Long, direction: Int,
        callback: Callback<Boolean>?
    ) {
        submit(Callable {
            if (direction != -1 && direction != 1) {
                false
            } else {
                val rows = dao.entries(playlistId)
                var moved = false
                for (i in rows.indices) {
                    if (rows[i].id != entryId) continue
                    val target = i + direction
                    if (target !in rows.indices) break
                    val first = rows[i]
                    val second = rows[target]
                    database.runInTransaction(Runnable {
                        dao.setPosition(first.id, second.position)
                        dao.setPosition(second.id, first.position)
                    })
                    moved = true
                    break
                }
                moved
            }
        }, callback)
    }

    fun close() {
        closed = true
        worker.execute { database.close() }
        worker.shutdown()
    }
}
