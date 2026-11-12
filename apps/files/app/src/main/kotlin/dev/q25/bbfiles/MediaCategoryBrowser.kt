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
    VIDEOS("videos", "Videos", "video/*");

    companion object {
        fun fromId(id: String): IndexedMediaCategory? =
            entries.firstOrNull { it.id == id }
    }
}

internal object MediaCategoryBrowser {
    data class Entry(val uri: Uri, val name: String, val mime: String) {
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
        }
        val projection = arrayOf(
            BaseColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE
        )
        return try {
            val found = mutableListOf<Entry>()
            val cursor = resolver.query(
                collection, projection, null, null,
                "${MediaStore.MediaColumns.DISPLAY_NAME} ASC"
            ) ?: return Listing.Unavailable("Android's media database is unavailable.")
            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(BaseColumns._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                while (it.moveToNext()) {
                    val name = it.getString(nameColumn)?.takeIf(String::isNotBlank) ?: continue
                    val mime = it.getString(mimeColumn)?.takeIf(String::isNotBlank)
                        ?: category.fallbackMime
                    found += Entry(
                        uri = ContentUris.withAppendedId(collection, it.getLong(idColumn)),
                        name = name,
                        mime = mime
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
