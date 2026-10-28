package dev.gcamredirect;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.Locale;

/**
 * Recognises camera apps without a fixed package list, so Google/Pixel Camera, Google Camera
 * "ports" (GCam mods have all sorts of package names) and other camera apps all work.
 */
final class CameraApps {
    private CameraApps() {}

    /** Installed camera apps as seen by {@link #isCamera}; used for diagnostics. */
    static java.util.List<String> installedCameras(Context c) {
        java.util.Set<String> out = new java.util.TreeSet<>();
        PackageManager pm = c.getPackageManager();
        for (String action : new String[] {"android.media.action.STILL_IMAGE_CAMERA", "android.media.action.IMAGE_CAPTURE"}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(action), 0)) {
                if (isCamera(c, ri.activityInfo.packageName)) out.add(ri.activityInfo.packageName);
            }
        }
        return new java.util.ArrayList<>(out);
    }

    static boolean isCamera(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(c.getPackageName()) || pkg.equals(Galleries.PHOTOS)) {
            return false;
        }
        String p = pkg.toLowerCase(Locale.ROOT);
        // Pixel Camera and the usual GCam port naming schemes
        if (p.contains("googlecamera") || p.contains("gcam") || p.startsWith("com.agc.")
                || p.startsWith("com.google.android.apps.camera")) {
            return true;
        }
        // anything that registers itself as a camera
        PackageManager pm = c.getPackageManager();
        String[] actions = {"android.media.action.STILL_IMAGE_CAMERA", "android.media.action.IMAGE_CAPTURE"};
        for (String action : actions) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(action), 0)) {
                if (ri.activityInfo.packageName.equals(pkg)) return true;
            }
        }
        return false;
    }
}
