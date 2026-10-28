package dev.gcamredirect;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tiny helpers so the screens can be built in code. */
final class Ui {
    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static boolean night(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static TextView text(Context c, CharSequence s, float sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        // Explicit, high-contrast defaults: the system theme's text colour can be quite pale.
        t.setTextColor(color != 0 ? color : (night(c) ? 0xFFEEEEEE : 0xFF1B1B1F));
        return t;
    }

    static ImageView icon(Context c, Drawable d, float sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(d);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    static void selectable(Context c, View v) {
        TypedArray a = c.obtainStyledAttributes(new int[] {android.R.attr.selectableItemBackground});
        v.setBackground(a.getDrawable(0));
        a.recycle();
    }

    static LinearLayout row(Context c, View leading, CharSequence title, CharSequence sub,
                            View trailing, View.OnClickListener click) {
        return row(c, leading, title, sub, null, trailing, click);
    }

    /**
     * A list row: [leading] title / subtitle / small explanatory note [trailing].
     * Any of leading/sub/note/trailing may be null.
     */
    static LinearLayout row(Context c, View leading, CharSequence title, CharSequence sub,
                            CharSequence note, View trailing, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        if (leading != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40));
            lp.rightMargin = dp(c, 14);
            row.addView(leading, lp);
        }
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(c, title, 16, false, 0));
        if (sub != null) {
            TextView s = text(c, sub, 13, false, 0);
            s.setAlpha(0.7f);
            col.addView(s);
        }
        if (note != null) {
            TextView n = text(c, note, 12, false, 0);
            n.setAlpha(0.55f);
            n.setPadding(0, dp(c, 3), 0, 0);
            col.addView(n);
        }
        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (trailing != null) row.addView(trailing);
        if (click != null) {
            row.setOnClickListener(click);
            selectable(c, row);
        }
        return row;
    }
}
