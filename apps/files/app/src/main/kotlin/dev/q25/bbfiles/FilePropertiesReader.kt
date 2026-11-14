package dev.q25.bbfiles

import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Files-owned, read-only filesystem metadata adapter for the shared Properties dialog.
 * Call from Dispatchers.IO; it never scans a directory or guesses folder size.
 */
internal object FilePropertiesReader {
    data class Metadata(
        val name: String,
        val location: String,
        val isDirectory: Boolean,
        val extension: String?,
        val sizeBytes: Long?,
        val modifiedMillis: Long?
    )

    fun read(root: File, item: File, displayName: String): Metadata? = try {
        if (!FileBrowser.isWithin(root, item) || !item.exists() || !item.canRead()) {
            null
        } else {
            val resolved = item.canonicalFile
            if (!resolved.isDirectory && !resolved.isFile) {
                null
            } else {
                val isDirectory = resolved.isDirectory
                Metadata(
                    name = displayName,
                    location = resolved.parentFile?.absolutePath ?: resolved.absolutePath,
                    isDirectory = isDirectory,
                    extension = if (isDirectory) null else
                        resolved.extension.lowercase(Locale.ROOT).ifEmpty { null },
                    sizeBytes = if (isDirectory) null else resolved.length(),
                    modifiedMillis = resolved.lastModified().takeIf { it > 0L }
                )
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
