package dev.q25.bbos6.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class Bbos6PageListNavigationTest {
    @Test fun boundedMovement() {
        assertEquals(0, Bbos6PageListNavigation.move(0, -1, 3))
        assertEquals(1, Bbos6PageListNavigation.move(0, 1, 3))
        assertEquals(2, Bbos6PageListNavigation.move(2, 1, 3))
        assertEquals(1, Bbos6PageListNavigation.move(2, -1, 3))
    }

    @Test fun handlesMissingAndStaleSelection() {
        assertEquals(-1, Bbos6PageListNavigation.move(0, 1, 0))
        assertEquals(1, Bbos6PageListNavigation.move(99, 1, 2))
        assertEquals(0, Bbos6PageListNavigation.move(-8, -1, 2))
    }

    @Test fun hardwareLongPressOnEmptyListOpensDirectoryMenu() {
        var rowInvocations = 0
        var menuInvocations = 0
        var receivedId: String? = "unexpected"
        val handled = Bbos6PageListNavigation.dispatchLongPress(
            selectedId = null,
            onLongPress = { rowInvocations++ },
            onMenu = { id ->
                menuInvocations++
                receivedId = id
            }
        )
        assertEquals(true, handled)
        assertEquals(0, rowInvocations)
        assertEquals(1, menuInvocations)
        assertEquals(null, receivedId)
    }

    @Test fun hardwareLongPressOnRowPrefersRowHandler() {
        var selectedRow: String? = null
        var menuCalls = 0
        val handled = Bbos6PageListNavigation.dispatchLongPress(
            selectedId = "file-a",
            onLongPress = { selectedRow = it },
            onMenu = { menuCalls++ }
        )
        assertEquals(true, handled)
        assertEquals("file-a", selectedRow)
        assertEquals(0, menuCalls)
    }

    @Test fun hardwareLongPressFallsBackToMenuWhenOnlyMenuHandlerExists() {
        var received: String? = null
        val handled = Bbos6PageListNavigation.dispatchLongPress(
            selectedId = "file-b",
            onLongPress = null,
            onMenu = { received = it }
        )
        assertEquals(true, handled)
        assertEquals("file-b", received)
    }

    @Test fun hardwareLongPressWithoutHandlerDoesNotClaimEvent() {
        assertEquals(
            false,
            Bbos6PageListNavigation.dispatchLongPress(
                selectedId = null, onLongPress = { }, onMenu = null
            )
        )
        assertEquals(
            false,
            Bbos6PageListNavigation.dispatchLongPress(
                selectedId = null, onLongPress = null, onMenu = null
            )
        )
    }
}
