package dev.q25.bbmusic

/** Original hardware key long-press state machine, now written in Kotlin. */
internal class TrackpadPressState {
    private var heldKey = -1
    private var longFired = false

    fun press(keyCode: Int): Boolean {
        if (heldKey != -1) return false
        heldKey = keyCode
        longFired = false
        return true
    }

    fun fireLongPress(): Boolean {
        if (heldKey == -1 || longFired) return false
        longFired = true
        return true
    }

    fun fireLongPress(enabled: Boolean): Boolean = enabled && fireLongPress()

    fun release(keyCode: Int): Boolean {
        val clicked = heldKey == keyCode && !longFired
        reset()
        return clicked
    }

    fun isHeld(): Boolean = heldKey != -1

    fun reset() {
        heldKey = -1
        longFired = false
    }
}
