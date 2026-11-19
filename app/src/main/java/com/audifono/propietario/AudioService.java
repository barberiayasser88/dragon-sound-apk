package com.audifono.propietario;

import android.media.AudioAttributes;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.NoiseSuppressor;
import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class AudioService extends Service {
    public static final String ACTION_START = "com.audifono.propietario.ACTION_START";
    public static final String ACTION_STOP = "com.audifono.propietario.ACTION_STOP";
    public static final String ACTION_FOREGROUND_READY =
            "com.audifono.propietario.ACTION_FOREGROUND_READY";
    public static final String ACTION_MODE_NORMAL = "com.audifono.propietario.ACTION_MODE_NORMAL";
    public static final String ACTION_MODE_VOICE = "com.audifono.propietario.ACTION_MODE_VOICE";
    public static final String ACTION_MODE_BASS = "com.audifono.propietario.ACTION_MODE_BASS";
    public static final String ACTION_MODE_TREBLE = "com.audifono.propietario.ACTION_MODE_TREBLE";
    public static final String ACTION_SET_GAIN = "com.audifono.propietario.ACTION_SET_GAIN";
    public static final String ACTION_SET_TREBLE = "com.audifono.propietario.ACTION_SET_TREBLE";
    public static final String ACTION_SET_NOISE_REDUCTION = "com.audifono.propietario.ACTION_SET_NOISE_REDUCTION";
    public static final String ACTION_SET_BASS = "com.audifono.propietario.ACTION_SET_BASS";

    private static final String TAG = "DragonSoundAudio";
    private static final String CHANNEL_ID = "dragon_sound_foreground";
    private static final int NOTIFICATION_ID = 1001;
    private static final int ERROR_NOTIFICATION_ID = 1002;
    private static final String PREFS_NAME = "dragon_sound_audio";
    public static final String PREF_ACTIVE = "audio_active";
    private static final String PREF_GAIN = "gain";
    private static final String PREF_TREBLE = "treble";
    private static final String PREF_BASS = "bass";
    private static final String PREF_FILTER = "filter";
    public static final String PREF_NOISE_REDUCTION = "noise_reduction";
    public static final String PREF_NOISE_REDUCTION_ACTIVE = "noise_reduction_active";
    public static final String PREF_LAST_ERROR = "last_error";
    public static final String PREF_BLUETOOTH_ROUTE = "bluetooth_route";
    public static final String BLUETOOTH_ROUTE_NONE = "none";
    public static final String BLUETOOTH_ROUTE_LOW_LATENCY = "low_latency";
    public static final String BLUETOOTH_ROUTE_STANDARD = "standard";
    

    private static final int SAMPLE_RATE = 44100;
    private static final float VOICE_HIGHPASS_ALPHA = (float) (SAMPLE_RATE / (SAMPLE_RATE + 2.0 * Math.PI * 250.0));
    private static final float VOICE_LOWPASS_ALPHA = (float) ((2.0 * Math.PI * 3400.0) / (SAMPLE_RATE + 2.0 * Math.PI * 3400.0));
    private static final float SAFETY_THRESHOLD = 0.50f;
    private static final float COMPRESSION_RATIO = 12.0f;

    private final Object noiseSuppressorLock = new Object();
    private NoiseSuppressor noiseSuppressor;
    private volatile boolean noiseReductionEnabled = true;
    private float previousVoiceInput;
    private float previousVoiceHighPass;
    private float previousVoiceLowPass;
    private volatile boolean isRunning;
    private volatile boolean stopRequested;
    private volatile float factorGanancia = 10.0f;
    private volatile float trebleGain;
    private volatile float bassGain;
    private volatile int filtroActual;

    private volatile AudioRecord recorder;
    private volatile AudioTrack player;
    private volatile Thread audioThread;
    private PowerManager.WakeLock wakeLock;
    private AudioManager communicationAudioManager;
    private AudioDeviceInfo previousCommunicationDevice;
    private int previousAudioMode = AudioManager.MODE_NORMAL;
    private boolean communicationRouteConfigured;
    private volatile boolean bluetoothCommunicationRouteActive;
    
    

    public static boolean isAudioActive(Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_ACTIVE, false);
    }

    public static float getSavedGain(Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getFloat(PREF_GAIN, 10.0f);
    }

    public static float getSavedTreble(Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getFloat(PREF_TREBLE, 0.0f);
    }

    public static float getSavedBass(Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getFloat(PREF_BASS, 0.0f);
    }

    public static int getSavedFilter(Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(PREF_FILTER, 1);
    }

    public static boolean isNoiseReductionEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_NOISE_REDUCTION, true);
    }

    public static boolean isNoiseReductionActive(Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_NOISE_REDUCTION_ACTIVE, false);
    }

    public static String consumeLastError(Context context) {
        String error = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(PREF_LAST_ERROR, null);
        if (error != null) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .remove(PREF_LAST_ERROR)
                    .apply();
        }
        return error;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        factorGanancia = getSavedGain(this);
        trebleGain = getSavedTreble(this);
        bassGain = getSavedBass(this);
        noiseReductionEnabled = isNoiseReductionEnabled(this);
        filtroActual = getSavedFilter(this);
        updateNoiseReductionActiveState(false);
        updateActiveState(false);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_START.equals(action)) {
            if (!hasMicrophonePermission()) {
                handleAudioFailure(new SecurityException("Falta el permiso de micrófono."));
                return START_NOT_STICKY;
            }
            if (!isRunning) {
                stopRequested = false;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(PREF_LAST_ERROR).apply();
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.cancel(ERROR_NOTIFICATION_ID);
                }
                startForegroundWithNotification();
                startAudio();
            }
        } else if (ACTION_STOP.equals(action)) {
            stopRequested = true;
            stopAudio();
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(PREF_LAST_ERROR).apply();
            stopForeground(true);
            stopSelf(startId);
        } else if (ACTION_MODE_NORMAL.equals(action)) {
            setFiltro(0);
        } else if (ACTION_MODE_VOICE.equals(action)) {
            setFiltro(1);
        } else if (ACTION_MODE_BASS.equals(action)) {
            setFiltro(2);
        } else if (ACTION_MODE_TREBLE.equals(action)) {
            setFiltro(3);
        } else if (ACTION_SET_GAIN.equals(action)) {
            setGanancia(intent.getFloatExtra("gain", factorGanancia));
        } else if (ACTION_SET_TREBLE.equals(action)) {
            setTreble(intent.getFloatExtra("treble", trebleGain));
        } else if (ACTION_SET_NOISE_REDUCTION.equals(action)) {
            setNoiseReduction(intent.getBooleanExtra("enabled", !noiseReductionEnabled));
        } else if (ACTION_SET_BASS.equals(action)) {
            setBass(intent.getFloatExtra("bass", bassGain));
        } else {
            Log.w(TAG, "Se ignoró una orden sin acción reconocida.");
            stopSelf(startId);
        }

        if (isRunning) {
            updateForegroundNotification();
        } else if (!ACTION_START.equals(action) && !ACTION_STOP.equals(action)) {
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    private boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Dragon Sound",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Estado y controles del audio en segundo plano");
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String contentText, boolean ongoing) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Dragon Sound")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_sound_tile)
                .setContentIntent(openPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing);

        if (ongoing) {
            Intent stopIntent = new Intent(this, AudioService.class).setAction(ACTION_STOP);
            PendingIntent stopPendingIntent = PendingIntent.getService(
                    this,
                    5,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(android.R.drawable.ic_media_pause, "Detener", stopPendingIntent);
        }
        return builder.build();
    }

    private void startForegroundWithNotification() {
        Notification notification = buildNotification("Iniciando escucha…", true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        Intent readyIntent = new Intent(ACTION_FOREGROUND_READY);
        readyIntent.setPackage(getPackageName());
        sendBroadcast(readyIntent);
    }

    private void updateForegroundNotification() {
        Notification notification = buildNotification(
                getModeName() + " · Escucha activa",
                true
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private String getModeName() {
        switch (filtroActual) {
            case 1:
                return "Modo Voz";
            case 2:
                return "Modo Bass";
            case 3:
                return "Modo Treble";
            default:
                return "Modo Normal";
        }
    }

    private void startAudio() {
        if (isRunning) {
            return;
        }
        Thread previousThread = audioThread;
        if (previousThread != null && previousThread.isAlive()) {
            Log.w(TAG, "No se inició el audio porque el cierre anterior aún está en curso.");
            return;
        }

        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "DragonSound:AudioProcessing"
                );
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (RuntimeException e) {
            handleAudioFailure(e);
            return;
        }

        isRunning = true;
        audioThread = new Thread(this::runAudioLoop, "DragonSoundAudio");
        audioThread.start();
    }

    private void runAudioLoop() {
        AudioRecord localRecorder = null;
        AudioTrack localPlayer = null;
        Exception failure = null;

        try {
            int minimumBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            if (minimumBufferSize <= 0) {
                throw new IllegalStateException("El dispositivo no admite la captura de audio solicitada.");
            }
            int minimumTrackBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            if (minimumTrackBufferSize <= 0) {
                throw new IllegalStateException("El dispositivo no admite la reproducción de audio solicitada.");
            }
            int requestedBufferSize = SAMPLE_RATE / 100 * 2;
            int bufferSize = Math.max(
                    Math.max(minimumBufferSize, minimumTrackBufferSize),
                    requestedBufferSize
            );

            localRecorder = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );

            boolean bluetoothVoiceRoute = configureBluetoothVoiceRoute();
            localPlayer = createAudioTrack(bufferSize, bluetoothVoiceRoute);
            selectBuiltInMicrophone(localRecorder);

            if (!isRunning) {
                return;
            }

            recorder = localRecorder;
            player = localPlayer;
            
            applyNoiseReduction(localRecorder);
            localRecorder.startRecording();
            
            verifyBuiltInMicrophoneRoute(localRecorder);
            localPlayer.play();
            updateActiveState(true);
            updateForegroundNotification();

            int sampleCapacity = SAMPLE_RATE / 100;
            short[] buffer = new short[sampleCapacity];
            float[] floatBuffer = new float[sampleCapacity];

            while (isRunning) {
                int read = localRecorder.read(buffer, 0, buffer.length);
                if (!isRunning) {
                    break;
                }
                if (read < 0) {
                    throw new IllegalStateException("La captura de audio se interrumpió: " + read);
                }
                if (read == 0) {
                    continue;
                }

                for (int i = 0; i < read; i++) {
                    floatBuffer[i] = buffer[i] / 32768.0f;
                }

                aplicarFiltro(floatBuffer, read);

                float gain = factorGanancia;
                for (int i = 0; i < read; i++) {
                    floatBuffer[i] *= gain;
                    if (floatBuffer[i] > SAFETY_THRESHOLD) {
                        floatBuffer[i] = SAFETY_THRESHOLD
                                + (floatBuffer[i] - SAFETY_THRESHOLD) / COMPRESSION_RATIO;
                    } else if (floatBuffer[i] < -SAFETY_THRESHOLD) {
                        floatBuffer[i] = -SAFETY_THRESHOLD
                                + (floatBuffer[i] + SAFETY_THRESHOLD) / COMPRESSION_RATIO;
                    }
                    floatBuffer[i] = Math.max(-1.0f, Math.min(1.0f, floatBuffer[i]));
                    buffer[i] = (short) (floatBuffer[i] * 32767.0f);
                }

                int written = localPlayer.write(buffer, 0, read);
                if (written < 0 && isRunning) {
                    throw new IllegalStateException("La reproducción de audio se interrumpió: " + written);
                }
            }
        } catch (Exception e) {
            if (isRunning) {
                failure = e;
            }
        } finally {
            isRunning = false;
            if (recorder == localRecorder) {
                recorder = null;
            }
            if (player == localPlayer) {
                player = null;
            }
            releaseNoiseSuppressor();
            restoreCommunicationRoute();
            updateBluetoothRouteStatus(BLUETOOTH_ROUTE_NONE);
            releaseAudioResources(localRecorder, localPlayer);
            releaseWakeLock();
            updateActiveState(false);

            if (failure != null) {
                handleAudioFailure(failure);
            } else if (!stopRequested) {
                Log.e(TAG, "El procesamiento de audio terminó inesperadamente.");
                stopForeground(true);
                stopSelf();
            }
        }
    }

    private void selectBuiltInMicrophone(AudioRecord audioRecorder) {
        AudioManager audioManager = getSystemService(AudioManager.class);
        if (audioManager == null) {
            throw new IllegalStateException("No se pudo acceder al audio del teléfono.");
        }

        for (AudioDeviceInfo inputDevice : audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (inputDevice.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                if (!audioRecorder.setPreferredDevice(inputDevice)) {
                    throw new IllegalStateException(
                            "Android no pudo seleccionar el micrófono integrado del teléfono."
                    );
                }
                return;
            }
        }
        throw new IllegalStateException("No se encontró el micrófono integrado del teléfono.");
    }

    private void verifyBuiltInMicrophoneRoute(AudioRecord audioRecorder) {
        AudioDeviceInfo routedDevice = audioRecorder.getRoutedDevice();
        if (routedDevice != null && routedDevice.getType() != AudioDeviceInfo.TYPE_BUILTIN_MIC) {
            throw new IllegalStateException(
                    "Android no pudo enrutar la entrada al micrófono integrado del teléfono."
            );
        }
    }

    private AudioTrack createAudioTrack(int bufferSize, boolean bluetoothVoiceRoute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioTrack lowLatencyPlayer = null;
            try {
                lowLatencyPlayer = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(bluetoothVoiceRoute ? AudioAttributes.USAGE_VOICE_COMMUNICATION : AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build())
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        .build();
                if (lowLatencyPlayer.getState() == AudioTrack.STATE_INITIALIZED) {
                    return lowLatencyPlayer;
                }
                lowLatencyPlayer.release();
                Log.w(TAG, "AudioTrack de baja latencia no se inicializó; se usará la ruta estándar.");
            } catch (IllegalArgumentException | IllegalStateException e) {
                if (lowLatencyPlayer != null) {
                    lowLatencyPlayer.release();
                }
                Log.w(TAG, "AudioTrack de baja latencia no está disponible; se usará la ruta estándar.", e);
            }
        }

        return new AudioTrack(
                bluetoothVoiceRoute ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
        );
    }

    private void releaseAudioResources(AudioRecord localRecorder, AudioTrack localPlayer) {
        if (localRecorder != null) {
            try {
                if (localRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    localRecorder.stop();
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "No se pudo detener AudioRecord durante la limpieza.", e);
            }
            localRecorder.release();
        }
        if (localPlayer != null) {
            try {
                if (localPlayer.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    localPlayer.stop();
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "No se pudo detener AudioTrack durante la limpieza.", e);
            }
            localPlayer.release();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null) {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
            wakeLock = null;
        }
    }

    private void aplicarFiltro(float[] buffer, int samples) {
        switch (filtroActual) {
            case 1:
                filtroVoz(buffer, samples);
                break;
            case 2:
                filtroGrave(buffer, samples);
                break;
            case 3:
                filtroAgudo(buffer, samples);
                break;
            default:
                break;
        }
    }

    private void filtroVoz(float[] buffer, int samples) {
        for (int i = 0; i < samples; i++) {
            float input = buffer[i];
            float highPass = VOICE_HIGHPASS_ALPHA
                    * (previousVoiceHighPass + input - previousVoiceInput);
            previousVoiceInput = input;
            previousVoiceHighPass = highPass;
            previousVoiceLowPass += VOICE_LOWPASS_ALPHA * (highPass - previousVoiceLowPass);
            buffer[i] = previousVoiceLowPass;
        }
    }

    private void filtroGrave(float[] buffer, int samples) {
        float boost = 1.0f + bassGain;
        for (int i = 0; i < samples; i++) {
            buffer[i] *= boost;
        }
    }

    private void filtroAgudo(float[] buffer, int samples) {
        float boost = 1.0f + trebleGain;
        for (int i = 1; i < samples; i++) {
            buffer[i] = buffer[i] + (buffer[i] - buffer[i - 1]) * boost;
        }
    }

    private void stopAudio() {
        isRunning = false;
        AudioRecord activeRecorder = recorder;
        if (activeRecorder != null) {
            try {
                if (activeRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    activeRecorder.stop();
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "AudioRecord ya se había detenido.", e);
            }
        }
        AudioTrack activePlayer = player;
        if (activePlayer != null) {
            try {
                if (activePlayer.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    activePlayer.stop();
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "AudioTrack ya se había detenido.", e);
            }
        }

        Thread thread = audioThread;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Se interrumpió la espera de cierre del audio.", e);
            }
        }
        releaseWakeLock();
        updateActiveState(false);
    }

    private void setNoiseReduction(boolean enabled) {
        noiseReductionEnabled = enabled;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(PREF_NOISE_REDUCTION, enabled)
                .apply();

        AudioRecord activeRecorder = recorder;
        if (activeRecorder != null) {
            applyNoiseReduction(activeRecorder);
        } else {
            updateNoiseReductionActiveState(false);
        }
    }

    private void applyNoiseReduction(AudioRecord audioRecorder) {
        synchronized (noiseSuppressorLock) {
            if (noiseSuppressor != null) {
                noiseSuppressor.release();
                noiseSuppressor = null;
            }
            if (!noiseReductionEnabled) {
                updateNoiseReductionActiveState(false);
                return;
            }
            if (!NoiseSuppressor.isAvailable()) {
                Log.w(TAG, "NoiseSuppressor no está disponible en este dispositivo.");
                updateNoiseReductionActiveState(false);
                return;
            }

            NoiseSuppressor effect = null;
            try {
                effect = NoiseSuppressor.create(audioRecorder.getAudioSessionId());
                if (effect == null || effect.setEnabled(true) != AudioEffect.SUCCESS) {
                    if (effect != null) {
                        effect.release();
                    }
                    Log.w(TAG, "Android no pudo activar NoiseSuppressor.");
                    updateNoiseReductionActiveState(false);
                    return;
                }
                noiseSuppressor = effect;
                updateNoiseReductionActiveState(true);
            } catch (RuntimeException e) {
                if (effect != null) {
                    effect.release();
                }
                Log.e(TAG, "No se pudo activar NoiseSuppressor.", e);
                updateNoiseReductionActiveState(false);
            }
        }
    }

    private void releaseNoiseSuppressor() {
        synchronized (noiseSuppressorLock) {
            if (noiseSuppressor != null) {
                noiseSuppressor.release();
                noiseSuppressor = null;
            }
        }
        updateNoiseReductionActiveState(false);
    }

    private void updateNoiseReductionActiveState(boolean active) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(PREF_NOISE_REDUCTION_ACTIVE, active)
                .apply();
    }

    private void setGanancia(float gain) {
        factorGanancia = Math.max(0.0f, Math.min(15.0f, gain));
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putFloat(PREF_GAIN, factorGanancia).apply();
    }

    private void setTreble(float treble) {
        trebleGain = treble;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putFloat(PREF_TREBLE, trebleGain).apply();
    }

    private void setBass(float bass) {
        bassGain = bass;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putFloat(PREF_BASS, bassGain).apply();
    }

    private void setFiltro(int filter) {
        filtroActual = Math.max(0, Math.min(3, filter));
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(PREF_FILTER, filtroActual).apply();
    }

    private void updateActiveState(boolean active) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_ACTIVE, active)
                .apply();
        requestTileUpdate();
    }

    private void requestTileUpdate() {
        AudioTileService.requestUpdate(this);
    }

    private void handleAudioFailure(Exception error) {
        Log.e(TAG, "Se detuvo el procesamiento de audio.", error);
        isRunning = false;
        updateActiveState(false);
        String message = error.getLocalizedMessage();
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_ERROR, message == null ? error.getClass().getSimpleName() : message)
                .apply();

        stopForeground(true);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(
                    ERROR_NOTIFICATION_ID,
                    buildNotification("El audio se detuvo. Toca para revisar.", false)
            );
        }
        releaseWakeLock();
        stopSelf();
    }


    private void updateBluetoothRouteStatus(String status) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(PREF_BLUETOOTH_ROUTE, status)
                .apply();
    }

    public static String getBluetoothRouteStatus(Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(PREF_BLUETOOTH_ROUTE, BLUETOOTH_ROUTE_NONE);
    }

    private boolean configureBluetoothVoiceRoute() {
        AudioManager manager = getSystemService(AudioManager.class);
        if (manager == null) {
            updateBluetoothRouteStatus(BLUETOOTH_ROUTE_STANDARD);
            return false;
        }

        boolean bluetoothOutputConnected = false;
        try {
            for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        && type == AudioDeviceInfo.TYPE_BLE_HEADSET)) {
                    bluetoothOutputConnected = true;
                    break;
                }
            }
        } catch (SecurityException e) {
            updateBluetoothRouteStatus(BLUETOOTH_ROUTE_STANDARD);
            Log.w(TAG, "No se pudo detectar la salida Bluetooth.", e);
            return false;
        }

        if (!bluetoothOutputConnected) {
            updateBluetoothRouteStatus(BLUETOOTH_ROUTE_NONE);
            return false;
        }

        updateBluetoothRouteStatus(BLUETOOTH_ROUTE_STANDARD);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.w(TAG, "La ruta Bluetooth de baja latencia requiere Android 12 o posterior.");
            return false;
        }
        if (manager.getMode() == AudioManager.MODE_IN_CALL) {
            Log.w(TAG, "No se cambio la ruta Bluetooth durante una llamada del sistema.");
            return false;
        }

        AudioDeviceInfo bluetoothDevice = null;
        try {
            for (AudioDeviceInfo device : manager.getAvailableCommunicationDevices()) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                    bluetoothDevice = device;
                    break;
                }
            }
        } catch (SecurityException | IllegalStateException e) {
            Log.w(TAG, "No se pudieron consultar las rutas Bluetooth de comunicacion.", e);
            return false;
        }

        if (bluetoothDevice == null) {
            Log.w(TAG, "No hay una ruta Bluetooth de llamada disponible; se usa la ruta normal.");
            return false;
        }

        previousAudioMode = manager.getMode();
        previousCommunicationDevice = manager.getCommunicationDevice();
        communicationAudioManager = manager;
        communicationRouteConfigured = true;
        try {
            manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            if (!manager.setCommunicationDevice(bluetoothDevice)) {
                restoreCommunicationRoute();
                updateBluetoothRouteStatus(BLUETOOTH_ROUTE_STANDARD);
                Log.w(TAG, "Android rechazo la ruta Bluetooth de comunicacion.");
                return false;
            }
            bluetoothCommunicationRouteActive = true;
            updateBluetoothRouteStatus(BLUETOOTH_ROUTE_LOW_LATENCY);
            return true;
        } catch (SecurityException | IllegalStateException e) {
            restoreCommunicationRoute();
            updateBluetoothRouteStatus(BLUETOOTH_ROUTE_STANDARD);
            Log.w(TAG, "No se pudo activar la ruta Bluetooth de baja latencia.", e);
            return false;
        }
    }

    private void restoreCommunicationRoute() {
        AudioManager manager = communicationAudioManager;
        if (!communicationRouteConfigured || manager == null) {
            return;
        }

        try {
            manager.clearCommunicationDevice();
            if (previousCommunicationDevice != null
                    && !manager.setCommunicationDevice(previousCommunicationDevice)) {
                Log.w(TAG, "No se pudo restaurar la ruta de audio anterior.");
            }
            manager.setMode(previousAudioMode);
        } catch (SecurityException | IllegalStateException e) {
            Log.w(TAG, "No se pudo restaurar el modo de audio anterior.", e);
        } finally {
            communicationRouteConfigured = false;
            communicationAudioManager = null;
            previousCommunicationDevice = null;
            bluetoothCommunicationRouteActive = false;
        }
    }

    @Override
    public void onDestroy() {
        stopRequested = true;
        stopAudio();
        stopForeground(true);
        super.onDestroy();
    }
}
