// Copyright 2018 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.espressif.ui;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.espressif.avs.ConfigureAVS;
import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.provision.transport.BLETransport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class BLEProvisionLanding extends AppCompatActivity {

    private static final String TAG = "Espressif::" + BLEProvisionLanding.class.getSimpleName();

    private Button btnScan;
    private ProgressBar progressBar;
    private ListView listView;

    private BluetoothAdapter bleAdapter;
    private ArrayList<BluetoothDevice> bluetoothDevices;

    public static BLETransport bleTransport;
    private BLETransport.BLETransportListener transportListener;
    // FIXME : Remove static BLE_TRANSPORT and think for another solution.

    private String configUUID;
    private String avsConfigUUID;
    private String serviceUUID;
    private String sessionUUID;
    private String deviceNamePrefix;
    private boolean isScanning;
    private boolean isDeviceConnected;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bleprovision_landing);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.connect_to_device_title);
        setSupportActionBar(toolbar);

        serviceUUID = getIntent().getStringExtra(BLETransport.SERVICE_UUID_KEY);
        sessionUUID = getIntent().getStringExtra(BLETransport.SESSION_UUID_KEY);
        configUUID = getIntent().getStringExtra(BLETransport.CONFIG_UUID_KEY);
        avsConfigUUID = getIntent().getStringExtra(ConfigureAVS.AVS_CONFIG_UUID_KEY);
        deviceNamePrefix = getIntent().getStringExtra(BLETransport.DEVICE_NAME_PREFIX_KEY);

        btnScan = findViewById(R.id.btn_scan);
        listView = findViewById(R.id.ble_devices_list);
        progressBar = findViewById(R.id.ble_landing_progress_indicator);

        isDeviceConnected = false;
        bluetoothDevices = new ArrayList<>();
        ArrayList<String> bleNames = new ArrayList<>();

        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                android.R.id.text1,
                bleNames);

        // Assign adapter to ListView
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                isDeviceConnected = false;
                btnScan.setVisibility(View.GONE);
                listView.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
                bleTransport.connect(bluetoothDevices.get(pos));
            }
        });

        btnScan.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                startScan();
            }
        });

        transportListener = new BLETransport.BLETransportListener() {

            @Override
            public void onPeripheralsFound(ArrayList<BluetoothDevice> devices) {

                boolean deviceExists = false;
                for (BluetoothDevice device : devices) {
                    for (BluetoothDevice alreadyHere : bluetoothDevices) {
                        if (device.equals(alreadyHere)) {
                            deviceExists = true;
                            break;
                        }
                    }
                    if (!deviceExists) {
                        bluetoothDevices.add(device);
                        adapter.add(device.getName());
                    }
                    deviceExists = false;
                }
                stopScan();
            }

            @Override
            public void onPeripheralsNotFound() {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(BLEProvisionLanding.this,
                                "No Bluetooth devices found!",
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });
                stopScan();
            }

            @Override
            public void onPeripheralConfigured(BluetoothDevice device) {

                bleDeviceConfigured(true);
            }

            @Override
            public void onPeripheralNotConfigured(BluetoothDevice device) {

                isScanning = false;
                btnScan.setEnabled(true);
                btnScan.setAlpha(1f);
                progressBar.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
                bleDeviceConfigured(false);
            }

            @Override
            public void onPeripheralDisconnected(Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(BLEProvisionLanding.this,
                                "Bluetooth device disconnected.",
                                Toast.LENGTH_LONG)
                                .show();
                        // TODO check for stopScan();
                    }
                });
            }

            @Override
            public void onFailure(final Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(BLEProvisionLanding.this,
                                "Bluetooth connection failed : " + e.getMessage(),
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });
            }
        };

        HashMap<String, String> configUUIDMap = new HashMap<>();
        configUUIDMap.put(Provision.PROVISIONING_CONFIG_PATH, configUUID);
        configUUIDMap.put("prov-scan", "0000ff50-0000-1000-8000-00805f9b34fb");
        if (avsConfigUUID != null) {
            configUUIDMap.put(ConfigureAVS.AVS_CONFIG_PATH, avsConfigUUID);
        }

        bleTransport = new BLETransport(this,
                UUID.fromString(serviceUUID),
                UUID.fromString(sessionUUID),
                configUUIDMap,
                deviceNamePrefix,
                3000);

        boolean isBLEEnabled = this.checkBLEPermissions();
        if (!isBLEEnabled) {
            btnScan.setEnabled(false);
            btnScan.setAlpha(0.5f);
            btnScan.setTextColor(Color.WHITE);
            requestBluetoothEnable();
        } else {
            btnScan.setEnabled(true);
            btnScan.setAlpha(1f);
            btnScan.setTextColor(Color.WHITE);
            startScan();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "ON RESUME");
        if (isDeviceConnected) {
//            bleTransport.disconnect();
            final TextView bleInstructions = findViewById(R.id.bluetooth_status_message);
            bleInstructions.setText(R.string.enable_bluetooth_instructions);
        }
    }

    private void bleDeviceConfigured(final Boolean isConfigured) {

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (isConfigured) {

                    isDeviceConnected = true;
                    finish();
                    goToProofOfPossessionActivity();

                } else {
                    Toast.makeText(BLEProvisionLanding.this,
                            "Bluetooth device could not be configured. Please try another device.",
                            Toast.LENGTH_LONG)
                            .show();
                }
            }
        });
    }

    private void startScan() {

        isScanning = true;
        bluetoothDevices.clear();
        adapter.clear();
        updateProgressAndScanBtn();
        bleTransport.scan(transportListener);
    }

    private void stopScan() {

        isScanning = false;
        updateProgressAndScanBtn();
    }

    /**
     * This method will update UI (Scan button enable / disable and progressbar visibility)
     */
    private void updateProgressAndScanBtn() {

        if (isScanning) {

            btnScan.setEnabled(false);
            btnScan.setAlpha(0.5f);
            btnScan.setTextColor(Color.WHITE);
            progressBar.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);

        } else {

            btnScan.setEnabled(true);
            btnScan.setAlpha(1f);
            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
    }

    private void goToProofOfPossessionActivity() {

        Intent alexaProvisioningIntent = new Intent(getApplicationContext(), ProofOfPossessionActivity.class);
        alexaProvisioningIntent.putExtras(getIntent());
        alexaProvisioningIntent.putExtra(LoginWithAmazon.KEY_IS_PROVISIONING, true);
        startActivity(alexaProvisioningIntent);
    }

    private boolean checkBLEPermissions() {
        boolean isBLEEnabled = false;
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bleAdapter = bluetoothManager.getAdapter();
        if (bleAdapter == null || !bleAdapter.isEnabled()) {
            isBLEEnabled = false;
        } else if (!hasLocationPermissions()) {
            requestLocationPermission();
        } else {
            isBLEEnabled = true;
        }

        return isBLEEnabled;
    }

    private void requestBluetoothEnable() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, Provision.REQUEST_ENABLE_BLE_CODE);
    }

    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 102);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.e(TAG, "On Activity Result : requestCode : " + requestCode + ", resultCode : " + resultCode);

        if (requestCode == Provision.REQUEST_PROVISIONING_CODE && resultCode == RESULT_OK) {
            setResult(resultCode);
            finish();
        } else if (requestCode == Provision.REQUEST_ENABLE_BLE_CODE && resultCode == RESULT_OK) {
            boolean isEnable = checkBLEPermissions();

            if (isEnable) {

                HashMap<String, String> configUUIDMap = new HashMap<>();
                configUUIDMap.put(Provision.PROVISIONING_CONFIG_PATH, configUUID);
                configUUIDMap.put("prov-scan", "0000ff50-0000-1000-8000-00805f9b34fb");
                if (avsConfigUUID != null) {
                    configUUIDMap.put(ConfigureAVS.AVS_CONFIG_PATH, avsConfigUUID);
                }
                startScan();
            } else {
                Log.e(TAG, "Error");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case Provision.REQUEST_PERMISSIONS_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkBLEPermissions();
                }
            }
            break;
        }
    }
}
