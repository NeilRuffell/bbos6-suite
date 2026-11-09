package dev.q25.bbmusic;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;

/**
 * Legacy BBOS6 media-chrome primitives retained for source compatibility.
 * The current Now Playing header and seek panel each render their own
 * reference-matched borders via the shared MusicPlayerChrome helper.
 */
final class MediaChromeDivider {
    // Older divider dimensions. The 2513.png header and 2486.png seek panel
    // now own their border dimensions directly in MusicPlayerChrome.
    static final int HEIGHT_DP = 1;
    static final int DARK_RULE_HEIGHT_DP = 2;

    private MediaChromeDivider() { }

    /** Earlier generic vertical media background (not used for the 2486/2513 panels). */
    static GradientDrawable darkMediaBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xff222422, 0xff181818});
    }

    /** Matches the 2dp dark top rule of the seekbar area. */
    static ColorDrawable darkRule() {
        return new ColorDrawable(0xff393c3c);
    }

    static GradientDrawable silverLine() {
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xffb5b6b5, 0xffffffff, 0xffb5b6b5});
    }
}
