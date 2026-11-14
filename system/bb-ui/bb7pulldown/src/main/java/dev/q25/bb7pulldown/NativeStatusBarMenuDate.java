package dev.q25.bb7pulldown;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Locale;

/**
 * Temporary date in the REAL SystemUI status bar while the BB-button menu
 * is visible. No duplicate status bar, notification observer, or icon backend.
 *
 * Only the native notification_icon_area is hidden; signal, battery, clock,
 * privacy, and the native notification views stay attached and operational.
 * The native TextClock handles date, time and timezone changes.
 */
final class NativeStatusBarMenuDate {
    private static final String TAG = "BB7-MenuDate";

    private static FrameLayout statusBar;
    private static View notificationIconArea;
    private static int visibilityToRestore;
    private static TextClock dateView;
    private static ViewTreeObserver trackedTree;
    private static ViewTreeObserver.OnPreDrawListener visibilityGuard;

    private NativeStatusBarMenuDate() {}

    /** Bind after the popup window has been successfully attached. */
    static void show(View nativeBar) {
        if (!(nativeBar instanceof FrameLayout) || !nativeBar.isAttachedToWindow()) {
            return;
        }
        final Context context = nativeBar.getContext();
        final int statusBarId = context.getResources().getIdentifier(
                "status_bar", "id", "com.android.systemui");
        final int notificationAreaId = context.getResources().getIdentifier(
                "notification_icon_area", "id", "com.android.systemui");
        if (statusBarId == 0 || nativeBar.getId() != statusBarId
                || notificationAreaId == 0) {
            Log.w(TAG, "Native Q25 status bar IDs unavailable; icons retained");
            return;
        }
        View area = nativeBar.findViewById(notificationAreaId);
        if (area == null) {
            Log.w(TAG, "Native notification icon area unavailable; icons retained");
            return;
        }
        FrameLayout bar = (FrameLayout) nativeBar;
        if (statusBar == bar && notificationIconArea == area && dateView != null
                && dateView.getParent() == bar) {
            suppressIcons();
            refreshTint();
            return;
        }

        hide(); // Also unbind the previous status-bar root after reinflation.
        try {
            TextClock date = new TextClock(context);
            Locale locale = context.getResources().getConfiguration().getLocales().get(0);
            String pattern = DateFormat.getBestDateTimePattern(locale, "yMMMd");
            date.setFormat12Hour(pattern);
            date.setFormat24Hour(pattern);
            date.setSingleLine(true);
            date.setEllipsize(TextUtils.TruncateAt.END);
            date.setIncludeFontPadding(false);
            date.setGravity(Gravity.CENTER);
            date.setFocusable(false);
            date.setClickable(false);
            date.setTextSize(14); // Native Q25 status clock fallback, sp.
            date.setTextColor(Color.WHITE);

            statusBar = bar;
            notificationIconArea = area;
            visibilityToRestore = area.getVisibility();
            dateView = date;

            bar.addView(date, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL));
            ViewTreeObserver tree = bar.getViewTreeObserver();
            if (tree.isAlive()) {
                visibilityGuard = () -> {
                    // Native SystemUI can redisplay notification icons as new
                    // notifications arrive. Only suppress their presentation.
                    suppressIcons();
                    refreshTint();
                    return true;
                };
                tree.addOnPreDrawListener(visibilityGuard);
                trackedTree = tree;
            }
            suppressIcons();
            refreshTint();
            NativeStatusBarHubIcons.setMenuDateVisible(true);
            Log.i(TAG, "BB menu date displayed in native status bar");
        } catch (RuntimeException error) {
            hide();
            Log.w(TAG, "Cannot show native status-bar date; icons restored", error);
        }
    }

    /** Restore the native icon area on every popup exit path. */
    static void hide() {
        if (trackedTree != null && visibilityGuard != null && trackedTree.isAlive()) {
            trackedTree.removeOnPreDrawListener(visibilityGuard);
        }
        trackedTree = null;
        visibilityGuard = null;
        if (notificationIconArea != null
                && notificationIconArea.getVisibility() == View.INVISIBLE) {
            notificationIconArea.setVisibility(visibilityToRestore);
        }
        if (dateView != null && dateView.getParent() instanceof ViewGroup) {
            ((ViewGroup) dateView.getParent()).removeView(dateView);
        }
        statusBar = null;
        notificationIconArea = null;
        dateView = null;
        NativeStatusBarHubIcons.setMenuDateVisible(false);
    }

    private static void suppressIcons() {
        if (notificationIconArea != null
                && notificationIconArea.getVisibility() == View.VISIBLE) {
            visibilityToRestore = View.VISIBLE;
            notificationIconArea.setVisibility(View.INVISIBLE);
        }
    }

    private static void refreshTint() {
        FrameLayout bar = statusBar;
        TextClock date = dateView;
        if (bar == null || date == null) return;
        int clockId = bar.getResources().getIdentifier(
                "clock", "id", "com.android.systemui");
        View clock = clockId == 0 ? null : bar.findViewById(clockId);
        if (clock instanceof TextView) {
            TextView nativeClock = (TextView) clock;
            if (date.getCurrentTextColor() != nativeClock.getCurrentTextColor()) {
                date.setTextColor(nativeClock.getCurrentTextColor());
            }
            if (date.getTextSize() != nativeClock.getTextSize()) {
                date.setTextSize(TypedValue.COMPLEX_UNIT_PX, nativeClock.getTextSize());
            }
            // A setter on every pre-draw can trigger repeated layout passes.
            if (date.getTypeface() != nativeClock.getTypeface()) {
                date.setTypeface(nativeClock.getTypeface());
            }
        }
    }
}
