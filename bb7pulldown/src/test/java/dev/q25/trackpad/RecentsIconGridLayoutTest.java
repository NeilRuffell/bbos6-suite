package dev.q25.trackpad;

import static org.junit.Assert.*;
import org.junit.Test;

/** The icon painter uses these coordinates; native Quickstep owns task data. */
public final class RecentsIconGridLayoutTest {
    @Test public void eightIconsFitOnePage() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 2f, 0, 8);
        assertNotNull(grid);
        assertEquals(4, RecentsIconGridLayout.COLUMNS);
        assertEquals(2, RecentsIconGridLayout.ROWS);
        assertEquals(8, RecentsIconGridLayout.PAGE_SIZE);
        assertEquals(1, grid.pageCount);
        assertEquals(0, grid.scrollForTask(7));
        assertEquals(grid.x(0), grid.x(4));
        assertEquals(grid.y(0), grid.y(1));
        assertTrue(grid.x(1) > grid.x(0));
        assertTrue(grid.y(4) > grid.y(0));
        assertEquals(0, grid.lastTaskScroll());
    }

    @Test public void ninthIconStartsSecondPage() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 2f, 0, 9);
        assertNotNull(grid);
        assertEquals(2, grid.pageCount);
        assertEquals(0, grid.page(7));
        assertEquals(1, grid.page(8));
        assertEquals(720, grid.scrollForTask(8));
        assertEquals(720, grid.lastTaskScroll());
        assertEquals(grid.x(0) + 720, grid.x(8));
        assertEquals(grid.y(0), grid.y(8));
        assertEquals(grid.y(7), grid.y(15));
    }

    @Test public void seventeenthIconStartsThirdPage() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 2f, 0, 17);
        assertNotNull(grid);
        assertEquals(3, grid.pageCount);
        assertEquals(720, grid.scrollForTask(15));
        assertEquals(1440, grid.scrollForTask(16));
        assertEquals(1440, grid.lastTaskScroll());
        assertEquals(grid.x(0) + 1440, grid.x(16));
        assertEquals(grid.y(0), grid.y(16));
    }

    @Test public void directionalNavigationUsesFourColumns() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 2f, 0, 20);
        assertNotNull(grid);
        assertEquals(1, grid.nextIndex(0, 3)); // right
        assertEquals(0, grid.nextIndex(0, 2)); // left boundary
        assertEquals(4, grid.nextIndex(0, 1)); // down
        assertEquals(0, grid.nextIndex(4, 0)); // up
        assertEquals(3, grid.nextIndex(3, 3)); // right column boundary
        assertEquals(8, grid.nextIndex(4, 1)); // next page
        assertEquals(4, grid.nextIndex(8, 0)); // previous page
        assertEquals(19, grid.nextIndex(19, 1)); // end of task list
    }

    @Test public void smallViewportsFailClosed() {
        assertNull(RecentsIconGridLayout.calculate(100, 120, 1f, 0, 4));
        assertNull(RecentsIconGridLayout.calculate(0, 640, 1f, 0, 1));
        assertNull(RecentsIconGridLayout.calculate(480, 640, 0, 0, 1));
    }

    @Test public void emptyTasksHaveNoNegativeScroll() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 2f, 0, 0);
        assertNotNull(grid);
        assertEquals(0, grid.pageCount);
        assertEquals(0, grid.lastTaskScroll());
        assertEquals(-1, grid.nextIndex(0, 1));
    }
}
