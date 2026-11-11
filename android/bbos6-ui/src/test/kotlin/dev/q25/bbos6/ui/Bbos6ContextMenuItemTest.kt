package dev.q25.bbos6.ui

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class Bbos6ContextMenuItemTest {
    @Test fun resourceIconIsOwnedByCaller() {
        val item = Bbos6ContextMenuItem.resource("play", "Play", 101)
        assertEquals("play", item.id())
        assertEquals("Play", item.label())
        assertEquals(101, item.iconResourceId())
        assertTrue(item.enabled())
    }

    @Test fun disabledItemPreservesIconAndId() {
        val enabled = Bbos6ContextMenuItem.resource("delete", "Delete", 55)
        val disabled = enabled.withEnabled(false)
        assertFalse(disabled.enabled())
        assertTrue(enabled.enabled())
        assertEquals(enabled.id(), disabled.id())
        assertEquals(enabled.label(), disabled.label())
        assertEquals(enabled.iconResourceId(), disabled.iconResourceId())
    }

    @Test fun textOnlyItemRetainsLayoutPosition() {
        val item = Bbos6ContextMenuItem.text("details", "Details")
        assertEquals(0, item.iconResourceId())
        assertTrue(item.enabled())
    }

    @Test fun invalidIdentifiersAndResourcesFailFast() {
        assertThrows(IllegalArgumentException::class.java) {
            Bbos6ContextMenuItem.text("  ", "Label")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Bbos6ContextMenuItem.text("key", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Bbos6ContextMenuItem.resource("key", "Label", 0)
        }
        // Verify the Java-callable API rejects null, rather than bypassing
        // Kotlin's compiler-enforced null safety in this source file.
        val method = Bbos6ContextMenuItem::class.java.getMethod(
            "text", String::class.java, String::class.java
        )
        val thrown = assertThrows(InvocationTargetException::class.java) {
            method.invoke(null, null, "Label")
        }
        assertTrue(thrown.cause is NullPointerException)
    }
}
