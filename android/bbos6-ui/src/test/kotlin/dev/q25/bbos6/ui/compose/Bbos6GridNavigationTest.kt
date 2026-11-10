package dev.q25.bbos6.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class Bbos6GridNavigationTest {
    private val cells = listOf(
        Bbos6GridCell(0, 0, true),
        Bbos6GridCell(0, 1, false),
        Bbos6GridCell(0, 2, true),
        Bbos6GridCell(1, 0, false),
        Bbos6GridCell(1, 1, true),
        Bbos6GridCell(2, 2, true)
    )

    @Test fun skipsDisabledAndClampsEdges() {
        assertEquals(0, Bbos6GridNavigation.firstEnabled(cells))
        assertEquals(-1, Bbos6GridNavigation.firstEnabled(cells.map { it.copy(enabled = false) }))
        assertEquals(0, Bbos6GridNavigation.move(cells, -1, Bbos6GridDirection.DOWN))
        assertEquals(2, Bbos6GridNavigation.move(cells, 0, Bbos6GridDirection.RIGHT))
        assertEquals(2, Bbos6GridNavigation.move(cells, 2, Bbos6GridDirection.RIGHT))
        assertEquals(0, Bbos6GridNavigation.move(cells, 2, Bbos6GridDirection.LEFT))
    }

    @Test fun verticalNearestAndPartialRows() {
        assertEquals(4, Bbos6GridNavigation.move(cells, 0, Bbos6GridDirection.DOWN))
        assertEquals(5, Bbos6GridNavigation.move(cells, 4, Bbos6GridDirection.DOWN))
        assertEquals(4, Bbos6GridNavigation.move(cells, 5, Bbos6GridDirection.UP))
        assertEquals(2, Bbos6GridNavigation.move(cells, 4, Bbos6GridDirection.UP))
        assertEquals(0, Bbos6GridNavigation.move(cells, 0, Bbos6GridDirection.UP))
    }

    @Test fun equalDistanceFavorsLeft() {
        val options = listOf(Bbos6GridCell(0, 1, true),
            Bbos6GridCell(1, 0, true), Bbos6GridCell(1, 2, true))
        assertEquals(1, Bbos6GridNavigation.move(options, 0, Bbos6GridDirection.DOWN))
    }
}
