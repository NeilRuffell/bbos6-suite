package dev.q25.bbmusic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Kotlin artwork controller for the existing BBOS6 Compose library rows.
 * Reads only scanned SAF content URIs; no MediaStore or unrelated folder scan.
 * Requests and generations are main-thread owned. Worker callbacks cannot
 * repopulate a cleared cache or a recycled row's stale binding key.
 */
class MusicListArtworkLoader(context: Context) {
    fun interface Listener {
        fun onLoaded(cover: Bitmap?)
    }

    companion object {
        private const val TAG = "BBMusic-ListArt"
        private const val THUMBNAIL_MAX_PX = 160
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val workers = Executors.newFixedThreadPool(2)
    private val covers = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, image: Bitmap): Int =
            maxOf(1, image.byteCount / 1024)
    }
    private val waiting = mutableMapOf<String, MutableList<Listener>>()
    private val missing = mutableSetOf<String>()
    private var generation = 0
    private var closed = false

    fun request(key: String?, albumTracks: List<Uri>?, listener: Listener) {
        if (closed || key == null || albumTracks == null || albumTracks.isEmpty()) {
            listener.onLoaded(null)
            return
        }
        covers.get(key)?.let {
            listener.onLoaded(it)
            return
        }
        if (key in missing) {
            listener.onLoaded(null)
            return
        }
        waiting[key]?.let {
            it.add(listener)
            return
        }
        waiting[key] = mutableListOf(listener)

        val requestGeneration = generation
        val allowedCandidates = ArrayList(albumTracks)
        workers.execute {
            var found: Bitmap? = null
            for (uri in allowedCandidates) {
                if (Thread.currentThread().isInterrupted) break
                found = embeddedCover(uri)
                if (found != null) break
            }
            val result = found
            main.post {
                if (closed || requestGeneration != generation) return@post
                if (result != null) covers.put(key, result)
                else missing.add(key)
                val listeners = waiting.remove(key) ?: return@post
                listeners.forEach { it.onLoaded(result) }
            }
        }
    }

    private fun embeddedCover(trackUri: Uri): Bitmap? {
        if (trackUri.scheme != "content") return null
        val retriever = MediaMetadataRetriever()
        try {
            // The candidate is supplied only by an authorized SAF tree scan.
            retriever.setDataSource(appContext, trackUri)
            val embedded = retriever.embeddedPicture
            if (embedded == null || embedded.isEmpty()) return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(embedded, 0, embedded.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            options.inSampleSize = 1
            val maxSide = maxOf(options.outWidth, options.outHeight)
            while (maxSide / options.inSampleSize > THUMBNAIL_MAX_PX) {
                options.inSampleSize *= 2
            }
            options.inJustDecodeBounds = false
            return BitmapFactory.decodeByteArray(embedded, 0, embedded.size, options)
        } catch (exception: RuntimeException) {
            Log.d(TAG, "Embedded cover unavailable for selected track", exception)
            return null
        } catch (error: OutOfMemoryError) {
            Log.w(TAG, "Skipping oversized embedded cover for selected track")
            return null
        } finally {
            try {
                retriever.release()
            } catch (exception: IOException) {
                Log.w(TAG, "Cover retriever cleanup failed", exception)
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Cover retriever cleanup failed", exception)
            }
        }
    }

    /** Invalidate only in-memory data when selected SAF tree grants change. */
    fun clear() {
        generation++
        covers.evictAll()
        missing.clear()
        waiting.clear()
    }

    fun close() {
        if (closed) return
        closed = true
        clear()
        workers.shutdownNow()
    }
}
