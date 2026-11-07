package dev.q25.bbmusic;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.TextView;

/**
 * Shared Settings section header. It is a non-selectable ListView row,
 * not a navigation item, so later settings groups use the same renderer.
 */
final class MusicSettingsHeaderView extends TextView {
    private static final int HEIGHT_DP = 36;

    MusicSettingsHeaderView(Context context) {
        super(context);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(16), 0, dp(16), 0);
        setTextSize(14);
        setTextColor(0xffe8ebef);
        setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setFocusable(false);
        setClickable(false);
        setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(HEIGHT_DP)));

        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff4b4e53, 0xff292c31});
        background.setStroke(dp(1), 0xff5a5d63);
        setBackground(background);
    }

    void bind(String title) {
        setText(title);
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
