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
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, BatchWidget.class));
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    private static int col(JSONObject o, String k, int def) {
        try {
            return Color.parseColor(o.optString(k));
        } catch (Exception e) {
            return def;
        }
    }

    private static RemoteViews build(Context c) {
        JSONObject o;
        try {
            o = new JSONObject(c.getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE).getString(KEY, "{}"));
        } catch (Exception e) {
            o = new JSONObject();
        }
        int bg = col(o, "bg", 0xFF211812), bg2 = col(o, "bg2", 0xFF17110D), fg = col(o, "fg", 0xFFF8EDE3), muted = col(o, "muted", 0xFFB49D8B);
        int accent = col(o, "accent", 0xFFF26D21), edge = col(o, "edge", 0xFFC98A4B), ink = col(o, "ink", 0xFF2A1407);
        float radius = (float) o.optDouble("radius", 26);

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_batch);
        v.setImageViewBitmap(R.id.w_bg, card(600, 300, bg, bg2, edge, radius * 2));
        v.setTextViewText(R.id.w_title, o.optString("title", "BB · Batch kontrol"));
        v.setTextColor(R.id.w_title, edge);
        v.setTextViewText(R.id.w_main, o.optString("main", "Parti kodunu kontrol et"));
        v.setTextColor(R.id.w_main, fg);
        v.setTextViewText(R.id.w_sub, o.optString("sub", "Dokun, kodu yaz: üretim tarihi ve rapor hemen gelsin."));
        v.setTextColor(R.id.w_sub, muted);
        v.setTextViewText(R.id.w_btn, o.optString("btn", "Kod kontrol et  ›"));
        v.setTextColor(R.id.w_btn, ink);
        v.setInt(R.id.w_btn, "setBackgroundColor", accent);

        Intent open = new Intent(c, MainActivity.class).putExtra("open", "batch")
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 41, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
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
