package dev.q25.bbfiles

import dev.q25.bbos6.ui.compose.Bbos6ContextAction

/**
 * Files-owned pending clipboard command set, shared by item and empty-folder
 * context menus. Paste and New Folder always target the currently open folder.
 * The menu is intentionally independent of the selected row.
 */
internal object FilesClipboardMenu {
    fun actions(cut: Boolean): List<Bbos6ContextAction> = listOf(
        Bbos6ContextAction.text("paste", "Paste"),
        Bbos6ContextAction.text("new", "New Folder"),
        Bbos6ContextAction.text(
            "cancel_clipboard",
            if (cut) "Cancel Cut" else "Cancel Copy"
        )
    )
}
