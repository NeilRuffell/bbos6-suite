package dev.q25.bbmusic

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.lang.ref.WeakReference

/**
 * Single app-owned Media3 playback authority. Kotlin source only: original
 * service class identity, SAF resumption format, session, Stop/Home/Recents
 * semantics and retained queue checkpoint schedule remain unchanged.
 */
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaLibraryService() {
    companion object {
        const val TAG = "BBMusic-Service"
        const val LIBRARY_ROOT = "bbmusic-recent-queue"
        const val CHECKPOINT_INTERVAL_MS = 5000L

        @Volatile
        var activeService: WeakReference<MusicPlaybackService?> = WeakReference(null)

        /** Java-callable so deletion continues after Activity teardown. */
        @JvmStatic
        fun pruneDeletedDocuments(context: Context, deletedIds: Set<String>?) {
            if (deletedIds.isNullOrEmpty()) return
            val ids = HashSet(deletedIds)
            Handler(Looper.getMainLooper()).post {
                val current = activeService.get()?.player
                if (current != null) {
                    for (i in current.mediaItemCount - 1 downTo 0) {
                        if (current.getMediaItemAt(i).mediaId in ids) {
                            current.removeMediaItem(i)
                        }
                    }
                }
                MusicPlaybackRetention.forgetDeleted(context, ids)
            }
        }
    }

    private val checkpointHandler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null

    private val checkpointTicker = object : Runnable {
        override fun run() {
            val current = player
            if (current != null && current.isPlaying) {
                MusicPlaybackRetention.checkpoint(this@MusicPlaybackService, current, false)
                checkpointHandler.postDelayed(this, CHECKPOINT_INTERVAL_MS)
            }
        }
    }

    private val retentionListener = object : Player.Listener {
        override fun onEvents(current: Player, events: Player.Events) {
            checkpointHandler.removeCallbacks(checkpointTicker)
            if (current.playbackState == Player.STATE_IDLE &&
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                MusicPlaybackRetention.clear(this@MusicPlaybackService)
                return
            }
            if (current.mediaItemCount > 0) {
                if (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                    (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                        current.playbackState == Player.STATE_BUFFERING)) {
                    MusicPlaybackRetention.saveQueue(this@MusicPlaybackService, current)
                } else if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                    events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                    events.contains(Player.EVENT_REPEAT_MODE_CHANGED) ||
                    events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    MusicPlaybackRetention.checkpoint(
                        this@MusicPlaybackService, current, !current.playWhenReady
                    )
                }
            }
            if (current.isPlaying) {
                checkpointHandler.postDelayed(checkpointTicker, CHECKPOINT_INTERVAL_MS)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback source failed; clearing retention", error)
            MusicPlaybackRetention.clear(this@MusicPlaybackService)
        }
    }

    private fun libraryRoot(): MediaItem =
        MediaItem.Builder().setMediaId(LIBRARY_ROOT)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Music")
                    .setIsBrowsable(true)
                    .setIsPlayable(false).build()
            ).build()

    /** Revalidate live SAF selection on every browse/resumption callback. */
    private fun recentQueue(): List<MediaItem> =
        MusicPlaybackRetention.restore(this)?.items ?: emptyList()

    private val libraryCallback = object : MediaLibrarySession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = MusicPlaybackRetention.restore(this@MusicPlaybackService)
            if (saved == null) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        emptyList(), C.INDEX_UNSET, C.TIME_UNSET
                    )
                )
            }
            if (!isForPlayback) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        listOf(saved.items[saved.index]), 0, saved.positionMs
                    )
                )
            }
            player?.let {
                it.shuffleModeEnabled = saved.shuffle
                it.repeatMode = saved.repeat
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    saved.items, saved.index, saved.positionMs
                )
            )
        }

        override fun onGetLibraryRoot(
            mediaSession: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(libraryRoot(), params))

        override fun onGetChildren(
            mediaSession: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val queue = if (parentId == LIBRARY_ROOT) recentQueue() else emptyList()
            val offset = page.toLong() * pageSize
            val start = minOf(queue.size.toLong(), offset).toInt()
            val end = minOf(queue.size.toLong(), offset + pageSize).toInt()
            return Futures.immediateFuture(
                LibraryResult.ofItemList(queue.subList(start, end), params)
            )
        }

        override fun onGetItem(
            mediaSession: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (mediaId == LIBRARY_ROOT) {
                return Futures.immediateFuture(LibraryResult.ofItem(libraryRoot(), null))
            }
            for (item in recentQueue()) {
                if (item.mediaId == mediaId) {
                    return Futures.immediateFuture(LibraryResult.ofItem(item, null))
                }
            }
            return super.onGetItem(mediaSession, browser, mediaId)
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = WeakReference(this)
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)
        val created = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this))
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = created
        created.addListener(retentionListener)

        val openMusic = Intent(this, MusicActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val action = PendingIntent.getActivity(
            this, 0, openMusic,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaLibrarySession.Builder(this, created, libraryCallback)
            .setSessionActivity(action).build()
        Log.i(TAG, "Media3 playback library session created")
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession? {
        Log.i(TAG, "Media3 session requested; available=" + (session != null))
        return session
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        val current = player
        if (current?.currentMediaItem != null) {
            current.pause()
            if (current.playbackState != Player.STATE_IDLE) {
                MusicPlaybackRetention.saveQueue(this, current)
                MusicPlaybackRetention.checkpoint(this, current, true)
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        if (activeService.get() === this) activeService = WeakReference(null)
        checkpointHandler.removeCallbacks(checkpointTicker)
        player?.let { current ->
            if (current.currentMediaItem != null &&
                current.playbackState != Player.STATE_IDLE) {
                MusicPlaybackRetention.saveQueue(this, current)
                MusicPlaybackRetention.checkpoint(this, current, true)
            }
            current.removeListener(retentionListener)
        }
        session?.release()
        session = null
        player?.release()
        player = null
        Log.i(TAG, "Media3 playback service destroyed")
        super.onDestroy()
    }
}
