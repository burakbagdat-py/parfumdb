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

    /** Colours and text for the home-screen widget, as JSON. */
    @JavascriptInterface
    public void setWidget(String json) {
        if (!activity.trusted() || json == null || json.length() > 4000) return;
        activity.getSharedPreferences(Scheduler.PREFS, android.content.Context.MODE_PRIVATE).edit().putString(BatchWidget.KEY, json).apply();
        BatchWidget.updateAll(activity);
    }

    /** Asks the launcher to add the widget to the home screen; false when the launcher cannot do that. */
    @JavascriptInterface
    public boolean pinWidget() {
        if (!activity.trusted()) return false;
        android.appwidget.AppWidgetManager m = android.appwidget.AppWidgetManager.getInstance(activity);
        if (!m.isRequestPinAppWidgetSupported()) return false;
        return m.requestPinAppWidget(new android.content.ComponentName(activity, BatchWidget.class), null, null);
    }

    /** Android 13+: asks the system to add the "Batch kontrol" tile to quick settings. */
    @JavascriptInterface
    @android.annotation.TargetApi(33)
    public boolean addTile() {
        if (!activity.trusted() || android.os.Build.VERSION.SDK_INT < 33) return false;
        try {
            android.app.StatusBarManager sb = activity.getSystemService(android.app.StatusBarManager.class);
            sb.requestAddTileService(new android.content.ComponentName(activity, BatchTileService.class), "Batch kontrol",
                    android.graphics.drawable.Icon.createWithResource(activity, R.drawable.ic_stat), activity.getMainExecutor(), r -> { });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The last code checked in the small dialog (JSON), so the page can show it too. */
    @JavascriptInterface
    public String lastBatch() {
        if (!activity.trusted()) return "";
        return activity.getSharedPreferences(Scheduler.PREFS, android.content.Context.MODE_PRIVATE).getString("last_batch", "");
    }

    /** What the app was opened for ("batch" from the widget); read once. */
    @JavascriptInterface
    public String pendingOpen() {
        if (!activity.trusted()) return "";
        return activity.takeOpen();
    }

    @JavascriptInterface
    public String appIcon() {
        if (!activity.trusted()) return "";
        return activity.appIcon();
    }

    /** Switches the launcher logo: classic, scandal, malachite or lune. */
    @JavascriptInterface
    public boolean setAppIcon(String key) {
        if (!activity.trusted()) return false;
        return activity.setAppIcon(key);
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
    public void shopSearch(int reqId, String ruleJson, String query, int ml) {
        if (!activity.trusted()) return;
        activity.shopSearch(reqId, ruleJson, query, ml);
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

    /** The system share sheet with a text (the card's link). */
    @JavascriptInterface
    public void shareText(String text) {
        if (!activity.trusted()) return;
        activity.runOnUiThread(() -> activity.shareText(text));
    }

    /** QR code for a text as "size;0101…" row by row, or "" when it cannot be made. Drawn by the page. */
    @JavascriptInterface
    public String qr(String text) {
        if (!activity.trusted()) return "";
        return MainActivity.qr(text);
    }

    /** Saves a part of the screen (CSS pixels of a viewport vw wide) as a picture and opens the share sheet with it. */
    @JavascriptInterface
    public void shareShot(double x, double y, double w, double h, double vw) {
        if (!activity.trusted()) return;
        activity.runOnUiThread(() -> activity.shareShot(x, y, w, h, vw));
    }

    @JavascriptInterface
    public String saveBackup(String json) {
        if (!activity.trusted()) return "";
        return activity.saveBackup(json);
    }
}
