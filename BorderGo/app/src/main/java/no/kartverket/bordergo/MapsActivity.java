package no.kartverket.bordergo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebStorage;
import android.widget.Button;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.android.gms.maps.model.UrlTileProvider;


import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;

import no.kartverket.bordergo.config.Config;
import no.kartverket.data.api.GateKeeperApi;
import no.kartverket.data.dtm.DTMGrid;
import no.kartverket.data.repository.ITokenServiceRepository;
import no.kartverket.data.repository.TokenServiceRepository;
import no.kartverket.geodesy.OriginData;
import no.kartverket.geometry.Pos;
import no.kartverket.positionorientation.TangoPositionOrientationProvider;
import no.kartverket.positionorientation.Transform;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Map view for manual calibration.
 */
public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    //private boolean showAerialMap = true;
    private String orthoSessionKey;
    private BorderGoApp app;
    private TangoService tangoService;
    private Double lat;
    private Double lng;
    private static ArrayList<MarkerOptions> mapMarkerOptions = new ArrayList();
    private static ArrayList<Marker> mapMarkers = new ArrayList();
    private static ArrayList<Transform.Observation> observations = new ArrayList();
    private String mapServiceUrlTemplateNormal;
    private String mapServiceUrlTemplateOrtho;

    private ServiceConnection tangoConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            TangoService.TangoBinder binder = (TangoService.TangoBinder) service;
            tangoService = binder.getService();
            if(tangoService != null){
                Location l = tangoService.getPositionOrientationProvider().getLocation();
                if (l != null) {
                    lat = l.getLatitude();
                    lng = l.getLongitude();
                }
            }
        }

        public void onServiceDisconnected(ComponentName arg0) {
            tangoService = null;
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            lat = extras.getDouble("Lat");
            lng = extras.getDouble("Lng");
        }

        mapServiceUrlTemplateOrtho = BorderGoApp.config.getConfigValue(Config.Keys.ORTHO_WMS_SERVICE_URL);
        mapServiceUrlTemplateNormal = BorderGoApp.config.getConfigValue(Config.Keys.BASE_WMS_SERVICE_URL);

        setContentView(R.layout.activity_maps);
        app = (BorderGoApp) getApplication();
        orthoSessionKey = app.getOrthoSessionKey();
        initMap();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }


    private void initMap(){
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }



    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = new Intent(this, TangoService.class);
        bindService(intent, tangoConnection, Context.BIND_AUTO_CREATE);

        //updateMapFromIntent(); // commentetd away as it has lost the original intent data it seems

    }

    @Override
    protected void onResume() {
        super.onResume();
        //updateMapFromIntent(); // commentetd away as has lost the original intent data it seems
    }

    @Override
    protected void onStop() {
        super.onStop();

        unbindService(tangoConnection);
    }

    private void updateMapFromIntent(){
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            lat = extras.getDouble("lat");
            lng = extras.getDouble("lng");
            updateMapLocation();
        }
    }

    private void updateMapLocation(){
        if(mMap != null){
            if((lat != null) && (lng != null)){

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 19.0f));

            } else { // fallback to Kartverket location
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(60.14408, 10.24909), 19.0f));
            }
         }
    }



    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMaxZoomPreference(19.0f); // currently the maximum zoom level from Kartverket for the ortho service.
        if(mapServiceUrlTemplateNormal != null && mapServiceUrlTemplateOrtho != null){
            mMap.setMapType(GoogleMap.MAP_TYPE_NONE);
            setTileOverlay();
        } else {
            mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        }

        updateMapLocation();
        reInitializeMarkers();
    }

    TileProvider tileProvider;
    public void setTileOverlay(){
        TileProvider tileProvider = new UrlTileProvider(256, 256) {
            @Override
            public synchronized URL getTileUrl(int x, int y, int zoom) {
                String s = "";
                if(app.usesAerialMap() && (orthoSessionKey !=null)){
                     s = String.format(mapServiceUrlTemplateOrtho, x, y, zoom, orthoSessionKey);
                }else{
                    s = String.format(mapServiceUrlTemplateNormal, x, y, zoom);
                }
                URL url = null;
                try {
                    url = new URL(s);
                } catch (MalformedURLException e) {
                    throw new AssertionError(e);
                }
                return url;
            }
        };

        mMap.addTileOverlay(new TileOverlayOptions().tileProvider(tileProvider));
    }

    public void clearMarkers(){
        for (Marker mapMarker : mapMarkers) {
            mapMarker.remove();
        }
        app.getScene().clearCalibrationMarkers();

        // Clear observations!
        TangoPositionOrientationProvider posOrientationProvider= (TangoPositionOrientationProvider) tangoService.getPositionOrientationProvider();
        posOrientationProvider.removeObservations(observations);

        // Clear arrays;
        mapMarkers.clear();
        mapMarkerOptions.clear();
        observations.clear();

        // Ensure that GPS and Compass will be given to position orientation provider.
        BorderGoApp.useGPSAndCompass();

        Button removeButton = (Button)findViewById(R.id.removePointsButton);
        removeButton.setVisibility(View.INVISIBLE);


    }

    private void reInitializeMarkers(){
        mapMarkers.clear(); // throw away the old marker references
        for (MarkerOptions mapMarkerOps : mapMarkerOptions) {
            Marker m = mMap.addMarker(mapMarkerOps);
            mapMarkers.add(m); // make new based on mapMarkerOptions
        }
        Button removeButton = (Button)findViewById(R.id.removePointsButton);
        if(mapMarkerOptions.size()>0){
            removeButton.setVisibility(View.VISIBLE);
        } else {
            removeButton.setVisibility(View.INVISIBLE);
        }
    }


    public void clearMarkersClick(View view){        clearMarkers();  }

    public void backButtonClick(View view){
        super.finish();
    }

    public void toggleAerialClick(View view){
        Button toggleButton = (Button)findViewById(R.id.toggleAerialButton);
        if(app.usesAerialMap()){
            toggleButton.setText("Vis flyfoto");
        } else {
            toggleButton.setText("Vis grunnkart");
        }
        app.toggleAerialMap();

        mMap.clear();
        setTileOverlay();

        reInitializeMarkers();
    }



    public void setCurrentPos(View view){
        LatLng coord = this.mMap.getCameraPosition().target;
        DTMGrid dtm = tangoService.getDtmGrid();
        if (dtm != null) {
            double h = dtm.getInterpolatedAltitude(coord.latitude, coord.longitude);
            if (h > Double.NEGATIVE_INFINITY) {

                TangoPositionOrientationProvider posOrientationProvider= (TangoPositionOrientationProvider) tangoService.getPositionOrientationProvider();
                Transform.Observation obs = posOrientationProvider.handleLatLngHObservation(
                        coord.latitude, coord.longitude,
                        h + tangoService.getDeviceHeight(),
                        tangoService.getMapCalibrationSigma(), tangoService.getDemSigma());
                MarkerOptions mo = new MarkerOptions().position(coord);
                mapMarkerOptions.add(mo);
                Marker m = mMap.addMarker(mo);
                mapMarkers.add(m);
                observations.add(obs);
                Pos p = new Pos();
                OriginData origin = posOrientationProvider.getOrigin();
                p.x = (float)origin.longitudeToLocalOrigin(coord.longitude);
                p.y = (float)origin.latitudeToLocalOrigin(coord.latitude);
                p.z = (float)origin.heightToLocalOrigin(h);
                app.getScene().addCalibrationMarker(p);
                if(observations.size()>2){
                    BorderGoApp.dontUseGpsAndCompass(); // stop listening to gps and compass
                } else {
                    BorderGoApp.useGPSAndCompass(); // listen to gps and compass
                }


                Button removeButton = (Button)findViewById(R.id.removePointsButton);
                removeButton.setVisibility(View.VISIBLE);

                return;
            }
        }
    }
}
