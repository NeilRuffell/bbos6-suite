package dev.q25.bbmusic

import java.util.Locale

/** Unchanged native Media3 seek policy, exposed to existing Java controllers. */
internal object MediaSeekPolicy {
    const val PROGRESS_MAX = 1000
    const val KEY_SEEK_STEP_MS = 5000L

    @JvmStatic fun toggleTrackpadEditing(
        highlighted: Boolean, editing: Boolean, canSeek: Boolean
    ): Boolean = highlighted && canSeek && !editing

    @JvmStatic fun canTrackpadSeek(
        highlighted: Boolean, editing: Boolean, canSeek: Boolean
    ): Boolean = highlighted && editing && canSeek

    @JvmStatic fun clamp(positionMs: Long, durationMs: Long): Long =
        minOf(maxOf(0L, positionMs), maxOf(0L, durationMs))

    @JvmStatic fun positionAt(
        positionMs: Long, durationMs: Long, updatedAtMs: Long,
        speed: Float, advancing: Boolean, nowMs: Long
    ): Long {
        var position = maxOf(0L, positionMs)
        if (advancing && updatedAtMs > 0 && nowMs > updatedAtMs &&
            !speed.isNaN() && !speed.isInfinite()) {
            val calculated = position.toDouble() +
                (nowMs - updatedAtMs) * speed.toDouble()
            position = when {
                calculated >= Long.MAX_VALUE.toDouble() -> Long.MAX_VALUE
                calculated <= 0.0 -> 0L
                else -> calculated.toLong()
            }
        }
        return if (durationMs > 0) clamp(position, durationMs) else position
    }

    @JvmStatic fun toProgress(positionMs: Long, durationMs: Long): Int {
        if (durationMs <= 0) return 0
        return java.lang.Math.round(
            clamp(positionMs, durationMs) * PROGRESS_MAX.toDouble() / durationMs
        ).toInt()
    }

    @JvmStatic fun fromProgress(progress: Int, durationMs: Long): Long {
        if (durationMs <= 0) return 0L
        val bounded = maxOf(0, minOf(PROGRESS_MAX, progress))
        return java.lang.Math.round(durationMs * (bounded.toDouble() / PROGRESS_MAX))
    }

    @JvmStatic fun seekStep(positionMs: Long, durationMs: Long, direction: Int): Long {
        if (durationMs <= 0) return 0L
        val bounded = clamp(positionMs, durationMs)
        return if (direction < 0) maxOf(0L, bounded - KEY_SEEK_STEP_MS)
        else if (bounded >= durationMs - KEY_SEEK_STEP_MS) durationMs
        else bounded + KEY_SEEK_STEP_MS
    }

    @JvmStatic fun formatRemainingTime(durationMs: Long, positionMs: Long): String =
        if (durationMs <= 0L) "--:--"
        else "-" + formatTime(durationMs - clamp(positionMs, durationMs))

    @JvmStatic fun formatTime(millis: Long): String {
        val seconds = maxOf(0L, millis) / 1000
        val minutes = seconds / 60
        return if (minutes >= 60)
            String.format(Locale.US, "%d:%02d:%02d", minutes / 60, minutes % 60, seconds % 60)
        else String.format(Locale.US, "%d:%02d", minutes, seconds % 60)
    }
}
