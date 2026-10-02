package com.parfumcarki.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Random;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int idx = intent.getIntExtra("idx", -1);
        JSONArray rs = Scheduler.reminders(c);
        JSONObject r = idx >= 0 ? rs.optJSONObject(idx) : null;
        if (r == null) return;

        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
        JSONObject msgs = r.optJSONObject("msgs");
        JSONArray list = msgs != null ? msgs.optJSONArray(String.valueOf(dow)) : null;
        String title = "Bugün ne sıksan?";
        String body = "BB Parfüm Envanteri'ni aç, çarkı çevir.";
        if (list != null && list.length() > 0) {
            JSONObject m = list.optJSONObject(new Random().nextInt(list.length()));
            if (m != null) {
                title = m.optString("t", title);
                body = m.optString("b", body);
            }
        }
        Notifier.show(c, 100 + idx, title, body,
                r.optString("sound", "default"), r.optBoolean("vib", true), r.optString("soundName", "Ses"));
        Scheduler.scheduleOne(c, idx, r);
    }
}
