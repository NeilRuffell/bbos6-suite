package dev.q25.bbmusic

import android.content.Context
import android.database.Cursor
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import java.io.IOException
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Selected-folder SAF music index. Retains the v3 Room cache and Java data
 * contracts while replacing the original Java worker implementation.
 * Never enumerates MediaStore, storage roots or unselected documents.
 */
internal class LocalMusicSource(context: Context) {
    interface Listener {
        fun onLoaded(songs: List<Song>, summary: ScanSummary)
        fun onError(reason: String)
    }

    class ScanSummary(
        @JvmField val scanned: Boolean,
        @JvmField val full: Boolean,
        @JvmField val added: Int,
        @JvmField val rechecked: Int,
        @JvmField val removed: Int,
        @JvmField val reused: Int,
        @JvmField val total: Int
    ) {
        fun artworkMayHaveChanged(): Boolean =
            full || added > 0 || rechecked > 0 || removed > 0
        fun message(): String = when {
            full -> "Full rescan complete: " + total + " tracks"
            added == 0 && rechecked == 0 && removed == 0 ->
                "Library up to date: " + total + " tracks"
            else -> "Library refreshed: " + added + " added, " +
                rechecked + " rechecked, " + removed + " removed"
        }
    }

    class Song(
        @JvmField val id: Long,
        @JvmField val uri: Uri,
        @JvmField val sourceTree: Uri,
        @JvmField val title: String,
        @JvmField val artist: String,
        @JvmField val album: String,
        @JvmField val genre: String,
        @JvmField val artistId: Long,
        @JvmField val albumId: Long,
        @JvmField val durationMs: Long,
        @JvmField val trackNumber: Int
    )

    private class ScanResult {
        val songs = ArrayList<Song>()
        val changes = ArrayList<MusicLibraryDatabase.TrackRow>()
        val removed = ArrayList<String>()
        val retainedUris = HashSet<String>()
        val visitedDocuments = HashSet<String>()
        var added = 0
        var rechecked = 0
        var reused = 0
        fun summary(full: Boolean): ScanSummary =
            ScanSummary(true, full, added, rechecked, removed.size, reused, songs.size)
    }

    companion object {
        private const val TAG = "BBMusic-Local"
        private const val RULES = "verified-audio-v3-album-tags:"
        private val songOrder = compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) {
            it.title
        }.thenBy { it.uri.toString() }

        private fun fromRow(row: MusicLibraryDatabase.TrackRow): Song =
            Song(row.id, Uri.parse(row.uri), Uri.parse(row.sourceTree),
                row.title, row.artist, row.album, row.genre,
                row.artistId, row.albumId, row.durationMs, row.trackNumber)

        private fun trackRow(
            song: Song, filename: String?, size: Long, modified: Long
        ): MusicLibraryDatabase.TrackRow =
            MusicLibraryDatabase.TrackRow().apply {
                id = song.id
                uri = song.uri.toString()
                sourceTree = song.sourceTree.toString()
                title = song.title
                artist = song.artist
                album = song.album
                genre = song.genre
                artistId = song.artistId
                albumId = song.albumId
                durationMs = song.durationMs
                trackNumber = song.trackNumber
                fileName = filename ?: ""
                fileSize = size
                lastModified = modified
            }

        private fun keyFor(trees: List<Uri>): String {
            val text = StringBuilder(RULES)
            for (tree in trees.map(Uri::toString).sorted()) {
                text.append(tree.length).append(':').append(tree)
            }
            return text.toString()
        }

        private fun cursorLong(cursor: Cursor, index: Int): Long =
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L

        private fun tag(value: String?, fallback: String): String =
            value?.trim { it <= ' ' }?.takeIf { it.isNotEmpty() } ?: fallback

        private fun number(value: String?): Long =
            try { value?.toLong() ?: 0L }
            catch (_: NumberFormatException) { 0L }

        private fun trackNumber(value: String?): Int {
            if (value == null) return 0
            val slash = value.indexOf('/')
            val part = if (slash >= 0) value.substring(0, slash) else value
            return minOf(Int.MAX_VALUE.toLong(), number(part)).toInt()
        }
    }

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val database = MusicLibraryDatabase.open(appContext)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    @Volatile private var serial = 0

    fun load(listener: Listener) = request(false, false, listener)
    fun refresh(listener: Listener) = request(true, false, listener)
    fun fullRescan(listener: Listener) = request(true, true, listener)

    private fun request(scan: Boolean, full: Boolean, listener: Listener) {
        if (closed) return
        val id = ++serial
        worker.execute {
            try {
                val trees = MusicFolders.selectedTrees(appContext)
                val key = keyFor(trees)
                val dao = database.libraryDao()
                val scanRow = dao.scanState()
                val rows = dao.tracks()
                val verifyLegacy = scanRow == null ||
                    !scanRow.selectedTreesKey.startsWith(RULES)
                val songs: List<Song>
                val summary: ScanSummary

                if (!scan && scanRow != null && key == scanRow.selectedTreesKey) {
                    val allowed = trees.mapTo(HashSet<String>()) { it.toString() }
                    val cached = ArrayList<Song>()
                    for (row in rows) {
                        if (row.sourceTree !in allowed || !row.uri.startsWith("content:"))
                            continue
                        cached.add(fromRow(row))
                    }
                    cached.sortWith(songOrder)
                    songs = Collections.unmodifiableList(cached)
                    summary = ScanSummary(
                        false, false, 0, 0, 0, cached.size, cached.size
                    )
                    Log.i(TAG, "Loaded persistent music index: " + songs.size)
                } else {
                    val previous = HashMap<String, MusicLibraryDatabase.TrackRow>()
                    for (row in rows) previous[row.uri] = row
                    val result = scanSelected(id, trees, previous, full, verifyLegacy)
                    if (closed || id != serial) return@execute
                    val next = MusicLibraryDatabase.ScanRow().apply {
                        selectedTreesKey = key
                    }
                    // Commit only after a successful selected-tree enumeration.
                    database.runInTransaction(Runnable {
                        if (closed || id != serial) return@Runnable
                        var index = 0
                        while (index < result.removed.size) {
                            dao.deleteTracks(result.removed.subList(
                                index, minOf(index + 400, result.removed.size)
                            ))
                            index += 400
                        }
                        if (result.changes.isNotEmpty()) dao.insertTracks(result.changes)
                        dao.setScanState(next)
                    })
                    songs = Collections.unmodifiableList(result.songs)
                    summary = result.summary(full)
                    Log.i(TAG, "Library scan: added=" + summary.added +
                        " rechecked=" + summary.rechecked +
                        " removed=" + summary.removed +
                        " reused=" + summary.reused +
                        " total=" + summary.total + " full=" + full)
                }
                main.post {
                    if (!closed && id == serial) listener.onLoaded(songs, summary)
                }
            } catch (error: RuntimeException) {
                Log.e(TAG, "Music library load failed", error)
                main.post {
                    if (!closed && id == serial) {
                        listener.onError("Cannot read selected music folders")
                    }
                }
            }
        }
    }

    fun invalidate() { serial++ }

    fun forgetRemovedTree(tree: Uri, noAuthorizedTreesRemain: Boolean) {
        if (closed) return
        worker.execute {
            try {
                val dao = database.libraryDao()
                database.runInTransaction(Runnable {
                    if (noAuthorizedTreesRemain) dao.deleteAllTracks()
                    else dao.deleteTracksFromTree(tree.toString())
                    dao.deleteScanState()
                })
            } catch (error: RuntimeException) {
                Log.e(TAG, "Cannot remove deleted folder's cached metadata", error)
            }
        }
    }

    private fun scanSelected(
        id: Int, trees: List<Uri>,
        previous: Map<String, MusicLibraryDatabase.TrackRow>,
        full: Boolean, verifyLegacy: Boolean
    ): ScanResult {
        val result = ScanResult()
        val visited = HashSet<String>()
        var readable = 0
        for (tree in trees) {
            if (closed || id != serial) break
            try {
                scanTree(tree, id, visited, previous, full, verifyLegacy, result)
                readable++
            } catch (error: SecurityException) {
                Log.w(TAG, "Lost access to selected folder " + tree, error)
                throw error
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to scan selected folder " + tree, error)
                throw error
            }
        }
        if (!closed && id == serial && trees.isNotEmpty() && readable == 0) {
            throw IllegalStateException("All selected music folders were inaccessible")
        }
        for (uri in previous.keys) {
            if (uri !in result.retainedUris) result.removed.add(uri)
        }
        result.songs.sortWith(songOrder)
        return result
    }

    private fun scanTree(
        tree: Uri, id: Int, visited: MutableSet<String>,
        previous: Map<String, MusicLibraryDatabase.TrackRow>,
        full: Boolean, verifyLegacy: Boolean, result: ScanResult
    ) {
        val directories = ArrayDeque<String>()
        directories.add(DocumentsContract.getTreeDocumentId(tree))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        while (directories.isNotEmpty() && !closed && id == serial) {
            val directory = directories.removeFirst()
            if (!visited.add(tree.authority + "|" + directory)) continue
            val childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, directory)
            try {
                val cursor = resolver.query(childrenUri, projection, null, null, null)
                    ?: throw IllegalStateException(
                        "No child list for authorized directory " + childrenUri
                    )
                cursor.use { c ->
                    val idCol = c.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = c.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = c.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedCol = c.getColumnIndex(
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    while (c.moveToNext() && !closed && id == serial) {
                        val docId = c.getString(idCol)
                        val filename = c.getString(nameCol)
                        val mime = c.getString(mimeCol)
                        if (docId.isNullOrEmpty()) continue
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            directories.addLast(docId)
                        } else if (MusicFilePolicy.isAudio(filename, mime)) {
                            val file = DocumentsContract.buildDocumentUriUsingTree(
                                tree, docId
                            )
                            val uri = file.toString()
                            if (!result.visitedDocuments.add(uri)) continue
                            val size = cursorLong(c, sizeCol)
                            val modified = cursorLong(c, modifiedCol)
                            val old = previous[uri]
                            val reusable = !full && old != null &&
                                tree.toString() == old.sourceTree &&
                                !(verifyLegacy &&
                                    MusicAlbumTag.requiresLegacyVerification(old.album)) &&
                                MusicFileChangeDetector.unchanged(
                                    old.fileSize, old.lastModified, old.fileName,
                                    size, modified, filename
                                )
                            val song = if (reusable) {
                                result.reused++
                                fromRow(old!!)
                            } else {
                                inspectAudio(tree, file, filename)?.also {
                                    result.changes.add(trackRow(it, filename, size, modified))
                                    if (old == null) result.added++ else result.rechecked++
                                }
                            }
                            if (song != null) {
                                result.retainedUris.add(uri)
                                result.songs.add(song)
                            }
                        }
                    }
                }
            } catch (error: SecurityException) {
                Log.w(TAG, "No access to directory within selected tree", error)
                throw error
            } catch (error: RuntimeException) {
                Log.w(TAG, "Cannot enumerate directory within selected tree", error)
                throw error
            }
        }
    }

    /** Require actual audio stream, not merely an extension or MIME label. */
    private fun probeAudio(file: Uri): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(appContext, file, null)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime == null || !mime.startsWith("audio/")) continue
                val micros = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION)
                } else 0L
                return maxOf(0L, micros / 1000L)
            }
        } catch (error: IOException) {
            Log.d(TAG, "Cannot recognize or extract audio from: " + file, error)
        } catch (error: RuntimeException) {
            Log.d(TAG, "Cannot recognize or extract audio from: " + file, error)
        } finally {
            extractor.release()
        }
        return -1L
    }

    private fun inspectAudio(tree: Uri, file: Uri, filename: String?): Song? {
        val verifiedDuration = probeAudio(file)
        if (verifiedDuration < 0L) {
            Log.i(TAG, "Skipping unrecognized or unsupported audio candidate: " + file)
            return null
        }
        var fallback = filename ?: "Unknown Title"
        val dot = fallback.lastIndexOf('.')
        if (dot > 0) fallback = fallback.substring(0, dot)
        var title = fallback
        var artist = "Unknown Artist"
        var album = ""
        var genre = "Unknown Genre"
        var duration = verifiedDuration
        var track = 0
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(appContext, file)
            title = tag(metadata.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_TITLE), fallback)
            artist = tag(metadata.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_ARTIST), artist)
            album = MusicAlbumTag.normalize(metadata.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_ALBUM))
            genre = tag(metadata.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_GENRE), genre)
            val tagged = number(metadata.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION))
            if (tagged > 0L) duration = tagged
            track = trackNumber(metadata.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
        } catch (error: RuntimeException) {
            Log.d(TAG, "Metadata unavailable for " + file, error)
        } finally {
            try {
                metadata.release()
            } catch (error: IOException) {
                Log.w(TAG, "Unable to release metadata retriever for " + file, error)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to release metadata retriever for " + file, error)
            }
        }
        val songId = file.toString().hashCode().toLong() and 0xffffffffL
        return Song(songId, file, tree, title, artist, album, genre,
            -1L, -1L, duration, track)
    }

    fun close() {
        closed = true
        invalidate()
        worker.execute { database.close() }
        worker.shutdown()
    }
}
