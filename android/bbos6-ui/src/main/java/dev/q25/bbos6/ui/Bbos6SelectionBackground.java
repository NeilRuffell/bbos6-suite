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
        return blue(context, 7);
    }

    /** Same selection face, with the corner radius owned by each component. */
    static GradientDrawable blue(Context context, int cornerRadiusDp) {
        GradientDrawable face = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff138fe4, 0xff0974cb, 0xff0750a8});
        face.setCornerRadius(dp(context, cornerRadiusDp));
        face.setStroke(Math.max(1, dp(context, 1)), 0xff2368a0);
        return face;
    }

    /**
     * Separate button face measured from the supplied BB9900 simulator image.
     * The original context-menu blue stays unchanged: its gradient is
     * intentionally darker and belongs to a different control.
     *
     * Five evenly spaced stops reach the solid blue lower face after the
     * upper half, instead of continuously darkening to the bottom.
     */
    static GradientDrawable popupButtonBlue(Context context, int cornerRadiusDp) {
        GradientDrawable face = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff0a8fef, 0xff1080ea, 0xff1766db,
                        0xff1766db, 0xff1766db});
        face.setCornerRadius(dp(context, cornerRadiusDp));
        face.setStroke(1, 0xff3980c4);
        return face;
    }

    private static int dp(Context context, float size) {
        return Math.round(size * context.getResources().getDisplayMetrics().density);
    }
}
