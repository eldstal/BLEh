package org.snutt.bleh;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;
import java.util.UUID;

public class VibeaseActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt gattServer;

    private boolean mScanning;
    private Handler handler;

    public TextView txtLog;

    private BluetoothDevice dev = null;
    private BluetoothGattCharacteristic cmd_read;
    private BluetoothGattCharacteristic cmd_write;

    // Stops scanning after 30 seconds.
    private static final long SCAN_PERIOD = 30000;

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback leScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    if (device.getName() == null) return;

                    if (device.getName().contains("VIBEASE")) {
                        VibeaseActivity.this.Write("FOUND: " + device.getAddress() + "   " + device.getName());
                        VibeaseActivity.this.dev = device;
                        VibeaseActivity.this.scanLeDevice(false);
                    }
                }
            };
    private VibeaseController controller = null;


    private void getPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Write("Bluetooth requires location permission. Please grant.");
            }

            // No explanation needed; request the permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    13);


        } else {
            // Permission has already been granted
            Write("Location permissions already granted.");
        }

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vibease);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        txtLog = (TextView) findViewById(R.id.txtLog);
        Button btnScan = (Button) findViewById(R.id.btnScan);
        Button btnPair = (Button) findViewById(R.id.btnPair);
        Button btnAction = (Button) findViewById(R.id.btnAction);

        this.handler = new Handler();

        getPermissions();

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 32);
        }



        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VibeaseActivity.this.scanLeDevice(true);
            }
        });

        btnPair.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VibeaseActivity.this.pair(VibeaseActivity.this.dev);
            }
        });

        btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (controller != null) {
                    controller.ToggleVibration();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanLeDevice(false);
        if (gattServer != null) {
            gattServer.close();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        scanLeDevice(false);
    }

    public void Write(final String txt) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (VibeaseActivity.this.txtLog != null) VibeaseActivity.this.txtLog.append(txt + "\n");
            }
        });
    }

    private void scanLeDevice(final boolean enable) {
        if (enable && !mScanning) {
            VibeaseActivity.this.Write("Starting scan...");
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    VibeaseActivity.this.scanLeDevice(false);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            bluetoothAdapter.startLeScan(leScanCallback);
        } else {
            if (mScanning) {
                VibeaseActivity.this.Write("Stopping scan...");
            }
            mScanning = false;
            bluetoothAdapter.stopLeScan(leScanCallback);

        }
    }

    private void pair(BluetoothDevice dev) {
        if (dev == null) {
            Write("Scan for a device first!");
        } else {
            controller = new VibeaseController(dev, this);

        }

    }

    private void request_key_hs(BluetoothGatt server) {
        if (dev == null) {
            Write("Scan for a device first!");
        } else if (server == null) {
            Write("Pair with device first!");
        } else {



        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
