package com.parfumcarki.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int FILE_REQ = 7;
    private static final int PERM_REQ = 8;
    private static final int SOUND_REQ = 9;
    static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private int soundReqId = -1;
    private Ringtone preview;
    private final ExecutorService net = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Notifier.ensureChannel(this);

        web = new WebView(this);
        web.setBackgroundColor(0xFF17110D);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(isOnline() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);

        web.addJavascriptInterface(new Bridge(this), "ParfumApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String home = Uri.parse(BuildConfig.WEB_URL).getHost();
                if (home != null && home.equals(u.getHost())) return false;
                openExternal(u.toString());
                return true;
            }

            /* Bottle photos come from hosts without CORS headers; serving them through the app lets the page
               read the pixels and cut away the white background. */
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String h = u.getHost();
                if (h == null || !"GET".equalsIgnoreCase(req.getMethod())) return null;
                if (!h.equals("fimgs.net") && !h.equals("statics-mp.boyner.com.tr")) return null;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(u.toString()).openConnection();
                    c.setRequestProperty("User-Agent", UA);
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(20000);
                    if (c.getResponseCode() != 200) return null;
                    String type = c.getContentType();
                    type = type == null ? "image/jpeg" : type.split(";")[0].trim();
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Access-Control-Allow-Origin", "*");
                    headers.put("Cache-Control", "public, max-age=604800");
                    return new WebResourceResponse(type, null, 200, "OK", headers, c.getInputStream());
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) view.loadDataWithBaseURL(null, offlinePage(), "text/html", "utf-8", null);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), FILE_REQ);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (state == null) loadHome();
        else web.restoreState(state);

        Scheduler.scheduleAll(this);
    }

    /* Each launch asks for a fresh copy of the page (unique URL), so a new version shows up immediately
       instead of after the HTTP cache expires. Offline, the cached copy of the plain URL is used. */
    private long loadedAt;

    private void loadHome() {
        boolean online = isOnline();
        web.getSettings().setCacheMode(online ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        String url = BuildConfig.WEB_URL;
        if (online) url += (url.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
        web.loadUrl(url);
        loadedAt = System.currentTimeMillis();
    }

    private String offlinePage() {
        return "<html><body style=\"background:#17110d;color:#f8ede3;font-family:sans-serif;padding:32px;text-align:center\">"
                + "<h2>İnternet bağlantısı yok</h2><p>BB Parfüm Envanteri ilk açılışta internete ihtiyaç duyar.</p>"
                + "<p><a style=\"color:#f0a25a\" href=\"" + BuildConfig.WEB_URL + "\">Tekrar dene</a></p></body></html>";
    }

    private boolean isOnline() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null || cm.getActiveNetwork() == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void deliver(int reqId, String json) {
        String js = "window.__nativeResult && window.__nativeResult(" + reqId + "," + JSONObject.quote(json) + ")";
        web.post(() -> web.evaluateJavascript(js, null));
    }

    void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    void setBars(String hex) {
        try {
            int col = Color.parseColor(hex);
            getWindow().setStatusBarColor(col);
            getWindow().setNavigationBarColor(col);
            web.setBackgroundColor(col);
        } catch (Exception ignored) { }
    }

    /* ---------- Boyner prices: one public search page per call, parsed on the phone ---------- */
    void boynerSearch(int reqId, String query, String mlFilter) {
        net.execute(() -> deliver(reqId, fetchBoyner(query, mlFilter)));
    }

    private static String fetchBoyner(String query, String mlFilter) {
        try {
            String url = "https://www.boyner.com.tr/search?q=" + URLEncoder.encode(query, "UTF-8")
                    + (mlFilter == null || mlFilter.isEmpty() ? "" : "&mililitre-bilgisi=" + URLEncoder.encode(mlFilter, "UTF-8"));
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            c.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9");
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String html = in == null ? "" : readAll(in);
            int i = html.indexOf("id=\"__NEXT_DATA__\"");
            if (i < 0) {
                JSONObject o = new JSONObject();
                o.put("error", "no-data");
                o.put("blocked", code == 403 || code == 503 || html.contains("Just a moment"));
                return o.toString();
            }
            int start = html.indexOf('>', i) + 1;
            int end = html.indexOf("</script>", start);
            JSONObject queries = new JSONObject(html.substring(start, end))
                    .getJSONObject("props").getJSONObject("pageProps").getJSONObject("initialState")
                    .getJSONObject("dsListingSearchService").getJSONObject("queries");
            JSONArray out = new JSONArray();
            Iterator<String> keys = queries.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (!k.startsWith("getProducts")) continue;
                JSONObject data = queries.getJSONObject(k).optJSONObject("data");
                JSONArray ps = data == null ? null : data.optJSONArray("Products");
                if (ps == null) continue;
                for (int j = 0; j < ps.length(); j++) {
                    JSONObject p = ps.getJSONObject(j);
                    JSONObject o = new JSONObject();
                    o.put("t", p.optString("Title"));
                    o.put("b", p.optString("Brand"));
                    o.put("c", p.optString("Category"));
                    o.put("u", p.optString("Url"));
                    JSONObject pi = p.optJSONObject("PriceInfo");
                    o.put("p", pi == null ? "" : pi.optString("Price"));
                    out.put(o);
                }
            }
            return new JSONObject().put("products", out).toString();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getClass().getSimpleName() + "\"}";
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    /* ---------- sounds ---------- */
    void previewSound(String spec) {
        if (preview != null && preview.isPlaying()) preview.stop();
        Uri u = Notifier.soundUri(this, spec);
        if (u == null) {
            Toast.makeText(this, "Sessiz", Toast.LENGTH_SHORT).show();
            return;
        }
        preview = RingtoneManager.getRingtone(this, u);
        if (preview != null) preview.play();
    }

    void pickSound(int reqId) {
        soundReqId = reqId;
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Bildirim sesi seç");
        try {
            startActivityForResult(i, SOUND_REQ);
        } catch (Exception e) {
            deliver(reqId, "{}");
        }
    }

    /* ---------- permissions, backup ---------- */
    void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            SharedPreferences p = getSharedPreferences(Scheduler.PREFS, MODE_PRIVATE);
            boolean askedBefore = p.getBoolean("asked", false);
            if (askedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                openNotificationSettings();
            } else {
                p.edit().putBoolean("asked", true).apply();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERM_REQ);
            }
        } else if (!Notifier.canNotify(this)) {
            openNotificationSettings();
        }
    }

    private void openNotificationSettings() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try { startActivity(i); } catch (Exception ignored) { }
    }

    String saveBackup(String json) {
        String name = "bb-parfum-yedek-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".json";
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IllegalStateException("insert failed");
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                return "İndirilenler klasörüne kaydedildi: " + name;
            } catch (Exception e) {
                return "Kaydedilemedi: " + e.getMessage();
            }
        }
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, json);
        runOnUiThread(() -> startActivity(Intent.createChooser(send, "Yedeği paylaş")));
        return "Paylaşım ekranı açıldı";
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == PERM_REQ) {
            boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            web.evaluateJavascript("window.onNativePermission && window.onNativePermission(" + ok + ")", null);
            Scheduler.scheduleAll(this);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == FILE_REQ && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
        } else if (code == SOUND_REQ && soundReqId >= 0) {
            int id = soundReqId;
            soundReqId = -1;
            Uri uri = result == RESULT_OK && data != null ? data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) : null;
            if (uri == null) {
                deliver(id, "{}");
                return;
            }
            String title = "Telefondan seçilen";
            try {
                Ringtone r = RingtoneManager.getRingtone(this, uri);
                if (r != null) title = r.getTitle(this);
            } catch (Exception ignored) { }
            try {
                deliver(id, new JSONObject().put("uri", uri.toString()).put("title", title).toString());
            } catch (Exception e) {
                deliver(id, "{}");
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("window.onNativeBack ? window.onNativeBack() : false", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        if (loadedAt > 0 && System.currentTimeMillis() - loadedAt > 6 * 3600_000L && isOnline()) loadHome();
        web.evaluateJavascript("window.onNativePermission && window.onNativePermission(" + Notifier.canNotify(this) + ")", null);
    }

    @Override
    protected void onPause() {
        web.onPause();
        if (preview != null && preview.isPlaying()) preview.stop();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }
}
