package com.parfumcarki.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** "Batch / QR kontrol" in the pull-down quick settings: opens the small window with the camera already reading, over whatever is on screen. */
public class BatchTileService extends TileService {
    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle("QR ve barkod okut");
        t.updateTile();
    }

    @Override
    public void onClick() {
        if (isLocked()) unlockAndRun(this::open);
        else open();
    }

    @SuppressWarnings("deprecation")
    private void open() {
        Intent i = new Intent(this, BatchDialogActivity.class).setAction("com.parfumcarki.app.SCAN").putExtra(BatchDialogActivity.MODE, BatchDialogActivity.SCAN)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 43, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(i);
        }
    }
}
