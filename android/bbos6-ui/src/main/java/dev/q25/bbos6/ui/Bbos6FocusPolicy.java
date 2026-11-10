package dev.q25.bbos6.ui;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import java.util.Objects;

/**
 * Shared focus policy for BBOS6 composite views that draw their own
 * trackpad/keyboard selection. A container is still allowed to take focus,
 * but Android must not paint a second, full-container focus highlight.
 *
 * Apply after the composite view's child hierarchy is assembled. Only
 * ViewGroup containers are affected: native EditText, buttons, and other
 * leaf controls retain their ordinary Android focus behavior.
 */
public final class Bbos6FocusPolicy {
    private Bbos6FocusPolicy() {}

    public static void forManagedContainers(ViewGroup root) {
        Objects.requireNonNull(root, "root");
        // Android introduced the automatic default focus highlight in API 26.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        apply(root);
    }

    private static void apply(ViewGroup container) {
        container.setDefaultFocusHighlightEnabled(false);
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof ViewGroup) {
                apply((ViewGroup) child);
            }
        }
    }
}
