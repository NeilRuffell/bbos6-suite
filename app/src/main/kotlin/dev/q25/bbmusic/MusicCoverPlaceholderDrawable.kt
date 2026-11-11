package dev.q25.bbmusic

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Original generic no-cover drawing, now Kotlin, with identical geometry. */
class MusicCoverPlaceholderDrawable : Drawable() {
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xff263443.toInt()
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xff667b90.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xffa8bcd0.toInt()
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val x = b.left.toFloat()
        val y = b.top.toFloat()
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        canvas.drawRect(b, background)
        canvas.drawRect(x + 1f, y + 1f, x + w - 1f, y + h - 1f, edge)
        if (w <= 0f || h <= 0f) return
        val stemX = x + w * 0.61f
        val stemTop = y + h * 0.19f
        val stemBottom = y + h * 0.69f
        val stemWidth = maxOf(2f, w * 0.075f)
        canvas.drawRect(stemX, stemTop, stemX + stemWidth, stemBottom, note)
        canvas.drawOval(
            x + w * 0.30f, y + h * 0.60f,
            x + w * 0.64f, y + h * 0.80f, note
        )
        canvas.drawRect(
            stemX, stemTop, x + w * 0.79f,
            stemTop + maxOf(3f, h * 0.09f), note
        )
    }

    override fun setAlpha(alpha: Int) {
        note.alpha = alpha
        edge.alpha = alpha
        background.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        note.colorFilter = colorFilter
        edge.colorFilter = colorFilter
        background.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Android Drawable opacity is required for Java callers")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
