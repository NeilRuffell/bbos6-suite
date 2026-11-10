package dev.q25.bbmusic;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;

import dev.q25.bbos6.ui.Bbos6TextField;

/**
 * BBOS6 Music search chrome: only the charcoal panel and its top rule.
 *
 * The input face is owned exclusively by the reusable Bbos6TextField in
 * bbos6-shared; this panel does not style or manage text editing.
 */
final class MusicSearchBarView extends FrameLayout {
    private static final int HEIGHT_DP = 54;
    private static final int TOP_BORDER = 0xff5a5d63;
    private static final int TOP_GRADIENT = 0xff4b4e53;
    private static final int BOTTOM_GRADIENT = 0xff292c31;

    MusicSearchBarView(Context context, Bbos6TextField input) {
        super(context);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClipChildren(false);

        GradientDrawable panel = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {TOP_GRADIENT, BOTTOM_GRADIENT});
        panel.setDither(true);
        LayerDrawable chrome = new LayerDrawable(new Drawable[] {
                panel, new ColorDrawable(TOP_BORDER)
        });
        chrome.setLayerHeight(1, dp(1));
        chrome.setLayerGravity(1, Gravity.TOP | Gravity.FILL_HORIZONTAL);
        setBackground(chrome);
        setPadding(dp(6), dp(6), dp(6), dp(5));

        addView(input, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
    }

    static int heightDp() {
        return HEIGHT_DP;
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
