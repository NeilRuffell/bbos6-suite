package dev.q25.bbmusic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Kotlin implementation of the existing Now Playing embedded-art loader.
 * Single-track metadata retrieval stays off the UI thread; new track
 * generations invalidate stale results and cannot change the current art.
 */
class MusicArtworkLoader(context: Context) {
    fun interface Listener {
        fun onArtwork(image: Bitmap?)
    }

    companion object {
        private const val TAG = "BBMusic-Artwork"
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var closed = false

    fun load(uri: Uri?, listener: Listener) {
        val request = ++generation
        if (closed || uri == null) {
            listener.onArtwork(null)
            return
        }
        worker.execute {
            var result: Bitmap? = null
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, uri)
                val picture = retriever.embeddedPicture
                if (picture != null && picture.isNotEmpty()) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
                    val maxSide = maxOf(options.outWidth, options.outHeight)
                    options.inSampleSize = 1
                    while (maxSide / options.inSampleSize > 600) {
                        options.inSampleSize *= 2
                    }
                    options.inJustDecodeBounds = false
                    result = BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
                }
            } catch (exception: RuntimeException) {
                Log.d(TAG, "Embedded artwork unavailable for selected track", exception)
            } catch (error: OutOfMemoryError) {
                Log.w(TAG, "Embedded artwork exceeds memory budget")
            } finally {
                try {
                    retriever.release()
                } catch (exception: IOException) {
                    Log.w(TAG, "Artwork retriever cleanup failed", exception)
                } catch (exception: RuntimeException) {
                    Log.w(TAG, "Artwork retriever cleanup failed", exception)
                }
            }
            val decoded = result
            main.post {
                if (!closed && request == generation) listener.onArtwork(decoded)
            }
        }
    }

    fun close() {
        closed = true
        generation++
        worker.shutdownNow()
    }
}
