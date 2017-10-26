package no.kartverket.bordergo;

import android.Manifest;
import android.app.Activity;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.location.Location;
import android.content.Context;

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoSupport;

import java.io.IOException;
import java.nio.FloatBuffer;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import no.kartverket.bordergo.config.Config;
import no.kartverket.bordergo.data.DataActivity;
import no.kartverket.data.dtm.DTMGrid;
import no.kartverket.data.dtm.DTMGridProvider;
import no.kartverket.data.DataLogger;

import no.kartverket.data.repository.ITokenServiceRepository;
import no.kartverket.data.repository.TokenServiceRepository;

import no.kartverket.data.utils.DTMRequest;
import no.kartverket.data.utils.WcsRequest;

import no.kartverket.data.utils.WmsRequest;
import no.kartverket.geodesy.Geodesy;
import no.kartverket.geodesy.OriginData;
import no.kartverket.geometry.IndexedTriangleMesh;
import no.kartverket.geometry.Pos;
import no.kartverket.glrenderer.ArGlRenderer;
import no.kartverket.glrenderer.ArScene;
import no.kartverket.glrenderer.GlColor;
import no.kartverket.positionorientation.OriginUpdateListener;
import no.kartverket.positionorientation.PositionOrientationProvider;
import no.kartverket.positionorientation.TangoPositionOrientationProvider;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

/**
 * An activity containing the main 3D view. The 3D view displays the live camera image together with
 * overlayed graphical data. Positional data is presented as textual information over the 3D view.
 * The activity also administers loading of data and creation of graphical data structures.
 */
public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int INVALID_TEXTURE_ID = 0;

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final String LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final int CAMERA_PERMISSION_CODE = 0;
    private static final int LOCATION_PERMISSION_CODE = 1;
    private static final int WRITE_EXTERNAL_STORAGE_CODE = 2;

    private ArGlRenderer renderer;
    private ArScene scene;
    private DataToDrawProvider  dataToDrawProvider;
    private TextView textA, textB,textC,textD, textD1, textE,textF, textG;
    private EditText deviceHeightEdit, demZigmaEdit, mapCalibrationZigmaEdit, pointCloudZigmaEdit;

    private BorderGoApp app;
    private GLSurfaceView surfaceView;

    private int connectedTextureIdGlThread = INVALID_TEXTURE_ID;
    private AtomicBoolean isFrameAvailableTangoThread = new AtomicBoolean(false);
    private double rgbTimestampGlThread;


    private DTMRequest heightService;

    private int displayRotation = 0;


    TangoService tangoService;
    boolean isBound = false;

    /**
     * Connects to the {@link TangoService} and registers a {@link FrameListener}
     */
    private ServiceConnection tangoConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            TangoService.TangoBinder binder = (TangoService.TangoBinder) service;
            tangoService = binder.getService();
            tangoService.getPositionOrientationProvider().addOriginUpdateListener(originUpdateListener);
            tangoService.addFrameListener(new FrameListener());
            //resetToDefault(null);
            updateFromPreferencesOrDefault();

            isBound = true;
        }

        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }

    };

    /**
     * Interface between tango and rendering
     */
    class FrameListener implements TangoService.FrameListener {
        /**
         * A camera frame is available for rendering
         *
         * @param cameraId
         */
        public void onFrameAvailable(int cameraId) {
            // Check if the frame available is for the camera we want and update its frame
            // on the view.
            if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                // Now that we are receiving onFrameAvailable callbacks, we can switch
                // to RENDERMODE_WHEN_DIRTY to drive the render loop from this callback.
                // This will result in a frame rate of approximately 30FPS, in synchrony with
                // the RGB camera driver.
                // If you need to render at a higher rate (i.e., if you want to render complex
                // animations smoothly) you can use RENDERMODE_CONTINUOUSLY throughout the
                // application lifecycle.
                if (surfaceView.getRenderMode() != GLSurfaceView.RENDERMODE_WHEN_DIRTY) {
                    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
                }

                // Mark a camera frame as available for rendering in the OpenGL thread.
                isFrameAvailableTangoThread.set(true);
                // Trigger an OpenGL render to update the OpenGL scene with the new RGB data.
                surfaceView.requestRender();
            }
        }
    }

    // Gesture detector
    private GestureDetectorCompat gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(getClass().getSimpleName(), "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String heightServiceURL = BorderGoApp.config.getConfigValue(Config.Keys.HEIGHT_SERVICE_URL);

        if (heightServiceURL.toLowerCase().contains("request=getmap"))
            heightService = new WmsRequest( heightServiceURL);
        else if (heightServiceURL.toLowerCase().contains("request=getcoverage"))
            heightService = new WcsRequest( heightServiceURL);

        textA = (TextView)findViewById(R.id.textA);
        textB = (TextView)findViewById(R.id.textB);
        textC = (TextView)findViewById(R.id.textC);
        textD = (TextView)findViewById(R.id.textD);
        textD1 = (TextView)findViewById(R.id.textD1);
        textE = (TextView)findViewById(R.id.textE);
        textF = (TextView)findViewById(R.id.textF);
        textG = (TextView)findViewById(R.id.textG);
        textA.setText("");textB.setText("");textC.setText("");textD.setText("");textD1.setText("");textE.setText("");textF.setText("");

        deviceHeightEdit        = (EditText)findViewById(R.id.deviceHeight);
        demZigmaEdit            = (EditText)findViewById(R.id.demZigma);
        mapCalibrationZigmaEdit = (EditText)findViewById(R.id.mapCalibrationZigma);
        pointCloudZigmaEdit     = (EditText)findViewById(R.id.pointCloudZigma);
        deviceHeightEdit.setOnEditorActionListener(editorActionListener);
        demZigmaEdit.setOnEditorActionListener(editorActionListener);
        mapCalibrationZigmaEdit.setOnEditorActionListener(editorActionListener);
        pointCloudZigmaEdit.setOnEditorActionListener(editorActionListener);


        surfaceView = (GLSurfaceView)findViewById(R.id.surfaceview);
        app = (BorderGoApp) getApplication();

        app.setScene(new ArScene());

        DataLogger logger = app.getDataLogger();
        if(!logger.hasLog(BorderGoApp.LoggNames.LAT_GPS)){
            logger.startLog(BorderGoApp.LoggNames.Z_TIMESTAMPED_DATA, DataLogger.LogTypes.TIME_SERIES_DOUBLE);
            logger.startLog(BorderGoApp.LoggNames.LAT_GPS, DataLogger.LogTypes.DOUBLE);
            logger.startLog(BorderGoApp.LoggNames.LNG_GPS, DataLogger.LogTypes.DOUBLE);
            logger.startLog(BorderGoApp.LoggNames.LAT_POP, DataLogger.LogTypes.DOUBLE);
            logger.startLog(BorderGoApp.LoggNames.LNG_POP, DataLogger.LogTypes.DOUBLE);
            logger.startLog(BorderGoApp.LoggNames.ZIG_POP, DataLogger.LogTypes.DOUBLE);


            logger.startLog(BorderGoApp.LoggNames.ALT_INTERPOLATED, DataLogger.LogTypes.DOUBLE);


        }


        Geodesy.initHREF(app.getApplicationContext());

        Intent intent = new Intent(this, TangoService.class);
        bindService(intent, tangoConnection, Context.BIND_AUTO_CREATE);



        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {


                @Override
                public void onDisplayAdded(int displayId) {
                }


                @Override
                public void onDisplayRemoved(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (this) {
                        setDisplayRotation();
                    }
                }
            }, null);
        }

        setupRenderer();
        initGuiUpdater();

        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return true;
            }
        });

        gestureDetector = new GestureDetectorCompat(this, new MyGestureListener());

    }

    protected void onDestroy() {
        super.onDestroy();

        if (isBound) {
            unbindService(tangoConnection);
        }
    }

    final Handler mHandler = new Handler();
    final Runnable updateBGStateUI = new Runnable() {
        public void run() {

            BGState s = BorderGoApp.getBGState();
            updateTangoValuesInBGState(s);
            textA.setText("Altitude from grid       :" + String.format("%.6f", s.altInterpolated));
            textB.setText("Altitude from 'pop/tango':" + String.format("%.6f", s.altTango));
            textC.setText("Lat from 'pop/tango'     :" + String.format("%.6f", s.latTango));
            textD.setText("Lng from 'pop/tango'     :" + String.format("%.6f", s.lngTango));
            textD1.setText("Accuracy 'pop/tango'     :" + String.format("%.6f", s.zigTango));
            textE.setText("Lat gps                  :" + String.format("%.6f", s.latGPS));
            textF.setText("Lng gps                  :" + String.format("%.6f", s.lngGPS));
            textF.setText("Accuracy gps             :" + String.format("%.6f", s.zigGPS));
            textG.setText("Altitude gps             :" + String.format("%.6f", s.altGPS));



        }
    };

    private void updateTangoValuesInBGState(BGState s){
        if((tangoService != null) && tangoService.isConnected()){

            Location l = tangoService.getPositionOrientationProvider().getLocation();
            if(l != null){
                s.latTango = l.getLatitude();
                s.lngTango = l.getLongitude();
                s.altTango = l.getAltitude();
                s.zigTango = l.getAccuracy();
            }

        }
    }

    private void updateGUI(){
        mHandler.post(updateBGStateUI);
    }

    private void initGuiUpdater(){

        Timer myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {updateGUI();}
        }, 0, 500);

    }

    @Override
    protected void onResume() {
        Log.d(getClass().getSimpleName(), "onResume()");
        super.onResume();
        surfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }


    @Override
    protected void onStart() {
        Log.d(getClass().getSimpleName(), "onStart()");
        super.onStart();
        surfaceView.onResume();

        textA = (TextView)findViewById(R.id.textA);

        textA.setText("");
        // Set render mode to RENDERMODE_CONTINUOUSLY to force getting onDraw callbacks until
        // the Tango service is properly set-up and we start getting onFrameAvailable callbacks.
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        setDisplayRotation();
    }

    @Override
    protected void onStop() {
        Log.d(getClass().getSimpleName(), "onStop()");
        super.onStop();
        surfaceView.onPause();

        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        // NOTE: DO NOT lock against this same object in the Tango callback thread. Tango.disconnect
        // will block here until all Tango callback calls are finished. If you lock against this
        // object in a Tango callback thread it will cause a deadlock.


        synchronized (this) {
            try {
                // isConnected = false;
                if (tangoService != null)
                    tangoService.getTango().disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                // We need to invalidate the connected texture ID so that we cause a
                // re-connection in the OpenGL thread after resume.
                connectedTextureIdGlThread = INVALID_TEXTURE_ID;
                scene.forceSceneUpdate();
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }

    }


    class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final String DEBUG_TAG = "Gestures";

        @Override
        public void onLongPress (MotionEvent event) {
            tangoService.snapCloud();
            Log.d(DEBUG_TAG,"onLongPress : " + event.toString());
        }
    }


    /**
     * Asynchronous data loader. Also generates graphical objects for the loaded data.
     */
    private class LoadDataTask extends AsyncTask<OriginData, Void, Integer> {

        @Override
        protected Integer doInBackground(OriginData... origins) {
            OriginData origin = origins[0];

            Context context = app.getApplicationContext();

            scene.clearScene();


            try {
                // Load grid
                DTMGridProvider grid_provider = new DTMGridProvider();
                DTMGrid grid = grid_provider.get_grid(context, origin.lat_0, origin.lon_0, heightService);
                grid.setOrigin(origin);
                tangoService.setDtmGrid(grid);

                // Create an artificial terrain point at the feet of the user
                FloatBuffer artificialPoint = FloatBuffer.allocate(4);
                artificialPoint.clear();
                artificialPoint.put(0).put(0).put(-tangoService.getDeviceHeight()).put(1).rewind();
                ((TangoPositionOrientationProvider) tangoService.getPositionOrientationProvider()).handlePointCloudObservation(1, artificialPoint, grid, tangoService.getDemSigma());

                // Create gridlines for visualization
                float height = (float) origin.h_0;
                int w = (int) grid.getCols();
                int h = (int) grid.getRows();
                Pos[] gridPositions = new Pos[w * h];

                // calculate all positions
                for (int i = 0; i < h; i++) {
                    for (int j = 0; j < w; j++) {
                        int index = i * w + j;
                        Pos p = new Pos();
                        p.z = grid.gridValue(j, i) - height;
                        p.x = grid.gridToWorldX(j, i);
                        p.y = grid.gridToWorldY(j, i);

                        gridPositions[index] = p;
                    }
                }

                // Create lines to draw
                drawPolyLineGrid(gridPositions, w, h);

                // Compute grid indexes
                short [] indexes = new short[(w-1)*(h-1)*2*3];
                int ix = 0;
                for (int i = 0; i < h-1; i++) {
                    for (int j = 0; j < w-1; j++) {
                        short i00 = (short)(i * w + j);
                        short i10 = (short)((i+1) * w + j);
                        short i01 = (short)(i * w + j + 1);
                        short i11 = (short)((i+1) * w + j + 1);
                        indexes[ix++] = i00;
                        indexes[ix++] = i10;
                        indexes[ix++] = i01;
                        indexes[ix++] = i11;
                        indexes[ix++] = i01;
                        indexes[ix++] = i10;
                    }
                }

                // Create terrain surface to draw (used for hidden gridline removal)
                IndexedTriangleMesh mesh = new IndexedTriangleMesh();
                mesh.positions = gridPositions;
                mesh.indexes = indexes;
                scene.addDepthSurface(mesh);
            }
            catch (IOException ex) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        messageBox("Error loading grid", "Is network enabled?");
                    }
                });
                Log.e(TAG, "Error in loading of grid", ex);
            }


            // Read matrikkel-data:
            String wfsUser = BorderGoApp.config.getConfigValue(Config.Keys.WFS_USER);
            String wfsPass = BorderGoApp.config.getConfigValue(Config.Keys.WFS_PASSWRD);
            String wfsUrl = BorderGoApp.config.getConfigValue(Config.Keys.WFS_BASE_URL);
            DataToDrawProvider data_provider = new DataToDrawProvider(tangoService.getDtmGrid(), wfsUser,wfsPass,wfsUrl);
            DataToDrawProvider.DataToDraw data =  data_provider.getData(context, origin.lat_0, origin.lon_0);

            // Draw polylines:

            int num_polylines = 0;

            for ( int i=0; i<data._objects.size() ; i++ ){

                ArrayList<Pos> positions = new ArrayList<Pos>();
                for ( int j=0 ; j<data._objects.get(i)._points.size() ; j++ ){

                    DataToDrawProvider.DataPoint P1 = data._objects.get(i)._points.get(j);


                    double north = origin.latitudeToLocalOrigin(P1._x[0]);
                    double east  = origin.longitudeToLocalOrigin(P1._x[1]);
                    double alt = origin.heightToLocalOrigin(P1._x[2]);


                    if ( Math.abs(north) < cutoff && Math.abs(east) < cutoff && Math.abs(alt) < cutoff ) {
                        Pos p = new Pos();
                        p.x = (float)east;
                        p.y = (float)north;
                        p.z = (float)alt;
                        positions.add(p);
                    } else {
                        // find out if number of we have a viable polyline
                        if(positions.size()>=2){
                            scene.addPolyLine(positions.toArray(new Pos[positions.size()]));
                            positions = new ArrayList<Pos>();
                        }
                    }
                }

                if(positions.size() >= 2){ // needs at least two points to draw polyline.
                    scene.addPolyLine(positions.toArray(new Pos[positions.size()]), ArScene.BORDER_COLOR, ArScene.BORDER_WIDTH);
                    num_polylines++;
                }
            }

            // Draw singlepoints:

            for ( int i=0 ; i<data._points.size() ; i++ ) {
                double north = origin.latitudeToLocalOrigin(data._points.get(i)._x[0]);
                double east  = origin.longitudeToLocalOrigin(data._points.get(i)._x[1]);
                double alt = origin.heightToLocalOrigin(data._points.get(i)._x[2]);
                Pos p = new Pos();
                p.x = (float)east;
                p.y = (float)north;
                p.z = (float)alt;
                scene.addPos(p,ArScene.BORDER_POINT_COLOR, ArScene.BORDER_POINT_WIDTH);
            }

            return num_polylines;
        }

        protected void onPostExecute(Integer num_polylines) {
            String mess = String.format("GNSS ok: %d polylines loaded", num_polylines);
            Toast.makeText(app.getApplicationContext(), mess, Toast.LENGTH_SHORT).show();
        }
    }

    private static int cutoff = 1000; // distance we dont want want to display data for.
    private OriginUpdateListener originUpdateListener = new OriginUpdateListener() {
        @Override
        public void originChanged(OriginData origin) {
            BGState st = BorderGoApp.getBGState();
            st.latTango = origin.lat_0;
            st.lngTango = origin.lon_0;
            st.altTango = origin.h_0;
            st.zigTango = 0;

            new LoadDataTask().execute(origin);
        }
    };

    /**
     * Draw gridlines based on a list of gridpoints
     *
     * @param grid
     * @param w
     * @param h
     */
    private void drawPolyLineGrid(Pos[] grid, int w, int h){
        // Rows "east/west"
        Pos[] positions = new Pos[2*w*h];
        if(grid.length == w*h){
            int f = 1; // forward
            int b = 0; // backward
            int index = 0;
            int resIndex = 0;
            for(int i = 0;i<h;i++){
                if(i%2 == 0){ // to the "east"
                    f = 1;
                    b = 0;
                } else { // the the "west"
                    f = 0;
                    b = 1;
                }

                for(int j = 0;j<w;j++){
                    index = w*i + f*j + b*(w-j-1);
                    positions[resIndex] = grid[index];
                    resIndex++;
                }
            }

            // Columns "north/south"

            for(int i = 0;i<w;i++){
                if(i%2 == 0){ // go down the column
                    f = 1;
                    b = 0;
                } else { // go up the columen
                    f = 0;
                    b = 1;
                }
                for(int j = 0;j<h;j++){
                    index = f*w*j + b*w*(h-j-1) + i;
                    positions[resIndex] = grid[index];
                    resIndex++;
                }
            }
            scene.addHiddenPolyLine(positions, new GlColor(){{r=0f;g=0.6f;b=0f; a=0.1f;}}, ArScene.LINE_WIDTH/4);

        } else {
            Log.i("MainActivity", "drawPolyLineGrid:Faulty gridlength" );
        }
    }


    private void setDisplayRotation() {
        Display display = getWindowManager().getDefaultDisplay();
        displayRotation = display.getRotation();

        // We also need to update the camera texture UV coordinates. This must be run in the OpenGL
        // thread.
        surfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (isBound) {
                    renderer.updateColorCameraTextureUv(displayRotation);
                }
            }
        });
    }


    /**
     * Check to see that we have the necessary permissions for this app.
     */
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

    /**
     * Request the necessary permissions for this app.
     */
    private void requestPermission_Camera() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION)) {
            showRequestPermissionRationale_Camera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{CAMERA_PERMISSION},
                    CAMERA_PERMISSION_CODE);
        }
    }

    private void requestPermission_Location() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, LOCATION_PERMISSION)) {
            showRequestPermissionRationale_Location();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{LOCATION_PERMISSION},
                    LOCATION_PERMISSION_CODE);
        }
    }

    private void requestPermission_Storage() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, WRITE_EXTERNAL_STORAGE)) {
            showRequestPermissionRationale_Location();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{WRITE_EXTERNAL_STORAGE},
                    WRITE_EXTERNAL_STORAGE_CODE);
        }
    }

    /**
     * If the user has declined the permission before, we have to explain that the app needs this
     * permission.
     */
    private void showRequestPermissionRationale_Camera() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("Border GO requires camera permission")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{CAMERA_PERMISSION}, CAMERA_PERMISSION_CODE);
                    }
                })
                .create();
        dialog.show();
    }

    private void showRequestPermissionRationale_Location() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("Border GO requires location permission")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{LOCATION_PERMISSION}, LOCATION_PERMISSION_CODE);
                    }
                })
                .create();
        dialog.show();
    }

    /**
     * Result for requesting camera permission.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (hasCameraPermission() && hasLocationPermission()) {
            // bindTangoService();
            // requestLocationUpdatesFromProvider();
        } else {
            Toast.makeText(this, "BORDER GO requires camera and location permission",
                    Toast.LENGTH_LONG).show();
        }
    }


    /**
     * Display toast on UI thread.
     *
     * @param resId The resource id of the string resource to use. Can be formatted text.
     */
    private void showsToastAndFinishOnUiThread(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this,
                        getString(resId), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }



    public void showDataView(View view){
        DataLogger logger =  app.getDataLogger();

        this.startActivity(new Intent(MainActivity.this,DataActivity.class));
    }

    private static float alpha = 0.6f;
    private GlColor coolBlue = new GlColor(){{r=0.2f; g=0.4f; b=1f; a=alpha;}};
    private GlColor brightOrange = new GlColor(){{r=1f; g=0.6f; b=0f; a=alpha;}};
    private GlColor limeGreen = new GlColor(){{r=0.3f; g=1f; b=0f; a=alpha;}};
    private GlColor red = new GlColor(){{r=1f; g=0; b=0; a=alpha;}};
    private GlColor yellow = new GlColor(){{r=1f; g=1; b=0; a=alpha;}};

    /**
     * Start the map-view for manual calibration
     *
     * @param view
     */
    public void showMap(View view){
        final Intent i = new Intent(MainActivity.this, MapsActivity.class);
        Location loc = tangoService.getPositionOrientationProvider().getLocation();
        if(loc!=null){
            Double lat = loc.getLatitude();
            Double lng = loc.getLongitude();
            i.putExtra("Lat",lat);
            i.putExtra("Lng",lng);
        }

        String baseUrl = BorderGoApp.config.getConfigValue(Config.Keys.KARTVERKET_NORGESKART_URL);
        String token =     BorderGoApp.config.getConfigValue(Config.Keys.KARTVERKET_TOKEN_KEY);


        TokenServiceRepository tokenService =  TokenServiceRepository.newInstance(baseUrl);

        try {
            tokenService.getSessionKeyAsync(token, new ITokenServiceRepository.SessionKeyCallback() {
                @Override
                public void onSuccess(@Nullable String response) {
                    app.setOrthoSessionKey(response);
                    startMapCalibration(i);
                }

                @Override
                public void onError(Throwable error, int code) {
                    //TODO: display error message to user?
                    startMapCalibration(i);
                }


            });
        } catch (IOException e) {
            e.printStackTrace();
            startMapCalibration(i);
        }
    }

    private void startMapCalibration(Intent i ){
        this.startActivity(i);
    }

    public void showMenu(View view){
        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        drawer.openDrawer(Gravity.RIGHT);
    }

    // DEFAULT UNCERTAINTY/ZIGMA VALUES FOR DIFFERENT SOURCES // TODO?: get defaults from config_strings.txt ?
    private static final float D_DEVICE_HEIGHT = 1.0f;
    private static final float D_DEM_SIGMA = 1.5f;
    private static final float D_MAP_CALIBRATION_ZIGMA = 0.3f;
    private static final float D_POINT_CLOUD_ZIGMA = 0.3f;

    public void resetToDefault(View view){
        // update service
        tangoService.setDemSigma(D_DEM_SIGMA);
        tangoService.setDeviceHeight(D_DEVICE_HEIGHT);
        tangoService.setMapCalibrationSigma(D_MAP_CALIBRATION_ZIGMA);
        tangoService.setPointCloudSigma(D_POINT_CLOUD_ZIGMA);

        // update  UI
        deviceHeightEdit.setText(String.valueOf(D_DEVICE_HEIGHT));
        demZigmaEdit.setText(String.valueOf(D_DEM_SIGMA));
        mapCalibrationZigmaEdit.setText(String.valueOf(D_MAP_CALIBRATION_ZIGMA));
        pointCloudZigmaEdit.setText(String.valueOf(D_POINT_CLOUD_ZIGMA));

        // update preferences
        SharedPreferences settings = getSharedPreferences(BorderGoApp.PREFERENCE_FILE, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putFloat(BorderGoApp.PrefNames.DTM_ZIGMA ,D_DEM_SIGMA);
        editor.putFloat(BorderGoApp.PrefNames.DEVICE_HEIGHT ,D_DEVICE_HEIGHT);
        editor.putFloat(BorderGoApp.PrefNames.MAP_CALIBRATION_ZIGMA ,D_MAP_CALIBRATION_ZIGMA);
        editor.putFloat(BorderGoApp.PrefNames.POINT_CLOUD_ZIGMA ,D_POINT_CLOUD_ZIGMA);
        editor.commit();
    }

    public void updateFromPreferencesOrDefault(){
        SharedPreferences settings = getSharedPreferences(BorderGoApp.PREFERENCE_FILE, 0);

        // assign from preferences or fallback to default values
        float deviceHeight = settings.getFloat(BorderGoApp.PrefNames.DEVICE_HEIGHT, D_DEVICE_HEIGHT);
        float demZigma = settings.getFloat(BorderGoApp.PrefNames.DTM_ZIGMA, D_DEM_SIGMA);
        float mapCalibrationZigma = settings.getFloat(BorderGoApp.PrefNames.MAP_CALIBRATION_ZIGMA, D_MAP_CALIBRATION_ZIGMA);
        float pointCloudZigma = settings.getFloat(BorderGoApp.PrefNames.POINT_CLOUD_ZIGMA, D_POINT_CLOUD_ZIGMA);

        // update service
        tangoService.setDeviceHeight(deviceHeight);
        tangoService.setDemSigma(demZigma);
        tangoService.setMapCalibrationSigma(mapCalibrationZigma);
        tangoService.setPointCloudSigma(pointCloudZigma);

        // update UI
        deviceHeightEdit.setText(String.valueOf(deviceHeight));
        demZigmaEdit.setText(String.valueOf(demZigma));
        mapCalibrationZigmaEdit.setText(String.valueOf(mapCalibrationZigma));
        pointCloudZigmaEdit.setText(String.valueOf(pointCloudZigma));
    }

    private TextView.OnEditorActionListener editorActionListener = new TextView.OnEditorActionListener() {

        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            boolean retval = false;

            try {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    EditText textWid = (EditText) v;
                    String valueStr = textWid.getText().toString();
                    float value = Float.parseFloat(valueStr);
                    String pref = "_";

                    // update service
                    switch (v.getId()) {
                        case R.id.deviceHeight:
                            tangoService.setDeviceHeight(value);
                            pref = BorderGoApp.PrefNames.DEVICE_HEIGHT;
                            retval = true;
                            break;
                        case R.id.demZigma:
                            tangoService.setDemSigma(value);
                            pref = BorderGoApp.PrefNames.DTM_ZIGMA;
                            retval = true;
                            break;
                        case R.id.mapCalibrationZigma:
                            tangoService.setMapCalibrationSigma(value);
                            pref = BorderGoApp.PrefNames.MAP_CALIBRATION_ZIGMA;
                            retval = true;
                            break;
                        case R.id.pointCloudZigma:
                            tangoService.setPointCloudSigma(value);
                            pref = BorderGoApp.PrefNames.POINT_CLOUD_ZIGMA;
                            retval = true;
                            break;
                    }

                    // update preference
                    SharedPreferences settings = getSharedPreferences(BorderGoApp.PREFERENCE_FILE, 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putFloat(pref ,value);
                    editor.commit();
                }

            }
            catch (Exception ex) {
                Log.e(TAG, "EditorListener", ex);
            }

            hideKeyboard();
            return retval;
        }


    };

    private void hideKeyboard(){
        View view = getCurrentFocus();

        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }


    private void shortToast(String message){
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    public void showInfoBox(View view) {
        Button button = (Button)view;
        button.setText("Skjul måleverdier");
        button.setOnClickListener(new View.OnClickListener() {
                                      @Override
                                      public void onClick(View v) {
                                          hideInfoBox(v);
                                      }
                                  });
        LinearLayout infoBox = (LinearLayout) findViewById(R.id.infoBox);
        infoBox.setVisibility(View.VISIBLE);

    }

    public void hideInfoBox(View view) {
        Button button = (Button)view;
        button.setText("Vis måleverdier");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInfoBox(v);
            }
        });
        LinearLayout infoBox = (LinearLayout)findViewById(R.id.infoBox);
        infoBox.setVisibility(View.INVISIBLE);
    }

    public void toggleLineRemoval(View view){
        Button button = (Button)view;
        if(this.scene.hasHiddenLineRemoval()){
            //button.setText("Høydegrid: vis bakside");
            button.setText("Høydegrid: skjul bakside");
            this.scene.setHiddenLineRemoval(false);
        } else {
            //button.setText("Høydegrid: skjul bakside");
            button.setText("Høydegrid: vis bakside");
            this.scene.setHiddenLineRemoval(true);
        }

    }

    /**
     * Initialize 3D rendering. Called in {@link #onCreate(Bundle)}
     */
    private void setupRenderer() {
        surfaceView.setEGLContextClientVersion(2);


        scene = app.getScene();

        renderer = new ArGlRenderer(this,scene,
                new ArGlRenderer.RenderCallback() {
                    private double lastRenderedTimeStamp;

                    @Override
                    public void preRender() {
                        // This is the work that you would do on your main OpenGL render thread.

                        try {
                            // Synchronize against concurrently disconnecting the service triggered
                            // from the UI thread.
                            synchronized (MainActivity.this) {
                                // We need to be careful not to run any Tango-dependent code in the
                                // OpenGL thread unless we know the Tango Service is properly
                                // set up and connected.
                                if (!isBound || tangoService == null || !tangoService.isConnected()) {
                                    return;
                                }

                                // Set up scene camera projection to match RGB camera intrinsics.
                                if (!renderer.isProjectionMatrixConfigured()) {
                                    TangoCameraIntrinsics intrinsics =
                                            TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(
                                                    TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                                    displayRotation);
                                    renderer.setProjectionMatrix(ArGlRenderer.projectionMatrixFromCameraIntrinsics(intrinsics));
                                            //projectionMatrixFromCameraIntrinsics(intrinsics));
                                }
                                // Connect the Tango SDK to the OpenGL texture ID where we are
                                // going to render the camera.
                                // NOTE: This must be done after both the texture is generated
                                // and the Tango Service is connected.
                                if (connectedTextureIdGlThread != renderer.getTextureId()) {
                                    tangoService.getTango().connectTextureId(
                                            TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                            renderer.getTextureId());
                                    connectedTextureIdGlThread = renderer.getTextureId();
                                    Log.d(TAG, "connected to texture id: " +
                                            renderer.getTextureId());
                                }
                                // If there is a new RGB camera frame available, update the texture
                                // and scene camera pose.
                                if (isFrameAvailableTangoThread.compareAndSet(true, false)) {
                                    // {@code mRgbTimestampGlThread} contains the exact timestamp at
                                    // which the rendered RGB frame was acquired.
                                    rgbTimestampGlThread =
                                            tangoService.getTango().updateTexture(TangoCameraIntrinsics.
                                                    TANGO_CAMERA_COLOR);

                                    // Get the transform from color camera to Start of Service
                                    // at the timestamp of the RGB image in OpenGL coordinates.
                                    //
                                    // When drift correction mode is enabled in config file, we need
                                    // to query the device with respect to Area Description pose in
                                    // order to use the drift-corrected pose.
                                    //
                                    // Note that if you don't want to use the drift corrected pose,
                                    // the normal device with respect to start of service pose is
                                    // still available.
                                    TangoSupport.TangoMatrixTransformData transform =
                                            TangoSupport.getMatrixTransformAtTime(
                                                    rgbTimestampGlThread,
                                                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                                    displayRotation);
                                    if (transform.statusCode == TangoPoseData.POSE_VALID) {
                                        PositionOrientationProvider pop = tangoService.getPositionOrientationProvider();
                                        textA = (TextView)findViewById(R.id.textA);
                                        if(tangoService.getDtmGrid() != null){
                                            BGState st = BorderGoApp.getBGState();
                                            Location loc = tangoService.getPositionOrientationProvider().getLocation();
                                            double lat = loc.getLatitude();
                                            double lng = loc.getLongitude();
                                            double zig = loc.getAccuracy();

                                            double h = tangoService.getDtmGrid().getInterpolatedAltitude(lat,lng);
                                            st.altInterpolated = (h == Double.NEGATIVE_INFINITY ? 0 : h);
                                            st.altTango = loc.getAltitude();
                                            st.latTango = lat;
                                            st.lngTango = lng;
                                            st.zigTango = zig;

                                            DataLogger logger = app.getDataLogger();
                                            logger.log(BorderGoApp.LoggNames.ALT_INTERPOLATED, h);
                                            logger.log(BorderGoApp.LoggNames.LAT_POP, lat);
                                            logger.log(BorderGoApp.LoggNames.LNG_POP, lng);
                                            logger.log(BorderGoApp.LoggNames.ZIG_POP, zig);

                                        }


                                        scene.setTangoWorldMatrix(pop.getTransformationMatrix());


                                        renderer.updateViewMatrix(transform.matrix);

                                        double deltaTime = rgbTimestampGlThread
                                                - lastRenderedTimeStamp;
                                        lastRenderedTimeStamp = rgbTimestampGlThread;


                                    } else {
                                        // When the pose status is not valid, it indicates tracking
                                        // has been lost. In this case, we simply stop rendering.
                                        //
                                        // This is also the place to display UI to suggest that the
                                        // user walk to recover tracking.


                                    }
                                }
                            }
                            // Avoid crashing the application due to unhandled exceptions.
                        } catch (TangoErrorException e) {
                            Log.e(TAG, "Tango API call error within the OpenGL render thread", e);
                        } catch (Throwable t) {
                            Log.e(TAG, "Exception on the OpenGL thread", t);
                        }
                    }
                });

        surfaceView.setRenderer(renderer);

    }

    private void messageBox(String title, String message) {
        // TODO Auto-generated method stub
        Log.d("EXCEPTION: " + title,  message);

        AlertDialog.Builder message_box = new AlertDialog.Builder(this);
        message_box.setTitle(title);
        message_box.setMessage(message);
        message_box.setCancelable(false);
        message_box.setNeutralButton("OK", null);
        message_box.show();
    }


}
