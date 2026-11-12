package dev.q25.bbos6.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class Bbos6PopupNavigationTest {
    private val actions = listOf(
        Bbos6PopupAction("save", "Save"),
        Bbos6PopupAction("skip", "Skip", enabled = false),
        Bbos6PopupAction("cancel", "Cancel")
    )

    @Test fun selectionAndDisabledSkipping() {
        assertEquals(0, Bbos6PopupNavigation.initial(actions, null))
        assertEquals(2, Bbos6PopupNavigation.initial(actions, "cancel"))
        assertEquals(0, Bbos6PopupNavigation.initial(actions, "skip"))
        assertEquals(2, Bbos6PopupNavigation.move(actions, 0, 1))
        assertEquals(0, Bbos6PopupNavigation.move(actions, 2, -1))
        assertEquals(2, Bbos6PopupNavigation.move(actions, 2, 1))
    }

    @Test fun uniqueLabelAccelerators() {
        val shortcuts = Bbos6PopupNavigation.shortcuts(actions)
        assertEquals(0 to 0, shortcuts['S'])
        assertEquals(2 to 0, shortcuts['C'])
        assertEquals(null, shortcuts['K'])
    }
}
