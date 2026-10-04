package com.parfumcarki.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;

import org.json.JSONObject;

/**
 * The small window opened by the quick-settings tile and by the widget. It never opens the app: the camera reads a QR code or a
 * barcode right here, a batch code can be typed, and the short report appears in the same window. Offline; wears the app's theme.
 */
public class BatchDialogActivity extends Activity implements CodeScanner.Listener {
    static final String MODE = "mode", SCAN = "scan";
    private static final int ASK_CAMERA = 7;

    private int fg, muted, bad;
    private TextView head, res, scanBtn, openBtn;
    private FrameLayout camBox;
    private LinearLayout camRow, typeRow;
    private EditText in;
    private CodeScanner scanner;
    private boolean scanning, asked;
    private String url = "";

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static int col(JSONObject o, String k, int def) {
        try {
            return Color.parseColor(o.optString(k));
        } catch (Exception e) {
            return def;
        }
    }

    private GradientDrawable box(int fill, int stroke, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(r));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView pill(String text, int color, int fill, int stroke) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackground(box(fill, stroke, 999));
        t.setPadding(dp(18), dp(12), dp(18), dp(12));
        return t;
    }

    private TextView link(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(12), dp(10), dp(8), dp(10));
        return t;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        JSONObject o;
        try {
            o = new JSONObject(getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE).getString(BatchWidget.KEY, "{}"));
        } catch (Exception e) {
            o = new JSONObject();
        }
        final int bg = col(o, "bg", 0xFF211812), edge = col(o, "edge", 0xFFC98A4B), field = col(o, "bg2", 0xFF17110D);
        final int accent = col(o, "accent", 0xFFF26D21), ink = col(o, "ink", 0xFF2A1407);
        fg = col(o, "fg", 0xFFF8EDE3);
        muted = col(o, "muted", 0xFFB49D8B);
        bad = 0xFFFF8A7A;
        final int soft = edge & 0x66FFFFFF;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(14));
        root.setBackground(box(bg, edge, 24));
        root.setFocusableInTouchMode(true);

        TextView title = new TextView(this);
        title.setText("BATCH / QR KONTROL");
        title.setTextColor(edge);
        title.setTextSize(11);
        title.setLetterSpacing(0.18f);
        root.addView(title);

        head = new TextView(this);
        head.setTextColor(fg);
        head.setTextSize(20);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setPadding(0, dp(4), 0, dp(10));
        root.addView(head);

        // the camera, with a frame to aim at
        camBox = new FrameLayout(this);
        camBox.setBackground(box(Color.BLACK, 0, 18));
        camBox.setClipToOutline(true);
        TextureView tv = new TextureView(this);
        camBox.addView(tv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View aim = new View(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(dp(14));
        ring.setStroke(dp(2), 0xE6FFFFFF);
        aim.setBackground(ring);
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        al.setMargins(dp(34), dp(30), dp(34), dp(30));
        camBox.addView(aim, al);
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(420));
        root.addView(camBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round((width - dp(40)) * 0.72f)));

        camRow = new LinearLayout(this);
        camRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView torch = pill("Fener", fg, field, soft);
        TextView typeBtn = pill("Kodu elle yaz", fg, field, soft);
        camRow.addView(torch, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams yl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f);
        yl.leftMargin = dp(10);
        camRow.addView(typeBtn, yl);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = dp(10);
        root.addView(camRow, cl);

        // typing a batch code
        typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        typeRow.setGravity(Gravity.CENTER_VERTICAL);
        in = new EditText(this);
        in.setHint("Parti kodu, ör. 2K01");
        in.setHintTextColor(muted);
        in.setTextColor(fg);
        in.setTextSize(18);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        in.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14), new InputFilter.AllCaps()});
        in.setImeOptions(EditorInfo.IME_ACTION_DONE);
        in.setBackground(box(field, soft, 14));
        in.setPadding(dp(14), dp(11), dp(14), dp(11));
        typeRow.addView(in, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView go = pill("Test et", ink, accent, 0);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.leftMargin = dp(10);
        typeRow.addView(go, gl);
        root.addView(typeRow);

        scanBtn = pill("QR ya da barkod okut", fg, field, soft);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(10);
        root.addView(scanBtn, sl);

        res = new TextView(this);
        res.setTextColor(muted);
        res.setTextSize(14);
        res.setLineSpacing(dp(3), 1f);
        res.setPadding(0, dp(12), 0, dp(4));
        ScrollView sc = new ScrollView(this);
        sc.addView(res);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        openBtn = link("Sayfayı aç", accent);
        openBtn.setVisibility(View.GONE);
        foot.addView(openBtn);
        TextView full = link("Uygulamada aç", muted);
        foot.addView(full);
        TextView close = link("Kapat", fg);
        foot.addView(close);
        root.addView(foot);

        setContentView(root);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);

        scanner = new CodeScanner(this, tv, this);

        go.setOnClickListener(v -> runTyped());
        in.setOnEditorActionListener((v, id, ev) -> {
            runTyped();
            return true;
        });
        scanBtn.setOnClickListener(v -> showScan());
        typeBtn.setOnClickListener(v -> showType(true));
        torch.setOnClickListener(v -> scanner.toggleTorch());
        close.setOnClickListener(v -> finish());
        openBtn.setOnClickListener(v -> {
            if (url.isEmpty()) return;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
            finish();
        });
        full.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).putExtra("open", "batch").setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });

        applyMode(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        asked = false;
        applyMode(intent);
    }

    private void applyMode(Intent i) {
        if (i != null && SCAN.equals(i.getStringExtra(MODE))) showScan();
        else showType(true);
    }

    private void keyboard(boolean show) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (show) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            in.requestFocus();
            if (imm != null) in.post(() -> imm.showSoftInput(in, InputMethodManager.SHOW_IMPLICIT));
        } else {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            in.clearFocus();
            if (imm != null) imm.hideSoftInputFromWindow(in.getWindowToken(), 0);
        }
    }

    /** Camera on, aiming frame visible. */
    private void showScan() {
        keyboard(false);
        url = "";
        openBtn.setVisibility(View.GONE);
        typeRow.setVisibility(View.GONE);
        scanBtn.setVisibility(View.GONE);
        camBox.setVisibility(View.VISIBLE);
        camRow.setVisibility(View.VISIBLE);
        head.setText("Kodu çerçeveye getir");
        head.setTextColor(fg);
        res.setTextColor(muted);
        res.setText("QR kodu, barkodu ya da kutudaki kare kodu okurum. Küçük basılı parti kodu kamerayla okunmaz; onu “Kodu elle yaz” ile gir.");
        scanning = true;
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            scanner.start();
        } else if (!asked) {
            asked = true;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, ASK_CAMERA);
        } else {
            noCamera();
        }
    }

    private void noCamera() {
        showType(false);
        head.setText("Kamera izni yok");
        head.setTextColor(bad);
        res.setTextColor(fg);
        res.setText("Okutmak için kamera izni gerekiyor: Ayarlar > Uygulamalar > BB Parfüm Envanteri > İzinler > Kamera. Parti kodunu yine de buraya yazabilirsin.");
    }

    /** Typing: the input row and the button that brings the camera back. */
    private void showType(boolean fresh) {
        scanning = false;
        scanner.stop();
        camBox.setVisibility(View.GONE);
        camRow.setVisibility(View.GONE);
        typeRow.setVisibility(View.VISIBLE);
        scanBtn.setVisibility(View.VISIBLE);
        scanBtn.setText("QR ya da barkod okut");
        if (!fresh) return;
        url = "";
        openBtn.setVisibility(View.GONE);
        head.setText("Kodu yaz ya da okut");
        head.setTextColor(fg);
        res.setTextColor(muted);
        res.setText("Parti kodunu yaz: marka seçmene gerek yok, kodun düzeninden hangi gruba ait olduğunu bulurum. QR ya da barkod için “okut”a dokun.");
        keyboard(true);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] grants) {
        if (code != ASK_CAMERA) return;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            if (scanning) scanner.start();
        } else {
            noCamera();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scanning && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanner.start();
    }

    @Override
    protected void onPause() {
        scanner.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        scanner.destroy();
        super.onDestroy();
    }

    @Override
    public void onCode(String text, BarcodeFormat format) {
        boolean product = format == BarcodeFormat.EAN_13 || format == BarcodeFormat.EAN_8 || format == BarcodeFormat.UPC_A;
        CodeJudge.Rep r = CodeJudge.judge(text, product);
        head.performHapticFeedback(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
        showType(false);
        scanBtn.setText("Tekrar okut");
        head.setText(r.head);
        head.setTextColor(r.tone == CodeJudge.BAD ? bad : fg);
        res.setTextColor(fg);
        res.setText(r.body);
        url = r.url;
        openBtn.setVisibility(url.isEmpty() ? View.GONE : View.VISIBLE);
        remember(r.head, r.code, r.kind, r.tone == CodeJudge.GOOD ? "good" : r.tone == CodeJudge.BAD ? "bad" : "warn");
    }

    @Override
    public void onError(String message) {
        showType(false);
        head.setText("Kamera açılamadı");
        head.setTextColor(bad);
        res.setTextColor(fg);
        res.setText(message + " Parti kodunu yine de buraya yazabilirsin.");
    }

    private void runTyped() {
        BatchCodec.Result r = BatchCodec.read(in.getText().toString());
        url = "";
        openBtn.setVisibility(View.GONE);
        head.setText(r.head);
        head.setTextColor(r.ok ? fg : bad);
        res.setText(r.report);
        res.setTextColor(fg);
        if (r.ok) remember(r.head, r.code, r.group, "good");
    }

    /** The widget and the app show the last report. */
    private void remember(String headline, String code, String kind, String cls) {
        try {
            JSONObject w = new JSONObject(getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE).getString(BatchWidget.KEY, "{}"));
            w.put("main", headline).put("sub", code + " · " + kind);
            getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE).edit().putString(BatchWidget.KEY, w.toString())
                    .putString("last_batch", new JSONObject().put("code", code).put("h", headline).put("group", kind).put("cls", cls).put("t", System.currentTimeMillis()).toString()).apply();
            BatchWidget.updateAll(this);
        } catch (Exception ignored) { }
    }
}
