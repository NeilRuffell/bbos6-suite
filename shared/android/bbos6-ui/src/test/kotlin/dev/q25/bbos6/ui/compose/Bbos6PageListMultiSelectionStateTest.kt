package dev.q25.bbos6.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Bbos6PageListMultiSelectionStateTest {
    @Test fun optInEntryAndToggle() {
        val state = Bbos6PageListMultiSelectionState()
        assertFalse(state.isActive)
        state.toggle("a")
        assertTrue(state.selectedIds.isEmpty())
        state.enter("a")
        assertEquals(setOf("a"), state.selectedIds)
        state.toggle("b")
        assertEquals(setOf("a", "b"), state.selectedIds)
        state.toggle("a")
        assertEquals(setOf("b"), state.selectedIds)
    }

    @Test fun selectClearAndExit() {
        val state = Bbos6PageListMultiSelectionState()
        state.enter()
        state.selectAll(listOf("a", "b", "a"))
        assertEquals(2, state.selectedCount)
        state.deselectAll()
        assertTrue(state.isActive)
        assertEquals(0, state.selectedCount)
        state.selectAll(listOf("a"))
        state.exit()
        assertFalse(state.isActive)
        assertTrue(state.selectedIds.isEmpty())
    }

    @Test fun retentionDropsStaleIds() {
        val state = Bbos6PageListMultiSelectionState()
        state.enter()
        state.selectAll(listOf("a", "b", "c"))
        state.retainAvailable(listOf("b", "c", "d"))
        assertEquals(setOf("b", "c"), state.selectedIds)
        state.exit()
        state.selectAll(listOf("x"))
        assertTrue(state.selectedIds.isEmpty())
    }
}
