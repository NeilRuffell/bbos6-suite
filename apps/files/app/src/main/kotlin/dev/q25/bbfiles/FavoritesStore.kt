package dev.q25.bbfiles

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * A favorite is a persistent shortcut, never a duplicate of the file.
 * Root identity is preserved to prevent a different SD card replacing a target.
 */
internal data class FileFavorite(
    val storageId: String,
    val rootPath: String,
    val path: String,
    val name: String,
    val isDirectory: Boolean
) {
    val id: String get() = "favorite:$storageId:$path"
}

internal object FavoritePolicy {
    fun create(storageId: String, root: File, file: File): FileFavorite? = try {
        val canonical = file.canonicalFile
        if (!FileBrowser.isWithin(root, canonical) ||
            canonical == root.canonicalFile ||
            (!canonical.isFile && !canonical.isDirectory)
        ) null else FileFavorite(
            storageId = storageId,
            rootPath = root.canonicalPath,
            path = canonical.path,
            name = file.name,
            isDirectory = canonical.isDirectory
        )
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    fun add(items: List<FileFavorite>, item: FileFavorite): List<FileFavorite> =
        if (items.any { it.id == item.id && it.rootPath == item.rootPath }) items
        else items + item

    fun remove(items: List<FileFavorite>, item: FileFavorite): List<FileFavorite> =
        items.filterNot { it.id == item.id && it.rootPath == item.rootPath }

    fun contains(items: List<FileFavorite>, item: FileFavorite): Boolean =
        items.any { it.id == item.id && it.rootPath == item.rootPath }

    /** Resolve afresh before opening; stale or remounted paths are not traversed. */
    fun resolve(root: File, favorite: FileFavorite): File? {
        return try {
            if (root.canonicalPath != favorite.rootPath) return null
            val file = File(favorite.path)
            if (!FileBrowser.isWithin(root, file)) return null
            val canonical = file.canonicalFile
            canonical.takeIf {
                it != root.canonicalFile &&
                    (it.isDirectory && favorite.isDirectory ||
                        it.isFile && !favorite.isDirectory)
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}

/** Stores insertion order in private app preferences; no media files are copied. */
internal class FavoritesStore(context: Context) {
    private val preferences = context.getSharedPreferences("files_favorites", Context.MODE_PRIVATE)

    fun load(): List<FileFavorite> = try {
        val array = JSONArray(preferences.getString("favorites", "[]") ?: "[]")
        val result = mutableListOf<FileFavorite>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val storageId = item.optString("storageId")
            val root = item.optString("rootPath")
            val path = item.optString("path")
            val name = item.optString("name")
            if (storageId !in listOf("internal", "removable") ||
                !root.startsWith("/") || !path.startsWith("/") || name.isBlank()) continue
            val favorite = FileFavorite(
                storageId, root, path, name, item.optBoolean("directory")
            )
            if (!result.any { it.id == favorite.id }) result += favorite
        }
        result
    } catch (_: JSONException) {
        emptyList()
    }

    fun save(favorites: List<FileFavorite>) {
        val array = JSONArray()
        favorites.forEach { favorite ->
            array.put(JSONObject().apply {
                put("storageId", favorite.storageId)
                put("rootPath", favorite.rootPath)
                put("path", favorite.path)
                put("name", favorite.name)
                put("directory", favorite.isDirectory)
            })
        }
        preferences.edit().putString("favorites", array.toString()).apply()
    }
}
