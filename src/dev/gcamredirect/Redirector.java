package dev.gcamredirect;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

final class Redirector {
    private static final String TAG = "GCamRedirect";

    static final int ACCESS_FULL = 2;
    static final int ACCESS_LIMITED = 1;
    static final int ACCESS_NONE = 0;

    /** A media item to show. */
    static final class Media {
        final Uri uri;
        final String mime;

        Media(Uri uri, String mime) {
            this.uri = uri;
            this.mime = mime;
        }
    }

    private Redirector() {}

    static int accessLevel(Context c) {
        if (Build.VERSION.SDK_INT < 33) {   // Android 10-12 only have the single storage permission
            return c.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    ? ACCESS_FULL : ACCESS_NONE;
        }
        boolean img = c.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        boolean vid = c.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        if (img && vid) return ACCESS_FULL;
        boolean part = c.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
        return (img || vid || part) ? ACCESS_LIMITED : ACCESS_NONE;
    }

    /** Newest photo/video saved by the camera, or null when we may not (or cannot) look it up. */
    static Media latestCameraMedia(Context c) {
        if (accessLevel(c) != ACCESS_FULL) return null;
        Uri files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        String type = MediaStore.Files.FileColumns.MEDIA_TYPE;
        String typeSel = "(" + type + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                + " OR " + type + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
        Media m = query(c, files, typeSel + " AND " + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE 'DCIM/Camera%'");
        return m != null ? m : query(c, files, typeSel);
    }

    private static Media query(Context c, Uri files, String selection) {
        Bundle args = new Bundle();
        args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
        args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                MediaStore.MediaColumns.DATE_ADDED + " DESC, " + MediaStore.MediaColumns._ID + " DESC");
        args.putInt(ContentResolver.QUERY_ARG_LIMIT, 1);
        String[] proj = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.MIME_TYPE };
        try (Cursor cur = c.getContentResolver().query(files, proj, args, null)) {
            if (cur == null || !cur.moveToFirst()) return null;
            long id = cur.getLong(0);
            boolean video = cur.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
            String mime = cur.getString(2);
            Uri base = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            return new Media(ContentUris.withAppendedId(base, id), mime != null ? mime : (video ? "video/*" : "image/*"));
        } catch (RuntimeException e) {
            Log.e(TAG, "media lookup failed", e);
            return null;
        }
    }

    /**
     * Opens the item in the given gallery; with no item (no access) just opens the gallery.
     * The gallery gets a one-off read grant for the item, so it does not need its own
     * photo permission to show it.
     */
    static boolean open(Context c, String pkg, Media media) {
        if (media != null) {
            Intent i = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(media.uri, media.mime)
                    .setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                c.startActivity(i);
                Log.i(TAG, "opened " + media.uri + " in " + pkg);
                return true;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w(TAG, "gallery refused the item, opening it plainly", e);
            }
        }
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(launch);
            Log.i(TAG, "launched " + pkg);
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "cannot launch " + pkg, e);
            return false;
        }
    }
}
