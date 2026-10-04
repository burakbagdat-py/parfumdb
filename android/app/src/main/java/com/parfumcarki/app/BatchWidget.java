package com.parfumcarki.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.RemoteViews;

import org.json.JSONObject;

/**
 * Home-screen widget: one tap opens the quick batch-code check; it shows the last report in the colours of the current theme.
 * The page sends colours and text through Bridge.setWidget(json); nothing here talks to the network.
 */
public class BatchWidget extends AppWidgetProvider {
    static final String KEY = "widget";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c, m.getAppWidgetOptions(id)));
    }

    /** Resized on the home screen: one line when it is a thin bar, two when there is room. */
    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, android.os.Bundle options) {
        m.updateAppWidget(id, build(c, options));
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, BatchWidget.class));
        for (int id : ids) m.updateAppWidget(id, build(c, m.getAppWidgetOptions(id)));
    }

    private static int col(JSONObject o, String k, int def) {
        try {
            return Color.parseColor(o.optString(k));
        } catch (Exception e) {
            return def;
        }
    }

    private static RemoteViews build(Context c, android.os.Bundle options) {
        JSONObject o;
        try {
            o = new JSONObject(c.getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE).getString(KEY, "{}"));
        } catch (Exception e) {
            o = new JSONObject();
        }
        int bg = col(o, "bg", 0xFF211812), bg2 = col(o, "bg2", 0xFF17110D), fg = col(o, "fg", 0xFFF8EDE3), muted = col(o, "muted", 0xFFB49D8B);
        int accent = col(o, "accent", 0xFFF26D21), edge = col(o, "edge", 0xFFC98A4B), ink = col(o, "ink", 0xFF2A1407);
        int hDp = options == null ? 0 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        int wDp = options == null ? 0 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        boolean slim = hDp < 84;
        if (wDp <= 0) wDp = 300;
        if (hDp <= 0) hDp = 48;

        RemoteViews v = new RemoteViews(c.getPackageName(), slim ? R.layout.widget_batch_slim : R.layout.widget_batch);
        // the background is drawn at the widget's own proportions so its corners stay round at any size
        int bw = 600, bh = Math.max(60, Math.min(600, Math.round(600f * hDp / wDp)));
        float r = Math.min(bh / 2f, slim ? bh / 2f : 44f);
        v.setImageViewBitmap(R.id.w_bg, card(bw, bh, bg, bg2, edge, r));
        String main = o.optString("main", "Kod yaz ya da okut");
        v.setTextViewText(R.id.w_main, main);
        v.setTextColor(R.id.w_main, fg);
        if (!slim) {
            v.setTextViewText(R.id.w_sub, o.optString("sub", "Yazıya dokun: kodu yaz · Okut: QR ve barkod"));
            v.setTextColor(R.id.w_sub, muted);
        }
        v.setTextViewText(R.id.w_btn, "Okut");
        v.setTextColor(R.id.w_btn, ink);
        v.setInt(R.id.w_btn, "setBackgroundColor", accent);

        // the text opens the window for typing a code; the button opens it with the camera already reading
        Intent open = new Intent(c, BatchDialogActivity.class).setAction("com.parfumcarki.app.TYPE").setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 41, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Intent scan = new Intent(c, BatchDialogActivity.class).setAction("com.parfumcarki.app.SCAN").putExtra(BatchDialogActivity.MODE, BatchDialogActivity.SCAN)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.w_btn, PendingIntent.getActivity(c, 43, scan, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    /** Rounded card with a soft diagonal gradient and a thin edge in the theme's second colour. */
    private static Bitmap card(int w, int h, int top, int bottom, int edge, float r) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, top, bottom, Shader.TileMode.CLAMP));
        RectF box = new RectF(2, 2, w - 2, h - 2);
        cv.drawRoundRect(box, r, r, p);
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(3);
        s.setColor(edge);
        s.setAlpha(150);
        cv.drawRoundRect(box, r, r, s);
        return b;
    }
}
