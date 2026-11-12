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
}
