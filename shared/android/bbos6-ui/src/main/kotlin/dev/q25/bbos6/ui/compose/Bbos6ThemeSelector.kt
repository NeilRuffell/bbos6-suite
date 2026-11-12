package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Reusable, Q25-oriented three-choice appearance settings page.
 *
 * Uses the shared page list for touch, DPAD/trackpad, selection, and scrolling.
 * Selection is saved by the manager for the current app only. This widget
 * neither forces a theme onto unrelated components nor changes other apps.
 */
@Composable
fun Bbos6ThemeSelector(
    modifier: Modifier = Modifier,
    manager: Bbos6ThemeManager? = null,
    showHeader: Boolean = true
) {
    val themeManager = manager ?: rememberBbos6ThemeManager()
    val selected = themeManager.mode
    Column(modifier) {
        if (showHeader) Bbos6SectionHeader(title = "Appearance")
        Bbos6PageList(
            items = Bbos6ThemeMode.entries.map { mode ->
                Bbos6ListSelectionItem(
                    id = mode.name,
                    label = mode.label() + if (mode == selected) " (selected)" else ""
                )
            },
            onActivate = { id ->
                Bbos6ThemeMode.entries.firstOrNull { it.name == id }?.let {
                    themeManager.setMode(it)
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

private fun Bbos6ThemeMode.label(): String = when (this) {
    Bbos6ThemeMode.DARK -> "Dark"
    Bbos6ThemeMode.LIGHT -> "Light"
    Bbos6ThemeMode.SYSTEM -> "System"
}
