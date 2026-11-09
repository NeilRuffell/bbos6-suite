package dev.q25.bbmusic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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
        void openArtist();
        void openAlbum();
    }

    private final MusicPlaybackClient client;
    private final MetadataNavigation metadataNavigation;
    private final MusicArtworkLoader artworkLoader;
    private final ImageView modeShuffle;
    private final ImageView modeRepeat;
    private final TextView queuePosition;
    private final ImageView artworkImage;
    private final TextView trackTitle;
    private final MusicBeveledTextLabel trackArtist;
    private final MusicBeveledTextLabel trackAlbum;
    private final TextView elapsed;
    private final TextView remaining;
    private final SeekBar seekBar;
    private final LinearLayout transportRow;
    private final ImageView[] transportButtons = new ImageView[4];
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

        LinearLayout modeHeader = new LinearLayout(context);
        modeHeader.setOrientation(HORIZONTAL);
        modeHeader.setGravity(Gravity.CENTER_VERTICAL);
        modeHeader.setPadding(0, 0, 0, 0);
        modeHeader.setBackground(MusicPlayerChrome.modeHeaderBackground(context));
        addView(modeHeader, new LayoutParams(LayoutParams.MATCH_PARENT,
                dp(MusicPlayerChrome.MODE_HEADER_HEIGHT_DP)));

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
        modeHeader.addView(modeRepeat, new LayoutParams(
                dp(48), LayoutParams.MATCH_PARENT));

        // Keep the count mathematically centered between equal-width buttons.
        // It is informational only and does not enter the trackpad focus order.
        queuePosition = text("0 of 0", 14, Color.WHITE);
        queuePosition.setGravity(Gravity.CENTER);
        queuePosition.setSingleLine(true);
        modeHeader.addView(queuePosition, new LayoutParams(
                0, LayoutParams.MATCH_PARENT, 1f));

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
        modeHeader.addView(modeShuffle, new LayoutParams(
                dp(48), LayoutParams.MATCH_PARENT));

        // All remaining viewport height belongs to the artwork/metadata stage;
        // mode header, seek strip and transport heights share the reference
        // proportions in MusicPlayerChrome.
        FrameLayout artStage = new FrameLayout(context);
        artStage.setBackground(new HalfRadialGradientDrawable());
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

        artworkImage = new ImageView(context);
        artworkImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        artworkImage.setPadding(dp(6), dp(6), dp(6), dp(6));
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

        trackTitle = text("Now playing", 18, Color.WHITE);
        trackTitle.setMaxLines(3);
        trackTitle.setEllipsize(TextUtils.TruncateAt.END);
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
        seekRow.setBackground(MusicPlayerChrome.seekBackground(context));
        addView(seekRow, new LayoutParams(LayoutParams.MATCH_PARENT,
                dp(MusicPlayerChrome.SEEK_PANEL_HEIGHT_DP)));

        elapsed = text("0:00", 14, 0xff99aab8);
        elapsed.setGravity(Gravity.CENTER_VERTICAL);
        // Reserve just enough for the label; use WRAP_CONTENT for hour-long
        // tracks instead of a hard slot that can clip text.
        elapsed.setMinWidth(dp(34));
        seekRow.addView(elapsed, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        seekBar = new SeekBar(context);
        seekBar.setMax(MediaSeekPolicy.PROGRESS_MAX);
        // A single shared background renders the full dark rail AND the white
        // completed portion; only the pill uses native padded travel bounds.
        seekBar.setBackground(MusicPlayerChrome.seekRailBackground(
                context, seekBar));
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
        seekRow.addView(seekBar, seekParams);

        remaining = text("--:--", 14, 0xff99aab8);
        remaining.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        remaining.setMinWidth(dp(42));
        seekRow.addView(remaining, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        transportRow = new LinearLayout(context);
        transportRow.setGravity(Gravity.CENTER_VERTICAL);
        transportRow.setOrientation(HORIZONTAL);
        transportRow.setBackground(MusicPlayerChrome.transportBackground());
        addView(transportRow, new LayoutParams(LayoutParams.MATCH_PARENT,
                dp(MusicPlayerChrome.TRANSPORT_HEIGHT_DP)));

        String[] descriptions = {"Previous", "Play or pause", "Stop", "Next"};
        for (int i = 0; i < transportButtons.length; i++) {
            final int buttonIndex = i;
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
            transportRow.addView(button, new LayoutParams(
                    0, LayoutParams.MATCH_PARENT, 1f));
        }
        transportRow.setForeground(
                MusicPlayerChrome.transportSeparators(context, transportButtons));
        render();
        ticker.postDelayed(tick, 1000);
    }

    private TextView text(String value, float size, int color) {
        TextView result = new TextView(getContext());
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        return result;
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
            CharSequence album = item.mediaMetadata.albumTitle;
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
            seekBar.setEnabled(false);
            return;
        }
        long duration = controller.getDuration();
        durationMs = duration == C.TIME_UNSET || duration < 0L ? 0L : duration;
        long position = Math.max(0L, controller.getCurrentPosition());
        elapsed.setText(MediaSeekPolicy.formatTime(position));
        remaining.setText(MediaSeekPolicy.formatRemainingTime(
                durationMs, position));
        seekBar.setProgress(MediaSeekPolicy.toProgress(position, durationMs));
        boolean canSeek = durationMs > 0L && controller.isCurrentMediaItemSeekable();
        seekBar.setEnabled(canSeek);
        seekBar.setAlpha(canSeek ? 1f : 0.4f);
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
        modeShuffle.setBackground(shuffleFocused
                ? MusicPlayerChrome.modeSelectionBackground(getContext()) : null);
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
        modeRepeat.setBackground(repeatFocused
                ? MusicPlayerChrome.modeSelectionBackground(getContext()) : null);
        modeRepeat.setAlpha(1f);
        seekBar.setThumb(MusicPlayerChrome.seekThumb(getContext(),
                focused && zone == ZONE_SEEK,
                focused && zone == ZONE_SEEK && seekEditing));
        for (int i = 0; i < transportButtons.length; i++) {
            boolean selected = focused && zone == ZONE_TRANSPORT && selectedButton == i;
            ImageView button = transportButtons[i];
            button.setBackground(selected ? MusicPlayerChrome.transportSelection() : null);
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
        artworkImage.setImageDrawable(null);
    }
}
