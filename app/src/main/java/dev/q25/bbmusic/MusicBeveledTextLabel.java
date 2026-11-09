package dev.q25.bbmusic;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Small, content-width BBOS6 metadata plate used for artist and album labels.
 *
 * Uses the platform's gradient, outline and elevation rendering: no bitmaps,
 * custom canvas drawing, or extra dependencies. The parent layout must allow
 * its children to draw outside their own bounds for the drop shadow.
 */
final class MusicBeveledTextLabel extends TextView {
    private final GradientDrawable idleBackground;
    private final GradientDrawable focusedBackground;
    private boolean metadataFocused;

    MusicBeveledTextLabel(Context context, float textSizeSp) {
        super(context);
        setTextSize(textSizeSp);
        setTextColor(Color.WHITE);
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setPadding(dp(5), dp(2), dp(5), dp(2));

        GradientDrawable bevel = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xff474d57, 0xff343a44, 0xff242a33});
        bevel.setShape(GradientDrawable.RECTANGLE);
        bevel.setCornerRadius(dp(4));
        bevel.setStroke(dp(1), 0xff626a76);
        bevel.setDither(true);
        idleBackground = bevel;
        focusedBackground = MusicSelectionGradient.create(context);
        focusedBackground.setCornerRadius(dp(4));
        focusedBackground.setStroke(dp(1), MusicPlayerChrome.BLUE_BORDER);
        setBackground(idleBackground);
        setElevation(dp(2));
    }

    /** Trackpad focus is managed by Now Playing, not Android TextView focus. */
    void setMetadataFocused(boolean focused) {
        if (metadataFocused == focused) return;
        metadataFocused = focused;
        setBackground(focused ? focusedBackground : idleBackground);
    }

    private int dp(float value) {
        return MusicPlayerChrome.dp(getContext(), value);
    }
}
