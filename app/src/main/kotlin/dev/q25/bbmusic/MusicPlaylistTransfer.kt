package dev.q25.bbmusic

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.concurrent.Executors

/**
 * App-only Android document-picker M3U8 I/O.
 *
 * Import resolves paths exclusively to already indexed SAF songs supplied by
 * the caller. Export rejects missing or non-primary tracks before writing.
 * No raw filesystem file is opened or scanned by this class.
 */
@JvmSuppressWildcards
internal class MusicPlaylistTransfer(context: Context) {
    fun interface Callback<T> {
        fun onComplete(value: T?, error: String?)
    }

    class Imported(
        @JvmField val songs: List<LocalMusicSource.Song>,
        @JvmField val unmatched: Int,
        @JvmField val total: Int
    )

    private companion object {
        const val TAG = "BBMusic-M3U8"
        const val MAX_BYTES = 2 * 1024 * 1024
    }

    private val resolver = context.applicationContext.contentResolver
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    // Android-provided primary-storage path, not a hard-coded "/storage".
    // Only used as an identifier for verified DocumentsProvider mappings.
    private val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
    @Volatile private var closed = false

    private fun pathFor(uri: Uri?): String? {
        if (uri == null || uri.authority != "com.android.externalstorage.documents") {
            return null
        }
        return try {
            val id = DocumentsContract.getDocumentId(uri)
            if (id == null || !id.startsWith("primary:")) return null
            val tail = id.substring("primary:".length)
            if (tail.isEmpty()) null else MusicPlaylistPaths.absolute(primaryRoot, tail)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun baseFor(document: Uri): String? {
        val full = pathFor(document) ?: return null
        return Paths.get(full).parent?.toString()
    }

    /** UTF-8 with optional BOM; maximum 2 MiB and 20,000 playable entries. */
    fun read(
        document: Uri,
        catalog: List<LocalMusicSource.Song>,
        callback: Callback<Imported>
    ) {
        worker.execute {
            try {
                val bytes = resolver.openInputStream(document)?.use { input ->
                    val buffer = ByteArrayOutputStream()
                    val part = ByteArray(8192)
                    while (true) {
                        val count = input.read(part)
                        if (count == -1) break
                        if (buffer.size() + count > MAX_BYTES) {
                            throw IllegalArgumentException("Playlist exceeds 2 MB")
                        }
                        buffer.write(part, 0, count)
                    }
                    buffer.toByteArray()
                } ?: throw IllegalStateException("Cannot open playlist")

                var data = String(bytes, StandardCharsets.UTF_8)
                if (data.startsWith("\uFEFF")) data = data.substring(1)
                val lookup = HashMap<String, LocalMusicSource.Song>()
                for (song in catalog) {
                    val key = pathFor(song.uri)
                    if (key != null) lookup[key] = song
                }
                val base = baseFor(document)
                val resolved = ArrayList<LocalMusicSource.Song>()
                var unmatched = 0
                var total = 0
                for (raw in data.split(Regex("\\r?\\n"))) {
                    val line = raw.trim { it <= ' ' }
                    if (line.isEmpty() || line.startsWith("#")) continue
                    if (++total > 20_000) {
                        throw IllegalArgumentException("Playlist exceeds 20,000 entries")
                    }
                    val path = MusicPlaylistPaths.resolve(primaryRoot, base, line)
                    val song = if (path == null) null else lookup[path]
                    if (song == null) unmatched++ else resolved.add(song)
                }
                val imported = Imported(resolved, unmatched, total)
                main.post {
                    if (!closed) callback.onComplete(imported, null)
                }
            } catch (exception: Exception) {
                Log.w(TAG, "Cannot import M3U8", exception)
                main.post {
                    if (!closed) {
                        callback.onComplete(
                            null, "Unable to import M3U8: " + exception.message
                        )
                    }
                }
            }
        }
    }

    /** Check all entries before showing the destination document picker. */
    fun checkExport(
        entries: List<MusicLibraryDatabase.PlaylistEntryRow>,
        catalog: List<LocalMusicSource.Song>,
        callback: Callback<Boolean>
    ) {
        worker.execute {
            val indexed = HashMap<String, LocalMusicSource.Song>()
            for (song in catalog) {
                indexed[song.uri.toString()] = song
            }
            var error: String? = null
            for (entry in entries) {
                val song = indexed[entry.uri]
                if (song == null || pathFor(song.uri) == null) {
                    error = "Cannot export: playlist contains unavailable " +
                        "or non-primary-storage tracks"
                    break
                }
            }
            val result = error
            main.post {
                if (!closed) callback.onComplete(result == null, result)
            }
        }
    }

    /** Write full M3U8 only if every entry is portable and available. */
    fun write(
        document: Uri,
        entries: List<MusicLibraryDatabase.PlaylistEntryRow>,
        catalog: List<LocalMusicSource.Song>,
        callback: Callback<Int>
    ) {
        worker.execute {
            try {
                val indexed = HashMap<String, LocalMusicSource.Song>()
                for (song in catalog) indexed[song.uri.toString()] = song

                val text = StringBuilder("#EXTM3U\n")
                var count = 0
                for (entry in entries) {
                    val song = indexed[entry.uri]
                    val path = song?.uri?.let(::pathFor)
                        ?: throw IllegalArgumentException(
                            "Playlist contains missing or non-primary-storage tracks"
                        )
                    val artist = song.artist.replace('\n', ' ').replace('\r', ' ')
                    val title = song.title.replace('\n', ' ').replace('\r', ' ')
                    text.append("#EXTINF:")
                        .append(if (song.durationMs > 0L) song.durationMs / 1000L else -1L)
                        .append(',').append(artist).append(" - ")
                        .append(title).append('\n')
                        .append(path).append('\n')
                    count++
                }
                resolver.openOutputStream(document, "wt")?.use { output ->
                    output.write(text.toString().toByteArray(StandardCharsets.UTF_8))
                } ?: throw IllegalStateException("Cannot write playlist")

                val written = count
                main.post {
                    if (!closed) callback.onComplete(written, null)
                }
            } catch (exception: Exception) {
                Log.w(TAG, "Cannot export M3U8", exception)
                main.post {
                    if (!closed) {
                        callback.onComplete(
                            null, "Unable to export M3U8: " + exception.message
                        )
                    }
                }
            }
        }
    }

    fun close() {
        closed = true
        worker.shutdown()
    }
}
