package dev.q25.bb7pulldown;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.util.List;

import de.robv.android.xposed.XposedHelpers;

/**
 * Restricted SystemUI-only native task-removal action. The exact task is
 * captured from foreground activity or Quickstep's highlighted task; neither
 * path force-stops the application's package.
 */
final class ForegroundTaskActions {
    private static final String TAG = "BB7-AppMenu";
    private static final String REMOVE_TASKS = "android.permission.REMOVE_TASKS";

    static final class Target {
        final int taskId;
        final String packageName;
        final int userId;
        final boolean fromRecents;

        Target(int taskId, String packageName) {
            this(taskId, packageName, -1, false);
        }

        Target(int taskId, String packageName, int userId, boolean fromRecents) {
            this.taskId = taskId;
            this.packageName = packageName;
            this.userId = userId;
            this.fromRecents = fromRecents;
        }
    }

    private ForegroundTaskActions() {}

    /** Resolve the captured app's installed display label for the BB menu. */
    static String applicationLabel(Context context, Target target) {
        if (target == null) return "Application";
        try {
            PackageManager pm = context.getPackageManager();
            CharSequence label = pm.getApplicationLabel(
                    pm.getApplicationInfo(target.packageName, 0));
            if (label != null && label.length() > 0) return label.toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            // Keep the existing generic label when the app is unavailable.
        }
        return "Application";
    }

    /** Capture the actual top task BEFORE BB UI's own popup takes input focus. */
    static Target currentApplication(Context context) {
        if (context.checkSelfPermission(REMOVE_TASKS)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            if (manager == null) return null;
            List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty()) return null;
            ActivityManager.RunningTaskInfo task = tasks.get(0);
            if (task == null || task.topActivity == null || task.id <= 0) return null;
            String pkg = task.topActivity.getPackageName();
            if (pkg == null || "com.android.systemui".equals(pkg)
                    || "com.android.launcher3".equals(pkg)
                    || "com.google.android.apps.nexuslauncher".equals(pkg)) {
                return null;
            }

            // Do not offer Close Application on the HOME task, even when
            // a third-party launcher uses a package name unknown to BB UI.
            Intent base = task.baseIntent;
            if (base != null && base.hasCategory(Intent.CATEGORY_HOME)) return null;
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolved = context.getPackageManager().resolveActivity(home,
                    PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null && resolved.activityInfo != null
                    && pkg.equals(resolved.activityInfo.packageName)) return null;

            return new Target(task.id, pkg);
        } catch (Throwable error) {
            Log.w(TAG, "Cannot identify privileged foreground task; hiding Close", error);
            return null;
        }
    }

    /**
     * The two BB menu origins share ONE native task-removal action. Validate
     * the captured task against its origin first; never infer a different
     * target from the foreground application or Recents ordering.
     */
    static boolean closeCapturedApplication(Context context, Target target) {
        if (target == null || context.checkSelfPermission(REMOVE_TASKS)
                != PackageManager.PERMISSION_GRANTED) return false;
        if (target.fromRecents) {
            if (!stillInRecentTasks(context, target)) {
                Log.w(TAG, "Close rejected: highlighted Recents task is no longer present");
                return false;
            }
        } else {
            Target current = currentApplication(context);
            if (current == null || current.taskId != target.taskId
                    || !current.packageName.equals(target.packageName)) {
                Log.w(TAG, "Close rejected: original application no longer foreground");
                return false;
            }
        }
        try {
            Class<?> atm = Class.forName("android.app.ActivityTaskManager");
            Object service = XposedHelpers.callStaticMethod(atm, "getService");
            Object removed = XposedHelpers.callMethod(service, "removeTask", target.taskId);
            boolean ok = Boolean.TRUE.equals(removed);
            if (ok) Log.i(TAG, "Removed application task: " + target.packageName
                    + " taskId=" + target.taskId
                    + " source=" + (target.fromRecents ? "recents" : "foreground"));
            else Log.w(TAG, "System task manager rejected close: " + target.packageName);
            return ok;
        } catch (Throwable error) {
            Log.e(TAG, "Native task removal unavailable; no app force-stop attempted", error);
            return false;
        }
    }

    /** Check the precise Quickstep task ID, user and base package before removal. */
    private static boolean stillInRecentTasks(Context context, Target target) {
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            if (manager == null) return false;
            List<ActivityManager.RecentTaskInfo> recent = manager.getRecentTasks(100, 0);
            if (recent == null) return false;
            for (ActivityManager.RecentTaskInfo task : recent) {
                if (task == null || (task.id != target.taskId
                        && task.persistentId != target.taskId)) {
                    continue;
                }
                if (target.userId >= 0) {
                    // RecentTaskInfo.userId is hidden from SDK 35 compilation.
                    // Read the existing platform field; fail closed if absent.
                    int userId;
                    try {
                        userId = XposedHelpers.getIntField(task, "userId");
                    } catch (Throwable error) {
                        Log.w(TAG, "Cannot verify Recents task user; refusing removal", error);
                        return false;
                    }
                    if (userId != target.userId) continue;
                }
                Intent base = task.baseIntent;
                if (base == null) continue;
                ComponentName component = base.getComponent();
                String pkg = component != null
                        ? component.getPackageName() : base.getPackage();
                if (target.packageName.equals(pkg)) return true;
            }
        } catch (Throwable error) {
            Log.w(TAG, "Unable to validate highlighted Recents task", error);
        }
        return false;
    }
}
