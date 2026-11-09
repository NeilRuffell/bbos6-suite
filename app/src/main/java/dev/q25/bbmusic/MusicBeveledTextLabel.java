package dev.q25.bbmusic;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Small, content-width BBOS6 metadata plate used for artist and album labels.
 *
 * The reference has a low-contrast charcoal face, a brighter upper rim,
 * darker lower edge and a soft exterior shadow. Render all three face layers
 * with native drawables instead of a conspicuous uniform button stroke.
 * No bitmap assets, custom Canvas painting or additional dependencies.
 */
final class MusicBeveledTextLabel extends TextView {
    private final Drawable idleBackground;
    private final GradientDrawable focusedBackground;
    private boolean metadataFocused;

    MusicBeveledTextLabel(Context context, float textSizeSp) {
        super(context);
        setTextSize(textSizeSp);
        setTextColor(Color.WHITE);
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        // The reference has a faint dark halo beneath its white lettering.
        setShadowLayer(dp(0.8f), 0f, dp(0.5f), 0x99000000);

        idleBackground = beveledBackground();
        focusedBackground = MusicSelectionGradient.create(context);
        focusedBackground.setCornerRadius(dp(5));
        focusedBackground.setStroke(dp(1), MusicPlayerChrome.BLUE_BORDER);
        setBackground(idleBackground);
        // Apply padding after the layered background to avoid inset-derived
        // drawable padding increasing the label's measured text spacing.
        setPadding(dp(5), dp(2), dp(5), dp(2));

        // Small, rounded platform shadow; do not blur a bitmap every frame.
        setElevation(dp(2));
        setOutlineAmbientShadowColor(0x99000000);
        setOutlineSpotShadowColor(0xbb000000);
    }

    private Drawable beveledBackground() {
        int edge = dp(1);
        int corner = dp(5);

        // Outer rim changes with height: a dim top reflection, quiet sides,
        // and a nearly black lower edge. No high-contrast uniform border.
        GradientDrawable rim = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xff50545b, 0xff353942, 0xff191c21});
        rim.setCornerRadius(corner);
        rim.setDither(true);

        // Inset face keeps the border narrow while producing the understated,
        // cool-charcoal gradient seen inside the original label.
        GradientDrawable face = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xff30343a, 0xff2c3036, 0xff272b30, 0xff24272b});
        face.setCornerRadius(Math.max(0, corner - edge));
        face.setDither(true);

        // A faint horizontal reflection just below the upper outline. Inset
        // its ends so it does not draw over the rounded outer corners.
        GradientDrawable upperSheen = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0x00565e66, 0x34565e66, 0x28565e66, 0x00565e66});
        upperSheen.setDither(true);

        LayerDrawable layers = new LayerDrawable(new Drawable[] {
                rim, face, upperSheen
        });
        layers.setLayerInset(1, edge, edge, edge, edge);
        layers.setLayerInset(2, dp(3), edge, dp(3), 0);
        layers.setLayerHeight(2, edge);
        layers.setLayerGravity(2, Gravity.TOP | Gravity.FILL_HORIZONTAL);
        return layers;
    }

    /** Trackpad focus is managed by Now Playing, not Android TextView focus. */
    void setMetadataFocused(boolean focused) {
        if (metadataFocused == focused) return;
        metadataFocused = focused;
        setBackground(focused ? focusedBackground : idleBackground);
        // Focus swaps drawables but must never change the label's dimensions.
        setPadding(dp(5), dp(2), dp(5), dp(2));
    }

    private int dp(float value) {
        return MusicPlayerChrome.dp(getContext(), value);
    }
}
