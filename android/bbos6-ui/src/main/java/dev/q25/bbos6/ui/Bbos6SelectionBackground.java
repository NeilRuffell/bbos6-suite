package dev.q25.bbos6.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/**
 * Shared BBOS6 menu-selection face. Retains the existing context-menu
 * colors, border and curvature; callers own their hit targets and shadows.
 */
final class Bbos6SelectionBackground {
    private Bbos6SelectionBackground() {}

    static GradientDrawable blue(Context context) {
        GradientDrawable face = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff138fe4, 0xff0974cb, 0xff0750a8});
        face.setCornerRadius(dp(context, 7));
        face.setStroke(Math.max(1, dp(context, 1)), 0xff2368a0);
        return face;
    }

    private static int dp(Context context, float size) {
        return Math.round(size * context.getResources().getDisplayMetrics().density);
    }
}
