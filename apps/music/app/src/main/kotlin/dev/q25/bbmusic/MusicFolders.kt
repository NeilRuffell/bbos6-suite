package dev.q25.bbmusic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * Explicit, persisted SAF folder allowlist. Original preference keys, URI
 * encoding, grant validation and synchronous-commit ordering are unchanged.
 */
internal object MusicFolders {
    private const val TAG = "BBMusic-Folders"
    private const val PREFS = "bbmusic_selected_folders"
    private const val TREES = "tree_uris"

    @JvmStatic fun savedTrees(context: Context): List<Uri> {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(TREES, emptySet()).orEmpty()
        return saved.sorted().map(Uri::parse)
    }

    @JvmStatic fun selectedTrees(context: Context): List<Uri> {
        val granted = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapTo(HashSet<String>()) { it.uri.toString() }
        return savedTrees(context).filter { it.toString() in granted }
    }

    @JvmStatic fun hasWriteGrant(context: Context, tree: Uri): Boolean {
        if (tree !in selectedTrees(context)) return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission && it.isWritePermission
        }
    }

    @JvmStatic fun add(context: Context, tree: Uri): Boolean {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = HashSet(preferences.getStringSet(TREES, emptySet()).orEmpty())
        if (!updated.add(tree.toString())) return false
        check(preferences.edit().putStringSet(TREES, updated).commit()) {
            "Cannot save selected music folder"
        }
        return true
    }

    @JvmStatic fun displayName(context: Context, tree: Uri): String {
        try {
            val document = DocumentsContract.buildDocumentUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            context.contentResolver.query(document, projection, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(0)
                    if (name != null && name.trim { ch -> ch <= ' ' }.isNotEmpty()) {
                        return name
                    }
                }
            }
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Cannot resolve selected folder name", exception)
        }
        val id = documentId(tree)
        if (id.isEmpty()) return "Music folder"
        val separator = maxOf(id.lastIndexOf('/'), id.lastIndexOf(':'))
        return if (separator >= 0 && separator < id.lastIndex)
            id.substring(separator + 1) else id
    }

    @JvmStatic fun documentId(tree: Uri): String = try {
        DocumentsContract.getTreeDocumentId(tree).orEmpty()
    } catch (exception: RuntimeException) {
        tree.toString()
    }

    @JvmStatic fun displayLocation(documentId: String?): String {
        if (documentId == null) return ""
        if (!documentId.startsWith("primary:")) return documentId
        val path = documentId.substring("primary:".length)
        return if (path.isEmpty()) "Internal storage" else path
    }

    /** First commit allowlist removal, then release OS grants; never files. */
    @JvmStatic fun remove(context: Context, tree: Uri): Boolean {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = HashSet(preferences.getStringSet(TREES, emptySet()).orEmpty())
        if (!updated.remove(tree.toString())) return false
        check(preferences.edit().putStringSet(TREES, updated).commit()) {
            "Cannot remove selected music folder"
        }
        for (permission in context.contentResolver.persistedUriPermissions) {
            if (permission.uri != tree) continue
            val flags = (if (permission.isReadPermission) {
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            } else 0) or (if (permission.isWritePermission) {
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            } else 0)
            if (flags == 0) continue
            try {
                context.contentResolver.releasePersistableUriPermission(tree, flags)
            } catch (exception: SecurityException) {
                Log.w(TAG, "Selected folder grant is no longer held", exception)
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Selected folder grant is no longer held", exception)
            }
        }
        return true
    }
}
