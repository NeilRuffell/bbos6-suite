package dev.q25.bbos6.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/** Pure Java model tests: no device, Android framework or audio file required. */
public final class Bbos6PropertiesItemTest {
    @Test public void textRetainsProvidedString() {
        Bbos6PropertiesItem item = Bbos6PropertiesItem.text("/Device/samples/music/");
        assertEquals(Bbos6PropertiesItem.Kind.TEXT, item.kind());
        assertEquals("/Device/samples/music/", item.label());
        assertEquals("", item.value());
    }

    @Test public void inlinePropertyRetainsExactLabelAndValue() {
        Bbos6PropertiesItem item = Bbos6PropertiesItem.inline("Album:", "Future Breeds");
        assertEquals(Bbos6PropertiesItem.Kind.INLINE_PROPERTY, item.kind());
        assertEquals("Album:", item.label());
        assertEquals("Future Breeds", item.value());
    }

    @Test public void trailingPropertyRetainsExactLabelAndValue() {
        Bbos6PropertiesItem item = Bbos6PropertiesItem.trailing(
                "Last Modified:", "28 Feb 2006 16:00");
        assertEquals(Bbos6PropertiesItem.Kind.TRAILING_PROPERTY, item.kind());
        assertEquals("Last Modified:", item.label());
        assertEquals("28 Feb 2006 16:00", item.value());
    }

    @Test public void spacerUsesDpWithoutConvertingToPxInTheModel() {
        Bbos6PropertiesItem item = Bbos6PropertiesItem.spacer(4);
        assertEquals(Bbos6PropertiesItem.Kind.SPACER, item.kind());
        assertEquals(4, item.spaceDp());
    }

    @Test public void negativeSpacerIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Bbos6PropertiesItem.spacer(-1));
    }

    @Test public void missingMetadataIsNotInvented() {
        assertThrows(NullPointerException.class,
                () -> Bbos6PropertiesItem.inline("Artist:", null));
        assertThrows(NullPointerException.class,
                () -> Bbos6PropertiesItem.trailing(null, "Value"));
    }
}
