package com.parfumcarki.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Serves notification sounds downloaded at runtime (files/sounds/*.wav), so new sounds can be added
 * from the web side without a new APK. Read-only; only plain file names inside that folder are served.
 */
public class SoundProvider extends ContentProvider {
    static final String AUTHORITY = "com.parfumcarki.app.sounds";

    static File dir(android.content.Context c) {
        File d = new File(c.getFilesDir(), "sounds");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static boolean safeName(String name) {
        return name != null && name.matches("[a-z0-9_]{1,40}");
    }

    static Uri uriFor(String name) {
        return Uri.parse("content://" + AUTHORITY + "/" + name + ".wav");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String seg = uri.getLastPathSegment();
        if (seg == null || !seg.endsWith(".wav") || !safeName(seg.substring(0, seg.length() - 4))) {
            throw new FileNotFoundException("unknown sound");
        }
        File f = new File(dir(getContext()), seg);
        if (!f.exists()) throw new FileNotFoundException(seg);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "audio/wav";
    }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues v) {
        return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] a) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues v, String s, String[] a) {
        return 0;
    }
}
