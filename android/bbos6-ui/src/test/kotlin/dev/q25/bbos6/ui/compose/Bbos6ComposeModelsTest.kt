package dev.q25.bbos6.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Bbos6ComposeModelsTest {
    @Test fun preservesContextActionIdentityAndState() {
        val action = Bbos6ContextAction.text("properties", "Properties")
        assertEquals("properties", action.id)
        assertEquals("Properties", action.label)
        assertEquals(false, action.copy(enabled = false).enabled)
        assertEquals(true, action.enabled)
        assertEquals(0, action.iconResourceId)
    }

    @Test fun requiresValidActionIdsAndIcons() {
        assertThrows(IllegalArgumentException::class.java) {
            Bbos6ContextAction.text("", "Open")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Bbos6ContextAction.resource("open", "Open", 0)
        }
    }

    @Test fun preservesPropertiesRowsWithoutSynthesizingValues() {
        val rows: List<Bbos6PropertyRow> = listOf(
            Bbos6PropertyRow.Text("/Music"),
            Bbos6PropertyRow.Inline("Artist:", ""),
            Bbos6PropertyRow.Trailing("Size:", "1 MB"),
            Bbos6PropertyRow.Spacer(3)
        )
        assertEquals("/Music", (rows[0] as Bbos6PropertyRow.Text).content)
        assertEquals("", (rows[1] as Bbos6PropertyRow.Inline).value)
        assertEquals("1 MB", (rows[2] as Bbos6PropertyRow.Trailing).value)
        assertEquals(3, (rows[3] as Bbos6PropertyRow.Spacer).heightDp)
    }

    @Test fun rejectsNegativeSpacer() {
        assertThrows(IllegalArgumentException::class.java) {
            Bbos6PropertyRow.Spacer(-1)
        }
    }
}
