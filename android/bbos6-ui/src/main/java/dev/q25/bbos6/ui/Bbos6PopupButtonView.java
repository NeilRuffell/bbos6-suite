package dev.q25.bbos6.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Canonical BBOS6 popup action-button face and intrinsic sizing.
 *
 * The parent owns its placement, current selection, and click/trackpad
 * handling. The button owns typography, padding, minimum content width,
 * and its native silver/blue faces, without layered shadow workarounds.
 */
public final class Bbos6PopupButtonView extends TextView {
    public static final int HEIGHT_DP = 30;
    private static final int CORNER_RADIUS_DP = 4;
    private static final int FIXED_WIDTH_PADDING_DP = 6;
    private static final int CONTENT_WIDTH_PADDING_DP = 14;
    private static final int MIN_CONTENT_WIDTH_DP = 48;

    public Bbos6PopupButtonView(Context context) {
        super(context);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setIncludeFontPadding(false);
        // The popup's panel is the keyboard/trackpad focus owner.
        setFocusable(false);
        setContentSized(false);
        setBbos6Highlighted(false);
    }

    /**
     * Content-sized buttons measure their own text plus horizontal padding.
     * The minimum width keeps short labels operable by touch. A parent may
     * still use a fixed width for vertically stacked buttons.
     */
    public void setContentSized(boolean contentSized) {
        int padding = contentSized
                ? CONTENT_WIDTH_PADDING_DP : FIXED_WIDTH_PADDING_DP;
        setPadding(dp(padding), 0, dp(padding), 0);
        setMinWidth(contentSized ? dp(MIN_CONTENT_WIDTH_DP) : 0);
    }

    /**
     * Caller supplies whether this action actually owns active selection.
     * Remembered selection must not be painted while another control
     * (such as an EditText) owns focus.
     */
    public void setBbos6Highlighted(boolean highlighted) {
        if (highlighted) {
            setBackground(Bbos6SelectionBackground.popupButtonBlue(
                    getContext(), CORNER_RADIUS_DP));
        } else {
            GradientDrawable face = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xffd8d8d9, 0xffcccdcf, 0xffafb1b4,
                            0xffafb1b4, 0xffafb1b4});
            face.setCornerRadius(dp(CORNER_RADIUS_DP));
            face.setStroke(1, 0xff959aa2);
            setBackground(face);
        }
        setTextColor(highlighted ? Color.WHITE : 0xff242629);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
