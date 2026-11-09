package dev.q25.bbmusic;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.List;

/**
 * The single Media3 playback authority. The MediaLibrarySession also supports
 * Android media-button/system playback resumption after process death/reboot.
 * All restorable media comes from still-authorized user-selected SAF trees.
 */
public final class MusicPlaybackService extends MediaLibraryService {
    private static final String TAG = "BBMusic-Service";
    private static final String LIBRARY_ROOT = "bbmusic-recent-queue";
    private static final long CHECKPOINT_INTERVAL_MS = 5000L;

    private final Handler checkpointHandler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private MediaLibrarySession session;

    private final Runnable checkpointTicker = new Runnable() {
        @Override public void run() {
            if (player != null && player.isPlaying()) {
                MusicPlaybackRetention.checkpoint(MusicPlaybackService.this,
                        player, false);
                checkpointHandler.postDelayed(this, CHECKPOINT_INTERVAL_MS);
            }
        }
    };

    private final Player.Listener retentionListener = new Player.Listener() {
        @Override public void onEvents(Player current, Player.Events events) {
            checkpointHandler.removeCallbacks(checkpointTicker);

            // Explicit Stop moves the retained Media3 item to IDLE. It must
            // not leave a resumable card in the OS media controls.
            if (current.getPlaybackState() == Player.STATE_IDLE
                    && events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                MusicPlaybackRetention.clear(MusicPlaybackService.this);
                return;
            }

            if (current.getMediaItemCount() > 0) {
                if (events.contains(Player.EVENT_TIMELINE_CHANGED)
                        || (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                        && current.getPlaybackState() == Player.STATE_BUFFERING)) {
                    MusicPlaybackRetention.saveQueue(MusicPlaybackService.this,
                            current);
                } else if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)
                        || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                        || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                        || events.contains(Player.EVENT_REPEAT_MODE_CHANGED)
                        || events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
                        || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    MusicPlaybackRetention.checkpoint(MusicPlaybackService.this,
                            current, !current.getPlayWhenReady());
                }
            }
            if (current.isPlaying()) {
                checkpointHandler.postDelayed(checkpointTicker,
                        CHECKPOINT_INTERVAL_MS);
            }
        }

        @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
            // Do not perpetually offer a broken/unavailable document.
            Log.w(TAG, "Playback source failed; clearing retention", error);
            MusicPlaybackRetention.clear(MusicPlaybackService.this);
        }
    };

    private MediaItem libraryRoot() {
        return new MediaItem.Builder().setMediaId(LIBRARY_ROOT)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle("Music")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build())
                .build();
    }

    /** The recent queue is the only browsable subtree: never scan other files. */
    private List<MediaItem> recentQueue() {
        // Even an already-running session must not expose a queue from a tree
        // whose saved selection/read permission has since been revoked.
        MusicPlaybackRetention.Saved saved =
                MusicPlaybackRetention.restore(this);
        return saved == null ? Collections.emptyList() : saved.items;
    }

    private final MediaLibrarySession.Callback callback = new MediaLibrarySession.Callback() {
        @Override
        public ListenableFuture<MediaSession.MediaItemsWithStartPosition>
                onPlaybackResumption(MediaSession mediaSession,
                        MediaSession.ControllerInfo controller, boolean isForPlayback) {
            MusicPlaybackRetention.Saved saved =
                    MusicPlaybackRetention.restore(MusicPlaybackService.this);
            if (saved == null) {
                return Futures.immediateFuture(
                        new MediaSession.MediaItemsWithStartPosition(
                                Collections.emptyList(), C.INDEX_UNSET, C.TIME_UNSET));
            }

            // For Android's boot-time system card, a single metadata-rich item
            // is sufficient. A real Bluetooth/system Play gets the full queue.
            if (!isForPlayback) {
                return Futures.immediateFuture(
                        new MediaSession.MediaItemsWithStartPosition(
                                Collections.singletonList(saved.items.get(saved.index)),
                                0, saved.positionMs));
            }
            if (player != null) {
                player.setShuffleModeEnabled(saved.shuffle);
                player.setRepeatMode(saved.repeat);
            }
            return Futures.immediateFuture(
                    new MediaSession.MediaItemsWithStartPosition(
                            saved.items, saved.index, saved.positionMs));
        }

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                MediaLibrarySession mediaSession,
                MediaSession.ControllerInfo browser, LibraryParams params) {
            return Futures.immediateFuture(LibraryResult.ofItem(libraryRoot(), params));
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                MediaLibrarySession mediaSession,
                MediaSession.ControllerInfo browser, String parentId,
                int page, int pageSize, LibraryParams params) {
            List<MediaItem> queue = LIBRARY_ROOT.equals(parentId)
                    ? recentQueue() : Collections.emptyList();
            long offset = (long) page * pageSize;
            int start = (int) Math.min(queue.size(), offset);
            int end = (int) Math.min(queue.size(), offset + pageSize);
            return Futures.immediateFuture(
                    LibraryResult.ofItemList(queue.subList(start, end), params));
        }

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                MediaLibrarySession mediaSession,
                MediaSession.ControllerInfo browser, String mediaId) {
            if (LIBRARY_ROOT.equals(mediaId)) {
                return Futures.immediateFuture(LibraryResult.ofItem(libraryRoot(), null));
            }
            for (MediaItem item : recentQueue()) {
                if (item.mediaId.equals(mediaId)) {
                    return Futures.immediateFuture(LibraryResult.ofItem(item, null));
                }
            }
            return MediaLibrarySession.Callback.super.onGetItem(
                    mediaSession, browser, mediaId);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // Preserve existing BBOS Stop behaviour: no stopped media notification.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER);
        // All indexed music is progressive SAF audio, not HLS/DASH.
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new ProgressiveMediaSource.Factory(
                        new DefaultDataSource.Factory(this)))
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        player.addListener(retentionListener);
        Intent openMusic = new Intent(this, MusicActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openMusicAction = PendingIntent.getActivity(this, 0, openMusic,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaLibrarySession.Builder(this, player, callback)
                .setSessionActivity(openMusicAction)
                .build();
        Log.i(TAG, "Media3 playback library session created");
    }

    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        Log.i(TAG, "Media3 session requested; available=" + (session != null));
        return session;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Recents dismissal closes playback, unlike merely pressing Home.
        if (player != null && player.getCurrentMediaItem() != null) {
            player.pause();
            if (player.getPlaybackState() != Player.STATE_IDLE) {
                MusicPlaybackRetention.saveQueue(this, player);
                MusicPlaybackRetention.checkpoint(this, player, true);
            }
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        checkpointHandler.removeCallbacks(checkpointTicker);
        if (player != null) {
            if (player.getCurrentMediaItem() != null
                    && player.getPlaybackState() != Player.STATE_IDLE) {
                MusicPlaybackRetention.saveQueue(this, player);
                MusicPlaybackRetention.checkpoint(this, player, true);
            }
            player.removeListener(retentionListener);
        }
        if (session != null) {
            session.release();
            session = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        Log.i(TAG, "Media3 playback service destroyed");
        super.onDestroy();
    }
}
