package dev.q25.bbmusic;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;

/**
 * Shared, borderless BBOS6 Music selection gradient for list rows and controls.
 * The selected colors match the pre-existing MusicListView light/dark theme
 * palettes; a fresh Drawable is returned for each independent view background.
 */
final class MusicSelectionGradient {
    private static final int[] LIGHT_SELECTION = {
            0xff088eef, 0xff088aef, 0xff0886ef, 0xff0882ef, 0xff087de7,
            0xff0879e7, 0xff1075e7, 0xff1071e7, 0xff106dde, 0xff1069de,
            0xff1065de
    };
    private static final int[] DARK_SELECTION = {
            0xff459fd9, 0xff3285c5, 0xff2368aa
    };

    private MusicSelectionGradient() {}

    static GradientDrawable create(Context context) {
        boolean dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                dark ? DARK_SELECTION : LIGHT_SELECTION);
        gradient.setCornerRadius(0);
        return gradient;
    }

    /**
     * Strong BBOS6 blue for selected metadata labels regardless of Android's
     * night-mode palette. Other list and control selections stay untouched.
     */
    static GradientDrawable createVivid() {
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, LIGHT_SELECTION);
        gradient.setCornerRadius(0);
        return gradient;
    }
}
