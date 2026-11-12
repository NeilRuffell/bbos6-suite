package dev.q25.bbos6.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class Bbos6ListSelectionPopupTest {
    @Test fun navigationStopsAtEdgesAndAdvancesOneRow() {
        assertEquals(0, Bbos6ListSelectionNavigation.move(0, -1, 4))
        assertEquals(1, Bbos6ListSelectionNavigation.move(0, 1, 4))
        assertEquals(2, Bbos6ListSelectionNavigation.move(1, 1, 4))
        assertEquals(3, Bbos6ListSelectionNavigation.move(3, 1, 4))
        assertEquals(2, Bbos6ListSelectionNavigation.move(3, -1, 4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidChoiceCountIsRejected() {
        Bbos6ListSelectionNavigation.move(0, 1, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankStableIdIsRejected() {
        Bbos6ListSelectionItem(" ", "New item")
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankCaptionIsRejected() {
        Bbos6ListSelectionItem("create", " ")
    }
}
