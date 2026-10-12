package com.parfumcarki.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Messages for the user are a small file beside the page (data/inbox.json). The page shows them when the app is open;
 * this looks at the same file every half hour while the app is closed and raises a notification for a message that
 * has not been told yet. Tapping it opens the app, where the message is on screen.
 */
public class InboxWatch extends BroadcastReceiver {
    private static final String KEY_TOLD = "inbox_told";
    private static final int REQ = 910;
    private static final long EVERY = 30 * 60 * 1000L;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE);
    }

    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, REQ, new Intent(c, InboxWatch.class).setAction("com.parfumcarki.app.INBOX"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        // inexact: the phone may run it a little late to save battery
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + EVERY, pi);
    }

    /** The page has shown these messages itself: no notification for them. */
    static void mark(Context c, String idsJson) {
        try {
            Set<String> told = new HashSet<>(prefs(c).getStringSet(KEY_TOLD, new HashSet<>()));
            JSONArray a = new JSONArray(idsJson);
            for (int i = 0; i < a.length(); i++) told.add(a.getString(i));
            prefs(c).edit().putStringSet(KEY_TOLD, told).apply();
        } catch (Exception ignored) { }
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
        try {
            String body = MainActivity.httpText(BuildConfig.WEB_URL + "data/inbox.json?t=" + (System.currentTimeMillis() / 600000));
            JSONArray msgs = new JSONObject(body).optJSONArray("msgs");
            if (msgs == null) return;
            Set<String> told = new HashSet<>(prefs(c).getStringSet(KEY_TOLD, new HashSet<>()));
            boolean changed = false;
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.optJSONObject(i);
                if (m == null) continue;
                String id = m.optString("id");
                if (id.isEmpty() || told.contains(id)) continue;
                told.add(id);
                changed = true;
                Notifier.show(c, 4300 + (Math.abs(id.hashCode()) % 500), m.optString("title", "Yeni mesaj"), m.optString("short", "Açmak için dokun"));
            }
            if (changed) prefs(c).edit().putStringSet(KEY_TOLD, told).apply();
        } catch (Exception ignored) { }
    }
}
