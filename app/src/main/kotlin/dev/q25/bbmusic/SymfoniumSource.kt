package dev.q25.bbmusic

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Kotlin port of the optional Android MediaBrowser/MediaController adapter.
 * The standalone Music app still owns its own independent Media3 playback.
 *
 * This adapter uses only the public platform media APIs and retains the
 * original callback interface, one-shot paged subscriptions and transport
 * request semantics. It does not read an external app's files or database.
 */
internal class SymfoniumSource(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onConnectionChanged(connected: Boolean, rootId: String?)
        fun onItems(parentId: String, page: Int, items: List<MediaBrowser.MediaItem>)
        fun onBrowseError(parentId: String, page: Int)
        fun onPlaybackChanged(metadata: MediaMetadata?, state: PlaybackState?)
    }

    private companion object {
        const val TAG = "BBMusic-Source"
        const val PACKAGE = "app.symfonik.music.player"
        const val SERVICE = "app.symfonik.core.playback.service.PlayerService"
    }

    private class Subscription(
        val parentId: String,
        val callback: MediaBrowser.SubscriptionCallback
    )

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val subscriptions = ArrayList<Subscription>()
    private var browser: MediaBrowser? = null
    private var controller: MediaController? = null
    private var started = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishPlayback()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishPlayback()
        }

        override fun onSessionDestroyed() {
            disconnectController()
            if (started) listener.onPlaybackChanged(null, null)
        }
    }

    fun connect() {
        if (started) return
        started = true
        val next = MediaBrowser(
            appContext,
            ComponentName(PACKAGE, SERVICE),
            object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() {
                    val current = browser
                    if (!started || current == null) return
                    try {
                        disconnectController()
                        val attached = MediaController(appContext, current.sessionToken)
                        controller = attached
                        attached.registerCallback(controllerCallback, main)
                    } catch (error: RuntimeException) {
                        Log.w(TAG, "Playback controller unavailable", error)
                    }
                    val root = current.root
                    Log.i(TAG, "Connected: root=$root")
                    listener.onConnectionChanged(true, root)
                    publishPlayback()
                }

                override fun onConnectionFailed() {
                    if (!started) return
                    Log.w(TAG, "Browser connection failed")
                    listener.onConnectionChanged(false, null)
                }

                override fun onConnectionSuspended() {
                    if (!started) return
                    Log.w(TAG, "Browser connection suspended")
                    disconnectController()
                    listener.onConnectionChanged(false, null)
                }
            },
            null
        )
        browser = next
        try {
            next.connect()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to connect to media service", error)
            listener.onConnectionChanged(false, null)
        }
    }

    fun isConnected(): Boolean = browser?.isConnected == true

    /**
     * A page request delivers one success or error callback, never both.
     * It is an external app media-service request, not proof of its response.
     */
    fun browse(parentId: String?, page: Int, pageSize: Int): Boolean {
        if (!started || !isConnected() || parentId == null ||
            page < 0 || pageSize <= 0) return false

        val current = browser ?: return false
        val options = Bundle().apply {
            putInt(MediaBrowser.EXTRA_PAGE, page)
            putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize)
        }
        val subscription = object : MediaBrowser.SubscriptionCallback() {
            private var completed = false

            override fun onChildrenLoaded(
                parentIdReturned: String,
                children: MutableList<MediaBrowser.MediaItem>
            ) {
                complete(children)
            }

            override fun onChildrenLoaded(
                parentIdReturned: String,
                children: MutableList<MediaBrowser.MediaItem>,
                extras: Bundle
            ) {
                complete(children)
            }

            override fun onError(parentIdReturned: String) {
                fail()
            }

            override fun onError(parentIdReturned: String, extras: Bundle) {
                fail()
            }

            private fun complete(children: List<MediaBrowser.MediaItem>?) {
                if (completed) return
                completed = true
                releaseSubscription(current, parentId, this)
                if (started) listener.onItems(parentId, page, children ?: emptyList())
            }

            private fun fail() {
                if (completed) return
                completed = true
                releaseSubscription(current, parentId, this)
                if (started) listener.onBrowseError(parentId, page)
            }
        }
        subscriptions.add(Subscription(parentId, subscription))
        return try {
            current.subscribe(parentId, options, subscription)
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Browse request failed for $parentId", error)
            releaseSubscription(current, parentId, subscription)
            listener.onBrowseError(parentId, page)
            false
        }
    }

    /** True means a transport request was sent, not that it completed. */
    fun playFromMediaId(mediaId: String?): Boolean {
        val transport = controller?.transportControls ?: return false
        if (mediaId == null) return false
        return try {
            transport.playFromMediaId(mediaId, null)
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Playback request failed", error)
            false
        }
    }

    fun play(): Boolean {
        val transport = controller?.transportControls ?: return false
        return try {
            transport.play()
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Play unavailable", error)
            false
        }
    }

    fun pause(): Boolean {
        val transport = controller?.transportControls ?: return false
        return try {
            transport.pause()
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Pause unavailable", error)
            false
        }
    }

    fun skipToNext(): Boolean {
        val transport = controller?.transportControls ?: return false
        return try {
            transport.skipToNext()
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Next unavailable", error)
            false
        }
    }

    fun skipToPrevious(): Boolean {
        val transport = controller?.transportControls ?: return false
        return try {
            transport.skipToPrevious()
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Previous unavailable", error)
            false
        }
    }

    private fun publishPlayback() {
        val current = controller ?: return
        if (!started) return
        try {
            listener.onPlaybackChanged(current.metadata, current.playbackState)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Playback state unavailable", error)
        }
    }

    private fun releaseSubscription(
        owner: MediaBrowser?,
        parentId: String,
        callback: MediaBrowser.SubscriptionCallback
    ) {
        subscriptions.removeAll { it.callback === callback }
        if (owner?.isConnected != true) return
        try {
            owner.unsubscribe(parentId, callback)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to unsubscribe $parentId", error)
        }
    }

    private fun disconnectController() {
        val current = controller ?: return
        try {
            current.unregisterCallback(controllerCallback)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Media callback cleanup failed", error)
        }
        controller = null
    }

    fun disconnect() {
        if (!started) return
        started = false
        val old = browser
        for (subscription in ArrayList(subscriptions)) {
            releaseSubscription(old, subscription.parentId, subscription.callback)
        }
        subscriptions.clear()
        disconnectController()
        browser = null
        if (old != null) {
            try {
                old.disconnect()
            } catch (error: RuntimeException) {
                Log.w(TAG, "Browser disconnect failed", error)
            }
        }
    }
}
