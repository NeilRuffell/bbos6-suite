package dev.q25.bbmusic

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Music-owned Android integrations for one indexed, user-authorized SAF song.
 * The original document is never modified. Ringtones are user-requested copies
 * in the platform MediaStore; no legacy storage privileges are required.
 */
internal class MusicTrackActions(context: Context) {
    fun interface RingtoneListener {
        fun onFinished(error: String?)
    }

    private companion object {
        const val TAG = "BBMusic-Actions"
    }

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    private fun authorized(song: LocalMusicSource.Song): Boolean =
        song.uri.scheme == "content" &&
            song.sourceTree in MusicFolders.selectedTrees(appContext)

    /** Opens the native Sharesheet with a narrow read grant to the chosen document. */
    fun share(activity: Activity, song: LocalMusicSource.Song): String? {
        if (!authorized(song)) return "Music folder access is unavailable"
        return try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = resolver.getType(song.uri)?.takeIf { it.startsWith("audio/") }
                    ?: "audio/*"
                putExtra(Intent.EXTRA_STREAM, song.uri)
                clipData = ClipData.newUri(resolver, song.title, song.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(shareIntent, "Share song").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            null
        } catch (error: RuntimeException) {
            Log.w(TAG, "Cannot open share destination: " + error.javaClass.simpleName)
            "Unable to share this audio file"
        }
    }

    fun setAsRingtone(song: LocalMusicSource.Song, listener: RingtoneListener) {
        if (closed) return
        worker.execute {
            val error = copyAndAssignRingtone(song)
            main.post {
                if (!closed) listener.onFinished(error)
            }
        }
    }

    private fun providerName(uri: Uri): String? {
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column < 0 || cursor.isNull(column)) null else cursor.getString(column)
            }
    }

    private fun audioMime(name: String?, reported: String?): String? {
        val extension = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        return when (extension) {
            "mp3" -> "audio/mpeg"
            "m4a", "alac" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            "amr" -> "audio/amr"
            "aif", "aiff" -> "audio/aiff"
            "wma" -> "audio/x-ms-wma"
            else -> reported?.takeIf { it.startsWith("audio/") }
        }
    }

    private fun outputName(song: LocalMusicSource.Song, providerFilename: String?, mime: String): String {
        val ext = providerFilename?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
            ?.takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
            ?: when (mime) {
                "audio/mpeg" -> "mp3"
                "audio/mp4" -> "m4a"
                "audio/aac" -> "aac"
                "audio/flac" -> "flac"
                "audio/ogg" -> "ogg"
                "audio/opus" -> "opus"
                "audio/wav" -> "wav"
                "audio/amr" -> "amr"
                "audio/aiff" -> "aiff"
                "audio/x-ms-wma" -> "wma"
                else -> throw IOException("Unknown ringtone audio format")
            }
        val title = song.title.replace(Regex("[\\p{Cntrl}/\\\\:]"), "_")
            .trim().take(80).ifBlank { "Music ringtone" }
        return "$title.$ext"
    }

    private fun copyAndAssignRingtone(song: LocalMusicSource.Song): String? {
        if (!authorized(song)) return "Music folder access is unavailable"
        if (!Settings.System.canWrite(appContext)) return "Modify system settings permission required"

        var created: Uri? = null
        try {
            val filename = providerName(song.uri)
            val mime = audioMime(filename, resolver.getType(song.uri))
                ?: return "Unsupported ringtone audio format"
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, outputName(song, filename, mime))
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES + "/")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            created = resolver.insert(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
            ) ?: throw IOException("MediaStore could not create ringtone")
            val destination = created
            val input = resolver.openInputStream(song.uri)
                ?: throw IOException("Song document cannot be opened")
            input.use { source ->
                val output = resolver.openOutputStream(destination, "w")
                    ?: throw IOException("Ringtone destination cannot be opened")
                output.use { source.copyTo(it) }
            }
            val ready = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            if (resolver.update(destination, ready, null, null) <= 0) {
                throw IOException("Ringtone could not be published")
            }
            RingtoneManager.setActualDefaultRingtoneUri(
                appContext, RingtoneManager.TYPE_RINGTONE, destination
            )
            return null
        } catch (error: Exception) {
            Log.w(TAG, "Cannot set device ringtone: " + error.javaClass.simpleName)
            created?.let { destination ->
                try { resolver.delete(destination, null, null) }
                catch (cleanupError: Exception) {
                    Log.w(TAG, "Cannot clean up failed ringtone: " +
                        cleanupError.javaClass.simpleName)
                }
            }
            return "Unable to set this song as the ringtone"
        }
    }

    fun close() {
        closed = true
        worker.shutdown()
    }
}
