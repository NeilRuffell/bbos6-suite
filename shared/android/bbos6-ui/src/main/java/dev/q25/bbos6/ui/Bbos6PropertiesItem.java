package dev.q25.bbos6.ui;

import java.util.Objects;

/**
 * One content row of a BBOS6-style properties popup.
 *
 * This model is intentionally independent of Android, files, music metadata,
 * or any specific application. Formatting and field availability are supplied
 * by the calling application.
 */
public final class Bbos6PropertiesItem {
    public enum Kind {
        TEXT,
        INLINE_PROPERTY,
        TRAILING_PROPERTY,
        SPACER
    }

    private final Kind kind;
    private final String label;
    private final String value;
    private final int spaceDp;

    private Bbos6PropertiesItem(Kind kind, String label, String value, int spaceDp) {
        this.kind = kind;
        this.label = label;
        this.value = value;
        this.spaceDp = spaceDp;
    }

    /** A full-width text line (for example, a path, filename, or file type). */
    public static Bbos6PropertiesItem text(String text) {
        return new Bbos6PropertiesItem(Kind.TEXT,
                Objects.requireNonNull(text, "text"), "", 0);
    }

    /** Label and value continue on the same text line: "Title: Sample". */
    public static Bbos6PropertiesItem inline(String label, String value) {
        return new Bbos6PropertiesItem(Kind.INLINE_PROPERTY,
                Objects.requireNonNull(label, "label"),
                Objects.requireNonNull(value, "value"), 0);
    }

    /** Label on the left and value aligned to the right: "Size:       2 MB". */
    public static Bbos6PropertiesItem trailing(String label, String value) {
        return new Bbos6PropertiesItem(Kind.TRAILING_PROPERTY,
                Objects.requireNonNull(label, "label"),
                Objects.requireNonNull(value, "value"), 0);
    }

    /** Vertical whitespace between groups, measured in density-independent pixels. */
    public static Bbos6PropertiesItem spacer(int dp) {
        if (dp < 0) throw new IllegalArgumentException("Spacer cannot be negative");
        return new Bbos6PropertiesItem(Kind.SPACER, "", "", dp);
    }

    public Kind kind() {
        return kind;
    }

    public String label() {
        return label;
    }

    public String value() {
        return value;
    }

    public int spaceDp() {
        return spaceDp;
    }
}
