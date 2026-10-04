package com.parfumcarki.app;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
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
import android.webkit.PermissionRequest;
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
    private static final int CAM_REQ = 77;
    private PermissionRequest cameraRequest;
    private static final int PERM_REQ = 8;
    private static final int SOUND_REQ = 9;
    static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private int soundReqId = -1;
    private Ringtone preview;
    // several stores are searched at the same time
    private final ExecutorService net = Executors.newFixedThreadPool(10);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Notifier.ensureChannel(this);
        readOpen(getIntent());

        int bar = getSharedPreferences(Scheduler.PREFS, MODE_PRIVATE).getInt("bar", 0xFF17110D);
        web = new WebView(this);
        web.setBackgroundColor(bar);
        // app feel: no edge glow, no scrollbars, no long-press menu
        web.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOnLongClickListener(v -> true);
        web.setLongClickable(false);

        // splash shown until the page reports it has drawn its first screen
        splash = new android.widget.FrameLayout(this);
        splash.setBackgroundColor(bar);
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher_fg);
        int size = (int) (160 * getResources().getDisplayMetrics().density);
        splash.addView(logo, new android.widget.FrameLayout.LayoutParams(size, size, android.view.Gravity.CENTER));
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(web);
        root.addView(splash);
        setContentView(root);
        setBars(String.format("#%06X", bar & 0xFFFFFF));
        if (getSharedPreferences(Scheduler.PREFS, MODE_PRIVATE).getBoolean("fullscreen", false)) setFullscreen(true);
        web.postDelayed(this::hideSplash, 8000);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);
        s.setSafeBrowsingEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setCacheMode(isOnline() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);

        web.addJavascriptInterface(new Bridge(this), "ParfumApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String home = Uri.parse(BuildConfig.WEB_URL).getHost();
                if ("https".equals(u.getScheme()) && home != null && home.equals(u.getHost())) return false;
                if ("https".equals(u.getScheme()) || "http".equals(u.getScheme())) openExternal(u.toString());
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
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                pageUrl = url;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.postDelayed(MainActivity.this::hideSplash, 1500);
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

            // camera for the in-app code scanner: only our own page, only video, and only after Android's own permission dialog
            @Override
            public void onPermissionRequest(final PermissionRequest req) {
                runOnUiThread(() -> {
                    boolean video = false;
                    for (String r : req.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) video = true;
                    if (!video || !trusted()) { req.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        req.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        if (cameraRequest != null) cameraRequest.deny();
                        cameraRequest = req;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAM_REQ);
                    }
                });
            }
        });

        loadHome();

        Scheduler.scheduleAll(this);
    }

    /* Each launch asks for a fresh copy of the page (unique URL), so a new version shows up immediately
       instead of after the HTTP cache expires. Offline, the cached copy of the plain URL is used. */
    private long loadedAt;
    private android.widget.FrameLayout splash;
    private volatile String pageUrl = "";

    /** True only while our own site (the GitHub Pages address) is shown. */
    private static final String[][] ICONS = {{"classic", "IconClassic"}, {"scandal", "IconScandal"}, {"malachite", "IconMalachite"}, {"lune", "IconLune"}, {"sartorial", "IconSartorial"}, {"denim", "IconDenim"}};
    private String pendingOpen = "";

    String takeOpen() {
        String o = pendingOpen;
        pendingOpen = "";
        return o;
    }

    private void readOpen(Intent i) {
        String o = i == null ? null : i.getStringExtra("open");
        if ("batch".equals(o) || "scan".equals(o)) pendingOpen = o;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        readOpen(intent);
        if (!pendingOpen.isEmpty() && web != null) web.evaluateJavascript("window.onNativeOpen && window.onNativeOpen()", null);
    }

    String appIcon() {
        PackageManager pm = getPackageManager();
        for (String[] ic : ICONS) {
            int st = pm.getComponentEnabledSetting(new android.content.ComponentName(this, getPackageName() + "." + ic[1]));
            if (st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return ic[0];
        }
        return "classic";
    }

    /** Exactly one launcher alias stays enabled; the new one is switched on before the others go off. */
    boolean setAppIcon(String key) {
        String target = null;
        for (String[] ic : ICONS) if (ic[0].equals(key)) target = ic[1];
        if (target == null) return false;
        PackageManager pm = getPackageManager();
        try {
            pm.setComponentEnabledSetting(new android.content.ComponentName(this, getPackageName() + "." + target),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            for (String[] ic : ICONS) {
                if (ic[1].equals(target)) continue;
                pm.setComponentEnabledSetting(new android.content.ComponentName(this, getPackageName() + "." + ic[1]),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    boolean trusted() {
        Uri home = Uri.parse(BuildConfig.WEB_URL), cur = Uri.parse(pageUrl == null ? "" : pageUrl);
        return "https".equals(cur.getScheme()) && home.getHost() != null && home.getHost().equals(cur.getHost())
                && cur.getPath() != null && cur.getPath().startsWith(home.getPath());
    }

    /** https://github.com/<owner>/<repo>/releases/download/ for this app's own repository. */
    private String releasePrefix() {
        Uri home = Uri.parse(BuildConfig.WEB_URL);
        String owner = home.getHost() == null ? "" : home.getHost().split("\\.")[0];
        String repo = home.getPathSegments().isEmpty() ? "" : home.getPathSegments().get(0);
        return "https://github.com/" + owner + "/" + repo + "/releases/download/";
    }

    private static boolean hostAllowed(String url, String... suffixes) {
        Uri u = Uri.parse(url);
        if (!"https".equals(u.getScheme()) || u.getHost() == null) return false;
        for (String s : suffixes) if (u.getHost().equals(s) || u.getHost().endsWith("." + s)) return true;
        return false;
    }

    void setFullscreen(boolean on) {
        android.view.View d = getWindow().getDecorView();
        int f = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        int cur = d.getSystemUiVisibility();
        d.setSystemUiVisibility(on ? (cur | f) : (cur & ~f));
        getSharedPreferences(Scheduler.PREFS, MODE_PRIVATE).edit().putBoolean("fullscreen", on).apply();
    }

    void hideSplash() {
        if (splash == null || splash.getVisibility() != android.view.View.VISIBLE) return;
        splash.animate().alpha(0f).setDuration(220).withEndAction(() -> splash.setVisibility(android.view.View.GONE)).start();
    }

    void haptic(String kind) {
        int c = android.view.HapticFeedbackConstants.CLOCK_TICK;
        if ("confirm".equals(kind)) c = Build.VERSION.SDK_INT >= 30 ? android.view.HapticFeedbackConstants.CONFIRM : android.view.HapticFeedbackConstants.LONG_PRESS;
        else if ("press".equals(kind)) c = android.view.HapticFeedbackConstants.KEYBOARD_TAP;
        web.performHapticFeedback(c);
    }

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
        String scheme = Uri.parse(url).getScheme();
        if (!"https".equals(scheme) && !"http".equals(scheme)) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    void setBars(String hex) {
        try {
            int col = Color.parseColor(hex);
            getSharedPreferences(Scheduler.PREFS, MODE_PRIVATE).edit().putInt("bar", col).apply();
            if (splash != null) splash.setBackgroundColor(col);
            getWindow().setStatusBarColor(col);
            getWindow().setNavigationBarColor(col);
            web.setBackgroundColor(col);
            // light themes need dark status/navigation bar icons
            double lum = (0.299 * Color.red(col) + 0.587 * Color.green(col) + 0.114 * Color.blue(col)) / 255;
            int flags = web.getRootView().getSystemUiVisibility();
            int light = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            web.getRootView().setSystemUiVisibility(lum > 0.6 ? (flags | light) : (flags & ~light));
        } catch (Exception ignored) { }
    }

    /* ---------- Boyner prices: one public search page per call, parsed on the phone ---------- */
    void boynerSearch(int reqId, String query, String mlFilter) {
        net.execute(() -> deliver(reqId, fetchBoyner(query, mlFilter)));
    }

    static String fetchBoyner(String query, String mlFilter) {
        try {
            String url = "https://www.boyner.com.tr/search?q=" + URLEncoder.encode(query, "UTF-8")
                    + (mlFilter == null || mlFilter.isEmpty() ? "" : "&mililitre-bilgisi=" + URLEncoder.encode(mlFilter, "UTF-8"));
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            c.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9");
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String html = in == null ? "" : readAll(in);
            int i = html.indexOf("id=\"__NEXT_DATA__\"");
            if (i < 0) {
                JSONObject o = new JSONObject();
                o.put("error", "no-data");
                o.put("blocked", code == 403 || code == 429 || code == 503 || html.contains("Just a moment"));
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

    static String httpText(String url) throws Exception {
        try {
            return httpOnce(url);
        } catch (java.net.SocketTimeoutException | IllegalStateException e) {
            throw e;    // too slow, or refused: asking again right away only makes it worse
        } catch (java.io.IOException e) {
            Thread.sleep(300);
            return httpOnce(url);   // the line dropped
        }
    }

    private static String httpOnce(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        c.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9");
        c.setConnectTimeout(6000);
        c.setReadTimeout(11000);
        int code = c.getResponseCode();
        if (code == 403 || code == 429 || code == 503) throw new IllegalStateException("blocked");
        if (code >= 400) throw new IllegalStateException("http " + code);
        return readAll(c.getInputStream());
    }

    void shopSearch(int reqId, String ruleJson, String query, int ml) {
        net.execute(() -> {
            try {
                JSONArray items = ShopEngine.search(new JSONObject(ruleJson), query, ml);
                deliver(reqId, new JSONObject().put("items", items).toString());
            } catch (Exception e) {
                boolean blocked = "blocked".equals(e.getMessage());
                deliver(reqId, "{\"error\":\"" + e.getClass().getSimpleName() + "\",\"blocked\":" + blocked + "}");
            }
        });
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    /* ---------- generic fetch: lets the page add new price sources without a new APK ---------- */
    void httpGet(int reqId, String url, String mode) {
        net.execute(() -> {
            try {
                if (!hostAllowed(url, "boyner.com.tr", "beymen.com", "sephora.com.tr", "trendyol.com", "hepsiburada.com", "amazon.com.tr", Uri.parse(BuildConfig.WEB_URL).getHost())) {
                    throw new IllegalArgumentException("host not allowed");
                }
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9");
                c.setConnectTimeout(15000);
                c.setReadTimeout(25000);
                int code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                String body = in == null ? "" : readAll(in);
                if ("next".equals(mode)) {
                    int i = body.indexOf("id=\"__NEXT_DATA__\"");
                    if (i < 0) {
                        body = "";
                    } else {
                        int s = body.indexOf('>', i) + 1;
                        body = body.substring(s, body.indexOf("</script>", s));
                    }
                } else if (mode != null && mode.startsWith("hrefs:")) {
                    // only the links whose path matches the given pattern, e.g. product pages
                    java.util.regex.Pattern pat = java.util.regex.Pattern.compile(mode.substring(6));
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("href=\"([^\"]{1,300})\"").matcher(body);
                    java.util.LinkedHashSet<String> links = new java.util.LinkedHashSet<>();
                    while (m.find() && links.size() < 80) if (pat.matcher(m.group(1)).find()) links.add(m.group(1));
                    body = new JSONArray(links).toString();
                } else if (body.length() > 3_000_000) {
                    body = body.substring(0, 3_000_000);
                }
                deliver(reqId, new JSONObject().put("status", code).put("body", body).toString());
            } catch (Exception e) {
                deliver(reqId, "{\"error\":\"" + e.getClass().getSimpleName() + "\"}");
            }
        });
    }

    /* ---------- downloadable notification sounds ---------- */
    boolean hasSound(String name) {
        return SoundProvider.safeName(name) && new java.io.File(SoundProvider.dir(this), name + ".wav").exists();
    }

    void installSound(int reqId, String name, String url) {
        net.execute(() -> {
            try {
                if (!SoundProvider.safeName(name)) throw new IllegalArgumentException("name");
                if (!url.startsWith(BuildConfig.WEB_URL)) throw new IllegalArgumentException("sounds come from our own site only");
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestProperty("User-Agent", UA);
                if (c.getResponseCode() != 200) throw new IllegalStateException("http " + c.getResponseCode());
                java.io.File tmp = new java.io.File(SoundProvider.dir(this), name + ".tmp");
                try (InputStream in = c.getInputStream(); OutputStream out = new java.io.FileOutputStream(tmp)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                java.io.File f = new java.io.File(SoundProvider.dir(this), name + ".wav");
                if (!tmp.renameTo(f)) throw new IllegalStateException("rename");
                deliver(reqId, "{\"ok\":true}");
            } catch (Exception e) {
                deliver(reqId, "{\"error\":\"" + e.getClass().getSimpleName() + "\"}");
            }
        });
    }

    /* ---------- in-app update: download the APK and hand it to Android's installer ---------- */
    void installUpdate(String url) {
        // updates may only come from this app's own GitHub releases; Android additionally refuses any APK
        // that is not signed with the app's key
        if (url == null || !url.startsWith(releasePrefix()) || !url.endsWith(".apk")) {
            Toast.makeText(this, "Güncelleme adresi geçersiz", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Bu uygulamaya “Bilinmeyen uygulamaları yükle” izni ver, sonra tekrar dokun", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
            return;
        }
        Toast.makeText(this, "Güncelleme indiriliyor…", Toast.LENGTH_SHORT).show();
        net.execute(() -> {
            try {
                android.content.pm.PackageInstaller pi = getPackageManager().getPackageInstaller();
                android.content.pm.PackageInstaller.SessionParams params =
                        new android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int sid = pi.createSession(params);
                try (android.content.pm.PackageInstaller.Session session = pi.openSession(sid)) {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestProperty("User-Agent", UA);
                    c.setInstanceFollowRedirects(true);
                    if (c.getResponseCode() != 200) throw new IllegalStateException("http " + c.getResponseCode());
                    try (InputStream in = c.getInputStream(); OutputStream out = session.openWrite("update.apk", 0, -1)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        session.fsync(out);
                    }
                    Intent done = new Intent(this, InstallReceiver.class);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent p = PendingIntent.getBroadcast(this, 77, done, flags);
                    session.commit(p.getIntentSender());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Güncelleme indirilemedi: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show());
            }
        });
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
        if (code == CAM_REQ && cameraRequest != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) cameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else cameraRequest.deny();
            cameraRequest = null;
        }
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

}
