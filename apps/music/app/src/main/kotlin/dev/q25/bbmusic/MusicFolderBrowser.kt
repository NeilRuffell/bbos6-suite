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
 * Kotlin read-only SAF browser. Restricts every request and callback to
 * still-selected, still-read-authorized tree URIs. Never scans another tree
 * or writes provider data. Provider I/O runs on one worker thread; listeners
 * are invoked on Android's main looper as in the original Java version.
 */
class MusicFolderBrowser(context: Context) {
    interface Listener {
        fun onLoaded(entries: List<Entry>)
        fun onError(message: String)
    }

    /** Java callers retain the same .name/.documentId/.uri/.directory fields. */
    class Entry(
        @JvmField val name: String,
        @JvmField val documentId: String,
        @JvmField val uri: Uri,
        @JvmField val directory: Boolean
    )

    companion object {
        private const val TAG = "BBMusic-Browser"
    }

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false

    fun listChildren(tree: Uri, directoryId: String, listener: Listener) {
        if (closed) return
        worker.execute {
            try {
                // Do not trust a previously opened Page as authorization.
                if (!MusicFolders.selectedTrees(appContext).contains(tree)) {
                    throw SecurityException("Selected folder access unavailable")
                }
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                    tree, directoryId
                )
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                val entries = ArrayList<Entry>()
                val cursor = resolver.query(children, projection, null, null, null)
                    ?: throw IllegalStateException("Folder has no readable child list")
                cursor.use { result ->
                    val idIndex = result.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID
                    )
                    val nameIndex = result.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    )
                    val mimeIndex = result.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    )
                    while (!closed && result.moveToNext()) {
                        val id = result.getString(idIndex)
                        if (id.isNullOrEmpty()) continue
                        var name = result.getString(nameIndex)
                        if (name == null || name.trim { it <= ' ' }.isEmpty()) {
                            // Display-only fallback; provider document ID is opaque.
                            val separator = maxOf(id.lastIndexOf('/'), id.lastIndexOf(':'))
                            name = if (separator >= 0) id.substring(separator + 1) else id
                        }
                        val mime = result.getString(mimeIndex)
                        val directory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                        if (directory) {
                            if (!MusicFilePolicy.isVisibleDirectory(name)) continue
                        } else if (!MusicFilePolicy.isAudio(name, mime)) {
                            continue
                        }
                        entries.add(
                            Entry(
                                name, id,
                                DocumentsContract.buildDocumentUriUsingTree(tree, id),
                                directory
                            )
                        )
                    }
                }
                entries.sortWith { a, b ->
                    if (a.directory != b.directory) {
                        if (a.directory) -1 else 1
                    } else {
                        val comparison = String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
                        if (comparison != 0) comparison
                        else a.documentId.compareTo(b.documentId)
                    }
                }
                val snapshot = Collections.unmodifiableList(ArrayList(entries))
                main.post {
                    if (!closed) {
                        if (!MusicFolders.selectedTrees(appContext).contains(tree)) {
                            listener.onError("Folder access unavailable")
                        } else {
                            listener.onLoaded(snapshot)
                        }
                    }
                }
            } catch (exception: SecurityException) {
                Log.w(TAG, "Selected music folder permission unavailable", exception)
                main.post {
                    if (!closed) listener.onError("Folder access unavailable")
                }
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Cannot browse selected music folder", exception)
                main.post {
                    if (!closed) listener.onError("Cannot open music folder")
                }
            }
        }
    }

    fun close() {
        closed = true
        worker.shutdownNow()
    }
}
