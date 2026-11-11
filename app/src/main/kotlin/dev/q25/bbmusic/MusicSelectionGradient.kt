package dev.q25.bbmusic

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable

/**
 * Kotlin compatibility adapter for the remaining native player chrome.
 * The canonical Compose media-list selection gradient remains in shared
 * Bbos6MediaListRow; this adapter retains exact legacy Android Drawable
 * stop colors for the native MusicPlayerChrome consumer until retired.
 */
internal object MusicSelectionGradient {
    private val lightSelection = intArrayOf(
        0xff088eef.toInt(), 0xff088aef.toInt(), 0xff0886ef.toInt(),
        0xff0882ef.toInt(), 0xff087de7.toInt(), 0xff0879e7.toInt(),
        0xff1075e7.toInt(), 0xff1071e7.toInt(), 0xff106dde.toInt(),
        0xff1069de.toInt(), 0xff1065de.toInt()
    )
    private val darkSelection = intArrayOf(
        0xff459fd9.toInt(), 0xff3285c5.toInt(), 0xff2368aa.toInt()
    )

    @JvmStatic
    fun create(context: Context): GradientDrawable {
        val dark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (dark) darkSelection else lightSelection
        ).apply { cornerRadius = 0f }
    }

    /** Strong canonical light-blue face for selected metadata labels. */
    @JvmStatic
    fun createVivid(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, lightSelection
        ).apply { cornerRadius = 0f }
}
