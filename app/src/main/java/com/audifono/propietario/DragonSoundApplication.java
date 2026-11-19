package com.audifono.propietario;

import android.app.Application;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

public final class DragonSoundApplication extends Application {
    private static final String TAG = "DragonSoundApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        ReleaseMessagingService.ensureNotificationChannel(this);
        subscribeToReleaseUpdates();
    }

    static void subscribeToReleaseUpdates() {
        FirebaseMessaging.getInstance()
                .subscribeToTopic(ReleaseMessagingService.UPDATE_TOPIC)
                .addOnFailureListener(error ->
                        Log.e(TAG, "Could not subscribe to release updates", error));
    }
}
