package dev.q25.bbos6.ui.compose

import dev.q25.bbos6.ui.Bbos6ContextMenuItem
import dev.q25.bbos6.ui.Bbos6PropertiesItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Bbos6JavaDialogBridgeTest {
    @Test fun menuActionIdsLabelsEnabledStateAndGroupsPreserved() {
        val first = Bbos6ContextMenuItem.text("play", "Play")
        val disabled = Bbos6ContextMenuItem.text("remove", "Remove").withEnabled(false)
        val third = Bbos6ContextMenuItem.resource("info", "Properties", 12)
        val mapped = javaContextActions(listOf(listOf(first, disabled), listOf(third)))
        assertEquals(2, mapped.size)
        assertEquals(listOf("play", "remove", "info"), mapped.flatten().map { it.id })
        assertEquals(listOf("Play", "Remove", "Properties"), mapped.flatten().map { it.label })
        assertFalse(mapped[0][1].enabled)
        assertTrue(mapped[1][0].enabled)
        assertEquals(12, mapped[1][0].iconResourceId)
    }

    @Test fun propertiesFourRowKindsPreserveExactContent() {
        val rows = javaPropertyRows(listOf(
            Bbos6PropertiesItem.text("/storage/Music"),
            Bbos6PropertiesItem.inline("Title:", "Example"),
            Bbos6PropertiesItem.trailing("Size:", "2 MB"),
            Bbos6PropertiesItem.spacer(9)
        ))
        assertEquals(Bbos6PropertyRow.Text("/storage/Music"), rows[0])
        assertEquals(Bbos6PropertyRow.Inline("Title:", "Example"), rows[1])
        assertEquals(Bbos6PropertyRow.Trailing("Size:", "2 MB"), rows[2])
        assertEquals(Bbos6PropertyRow.Spacer(9), rows[3])
    }
}
