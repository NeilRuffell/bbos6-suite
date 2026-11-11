package dev.q25.bbmusic

import android.content.Context
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.Log
import dev.q25.bbos6.ui.Bbos6PropertiesItem
import java.util.Date
import java.util.concurrent.Executors

/**
 * App-specific SAF metadata adapter for the canonical BBOS6 Properties UI.
 * Reads only one still-authorized selected folder's document. No absolute
 * file paths, creation timestamps or other unavailable metadata are invented.
 */
internal class MusicTrackPropertiesReader(context: Context) {
    fun interface Listener {
        fun onLoaded(items: List<Bbos6PropertiesItem>)
    }

    private companion object {
        const val TAG = "BBMusic-Properties"

        fun known(value: String?): Boolean =
            value != null && value.trim { it <= ' ' }.isNotEmpty()

        fun readString(cursor: Cursor, column: String): String? {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) null else cursor.getString(index)
        }

        fun readPositiveOrZero(cursor: Cursor, column: String): Long? {
            val index = cursor.getColumnIndex(column)
            if (index < 0 || cursor.isNull(index)) return null
            val value = cursor.getLong(index)
            return if (value < 0L) null else value
        }
    }

    private val context = context.applicationContext
    private val resolver = this.context.contentResolver
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false

    fun load(song: LocalMusicSource.Song, listener: Listener) {
        if (closed) return
        worker.execute {
            if (closed) return@execute
            // Revoked/removed trees do not authorize metadata retrieval.
            if (song.sourceTree !in MusicFolders.selectedTrees(context)) {
                main.post {
                    if (!closed) listener.onLoaded(ArrayList())
                }
                return@execute
            }

            var fileName: String? = null
            var mime: String? = null
            var fileSize: Long? = null
            var modifiedAt: Long? = null
            try {
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )
                resolver.query(song.uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        fileName = readString(cursor, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        mime = readString(cursor, DocumentsContract.Document.COLUMN_MIME_TYPE)
                        fileSize = readPositiveOrZero(cursor, DocumentsContract.Document.COLUMN_SIZE)
                        modifiedAt = readPositiveOrZero(
                            cursor, DocumentsContract.Document.COLUMN_LAST_MODIFIED
                        )
                    }
                }
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Optional document metadata unavailable (" +
                    exception.javaClass.simpleName + ")")
            }

            val items = ArrayList<Bbos6PropertiesItem>()
            if (known(fileName)) {
                try {
                    val docId = DocumentsContract.getDocumentId(song.uri)
                    val path = MusicDocumentPath.readableLocation(
                        song.uri.authority, docId, fileName
                    )
                    if (path != null) {
                        items.add(Bbos6PropertiesItem.text("Path: " + path))
                    }
                } catch (_: RuntimeException) {
                    // Opaque provider ID is not an inferred filesystem path.
                }
            }
            if (fileSize != null) {
                items.add(Bbos6PropertiesItem.trailing(
                    "Size:", Formatter.formatShortFileSize(context, fileSize!!)
                ))
            }
            items.add(Bbos6PropertiesItem.`inline`(
                "Type:", if (mime?.startsWith("audio/") == true) mime!! else "Audio File"
            ))
            if (modifiedAt != null && modifiedAt!! > 0L) {
                val date = Date(modifiedAt!!)
                val formatted = DateFormat.getMediumDateFormat(context).format(date) +
                    " " + DateFormat.getTimeFormat(context).format(date)
                items.add(Bbos6PropertiesItem.trailing("Last Modified:", formatted))
            }
            if (known(song.title)) {
                items.add(Bbos6PropertiesItem.`inline`("Title:", song.title))
            }
            if (MusicAlbumTag.present(song.album)) {
                items.add(Bbos6PropertiesItem.`inline`("Album:", song.album))
            }
            if (known(song.artist) && song.artist != "Unknown Artist") {
                items.add(Bbos6PropertiesItem.`inline`("Artist:", song.artist))
            }

            main.post {
                if (closed) return@post
                // Check authorization again immediately before displaying data.
                if (song.sourceTree !in MusicFolders.selectedTrees(context)) {
                    listener.onLoaded(ArrayList())
                } else {
                    listener.onLoaded(items)
                }
            }
        }
    }

    fun close() {
        closed = true
        worker.shutdownNow()
    }
}
