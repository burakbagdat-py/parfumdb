package com.parfumcarki.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

final class Notifier {
    private static final String LEGACY_CHANNEL = "daily";

    private Notifier() { }

    static void ensureChannel(Context c) {
        channelFor(c, "default", true, "Telefonun sesi");
    }

    static boolean canNotify(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    /**
     * Sound spec: "default", "silent", "raw:&lt;name&gt;" (bundled sound) or a content:// URI picked on the phone.
     * Returns null for silent.
     */
    static Uri soundUri(Context c, String spec) {
        if (spec == null || spec.isEmpty() || spec.equals("default")) return Settings.System.DEFAULT_NOTIFICATION_URI;
        if (spec.equals("silent")) return null;
        if (spec.startsWith("raw:")) {
            int id = c.getResources().getIdentifier(spec.substring(4), "raw", c.getPackageName());
            if (id == 0) return Settings.System.DEFAULT_NOTIFICATION_URI;
            return Uri.parse("android.resource://" + c.getPackageName() + "/" + id);
        }
        return Uri.parse(spec);
    }

    /** A channel's sound cannot change after creation, so every sound/vibration combination gets its own channel. */
    static String channelFor(Context c, String spec, boolean vib, String soundName) {
        String key = spec == null ? "default" : spec;
        String id = "s_" + Integer.toHexString(key.hashCode()) + (vib ? "_v" : "_n");
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(id) == null) {
            NotificationChannel ch = new NotificationChannel(id,
                    "Hatırlatıcı · " + (soundName == null || soundName.isEmpty() ? "Ses" : soundName) + (vib ? "" : " · titreşimsiz"),
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Parfüm hatırlatıcıları");
            Uri u = soundUri(c, key);
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            ch.setSound(u, u == null ? null : aa);
            ch.enableVibration(vib);
            if (vib) ch.setVibrationPattern(new long[]{0, 180, 120, 220});
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(LEGACY_CHANNEL) != null) nm.deleteNotificationChannel(LEGACY_CHANNEL);
        return id;
    }

    static void show(Context c, int id, String title, String body) {
        show(c, id, title, body, "default", true, "Telefonun sesi");
    }

    static void show(Context c, int id, String title, String body, String sound, boolean vib, String soundName) {
        if (!canNotify(c)) return;
        String channel = channelFor(c, sound, vib, soundName);
        Intent open = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFFF26D21)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        c.getSystemService(NotificationManager.class).notify(id, n);
    }
}
