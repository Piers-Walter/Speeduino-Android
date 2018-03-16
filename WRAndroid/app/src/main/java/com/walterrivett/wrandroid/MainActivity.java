package com.walterrivett.wrandroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.Toast;

import com.github.anastr.speedviewlib.RaySpeedometer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class MainActivity extends Activity {
    LocationManager locationManager;
    LocationListener locationListener;
    RaySpeedometer speedometer;

    Context mContext;

    private static int kFineLocation = 1000;

    private ImageView indicatorImage;
    private ImageView neutralImage;
    private ImageView hiBeamImage;
    private ImageView bluetoothImage;

    private BroadcastReceiver bluetoothConnectReceiver;
    private BroadcastReceiver bluetoothDisconnectReceiver;
    private BroadcastReceiver bluetoothFailedReceiver;

    private BluetoothSerial bluetoothSerial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;

        indicatorImage = findViewById(R.id.indicatorImage);
        neutralImage = findViewById(R.id.neutralImage);
        hiBeamImage = findViewById(R.id.hiBeamImage);
        bluetoothImage = findViewById(R.id.bluetoothIcon);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new speed();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, kFineLocation);
            return;
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        Typeface segmentFont = ResourcesCompat.getFont(this,R.font.dseg14modern_regular);

        speedometer = (RaySpeedometer) findViewById(R.id.raySpeedometer);

        speedometer.setSpeedTextTypeface(segmentFont);
        speedometer.setTextTypeface(segmentFont);
        speedometer.speedTo(80f, 1000);
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //Do something after 100ms
                speedometer.speedTo(0f,1000);
            }
        }, 1300);

        bluetoothSerial = new BluetoothSerial(this, new BluetoothSerial.MessageHandler() {
            @Override
            public int read(int bufferSize, byte[] buffer) throws IOException, InterruptedException {
                if(bluetoothSerial.connected) {
                    Log.d("Bluetooth Handler", "Received Message");
                    while(bufferSize!=4){
                        Thread.sleep(10);
                    }
                    StringBuilder outputString = new StringBuilder();
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    final byte[] outputBytes;

                    int readByte;
                    try {
                        readByte = bluetoothSerial.read();
                        while(readByte!=0xFF) {
                            output.write(readByte);
                            outputString.append(Integer.toHexString(readByte));
                            outputString.append(",");
                            readByte = bluetoothSerial.read();
                        }
                        output.write(0xFF);
                        outputString.append(Integer.toHexString(0xFF));

                        outputBytes = output.toByteArray();
                        Log.d("Bluetooth Handler", "Message Hex: " + outputString);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    processBytes(outputBytes);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }catch (IOException e){
                        Log.d("Serial Read", "Handling Disconnect");
                        bluetoothSerial.close();
                        bluetoothSerial.connect();
                    }

                }else{
                    bluetoothSerial.connect();
                }

                return 0;

            }
        }, "Pi");


        setupBroadcastReceivers();

        //Fired when connection is established and also fired when onResume is called if a connection is already established.
        LocalBroadcastManager.getInstance(this).registerReceiver(bluetoothConnectReceiver, new IntentFilter(BluetoothSerial.BLUETOOTH_CONNECTED));
        //Fired when the connection is lost
        LocalBroadcastManager.getInstance(this).registerReceiver(bluetoothDisconnectReceiver, new IntentFilter(BluetoothSerial.BLUETOOTH_DISCONNECTED));
        //Fired when connection can not be established, after 30 attempts.
        LocalBroadcastManager.getInstance(this).registerReceiver(bluetoothFailedReceiver, new IntentFilter(BluetoothSerial.BLUETOOTH_FAILED));



        bluetoothSerial.connect();
        //Set bluetooth icon flashing
        startBluetoothAnimation();

    }

    private void startBluetoothAnimation(){
        Animation animation = new AlphaAnimation(1, 0);
        animation.setDuration(1000);
        animation.setInterpolator(new AccelerateDecelerateInterpolator());
        animation.setRepeatCount(Animation.INFINITE);
        animation.setRepeatMode(Animation.REVERSE);
        bluetoothImage.startAnimation(animation);
    }

    private void setupBroadcastReceivers(){
        bluetoothConnectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //Called When Bluetooth Successfully connects
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //Set Bluetooth icon to disappeared and set visibility of bike icons to invisible
                        hiBeamImage.setVisibility(INVISIBLE);
                        indicatorImage.setVisibility(INVISIBLE);
                        neutralImage.setVisibility(INVISIBLE);
                        bluetoothImage.clearAnimation();
                        bluetoothImage.setVisibility(GONE);

                    }
                });
            }
        };

        bluetoothDisconnectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //Set Bluetooth icon to disappeared and set visibility of bike icons to invisible
                        hiBeamImage.setVisibility(GONE);
                        indicatorImage.setVisibility(GONE);
                        neutralImage.setVisibility(GONE);
                        startBluetoothAnimation();
                        bluetoothSerial.connect();
                    }
                });
            }
        };
    }

    private void processBytes(byte[] bytes) throws IOException {
        if(bytes.length == 4 && bytes[3] == -1 /* 0xFF */){
            Log.d("Process Bytes","Received Correct Byte Count");
            //Indicators
            if(bytes[0]==1){

                showIndicators();



            }else{
                hideIndicators();
            }

            //Neutral
            if(bytes[1]==1){
                showNeutral();
            }else{
                hideNeutral();
            }

            //Hi-Beam
            if(bytes[2]==1){
                showHiBeam();
            }else{
                hideHiBeam();
            }
        }else{
            while(bluetoothSerial.available()>0){
                bluetoothSerial.read();
            }
        }
    }

    private void showHiBeam() {
        hiBeamImage.setVisibility(VISIBLE);
    }
    private void hideHiBeam(){
        hiBeamImage.setVisibility(INVISIBLE);
    }

    private void hideNeutral() {
        neutralImage.setVisibility(INVISIBLE);
    }

    private void showIndicators(){
        indicatorImage.setVisibility(VISIBLE);
    }
    private void hideIndicators(){
        indicatorImage.setVisibility(INVISIBLE);
    }

    private void showNeutral() {
        neutralImage.setVisibility(VISIBLE);
    }


    private class speed implements LocationListener{
        @Override
        public void onLocationChanged(Location loc) {
            Float thespeed=loc.getSpeed()*2.23694f;
            speedometer.speedTo(thespeed,500);
        }
        @Override
        public void onProviderDisabled(String arg0) {
            Toast.makeText(mContext, "onProviderDisabled",Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onProviderEnabled(String arg0) {
            Toast.makeText(mContext, "onProvderEnabled", Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
            Toast.makeText(mContext, "onStatusChanged",Toast.LENGTH_SHORT).show();
        }

    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode==kFineLocation){
            if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            }
        }else{
            Toast.makeText(this,"This App Needs Location Permission to Work",Toast.LENGTH_LONG);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    }
}
