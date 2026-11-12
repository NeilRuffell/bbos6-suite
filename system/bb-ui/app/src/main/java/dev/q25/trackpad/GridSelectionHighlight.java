package dev.q25.trackpad;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

/**
 * Shared BlackBerry OS 6-style selection background for icon grids.
 *
 * This renderer is deliberately independent of Recents, task IDs, view
 * hierarchy and navigation. Any BBUI grid can draw this highlight using its
 * own selection bounds and display density.
 */
public final class GridSelectionHighlight {
    public static final int TOP_COLOR = 0xff088eef;
    public static final int BOTTOM_COLOR = 0xff0851b5;

    // Approximately matches the corners of the supplied BBOS6 icon tile.
    private static final float CORNER_RADIUS_DP = 6f;

    private GridSelectionHighlight() {}

    /** Draw the gradient behind the selected item, in caller-supplied bounds. */
    public static void draw(Canvas canvas, float left, float top,
            float right, float bottom, float density) {
        if (canvas == null || right <= left || bottom <= top || density <= 0f) {
            return;
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0f, top, 0f, bottom,
                TOP_COLOR, BOTTOM_COLOR, Shader.TileMode.CLAMP));
        float radius = Math.min(CORNER_RADIUS_DP * density,
                Math.min(right - left, bottom - top) / 2f);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
    }
}
