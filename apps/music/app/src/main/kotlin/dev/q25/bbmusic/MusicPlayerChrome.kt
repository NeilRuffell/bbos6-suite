package dev.q25.bbmusic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.SeekBar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Kotlin translation of the existing native player chrome. Retains the
 * Java-callable static API, original colors, layer order and pixel geometry.
 * The canonical reusable Compose panel surfaces remain in bbos6-shared.
 */
object MusicPlayerChrome {
    const val BLUE_BORDER: Int = 0xffb4e2fb.toInt()
    private const val TRACK_TOP: Int = 0xff101010.toInt()
    private const val TRACK_BOTTOM: Int = 0xff393839.toInt()

    // Curve 9300 reference: header 22px, seek 20px, transport 30px.
    const val TRANSPORT_HEIGHT_DP = 48
    const val MODE_HEADER_HEIGHT_DP = 35
    const val SEEK_PANEL_HEIGHT_DP = 32
    const val SEEK_THUMB_WIDTH_DP = 42
    const val SEEK_THUMB_HEIGHT_DP = 13
    const val SEEK_TRACK_HEIGHT_DP = 3
    const val SEEK_END_GUARD_DP = 2

    private fun colors(vararg values: Long): IntArray = values.map(Long::toInt).toIntArray()

    @JvmStatic
    fun dp(context: Context, value: Float): Int =
        (context.resources.displayMetrics.density * value).roundToInt()

    // 2486.png: dark seek panel, 2D lower-centre bevel, distinct silver edge.
    @JvmStatic
    fun seekBackground(context: Context): Drawable {
        val body = GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setColors(
                colors(0xff212421, 0xff212021, 0xff181c18, 0xff181818, 0xff181818),
                floatArrayOf(0f, 0.28f, 0.53f, 0.68f, 1f)
            )
            setDither(true)
        }
        val topRule = GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            setColors(
                colors(0xff393839, 0xff4a494a, 0xff5a5d5a, 0xff5a6163),
                floatArrayOf(0f, 0.23f, 0.51f, 1f)
            )
        }
        val lowerBevel = object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private var drawableAlpha = 255

            override fun onBoundsChange(bounds: Rect) {
                super.onBoundsChange(bounds)
                if (bounds.isEmpty) {
                    paint.shader = null
                    return
                }
                paint.shader = LinearGradient(
                    bounds.left.toFloat(), bounds.top.toFloat(),
                    bounds.right.toFloat(), bounds.top.toFloat(),
                    colors(0x00ffffff, 0x00ffffff, 0xffffffff, 0x00ffffff, 0x00ffffff),
                    floatArrayOf(0f, 0.21f, 0.50f, 0.79f, 1f),
                    Shader.TileMode.CLAMP
                )
            }

            override fun draw(canvas: Canvas) {
                val rect = bounds
                if (rect.isEmpty) return
                val end = rect.bottom - dp(context, 1f)
                val start = max(rect.top + dp(context, 1f), end - dp(context, 5f))
                val height = end - start
                if (height <= 0) return
                for (y in start until end) {
                    val rise = (y - start + 1).toFloat() / height
                    paint.alpha = (52f * rise.toDouble().pow(3.2).toFloat() *
                        drawableAlpha / 255f).roundToInt()
                    canvas.drawRect(
                        rect.left.toFloat(), y.toFloat(),
                        rect.right.toFloat(), (y + 1).toFloat(), paint
                    )
                }
            }

            override fun setAlpha(alpha: Int) {
                drawableAlpha = alpha
                invalidateSelf()
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                paint.colorFilter = colorFilter
                invalidateSelf()
            }

            @Deprecated("Android Drawable API")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
        val silverBottomRule = GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            setColors(
                colors(0xff949294, 0xfff7f7f7, 0xffffffff, 0xfff7f7f7, 0xff9c9a9c),
                floatArrayOf(0f, 0.36f, 0.45f, 0.64f, 1f)
            )
        }
        return LayerDrawable(arrayOf(body, lowerBevel, topRule, silverBottomRule)).apply {
            setLayerHeight(2, dp(context, 1f))
            setLayerGravity(2, Gravity.TOP or Gravity.FILL_HORIZONTAL)
            setLayerHeight(3, dp(context, 1f))
            setLayerGravity(3, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
        }
    }

    // 2513.png: shallow upper highlight and separate subtle bottom bevel.
    @JvmStatic
    fun modeHeaderBackground(context: Context): Drawable {
        val body = GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setColors(
                colors(0xff212021, 0xff212021, 0xff181818, 0xff181818, 0xff101010, 0xff101010),
                floatArrayOf(0f, 0.20f, 0.48f, 0.67f, 0.90f, 1f)
            )
            setDither(true)
        }
        val upperBevel = object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private var drawableAlpha = 255

            override fun onBoundsChange(bounds: Rect) {
                super.onBoundsChange(bounds)
                if (bounds.isEmpty) return
                paint.shader = LinearGradient(
                    bounds.left.toFloat(), bounds.top.toFloat(),
                    bounds.right.toFloat(), bounds.top.toFloat(),
                    colors(0x00ffffff, 0xffffffff, 0xffffffff, 0x00ffffff),
                    floatArrayOf(0f, 0.43f, 0.57f, 1f),
                    Shader.TileMode.CLAMP
                )
            }

            override fun draw(canvas: Canvas) {
                val rect = bounds
                if (rect.isEmpty) return
                val top = rect.top + dp(context, 1f)
                val bottom = min(rect.bottom - dp(context, 1f), top + dp(context, 7f))
                val height = bottom - top
                if (height <= 0) return
                for (y in top until bottom) {
                    val progress = (y - top + 0.5f) / height
                    val strength = (1f - progress).toDouble().pow(1.55).toFloat()
                    paint.alpha = (42f * strength * drawableAlpha / 255f).roundToInt()
                    canvas.drawRect(
                        rect.left.toFloat(), y.toFloat(),
                        rect.right.toFloat(), (y + 1).toFloat(), paint
                    )
                }
            }

            override fun setAlpha(alpha: Int) {
                drawableAlpha = alpha
                invalidateSelf()
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                paint.colorFilter = colorFilter
                invalidateSelf()
            }

            @Deprecated("Android Drawable API")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
        val topHighlight = GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            setColors(
                colors(0xff9c9e9c, 0xffffffff, 0xffffffff, 0xffa5a2a5),
                floatArrayOf(0f, 0.45f, 0.55f, 1f)
            )
        }
        val bottomBevel = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            colors(0xff212021, 0xff212421, 0xff212021)
        )
        return LayerDrawable(arrayOf(body, upperBevel, topHighlight, bottomBevel)).apply {
            setLayerHeight(2, dp(context, 1f))
            setLayerGravity(2, Gravity.TOP or Gravity.FILL_HORIZONTAL)
            setLayerHeight(3, dp(context, 1f))
            setLayerGravity(3, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
        }
    }

    @JvmStatic
    fun modeSelectionBackground(context: Context): Drawable {
        val border = dp(context, 1f)
        return InsetDrawable(MusicSelectionGradient.create(context), 0, border, 0, border)
    }

    // Native thumb and completed portion share guard-relative rail endpoints.
    @JvmStatic
    fun seekRailBackground(context: Context, seekBar: SeekBar): Drawable {
        val railHeight = dp(context, SEEK_TRACK_HEIGHT_DP.toFloat())
        val guard = dp(context, SEEK_END_GUARD_DP.toFloat())
        return object : Drawable() {
            private val top = Paint().apply { color = TRACK_TOP }
            private val bottom = Paint().apply { color = TRACK_BOTTOM }
            private val played = Paint().apply { color = Color.WHITE }

            override fun draw(canvas: Canvas) {
                val rect = bounds
                val left = rect.left + guard.toFloat()
                val right = rect.right - guard.toFloat()
                if (right <= left) return
                val fraction = if (seekBar.max > 0) {
                    max(0f, min(1f, seekBar.progress / seekBar.max.toFloat()))
                } else 0f
                val filledRight = left + (right - left) * fraction
                val y = rect.centerY() - railHeight / 2
                val middle = y + railHeight / 2
                canvas.drawRect(left, y.toFloat(), right, middle.toFloat(), top)
                canvas.drawRect(left, middle.toFloat(), right, (y + railHeight).toFloat(), bottom)
                if (filledRight > left) {
                    canvas.drawRect(left, y.toFloat(), filledRight, (y + railHeight).toFloat(), played)
                }
            }

            override fun getIntrinsicHeight(): Int = railHeight

            override fun setAlpha(alpha: Int) {
                top.alpha = alpha
                bottom.alpha = alpha
                played.alpha = alpha
                invalidateSelf()
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                top.colorFilter = colorFilter
                bottom.colorFilter = colorFilter
                played.colorFilter = colorFilter
                invalidateSelf()
            }

            @Deprecated("Android Drawable API")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }

    @JvmStatic
    fun seekThumb(context: Context, highlighted: Boolean, selected: Boolean): Drawable {
        if (selected) {
            return GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                colors(
                    0xff088eef, 0xff088aef, 0xff0886ef, 0xff0882ef, 0xff087de7,
                    0xff0879e7, 0xff1075e7, 0xff1071e7, 0xff106dde,
                    0xff1069de, 0xff1065de
                )
            ).apply {
                cornerRadius = dp(context, SEEK_THUMB_HEIGHT_DP / 2f).toFloat()
                setSize(dp(context, SEEK_THUMB_WIDTH_DP.toFloat()), dp(context, SEEK_THUMB_HEIGHT_DP.toFloat()))
                setStroke(dp(context, 2f), BLUE_BORDER)
            }
        }
        val shell = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (highlighted) colors(0xff19466e, 0xff477eac, 0xff12365c)
            else colors(0xff636563, 0xff929396, 0xff525253)
        ).apply {
            cornerRadius = dp(context, SEEK_THUMB_HEIGHT_DP / 2f).toFloat()
            setSize(dp(context, SEEK_THUMB_WIDTH_DP.toFloat()), dp(context, SEEK_THUMB_HEIGHT_DP.toFloat()))
        }
        val body = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (highlighted) colors(
                0xffe7f6ff, 0xff4b92d6, 0xff559cdf, 0xff64a7e6, 0xff7cb8ef,
                0xff92c8f6, 0xffbadeff, 0xffe1f1ff, 0xff578fbe
            ) else colors(
                0xfff7f7f7, 0xff8ca6c6, 0xff9caece, 0xffa5bace, 0xffbdcbde,
                0xffced7e7, 0xffdee3ef, 0xffefeff7, 0xffadaaad
            )
        ).apply {
            cornerRadius = dp(context, (SEEK_THUMB_HEIGHT_DP - 2) / 2f).toFloat()
            setSize(dp(context, (SEEK_THUMB_WIDTH_DP - 2).toFloat()), dp(context, (SEEK_THUMB_HEIGHT_DP - 2).toFloat()))
        }
        return LayerDrawable(arrayOf(shell, body)).apply {
            setLayerInset(1, dp(context, 1f), dp(context, 1f), dp(context, 1f), dp(context, 1f))
        }
    }

    private val transportSmoothColors = colors(
        0xff6b6d73, 0xff63656b, 0xff5a5d63, 0xff4a515a, 0xff42454a,
        0xff393c42, 0xff313839, 0xff293031, 0xff212829, 0xff212421
    )
    private val transportSmoothStops = floatArrayOf(
        0f, 0.10f, 0.17f, 0.25f, 0.34f, 0.42f, 0.51f, 0.67f, 0.82f, 1f
    )

    @JvmStatic
    fun transportBackground(): GradientDrawable = GradientDrawable().apply {
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setColors(transportSmoothColors, transportSmoothStops)
        setDither(true)
    }

    @JvmStatic
    fun transportSelection(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        colors(
            0xff0871ce, 0xff0871ce, 0xff086dc6, 0xff086dc6, 0xff0869bd,
            0xff0865bd, 0xff0861b5, 0xff105db5, 0xff1059ad, 0xff1055a5, 0xff1055a5
        )
    )

    @JvmStatic
    fun transportSeparators(context: Context, buttons: Array<View>): Drawable =
        object : Drawable() {
            private val dark = Paint()
            private val light = Paint()

            override fun onBoundsChange(bounds: Rect) {
                super.onBoundsChange(bounds)
                if (bounds.isEmpty) return
                val stops = floatArrayOf(0f, 0.17f, 0.34f, 0.51f, 1f)
                dark.shader = LinearGradient(
                    0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
                    colors(0xff525152, 0xff313839, 0xff212829, 0xff181c18, 0xff080808),
                    stops, Shader.TileMode.CLAMP
                )
                light.shader = LinearGradient(
                    0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
                    colors(0xffb5b2b5, 0xff9c9ea5, 0xff848684, 0xff6b6d73, 0xff313831),
                    stops, Shader.TileMode.CLAMP
                )
            }

            override fun draw(canvas: Canvas) {
                val rect = bounds
                val width = dp(context, 1f)
                for (i in 0 until buttons.size - 1) {
                    val edge = buttons[i].right
                    if (edge <= rect.left || edge >= rect.right) continue
                    canvas.drawRect(
                        (edge - width).toFloat(), rect.top.toFloat(),
                        edge.toFloat(), rect.bottom.toFloat(), dark
                    )
                    canvas.drawRect(
                        edge.toFloat(), rect.top.toFloat(),
                        (edge + width).toFloat(), rect.bottom.toFloat(), light
                    )
                }
            }

            override fun setAlpha(alpha: Int) {
                dark.alpha = alpha
                light.alpha = alpha
                invalidateSelf()
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                dark.colorFilter = colorFilter
                light.colorFilter = colorFilter
                invalidateSelf()
            }

            @Deprecated("Android Drawable API")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }

    @JvmStatic
    fun transportIcon(context: Context, action: Int, pause: Boolean): Drawable {
        val shape = Path().apply {
            when {
                action == 0 -> {
                    addRect(5f, 5f, 7f, 19f, Path.Direction.CW)
                    moveTo(18f, 5f)
                    lineTo(8f, 12f)
                    lineTo(18f, 19f)
                    close()
                }
                action == 3 -> {
                    moveTo(6f, 5f)
                    lineTo(16f, 12f)
                    lineTo(6f, 19f)
                    close()
                    addRect(17f, 5f, 19f, 19f, Path.Direction.CW)
                }
                action == 2 -> addRect(6f, 6f, 18f, 18f, Path.Direction.CW)
                pause -> {
                    addRect(6f, 5f, 10f, 19f, Path.Direction.CW)
                    addRect(14f, 5f, 18f, 19f, Path.Direction.CW)
                }
                else -> {
                    moveTo(7f, 5f)
                    lineTo(19f, 12f)
                    lineTo(7f, 19f)
                    close()
                }
            }
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        return object : Drawable() {
            override fun draw(canvas: Canvas) {
                val rect = bounds
                if (rect.isEmpty) return
                val saved = canvas.save()
                canvas.translate(rect.left.toFloat(), rect.top.toFloat())
                canvas.scale(rect.width() / 24f, rect.height() / 24f)
                canvas.drawPath(shape, fill)
                canvas.restoreToCount(saved)
            }

            override fun getIntrinsicWidth(): Int = dp(context, 36f)
            override fun getIntrinsicHeight(): Int = dp(context, 36f)

            override fun setAlpha(alpha: Int) {
                fill.alpha = alpha
                invalidateSelf()
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                fill.colorFilter = colorFilter
                invalidateSelf()
            }

            @Deprecated("Android Drawable API")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }
}
