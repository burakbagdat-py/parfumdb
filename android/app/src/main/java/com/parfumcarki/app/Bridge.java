package com.parfumcarki.app;

import android.webkit.JavascriptInterface;

/** Methods the web page can call as window.ParfumApp.*. Async calls answer through window.__nativeResult(id, json). */
public class Bridge {
    private final MainActivity activity;

    Bridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public boolean isApp() {
        return true;
    }

    @JavascriptInterface
    public String versionCode() {
        return String.valueOf(BuildConfig.VERSION_CODE);
    }

    @JavascriptInterface
    public String versionName() {
        return BuildConfig.VERSION_NAME;
    }

    /** Stores the reminder plan (JSON) and re-arms all alarms. */
    @JavascriptInterface
    public void schedule(String json) {
        Scheduler.savePlan(activity, json);
        Scheduler.scheduleAll(activity);
    }

    @JavascriptInterface
    public boolean hasNotifPermission() {
        return Notifier.canNotify(activity);
    }

    @JavascriptInterface
    public void requestNotifPermission() {
        activity.runOnUiThread(activity::requestNotifPermission);
    }

    @JavascriptInterface
    public void test(String title, String body) {
        Notifier.show(activity, 4242, title, body);
    }

    @JavascriptInterface
    public void test(String title, String body, String sound, boolean vib) {
        Notifier.show(activity, 4242, title, body, sound, vib, "Deneme");
    }

    @JavascriptInterface
    public void previewSound(String spec) {
        activity.runOnUiThread(() -> activity.previewSound(spec));
    }

    @JavascriptInterface
    public void pickSound(int reqId) {
        activity.runOnUiThread(() -> activity.pickSound(reqId));
    }

    @JavascriptInterface
    public void boynerSearch(int reqId, String query, String mlFilter) {
        activity.boynerSearch(reqId, query, mlFilter);
    }

    @JavascriptInterface
    public void setBars(String hex) {
        activity.runOnUiThread(() -> activity.setBars(hex));
    }

    @JavascriptInterface
    public void openUrl(String url) {
        activity.runOnUiThread(() -> activity.openExternal(url));
    }

    @JavascriptInterface
    public String saveBackup(String json) {
        return activity.saveBackup(json);
    }
}
