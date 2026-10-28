package dev.gcamredirect;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

final class Prefs {
    /** Special value for "ask which gallery every time". */
    static final String ASK = "*ask*";

    private static final String FILE = "prefs";
    private static final String KEY_TARGET = "target_pkg";
    private static final String KEY_KNOWN = "known_galleries";
    private static final String KEY_PERM_ASKED = "perm_asked";
    private static final String KEY_PERM_REQUESTED = "perm_requested";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Explicit user choice: a package name, {@link #ASK}, or null when never chosen. */
    static String getTarget(Context c) {
        return sp(c).getString(KEY_TARGET, null);
    }

    /**
     * Remembers the choice together with the galleries that existed when it was made, so a
     * gallery installed later can be noticed and the user asked again.
     */
    static void choose(Context c, String pkgOrAsk) {
        sp(c).edit().putString(KEY_TARGET, pkgOrAsk).apply();
        saveKnownGalleries(c);
    }

    static void saveKnownGalleries(Context c) {
        Set<String> known = new HashSet<>();
        for (Galleries.App a : Galleries.list(c)) if (a.gallery) known.add(a.pkg);
        sp(c).edit().putStringSet(KEY_KNOWN, known).apply();
    }

    /** Galleries that existed when the user last chose, or null if never recorded. */
    static Set<String> knownGalleries(Context c) {
        Set<String> s = sp(c).getStringSet(KEY_KNOWN, null);
        return s == null ? null : new HashSet<>(s);
    }

    static boolean permissionRequested(Context c) {
        return sp(c).getBoolean(KEY_PERM_REQUESTED, false);
    }

    static void setPermissionRequested(Context c) {
        sp(c).edit().putBoolean(KEY_PERM_REQUESTED, true).apply();
    }

    static boolean permissionAsked(Context c) {
        return sp(c).getBoolean(KEY_PERM_ASKED, false);
    }

    static void setPermissionAsked(Context c) {
        sp(c).edit().putBoolean(KEY_PERM_ASKED, true).apply();
    }
}
