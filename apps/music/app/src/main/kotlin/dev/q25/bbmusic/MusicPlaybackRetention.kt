package dev.q25.bbmusic

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Retained Media3 queue and position. This is a Kotlin port of the
 * established queue_v1 format, not a storage migration.
 *
 * Restore verifies each content document against exactly the same
 * still-authorized SAF tree before constructing a playable media item.
 */
internal object MusicPlaybackRetention {
    private const val TAG = "BBMusic-Retention"
    private const val PREFS = "bbmusic_playback"
    private const val QUEUE = "queue_v1"
    private const val INDEX = "index"
    private const val POSITION = "position_ms"
    private const val SHUFFLE = "shuffle"
    private const val REPEAT = "repeat"
    private const val URI = "uri"
    private const val TITLE = "title"
    private const val ARTIST = "artist"
    private const val ALBUM = "album"

    /** Java callers still receive the same nested class and public fields. */
    class Saved(
        @JvmField val items: ArrayList<MediaItem>,
        @JvmField val index: Int,
        @JvmField val positionMs: Long,
        @JvmField val shuffle: Boolean,
        @JvmField val repeat: Int
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun text(value: CharSequence?): String = value?.toString() ?: ""

    /** SAF provider IDs are opaque. Check exact tree identity, not path prefix. */
    private fun allowed(file: Uri?, selectedTrees: List<Uri>): Boolean {
        if (file == null || file.scheme != "content") return false
        try {
            if (!DocumentsContract.isTreeUri(file)) return false
            val fileTree = DocumentsContract.getTreeDocumentId(file)
            if (fileTree == null || DocumentsContract.getDocumentId(file) == null ||
                !file.pathSegments.contains("document")) return false
            for (tree in selectedTrees) {
                if (tree.authority != null && tree.authority == file.authority &&
                    fileTree == DocumentsContract.getTreeDocumentId(tree)) {
                    return true
                }
            }
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Cannot verify saved music document", exception)
        }
        return false
    }

    /** Save queue metadata only after validating every authorized item. */
    @JvmStatic
    fun saveQueue(context: Context, player: Player) {
        if (player.mediaItemCount == 0 || player.currentMediaItemIndex < 0) return
        val selected = MusicFolders.selectedTrees(context)
        val queue = JSONArray()
        for (i in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(i)
            val uri = item.localConfiguration?.uri
            if (uri == null || !allowed(uri, selected)) {
                Log.w(TAG, "Refusing to retain a queue outside selected folders")
                clear(context)
                return
            }
            val entry = JSONObject()
            try {
                entry.put(URI, uri.toString())
                entry.put(TITLE, text(item.mediaMetadata.title))
                entry.put(ARTIST, text(item.mediaMetadata.artist))
                entry.put(ALBUM, text(item.mediaMetadata.albumTitle))
            } catch (exception: JSONException) {
                Log.w(TAG, "Cannot encode Media3 queue", exception)
                return
            }
            queue.put(entry)
        }
        prefs(context).edit()
            .putString(QUEUE, queue.toString())
            .putInt(INDEX, player.currentMediaItemIndex)
            .putLong(POSITION, maxOf(0L, player.currentPosition))
            .putBoolean(SHUFFLE, player.shuffleModeEnabled)
            .putInt(REPEAT, player.repeatMode)
            .apply()
    }

    /** Frequent position updates do not reserialize the unchanged queue. */
    @JvmStatic
    fun checkpoint(context: Context, player: Player, durable: Boolean) {
        if (player.mediaItemCount == 0 || player.currentMediaItemIndex < 0) return
        val editor = prefs(context).edit()
            .putInt(INDEX, player.currentMediaItemIndex)
            .putLong(POSITION, maxOf(0L, player.currentPosition))
            .putBoolean(SHUFFLE, player.shuffleModeEnabled)
            .putInt(REPEAT, player.repeatMode)
        if (durable) {
            if (!editor.commit()) Log.w(TAG, "Playback checkpoint not committed")
        } else {
            editor.apply()
        }
    }

    /** Deliberate Stop removes system playback-resumption state. */
    @JvmStatic
    fun clear(context: Context) {
        if (!prefs(context).edit().clear().commit()) {
            Log.w(TAG, "Unable to clear previous playback state")
        }
    }

    /** Delete from saved queue while preserving surviving current track. */
    @JvmStatic
    fun forgetDeleted(context: Context, deletedIds: Set<String>?) {
        if (deletedIds.isNullOrEmpty()) return
        val preferences = prefs(context)
        val encoded = preferences.getString(QUEUE, null)
        if (encoded.isNullOrEmpty()) return
        try {
            val before = JSONArray(encoded)
            val remaining = JSONArray()
            val oldIndex = preferences.getInt(INDEX, 0)
            if (oldIndex < 0 || oldIndex >= before.length()) {
                clear(context)
                return
            }
            val kept = BooleanArray(before.length())
            var currentRemoved = false
            var changed = false
            for (i in 0 until before.length()) {
                val item = before.getJSONObject(i)
                if (item.getString(URI) in deletedIds) {
                    changed = true
                    if (i == oldIndex) currentRemoved = true
                } else {
                    remaining.put(item)
                    kept[i] = true
                }
            }
            if (!changed) return
            if (remaining.length() == 0) {
                clear(context)
                return
            }
            val newIndex = MusicDeletedQueueIndex.afterFiltering(oldIndex, kept)
            if (!preferences.edit()
                    .putString(QUEUE, remaining.toString())
                    .putInt(INDEX, newIndex)
                    .putLong(POSITION, if (currentRemoved) 0L else
                        preferences.getLong(POSITION, 0L))
                    .commit()) {
                Log.w(TAG, "Unable to persist deletion-adjusted playback queue")
            }
        } catch (exception: JSONException) {
            Log.w(TAG, "Invalid retained queue after deletion", exception)
            clear(context)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Invalid retained queue after deletion", exception)
            clear(context)
        }
    }

    /** Rehydrate only allowed files, never substitute a removed current track. */
    @JvmStatic
    fun restore(context: Context): Saved? {
        val preferences = prefs(context)
        val encoded = preferences.getString(QUEUE, null)
        if (encoded.isNullOrEmpty()) return null
        val selected = MusicFolders.selectedTrees(context)
        try {
            val queue = JSONArray(encoded)
            val oldIndex = preferences.getInt(INDEX, 0)
            if (oldIndex < 0 || oldIndex >= queue.length()) {
                clear(context)
                return null
            }
            val restored = ArrayList<MediaItem>()
            var restoredIndex = -1
            for (i in 0 until queue.length()) {
                val entry = queue.getJSONObject(i)
                val uri = Uri.parse(entry.getString(URI))
                if (!allowed(uri, selected)) {
                    if (i == oldIndex) {
                        clear(context)
                        return null
                    }
                    continue
                }
                if (i == oldIndex) restoredIndex = restored.size
                val metadata = MediaMetadata.Builder()
                    .setTitle(entry.optString(TITLE, ""))
                    .setArtist(entry.optString(ARTIST, ""))
                    .setAlbumTitle(entry.optString(ALBUM, ""))
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
                restored.add(MediaItem.Builder()
                    .setMediaId(uri.toString())
                    .setUri(uri)
                    .setMediaMetadata(metadata)
                    .build())
            }
            if (restored.isEmpty() || restoredIndex < 0) {
                clear(context)
                return null
            }
            val savedRepeat = preferences.getInt(REPEAT, Player.REPEAT_MODE_OFF)
            val repeat = if (savedRepeat == Player.REPEAT_MODE_OFF ||
                savedRepeat == Player.REPEAT_MODE_ONE ||
                savedRepeat == Player.REPEAT_MODE_ALL) savedRepeat
                else Player.REPEAT_MODE_OFF
            return Saved(
                restored,
                restoredIndex,
                maxOf(0L, preferences.getLong(POSITION, 0L)),
                preferences.getBoolean(SHUFFLE, false),
                repeat
            )
        } catch (exception: JSONException) {
            Log.w(TAG, "Invalid saved playback context; ignoring", exception)
            clear(context)
            return null
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Invalid saved playback context; ignoring", exception)
            clear(context)
            return null
        }
    }
}
