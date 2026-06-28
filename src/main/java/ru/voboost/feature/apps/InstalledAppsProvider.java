package ru.voboost.feature.apps;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

/**
 * Provides a list of user-installed (non-system) applications.
 * Works on Android 9 (API 28) and above.
 *
 * On Android 11+ the host app must declare either:
 * - {@code <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>}
 * - or a {@code <queries>} block in its AndroidManifest.xml
 * to see other installed packages.
 */
public final class InstalledAppsProvider {

    /**
     * Lightweight descriptor for a user-installed application.
     */
    public static final class AppInfo {
        private final String packageName;
        private final String label;
        private final Drawable icon;

        public AppInfo(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }

        public String getPackageName() {
            return packageName;
        }

        public String getLabel() {
            return label;
        }

        public Drawable getIcon() {
            return icon;
        }
    }

    private InstalledAppsProvider() {}

    /**
     * Returns all user-installed applications (excluding system and
     * updated-system apps).
     *
     * @param context any context
     * @return list of {@link AppInfo}, never null
     */
    public static List<AppInfo> getUserInstalledApps(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> allApps = pm.getInstalledApplications(0);
        List<AppInfo> result = new ArrayList<>();

        for (ApplicationInfo app : allApps) {
            if (isUserInstalled(app)) {
                String label = pm.getApplicationLabel(app).toString();
                Drawable icon = pm.getApplicationIcon(app);
                result.add(new AppInfo(app.packageName, label, icon));
            }
        }

        return result;
    }

    /**
     * Returns true if the application was installed by the user
     * (not pre-loaded in /system).
     */
    private static boolean isUserInstalled(ApplicationInfo appInfo) {
        // FLAG_SYSTEM — pre-installed in /system partition
        // FLAG_UPDATED_SYSTEM_APP — system app that was later updated by user
        return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                && (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
    }
}
