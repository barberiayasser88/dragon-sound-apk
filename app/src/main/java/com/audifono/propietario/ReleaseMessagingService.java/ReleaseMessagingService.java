package com.audifono.propietario;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.List;

public final class ReleaseMessagingService extends FirebaseMessagingService {
    static final String UPDATE_TOPIC = "dragon-sound-updates";
    static final String EXTRA_RELEASE_URL = "com.audifono.propietario.extra.RELEASE_URL";
    static final String ACTION_OPEN_RELEASE = "com.audifono.propietario.OPEN_RELEASE";

    private static final String TAG = "ReleaseMessaging";
    private static final int UPDATE_NOTIFICATION_ID = 1001;

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        DragonSoundApplication.subscribeToReleaseUpdates();
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        String releaseUrl = message.getData().get("release_url");
        if (!isValidReleaseUrl(releaseUrl)) {
            Log.w(TAG, "Ignoring update notification with invalid release URL");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Update notification permission is not granted");
            return;
        }

        ensureNotificationChannel(this);

        RemoteMessage.Notification remoteNotification = message.getNotification();
        String title = remoteNotification != null ? remoteNotification.getTitle() : null;
        String body = remoteNotification != null ? remoteNotification.getBody() : null;
        if (title == null || title.isEmpty()) {
            title = message.getData().get("title");
        }
        if (body == null || body.isEmpty()) {
            body = message.getData().get("body");
        }
        if (title == null || title.isEmpty()) {
            title = "Nueva versión de Dragon Sound";
        }
        if (body == null || body.isEmpty()) {
            body = "Toca para ver la descarga.";
        }

        Intent openIntent = new Intent(this, OpenReleaseActivity.class);
        openIntent.setAction(ACTION_OPEN_RELEASE);
        openIntent.putExtra(EXTRA_RELEASE_URL, releaseUrl);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                UPDATE_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder notification = new NotificationCompat.Builder(
                this,
                getString(R.string.updates_channel_id)
        )
                .setSmallIcon(R.drawable.ic_sound_tile)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        NotificationManagerCompat.from(this).notify(UPDATE_NOTIFICATION_ID, notification.build());
    }

    static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            Log.e(TAG, "Notification manager is unavailable");
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                context.getString(R.string.updates_channel_id),
                context.getString(R.string.updates_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(context.getString(R.string.updates_channel_description));
        manager.createNotificationChannel(channel);
    }

    static boolean isValidReleaseUrl(String value) {
        if (value == null || value.length() > 2048) {
            return false;
        }

        Uri uri = Uri.parse(value);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"github.com".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1
                || uri.getUserInfo() != null) {
            return false;
        }

        List<String> segments = uri.getPathSegments();
        if (segments.size() == 4) {
            return "barberiayasser88".equals(segments.get(0))
                    && "dragon-sound-apk".equals(segments.get(1))
                    && "releases".equals(segments.get(2))
                    && "latest".equals(segments.get(3));
        }

        return segments.size() == 5
                && "barberiayasser88".equals(segments.get(0))
                && "dragon-sound-apk".equals(segments.get(1))
                && "releases".equals(segments.get(2))
                && "tag".equals(segments.get(3))
                && !segments.get(4).isEmpty();
    }
}
