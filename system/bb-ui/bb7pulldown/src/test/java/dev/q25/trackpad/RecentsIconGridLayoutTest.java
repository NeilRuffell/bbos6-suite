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

    @Test public void completeTwoRowGridIsCenteredVertically() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 2f, 0, 8);
        assertNotNull(grid);
        int gridHeight = RecentsIconGridLayout.ROWS * grid.cellHeight
                + (RecentsIconGridLayout.ROWS - 1) * grid.gap;
        assertEquals((720 - gridHeight) / 2, grid.top);
        assertEquals(grid.top, grid.y(0));
        assertEquals(grid.top + grid.cellHeight + grid.gap, grid.y(4));
        assertTrue(Math.abs((grid.top * 2 + gridHeight) - 720) <= 1);
    }

    @Test public void allPagesStayAtSameVerticalPosition() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 1.4f, 0, 17);
        assertNotNull(grid);
        assertEquals(3, grid.pageCount);
        assertEquals(grid.y(0), grid.y(8));
        assertEquals(grid.y(0), grid.y(16));
        assertEquals(grid.y(4), grid.y(12));
    }

    @Test public void compactCellsHaveSmallGapsAndRemainCentered() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 1.4f, 0, 8);
        assertNotNull(grid);
        assertEquals(Math.round(82 * 1.4f), grid.cellWidth);
        assertEquals(Math.round(6 * 1.4f), grid.gap);
        int blockWidth = RecentsIconGridLayout.COLUMNS * grid.cellWidth
                + (RecentsIconGridLayout.COLUMNS - 1) * grid.gap;
        int blockHeight = RecentsIconGridLayout.ROWS * grid.cellHeight
                + (RecentsIconGridLayout.ROWS - 1) * grid.gap;
        assertEquals((720 - blockWidth) / 2, grid.x(0));
        assertEquals((720 - blockHeight) / 2, grid.y(0));
        assertEquals(grid.cellWidth + grid.gap, grid.x(1) - grid.x(0));
        assertEquals(grid.cellHeight + grid.gap, grid.y(4) - grid.y(0));
        assertTrue(blockWidth < 500); // formerly nearly full 720px
        assertTrue(blockHeight < 250);
    }

    @Test public void compactGridKeepsPageAndTapCoordinatesAligned() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 1.4f, 0, 17);
        assertNotNull(grid);
        assertEquals(grid.x(0) + 720, grid.x(8));
        assertEquals(grid.x(0) + 1440, grid.x(16));
        assertEquals(grid.y(0), grid.y(8));
        assertEquals(grid.y(0), grid.y(16));
        assertTrue(grid.cellWidth >= Math.round(55 * 1.4f) + Math.round(14 * 1.4f));
    }

    @Test public void selectedAppHeaderSharesTheGridCenterAndWidth() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 1.4f, 0, 17);
        assertNotNull(grid);
        int leftEdge = grid.x(0);
        int rightEdge = grid.x(3) + grid.cellWidth;
        assertEquals(rightEdge - leftEdge, grid.gridWidth());
        assertEquals(360f, grid.centerX(), 0.5f);
        assertEquals((leftEdge + rightEdge) / 2f, grid.centerX(), 0.5f);
        // Header is one fixed heading for every horizontal page.
        assertEquals(grid.centerX(), (grid.x(8) - 720
                + grid.x(11) - 720 + grid.cellWidth) / 2f, 0.5f);
    }

    @Test public void pageCounterShowsOneBasedPositionForMultiplePages() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 1.4f, 0, 17);
        assertNotNull(grid);
        assertEquals(3, grid.pageCount);
        assertEquals("1/3", grid.pageCounterText(0));
        assertEquals("2/3", grid.pageCounterText(1));
        assertEquals("3/3", grid.pageCounterText(2));
        assertEquals("1/3", grid.pageCounterText(-5)); // clamp invalid page
        assertEquals("3/3", grid.pageCounterText(99));
    }

    @Test public void pageCounterHiddenWhenOnlyOneOrNoPageExists() {
        RecentsIconGridLayout one = RecentsIconGridLayout.calculate(
                720, 720, 1.4f, 0, 8);
        RecentsIconGridLayout empty = RecentsIconGridLayout.calculate(
                720, 720, 1.4f, 0, 0);
        RecentsIconGridLayout two = RecentsIconGridLayout.calculate(
                720, 720, 1.4f, 0, 9);
        assertNotNull(one);
        assertNotNull(empty);
        assertNotNull(two);
        assertNull(one.pageCounterText(0));
        assertNull(empty.pageCounterText(0));
        assertEquals("1/2", two.pageCounterText(0));
        assertEquals("2/2", two.pageCounterText(1));
    }

    @Test public void pageCounterAnchorIsBelowBothRowsOnEveryPage() {
        RecentsIconGridLayout grid = RecentsIconGridLayout.calculate(
                720, 720, 1.4f, 0, 17);
        assertNotNull(grid);
        assertEquals(grid.y(4) + grid.cellHeight, grid.gridBottom());
        assertEquals(grid.y(12) + grid.cellHeight, grid.gridBottom());
        assertEquals(grid.top + 2 * grid.cellHeight + grid.gap,
                grid.gridBottom());
        assertEquals(360f, grid.centerX(), 0.5f);
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
