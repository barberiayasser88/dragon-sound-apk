package com.audifono.propietario;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.core.content.ContextCompat;

public class AudioTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (AudioService.isAudioActive(this)) {
            Intent stopIntent = new Intent(this, AudioService.class);
            stopIntent.setAction(AudioService.ACTION_STOP);
            ContextCompat.startForegroundService(this, stopIntent);
            setTileState(false);
        } else {
            launchActivityToStartAudio();
        }
    }

    private void launchActivityToStartAudio() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(MainActivity.ACTION_START_FROM_TILE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    2001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void updateTileState() {
        setTileState(AudioService.isAudioActive(this));
    }

    private void setTileState(boolean active) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setLabel(getString(active ? R.string.tile_active : R.string.tile_inactive));
        }
        tile.updateTile();
    }

    public static void requestUpdate(android.content.Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(
                    context,
                    new ComponentName(context, AudioTileService.class)
            );
        }
    }
}
