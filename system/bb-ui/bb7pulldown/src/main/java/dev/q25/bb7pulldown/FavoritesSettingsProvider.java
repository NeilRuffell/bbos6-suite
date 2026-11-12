package dev.q25.bb7pulldown;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;

import org.json.JSONArray;

/**
 * Existing BB UI interprocess bridge: SystemUI-only Favorites configuration,
 * and a volatile Quickstep selection snapshot for the BB application menu.
 * Quickstep may update ONLY the selection; it cannot read or change Favorites.
 */
public final class FavoritesSettingsProvider extends ContentProvider {
    static final String AUTHORITY = "dev.q25.trackpad.favorites";
    static final Uri RULES_URI = Uri.parse("content://" + AUTHORITY + "/rules");
    static final Uri CONVERSATIONS_URI = Uri.parse("content://" + AUTHORITY + "/conversations");
    static final Uri MODE_URI = Uri.parse("content://" + AUTHORITY + "/shadeMode");
    static final Uri RECENTS_URI = Uri.parse("content://" + AUTHORITY + "/recentsSelection");
    static final String METHOD_REPORT_CONVERSATIONS = "reportConversations";
    static final String METHOD_PUBLISH_RECENTS_SELECTION = "publishRecentsSelection";
    static final String METHOD_GET_RECENTS_SELECTION = "getRecentsSelection";
    static final String RECENTS_TASK_ID = "taskId";
    static final String RECENTS_USER_ID = "userId";
    static final String RECENTS_PACKAGE = "packageName";
    static final String RECENTS_OVERVIEW_ACTIVE = "overviewActive";
    static final String RECENTS_PUBLISHER_TOKEN = "publisherToken";
    private static final String PREFS = "bbui_favorites";
    private static final String RULES_KEY = "rules";
    private static final String MODE_KEY = "shade_mode";
    private static final String OBSERVED_KEY = "observed";
    private static final String DEFAULT_RULES =
            "{\"version\":1,\"enabled\":false,\"apps\":[]}";
    // In-memory only: never persist a task selection past a provider restart.
    private final Object recentsLock = new Object();
    private int selectedRecentTaskId = -1;
    private int selectedRecentUserId = -1;
    private String selectedRecentPackage = "";
    private boolean overviewActive;
    // Remote Binder proxy becomes dead if the Launcher3 process exits.
    private IBinder selectionPublisher;

    @Override public boolean onCreate() { return true; }

    static FavoritesRules loadLocal(Context context) {
        return FavoritesJson.parse(prefs(context).getString(RULES_KEY, DEFAULT_RULES));
    }

    static String loadModeLocal(Context context) {
        return NotificationShadeModePolicy.normalized(
                prefs(context).getString(MODE_KEY, NotificationShadeModePolicy.APPS));
    }

    static void saveModeLocal(Context context, String mode) {
        prefs(context).edit()
                .putString(MODE_KEY, NotificationShadeModePolicy.normalized(mode))
                .apply();
        // Reuse the existing live SystemUI observer, including while QS is visible.
        context.getContentResolver().notifyChange(RULES_URI, null);
        context.getContentResolver().notifyChange(MODE_URI, null);
    }

    static void saveLocal(Context context, FavoritesRules favorites) {
        // Drop previously observed conversation labels once their app is removed.
        JSONArray observed = observedLocal(context);
        JSONArray retained = new JSONArray();
        for (int i = 0; i < observed.length(); i++) {
            org.json.JSONObject candidate = observed.optJSONObject(i);
            if (candidate != null
                    && favorites.find(candidate.optString("package", "")) != null) {
                retained.put(candidate);
            }
        }
        prefs(context).edit()
                .putString(RULES_KEY, FavoritesJson.serialize(favorites))
                .putString(OBSERVED_KEY, retained.toString())
                .apply();
        context.getContentResolver().notifyChange(RULES_URI, null);
    }

    static JSONArray observedLocal(Context context) {
        String json = prefs(context).getString(OBSERVED_KEY, "[]");
        try {
            return new JSONArray(json == null ? "[]" : json);
        } catch (org.json.JSONException error) {
            return new JSONArray();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void enforceCaller() {
        Context context = getContext();
        if (context == null) throw new SecurityException("Favorites provider unavailable");
        int callingUid = Binder.getCallingUid();
        if (callingUid == Process.myUid()) return;
        try {
            int systemUiUid = context.getPackageManager().getPackageUid(
                    "com.android.systemui", 0);
            if (callingUid == systemUiUid) return;
        } catch (PackageManager.NameNotFoundException ignored) { }
        throw new SecurityException("Favorites configuration is private to BB UI and SystemUI");
    }

    private void enforceLauncherCaller() {
        Context context = getContext();
        if (context == null) throw new SecurityException("BB UI provider unavailable");
        try {
            int launcherUid = context.getPackageManager().getPackageUid(
                    "com.android.launcher3", 0);
            if (Binder.getCallingUid() == launcherUid) return;
        } catch (PackageManager.NameNotFoundException ignored) { }
        throw new SecurityException("Only Quickstep may publish a Recents selection");
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        enforceCaller();
        Context context = getContext();
        if (context == null) return null;
        String value;
        if (RULES_URI.equals(uri)) {
            value = prefs(context).getString(RULES_KEY, DEFAULT_RULES);
        } else if (CONVERSATIONS_URI.equals(uri)) {
            value = prefs(context).getString(OBSERVED_KEY, "[]");
        } else if (MODE_URI.equals(uri)) {
            value = loadModeLocal(context);
        } else {
            throw new IllegalArgumentException("Unknown Favorites URI");
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{"json"});
        cursor.addRow(new Object[]{value});
        return cursor;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_PUBLISH_RECENTS_SELECTION.equals(method)) {
            enforceLauncherCaller();
            int id = extras == null ? -1 : extras.getInt(RECENTS_TASK_ID, -1);
            int user = extras == null ? -1 : extras.getInt(RECENTS_USER_ID, -1);
            String pkg = extras == null ? null : extras.getString(RECENTS_PACKAGE);
            boolean active = extras != null
                    && extras.getBoolean(RECENTS_OVERVIEW_ACTIVE, false);
            IBinder publisher = extras == null
                    ? null : extras.getBinder(RECENTS_PUBLISHER_TOKEN);
            if (publisher == null || !publisher.isBinderAlive()) {
                throw new SecurityException("Live Launcher3 selection publisher required");
            }
            synchronized (recentsLock) {
                selectionPublisher = publisher;
                overviewActive = active;
                selectedRecentTaskId = active && id > 0 && pkg != null && !pkg.isEmpty()
                        ? id : -1;
                selectedRecentUserId = selectedRecentTaskId > 0 ? user : -1;
                selectedRecentPackage = selectedRecentTaskId > 0 ? pkg : "";
            }
            return Bundle.EMPTY;
        }
        // The Launcher3 UID is deliberately NOT allowed to read this result,
        // and never passes the Favorites configuration authorization above.
        enforceCaller();
        if (METHOD_GET_RECENTS_SELECTION.equals(method)) {
            Bundle result = new Bundle();
            synchronized (recentsLock) {
                boolean valid = selectionPublisher != null
                        && selectionPublisher.isBinderAlive();
                result.putBoolean(RECENTS_OVERVIEW_ACTIVE, valid && overviewActive);
                result.putInt(RECENTS_TASK_ID, valid ? selectedRecentTaskId : -1);
                result.putInt(RECENTS_USER_ID, valid ? selectedRecentUserId : -1);
                result.putString(RECENTS_PACKAGE, valid ? selectedRecentPackage : "");
            }
            return result;
        }
        Context context = getContext();
        if (context == null || !METHOD_REPORT_CONVERSATIONS.equals(method)) {
            return super.call(method, arg, extras);
        }
        // SystemUI reports only currently active conversation IDs, not message bodies.
        String json = extras == null ? null : extras.getString("json");
        if (json == null || json.length() > 32_768) return Bundle.EMPTY;
        try {
            new JSONArray(json); // Reject invalid input.
            prefs(context).edit().putString(OBSERVED_KEY, json).apply();
            context.getContentResolver().notifyChange(CONVERSATIONS_URI, null);
        } catch (org.json.JSONException ignored) { }
        return Bundle.EMPTY;
    }

    @Override public String getType(Uri uri) {
        enforceCaller();
        return "vnd.android.cursor.item/vnd.bbui.favorites";
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new SecurityException("Favorites settings are app-owned");
    }

    @Override public int update(Uri uri, ContentValues values,
            String selection, String[] selectionArgs) {
        throw new SecurityException("Favorites settings are app-owned");
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new SecurityException("Favorites settings are app-owned");
    }
}
