package dev.q25.bbos6.ui;

import android.graphics.drawable.Drawable;

import java.util.Objects;

/**
 * An immutable action description for the reusable BBOS6 grid context menu.
 *
 * Action IDs and icon resources belong to the calling application. This
 * library neither chooses menu commands nor performs application actions.
 */
public final class Bbos6ContextMenuItem {
    private final String id;
    private final String label;
    private final int iconResourceId;
    private final Drawable iconDrawable;
    private final boolean enabled;

    private Bbos6ContextMenuItem(String id, String label, int iconResourceId,
            Drawable iconDrawable, boolean enabled) {
        this.id = requireText(id, "id");
        this.label = requireText(label, "label");
        this.iconResourceId = iconResourceId;
        this.iconDrawable = iconDrawable;
        this.enabled = enabled;
    }

    /** Use a drawable resource from the host Android application. */
    public static Bbos6ContextMenuItem resource(String id, String label,
            int iconResourceId) {
        if (iconResourceId == 0) {
            throw new IllegalArgumentException("iconResourceId must not be zero");
        }
        return new Bbos6ContextMenuItem(id, label, iconResourceId, null, true);
    }

    /** Use a caller-supplied Drawable, including an in-memory image. */
    public static Bbos6ContextMenuItem drawable(String id, String label,
            Drawable icon) {
        return new Bbos6ContextMenuItem(id, label, 0,
                Objects.requireNonNull(icon, "icon"), true);
    }

    /** An optional text-only action; the icon region is kept for alignment. */
    public static Bbos6ContextMenuItem text(String id, String label) {
        return new Bbos6ContextMenuItem(id, label, 0, null, true);
    }

    /** Disabled actions remain visible but cannot be selected or invoked. */
    public Bbos6ContextMenuItem withEnabled(boolean enabled) {
        return new Bbos6ContextMenuItem(id, label, iconResourceId,
                iconDrawable, enabled);
    }

    public String id() { return id; }
    public String label() { return label; }
    public int iconResourceId() { return iconResourceId; }
    public Drawable iconDrawable() { return iconDrawable; }
    public boolean enabled() { return enabled; }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
