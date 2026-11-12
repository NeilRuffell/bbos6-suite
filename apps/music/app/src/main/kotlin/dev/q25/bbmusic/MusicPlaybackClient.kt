package dev.q25.bbmusic

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException

/**
 * Kotlin UI controller for the existing independent Media3 session service.
 * No queue format, persistent media state, connection behavior, or service
 * ownership changes; retains Java Activity / Now Playing interfaces.
 */
internal class MusicPlaybackClient(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onPlaybackChanged()
        fun onPlaybackError(message: String)
    }

    private companion object { const val TAG = "BBMusic-Playback" }
    private val context = context.applicationContext
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var connected = false
    private var connectionRetryCount = 0
    private var pendingItems: List<MediaItem>? = null
    private var pendingIndex = 0
    private var pendingShuffle = false
    private val pendingDeletedIds = HashSet<String>()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                Log.i(TAG, "Player state=" + player.playbackState +
                    ", hasItem=" + (player.currentMediaItem != null) +
                    ", playWhenReady=" + player.playWhenReady)
            }
            if (connected) listener.onPlaybackChanged()
        }
        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Selected-folder audio playback failed", error)
            if (connected) listener.onPlaybackError("Cannot play this audio file")
        }
    }

    fun connect() {
        connected = true
        if (controller != null || controllerFuture != null) return
        try {
            val token = SessionToken(
                context, ComponentName(context, MusicPlaybackService::class.java)
            )
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            Log.i(TAG, "Connecting Media3 controller")
            future.addListener(Runnable {
                val established = try {
                    future.get()
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    onConnectionFailed(future, error)
                    return@Runnable
                } catch (error: ExecutionException) {
                    onConnectionFailed(future, error)
                    return@Runnable
                } catch (error: RuntimeException) {
                    onConnectionFailed(future, error)
                    return@Runnable
                }
                if (!connected || controllerFuture !== future) return@Runnable
                controller = established
                connectionRetryCount = 0
                established.addListener(playerListener)
                if (pendingDeletedIds.isNotEmpty()) {
                    removeDeletedFromController(pendingDeletedIds)
                    pendingDeletedIds.clear()
                }
                Log.i(TAG, "Media3 controller connected; queuePending=" +
                    (pendingItems != null))
                val hadNewQueue = pendingItems != null
                flushPendingQueue()
                if (!hadNewQueue) restorePausedSession()
                listener.onPlaybackChanged()
            }, context.mainExecutor)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to initialize Media3 controller", error)
            controllerFuture = null
            listener.onPlaybackError("Cannot initialize BB Music player")
        }
    }

    private fun onConnectionFailed(
        future: ListenableFuture<MediaController>, error: Exception
    ) {
        Log.e(TAG, "Media3 controller connection failed", error)
        if (!connected || controllerFuture !== future) return
        controllerFuture = null
        MediaController.releaseFuture(future)
        if (pendingItems != null && connectionRetryCount++ == 0) {
            Log.w(TAG, "Retrying failed Media3 connection once")
            connect()
            return
        }
        listener.onPlaybackError("Cannot connect to BB Music player")
    }

    private fun flushPendingQueue() {
        if (controller == null) return
        val items = pendingItems ?: return
        val index = pendingIndex
        val shuffle = pendingShuffle
        pendingItems = null
        startQueue(items, index, shuffle)
    }

    fun playSongs(songs: List<LocalMusicSource.Song?>?, index: Int, shuffle: Boolean) {
        if (songs.isNullOrEmpty() || index !in songs.indices) {
            listener.onPlaybackError("No selected-folder songs to play")
            return
        }
        val items = ArrayList<MediaItem>(songs.size)
        for (song in songs) {
            val uri = song?.uri
            if (uri == null || uri.scheme != "content") {
                listener.onPlaybackError("Invalid selected-folder audio URI")
                return
            }
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album)
                .setIsPlayable(true).setIsBrowsable(false).build()
            items.add(
                MediaItem.Builder().setMediaId(uri.toString()).setUri(uri)
                    .setMediaMetadata(metadata).build()
            )
        }
        Log.i(TAG, "Play requested: queueSize=" + items.size +
            ", index=" + index + ", controllerReady=" + (controller != null))
        // Pending request must predate the future connection callback.
        pendingItems = items
        pendingIndex = index
        pendingShuffle = shuffle
        if (controller != null) flushPendingQueue()
        else {
            if (controllerFuture == null) connectionRetryCount = 0
            connect()
        }
    }

    private fun startQueue(items: List<MediaItem>, index: Int, shuffle: Boolean) {
        val current = controller ?: return
        try {
            Log.i(TAG, "Submitting Media3 playback queue: tracks=" + items.size +
                ", index=" + index)
            current.shuffleModeEnabled = shuffle
            current.repeatMode = Player.REPEAT_MODE_OFF
            current.setMediaItems(items, index, 0L)
            current.prepare()
            current.play()
            listener.onPlaybackChanged()
        } catch (error: RuntimeException) {
            Log.e(TAG, "Cannot start local playback", error)
            listener.onPlaybackError("Unable to play this audio file")
        }
    }

    /** Existing system-media-button session always takes precedence. */
    private fun restorePausedSession() {
        val current = controller ?: return
        if (current.mediaItemCount != 0) return
        val saved = MusicPlaybackRetention.restore(context) ?: return
        try {
            current.playWhenReady = false
            current.shuffleModeEnabled = saved.shuffle
            current.repeatMode = saved.repeat
            current.setMediaItems(saved.items, saved.index, saved.positionMs)
            current.prepare()
            Log.i(TAG, "Restored paused Media3 queue at " + saved.positionMs + " ms")
        } catch (error: RuntimeException) {
            Log.w(TAG, "Cannot restore previous playback", error)
            MusicPlaybackRetention.clear(context)
        }
    }

    fun pauseForExit() {
        val current = controller ?: return
        if (current.currentMediaItem != null && current.playWhenReady) current.pause()
    }
    fun controller(): MediaController? = controller
    fun hasCurrentTrack(): Boolean {
        val current = controller ?: return false
        if (current.currentMediaItem == null) return false
        return current.playbackState == Player.STATE_READY ||
            current.playbackState == Player.STATE_BUFFERING
    }

    fun playPause() {
        val current = controller ?: return
        if (current.currentMediaItem == null) return
        if (current.playWhenReady) current.pause()
        else {
            if (current.playbackState == Player.STATE_IDLE) current.prepare()
            current.play()
        }
    }

    /** Stop does not clear the in-app media item, only system resumption. */
    fun stop() {
        val current = controller ?: return
        if (current.currentMediaItem == null) return
        current.pause()
        current.seekTo(0L)
        current.stop()
        MusicPlaybackRetention.clear(context)
        listener.onPlaybackChanged()
    }

    fun previous() { controller?.seekToPreviousMediaItem() }
    fun next() { controller?.seekToNextMediaItem() }
    fun seek(ms: Long) {
        controller?.let { if (it.isCurrentMediaItemSeekable) it.seekTo(ms) }
    }
    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }
    fun toggleRepeat() {
        val current = controller ?: return
        current.repeatMode = when (current.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun removeDeletedSongs(deletedIds: Set<String>?) {
        if (deletedIds.isNullOrEmpty()) return
        pendingItems?.let { items ->
            val kept = BooleanArray(items.size)
            val remaining = ArrayList<MediaItem>()
            for (i in items.indices) {
                val item = items[i]
                kept[i] = item.mediaId !in deletedIds
                if (kept[i]) remaining.add(item)
            }
            if (remaining.size != items.size) {
                if (remaining.isEmpty()) pendingItems = null
                else {
                    pendingIndex = MusicDeletedQueueIndex.afterFiltering(pendingIndex, kept)
                    pendingItems = remaining
                }
            }
        }
        if (controller != null) removeDeletedFromController(deletedIds)
        else pendingDeletedIds.addAll(deletedIds)
        MusicPlaybackRetention.forgetDeleted(context, deletedIds)
        listener.onPlaybackChanged()
    }

    private fun removeDeletedFromController(deletedIds: Set<String>) {
        val current = controller ?: return
        for (i in current.mediaItemCount - 1 downTo 0) {
            if (current.getMediaItemAt(i).mediaId in deletedIds) {
                current.removeMediaItem(i)
            }
        }
    }

    fun disconnect() {
        connected = false
        pendingItems = null
        connectionRetryCount = 0
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }
}
