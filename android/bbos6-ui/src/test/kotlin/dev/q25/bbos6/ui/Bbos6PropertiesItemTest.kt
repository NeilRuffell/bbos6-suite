package dev.q25.bbos6.ui

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class Bbos6PropertiesItemTest {
    @Test fun textRetainsProvidedString() {
        val item = Bbos6PropertiesItem.text("/Device/samples/music/")
        assertEquals(Bbos6PropertiesItem.Kind.TEXT, item.kind())
        assertEquals("/Device/samples/music/", item.label())
        assertEquals("", item.value())
    }

    @Test fun inlinePropertyRetainsExactLabelAndValue() {
        val item = Bbos6PropertiesItem.`inline`("Album:", "Future Breeds")
        assertEquals(Bbos6PropertiesItem.Kind.INLINE_PROPERTY, item.kind())
        assertEquals("Album:", item.label())
        assertEquals("Future Breeds", item.value())
    }

    @Test fun trailingPropertyRetainsExactLabelAndValue() {
        val item = Bbos6PropertiesItem.trailing(
            "Last Modified:", "28 Feb 2006 16:00"
        )
        assertEquals(Bbos6PropertiesItem.Kind.TRAILING_PROPERTY, item.kind())
        assertEquals("Last Modified:", item.label())
        assertEquals("28 Feb 2006 16:00", item.value())
    }

    @Test fun spacerUsesDpWithoutConvertingToPxInTheModel() {
        val item = Bbos6PropertiesItem.spacer(4)
        assertEquals(Bbos6PropertiesItem.Kind.SPACER, item.kind())
        assertEquals(4, item.spaceDp())
    }

    @Test fun negativeSpacerIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Bbos6PropertiesItem.spacer(-1)
        }
    }

    @Test fun missingMetadataIsNotInvented() {
        val method = Bbos6PropertiesItem::class.java.getMethod(
            "inline", String::class.java, String::class.java
        )
        val exception = assertThrows(InvocationTargetException::class.java) {
            method.invoke(null, "Artist:", null)
        }
        assertTrue(exception.cause is NullPointerException)
        val other = assertThrows(InvocationTargetException::class.java) {
            method.invoke(null, null, "Value")
        }
        assertTrue(other.cause is NullPointerException)
    }
}
