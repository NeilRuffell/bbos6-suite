package dev.q25.trackpad;

/**
 * Layout policy for the single native Quickstep RecentsView. No Android,
 * Xposed or task-service dependency; suitable for a direct ROM port.
 *
 * The BBUI grid uses horizontal viewport page offsets, with eight native
 * task icons per page. No overlay, secondary view tree or duplicate task model.
 */
final class RecentsIconGridLayout {
    static final int COLUMNS = 4;
    static final int ROWS = 2;
    static final int PAGE_SIZE = COLUMNS * ROWS;

    final int pageWidth;
    final int cellWidth;
    final int cellHeight;
    final int gap;
    final int left;
    final int top;
    final int tasks;
    final int pageCount;

    private RecentsIconGridLayout(int width, int tileWidth, int tileHeight,
            int spacing, int leftPadding, int topPadding, int taskCount) {
        pageWidth = width;
        cellWidth = tileWidth;
        cellHeight = tileHeight;
        gap = spacing;
        left = leftPadding;
        top = topPadding;
        tasks = taskCount;
        pageCount = (taskCount + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    static RecentsIconGridLayout calculate(int width, int height,
            float density, int statusBarOverlap, int taskCount) {
        if (width <= 0 || height <= 0 || density <= 0 || taskCount < 0) return null;
        // Cap each hit target at 82dp, leaving the existing 55dp icons
        // unchanged but bringing adjacent icons much closer together.
        // Six-dp gaps and an evenly centered four-column block avoid an
        // unnecessarily wide grid on the Q25's 720px Recents viewport.
        int spacing = Math.max(2, Math.round(6 * density));
        int topPadding = Math.max(0, statusBarOverlap) + Math.round(18 * density);
        int availableCellWidth = (width - spacing * (COLUMNS + 1)) / COLUMNS;
        int tileWidth = Math.min(Math.round(82 * density), availableCellWidth);
        int tileHeight = Math.min(tileWidth,
                (height - topPadding - spacing * (ROWS + 1)) / ROWS);
        if (tileWidth < Math.round(40 * density)
                || tileHeight < Math.round(40 * density)) return null;
        // Center the *complete* two-row grid in the Recents viewport.
        // Each page shares the same cell positions; horizontal page changes
        // therefore do not move the icons vertically. Keep the reserved top
        // inset as a minimum in unusually short viewports.
        int gridHeight = ROWS * tileHeight + (ROWS - 1) * spacing;
        int gridWidth = COLUMNS * tileWidth + (COLUMNS - 1) * spacing;
        int centeredTop = Math.max(topPadding, (height - gridHeight) / 2);
        int centeredLeft = (width - gridWidth) / 2;
        return new RecentsIconGridLayout(width, tileWidth, tileHeight,
                spacing, centeredLeft, centeredTop, taskCount);
    }

    /** Full width of the icon grid, including its three inner gaps. */
    int gridWidth() {
        return COLUMNS * cellWidth + (COLUMNS - 1) * gap;
    }

    /** Shared horizontal center for the selected-app heading and grid. */
    float centerX() {
        return left + gridWidth() / 2f;
    }

    /** Bottom edge of both rows, shared by all Recents pages. */
    int gridBottom() {
        return top + ROWS * cellHeight + (ROWS - 1) * gap;
    }

    /** Display a one-based page counter only when another page exists. */
    String pageCounterText(int currentPage) {
        if (pageCount <= 1) return null;
        int visiblePage = Math.min(Math.max(currentPage, 0), pageCount - 1) + 1;
        return visiblePage + "/" + pageCount;
    }

    int page(int taskIndex) {
        return taskIndex / PAGE_SIZE;
    }

    int x(int taskIndex) {
        return page(taskIndex) * pageWidth
                + left + (taskIndex % COLUMNS) * (cellWidth + gap);
    }

    int y(int taskIndex) {
        int row = (taskIndex % PAGE_SIZE) / COLUMNS;
        return top + row * (cellHeight + gap);
    }

    int scrollForTask(int taskIndex) {
        return page(taskIndex) * pageWidth;
    }

    int lastTaskScroll() {
        return Math.max(0, pageCount - 1) * pageWidth;
    }

    int nextIndex(int current, int direction) {
        if (tasks == 0) return -1;
        if (current < 0 || current >= tasks) current = 0;
        int currentPage = page(current);
        int withinPage = current % PAGE_SIZE;
        int row = withinPage / COLUMNS;
        int column = withinPage % COLUMNS;
        switch (direction) {
            case 0: // Up: never leave the current page.
                return row > 0 ? current - COLUMNS : current;
            case 1: { // Down: never leave the current page.
                int below = current + COLUMNS;
                return row + 1 < ROWS && below < tasks
                        && page(below) == currentPage ? below : current;
            }
            case 2: // Left: leave the page horizontally at its left edge.
                if (column > 0) return current - 1;
                return currentPage > 0
                        ? (currentPage - 1) * PAGE_SIZE + row * COLUMNS
                                + COLUMNS - 1
                        : current;
            case 3: { // Right: leave the page horizontally at its right edge.
                if (column < COLUMNS - 1) {
                    int right = current + 1;
                    return right < tasks && page(right) == currentPage
                            ? right : current;
                }
                int nextPageStart = (currentPage + 1) * PAGE_SIZE;
                if (nextPageStart >= tasks) return current;
                int matchingRow = nextPageStart + row * COLUMNS;
                return matchingRow < tasks ? matchingRow : nextPageStart;
            }
            default:
                return current;
        }
    }
}
