package dev.q25.bbos6.ui;

import java.util.List;

/**
 * Pure-Java, deterministic physical trackpad navigation for a grouped grid.
 * No dependency on Android's key dispatch, application pages, or scrollviews.
 */
final class Bbos6ContextMenuNavigation {
    enum Direction { LEFT, RIGHT, UP, DOWN }

    static final class Cell {
        final int row;
        final int column;
        final boolean enabled;

        Cell(int row, int column, boolean enabled) {
            this.row = row;
            this.column = column;
            this.enabled = enabled;
        }
    }

    private Bbos6ContextMenuNavigation() {}

    static int firstEnabled(List<Cell> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).enabled) return i;
        }
        return -1;
    }

    /**
     * Horizontal navigation stays in the same row. Vertical navigation moves
     * to the nearest enabled column in the next non-empty row, preserving the
     * original column when it is available. No unexpected edge wrapping.
     */
    static int move(List<Cell> cells, int selected, Direction direction) {
        if (selected < 0 || selected >= cells.size()
                || !cells.get(selected).enabled) return firstEnabled(cells);
        Cell current = cells.get(selected);

        if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            boolean right = direction == Direction.RIGHT;
            int best = selected;
            int distance = Integer.MAX_VALUE;
            for (int i = 0; i < cells.size(); i++) {
                Cell candidate = cells.get(i);
                if (!candidate.enabled || candidate.row != current.row) continue;
                int delta = candidate.column - current.column;
                if ((right && delta <= 0) || (!right && delta >= 0)) continue;
                int separation = Math.abs(delta);
                if (separation < distance) {
                    best = i;
                    distance = separation;
                }
            }
            return best;
        }

        boolean down = direction == Direction.DOWN;
        int nextRow = current.row + (down ? 1 : -1);
        int lastRow = -1;
        for (Cell cell : cells) lastRow = Math.max(lastRow, cell.row);

        while (nextRow >= 0 && nextRow <= lastRow) {
            int best = -1;
            int distance = Integer.MAX_VALUE;
            for (int i = 0; i < cells.size(); i++) {
                Cell candidate = cells.get(i);
                if (!candidate.enabled || candidate.row != nextRow) continue;
                int separation = Math.abs(candidate.column - current.column);
                if (separation < distance
                        || (separation == distance && best >= 0
                        && candidate.column < cells.get(best).column)) {
                    best = i;
                    distance = separation;
                }
            }
            if (best >= 0) return best;
            nextRow += down ? 1 : -1;
        }
        return selected;
    }
}
