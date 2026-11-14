package dev.q25.bbfiles

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.util.Locale

/**
 * System Storage Access Framework adapter for Files' existing authorized
 * shared-storage volumes. Android's DocumentsUI owns the picker presentation,
 * URI grants and confirmation of user-selected operations.
 *
 * Never expose app-private paths, raw filesystem paths as document IDs, or
 * storage volumes while the user has revoked All Files Access.
 */
class FilesDocumentsProvider : DocumentsProvider() {
    private data class Root(
        val id: String,
        val title: String,
        val directory: File,
        val writable: Boolean
    )

    private data class Resolved(val root: Root, val file: File)

    private val authority: String
        get() = "${requireNotNull(context).packageName}.documents"

    override fun onCreate(): Boolean = true

    private fun roots(): List<Root> {
        if (!Environment.isExternalStorageManager()) return emptyList()
        val appContext = context ?: return emptyList()
        val found = mutableListOf<Root>()
        val primary = Environment.getExternalStorageDirectory()
        val state = Environment.getExternalStorageState()
        if (primary.isDirectory && primary.canRead() &&
            (state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY)
        ) {
            found += Root(
                "primary", "Internal Storage", primary,
                state == Environment.MEDIA_MOUNTED && primary.canWrite()
            )
        }
        val manager = appContext.getSystemService(StorageManager::class.java)
        for (volume in manager.storageVolumes) {
            if (!volume.isRemovable ||
                (volume.state != Environment.MEDIA_MOUNTED &&
                    volume.state != Environment.MEDIA_MOUNTED_READ_ONLY)
            ) continue
            val directory = volume.directory ?: continue
            if (!directory.isDirectory || !directory.canRead()) continue
            // No path-based fallback: a reused mount path could otherwise
            // redirect a persisted document grant to a different SD card.
            val key = volume.uuid?.takeIf { it.isNotBlank() && ':' !in it }
                ?: continue
            found += Root(
                "sd-$key", "SD Card", directory,
                volume.state == Environment.MEDIA_MOUNTED && directory.canWrite()
            )
        }
        return found.distinctBy { it.id }
    }

    private fun resolve(documentId: String): Resolved {
        val rootId = documentId.substringBefore(':')
        val root = roots().firstOrNull { it.id == rootId }
            ?: throw FileNotFoundException("Storage access is unavailable.")
        val file = FilesDocumentIds.resolve(root.id, root.directory, documentId)
            ?: throw FileNotFoundException("Invalid document ID.")
        if (!file.exists() || !file.canRead() || (!file.isDirectory && !file.isFile)) {
            throw FileNotFoundException("Document is unavailable.")
        }
        return Resolved(root, file)
    }

    private fun idFor(root: Root, file: File): String =
        FilesDocumentIds.forFile(root.id, root.directory, file)
            ?: throw FileNotFoundException("Document is outside authorized storage.")

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: ROOT_COLUMNS
        val result = MatrixCursor(columns)
        for (root in roots()) {
            val values = mapOf<String, Any?>(
                DocumentsContract.Root.COLUMN_ROOT_ID to root.id,
                DocumentsContract.Root.COLUMN_DOCUMENT_ID to idFor(root, root.directory),
                DocumentsContract.Root.COLUMN_TITLE to root.title,
                DocumentsContract.Root.COLUMN_SUMMARY to "Files",
                DocumentsContract.Root.COLUMN_ICON to R.mipmap.ic_launcher,
                DocumentsContract.Root.COLUMN_FLAGS to (
                    DocumentsContract.Root.FLAG_LOCAL_ONLY or
                        DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD or
                        if (root.writable) DocumentsContract.Root.FLAG_SUPPORTS_CREATE else 0
                    ),
                DocumentsContract.Root.COLUMN_AVAILABLE_BYTES to root.directory.usableSpace,
                DocumentsContract.Root.COLUMN_CAPACITY_BYTES to root.directory.totalSpace
            )
            result.addRow(columns.map { values[it] }.toTypedArray())
        }
        context?.let {
            result.setNotificationUri(it.contentResolver, DocumentsContract.buildRootsUri(authority))
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns = projection ?: DOCUMENT_COLUMNS
        val result = MatrixCursor(columns)
        val item = resolve(documentId)
        addDocument(result, columns, item.root, item.file)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val columns = projection ?: DOCUMENT_COLUMNS
        val result = MatrixCursor(columns)
        val parent = resolve(parentDocumentId)
        if (!parent.file.isDirectory) throw FileNotFoundException("Not a directory.")
        when (val listing = FileBrowser.list(parent.root.directory, parent.file)) {
            is FileBrowser.Listing.Unavailable ->
                throw FileNotFoundException(listing.reason)
            is FileBrowser.Listing.Available -> {
                for (child in listing.entries) {
                    // Exclude symlink aliases: a picker child ID must resolve
                    // to a direct child, not a duplicated target elsewhere.
                    if (!Files.isSymbolicLink(File(parent.file, child.name).toPath()) &&
                        child.file.canRead()
                    ) {
                        addDocument(result, columns, parent.root, child.file)
                    }
                }
            }
        }
        context?.let {
            result.setNotificationUri(
                it.contentResolver,
                DocumentsContract.buildChildDocumentsUri(authority, parentDocumentId)
            )
        }
        return result
    }

    private fun addDocument(
        cursor: MatrixCursor, columns: Array<out String>, root: Root, file: File
    ) {
        val rootDirectory = root.directory.canonicalFile
        val isRoot = file.canonicalFile == rootDirectory
        val directory = file.isDirectory
        val writable = root.writable && file.canWrite()
        val parentWritable = root.writable && !isRoot && file.parentFile?.canWrite() == true
        var flags = 0
        if (directory && writable) {
            flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        }
        if (!directory && writable) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        }
        if (parentWritable) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE
        }
        val modified = file.lastModified().takeIf { it > 0L }
        val values = mapOf<String, Any?>(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID to idFor(root, file),
            DocumentsContract.Document.COLUMN_DISPLAY_NAME to
                if (isRoot) root.title else file.name,
            DocumentsContract.Document.COLUMN_MIME_TYPE to
                if (directory) DocumentsContract.Document.MIME_TYPE_DIR else mimeFor(file),
            DocumentsContract.Document.COLUMN_FLAGS to flags,
            DocumentsContract.Document.COLUMN_SIZE to
                if (directory) null else FileBrowser.fileSizeBytes(file),
            DocumentsContract.Document.COLUMN_LAST_MODIFIED to modified
        )
        cursor.addRow(columns.map { values[it] }.toTypedArray())
    }

    private fun mimeFor(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            file.extension.lowercase(Locale.ROOT)
        ) ?: "application/octet-stream"

    override fun getDocumentType(documentId: String): String {
        val file = resolve(documentId).file
        return if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else mimeFor(file)
    }

    override fun openDocument(
        documentId: String, mode: String, signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val item = resolve(documentId)
        if (!item.file.isFile) throw FileNotFoundException("Cannot open a directory.")
        if (mode !in setOf("r", "w", "wt", "wa", "rw", "rwt")) {
            throw FileNotFoundException("Unsupported access mode.")
        }
        if (mode != "r" && (!item.root.writable || !item.file.canWrite())) {
            throw FileNotFoundException("Document is read-only.")
        }
        try {
            return ParcelFileDescriptor.open(item.file, ParcelFileDescriptor.parseMode(mode))
        } catch (_: IOException) {
            throw FileNotFoundException("Cannot open document.")
        } catch (_: SecurityException) {
            throw FileNotFoundException("Android denied document access.")
        }
    }

    override fun createDocument(
        parentDocumentId: String, mimeType: String, displayName: String
    ): String {
        val parent = resolve(parentDocumentId)
        if (!parent.root.writable || !parent.file.isDirectory || !parent.file.canWrite() ||
            !FileOperations.validName(displayName)
        ) throw FileNotFoundException("Cannot create document here.")
        val created = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            when (val outcome = FileOperations.newFolder(
                parent.root.directory, parent.file, displayName
            )) {
                is FileOperations.Result.Success -> outcome.file
                is FileOperations.Result.Failure ->
                    throw FileNotFoundException(outcome.message)
            }
        } else {
            val target = File(parent.file, displayName)
            if (!FileBrowser.isWithin(parent.root.directory, target) ||
                target.exists() || Files.isSymbolicLink(target.toPath())
            ) throw FileNotFoundException("A document with this name already exists.")
            try {
                if (!target.createNewFile()) throw FileNotFoundException("Document already exists.")
            } catch (_: IOException) {
                throw FileNotFoundException("Unable to create document.")
            }
            target
        } ?: throw FileNotFoundException("Unable to create document.")
        notifyChildren(parentDocumentId)
        return idFor(parent.root, created)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val item = resolve(documentId)
        if (!item.root.writable || !FileOperations.validName(displayName)) {
            throw FileNotFoundException("Cannot rename document.")
        }
        val renamed = when (val outcome = FileOperations.rename(
            item.root.directory, item.file, displayName
        )) {
            is FileOperations.Result.Success -> outcome.file
            is FileOperations.Result.Failure -> throw FileNotFoundException(outcome.message)
        } ?: throw FileNotFoundException("Rename failed.")
        notifyParent(documentId)
        return idFor(item.root, renamed)
    }

    override fun deleteDocument(documentId: String) {
        val item = resolve(documentId)
        if (!item.root.writable) throw FileNotFoundException("Storage is read-only.")
        when (val result = FileOperations.delete(item.root.directory, item.file)) {
            is FileOperations.Result.Success -> notifyParent(documentId)
            is FileOperations.Result.Failure -> throw FileNotFoundException(result.message)
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        try {
            val parent = resolve(parentDocumentId)
            val child = resolve(documentId)
            parent.root.id == child.root.id &&
                parent.file != child.file &&
                parent.file.isDirectory &&
                FileBrowser.isWithin(parent.file, child.file)
        } catch (_: FileNotFoundException) {
            false
        }

    private fun notifyParent(documentId: String) {
        val parentId = documentId.substringBeforeLast('/', documentId.substringBefore(':') + ":")
        notifyChildren(parentId)
    }

    private fun notifyChildren(parentId: String) {
        context?.contentResolver?.notifyChange(
            DocumentsContract.buildChildDocumentsUri(authority, parentId), null
        )
    }

    private companion object {
        val ROOT_COLUMNS = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
            DocumentsContract.Root.COLUMN_CAPACITY_BYTES
        )
        val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }
}
