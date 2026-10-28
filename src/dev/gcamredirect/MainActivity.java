package dev.gcamredirect;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_MEDIA = 1;

    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);   // targetSdk 35 is edge-to-edge: stay below the bars
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        // First launch: ask for photo access right away so nobody has to hunt for it.
        if (Redirector.accessLevel(this) == Redirector.ACCESS_NONE && !Prefs.permissionAsked(this)) {
            showPhotoRationale();
        }
    }

    /** Explains *why* before Android's own permission dialog appears. */
    private void showPhotoRationale() {
        Prefs.setPermissionAsked(this);   // only auto-show once; the button in the list stays available
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.rationale_title)
                .setMessage(R.string.rationale_body)
                .setPositiveButton(android.R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) { requestMedia(); }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        render();
    }

    // ---- state -------------------------------------------------------------------------

    private boolean serviceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String me = new ComponentName(this, RedirectService.class).flattenToString();
        return enabled != null && enabled.contains(me);
    }

    private void requestMedia() {
        Prefs.setPermissionRequested(this);
        String[] perms;
        if (Build.VERSION.SDK_INT < 33) {
            perms = new String[] {Manifest.permission.READ_EXTERNAL_STORAGE};
        } else if (Build.VERSION.SDK_INT == 33) {
            perms = new String[] {Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO};
        } else {
            perms = new String[] {Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
        }
        requestPermissions(perms, REQ_MEDIA);
    }

    private void openAppInfo() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private void openServiceSettings() {
        // (Android's per-service details page needs a system-only permission, so open the list.)
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void onAccessClicked(int level) {
        if (level == Redirector.ACCESS_NONE) {
            String primary = Build.VERSION.SDK_INT < 33
                    ? Manifest.permission.READ_EXTERNAL_STORAGE : Manifest.permission.READ_MEDIA_IMAGES;
            // Android shows no dialog once a permission was refused twice; then only Settings can help.
            if (!Prefs.permissionRequested(this) || shouldShowRequestPermissionRationale(primary)) requestMedia();
            else openAppInfo();
        } else {
            openAppInfo();   // "Allow all" lives in the app's permission page
        }
    }

    // ---- UI ----------------------------------------------------------------------------

    private void render() {
        final boolean night = Ui.night(this);
        // Dark status/navigation bar icons on the light theme (otherwise they vanish on a light background).
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (android.os.Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        getWindow().getDecorView().setSystemUiVisibility(night ? flags & ~light : flags | light);
        final int pad = Ui.dp(this, 16);
        final int level = Redirector.accessLevel(this);
        final boolean svc = serviceEnabled();
        List<Galleries.App> apps = Galleries.list(this);
        final String target = Galleries.effectiveTarget(this);
        final boolean ready = target != null && svc;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad * 2);

        // header: logo + name + tagline
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.icon(this, getApplicationInfo().loadIcon(getPackageManager()), 64));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(pad, 0, 0, 0);
        titles.addView(Ui.text(this, getString(R.string.app_name), 22, true, 0));
        TextView tag = Ui.text(this, getString(R.string.tagline), 14, false, 0);
        tag.setAlpha(0.7f);
        titles.addView(tag);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header);

        // status card
        int cardColor = ready ? (night ? 0xFF1E3A29 : 0xFFE3F2E6) : (night ? 0xFF4A3512 : 0xFFFFF3E0);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(Ui.rounded(this, cardColor, 20));
        card.addView(Ui.text(this, (ready ? "✓  " : "!  ") + getString(ready ? R.string.status_ready : R.string.status_setup), 20, true, 0));
        String body;
        if (ready) {
            Galleries.App a = Prefs.ASK.equals(target) ? null : Galleries.find(apps, target);
            body = a != null ? getString(R.string.status_ready_body, a.label) : getString(R.string.status_ready_ask);
        } else {
            body = getString(R.string.status_setup_body);
        }
        TextView bodyView = Ui.text(this, body, 15, false, 0);
        bodyView.setPadding(0, Ui.dp(this, 4), 0, 0);
        card.addView(bodyView);
        if (!ready && target != null) {
            Button next = new Button(this);
            next.setText(R.string.btn_continue);
            next.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (level == Redirector.ACCESS_NONE) onAccessClicked(level);
                    else openServiceSettings();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(this, 12);
            card.addView(next, lp);
        }
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.topMargin = pad;
        root.addView(card, cardLp);

        // checklist
        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        steps.setPadding(0, pad, 0, 0);

        String galleryState;
        if (target == null) galleryState = getString(R.string.gallery_none);
        else if (Prefs.ASK.equals(target)) galleryState = getString(R.string.gallery_ask);
        else {
            Galleries.App a = Galleries.find(apps, target);
            galleryState = a != null ? a.label.toString() : target;
        }
        steps.addView(Ui.row(this, mark(target != null ? "✓" : "✗", target != null ? 0xFF2E7D32 : 0xFFC62828),
                getString(R.string.step_gallery), galleryState, null, null));

        int accessColor = level == Redirector.ACCESS_FULL ? 0xFF2E7D32 : level == Redirector.ACCESS_LIMITED ? 0xFFEF6C00 : 0xFFC62828;
        String accessMark = level == Redirector.ACCESS_FULL ? "✓" : level == Redirector.ACCESS_LIMITED ? "!" : "✗";
        String accessState = getString(level == Redirector.ACCESS_FULL ? R.string.access_full
                : level == Redirector.ACCESS_LIMITED ? R.string.access_limited : R.string.access_none);
        Button accessBtn = null;
        if (level != Redirector.ACCESS_FULL) {
            accessBtn = new Button(this);
            accessBtn.setText(level == Redirector.ACCESS_LIMITED ? R.string.btn_allow_all : R.string.btn_allow);
            accessBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onAccessClicked(level); }
            });
        }
        steps.addView(Ui.row(this, mark(accessMark, accessColor), getString(R.string.step_photos), accessState,
                getString(R.string.why_photos), accessBtn, null));

        Button svcBtn = null;
        if (!svc) {
            svcBtn = new Button(this);
            svcBtn.setText(R.string.btn_turn_on);
            svcBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openServiceSettings(); }
            });
        }
        steps.addView(Ui.row(this, mark(svc ? "✓" : "✗", svc ? 0xFF2E7D32 : 0xFFC62828),
                getString(R.string.step_service), getString(svc ? R.string.service_on : R.string.service_off),
                getString(R.string.why_service), svcBtn, null));
        root.addView(steps);

        // gallery choice
        if (!apps.isEmpty()) {
            root.addView(section(getString(R.string.section_galleries)));
            List<Galleries.App> fresh = Galleries.newGalleries(this, apps);
            if (!fresh.isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (Galleries.App a : fresh) {
                    if (names.length() > 0) names.append(", ");
                    names.append(a.label);
                }
                TextView note = Ui.text(this, getString(R.string.new_gallery_note, names), 14, false, 0xFFEF6C00);
                note.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 6));
                root.addView(note);
            }
            root.addView(choiceRow(askIcon(), getString(R.string.gallery_ask), getString(R.string.ask_desc),
                    Prefs.ASK, Prefs.ASK.equals(target)));
            boolean otherHeader = false;
            for (Galleries.App a : apps) {
                if (!a.gallery && !otherHeader) {
                    root.addView(section(getString(R.string.section_other)));
                    otherHeader = true;
                }
                root.addView(choiceRow(Ui.icon(this, a.icon, 40), a.label.toString(), null, a.pkg, a.pkg.equals(target)));
            }
        }

        // help for sideloaded installs, where Android greys the accessibility switch out
        if (!svc) {
            root.addView(section(getString(R.string.hint_title)));
            TextView hint = Ui.text(this, getString(R.string.hint_body), 14, false, 0);
            hint.setAlpha(0.8f);
            hint.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
            root.addView(hint);
            Button info = new Button(this);
            info.setText(R.string.btn_app_info);
            info.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openAppInfo(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = Ui.dp(this, 4);
            root.addView(info, lp);
        }

        scroll.removeAllViews();
        scroll.addView(root);
    }

    private TextView section(String s) {
        TextView t = Ui.text(this, s.toUpperCase(), 12, true, 0);
        t.setAlpha(0.6f);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 22), 0, Ui.dp(this, 4));
        return t;
    }

    private TextView mark(String glyph, int color) {
        TextView t = Ui.text(this, glyph, 20, true, color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView askIcon() {
        TextView t = Ui.text(this, "?", 20, true, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rounded(this, 0xFF3367D6, 20));
        return t;
    }

    private View choiceRow(View icon, String title, String sub, final String value, boolean selected) {
        RadioButton rb = new RadioButton(this);
        rb.setChecked(selected);
        rb.setClickable(false);
        rb.setFocusable(false);
        return Ui.row(this, icon, title, sub, rb, new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.choose(MainActivity.this, value);
                render();
            }
        });
    }
}
