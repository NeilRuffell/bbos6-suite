package dev.q25.bbos6.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class Bbos6ContextMenuItemTest {
    @Test public void resourceIconIsOwnedByCaller() {
        Bbos6ContextMenuItem item =
                Bbos6ContextMenuItem.resource("play", "Play", 101);
        assertEquals("play", item.id());
        assertEquals("Play", item.label());
        assertEquals(101, item.iconResourceId());
        assertTrue(item.enabled());
    }

    @Test public void disabledItemPreservesIconAndId() {
        Bbos6ContextMenuItem enabled =
                Bbos6ContextMenuItem.resource("delete", "Delete", 55);
        Bbos6ContextMenuItem disabled = enabled.withEnabled(false);
        assertFalse(disabled.enabled());
        assertTrue(enabled.enabled());
        assertEquals(enabled.id(), disabled.id());
        assertEquals(enabled.label(), disabled.label());
        assertEquals(enabled.iconResourceId(), disabled.iconResourceId());
    }

    @Test public void textOnlyItemRetainsLayoutPosition() {
        Bbos6ContextMenuItem item =
                Bbos6ContextMenuItem.text("details", "Details");
        assertEquals(0, item.iconResourceId());
        assertTrue(item.enabled());
    }

    @Test public void invalidIdentifiersAndResourcesFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> Bbos6ContextMenuItem.text("  ", "Label"));
        assertThrows(IllegalArgumentException.class,
                () -> Bbos6ContextMenuItem.text("key", ""));
        assertThrows(IllegalArgumentException.class,
                () -> Bbos6ContextMenuItem.resource("key", "Label", 0));
        assertThrows(NullPointerException.class,
                () -> Bbos6ContextMenuItem.text(null, "Label"));
    }
}
