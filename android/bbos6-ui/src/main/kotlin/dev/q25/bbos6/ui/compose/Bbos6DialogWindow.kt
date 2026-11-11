package dev.q25.bbos6.ui.compose

import android.view.View
import android.view.Window
import androidx.compose.ui.window.DialogWindowProvider

/** Locate a Compose Dialog's real window without assuming the immediate parent. */
internal fun bbos6DialogWindow(view: View): Window? {
    var node: View? = view
    while (node != null) {
        if (node is DialogWindowProvider) return node.window
        node = node.parent as? View
    }
    return null
}
