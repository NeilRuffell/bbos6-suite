package dev.q25.bbos6.ui

import android.os.Build
import android.view.ViewGroup

/**
 * Only suppress Android's duplicate focus highlight on BBOS6-managed
 * ViewGroup containers. Native editable leaf controls are not modified.
 */
object Bbos6FocusPolicy {
    @JvmStatic
    fun forManagedContainers(root: ViewGroup) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        applyTo(root)
    }

    private fun applyTo(container: ViewGroup) {
        container.defaultFocusHighlightEnabled = false
        for (i in 0 until container.childCount) {
            (container.getChildAt(i) as? ViewGroup)?.let(::applyTo)
        }
    }
}
