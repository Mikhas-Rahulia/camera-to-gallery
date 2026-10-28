package dev.gcamredirect;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** "Open photo with…" sheet shown when several galleries are installed and none is preferred. */
public class PickerActivity extends Activity {
    private CheckBox always;
    private Redirector.Media media;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        media = in.getData() != null ? new Redirector.Media(in.getData(), in.getType()) : null;

        int pad = Ui.dp(this, 20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, Ui.dp(this, 12));

        TextView title = Ui.text(this, getString(R.string.picker_title), 20, true, 0);
        title.setPadding(Ui.dp(this, 12), 0, 0, Ui.dp(this, 8));
        root.addView(title);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        List<Galleries.App> apps = Galleries.list(this);
        for (final Galleries.App a : apps) {
            list.addView(Ui.row(this, Ui.icon(this, a.icon, 40), a.label, null, null,
                    new View.OnClickListener() {
                        @Override public void onClick(View v) { choose(a.pkg); }
                    }));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        always = new CheckBox(this);
        always.setText(R.string.picker_always);
        always.setPadding(Ui.dp(this, 8), 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 8);
        root.addView(always, lp);

        setContentView(root);
        setFinishOnTouchOutside(true);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.min(Ui.dp(this, 360), dm.widthPixels - Ui.dp(this, 32));
        getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void choose(String pkg) {
        if (always.isChecked()) Prefs.choose(this, pkg);
        Redirector.open(this, pkg, media);
        finish();
    }
}
