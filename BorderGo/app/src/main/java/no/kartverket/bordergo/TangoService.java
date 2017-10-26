package no.kartverket.bordergo;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.projecttango.tangosupport.TangoSupport;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Calendar;

import no.kartverket.data.dtm.DTMGrid;
import no.kartverket.data.DataLogger;
import no.kartverket.positionorientation.PositionOrientationProvider;
import no.kartverket.positionorientation.TangoPositionOrientationProvider;

/**
 * Independent service handling the tango system, and the transformations between the tango system
 * and external geographic coordinates. The TangoService should be independent of specific
 * activities, as it us used by several, and we would like the Tango reference frames to persist
 * when activities are started and stopped - destroyed and recreated.
 * <p>
 * Created by runaas on 25.08.2017.
 */

public class TangoService extends Service {
    public final static String TAG = TangoService.class.getSimpleName();

    private BorderGoApp app;

    private Object tangoLock = new Object();
    private Tango tango;
    private TangoConfig tangoConfig;
    private boolean isConnected = false;
    private TangoPositionOrientationProvider positionOrientationProvider;

    private DTMGrid dtmGrid;


    private LocationManager location_manager;
    private SensorManager sensor_manager;
    private Sensor rotationVectorSensor;

    private float  deviceHeight;
    private float  demSigma;
    private float  mapCalibrationSigma;
    private float  pointCloudSigma;

    // Snap a point cloud
    private volatile boolean snapCloud = false;

    public void snapCloud() {
        snapCloud = true;
    }

    public boolean isConnected() { return isConnected; }

    public float getDeviceHeight() {
        return deviceHeight;
    }

    public void setDeviceHeight(float deviceHeight) {
        this.deviceHeight = deviceHeight;
    }

    public float getDemSigma() {
        return demSigma;
    }

    public void setDemSigma(float demSigma) {
        this.demSigma = demSigma;
    }

    public float getMapCalibrationSigma() {
        return mapCalibrationSigma;
    }

    public void setMapCalibrationSigma(float mapCalibrationSigma) {
        this.mapCalibrationSigma = mapCalibrationSigma;
    }

    public float getPointCloudSigma() {
        return pointCloudSigma;
    }

    public void setPointCloudSigma(float pointCloudSigma) {
        this.pointCloudSigma = pointCloudSigma;
    }

    /**
     * For rendering of camera images provided by Tango, should be implemented in the 3D view
     */
    public interface FrameListener {
        /**
         * Called each time a camera image is ready from Tango. Should pass the texture and
         * initiate drawing of 3D data
         *
         * @param cameraId
         */
        public void onFrameAvailable(int cameraId);
    }

    private FrameListener frameListener;

    public void addFrameListener(FrameListener frameListener) {
        this.frameListener = frameListener;
    }

    // Binder
    private final IBinder binder = new TangoBinder();
    public class TangoBinder extends Binder {
        TangoService getService() {
            return TangoService.this;
        }
    }

    @Override
    public void onCreate() {
        app = (BorderGoApp) getApplication();

        // Get a reference to the LocationManager object.
        location_manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Get a reference to the SensorManager object
        sensor_manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationVectorSensor = sensor_manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensor_manager.registerListener(rotationListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);

        // Create PositionOrientationProvider
        positionOrientationProvider = new TangoPositionOrientationProvider();

        // Check and request camera and location permission at run time.
        //if (checkAndRequestPermissions()) {
            bindTangoService();

            requestLocationUpdatesFromProvider();
        //}



        // Reset PositionOrientationProvider
        positionOrientationProvider.reset();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        location_manager.removeUpdates(locationListener);
        sensor_manager.unregisterListener(rotationListener);

        positionOrientationProvider.onDestroy();
    }




    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public Tango getTango(){ return tango;}
    public Object getTangoLock() { return tangoLock; }

    public DTMGrid getDtmGrid() {
        return dtmGrid;
    }

    public void setDtmGrid(DTMGrid dtmGrid) {
        this.dtmGrid = dtmGrid;
    }

    private void requestLocationUpdatesFromProvider() {
        if ( location_manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                location_manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1, locationListener);
        }
    }

    /**
     * Initialize Tango Service as a normal Android Service.
     */

    private void bindTangoService() {
        // Initialize Tango Service as a normal Android Service. Since we call mTango.disconnect()
        // in onPause, this will unbind Tango Service, so every time onResume gets called we
        // should create a new Tango object.
        tango = new Tango(getApplicationContext(), new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready; this Runnable
            // will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only when there are no
            // UI thread changes involved.
            @Override
            public void run() {
                // Synchronize against disconnecting while the service is being used in the OpenGL
                // thread or in the UI thread.
                synchronized (getTangoLock()) {
                    try {
                        TangoSupport.initialize();
                        tangoConfig = setupTangoConfig(tango);
                        tango.connect(tangoConfig);
                        startupTango();
                        isConnected = true;

                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                        // showsToastAndFinishOnUiThread(R.string.exception_out_of_date);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                        // showsToastAndFinishOnUiThread(R.string.exception_tango_error);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.exception_tango_invalid), e);
                        // showsToastAndFinishOnUiThread(R.string.exception_tango_invalid);
                    }
                }
            }
        });
    }


    /**
     * Set up the callback listeners for the Tango Service and obtain other parameters required
     * after Tango connection.
     * Listen to updates from the RGB camera.
     */
    private void startupTango() {
        // No need to add any coordinate frame pairs since we aren't using pose data from callbacks.
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        tango.connectListener(framePairs, new Tango.TangoUpdateCallback() {
            FloatBuffer selectedPoints = FloatBuffer.allocate(1024);

            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                // Send the current Tango position to the TangoPositionOrientationProvider.
                ((TangoPositionOrientationProvider)getPositionOrientationProvider()).handlePoseObservation(pose);
                double z = pose.translation[TangoPoseData.INDEX_TRANSLATION_Z];
                DataLogger logger = app.getDataLogger();
                long timestamp = System.currentTimeMillis() - app.getStartTime();
                logger.log(BorderGoApp.LoggNames.Z_TIMESTAMPED_DATA, z, timestamp);
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
                if (snapCloud) {
                    // Calculate the depth camera pose at the last point cloud update.
                    TangoSupport.TangoMatrixTransformData transform =
                            TangoSupport.getMatrixTransformAtTime(pointCloud.timestamp,
                                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                    TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                                    TangoSupport.ROTATION_IGNORED);
                    float[] m = transform.matrix;
                    int numSelectedPoints = 0;
                    selectedPoints.clear();

                    for (int i = 0; i < pointCloud.numPoints; ++i) {
                        float x = pointCloud.points.get(4 * i + 0);
                        float y = pointCloud.points.get(4 * i + 1);
                        float z = pointCloud.points.get(4 * i + 2);
                        float w = pointCloud.points.get(4 * i + 3);

                        float xs = x / z;
                        float ys = y / z;

                        // Use only points at a distance of between 0.5 and 4.0m and within a cone
                        // of approximately 10 deg from the camera axis and with a
                        // "quality" of at least 0.5 (the "quality" is a weakly documented number
                        // given by the Tango system)
                        if (z > 0.5 && z < 4.0 && xs > -0.2 && ys > -0.2 && xs < 0.2 && ys < 0.2 && w > 0.5) {
                            numSelectedPoints++;
                            // If necessary, expand the point buffer
                            if (numSelectedPoints * 4 >= selectedPoints.capacity()) {
                                FloatBuffer tmp = FloatBuffer.allocate(selectedPoints.capacity() * 2);
                                tmp.clear();
                                selectedPoints.flip();
                                tmp.put(selectedPoints);
                                selectedPoints = tmp;
                            }

                            // Transform points from camera to Tango coordiante system and store in buffer
                            selectedPoints.put(x*m[0] + y*m[4] + z*m[8]  + m[12]);
                            selectedPoints.put(-(x*m[2] + y*m[6] + z*m[10] + m[14]));
                            selectedPoints.put(x*m[1] + y*m[5] + z*m[9]  + m[13]);
                            selectedPoints.put(w);
                        }
                    }

                    // Decimate down to approx. 100 points
                    int ix = 0;
                    int step = Math.max(numSelectedPoints / 100, 1);
                    for (int i = 0; i < numSelectedPoints; i += step) {
                        selectedPoints.put(4*ix,   selectedPoints.get(4*i));
                        selectedPoints.put(4*ix+1, selectedPoints.get(4*i+1));
                        selectedPoints.put(4*ix+2, selectedPoints.get(4*i+2));
                        selectedPoints.put(4*ix+3, selectedPoints.get(4*i+3));
                        ++ix;
                    }

                    ((TangoPositionOrientationProvider)getPositionOrientationProvider()).handlePointCloudObservation(
                            ix, selectedPoints, dtmGrid, (float)Math.hypot(getPointCloudSigma(), getDemSigma()));
                    snapCloud = false;

                    final int numpoint = ix;
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {

                        @Override
                        public void run() {
                            Toast.makeText(app.getApplicationContext(), "Tango point cloud: Snapped " + String.valueOf(numpoint) + " points", Toast.LENGTH_SHORT).show();
                        }
                    });

                }
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                // We are not using onTangoEvent for this app.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // Pass this on to a frame listener
                if (frameListener != null)
                    frameListener.onFrameAvailable(cameraId);

            }
        });

        requestLocationUpdatesFromProvider();
    }

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Use default configuration for Tango Service, plus color camera, low latency
        // IMU integration and drift correction.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        // NOTE: Low latency integration is necessary to achieve a precise alignment of
        // virtual objects with the RGB image and produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        // Drift correction allows motion tracking to recover after it loses tracking.
        // The drift corrected pose is available through the frame pair with
        // base frame AREA_DESCRIPTION and target frame DEVICE.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DRIFT_CORRECTION, true);

        // Recieve depth information
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);

        return config;
    }

    public PositionOrientationProvider getPositionOrientationProvider() {
        return positionOrientationProvider;
    }

    /**
     * Rotation listener is used to find the angular difference between the Tango coordinate system
     * and geographical north
     */
    private final SensorEventListener rotationListener = new SensorEventListener() {
        long last_t = 0;
        @Override
        public void onSensorChanged(SensorEvent event) {
            if(BorderGoApp.usesGPSAndCompass()){
                if (event.sensor == rotationVectorSensor) {
                    long curr_t = System.currentTimeMillis();
                    // Not more than every 10th second
                    if (curr_t > last_t + 5000) {
                        positionOrientationProvider.handleRotationVectorObservation(event.values);
                        last_t = curr_t;
                    }
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    /**
     * The Location listener is used for collecting GPS positions for the {@link PositionOrientationProvider}
     * where thy are matched with corresponding positions in the Tango coordiante system to estimate
     * the transform between Tango and the geographic coordiante system
     */
    private final LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            BGState st = BorderGoApp.getBGState();
            st.altGPS = location.getAltitude();
            st.latGPS = location.getLatitude();
            st.lngGPS = location.getLongitude();
            st.zigGPS = location.getAccuracy();

            loggLocation(location);
            // ((TangoPositionOrientationProvider) app.getPositionOrientationProvider()).handleLocationObservation(location);

            if(BorderGoApp.usesGPSAndCompass()){
                long T = location.getTime();
                //Calendar C = Calendar.getInstance();
                Calendar Cnow = Calendar.getInstance();
                long age = Cnow.getTimeInMillis() - T;
                if (age < 10000 && location.getAccuracy() < 50.) {  // age <10 s && accuracy < 50 m
                    positionOrientationProvider.handleLocationObservation(location);
                }
            }

        }

        private void loggLocation(Location location) {

            DataLogger logger = app.getDataLogger();

            boolean latSuccess = logger.log("Latitude_GPS",location.getLatitude());

            boolean lngSuccess = logger.log("Longitude_GPS",location.getLongitude());

            if(latSuccess && lngSuccess){
                Log.i("DataLogger", "GPS LOG SUCCESS");
            } else{
                Log.i("DataLogger", "Failed to logg GPS");
            }
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };



}
