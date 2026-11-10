package dev.q25.bbos6.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Bbos6ContextMenuNavigationTest {
    private static final Bbos6ContextMenuNavigation.Direction LEFT =
            Bbos6ContextMenuNavigation.Direction.LEFT;
    private static final Bbos6ContextMenuNavigation.Direction RIGHT =
            Bbos6ContextMenuNavigation.Direction.RIGHT;
    private static final Bbos6ContextMenuNavigation.Direction UP =
            Bbos6ContextMenuNavigation.Direction.UP;
    private static final Bbos6ContextMenuNavigation.Direction DOWN =
            Bbos6ContextMenuNavigation.Direction.DOWN;

    private static List<Bbos6ContextMenuNavigation.Cell> grid() {
        return Arrays.asList(
                new Bbos6ContextMenuNavigation.Cell(0, 0, true),
                new Bbos6ContextMenuNavigation.Cell(0, 1, true),
                new Bbos6ContextMenuNavigation.Cell(0, 2, true),
                new Bbos6ContextMenuNavigation.Cell(1, 0, true),
                new Bbos6ContextMenuNavigation.Cell(1, 1, true),
                new Bbos6ContextMenuNavigation.Cell(1, 2, true),
                new Bbos6ContextMenuNavigation.Cell(2, 0, true),
                new Bbos6ContextMenuNavigation.Cell(2, 1, true),
                new Bbos6ContextMenuNavigation.Cell(2, 2, true));
    }

    @Test public void movesAcrossReferenceThreeByThreeMenu() {
        List<Bbos6ContextMenuNavigation.Cell> cells = grid();
        assertEquals(4, Bbos6ContextMenuNavigation.move(cells, 1, DOWN));
        assertEquals(7, Bbos6ContextMenuNavigation.move(cells, 4, DOWN));
        assertEquals(6, Bbos6ContextMenuNavigation.move(cells, 7, LEFT));
        assertEquals(8, Bbos6ContextMenuNavigation.move(cells, 7, RIGHT));
        assertEquals(5, Bbos6ContextMenuNavigation.move(cells, 8, UP));
        assertEquals(1, Bbos6ContextMenuNavigation.move(cells, 4, UP));
    }

    @Test public void doesNotWrapOffAnEdge() {
        List<Bbos6ContextMenuNavigation.Cell> cells = grid();
        assertEquals(0, Bbos6ContextMenuNavigation.move(cells, 0, LEFT));
        assertEquals(2, Bbos6ContextMenuNavigation.move(cells, 2, RIGHT));
        assertEquals(1, Bbos6ContextMenuNavigation.move(cells, 1, UP));
        assertEquals(7, Bbos6ContextMenuNavigation.move(cells, 7, DOWN));
    }

    @Test public void skipsDisabledHorizontalItems() {
        List<Bbos6ContextMenuNavigation.Cell> cells = Arrays.asList(
                new Bbos6ContextMenuNavigation.Cell(0, 0, true),
                new Bbos6ContextMenuNavigation.Cell(0, 1, false),
                new Bbos6ContextMenuNavigation.Cell(0, 2, true));
        assertEquals(2, Bbos6ContextMenuNavigation.move(cells, 0, RIGHT));
        assertEquals(0, Bbos6ContextMenuNavigation.move(cells, 2, LEFT));
    }

    @Test public void incompleteRowsPreferSameColumnThenNearestEnabled() {
        List<Bbos6ContextMenuNavigation.Cell> cells = Arrays.asList(
                new Bbos6ContextMenuNavigation.Cell(0, 0, true),
                new Bbos6ContextMenuNavigation.Cell(0, 1, true),
                new Bbos6ContextMenuNavigation.Cell(0, 2, true),
                new Bbos6ContextMenuNavigation.Cell(1, 0, true));
        assertEquals(3, Bbos6ContextMenuNavigation.move(cells, 2, DOWN));
        assertEquals(0, Bbos6ContextMenuNavigation.move(cells, 3, UP));
    }

    @Test public void skipsCompletelyDisabledRows() {
        List<Bbos6ContextMenuNavigation.Cell> cells = Arrays.asList(
                new Bbos6ContextMenuNavigation.Cell(0, 1, true),
                new Bbos6ContextMenuNavigation.Cell(1, 1, false),
                new Bbos6ContextMenuNavigation.Cell(2, 1, true));
        assertEquals(2, Bbos6ContextMenuNavigation.move(cells, 0, DOWN));
        assertEquals(0, Bbos6ContextMenuNavigation.move(cells, 2, UP));
    }

    @Test public void noEnabledItemsRemainUnselected() {
        assertEquals(-1, Bbos6ContextMenuNavigation.firstEnabled(Collections.emptyList()));
        List<Bbos6ContextMenuNavigation.Cell> disabled = Collections.singletonList(
                new Bbos6ContextMenuNavigation.Cell(0, 0, false));
        assertEquals(-1, Bbos6ContextMenuNavigation.firstEnabled(disabled));
        assertEquals(-1, Bbos6ContextMenuNavigation.move(disabled, -1, DOWN));
    }
}
