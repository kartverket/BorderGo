package no.kartverket.bordergo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;
import android.view.WindowManager;


import java.util.Timer;
import java.util.TimerTask;

import no.kartverket.bordergo.config.Config;
import no.kartverket.data.api.GateKeeperApi;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

/**
 * Created by janvin on 18.08.2017.
 */

public class SplashActivity extends AppCompatActivity {

    private static final String CAMERA_PERMISSION = android.Manifest.permission.CAMERA;
    private static final String LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION;

    private static final int MY_PERMISSIONS_REQUEST = 7171;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        /*getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);*/
        BorderGoApp app = (BorderGoApp) getApplication();
        BorderGoApp.config = new Config(this.getApplicationContext()); // instantiate in the first view class so that config gets the correct context to access text assets.
        Context context = this;





        if (! (hasCameraPermission() &&   hasLocationPermission() && hasStoragePermission())) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.CAMERA,
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            WRITE_EXTERNAL_STORAGE
                    },
                    MY_PERMISSIONS_REQUEST);
        }else {

            new Timer().schedule(new TimerTask(){
                public void run(){
                    startMainActivity();
                };
            }, 250);
            /*Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();*/
        }
    }

    private void  startMainActivity(){
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    final SplashActivity context = this;

                    new Timer().schedule(new TimerTask(){
                        public void run(){
                            Intent intent = new Intent(context, MainActivity.class);
                            startActivity(intent);
                            finish();
                        };
                    }, 500);

                } else {
                    // permission denied.
                }
                return;
            }
        }
    }


    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED;
    }



}