package com.audifono.propietario;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public final class OpenReleaseActivity extends Activity {
    private static final String TAG = "OpenReleaseActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String releaseUrl = getIntent().getStringExtra(ReleaseMessagingService.EXTRA_RELEASE_URL);
        if (releaseUrl == null) {
            releaseUrl = getIntent().getStringExtra("release_url");
        }
        if (ReleaseMessagingService.isValidReleaseUrl(releaseUrl)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)));
            } catch (ActivityNotFoundException error) {
                Log.e(TAG, "No browser is available to open the release page", error);
                Toast.makeText(this, "No se pudo abrir la página de descarga.", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.w(TAG, "Ignoring notification with invalid release URL");
        }

        finish();
    }
}
