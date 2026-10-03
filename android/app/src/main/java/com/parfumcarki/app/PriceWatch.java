package com.parfumcarki.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Daily price alarm: once a day, for every watched perfume, one Boyner search for the chosen size;
 * if the lowest matching price is at or below the target, a notification is shown.
 * Watches are written by the page: [{"id","name","brand","q","ml","target"}].
 */
public class PriceWatch extends BroadcastReceiver {
    private static final String KEY_WATCHES = "price_watches";
    private static final String KEY_RESULTS = "price_results";
    private static final String KEY_TIME = "price_time";
    private static final int REQ = 900;
    private static final Set<String> SKIP = new HashSet<>(java.util.Arrays.asList(
            "eau", "de", "du", "la", "le", "parfum", "toilette", "edt", "edp", "extrait", "cologne", "spray", "ml",
            "erkek", "kadin", "unisex", "for", "men", "women", "pour", "homme", "femme", "fragrance", "intense"));
    private static final String[] NOT_PERFUME = {"set", "seti", "deodorant", "deo", "dus", "shower", "balm", "lotion",
            "losyon", "sampuan", "serum", "vucut", "body", "after", "sabun", "kit", "minyatur", "refill", "stick", "krem", "hair", "sac"};

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE);
    }

    static void save(Context c, String watchesJson, String hhmm) {
        prefs(c).edit().putString(KEY_WATCHES, watchesJson).putString(KEY_TIME, hhmm).apply();
        schedule(c);
    }

    static String results(Context c) {
        return prefs(c).getString(KEY_RESULTS, "{}");
    }

    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, REQ, new Intent(c, PriceWatch.class).setAction("com.parfumcarki.app.PRICE"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        JSONArray w = watches(c);
        if (w.length() == 0) return;
        String[] t = prefs(c).getString(KEY_TIME, "12:00").split(":");
        Calendar x = Calendar.getInstance();
        x.set(Calendar.HOUR_OF_DAY, Integer.parseInt(t[0]));
        x.set(Calendar.MINUTE, Integer.parseInt(t[1]));
        x.set(Calendar.SECOND, 0);
        if (x.getTimeInMillis() <= System.currentTimeMillis() + 60_000) x.add(Calendar.DAY_OF_YEAR, 1);
        // inexact is fine for a daily check and kinder to the battery
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, x.getTimeInMillis(), pi);
    }

    private static JSONArray watches(Context c) {
        try {
            return new JSONArray(prefs(c).getString(KEY_WATCHES, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s.toLowerCase(new Locale("tr")).replace('ı', 'i'), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").trim();
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        PendingResult done = goAsync();
        new Thread(() -> {
            try {
                check(c);
            } finally {
                schedule(c);
                done.finish();
            }
        }).start();
    }

    static void check(Context c) {
        JSONArray w = watches(c);
        JSONObject res;
        try {
            res = new JSONObject(results(c));
        } catch (Exception e) {
            res = new JSONObject();
        }
        for (int i = 0; i < w.length(); i++) {
            JSONObject x = w.optJSONObject(i);
            if (x == null) continue;
            try {
                String json = MainActivity.fetchBoyner(x.optString("q"), x.optInt("ml", 100) + "-ml");
                JSONArray ps = new JSONObject(json).optJSONArray("products");
                if (ps == null) continue;
                Set<String> need = new HashSet<>();
                for (String t : fold(x.optString("name")).split(" ")) if (!t.isEmpty() && !SKIP.contains(t)) need.add(t);
                double best = Double.MAX_VALUE;
                String url = "";
                outer:
                for (int j = 0; j < ps.length(); j++) {
                    JSONObject p = ps.getJSONObject(j);
                    String title = " " + fold(p.optString("t")) + " ";
                    if (!fold(p.optString("c")).contains("parfum")) continue;
                    for (String bad : NOT_PERFUME) if (title.contains(" " + bad + " ")) continue outer;
                    for (String t : need) if (!title.contains(" " + t + " ")) continue outer;
                    double price = Double.parseDouble(p.optString("p", "0").replace(".", "").replace(',', '.'));
                    if (price > 0 && price < best) {
                        best = price;
                        url = "https://www.boyner.com.tr/" + p.optString("u");
                    }
                }
                if (best == Double.MAX_VALUE) continue;
                JSONObject r = new JSONObject().put("price", best).put("url", url).put("t", System.currentTimeMillis());
                res.put(x.optString("id"), r);
                double target = x.optDouble("target", 0);
                if (target > 0 && best <= target) {
                    Notifier.show(c, 700 + i, "Fiyat düştü: " + x.optString("name"),
                            x.optString("brand") + " · " + x.optInt("ml", 100) + " ml · " + formatTl(best) + " (hedefin " + formatTl(target) + ")",
                            "dl:bb_yumusak", true, "Fiyat alarmı");
                }
                Thread.sleep(1500);
            } catch (Exception ignored) { }
        }
        prefs(c).edit().putString(KEY_RESULTS, res.toString()).apply();
    }

    private static String formatTl(double v) {
        return String.format(new Locale("tr", "TR"), "%,.0f TL", v);
    }
}
