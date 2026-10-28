package dev.gcamredirect;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds installed photo apps without any hard-coded list. An app counts as a "gallery" if it
 * can view images AND registers for the camera "review" actions (the contract real galleries
 * implement so a camera can open its last shot). Other image viewers are listed separately.
 */
final class Galleries {
    static final String PHOTOS = "com.google.android.apps.photos";

    static final class App {
        final String pkg;
        final CharSequence label;
        final Drawable icon;
        final boolean gallery;

        App(String pkg, CharSequence label, Drawable icon, boolean gallery) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
            this.gallery = gallery;
        }
    }

    private Galleries() {}

    static List<App> list(Context c) {
        PackageManager pm = c.getPackageManager();
        Uri probe = Uri.parse("content://media/external/images/media/1");

        Set<String> review = new HashSet<>();
        for (String action : new String[] {"android.provider.action.REVIEW", "com.android.camera.action.REVIEW"}) {
            Intent r = new Intent(action).setDataAndType(probe, "image/jpeg");
            for (ResolveInfo ri : pm.queryIntentActivities(r, 0)) review.add(ri.activityInfo.packageName);
        }

        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(probe, "image/jpeg");
        Map<String, App> found = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(view, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || pkg.equals(PHOTOS) || pkg.equals("android")) continue;
            if (found.containsKey(pkg)) continue;
            found.put(pkg, new App(pkg, ri.loadLabel(pm), ri.loadIcon(pm), review.contains(pkg)));
        }

        List<App> out = new ArrayList<>(found.values());
        Collections.sort(out, new Comparator<App>() {
            @Override public int compare(App a, App b) {
                if (a.gallery != b.gallery) return a.gallery ? -1 : 1;
                return a.label.toString().compareToIgnoreCase(b.label.toString());
            }
        });
        return out;
    }

    /**
     * What the redirect should do right now: a package name, {@link Prefs#ASK}, or null
     * (nothing to redirect to, leave Google Photos alone).
     */
    static String effectiveTarget(Context c) {
        List<App> all = list(c);
        if (all.isEmpty()) return null;
        String pref = Prefs.getTarget(c);
        if (Prefs.ASK.equals(pref)) return Prefs.ASK;
        if (pref != null) {
            for (App a : all) {
                if (a.pkg.equals(pref)) {
                    // a gallery installed after the user chose: ask again instead of ignoring it
                    return newGalleries(c, all).isEmpty() ? pref : Prefs.ASK;
                }
            }
            // chosen gallery was uninstalled: fall through to automatic choice
        }
        List<App> galleries = new ArrayList<>();
        for (App a : all) if (a.gallery) galleries.add(a);
        if (galleries.size() == 1) return galleries.get(0).pkg;
        if (galleries.isEmpty() && all.size() == 1) return all.get(0).pkg;
        return Prefs.ASK;
    }

    /** Galleries installed since the user last made a choice (empty if they never chose). */
    static List<App> newGalleries(Context c, List<App> all) {
        List<App> out = new ArrayList<>();
        String pref = Prefs.getTarget(c);
        if (pref == null || Prefs.ASK.equals(pref)) return out;   // nothing to re-confirm
        Set<String> known = Prefs.knownGalleries(c);
        if (known == null) {   // choice made before this was tracked: treat current ones as known
            Prefs.saveKnownGalleries(c);
            return out;
        }
        for (App a : all) if (a.gallery && !known.contains(a.pkg)) out.add(a);
        return out;
    }

    static App find(List<App> apps, String pkg) {
        for (App a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }
}
