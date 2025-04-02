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
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.LinearLayout;
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

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private TextView tvRadiationValue;
    private TextView tvRadiationUnit;
    private TextView tvRadiationLevel;
    private TextView tvConnectionStatus;
    private LinearLayout radiationContainer;
    private RelativeLayout bg;
    private final Handler handler = new Handler();
    private GeigerClickPlayer geigerPlayer;
    private final Handler updatesHandler = new Handler();


    private final RadiationCalculator radiationCalculator = new RadiationCalculator();

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> {
                    if (ActivityCompat.checkSelfPermission(DetectorActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        tvConnectionStatus.setText("Connesso");
                    } else {
                        checkPermissions();
                    }
                });
                handler.post(rssiUpdater);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(() -> {
                    tvConnectionStatus.setText("Disconnesso");
                    tvRadiationLevel.setText("-- μSv/h");
                });
                handler.removeCallbacks(rssiUpdater);
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                radiationCalculator.updateRssi(rssi);
                updateRadiationLevel();
            } else {
                Log.e("BLE_RSSI", "Errore lettura RSSI: " + status);
            }
        }
    };

    private final Runnable rssiUpdater = new Runnable() {
        @Override
        public void run() {
            if (bluetoothGatt != null) {
                if (ActivityCompat.checkSelfPermission(DetectorActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothGatt.readRemoteRssi();
                }
            }
            handler.postDelayed(this, 1000);
        }
    };
    private String deviceAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detector);

        deviceAddress = getIntent().getStringExtra("DEVICE_ADDRESS");

        // Inizializza UI
        bg = findViewById(R.id.det_act);
        tvRadiationValue = findViewById(R.id.tv_radiation_value);
        tvRadiationUnit = findViewById(R.id.tv_radiation_unit);
        tvRadiationLevel = findViewById(R.id.tv_radiation_level);
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        radiationContainer = findViewById(R.id.radiationContainer);

        // Inizializza Bluetooth
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Verifica se il dispositivo supporta Bluetooth
        if (bluetoothAdapter == null) {
            tvConnectionStatus.setText("Bluetooth non supportato");
            return;
        }

        // Configura SoundPool per il suono Geiger
        geigerPlayer = new GeigerClickPlayer(this);

        checkPermissions();
    }

    private void checkPermissions() {
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
        } else {
            connectToTargetDevice();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToTargetDevice();
            } else {
                Toast.makeText(this, "Permesso negato", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void connectToTargetDevice() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice targetDevice = adapter.getRemoteDevice(deviceAddress);

        if (targetDevice == null) {
            Toast.makeText(this, "Dispositivo non valido", Toast.LENGTH_SHORT).show();
            return;
        }

        tvConnectionStatus.setText("Connessione in corso...");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt = targetDevice.connectGatt(this, false, gattCallback);
        } else {
            checkPermissions();
        }
    }

    private void updateRadiationLevel() {
        int radiation = radiationCalculator.calculateRadiation();
        geigerPlayer.updateRadiationLevel(radiation);
        String levelName;
        int color;

        // Assegna nome livello e colore in base al valore
        if (radiation <= 50) {
            levelName = "Aria Vault";
            color = ContextCompat.getColor(this, R.color.radiation_background_normal);
        } else if (radiation <= 150) {
            levelName = "Polvere Glow";
            color = ContextCompat.getColor(this, R.color.radiation_background_light);
        } else if (radiation <= 300) {
            levelName = "Zona Gialla";
            color = ContextCompat.getColor(this, R.color.radiation_background_moderate);
        } else if (radiation <= 500) {
            levelName = "Sangue Verde";
            color = ContextCompat.getColor(this, R.color.radiation_background_high);
        } else if (radiation <= 700) {
            levelName = "Ghoul Ferale";
            color = ContextCompat.getColor(this, R.color.radiation_background_danger);
        } else if (radiation <= 850) {
            levelName = "Scorie Dirette";
            color = ContextCompat.getColor(this, R.color.radiation_background_emergency);
        } else if (radiation <= 950) {
            levelName = "Cuore FEV";
            color = ContextCompat.getColor(this, R.color.radiation_background_critical);
        } else {
            levelName = "Liberty Prime";
            color = ContextCompat.getColor(this, R.color.radiation_background_lethal);
        }

        runOnUiThread(() -> {
            // Aggiorna le nuove View separate
            tvRadiationValue.setText(String.valueOf(radiation));
            tvRadiationLevel.setText(levelName);

            // Imposta colore sfondo
            bg.setBackgroundColor(color);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        geigerPlayer.stop(); // Ferma i click quando l'app è in pausa
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(rssiUpdater);
        geigerPlayer.release();
        updatesHandler.removeCallbacksAndMessages(null);
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            } else {
                checkPermissions();
            }
        }
    }
}