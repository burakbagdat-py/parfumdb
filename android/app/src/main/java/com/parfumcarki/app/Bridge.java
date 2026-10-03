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
        if (!activity.trusted()) return "";
        return String.valueOf(BuildConfig.VERSION_CODE);
    }

    @JavascriptInterface
    public String versionName() {
        if (!activity.trusted()) return "";
        return BuildConfig.VERSION_NAME;
    }

    /** Stores the reminder plan (JSON) and re-arms all alarms. */
    @JavascriptInterface
    public void schedule(String json) {
        if (!activity.trusted()) return;
        Scheduler.savePlan(activity, json);
        Scheduler.scheduleAll(activity);
    }

    @JavascriptInterface
    public boolean hasNotifPermission() {
        if (!activity.trusted()) return false;
        return Notifier.canNotify(activity);
    }

    @JavascriptInterface
    public void requestNotifPermission() {
        if (!activity.trusted()) return;
        activity.runOnUiThread(activity::requestNotifPermission);
    }

    @JavascriptInterface
    public void test(String title, String body) {
        if (!activity.trusted()) return;
        Notifier.show(activity, 4242, title, body);
    }

    @JavascriptInterface
    public void test(String title, String body, String sound, boolean vib) {
        if (!activity.trusted()) return;
        Notifier.show(activity, 4242, title, body, sound, vib, "Deneme");
    }

    @JavascriptInterface
    public void previewSound(String spec) {
        if (!activity.trusted()) return;
        activity.runOnUiThread(() -> activity.previewSound(spec));
    }

    @JavascriptInterface
    public void pickSound(int reqId) {
        if (!activity.trusted()) return;
        activity.runOnUiThread(() -> activity.pickSound(reqId));
    }

    @JavascriptInterface
    public void boynerSearch(int reqId, String query, String mlFilter) {
        if (!activity.trusted()) return;
        activity.boynerSearch(reqId, query, mlFilter);
    }

    @JavascriptInterface
    public void httpGet(int reqId, String url, String mode) {
        if (!activity.trusted()) return;
        activity.httpGet(reqId, url, mode);
    }

    @JavascriptInterface
    public boolean hasSound(String name) {
        if (!activity.trusted()) return false;
        return activity.hasSound(name);
    }

    @JavascriptInterface
    public void installSound(int reqId, String name, String url) {
        if (!activity.trusted()) return;
        activity.installSound(reqId, name, url);
    }

    @JavascriptInterface
    public void installUpdate(String url) {
        if (!activity.trusted()) return;
        activity.runOnUiThread(() -> activity.installUpdate(url));
    }

    @JavascriptInterface
    public void ready() {
        if (!activity.trusted()) return;
        activity.runOnUiThread(activity::hideSplash);
    }

    @JavascriptInterface
    public void haptic(String kind) {
        if (!activity.trusted()) return;
        activity.runOnUiThread(() -> activity.haptic(kind));
    }

    @JavascriptInterface
    public void setFullscreen(boolean on) {
        if (!activity.trusted()) return;
        activity.runOnUiThread(() -> activity.setFullscreen(on));
    }

    @JavascriptInterface
    public void setPriceWatches(String json, String hhmm) {
        if (!activity.trusted()) return;
        PriceWatch.save(activity, json, hhmm);
    }

    @JavascriptInterface
    public void setPriceSound(String spec) {
        if (!activity.trusted()) return;
        PriceWatch.setSound(activity, spec);
    }

    @JavascriptInterface
    public String priceResults() {
        if (!activity.trusted()) return "{}";
        return PriceWatch.results(activity);
    }

    @JavascriptInterface
    public void checkPricesNow() {
        if (!activity.trusted()) return;
        new Thread(() -> PriceWatch.check(activity)).start();
    }

    @JavascriptInterface
    public void setBars(String hex) {
        if (!activity.trusted()) return;
        activity.runOnUiThread(() -> activity.setBars(hex));
    }

    @JavascriptInterface
    public void openUrl(String url) {
        if (!activity.trusted()) return;
        activity.runOnUiThread(() -> activity.openExternal(url));
    }

    @JavascriptInterface
    public String saveBackup(String json) {
        if (!activity.trusted()) return "";
        return activity.saveBackup(json);
    }
}
