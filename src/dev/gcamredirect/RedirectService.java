package dev.gcamredirect;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Pixel Camera opens the last shot with an explicit intent pinned to Google Photos
 * (android.provider.action.REVIEW, pkg=com.google.android.apps.photos), so it can't be
 * re-routed by intent resolution. This service notices Photos' viewer appearing right
 * after the camera, hides the transition behind a black overlay, closes Photos and opens
 * the newest camera item in the chosen gallery.
 */
public class RedirectService extends AccessibilityService {
    private static final String TAG = "GCamRedirect";
    private static final String PHOTOS = Galleries.PHOTOS;
    private static final long OVERLAY_TIMEOUT_MS = 2500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastPkg = "";
    private long lastRedirectAt;

    // transition overlay
    private View overlay;
    private WindowManager overlayWm;
    private String awaitPkg;   // hide overlay once this package shows a window
    private String awaitCls;   // ...optionally of this class (suffix)
    private final Runnable hideRunnable = new Runnable() {
        @Override public void run() { hideOverlay(); }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = e.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        String cls = e.getClassName() == null ? "" : e.getClassName().toString();
        // Google Photos' viewer window appearing straight after a camera app = "show my last shot".
        // If the user has paused (suspended) Google Photos, Android shows its own "App isn't
        // available" dialog instead; treat that the same way.
        boolean photosViewer = pkg.equals(PHOTOS) && cls.contains("Activity");
        // (Android reports that dialog as package "android", class android.app.Dialog.)
        boolean pausedDialog = pkg.equals("android")
                && (cls.endsWith("SuspendedAppActivity") || cls.equals("android.app.Dialog"))
                && photosSuspended();
        if ((photosViewer || pausedDialog) && CameraApps.isCamera(this, lastPkg)) {
            lastPkg = PHOTOS;
            redirect();
            return;
        }
        if (overlay != null && awaitPkg != null && pkg.equals(awaitPkg)
                && (awaitCls == null || cls.endsWith(awaitCls))) {
            // destination is on screen: let it settle for a moment, then fade the overlay out
            handler.removeCallbacks(hideRunnable);
            handler.postDelayed(hideRunnable, 120);
        }
        if (isTransient(pkg) || pkg.equals(getPackageName())) return;
        lastPkg = pkg;
    }

    private boolean photosSuspended() {
        try {
            return (getPackageManager().getApplicationInfo(PHOTOS, 0).flags
                    & android.content.pm.ApplicationInfo.FLAG_SUSPENDED) != 0;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** System UI / keyboard windows pop up on top of the camera without leaving it. */
    private static boolean isTransient(String pkg) {
        return pkg.equals("com.android.systemui")
                || pkg.contains("inputmethod")
                || pkg.equals("android");
    }

    private void redirect() {
        long now = System.currentTimeMillis();
        if (now - lastRedirectAt < 1500) return;

        final String target = Galleries.effectiveTarget(this);
        if (target == null) {
            Log.w(TAG, "no gallery installed, leaving Google Photos open");
            return;
        }
        lastRedirectAt = now;
        final Redirector.Media media = Redirector.latestCameraMedia(this);

        showOverlay(Prefs.ASK.equals(target) ? getPackageName() : target,
                Prefs.ASK.equals(target) ? "PickerActivity" : null);
        performGlobalAction(GLOBAL_ACTION_BACK);   // close Photos (it sits in the camera's task)
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (Prefs.ASK.equals(target)) {
                    Intent i = new Intent(RedirectService.this, PickerActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (media != null) i.setDataAndType(media.uri, media.mime);
                    try {
                        startActivity(i);
                    } catch (RuntimeException ex) {
                        Log.e(TAG, "cannot show picker", ex);
                        hideOverlay();
                    }
                } else if (!Redirector.open(RedirectService.this, target, media)) {
                    hideOverlay();
                }
            }
        }, 40);
    }

    // ---- overlay -----------------------------------------------------------------------

    private void showOverlay(String waitForPkg, String waitForCls) {
        hideOverlayNow();
        try {
            // An accessibility service may add TYPE_ACCESSIBILITY_OVERLAY windows through its
            // own WindowManager (no SYSTEM_ALERT_WINDOW needed).
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            View v = new View(this);
            v.setBackgroundColor(Color.BLACK);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.OPAQUE);
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            wm.addView(v, lp);
            overlay = v;
            overlayWm = wm;
            awaitPkg = waitForPkg;
            awaitCls = waitForCls;
            handler.removeCallbacks(hideRunnable);
            handler.postDelayed(hideRunnable, OVERLAY_TIMEOUT_MS);   // safety net
        } catch (RuntimeException ex) {
            Log.w(TAG, "overlay unavailable, continuing without it", ex);
            overlay = null;
        }
    }

    private void hideOverlay() {
        final View v = overlay;
        final WindowManager wm = overlayWm;
        if (v == null) return;
        overlay = null;
        overlayWm = null;
        handler.removeCallbacks(hideRunnable);
        v.animate().alpha(0f).setDuration(140).withEndAction(new Runnable() {
            @Override public void run() { removeQuietly(wm, v); }
        }).start();
    }

    private void hideOverlayNow() {
        handler.removeCallbacks(hideRunnable);
        if (overlay != null) removeQuietly(overlayWm, overlay);
        overlay = null;
        overlayWm = null;
    }

    private static void removeQuietly(WindowManager wm, View v) {
        try {
            wm.removeViewImmediate(v);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "service connected, cameras: " + CameraApps.installedCameras(this)
                + ", target: " + Galleries.effectiveTarget(this));
    }

    @Override
    public boolean onUnbind(Intent intent) {
        hideOverlayNow();
        return super.onUnbind(intent);
    }

    @Override
    public void onInterrupt() {}
}
