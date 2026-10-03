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

    static void setSound(Context c, String spec) {
        prefs(c).edit().putString("price_sound", spec).apply();
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
            String name = x.optString("name"), brand = x.optString("brand"), conc = x.optString("conc"), q = x.optString("q");
            int ml = x.optInt("ml", 100);
            JSONArray rules = x.optJSONArray("rules");
            if (rules == null) rules = defaultRules();
            double best = Double.MAX_VALUE;
            String bestUrl = "", bestShop = "";
            JSONObject perShop = new JSONObject();
            for (int k = 0; k < rules.length(); k++) {
                try {
                    JSONObject rule = rules.getJSONObject(k);
                    boolean boyner = "boyner".equals(rule.optString("type"));
                    JSONArray qs = x.optJSONArray("qs");
                    if (qs == null || qs.length() == 0) qs = new JSONArray().put(q);
                    JSONObject hit = null;
                    for (int v = 0; v < qs.length() && hit == null; v++) {
                        JSONArray items;
                        try {
                            items = ShopEngine.search(rule, qs.getString(v), boyner ? ml : 0);
                        } catch (Exception e) {
                            if ("blocked".equals(e.getMessage())) break;
                            Thread.sleep(2000);
                            try {
                                items = ShopEngine.search(rule, qs.getString(v), boyner ? ml : 0);
                            } catch (Exception e2) {
                                continue;
                            }
                        }
                        hit = ShopEngine.sizes(items, name, brand, conc, boyner ? ml : 0).optJSONObject(String.valueOf(ml));
                        if (hit == null) Thread.sleep(800);
                    }
                    if (hit != null) {
                        perShop.put(rule.optString("id"), hit.getDouble("p"));
                        if (hit.getDouble("p") < best) {
                            best = hit.getDouble("p");
                            bestUrl = hit.optString("u");
                            bestShop = rule.optString("n");
                        }
                    }
                    Thread.sleep(1200);
                } catch (Exception ignored) { }
            }
            if (best == Double.MAX_VALUE) continue;
            try {
                res.put(x.optString("id"), new JSONObject().put("price", best).put("url", bestUrl).put("shop", bestShop)
                        .put("shops", perShop).put("t", System.currentTimeMillis()));
                double target = x.optDouble("target", 0);
                if (target > 0 && best <= target) {
                    Notifier.show(c, 700 + i, "Fiyat düştü: " + name,
                            bestShop + " · " + ml + " ml · " + formatTl(best) + " (hedefin " + formatTl(target) + ")",
                            prefs(c).getString("price_sound", "dl:fiyat"), true, "Fiyat alarmı");
                }
            } catch (Exception ignored) { }
        }
        prefs(c).edit().putString(KEY_RESULTS, res.toString()).apply();
    }

    private static JSONArray defaultRules() {
        try {
            return new JSONArray().put(new JSONObject().put("id", "boyner").put("n", "Boyner").put("type", "boyner"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static String formatTl(double v) {
        return String.format(new Locale("tr", "TR"), "%,.0f TL", v);
    }
}
