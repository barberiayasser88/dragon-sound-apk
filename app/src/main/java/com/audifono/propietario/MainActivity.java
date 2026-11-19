package com.audifono.propietario;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    public static final String ACTION_START_FROM_TILE =
            "com.audifono.propietario.ACTION_START_FROM_TILE";

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int AUDIO_PERMISSION_REQUEST = 101;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 102;
    private static final String PREFS_NAME = "dragon_sound_audio";
    private static final String PREF_NOTIFICATION_PROMPTED = "notification_prompted";

    private BluetoothManager bluetoothManager;
    private WiFiManager wifiManager;
    private boolean pendingAudioStart;
    private boolean closeAfterTileStart;
    private boolean foregroundReadyReceiverRegistered;
    private SharedPreferences audioPreferences;

    private final BroadcastReceiver foregroundReadyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (closeAfterTileStart) {
                closeAfterTileStart = false;
                unregisterForegroundReadyReceiver();
                finish();
            }
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener audioStateListener =
            (preferences, key) -> {
                if (AudioService.PREF_ACTIVE.equals(key)
                        || AudioService.PREF_LAST_ERROR.equals(key)
                        || AudioService.PREF_NOISE_REDUCTION.equals(key)
                        || AudioService.PREF_NOISE_REDUCTION_ACTIVE.equals(key)) {
                    updateStatus();
                }
            };

    private SeekBar gainSeekBar;
    private SeekBar trebleSeekBar;
    private SeekBar bassSeekBar;
    private Button startButton;
    private Button stopButton;
    private Button noiseReductionButton;
    private Button bluetoothButton;
    private Button wifiButton;
    private TextView gainTextView;
    private TextView statusTextView;
    private Spinner filterSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();
        bluetoothManager = new BluetoothManager(this);
        wifiManager = new WiFiManager(this);
        audioPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        handleStartRequest(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        audioPreferences.registerOnSharedPreferenceChangeListener(audioStateListener);
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onStop() {
        audioPreferences.unregisterOnSharedPreferenceChangeListener(audioStateListener);
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleStartRequest(intent);
    }

    private void initializeUI() {
        gainSeekBar = findViewById(R.id.gainSeekBar);
        trebleSeekBar = findViewById(R.id.trebleSeekBar);
        bassSeekBar = findViewById(R.id.bassSeekBar);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        noiseReductionButton = findViewById(R.id.noiseReductionButton);
        bluetoothButton = findViewById(R.id.bluetoothButton);
        wifiButton = findViewById(R.id.wifiButton);
        gainTextView = findViewById(R.id.gainTextView);
        statusTextView = findViewById(R.id.statusTextView);
        filterSpinner = findViewById(R.id.filterSpinner);

        String[] filters = {"Normal", "Voz (Pasa-banda)", "Grave (Bass)", "Agudo (Treble)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                filters
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(adapter);

        float savedGain = AudioService.getSavedGain(this);
        float savedTreble = AudioService.getSavedTreble(this);
        float savedBass = AudioService.getSavedBass(this);
        gainSeekBar.setProgress(Math.round(savedGain / 15.0f * 100.0f));
        trebleSeekBar.setProgress(Math.round(savedTreble * 100.0f + 50.0f));
        bassSeekBar.setProgress(Math.round(savedBass * 100.0f + 50.0f));
        filterSpinner.setSelection(AudioService.getSavedFilter(this));
        updateGainText(savedGain);

        updateNoiseReductionButton();

        gainSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                float gain = (progress / 100.0f) * 15.0f;
                updateGainText(gain);
                sendAudioSetting(AudioService.ACTION_SET_GAIN, "gain", gain);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        trebleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    sendAudioSetting(
                            AudioService.ACTION_SET_TREBLE,
                            "treble",
                            (progress - 50) / 100.0f
                    );
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        bassSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    sendAudioSetting(
                            AudioService.ACTION_SET_BASS,
                            "bass",
                            (progress - 50) / 100.0f
                    );
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        startButton.setOnClickListener(v -> requestAudioStart(false));
        stopButton.setOnClickListener(v -> stopAudio());
        noiseReductionButton.setOnClickListener(v -> toggleNoiseReduction());
        bluetoothButton.setOnClickListener(v -> requestBluetoothPermissionsOrShowDevices());
        wifiButton.setOnClickListener(v -> wifiManager.mostrarRedes());

        filterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    android.widget.AdapterView<?> parent,
                    android.view.View view,
                    int position,
                    long id
            ) {
                String[] actions = {
                        AudioService.ACTION_MODE_NORMAL,
                        AudioService.ACTION_MODE_VOICE,
                        AudioService.ACTION_MODE_BASS,
                        AudioService.ACTION_MODE_TREBLE
                };
                sendAudioCommand(actions[position]);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void toggleNoiseReduction() {
        boolean enabled = !AudioService.isNoiseReductionEnabled(this);
        Intent intent = new Intent(this, AudioService.class);
        intent.setAction(AudioService.ACTION_SET_NOISE_REDUCTION);
        intent.putExtra("enabled", enabled);
        startService(intent);
        updateNoiseReductionButton();
    }

    private void updateNoiseReductionButton() {
        boolean enabled = AudioService.isNoiseReductionEnabled(this);
        boolean audioActive = AudioService.isAudioActive(this);
        boolean reductionActive = AudioService.isNoiseReductionActive(this);

        String label;
        if (!enabled) {
            label = "Reducción de ruido: DESACTIVADA";
        } else if (!audioActive) {
            label = "Reducción de ruido: ACTIVADA al iniciar";
        } else if (reductionActive) {
            label = "Reducción de ruido: ACTIVA";
        } else {
            label = "Reducción de ruido: NO DISPONIBLE";
        }
        noiseReductionButton.setText(label);
        noiseReductionButton.setContentDescription(label + ". Toca para cambiar.");
    }

    private void updateGainText(float gain) {
        int db = gain > 0.0f ? (int) (20.0f * Math.log10(gain)) : 0;
        gainTextView.setText(String.format("Ganancia: %.1f x (%d dB)", gain, db));
    }

    private void sendAudioSetting(String action, String key, float value) {
        Intent intent = new Intent(this, AudioService.class);
        intent.setAction(action);
        intent.putExtra(key, value);
        startService(intent);
    }

    private void sendAudioCommand(String action) {
        Intent intent = new Intent(this, AudioService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void handleStartRequest(Intent intent) {
        if (intent != null && ACTION_START_FROM_TILE.equals(intent.getAction())) {
            intent.setAction(null);
            requestAudioStart(true);
        }
    }

    private void requestAudioStart(boolean fromTile) {
        pendingAudioStart = true;
        closeAfterTileStart = fromTile;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    AUDIO_PERMISSION_REQUEST
            );
            return;
        }
        requestNotificationPermissionOrStart();
    }

    private void requestNotificationPermissionOrStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            boolean alreadyPrompted = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getBoolean(PREF_NOTIFICATION_PROMPTED, false);
            if (!alreadyPrompted) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_NOTIFICATION_PROMPTED, true)
                        .apply();
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST
                );
                return;
            }
            Toast.makeText(
                    this,
                    "Puedes activar las notificaciones para ver los controles de audio en la pantalla bloqueada.",
                    Toast.LENGTH_LONG
            ).show();
        }
        startAudioService();
    }

    private void startAudioService() {
        if (!pendingAudioStart) {
            return;
        }
        pendingAudioStart = false;
        if (closeAfterTileStart && !foregroundReadyReceiverRegistered) {
            ContextCompat.registerReceiver(
                    this,
                    foregroundReadyReceiver,
                    new IntentFilter(AudioService.ACTION_FOREGROUND_READY),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
            foregroundReadyReceiverRegistered = true;
        }
        Intent serviceIntent = new Intent(this, AudioService.class);
        serviceIntent.setAction(AudioService.ACTION_START);
        ContextCompat.startForegroundService(this, serviceIntent);
        updateStatus();
    }

    private void stopAudio() {
        Intent serviceIntent = new Intent(this, AudioService.class);
        serviceIntent.setAction(AudioService.ACTION_STOP);
        startService(serviceIntent);
        pendingAudioStart = false;
        closeAfterTileStart = false;
        updateStatus();
    }

    private void unregisterForegroundReadyReceiver() {
        if (foregroundReadyReceiverRegistered) {
            unregisterReceiver(foregroundReadyReceiver);
            foregroundReadyReceiverRegistered = false;
        }
    }

    private void requestBluetoothPermissionsOrShowDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissions = {
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            };
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
                    return;
                }
            }
        }
        bluetoothManager.mostrarDispositivosEmparejados();
    }

    private void updateStatus() {
        updateNoiseReductionButton();
        String error = AudioService.consumeLastError(this);
        if (error != null && !error.isEmpty()) {
            statusTextView.setText("Estado: audio detenido");
            statusTextView.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            Toast.makeText(this, "El audio se detuvo: " + error, Toast.LENGTH_LONG).show();
            return;
        }

        boolean active = AudioService.isAudioActive(this);
        statusTextView.setText(active ? "Estado: ACTIVO" : "Estado: DETENIDO");
        statusTextView.setTextColor(getResources().getColor(
                active ? android.R.color.holo_green_dark : android.R.color.holo_red_dark
        ));
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionOrStart();
            } else {
                pendingAudioStart = false;
                closeAfterTileStart = false;
                Toast.makeText(this, "Se necesita el permiso de micrófono para escuchar.", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        this,
                        "El audio puede funcionar, pero Android podría ocultar la notificación de controles en la pantalla bloqueada.",
                        Toast.LENGTH_LONG
                ).show();
            }
            startAudioService();
            return;
        }

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = grantResults.length > 0;
            for (int grant : grantResults) {
                allGranted &= grant == PackageManager.PERMISSION_GRANTED;
            }
            if (allGranted) {
                bluetoothManager.mostrarDispositivosEmparejados();
            } else {
                Toast.makeText(this, "Algunos permisos de Bluetooth fueron denegados.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        unregisterForegroundReadyReceiver();
        super.onDestroy();
    }
}
