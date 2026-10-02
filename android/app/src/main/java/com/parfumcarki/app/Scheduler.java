package com.parfumcarki.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Plan format (written by the web page):
 * {"reminders":[{"id":"sabah","h":8,"m":30,"days":[0..6],"msgs":{"0":[{"t":"..","b":".."}],...}}]}
 * days use JavaScript numbering: 0 = Sunday.
 */
final class Scheduler {
    static final String PREFS = "parfum";
    private static final String KEY_PLAN = "plan";
    private static final String KEY_COUNT = "count";
    private static final int MAX = 20;

    private Scheduler() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void savePlan(Context c, String json) {
        prefs(c).edit().putString(KEY_PLAN, json).apply();
    }

    static JSONArray reminders(Context c) {
        try {
            JSONArray a = new JSONObject(prefs(c).getString(KEY_PLAN, "{}")).optJSONArray("reminders");
            return a != null ? a : new JSONArray();
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void scheduleAll(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        int old = prefs(c).getInt(KEY_COUNT, MAX);
        for (int i = 0; i < Math.max(old, 1); i++) am.cancel(pending(c, i));
        JSONArray rs = reminders(c);
        int n = Math.min(rs.length(), MAX);
        for (int i = 0; i < n; i++) scheduleOne(c, i, rs.optJSONObject(i));
        prefs(c).edit().putInt(KEY_COUNT, n).apply();
    }

    static void scheduleOne(Context c, int idx, JSONObject r) {
        if (r == null) return;
        long when = nextTrigger(r);
        if (when < 0) return;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = pending(c, idx);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    static long nextTrigger(JSONObject r) {
        // one-time reminder (e.g. "buy this perfume"): fires once at the given moment
        if (r.has("at")) {
            long at = r.optLong("at", -1);
            return at > System.currentTimeMillis() ? at : -1;
        }
        int h = r.optInt("h", 8), m = r.optInt("m", 30);
        boolean[] ok = new boolean[7];
        JSONArray days = r.optJSONArray("days");
        if (days == null) return -1;
        for (int k = 0; k < days.length(); k++) {
            int d = days.optInt(k, -1);
            if (d >= 0 && d < 7) ok[d] = true;
        }
        long now = System.currentTimeMillis();
        for (int add = 0; add < 8; add++) {
            Calendar x = Calendar.getInstance();
            x.add(Calendar.DAY_OF_YEAR, add);
            x.set(Calendar.HOUR_OF_DAY, h);
            x.set(Calendar.MINUTE, m);
            x.set(Calendar.SECOND, 0);
            x.set(Calendar.MILLISECOND, 0);
            int dow = x.get(Calendar.DAY_OF_WEEK) - 1;
            if (ok[dow] && x.getTimeInMillis() > now + 1000) return x.getTimeInMillis();
        }
        return -1;
    }

    private static PendingIntent pending(Context c, int idx) {
        Intent it = new Intent(c, AlarmReceiver.class)
                .setAction("com.parfumcarki.app.REMIND." + idx)
                .putExtra("idx", idx);
        return PendingIntent.getBroadcast(c, idx, it, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
