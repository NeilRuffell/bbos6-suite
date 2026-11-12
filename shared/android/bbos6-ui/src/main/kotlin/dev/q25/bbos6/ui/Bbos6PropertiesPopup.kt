package dev.q25.bbos6.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.Outline
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import dev.q25.bbos6.ui.compose.Bbos6PropertiesComposeBridge
import java.util.Collections

/**
 * App-neutral read-only Properties window. Kotlin owns both the shared
 * Compose bridge and the unchanged native dialog fallback for plain Activity.
 */
object Bbos6PropertiesPopup {
    const val SURFACE_COLOR: Int = -14605271 // #FF212429
    const val TEXT_COLOR: Int = Color.WHITE

    private const val MAX_VIEWPORT_FRACTION = 0.90f
    private const val CORNER_RADIUS_DP = 10
    private const val HORIZONTAL_PADDING_DP = 12
    private const val VERTICAL_PADDING_DP = 10
    private const val MIN_ROW_HEIGHT_DP = 18
    private const val FONT_SIZE_SP = 15f

    @JvmStatic fun builder(): Builder = Builder()

    class Builder {
        private val source = ArrayList<Bbos6PropertiesItem>()

        fun add(item: Bbos6PropertiesItem): Builder = apply { source.add(item) }
        fun addText(text: String): Builder = add(Bbos6PropertiesItem.text(text))
        fun addInline(label: String, value: String): Builder =
            add(Bbos6PropertiesItem.`inline`(label, value))
        fun addTrailing(label: String, value: String): Builder =
            add(Bbos6PropertiesItem.trailing(label, value))
        fun addSpacer(dp: Int): Builder = add(Bbos6PropertiesItem.spacer(dp))

        fun items(): List<Bbos6PropertiesItem> =
            Collections.unmodifiableList(ArrayList(source))

        fun show(activity: Activity): Dialog = Bbos6PropertiesPopup.show(activity, items())
    }

    /**
     * Returns an actual Android Dialog, with no invented metadata, no dim
     * scrim, Back dismissal and outside-touch dismissal disabled.
     */
    @JvmStatic
    fun show(activity: Activity, items: List<Bbos6PropertiesItem>): Dialog {
        check(!activity.isFinishing && !activity.isDestroyed) {
            "Cannot show a popup on a closed Activity"
        }
        val snapshot = ArrayList(items)
        if (activity is ComponentActivity) {
            return Bbos6PropertiesComposeBridge.show(activity, snapshot)
        }

        val viewport = availableViewport(activity)
        val popupWidth = maxOf(1, Math.round(viewport.first * MAX_VIEWPORT_FRACTION))
        val popupMaxHeight = maxOf(1, Math.round(viewport.second * MAX_VIEWPORT_FRACTION))
        val panel = FrameLayout(activity).apply {
            background = panelBackground(activity)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        0, 0, view.width, view.height, dp(activity, CORNER_RADIUS_DP).toFloat()
                    )
                }
            }
            clipToOutline = true
            elevation = dp(activity, 3).toFloat()
        }
        val scroll = BoundedScrollView(activity, popupMaxHeight).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(activity, HORIZONTAL_PADDING_DP), dp(activity, VERTICAL_PADDING_DP),
                dp(activity, HORIZONTAL_PADDING_DP), dp(activity, VERTICAL_PADDING_DP)
            )
        }
        for (item in snapshot) addItem(activity, content, item)
        scroll.addView(
            content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        panel.addView(
            scroll, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        Bbos6FocusPolicy.forManagedContainers(panel)
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(panel)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)
        val window = dialog.window
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.CENTER)
        }
        dialog.show()
        window?.setLayout(popupWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    private fun addItem(activity: Activity, parent: LinearLayout, item: Bbos6PropertiesItem) {
        when (item.kind()) {
            Bbos6PropertiesItem.Kind.SPACER -> {
                if (item.spaceDp() > 0) {
                    parent.addView(
                        View(activity), LinearLayout.LayoutParams(1, dp(activity, item.spaceDp()))
                    )
                }
            }
            Bbos6PropertiesItem.Kind.TRAILING_PROPERTY -> {
                val pair = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(activity, MIN_ROW_HEIGHT_DP)
                }
                val label = textView(activity, item.label())
                pair.addView(label, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ))
                val value = textView(activity, item.value()).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                }
                pair.addView(value, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply { leftMargin = dp(activity, 6) })
                parent.addView(pair, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }
            Bbos6PropertiesItem.Kind.TEXT, Bbos6PropertiesItem.Kind.INLINE_PROPERTY -> {
                val value = if (item.kind() == Bbos6PropertiesItem.Kind.INLINE_PROPERTY) {
                    item.label() + (if (item.value().isEmpty()) "" else " " + item.value())
                } else item.label()
                parent.addView(textView(activity, value), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }
        }
    }

    private fun textView(activity: Activity, text: String) = TextView(activity).apply {
        this.text = text
        setTextColor(TEXT_COLOR)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_SP)
        includeFontPadding = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        minHeight = dp(activity, MIN_ROW_HEIGHT_DP)
    }

    private fun panelBackground(activity: Activity): LayerDrawable {
        val outerRadius = dp(activity, CORNER_RADIUS_DP)
        val border = maxOf(1, dp(activity, 1))
        val rim = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xff73797d.toInt(), 0xff4b5055.toInt())
        ).apply { cornerRadius = outerRadius.toFloat() }
        val face = GradientDrawable().apply {
            setColor(SURFACE_COLOR)
            cornerRadius = maxOf(0, outerRadius - border).toFloat()
        }
        return LayerDrawable(arrayOf(rim, face)).apply {
            setLayerInset(1, border, border, border, border)
        }
    }

    private fun dp(activity: Activity, value: Int): Int =
        Math.round(value * activity.resources.displayMetrics.density)

    private fun availableViewport(activity: Activity): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = activity.windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return maxOf(1, bounds.width() - bars.left - bars.right) to
                maxOf(1, bounds.height() - bars.top - bars.bottom)
        }
        val dm = activity.resources.displayMetrics
        return maxOf(1, dm.widthPixels) to maxOf(1, dm.heightPixels)
    }

    private class BoundedScrollView(
        activity: Activity, private val maximumHeightPx: Int
    ) : ScrollView(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val mode = View.MeasureSpec.getMode(heightMeasureSpec)
            val allowed = if (mode == View.MeasureSpec.UNSPECIFIED) maximumHeightPx
            else minOf(maximumHeightPx, View.MeasureSpec.getSize(heightMeasureSpec))
            super.onMeasure(
                widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(allowed, View.MeasureSpec.AT_MOST)
            )
        }
    }
}
