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

    @Test public void horizontalKeysChangePagesAtGridEdges() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 2f, 0, 20);
        assertNotNull(grid);
        assertEquals(1, grid.nextIndex(0, 3));  // right inside row
        assertEquals(0, grid.nextIndex(0, 2));  // no page to the left
        assertEquals(4, grid.nextIndex(0, 1));  // down within page
        assertEquals(0, grid.nextIndex(4, 0));  // up within page
        assertEquals(8, grid.nextIndex(3, 3));  // right -> next page, row 1
        assertEquals(12, grid.nextIndex(7, 3)); // right -> next page, row 2
        assertEquals(3, grid.nextIndex(8, 2));  // left -> previous page, row 1
        assertEquals(7, grid.nextIndex(12, 2)); // left -> previous page, row 2
        assertEquals(16, grid.nextIndex(11, 3)); // page 2 -> page 3
        assertEquals(16, grid.nextIndex(15, 3)); // final partial page, top row
        assertEquals(11, grid.nextIndex(16, 2)); // page 3 -> page 2, top row
    }

    @Test public void verticalKeysNeverSwitchPages() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 2f, 0, 20);
        assertNotNull(grid);
        assertEquals(4, grid.nextIndex(4, 1));  // bottom row, stay on page 1
        assertEquals(8, grid.nextIndex(8, 0));  // top row, stay on page 2
        assertEquals(12, grid.nextIndex(12, 1)); // bottom row, stay on page 2
        assertEquals(16, grid.nextIndex(16, 0)); // top row, stay on page 3
        assertEquals(19, grid.nextIndex(19, 1)); // no bottom row on page 3
        assertEquals(19, grid.nextIndex(19, 0)); // no previous row on page 3
    }

    @Test public void partialLastPageUsesHorizontalFallback() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 2f, 0, 9);
        assertNotNull(grid);
        assertEquals(8, grid.nextIndex(3, 3));  // next page, first row
        assertEquals(8, grid.nextIndex(7, 3));  // no second row on next page
        assertEquals(3, grid.nextIndex(8, 2));  // previous page
        assertEquals(8, grid.nextIndex(8, 3));  // no icon to the right
        assertEquals(8, grid.nextIndex(8, 1));  // no second row
    }

    @Test public void onePageDoesNotScrollPastItsEdges() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 2f, 0, 8);
        assertNotNull(grid);
        assertEquals(3, grid.nextIndex(3, 3));  // no next page
        assertEquals(7, grid.nextIndex(7, 3));  // no next page
        assertEquals(0, grid.nextIndex(0, 2));  // no previous page
        assertEquals(4, grid.nextIndex(4, 1));  // no vertical page transition
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
