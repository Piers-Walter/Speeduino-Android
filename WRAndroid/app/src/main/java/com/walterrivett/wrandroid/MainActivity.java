package com.walterrivett.wrandroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.res.ResourcesCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.anastr.speedviewlib.RaySpeedometer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import si.inova.neatle.Neatle;
import si.inova.neatle.monitor.Connection;
import si.inova.neatle.monitor.ConnectionMonitor;
import si.inova.neatle.monitor.ConnectionStateListener;
import si.inova.neatle.operation.CharacteristicSubscription;
import si.inova.neatle.operation.CharacteristicsChangedListener;
import si.inova.neatle.operation.CommandResult;
import si.inova.neatle.operation.Operation;
import si.inova.neatle.source.InputSource;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class MainActivity extends Activity implements CharacteristicsChangedListener {
    LocationManager locationManager;
    LocationListener locationListener;
    RaySpeedometer speedometer;

    Context mContext;

    private static int kFineLocation = 1000;

    private ImageView indicatorImage;
    private ImageView neutralImage;
    private ImageView hiBeamImage;

    private ImageButton unlockButton;
    private ProgressBar unlockProgress;

    private boolean unlocked = false;


    public static String TXRX_SERVICE_UUID = "0000FFE0-0000-1000-8000-00805F9B34FB";
    public static String TXRX_UUID = "0000FFE1-0000-1000-8000-00805F9B34FB";
    public static String BLE_MAC = "D0:5F:B8:3B:AA:CD";

    public static String password = "BikeUnlockCode";

    CharacteristicSubscription characteristicSubscription;
    byte[] toSend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Lock to Landscaoe
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        setContentView(R.layout.activity_main);

        mContext = this;

        indicatorImage = findViewById(R.id.indicatorImage);
        neutralImage = findViewById(R.id.neutralImage);
        hiBeamImage = findViewById(R.id.hiBeamImage);

        unlockButton = findViewById(R.id.unlockButton);
        unlockProgress = findViewById(R.id.unlockProgress);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new speed();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, kFineLocation);
            return;
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        Typeface segmentFont = ResourcesCompat.getFont(this, R.font.dseg14modern_regular);

        speedometer = findViewById(R.id.raySpeedometer);

        speedometer.setSpeedTextTypeface(segmentFont);
        speedometer.setTextTypeface(segmentFont);
        speedometer.speedTo(80f, 1000);
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //Do something after 100ms
                speedometer.speedTo(0f, 1000);
            }
        }, 1300);

        //Setup Bluetooth
        UUID TXRX_SERVICE = UUID.fromString(TXRX_SERVICE_UUID);
        UUID TXRX = UUID.fromString(TXRX_UUID);

        ConnectionMonitor monitor =
                Neatle.createConnectionMonitor(this, Neatle.getDevice(BLE_MAC));
        monitor.setOnConnectionStateListener(new ConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(Connection connection, int newState) {
                if (connection.isConnected()) {
                    // The device has connected
                    Log.d("Main Activity", "Connected to Device");
                    unlockProgress.setVisibility(INVISIBLE);
                    unlockButton.setVisibility(VISIBLE);
                }

                if(!connection.isConnected()){
                    unlockProgress.setVisibility(VISIBLE);
                    unlockButton.setVisibility(INVISIBLE);
                    hideHiBeam();
                    hideIndicators();
                    hideNeutral();
                }
            }
        });
        monitor.setKeepAlive(true);
        monitor.start();

        characteristicSubscription = Neatle.createSubscription(this, Neatle.getDevice(BLE_MAC), TXRX_SERVICE, TXRX);
        characteristicSubscription.setOnCharacteristicsChangedListener(this);
        characteristicSubscription.start();

        BluetoothDevice device = Neatle.getDevice(BLE_MAC);
        Operation operation = Neatle.createOperationBuilder(this).write(TXRX_SERVICE, TXRX, new InputSource() {
            byte[] output;
            int i = 0;

            @Override
            public void open() throws IOException {
                output = toSend;
            }

            @Override
            public byte[] nextChunk() throws IOException {
                byte[] nextOutput = output;
                output = null;
                return nextOutput;
            }

            @Override
            public void close() throws IOException {

            }
        }).build(device);

        unlockButton.setOnClickListener(v -> {
            toSend = password.getBytes();
            operation.execute();
        });

    }

    private void processBytes(byte[] bytes) {
        if (bytes.length != 3) {
            return;
        }
        if (bytes[0] == 0) {
            showNeutral();
        } else {
            hideNeutral();
        }

        if (bytes[1] == 0) {
            hideIndicators();
        } else {
            showIndicators();
        }

        if (bytes[2] == 0) {
            hideHiBeam();
        } else {
            showHiBeam();
        }
    }

    private void showHiBeam() {
        hiBeamImage.setVisibility(VISIBLE);
    }

    private void hideHiBeam() {
        hiBeamImage.setVisibility(INVISIBLE);
    }

    private void hideNeutral() {
        neutralImage.setVisibility(INVISIBLE);
    }

    private void showIndicators() {
        indicatorImage.setVisibility(VISIBLE);
    }

    private void hideIndicators() {
        indicatorImage.setVisibility(INVISIBLE);
    }

    private void showNeutral() {
        neutralImage.setVisibility(VISIBLE);
    }


    private class speed implements LocationListener {
        @Override
        public void onLocationChanged(Location loc) {
            try {
                if (loc.hasSpeed()) {
                    //Convert from m/s to mph
                    Float thespeed = loc.getSpeed() * 2.23694f;
                    speedometer.speedTo(thespeed, 500);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onProviderDisabled(String arg0) {
            //Toast.makeText(mContext, "onProviderDisabled",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onProviderEnabled(String arg0) {
            //Toast.makeText(mContext, "onProvderEnabled", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
            //Toast.makeText(mContext, "onStatusChanged",Toast.LENGTH_SHORT).show();
        }

    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == kFineLocation) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            }
        } else {
            Toast.makeText(this, "This App Needs Location Permission to Work", Toast.LENGTH_LONG);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    @Override
    public void onCharacteristicChanged(CommandResult change) {
        if (change.wasSuccessful()) {

            if (change.getValueAsString().equals("Unlocked")) {
                unlocked = true;
                unlockProgress.setVisibility(INVISIBLE);
                unlockButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_lock_open_black_24dp));
                new Handler().postDelayed(() -> {
                    unlockButton.setVisibility(GONE);
                }, 500);
            } else {

                //Received Byte Array
                byte[] received = change.getValue();

                if (unlocked) {
                    processBytes(received);
                }
            }


        } else {
            Log.d("onCharac2", "Received: " + change.getValueAsString());
        }
    }

    public void onPause() {
        super.onPause();

        locationManager.removeUpdates(locationListener);

    }

    public void onResume() {
        super.onResume();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        //Setup Bluetooth
        UUID TXRX_SERVICE = UUID.fromString(TXRX_SERVICE_UUID);
        UUID TXRX = UUID.fromString(TXRX_UUID);

        ConnectionMonitor monitor =
                Neatle.createConnectionMonitor(this, Neatle.getDevice(BLE_MAC));
        monitor.setOnConnectionStateListener(new ConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(Connection connection, int newState) {
                if (connection.isConnected()) {
                    // The device has connected
                    Log.d("Main Activity", "Connected to Device");
                    unlockProgress.setVisibility(INVISIBLE);
                    unlockButton.setVisibility(VISIBLE);
                    unlockButton.setImageDrawable(getDrawable(R.drawable.ic_lock_outline_black_24dp));
                }

                if(!connection.isConnected()){
                    unlockProgress.setVisibility(VISIBLE);
                    unlockButton.setVisibility(INVISIBLE);
                    hideHiBeam();
                    hideIndicators();
                    hideNeutral();
                }
            }
        });
        monitor.setKeepAlive(true);
        monitor.start();

        characteristicSubscription = Neatle.createSubscription(this, Neatle.getDevice(BLE_MAC), TXRX_SERVICE, TXRX);
        characteristicSubscription.setOnCharacteristicsChangedListener(this);
        characteristicSubscription.start();

        BluetoothDevice device = Neatle.getDevice(BLE_MAC);
        Operation operation = Neatle.createOperationBuilder(this).write(TXRX_SERVICE, TXRX, new InputSource() {
            byte[] output;
            int i = 0;

            @Override
            public void open() throws IOException {
                output = toSend;
            }

            @Override
            public byte[] nextChunk() throws IOException {
                byte[] nextOutput = output;
                output = null;
                return nextOutput;
            }

            @Override
            public void close() throws IOException {

            }
        }).build(device);

        unlockButton.setOnClickListener(v -> {
            toSend = password.getBytes();
            operation.execute();
        });
    }
}
