package dev.q25.bbmusic

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Confirmed deletion of selected, writable, provider-approved *audio files*
 * via the native Storage Access Framework. Never deletes directories or
 * accesses raw filesystem/MediaStore paths.
 *
 * Invalidates Room scan state both before and after a confirmed batch so a
 * process interruption cannot leave a trusted catalog of deleted files.
 */
internal class MusicTrackDeletion(context: Context) {
    fun interface Listener {
        fun onFinished(result: Result)
    }

    class Result(ids: Set<String>, @JvmField val failed: Int) {
        @JvmField val deletedIds: Set<String> =
            Collections.unmodifiableSet(HashSet(ids))

        fun deletedCount(): Int = deletedIds.size
    }

    fun interface DisplayNameListener {
        fun onLoaded(displayName: String?)
    }

    private companion object {
        const val TAG = "BBMusic-Delete"
    }

    private val context = context.applicationContext
    private val resolver = this.context.contentResolver
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    /** Read the *provider filename*, not the song metadata title. */
    fun loadDisplayName(song: LocalMusicSource.Song?, listener: DisplayNameListener) {
        if (closed) return
        executor.execute {
            var displayName: String? = null
            if (song?.uri != null && song.sourceTree != null &&
                song.sourceTree in MusicFolders.selectedTrees(context)) {
                val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                try {
                    resolver.query(song.uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val column = cursor.getColumnIndex(
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME
                            )
                            if (column >= 0 && !cursor.isNull(column)) {
                                val value = cursor.getString(column)
                                if (value != null && value.trim { it <= ' ' }.isNotEmpty()) {
                                    displayName = value
                                }
                            }
                        }
                    }
                } catch (exception: RuntimeException) {
                    Log.w(TAG, "Document filename unavailable: " +
                        exception.javaClass.simpleName)
                }
            }
            val result = displayName
            main.post {
                if (!closed) listener.onLoaded(result)
            }
        }
    }

    /** The owning Activity must already have confirmed the destructive action. */
    fun delete(requested: List<LocalMusicSource.Song?>, listener: Listener) {
        if (closed || requested.isEmpty()) return
        // The same SAF URI may appear more than once in a grouped selection.
        val distinct = LinkedHashMap<String, LocalMusicSource.Song>()
        for (song in requested) {
            if (song?.uri != null) distinct[song.uri.toString()] = song
        }
        if (distinct.isEmpty()) return

        executor.execute {
            val deleted = HashSet<String>()
            var failed = 0
            var database: MusicLibraryDatabase? = null
            try {
                database = MusicLibraryDatabase.open(context)
                val db = database
                val dao = db.libraryDao()
                // First invalidation is durable before touching any document.
                db.runInTransaction(Runnable { dao.deleteScanState() })
                for (song in distinct.values) {
                    val uri = song.uri
                    if (!authorized(song) || !supportsDelete(uri)) {
                        failed++
                        continue
                    }
                    try {
                        if (DocumentsContract.deleteDocument(resolver, uri)) {
                            deleted.add(uri.toString())
                        } else {
                            failed++
                        }
                    } catch (exception: Exception) {
                        failed++
                        Log.w(TAG, "Provider rejected track deletion: " +
                            exception.javaClass.simpleName)
                    }
                }
                if (deleted.isNotEmpty()) {
                    // Persist even if the Activity was destroyed mid-batch.
                    MusicPlaybackRetention.forgetDeleted(context, deleted)
                    val uris = ArrayList(deleted)
                    var i = 0
                    while (i < uris.size) {
                        val batch = uris.subList(i, minOf(i + 400, uris.size))
                        db.runInTransaction(Runnable { dao.deleteTracks(batch) })
                        i += 400
                    }
                }
                // The second invalidation also protects against concurrent scans.
                db.runInTransaction(Runnable { dao.deleteScanState() })
            } catch (exception: RuntimeException) {
                Log.e(TAG, "Track deletion database operation failed", exception)
                failed += distinct.size - deleted.size - failed
            } finally {
                database?.close()
            }

            if (deleted.isNotEmpty()) {
                MusicPlaybackService.pruneDeletedDocuments(context, deleted)
            }
            val result = Result(deleted, failed)
            main.post {
                if (!closed) listener.onFinished(result)
            }
        }
    }

    private fun authorized(song: LocalMusicSource.Song): Boolean {
        val tree = song.sourceTree ?: return false
        val uri = song.uri ?: return false
        if (tree !in MusicFolders.selectedTrees(context) ||
            !MusicFolders.hasWriteGrant(context, tree)) return false
        return try {
            if (uri.scheme != "content" ||
                !DocumentsContract.isTreeUri(uri) ||
                tree.authority != uri.authority) return false
            val selectedRoot = DocumentsContract.getTreeDocumentId(tree)
            if (selectedRoot != DocumentsContract.getTreeDocumentId(uri)) return false
            val id = DocumentsContract.getDocumentId(uri)
            id != null && uri == DocumentsContract.buildDocumentUriUsingTree(tree, id)
        } catch (_: RuntimeException) {
            false
        }
    }

    /** Provider must explicitly advertise DELETE, and the target is not a dir. */
    private fun supportsDelete(uri: Uri): Boolean {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        return try {
            val cursor = resolver.query(uri, projection, null, null, null)
                ?: return false
            cursor.use {
                if (!it.moveToFirst()) return false
                val flagsIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (flagsIndex < 0 || it.isNull(flagsIndex)) return false
                if (mimeIndex >= 0 && !it.isNull(mimeIndex) &&
                    it.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return false
                }
                (it.getInt(flagsIndex) and
                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0
            }
        } catch (_: RuntimeException) {
            false
        }
    }

    /**
     * Suppress callbacks to a destroyed Activity while already confirmed
     * batches finish and persist changes on the worker.
     */
    fun close() {
        closed = true
        executor.shutdown()
    }
}
