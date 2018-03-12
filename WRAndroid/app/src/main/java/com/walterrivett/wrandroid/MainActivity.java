package com.walterrivett.wrandroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import com.github.anastr.speedviewlib.RaySpeedometer;

public class MainActivity extends Activity {
    LocationManager locationManager;
    LocationListener locationListener;
    RaySpeedometer speedometer;

    Context mContext;

    private static int kFineLocation = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;

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
}
