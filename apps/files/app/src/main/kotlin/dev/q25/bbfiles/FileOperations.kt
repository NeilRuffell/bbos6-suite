package dev.q25.bbfiles

import java.io.File
import java.io.IOException
import java.nio.file.Files

/** File operations bounded to a selected shared-storage volume. No overwrites. */
internal object FileOperations {
    sealed interface Result {
        data class Success(val file: File? = null) : Result
        data class Failure(val message: String) : Result
    }

    fun validName(name: String): Boolean =
        name.isNotBlank() && name == name.trim() &&
            name != "." && name != ".." &&
            '/' !in name && '\\' !in name &&
            name.none { it.code < 32 || it.code == 127 }

    private fun writableItem(root: File, item: File): Boolean =
        FileBrowser.isWithin(root, item) && item.canonicalFile != root.canonicalFile

    fun newFolder(root: File, parent: File, name: String): Result = guarded {
        if (!validName(name)) return@guarded Result.Failure("Enter a valid folder name.")
        if (!FileBrowser.isWithin(root, parent) || !parent.isDirectory) {
            return@guarded Result.Failure("The destination folder is unavailable.")
        }
        val target = File(parent, name)
        if (!FileBrowser.isWithin(root, target)) {
            return@guarded Result.Failure("The folder is outside the storage volume.")
        }
        if (target.exists()) return@guarded Result.Failure("An item with this name already exists.")
        if (target.mkdir()) Result.Success(target)
        else Result.Failure("Android could not create the folder.")
    }

    fun rename(root: File, source: File, newName: String): Result = guarded {
        if (!validName(newName)) return@guarded Result.Failure("Enter a valid name.")
        if (!writableItem(root, source) || !source.exists()) {
            return@guarded Result.Failure("The selected item is unavailable.")
        }
        val parent = source.parentFile
            ?: return@guarded Result.Failure("The selected item has no parent folder.")
        val target = File(parent, newName)
        if (!FileBrowser.isWithin(root, target)) {
            return@guarded Result.Failure("The new path is outside this storage volume.")
        }
        if (target.canonicalFile == source.canonicalFile) {
            return@guarded Result.Success(source)
        }
        if (target.exists()) return@guarded Result.Failure("An item with this name already exists.")
        if (source.renameTo(target)) Result.Success(target)
        else Result.Failure("Android could not rename this item.")
    }

    fun delete(root: File, source: File): Result = guarded {
        if (!writableItem(root, source) || !source.exists()) {
            return@guarded Result.Failure("The selected item is unavailable.")
        }
        if (deleteTree(root, source)) Result.Success()
        else Result.Failure("Deletion was incomplete; check the folder contents.")
    }

    /** For both copy and cut/paste. Cross-volume moves copy before deleting. */
    fun paste(
        sourceRoot: File, source: File, targetRoot: File,
        targetFolder: File, move: Boolean
    ): Result = guarded {
        if (!writableItem(sourceRoot, source) || !source.exists()) {
            return@guarded Result.Failure("The source item is no longer available.")
        }
        if (!FileBrowser.isWithin(targetRoot, targetFolder) || !targetFolder.isDirectory) {
            return@guarded Result.Failure("The destination folder is unavailable.")
        }
        if (source.isDirectory && FileBrowser.isWithin(source, targetFolder)) {
            return@guarded Result.Failure("A folder cannot be placed inside itself.")
        }
        val target = File(targetFolder, source.name)
        if (!FileBrowser.isWithin(targetRoot, target)) {
            return@guarded Result.Failure("Destination is outside the selected storage.")
        }
        if (target.exists()) return@guarded Result.Failure("An item with this name already exists.")
        if (Files.isSymbolicLink(source.toPath())) {
            return@guarded Result.Failure("Symbolic links cannot be transferred.")
        }

        if (move && source.renameTo(target)) return@guarded Result.Success(target)
        try {
            copyTree(sourceRoot, source, targetRoot, target)
        } catch (error: IOException) {
            // Only a newly created, non-overwriting target is removed.
            if (target.exists()) deleteTree(targetRoot, target)
            return@guarded Result.Failure("Copy failed: ${error.message ?: "storage error"}.")
        }

        if (move && !deleteTree(sourceRoot, source)) {
            return@guarded Result.Failure(
                "Copy completed but the original could not be fully removed; check both folders."
            )
        }
        Result.Success(target)
    }

    private fun copyTree(sourceRoot: File, source: File, targetRoot: File, dest: File) {
        if (!FileBrowser.isWithin(sourceRoot, source) ||
            !FileBrowser.isWithin(targetRoot, dest) ||
            Files.isSymbolicLink(source.toPath())) {
            throw IOException("Storage boundary or symbolic link")
        }
        if (source.isDirectory) {
            if (!dest.mkdir()) throw IOException("Cannot create destination folder")
            val children = source.listFiles() ?: throw IOException("Cannot read source folder")
            for (child in children) copyTree(sourceRoot, child, targetRoot, File(dest, child.name))
        } else if (source.isFile) {
            source.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            throw IOException("Unsupported source item")
        }
    }

    private fun deleteTree(root: File, item: File): Boolean {
        if (!writableItem(root, item)) return false
        if (Files.isSymbolicLink(item.toPath())) return item.delete()
        if (item.isDirectory) {
            val children = item.listFiles() ?: return false
            if (children.any { !deleteTree(root, it) }) return false
        }
        return item.delete()
    }

    private inline fun guarded(block: () -> Result): Result =
        try { block() }
        catch (_: SecurityException) { Result.Failure("Android denied this storage operation.") }
        catch (e: IOException) { Result.Failure(e.message ?: "Storage operation failed.") }
    }
