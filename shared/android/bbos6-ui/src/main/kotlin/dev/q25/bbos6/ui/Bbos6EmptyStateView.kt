package dev.q25.bbos6.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Kotlin native-View compatibility for existing empty-state layouts.
 * New Compose screens should directly use Bbos6EmptyState.
 */
class Bbos6EmptyStateView(context: Context) : LinearLayout(context) {
    private companion object {
        const val ARTWORK_DP = 144
        const val ARTWORK_TO_TITLE_DP = 12
        const val TITLE_TO_MESSAGE_DP = 16
        const val SIDE_PADDING_DP = 22
    }

    private val artworkView = ImageView(context)
    private val titleView = TextView(context)
    private val messageView = TextView(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(SIDE_PADDING_DP), dp(12), dp(SIDE_PADDING_DP), dp(12))
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false

        artworkView.apply {
            isFocusable = false
            isClickable = false
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        addView(artworkView, LayoutParams(dp(ARTWORK_DP), dp(ARTWORK_DP)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(ARTWORK_TO_TITLE_DP)
        })

        titleView.apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            isFocusable = false
            isClickable = false
            visibility = View.GONE
        }
        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(TITLE_TO_MESSAGE_DP)
        })

        messageView.apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            isFocusable = false
            isClickable = false
            visibility = View.GONE
        }
        addView(messageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setContent(heading: CharSequence?, description: CharSequence?) {
        titleView.text = heading
        titleView.visibility = if (heading.isNullOrBlank()) View.GONE else View.VISIBLE
        messageView.text = description
        messageView.visibility = if (description.isNullOrBlank()) View.GONE else View.VISIBLE
        updateSpacing()
    }

    fun setArtwork(drawable: Drawable?) {
        artworkView.setImageDrawable(drawable)
        artworkView.visibility = if (drawable == null) View.GONE else View.VISIBLE
        updateSpacing()
    }

    fun setArtworkResource(drawableRes: Int) {
        if (drawableRes == 0) setArtwork(null)
        else setArtwork(context.getDrawable(drawableRes))
    }

    private fun updateSpacing() {
        val art = artworkView.layoutParams as LayoutParams
        art.bottomMargin = if (
            titleView.visibility == View.VISIBLE || messageView.visibility == View.VISIBLE
        ) dp(ARTWORK_TO_TITLE_DP) else 0
        artworkView.layoutParams = art

        val title = titleView.layoutParams as LayoutParams
        title.bottomMargin = if (messageView.visibility == View.VISIBLE)
            dp(TITLE_TO_MESSAGE_DP) else 0
        titleView.layoutParams = title
    }

    private fun dp(value: Int): Int = Math.round(resources.displayMetrics.density * value)
}
