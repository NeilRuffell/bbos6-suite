package dev.q25.bb7pulldown;

import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * Ephemeral Quickstep -> BB menu selection, using BB UI's existing provider.
 * Quickstep publishes its actual highlighted task; only SystemUI can read it.
 * No persistent task cache, substitute app navigation or duplicated task model.
 */
public final class RecentsSelectionBridge {
    private static final String TAG = "BB7-RecentsMenu";
    private static final IBinder QUICKSTEP_PROCESS = new Binder();

    private RecentsSelectionBridge() {}

    static final class Snapshot {
        final boolean overviewActive;
        final ForegroundTaskActions.Target target;

        Snapshot(boolean overviewActive, ForegroundTaskActions.Target target) {
            this.overviewActive = overviewActive;
            this.target = target;
        }
    }

    /** Called from the native Quickstep RecentsView's existing selection flow. */
    public static boolean publish(Context context, int taskId, int userId,
            String packageName) {
        return send(context, true, taskId, userId, packageName);
    }

    /** The grid is still open, but no unambiguous task is selected. */
    public static boolean clear(Context context) {
        return send(context, true, -1, -1, null);
    }

    /** Native Quickstep's Overview state has actually exited. */
    public static boolean leaveOverview(Context context) {
        return send(context, false, -1, -1, null);
    }

    private static boolean send(Context context, boolean overviewActive,
            int taskId, int userId, String packageName) {
        Bundle data = new Bundle();
        data.putInt(FavoritesSettingsProvider.RECENTS_TASK_ID, taskId);
        data.putInt(FavoritesSettingsProvider.RECENTS_USER_ID, userId);
        data.putString(FavoritesSettingsProvider.RECENTS_PACKAGE, packageName);
        data.putBoolean(FavoritesSettingsProvider.RECENTS_OVERVIEW_ACTIVE, overviewActive);
        data.putBinder(FavoritesSettingsProvider.RECENTS_PUBLISHER_TOKEN, QUICKSTEP_PROCESS);
        try {
            return context.getContentResolver().call(
                    FavoritesSettingsProvider.RECENTS_URI,
                    FavoritesSettingsProvider.METHOD_PUBLISH_RECENTS_SELECTION,
                    null, data) != null;
        } catch (RuntimeException error) {
            Log.w(TAG, "Quickstep selection publication unavailable", error);
            return false;
        }
    }

    /** Returns native Overview visibility alongside its exact selection. */
    static Snapshot snapshot(Context context) {
        try {
            Bundle data = context.getContentResolver().call(
                    FavoritesSettingsProvider.RECENTS_URI,
                    FavoritesSettingsProvider.METHOD_GET_RECENTS_SELECTION,
                    null, null);
            if (data == null) return new Snapshot(false, null);
            boolean active = data.getBoolean(
                    FavoritesSettingsProvider.RECENTS_OVERVIEW_ACTIVE, false);
            if (!active) return new Snapshot(false, null);
            int id = data.getInt(FavoritesSettingsProvider.RECENTS_TASK_ID, -1);
            int user = data.getInt(FavoritesSettingsProvider.RECENTS_USER_ID, -1);
            String pkg = data.getString(FavoritesSettingsProvider.RECENTS_PACKAGE);
            ForegroundTaskActions.Target target = id > 0 && pkg != null && !pkg.isEmpty()
                    ? new ForegroundTaskActions.Target(id, pkg, user, true) : null;
            return new Snapshot(true, target);
        } catch (RuntimeException error) {
            Log.w(TAG, "Highlighted Recents task unavailable; hiding Close", error);
            return new Snapshot(false, null);
        }
    }
}
