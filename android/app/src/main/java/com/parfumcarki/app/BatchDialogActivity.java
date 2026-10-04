package com.parfumcarki.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/** Opened by the widget and by the quick-settings tile. Works offline and wears the colours of the app's current theme. */
public class BatchDialogActivity extends Activity {
    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static int col(JSONObject o, String k, int def) {
        try {
            return Color.parseColor(o.optString(k));
        } catch (Exception e) {
            return def;
        }
    }

    private GradientDrawable box(int fill, int stroke, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(r));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        JSONObject o;
        try {
            o = new JSONObject(getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE).getString(BatchWidget.KEY, "{}"));
        } catch (Exception e) {
            o = new JSONObject();
        }
        final int bg = col(o, "bg", 0xFF211812), fg = col(o, "fg", 0xFFF8EDE3), muted = col(o, "muted", 0xFFB49D8B);
        final int accent = col(o, "accent", 0xFFF26D21), edge = col(o, "edge", 0xFFC98A4B), ink = col(o, "ink", 0xFF2A1407), field = col(o, "bg2", 0xFF17110D);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(16));
        root.setBackground(box(bg, edge, 24));

        TextView title = new TextView(this);
        title.setText("BATCH KONTROL");
        title.setTextColor(edge);
        title.setTextSize(11);
        title.setLetterSpacing(0.18f);
        root.addView(title);

        TextView head = new TextView(this);
        head.setText("Parti kodunu yaz");
        head.setTextColor(fg);
        head.setTextSize(20);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setPadding(0, dp(4), 0, dp(10));
        root.addView(head);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final EditText in = new EditText(this);
        in.setHint("ör. 2K01");
        in.setHintTextColor(muted);
        in.setTextColor(fg);
        in.setTextSize(18);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        in.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14), new InputFilter.AllCaps()});
        in.setImeOptions(EditorInfo.IME_ACTION_DONE);
        in.setBackground(box(field, edge & 0x66FFFFFF, 14));
        in.setPadding(dp(14), dp(11), dp(14), dp(11));
        row.addView(in, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView go = new TextView(this);
        go.setText("Test et");
        go.setTextColor(ink);
        go.setTextSize(15);
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setBackground(box(accent, 0, 999));
        go.setPadding(dp(18), dp(12), dp(18), dp(12));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.leftMargin = dp(10);
        row.addView(go, gl);
        root.addView(row);

        final TextView res = new TextView(this);
        res.setText("Marka seçmene gerek yok: kodun düzeninden hangi gruba ait olduğunu ben bulurum.");
        res.setTextColor(muted);
        res.setTextSize(14);
        res.setLineSpacing(dp(3), 1f);
        res.setPadding(0, dp(12), 0, dp(4));
        ScrollView sc = new ScrollView(this);
        sc.addView(res);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.END);
        TextView full = new TextView(this);
        full.setText("Uygulamada aç");
        full.setTextColor(accent);
        full.setTextSize(14);
        full.setTypeface(Typeface.DEFAULT_BOLD);
        full.setPadding(dp(12), dp(10), dp(12), dp(10));
        foot.addView(full);
        TextView close = new TextView(this);
        close.setText("Kapat");
        close.setTextColor(muted);
        close.setTextSize(14);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setPadding(dp(12), dp(10), dp(4), dp(10));
        foot.addView(close);
        root.addView(foot);

        setContentView(root);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(420)), ViewGroup.LayoutParams.WRAP_CONTENT);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        in.requestFocus();

        final Runnable run = () -> {
            BatchCodec.Result r = BatchCodec.read(in.getText().toString());
            head.setText(r.head);
            head.setTextColor(r.ok ? fg : 0xFFFF8A7A);
            res.setText(r.report);
            res.setTextColor(fg);
            if (r.ok) {
                try {
                    JSONObject w = new JSONObject(getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE).getString(BatchWidget.KEY, "{}"));
                    w.put("main", r.head).put("sub", r.code + " · " + r.group);
                    getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE).edit().putString(BatchWidget.KEY, w.toString())
                            .putString("last_batch", new JSONObject().put("code", r.code).put("h", r.head).put("group", r.group).put("t", System.currentTimeMillis()).toString()).apply();
                    BatchWidget.updateAll(this);
                } catch (Exception ignored) { }
            }
        };
        go.setOnClickListener(v -> run.run());
        in.setOnEditorActionListener((v, id, ev) -> {
            run.run();
            return true;
        });
        close.setOnClickListener(v -> finish());
        full.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).putExtra("open", "batch").setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
    }
}
