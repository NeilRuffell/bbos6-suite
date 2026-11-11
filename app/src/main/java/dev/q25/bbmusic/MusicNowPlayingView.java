package dev.q25.bbmusic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

import dev.q25.bbos6.ui.Bbos6FocusPolicy;
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingPanel;
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingSurfaceView;
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingTextAlignment;
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingControlKind;
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingControlSelectionView;
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingSeekRailView;

/**
 * Standalone presentation port of the BB UI shade's expanded Now Playing:
 * proportionally sized header with Repeat on the left, queue position in the
 * center and Shuffle on the right; flexible half-radial artwork/metadata,
 * seek strip and four-button transport. MusicPlayerChrome defines shared
 * height ratios and drawables; HalfRadialGradientDrawable draws the art stage.
 *
 * Trackpad moves vertically through modes, artist, album, seek and
 * transport, skipping absent metadata. Enter activates the focused link or
 * control; left/right seeks only while seek editing is enabled. This remains
 * a View, not a SystemUI hook or independent player.
 */
final class MusicNowPlayingView extends LinearLayout {
    private static final int ZONE_MODE = 0;
    private static final int ZONE_ARTIST = 1;
    private static final int ZONE_ALBUM = 2;
    private static final int ZONE_SEEK = 3;
    private static final int ZONE_TRANSPORT = 4;

    interface MetadataNavigation {
        CharSequence albumFor(MediaItem item);
        void openArtist();
        void openAlbum();
    }

    private final MusicPlaybackClient client;
    private final MetadataNavigation metadataNavigation;
    private final MusicArtworkLoader artworkLoader;
    private final ImageView modeShuffle;
    private final Bbos6NowPlayingControlSelectionView modeShuffleFace;
    private final Bbos6NowPlayingControlSelectionView modeRepeatFace;
    private final ImageView modeRepeat;
    private final MusicPlayerTextView queuePosition;
    private final MusicNowPlayingArtworkView artworkImage;
    private final MusicPlayerTextView trackTitle;
    private final MusicBeveledTextLabel trackArtist;
    private final MusicBeveledTextLabel trackAlbum;
    private final MusicPlayerTextView elapsed;
    private final MusicPlayerTextView remaining;
    private final SeekBar seekBar;
    private final Bbos6NowPlayingSeekRailView seekRail;
    private final LinearLayout transportRow;
    private final ImageView[] transportButtons = new ImageView[4];
    private final FrameLayout[] transportSlots = new FrameLayout[4];
    private final Bbos6NowPlayingControlSelectionView[] transportFaces =
            new Bbos6NowPlayingControlSelectionView[4];
    private final Handler ticker = new Handler(Looper.getMainLooper());

    private int zone = ZONE_TRANSPORT;
    private int selectedButton = 1;
    private int selectedMode;
    private boolean seekEditing;
    private boolean seekDragging;
    private String shownArtworkUri;
    private long durationMs;
    private boolean active = true;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active) return;
            renderProgress();
            ticker.postDelayed(this, 1000);
        }
    };

    MusicNowPlayingView(Context context, MusicPlaybackClient client,
            MetadataNavigation metadataNavigation) {
        super(context);
        this.client = client;
        this.metadataNavigation = metadataNavigation;
        artworkLoader = new MusicArtworkLoader(context);
        setOrientation(VERTICAL);
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
        setFocusableInTouchMode(true);

        // Four chrome panels render through the app-neutral shared Compose
        // surfaces; Android Views on top keep original input and focus rules.
        FrameLayout modePanel = new FrameLayout(context);
        modePanel.addView(new Bbos6NowPlayingSurfaceView(
                context, Bbos6NowPlayingPanel.MODE), new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        LinearLayout modeHeader = new LinearLayout(context);
        modeHeader.setOrientation(HORIZONTAL);
        modeHeader.setGravity(Gravity.CENTER_VERTICAL);
        modeHeader.setPadding(0, 0, 0, 0);
        modePanel.addView(modeHeader, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(modePanel, new LayoutParams(LayoutParams.MATCH_PARENT,
                dp(MusicPlayerChrome.MODE_HEADER_HEIGHT_DP)));

        // The shared Compose highlight is passive and sits beneath the
        // original click-owning ImageView. Keep both 48dp mode touch slots.
        FrameLayout repeatSlot = new FrameLayout(context);
        modeRepeatFace = new Bbos6NowPlayingControlSelectionView(
                context, Bbos6NowPlayingControlKind.MODE);
        repeatSlot.addView(modeRepeatFace, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        // Repeat occupies the left-hand header slot formerly used by "Media".
        modeRepeat = new ImageView(context);
        modeRepeat.setScaleType(ImageView.ScaleType.FIT_CENTER);
        modeRepeat.setPadding(0, 0, 0, 0);
        modeRepeat.setContentDescription("Repeat unavailable");
        modeRepeat.setOnClickListener(view -> {
            zone = ZONE_MODE;
            selectedMode = 0;
            requestFocus();
            client.toggleRepeat();
            render();
        });
        repeatSlot.addView(modeRepeat, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        modeHeader.addView(repeatSlot, new LayoutParams(
                dp(48), LayoutParams.MATCH_PARENT));

        // Keep the count mathematically centered between equal-width buttons.
        // It is informational only and does not enter the trackpad focus order.
        queuePosition = new MusicPlayerTextView(context, "0 of 0", 14f,
                Color.WHITE, Bbos6NowPlayingTextAlignment.CENTER,
                1, 0, true, true);
        modeHeader.addView(queuePosition, new LayoutParams(
                0, LayoutParams.MATCH_PARENT, 1f));

        FrameLayout shuffleSlot = new FrameLayout(context);
        modeShuffleFace = new Bbos6NowPlayingControlSelectionView(
                context, Bbos6NowPlayingControlKind.MODE);
        shuffleSlot.addView(modeShuffleFace, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        modeShuffle = new ImageView(context);
        modeShuffle.setScaleType(ImageView.ScaleType.FIT_CENTER);
        modeShuffle.setPadding(0, 0, 0, 0);
        modeShuffle.setContentDescription("Shuffle unavailable");
        modeShuffle.setOnClickListener(view -> {
            zone = ZONE_MODE;
            selectedMode = 1;
            requestFocus();
            client.toggleShuffle();
            render();
        });
        shuffleSlot.addView(modeShuffle, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        modeHeader.addView(shuffleSlot, new LayoutParams(
                dp(48), LayoutParams.MATCH_PARENT));

        // All remaining viewport height belongs to the artwork/metadata stage;
        // mode header, seek strip and transport heights share the reference
        // proportions in MusicPlayerChrome.
        FrameLayout artStage = new FrameLayout(context);
        artStage.addView(new Bbos6NowPlayingSurfaceView(
                context, Bbos6NowPlayingPanel.ARTWORK),
                new FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(artStage, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout artInfo = new LinearLayout(context);
        artInfo.setOrientation(HORIZONTAL);
        artInfo.setGravity(Gravity.CENTER_VERTICAL);
        artInfo.setPadding(dp(10), dp(8), dp(10), dp(8));
        artInfo.setClipChildren(false);
        artStage.addView(artInfo, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        FrameLayout artArea = new FrameLayout(context);
        artInfo.addView(artArea, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        // Shared FIT_CENTER Compose presentation; same loader and fallback.
        artworkImage = new MusicNowPlayingArtworkView(context);
        artworkImage.setContentDescription("Album artwork");
        artArea.addView(artworkImage, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        // Same original BBOS6 543.png fallback used in album browse lists.
        // This is replaced only when embedded cover artwork is decoded.
        artworkImage.setImageResource(R.drawable.bbos6_album_no_art);

        LinearLayout trackInfo = new LinearLayout(context);
        trackInfo.setOrientation(VERTICAL);
        trackInfo.setGravity(Gravity.CENTER_VERTICAL);
        trackInfo.setPadding(dp(14), dp(8), dp(8), dp(8));
        trackInfo.setClipChildren(false);
        trackInfo.setClipToPadding(false);
        artInfo.addView(trackInfo, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        // Centre single- and multi-line titles in the same metadata column.
        trackTitle = new MusicPlayerTextView(context, "Now playing", 18f,
                Color.WHITE, Bbos6NowPlayingTextAlignment.CENTER,
                3, 0, true, false);
        trackInfo.addView(trackTitle, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // The original BBOS6 metadata text sits on independent, subtly raised
        // plates sized to its contents. The song title remains undecorated.
        trackArtist = new MusicBeveledTextLabel(context, 13);
        trackArtist.setFocusable(false);
        trackArtist.setOnClickListener(view -> activateMetadata(ZONE_ARTIST));
        LayoutParams artistParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        artistParams.gravity = Gravity.CENTER_HORIZONTAL;
        artistParams.topMargin = dp(6);
        trackInfo.addView(trackArtist, artistParams);

        trackAlbum = new MusicBeveledTextLabel(context, 12);
        trackAlbum.setFocusable(false);
        trackAlbum.setOnClickListener(view -> activateMetadata(ZONE_ALBUM));
        LayoutParams albumParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        albumParams.gravity = Gravity.CENTER_HORIZONTAL;
        albumParams.topMargin = dp(4);
        trackInfo.addView(trackAlbum, albumParams);

        LinearLayout seekRow = new LinearLayout(context);
        seekRow.setOrientation(HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        // Curve 9300 reference: time labels sit near the display edges.
        // The SeekBar owns its dead zones, not the entire seek row.
        seekRow.setPadding(dp(4), 0, dp(4), 0);
        FrameLayout seekPanel = new FrameLayout(context);
        seekPanel.addView(new Bbos6NowPlayingSurfaceView(
                context, Bbos6NowPlayingPanel.SEEK),
                new FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        seekPanel.addView(seekRow, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(seekPanel, new LayoutParams(LayoutParams.MATCH_PARENT,
                dp(MusicPlayerChrome.SEEK_PANEL_HEIGHT_DP)));

        // Reserve just enough for the label; use WRAP_CONTENT for hour-long
        // tracks instead of a hard slot that can clip text.
        elapsed = new MusicPlayerTextView(context, "0:00", 14f, 0xff99aab8,
                Bbos6NowPlayingTextAlignment.START, 1, 34, false, true);
        seekRow.addView(elapsed, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        // Compose paints the complete rail below the unmodified native
        // SeekBar thumb and gesture target, in the same width/height bounds.
        seekRail = new Bbos6NowPlayingSeekRailView(context,
                MediaSeekPolicy.PROGRESS_MAX,
                MusicPlayerChrome.SEEK_END_GUARD_DP,
                MusicPlayerChrome.SEEK_TRACK_HEIGHT_DP);
        seekBar = new SeekBar(context);
        seekBar.setMax(MediaSeekPolicy.PROGRESS_MAX);
        // The shared Compose canvas renders the full-width progress rail.
        // SeekBar retains only the original native pill/seek interaction.
        seekBar.setBackground(null);
        seekBar.setFocusable(false);
        seekBar.setFocusableInTouchMode(false);
        // Native thumb travel is inset by half a pill + 2dp. With the
        // matching thumbOffset, the pill's outer edges meet the full rail
        // endpoints at 0% and 100%, without overlapping the time labels.
        int thumbHalf = dp(MusicPlayerChrome.SEEK_THUMB_WIDTH_DP) / 2;
        int thumbInset = thumbHalf + dp(MusicPlayerChrome.SEEK_END_GUARD_DP);
        seekBar.setPadding(thumbInset, 0, thumbInset, 0);
        seekBar.setThumbOffset(thumbHalf);
        seekBar.setProgressTintList(null);
        seekBar.setProgressBackgroundTintList(null);
        seekBar.setSecondaryProgressTintList(null);
        // Hide the native progress layer: it is clipped to the padded travel
        // region and cannot share the background rail's endpoints.
        seekBar.setProgressDrawable(new ColorDrawable(Color.TRANSPARENT));
        seekBar.setThumbTintList(null);
        seekBar.setThumb(MusicPlayerChrome.seekThumb(context, false, false));
        seekBar.setContentDescription("Playback position");
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {
                seekDragging = true;
                zone = ZONE_SEEK;
                seekEditing = false;
                requestFocus();
                renderFocus();
            }

            @Override public void onProgressChanged(SeekBar bar,
                    int progress, boolean fromUser) {
                // Progress now paints in the full-width SeekBar background.
                // Explicit invalidation also handles programmatic ticks.
                bar.invalidate();
                seekRail.updateProgress(progress);
                if (fromUser && seekDragging) {
                    long previewPosition = MediaSeekPolicy.fromProgress(
                            progress, durationMs);
                    elapsed.setText(MediaSeekPolicy.formatTime(previewPosition));
                    remaining.setText(MediaSeekPolicy.formatRemainingTime(
                            durationMs, previewPosition));
                }
            }

            @Override public void onStopTrackingTouch(SeekBar bar) {
                seekDragging = false;
                client.seek(MediaSeekPolicy.fromProgress(bar.getProgress(), durationMs));
                renderProgress();
            }
        });
        // Fill every remaining pixel between labels; keep only a narrow
        // 2dp separation on each side of the SeekBar's protected bounds.
        LayoutParams seekParams = new LayoutParams(
                0, LayoutParams.MATCH_PARENT, 1f);
        seekParams.leftMargin = dp(2);
        seekParams.rightMargin = dp(2);
        FrameLayout seekTrack = new FrameLayout(context);
        seekTrack.addView(seekRail, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        seekTrack.addView(seekBar, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        seekRow.addView(seekTrack, seekParams);

        remaining = new MusicPlayerTextView(context, "--:--", 14f, 0xff99aab8,
                Bbos6NowPlayingTextAlignment.END, 1, 42, false, true);
        seekRow.addView(remaining, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        transportRow = new LinearLayout(context);
        transportRow.setGravity(Gravity.CENTER_VERTICAL);
        transportRow.setOrientation(HORIZONTAL);
        FrameLayout transportPanel = new FrameLayout(context);
        transportPanel.addView(new Bbos6NowPlayingSurfaceView(
                context, Bbos6NowPlayingPanel.TRANSPORT),
                new FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        transportPanel.addView(transportRow, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(transportPanel, new LayoutParams(LayoutParams.MATCH_PARENT,
                dp(MusicPlayerChrome.TRANSPORT_HEIGHT_DP)));

        String[] descriptions = {"Previous", "Play or pause", "Stop", "Next"};
        for (int i = 0; i < transportButtons.length; i++) {
            final int buttonIndex = i;
            FrameLayout slot = new FrameLayout(context);
            Bbos6NowPlayingControlSelectionView selectionFace =
                    new Bbos6NowPlayingControlSelectionView(
                            context, Bbos6NowPlayingControlKind.TRANSPORT);
            slot.addView(selectionFace, new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            ImageView button = new ImageView(context);
            button.setScaleType(ImageView.ScaleType.CENTER);
            button.setImageDrawable(MusicPlayerChrome.transportIcon(context, i, false));
            // A 36dp transport glyph fits the compact 48dp button with
            // 6dp vertical insets; horizontal padding is unchanged.
            button.setPadding(dp(10), dp(6), dp(10), dp(6));
            button.setContentDescription(descriptions[i]);
            button.setOnClickListener(view -> {
                zone = ZONE_TRANSPORT;
                selectedButton = buttonIndex;
                seekEditing = false;
                requestFocus();
                activate();
            });
            transportButtons[i] = button;
            transportFaces[i] = selectionFace;
            transportSlots[i] = slot;
            slot.addView(button, new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            transportRow.addView(slot, new LayoutParams(
                    0, LayoutParams.MATCH_PARENT, 1f));
        }
        transportRow.setForeground(
                MusicPlayerChrome.transportSeparators(context, transportSlots));
        // The view owns trackpad focus and draws its own BBOS6 selection.
        // Never let Android paint its default highlight across the player.
        Bbos6FocusPolicy.forManagedContainers(this);
        render();
        ticker.postDelayed(tick, 1000);
    }

    private int dp(float value) {
        return MusicPlayerChrome.dp(getContext(), value);
    }

    void render() {
        if (!active) return;
        MediaController controller = client.controller();
        MediaItem item = controller == null ? null : controller.getCurrentMediaItem();
        if (item == null) {
            trackTitle.setText("Now playing");
            trackArtist.setText("");
            trackArtist.setVisibility(View.GONE);
            trackAlbum.setText("");
            trackAlbum.setVisibility(View.GONE);
            updateArtwork(null);
        } else {
            CharSequence title = item.mediaMetadata.title;
            CharSequence artist = item.mediaMetadata.artist;
            // Live Media3 queues restored from older versions can still carry
            // the synthetic label. Resolve through the verified local catalog
            // when available, preserving a genuinely tagged "Unknown Album".
            CharSequence album = metadataNavigation.albumFor(item);
            trackTitle.setText(title == null || title.length() == 0
                    ? "Now playing" : title);
            trackArtist.setText(artist == null ? "" : artist);
            trackArtist.setVisibility(artist == null || artist.length() == 0
                    ? View.GONE : View.VISIBLE);
            trackAlbum.setText(album == null ? "" : album);
            trackAlbum.setVisibility(album == null || album.length() == 0
                    ? View.GONE : View.VISIBLE);
            // A MediaSession may omit LocalConfiguration from the
            // controller-facing MediaItem. Our own mediaId preserves the
            // exact SAF URI already approved by MusicFolders.
            Uri artworkSource = item.localConfiguration == null
                    ? null : item.localConfiguration.uri;
            if (artworkSource == null && item.mediaId.startsWith("content://")) {
                artworkSource = Uri.parse(item.mediaId);
            }
            updateArtwork(artworkSource);
        }
        // Media3 supplies the current item index and total for the active
        // playback queue, not the size of the scanned music library.
        int queueSize = controller == null ? 0 : controller.getMediaItemCount();
        int index = controller == null || item == null
                ? C.INDEX_UNSET : controller.getCurrentMediaItemIndex();
        int position = index >= 0 && index < queueSize ? index + 1 : 0;
        queuePosition.setText(position + " of " + queueSize);

        boolean playing = controller != null && controller.getPlayWhenReady();
        transportButtons[1].setImageDrawable(
                MusicPlayerChrome.transportIcon(getContext(), 1, playing));
        transportButtons[1].setContentDescription(playing ? "Pause" : "Play");
        renderProgress();
        renderFocus();
    }

    private void updateArtwork(Uri uri) {
        String key = uri == null ? "" : uri.toString();
        if (key.equals(shownArtworkUri)) return;
        shownArtworkUri = key;
        // Display the bundled original cover whenever embedded art is absent.
        // Reset immediately so a previous track's art is never shown for a
        // new track while the metadata loader is still working.
        artworkImage.setImageResource(R.drawable.bbos6_album_no_art);
        artworkLoader.load(uri, bitmap -> {
            if (!active || !key.equals(shownArtworkUri)) return;
            if (bitmap != null) artworkImage.setImageBitmap(bitmap);
            // A null result keeps the already displayed BBOS6 fallback.
        });
    }

    private void renderProgress() {
        if (!active || seekDragging) return;
        MediaController controller = client.controller();
        if (controller == null) {
            durationMs = 0L;
            elapsed.setText("0:00");
            remaining.setText("--:--");
            seekBar.setProgress(0);
            seekRail.updateProgress(0);
            seekBar.setEnabled(false);
            seekRail.setAlpha(seekBar.getAlpha());
            return;
        }
        long duration = controller.getDuration();
        durationMs = duration == C.TIME_UNSET || duration < 0L ? 0L : duration;
        long position = Math.max(0L, controller.getCurrentPosition());
        elapsed.setText(MediaSeekPolicy.formatTime(position));
        remaining.setText(MediaSeekPolicy.formatRemainingTime(
                durationMs, position));
        seekBar.setProgress(MediaSeekPolicy.toProgress(position, durationMs));
        seekRail.updateProgress(seekBar.getProgress());
        boolean canSeek = durationMs > 0L && controller.isCurrentMediaItemSeekable();
        seekBar.setEnabled(canSeek);
        seekBar.setAlpha(canSeek ? 1f : 0.4f);
        seekRail.setAlpha(canSeek ? 1f : 0.4f);
        if (!canSeek && seekEditing) {
            seekEditing = false;
            renderFocus();
        }
    }

    private void renderFocus() {
        if (!zoneAvailable(zone)) zone = ZONE_SEEK;
        boolean focused = hasFocus();
        trackArtist.setMetadataFocused(focused && zone == ZONE_ARTIST);
        trackAlbum.setMetadataFocused(focused && zone == ZONE_ALBUM);
        MediaController controller = client.controller();
        boolean shuffleAvailable = controller != null
                && controller.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE);
        boolean shuffle = shuffleAvailable && controller.getShuffleModeEnabled();
        boolean shuffleFocused = shuffleAvailable && focused
                && zone == ZONE_MODE && selectedMode == 1;
        modeShuffle.setImageResource(!shuffleAvailable
                ? R.drawable.bbos6_shuffle_unavailable
                : shuffle ? R.drawable.bbos6_shuffle_on
                : R.drawable.bbos6_shuffle_off);
        modeShuffle.setContentDescription(!shuffleAvailable ? "Shuffle unavailable"
                : shuffle ? "Shuffle on" : "Shuffle off");
        modeShuffle.setEnabled(shuffleAvailable);
        // No persistent active-state background: highlight only the focused
        // Shuffle control with the exact same selection as library list rows.
        modeShuffleFace.setHighlighted(shuffleFocused);
        // Preserve the supplied PNG colors, including the unavailable variant.
        modeShuffle.setAlpha(1f);
        boolean repeatAvailable = controller != null
                && controller.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE);
        int repeatMode = repeatAvailable ? controller.getRepeatMode()
                : Player.REPEAT_MODE_OFF;
        modeRepeat.setImageResource(!repeatAvailable
                ? R.drawable.bbos6_repeat_unavailable
                : repeatMode == Player.REPEAT_MODE_ONE
                ? R.drawable.bbos6_repeat_one
                : repeatMode == Player.REPEAT_MODE_ALL
                ? R.drawable.bbos6_repeat_all
                : R.drawable.bbos6_repeat_off);
        modeRepeat.setContentDescription(!repeatAvailable ? "Repeat unavailable"
                : repeatMode == Player.REPEAT_MODE_ONE ? "Repeat one"
                : repeatMode == Player.REPEAT_MODE_ALL ? "Repeat all"
                : "Repeat off");
        modeRepeat.setEnabled(repeatAvailable);
        boolean repeatFocused = repeatAvailable && focused
                && zone == ZONE_MODE && selectedMode == 0;
        modeRepeatFace.setHighlighted(repeatFocused);
        modeRepeat.setAlpha(1f);
        seekBar.setThumb(MusicPlayerChrome.seekThumb(getContext(),
                focused && zone == ZONE_SEEK,
                focused && zone == ZONE_SEEK && seekEditing));
        for (int i = 0; i < transportButtons.length; i++) {
            boolean selected = focused && zone == ZONE_TRANSPORT && selectedButton == i;
            ImageView button = transportButtons[i];
            transportFaces[i].setHighlighted(selected);
            boolean enabled = controller != null
                    && (i == 0 ? controller.hasPreviousMediaItem()
                    : i == 3 ? controller.hasNextMediaItem()
                    : controller.getCurrentMediaItem() != null);
            button.setAlpha(enabled ? 1f : 0.35f);
        }
    }

    private boolean zoneAvailable(int candidate) {
        if (candidate == ZONE_ARTIST) {
            return trackArtist.getVisibility() == View.VISIBLE;
        }
        if (candidate == ZONE_ALBUM) {
            return trackAlbum.getVisibility() == View.VISIBLE;
        }
        return candidate >= ZONE_MODE && candidate <= ZONE_TRANSPORT;
    }

    private void activateMetadata(int targetZone) {
        if (!active || !zoneAvailable(targetZone)) return;
        zone = targetZone;
        seekEditing = false;
        requestFocus();
        renderFocus();
        if (targetZone == ZONE_ARTIST) metadataNavigation.openArtist();
        else metadataNavigation.openAlbum();
    }

    void directional(int code) {
        if (code == KeyEvent.KEYCODE_DPAD_UP
                || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (seekEditing) return;
            int delta = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
            // The BBOS6 metadata links occupy two vertical positions
            // between Repeat/Shuffle and Seek. Missing tags are skipped.
            for (int next = zone + delta;
                    next >= ZONE_MODE && next <= ZONE_TRANSPORT; next += delta) {
                if (zoneAvailable(next)) {
                    zone = next;
                    break;
                }
            }
        } else if (code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
            if (zone == ZONE_SEEK) {
                if (seekEditing) {
                    MediaController controller = client.controller();
                    if (controller != null && durationMs > 0L) {
                        client.seek(MediaSeekPolicy.seekStep(
                                controller.getCurrentPosition(), durationMs, step));
                    }
                }
            } else if (zone == ZONE_MODE) {
                selectedMode = Math.max(0, Math.min(1, selectedMode + step));
            } else if (zone == ZONE_TRANSPORT) {
                selectedButton = Math.max(0, Math.min(transportButtons.length - 1,
                        selectedButton + step));
            }
        }
        renderFocus();
    }

    void activate() {
        if (zone == ZONE_ARTIST || zone == ZONE_ALBUM) {
            activateMetadata(zone);
            return;
        }
        if (zone == ZONE_MODE) {
            MediaController controller = client.controller();
            if (selectedMode == 0) {
                if (controller != null
                        && controller.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)) {
                    client.toggleRepeat();
                }
            } else {
                if (controller != null
                        && controller.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) {
                    client.toggleShuffle();
                }
            }
        } else if (zone == ZONE_SEEK) {
            MediaController controller = client.controller();
            boolean canSeek = controller != null && durationMs > 0L
                    && controller.isCurrentMediaItemSeekable();
            seekEditing = MediaSeekPolicy.toggleTrackpadEditing(
                    true, seekEditing, canSeek);
        } else {
            if (selectedButton == 0) client.previous();
            else if (selectedButton == 1) client.playPause();
            else if (selectedButton == 2) client.stop();
            else client.next();
        }
        render();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        renderFocus();
    }

    void dispose() {
        active = false;
        ticker.removeCallbacks(tick);
        artworkLoader.close();
        artworkImage.clear();
    }
}
