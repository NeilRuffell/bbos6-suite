package dev.q25.bbos6.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class Bbos6ContextMenuNavigationTest {
    private val LEFT = Bbos6ContextMenuNavigation.Direction.LEFT
    private val RIGHT = Bbos6ContextMenuNavigation.Direction.RIGHT
    private val UP = Bbos6ContextMenuNavigation.Direction.UP
    private val DOWN = Bbos6ContextMenuNavigation.Direction.DOWN

    private fun grid(): List<Bbos6ContextMenuNavigation.Cell> = buildList {
        for (row in 0..2) for (column in 0..2) {
            add(Bbos6ContextMenuNavigation.Cell(row, column, true))
        }
    }

    @Test fun movesAcrossReferenceThreeByThreeMenu() {
        val cells = grid()
        assertEquals(4, Bbos6ContextMenuNavigation.move(cells, 1, DOWN))
        assertEquals(7, Bbos6ContextMenuNavigation.move(cells, 4, DOWN))
        assertEquals(6, Bbos6ContextMenuNavigation.move(cells, 7, LEFT))
        assertEquals(8, Bbos6ContextMenuNavigation.move(cells, 7, RIGHT))
        assertEquals(5, Bbos6ContextMenuNavigation.move(cells, 8, UP))
        assertEquals(1, Bbos6ContextMenuNavigation.move(cells, 4, UP))
    }

    @Test fun doesNotWrapOffAnEdge() {
        val cells = grid()
        assertEquals(0, Bbos6ContextMenuNavigation.move(cells, 0, LEFT))
        assertEquals(2, Bbos6ContextMenuNavigation.move(cells, 2, RIGHT))
        assertEquals(1, Bbos6ContextMenuNavigation.move(cells, 1, UP))
        assertEquals(7, Bbos6ContextMenuNavigation.move(cells, 7, DOWN))
    }

    @Test fun skipsDisabledHorizontalItems() {
        val cells = listOf(
            Bbos6ContextMenuNavigation.Cell(0, 0, true),
            Bbos6ContextMenuNavigation.Cell(0, 1, false),
            Bbos6ContextMenuNavigation.Cell(0, 2, true)
        )
        assertEquals(2, Bbos6ContextMenuNavigation.move(cells, 0, RIGHT))
        assertEquals(0, Bbos6ContextMenuNavigation.move(cells, 2, LEFT))
    }

    @Test fun incompleteRowsPreferSameColumnThenNearestEnabled() {
        val cells = listOf(
            Bbos6ContextMenuNavigation.Cell(0, 0, true),
            Bbos6ContextMenuNavigation.Cell(0, 1, true),
            Bbos6ContextMenuNavigation.Cell(0, 2, true),
            Bbos6ContextMenuNavigation.Cell(1, 0, true)
        )
        assertEquals(3, Bbos6ContextMenuNavigation.move(cells, 2, DOWN))
        assertEquals(0, Bbos6ContextMenuNavigation.move(cells, 3, UP))
    }

    @Test fun skipsCompletelyDisabledRows() {
        val cells = listOf(
            Bbos6ContextMenuNavigation.Cell(0, 1, true),
            Bbos6ContextMenuNavigation.Cell(1, 1, false),
            Bbos6ContextMenuNavigation.Cell(2, 1, true)
        )
        assertEquals(2, Bbos6ContextMenuNavigation.move(cells, 0, DOWN))
        assertEquals(0, Bbos6ContextMenuNavigation.move(cells, 2, UP))
    }

    @Test fun noEnabledItemsRemainUnselected() {
        assertEquals(-1, Bbos6ContextMenuNavigation.firstEnabled(emptyList()))
        val disabled = listOf(Bbos6ContextMenuNavigation.Cell(0, 0, false))
        assertEquals(-1, Bbos6ContextMenuNavigation.firstEnabled(disabled))
        assertEquals(-1, Bbos6ContextMenuNavigation.move(disabled, -1, DOWN))
    }
}
