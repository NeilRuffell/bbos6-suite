package dev.q25.bbfiles

import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Storage-specific path validation and directory enumeration, never UI logic. */
internal object FileBrowser {
    data class Entry(
        val file: File,
        val name: String,
        val isDirectory: Boolean,
        // Read with the directory listing on Dispatchers.IO; never size folders.
        val sizeBytes: Long? = null
    )

    sealed interface Listing {
        data class Available(val entries: List<Entry>) : Listing
        data class Unavailable(val reason: String) : Listing
    }

    /** Keeps navigation below the selected storage root, including canonical symlinks. */
    fun isWithin(root: File, candidate: File): Boolean = try {
        candidate.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

    fun parentWithin(root: File, current: File): File? {
        val parent = try {
            if (current.canonicalFile == root.canonicalFile) return null
            current.canonicalFile.parentFile
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
        return parent?.takeIf { isWithin(root, it) }
    }

    fun relativeTitle(root: File, directory: File, rootTitle: String): String = try {
        val relative = root.canonicalFile.toPath().relativize(directory.canonicalFile.toPath())
        if (relative.toString().isEmpty()) rootTitle
        else "$rootTitle / " + relative.joinToString(" / ")
    } catch (_: Exception) {
        rootTitle
    }

    fun list(root: File, directory: File): Listing {
        if (!isWithin(root, directory)) {
            return Listing.Unavailable("This folder is outside the selected storage.")
        }
        if (!directory.isDirectory) {
            return Listing.Unavailable("The folder is not available.")
        }
        val files = try {
            directory.listFiles()
        } catch (_: SecurityException) {
            null
        } ?: return Listing.Unavailable(
            "Android denied access to this folder, or storage is unavailable."
        )

        val entries = files.mapNotNull { candidate ->
            try {
                val resolved = candidate.canonicalFile
                if (isWithin(root, resolved)) {
                    val isDirectory = resolved.isDirectory
                    val size = if (resolved.isFile) {
                        try {
                            Files.size(resolved.toPath())
                        } catch (_: IOException) {
                            null
                        } catch (_: SecurityException) {
                            null
                        }
                    } else null
                    Entry(resolved, candidate.name, isDirectory, size)
                } else null
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }.sortedWith(
            compareByDescending<Entry> { it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.name }
        )
        return Listing.Available(entries)
    }
}
