package dev.q25.bbfiles

import java.io.File
import java.io.IOException

/**
 * Stable, volume-scoped SAF document IDs. Never interpret an ID as an absolute
 * filesystem path; every lookup is resolved under a currently authorized root.
 */
internal object FilesDocumentIds {
    fun forFile(rootId: String, root: File, file: File): String? = try {
        if (':' in rootId || !FileBrowser.isWithin(root, file)) null
        else {
            val relative = root.canonicalFile.toPath()
                .relativize(file.canonicalFile.toPath())
                .toString().replace(File.separatorChar, '/')
            "$" + "{rootId}:$" + "relative"
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    fun resolve(rootId: String, root: File, documentId: String): File? = try {
        val prefix = "$" + "{rootId}:"
        if (':' in rootId || !documentId.startsWith(prefix)) return null
        val relative = documentId.removePrefix(prefix)
        if (relative.isNotEmpty() && (
                relative.startsWith('/') || relative.endsWith('/') ||
                    relative.split('/').any {
                        it.isEmpty() || it == "." || it == ".." ||
                            '\\' in it || it.any { character -> character.code < 32 }
                    }
                )
        ) return null
        val file = if (relative.isEmpty()) root.canonicalFile
        else File(root, relative).canonicalFile
        file.takeIf { FileBrowser.isWithin(root, it) }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
