package com.example.fakegeigercounter;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class DetectorActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 1;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private TextView tvRadiationValue;
    private TextView tvRadiationLevel;
    private TextView tvConnectionStatus;
    private Button btnStart;
    private RelativeLayout bg;

    private final Handler handler = new Handler();
    private GeigerClickPlayer geigerPlayer;
    private RadiationCalculator radiationCalculator = new RadiationCalculator();

    private boolean isGeigerActive = false;
    private String deviceAddress;

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> {
                    tvConnectionStatus.setText(isGeigerActive ? "Contatore attivo" : "Connesso (in pausa)");
                    if (isGeigerActive) {
                        startRssiUpdates();
                    }
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(() -> {
                    tvConnectionStatus.setText("Disconnesso");
                    stopGeigerCounter();
                });
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                radiationCalculator.updateRssi(rssi);
                updateRadiationLevel();
            }
        }
    };

    private final Runnable rssiUpdater = new Runnable() {
        @Override
        public void run() {
            if (bluetoothGatt != null && isGeigerActive) {
                if (ActivityCompat.checkSelfPermission(DetectorActivity.this,
                        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothGatt.readRemoteRssi();
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detector);

        // Inizializzazione UI
        initViews();

        // Ottieni indirizzo dispositivo dal Intent
        deviceAddress = getIntent().getStringExtra("DEVICE_ADDRESS");

        // Inizializza Bluetooth
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Inizializza SoundPool
        geigerPlayer = new GeigerClickPlayer(this);

        // Verifica permessi e connetti
        if (checkPermissions()) {
            connectToDevice();
        }
    }

    private void initViews() {
        tvRadiationValue = findViewById(R.id.tv_radiation_value);
        tvRadiationLevel = findViewById(R.id.tv_radiation_level);
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        btnStart = findViewById(R.id.btn_start);
        bg = findViewById(R.id.det_act);

        btnStart.setOnClickListener(v -> toggleGeigerCounter());
    }

    public void toggleGeigerCounter() {
        isGeigerActive = !isGeigerActive;

        if (isGeigerActive) {
            startGeigerCounter();
        } else {
            stopGeigerCounter();
        }
        updateButtonState();
    }

    private void startGeigerCounter() {
        if (bluetoothGatt == null) {
            connectToDevice();
            return;
        }

        startRssiUpdates();
        geigerPlayer.setEnabled(true);
        tvConnectionStatus.setText("Contatore attivo");
    }

    private void stopGeigerCounter() {
        handler.removeCallbacks(rssiUpdater);
        geigerPlayer.setEnabled(false);
        tvConnectionStatus.setText("Connesso (in pausa)");
        resetRadiationDisplay();
    }

    private void startRssiUpdates() {
        handler.post(rssiUpdater);
    }

    private void updateButtonState() {
        btnStart.setText(isGeigerActive ? "FERMA CONTATORE" : "AVVIA CONTATORE");
    }

    private void resetRadiationDisplay() {
        tvRadiationValue.setText("--");
        tvRadiationLevel.setText("-- μSv/h");
        bg.setBackgroundColor(ContextCompat.getColor(this, R.color.radiation_background_normal));
    }

    private void updateRadiationLevel() {
        if (!isGeigerActive) return;

        int radiation = radiationCalculator.calculateRadiation();
        geigerPlayer.updateRadiationLevel(radiation);

        String levelName;
        int color;

        if (radiation <= 50) {
            levelName = "Aria Vault";
            color = R.color.radiation_background_normal;
        } else if (radiation <= 150) {
            levelName = "Polvere Glow";
            color = R.color.radiation_background_light;
        } else if (radiation <= 300) {
            levelName = "Zona Gialla";
            color =  R.color.radiation_background_moderate;
        } else if (radiation <= 500) {
            levelName = "Sangue Verde";
            color =  R.color.radiation_background_high;
        } else if (radiation <= 700) {
            levelName = "Ghoul Ferale";
            color =  R.color.radiation_background_danger;
        } else if (radiation <= 850) {
            levelName = "Scorie Dirette";
            color =  R.color.radiation_background_emergency;
        } else if (radiation <= 950) {
            levelName = "Cuore FEV";
            color =  R.color.radiation_background_critical;
        } else {
            levelName = "Liberty Prime";
            color =  R.color.radiation_background_lethal;
        }

        runOnUiThread(() -> {
            tvRadiationValue.setText(String.valueOf(radiation));
            tvRadiationLevel.setText(levelName);
            bg.setBackgroundColor(ContextCompat.getColor(this, color));
        });
    }

    private boolean checkPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    REQUEST_PERMISSIONS);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToDevice();
            } else {
                Toast.makeText(this, "Permessi necessari per funzionare", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void connectToDevice() {
        if (deviceAddress == null) {
            Toast.makeText(this, "Dispositivo non valido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        if (device == null) {
            Toast.makeText(this, "Dispositivo non trovato", Toast.LENGTH_SHORT).show();
            return;
        }

        tvConnectionStatus.setText("Connessione in corso...");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isGeigerActive) {
            stopGeigerCounter();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(rssiUpdater);
        geigerPlayer.release();

        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
    }
}