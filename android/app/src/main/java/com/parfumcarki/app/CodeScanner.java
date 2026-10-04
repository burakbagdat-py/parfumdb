package com.parfumcarki.app;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.TextureView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Camera preview in a TextureView plus QR / barcode reading, entirely on the phone (ZXing, no network, no Play services).
 * Every camera call runs on one background thread, so opening and releasing can never overlap.
 */
@SuppressWarnings("deprecation")
final class CodeScanner implements TextureView.SurfaceTextureListener, Camera.PreviewCallback {
    interface Listener {
        void onCode(String text, BarcodeFormat format);

        void onError(String message);
    }

    private final Activity act;
    private final TextureView view;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("code-scan");
    private final Handler bg;
    private final MultiFormatReader reader = new MultiFormatReader();
    private Camera cam;
    private int pw, ph, turn;
    private byte[] rot;
    private boolean flip, torch;
    private volatile boolean running, found;

    CodeScanner(Activity a, TextureView v, Listener l) {
        act = a;
        view = v;
        listener = l;
        thread.start();
        bg = new Handler(thread.getLooper());
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.CODE_128));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);
        v.setSurfaceTextureListener(this);
    }

    void start() {
        if (running) return;
        running = true;
        found = false;
        if (view.isAvailable()) {
            final SurfaceTexture st = view.getSurfaceTexture();
            bg.post(() -> open(st));
        }
    }

    void stop() {
        running = false;
        bg.post(this::release);
    }

    void destroy() {
        stop();
        thread.quitSafely();
    }

    void toggleTorch() {
        bg.post(() -> {
            if (cam == null) return;
            try {
                Camera.Parameters p = cam.getParameters();
                List<String> modes = p.getSupportedFlashModes();
                if (modes == null || !modes.contains(Camera.Parameters.FLASH_MODE_TORCH)) return;
                torch = !torch;
                p.setFlashMode(torch ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
                cam.setParameters(p);
            } catch (Exception ignored) { }
        });
    }

    private void open(SurfaceTexture st) {
        if (!running || cam != null) return;
        try {
            Camera.CameraInfo info = new Camera.CameraInfo();
            int id = 0;
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    id = i;
                    break;
                }
            }
            Camera.getCameraInfo(id, info);
            cam = Camera.open(id);
            Camera.Parameters p = cam.getParameters();
            Camera.Size best = null;
            for (Camera.Size s : p.getSupportedPreviewSizes()) {
                if (s.width > 1280 || s.height > 720) continue;
                if (best == null || s.width * s.height > best.width * best.height) best = s;
            }
            if (best == null) best = p.getPreviewSize();
            p.setPreviewSize(best.width, best.height);
            List<String> focus = p.getSupportedFocusModes();
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            else if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_AUTO)) p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            cam.setParameters(p);
            pw = best.width;
            ph = best.height;
            int degrees = act.getWindowManager().getDefaultDisplay().getRotation() * 90;
            turn = (info.orientation - degrees + 360) % 360;
            cam.setDisplayOrientation(turn);
            cam.setPreviewTexture(st);
            cam.addCallbackBuffer(new byte[pw * ph * 3 / 2]);
            cam.setPreviewCallbackWithBuffer(this);
            cam.startPreview();
            ui.post(this::fit);
        } catch (Exception e) {
            release();
            ui.post(() -> {
                if (running) listener.onError("Kamera açılamadı. Başka bir uygulama kamerayı kullanıyor olabilir.");
            });
        }
    }

    private void release() {
        Camera c = cam;
        cam = null;
        torch = false;
        if (c == null) return;
        try {
            c.setPreviewCallbackWithBuffer(null);
            c.stopPreview();
        } catch (Exception ignored) { }
        try {
            c.release();
        } catch (Exception ignored) { }
    }

    /** The view is wider than the upright camera picture: fill it and crop what does not fit instead of stretching. */
    private void fit() {
        int vw = view.getWidth(), vh = view.getHeight();
        if (vw == 0 || vh == 0 || pw == 0) return;
        boolean side = turn % 180 != 0;
        float cw = side ? ph : pw, ch = side ? pw : ph;
        float ca = cw / ch, va = (float) vw / vh, sx = 1, sy = 1;
        if (ca > va) sx = ca / va;
        else sy = va / ca;
        Matrix m = new Matrix();
        m.setScale(sx, sy, vw / 2f, vh / 2f);
        view.setTransform(m);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera c) {
        if (!running || found || data == null) return;
        Result r = decode(data);
        if (r != null && r.getText() != null) {
            found = true;
            final String text = r.getText();
            final BarcodeFormat format = r.getBarcodeFormat();
            ui.post(() -> {
                if (running) listener.onCode(text, format);
            });
            return;
        }
        if (cam != null) c.addCallbackBuffer(data);
    }

    /** One frame as it comes, the next one turned a quarter: a product barcode is only readable along its bars. */
    private Result decode(byte[] data) {
        flip = !flip;
        try {
            LuminanceSource src;
            if (flip) {
                if (rot == null || rot.length != pw * ph) rot = new byte[pw * ph];
                for (int y = 0; y < ph; y++) {
                    int row = y * pw;
                    for (int x = 0; x < pw; x++) rot[x * ph + (ph - 1 - y)] = data[row + x];
                }
                src = new PlanarYUVLuminanceSource(rot, ph, pw, 0, 0, ph, pw, false);
            } else {
                src = new PlanarYUVLuminanceSource(data, pw, ph, 0, 0, pw, ph, false);
            }
            return reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(src)));
        } catch (Exception e) {
            return null;
        } finally {
            reader.reset();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        if (running) bg.post(() -> open(st));
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
        fit();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        bg.post(this::release);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) { }
}
