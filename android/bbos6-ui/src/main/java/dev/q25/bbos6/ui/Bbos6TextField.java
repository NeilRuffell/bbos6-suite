package dev.q25.bbos6.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.widget.EditText;

/**
 * Canonical BBOS6 text-entry face extracted from Music's search field.
 *
 * Only the native EditText's appearance is customized. Android continues
 * to own cursor/selection, text editing, keyboard/IME and focus semantics.
 * Search bars, dialogs and other consumers provide their own surrounding
 * panel (if any) and input behavior.
 */
public final class Bbos6TextField extends EditText {
    private static final int HEIGHT_DP = 43;
    private static final int CORNER_DP = 5;

    public Bbos6TextField(Context context) {
        super(context);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        applyAppearance();
    }

    private void applyAppearance() {
        GradientDrawable rim = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xff4e555b, 0xff6b7074});
        rim.setCornerRadius(dp(CORNER_DP));

        GradientDrawable inset = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        0xffd6d7d6, 0xffe7e7e7, 0xffefefef,
                        0xfff7f7f7, Color.WHITE, Color.WHITE
                });
        inset.setColors(new int[] {
                0xffd6d7d6, 0xffe7e7e7, 0xffefefef,
                0xfff7f7f7, Color.WHITE, Color.WHITE
        }, new float[] {0f, 0.04f, 0.08f, 0.12f, 0.18f, 1f});
        inset.setCornerRadius(dp(CORNER_DP - 1));

        LayerDrawable face = new LayerDrawable(new Drawable[] {rim, inset});
        face.setLayerInset(1, dp(1), dp(1), dp(1), dp(1));
        setBackground(face);
        setTextColor(0xff24272b);
        setHintTextColor(0xffb6b7bb);
        setPadding(dp(12), 0, dp(12), 0);
        setMinHeight(dp(HEIGHT_DP));
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
