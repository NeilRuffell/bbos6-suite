package dev.q25.bbfiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesClipboardMenuTest {
    @Test fun pendingCopyShowsOnlyPasteNewFolderCancelCopy() {
        val actions = FilesClipboardMenu.actions(cut = false)
        assertEquals(listOf("paste", "new", "cancel_clipboard"), actions.map { it.id })
        assertEquals(listOf("Paste", "New Folder", "Cancel Copy"), actions.map { it.label })
        assertTrue(actions.all { it.enabled })
    }

    @Test fun pendingCutShowsOnlyPasteNewFolderCancelCut() {
        val actions = FilesClipboardMenu.actions(cut = true)
        assertEquals(listOf("paste", "new", "cancel_clipboard"), actions.map { it.id })
        assertEquals(listOf("Paste", "New Folder", "Cancel Cut"), actions.map { it.label })
        assertTrue(actions.all { it.enabled })
    }
}
