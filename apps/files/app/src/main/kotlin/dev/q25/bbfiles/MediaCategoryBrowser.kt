package dev.q25.bbfiles

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.BaseColumns
import android.provider.MediaStore

/** Native Android indexed media collections, not filesystem scans. */
internal enum class IndexedMediaCategory(
    val id: String,
    val title: String,
    val fallbackMime: String
) {
    IMAGES("images", "Images", "image/*"),
    AUDIO("audio", "Audio", "audio/*"),
    VIDEOS("videos", "Videos", "video/*"),
    DOCUMENTS("documents", "Documents", "application/octet-stream");

    companion object {
        fun fromId(id: String): IndexedMediaCategory? =
            entries.firstOrNull { it.id == id }
    }
}

/** Document MIME classifications used for the MediaStore.Files index query.
 * These are file types, not paths, so results include documents outside Documents/.
 * Only files already indexed by Android and with recognized document MIME types appear.
 */
internal object DocumentMimePolicy {
    private val prefixes = listOf(
        "text/",
        "application/vnd.openxmlformats-officedocument.",
        "application/vnd.oasis.opendocument.",
        "application/vnd.ms-",
        "application/vnd.apple.",
        "application/x-iwork-"
    )
    private val exact = listOf(
        "application/pdf",
        "application/msword",
        "application/rtf",
        "application/epub+zip",
        "application/json",
        "application/xml",
        "application/x-abiword"
    )

    fun matches(mime: String?): Boolean {
        if (mime.isNullOrBlank()) return false
        return prefixes.any { mime.startsWith(it, ignoreCase = true) } ||
            exact.any { mime.equals(it, ignoreCase = true) }
    }

    fun selection(column: String): String =
        (prefixes.map { "$column LIKE ?" } + exact.map { "$column = ?" })
            .joinToString(separator = " OR ", prefix = "(", postfix = ")")

    fun selectionArgs(): Array<String> =
        (prefixes.map { "$it%" } + exact).toTypedArray()
}

internal object MediaCategoryBrowser {
    data class Entry(
        val uri: Uri,
        val name: String,
        val mime: String,
        val sizeBytes: Long? = null
    ) {
        val id: String get() = uri.toString()
    }

    sealed interface Listing {
        data class Available(val entries: List<Entry>) : Listing
        data class Unavailable(val reason: String) : Listing
    }

    fun query(resolver: ContentResolver, category: IndexedMediaCategory): Listing {
        val collection = when (category) {
            IndexedMediaCategory.IMAGES -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            IndexedMediaCategory.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            IndexedMediaCategory.VIDEOS -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            IndexedMediaCategory.DOCUMENTS -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        val projection = arrayOf(
            BaseColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE
        )
        val isDocuments = category == IndexedMediaCategory.DOCUMENTS
        val selection = if (isDocuments) {
            DocumentMimePolicy.selection(MediaStore.MediaColumns.MIME_TYPE)
        } else null
        val selectionArgs = if (isDocuments) DocumentMimePolicy.selectionArgs() else null
        return try {
            val found = mutableListOf<Entry>()
            val cursor = resolver.query(
                collection, projection, selection, selectionArgs,
                "${MediaStore.MediaColumns.DISPLAY_NAME} ASC"
            ) ?: return Listing.Unavailable("Android's media database is unavailable.")
            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(BaseColumns._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeColumn = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                while (it.moveToNext()) {
                    val name = it.getString(nameColumn)?.takeIf(String::isNotBlank) ?: continue
                    val mime = it.getString(mimeColumn)?.takeIf(String::isNotBlank)
                        ?: category.fallbackMime
                    if (isDocuments && !DocumentMimePolicy.matches(mime)) continue
                    found += Entry(
                        uri = ContentUris.withAppendedId(collection, it.getLong(idColumn)),
                        name = name,
                        mime = mime,
                        sizeBytes = if (sizeColumn >= 0 && !it.isNull(sizeColumn)) {
                            it.getLong(sizeColumn).takeIf { bytes -> bytes >= 0L }
                        } else null
                    )
                }
            }
            Listing.Available(
                found.sortedWith(compareBy<Entry, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.name })
            )
        } catch (_: SecurityException) {
            Listing.Unavailable("Android denied access to these media files.")
        } catch (_: RuntimeException) {
            Listing.Unavailable("Android could not read the media index.")
        }
    }
}
