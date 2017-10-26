package no.kartverket.positionorientation;

import android.hardware.GeomagneticField;
import android.hardware.SensorManager;
import android.location.Location;
import android.util.Log;

import com.google.atap.tangoservice.TangoPoseData;

import org.ejml.data.DMatrixRMaj;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import no.kartverket.data.dtm.DTMGrid;
import no.kartverket.geodesy.Geodesy;
import no.kartverket.geodesy.OriginData;


/**
 * Provides position and orientation information for the application.
 *
 * The the Tango system uses various device sensors (camera, distance sensors,
 * acceleration/rotation sensors etc...) to find the device position in relation to the environment,
 * and build a geometrical model of the environment. The device and environment positions are
 * determined in a local coordinate system with the x and y axis in the horizontal plane, and
 * the z axis towards zenith. The position of the origin and the horizontal orientation of the
 * coordinate system is determined my the device position and orientation when Tango si initialized.
 * <p>
 * By using data from external sensors the local Tango coordiante system can be placed and oriented in
 * relation to a global (geographic) coordinate system. For an initial determination of the position
 * and orientation parameters data from the devices compass and GPS may be added
 * through the {@link #handleRotationVectorObservation(float[])} and
 * {@link #handleLocationObservation(Location)} methods.<p>
 *
 * For further refinement manual observations may be added by pointing in a map or using the Tango
 * point cloud sensor for matching against a terrain model
 *
 * <p>
 * Created by runaas on 20.04.2017.
 */

public final class TangoPositionOrientationProvider extends PositionOrientationProvider {
    static final String TAG = TangoPositionOrientationProvider.class.getSimpleName();

    static final double EarthRadius = 6371000.0;

    private volatile float[] transformMatrix;

    // Kalman filter stuff
    private static final int numParameters = 9;

    /**
     * Apriori variance of Tango device position
     */
    private double tangoVariance = .001;
    /**
     * Timestamp of previous kalman filter prediction
     */
    private long prevCompTime = -1;

    private OriginData origin;
    private double geoidH_0;

    private DMatrixRMaj z_tango, R_tango, H_tango;
    /**
     * Simple Kalman filter for smoothing Tango position information and computing
     * speed, travel direction and predicted positions
     */
    private KalmanFilter kalmanFilter = new KalmanFilter(numParameters);

    /**
     * List of observations used in determining the transformation between TAngo and external coordiantes
     */
    private CopyOnWriteArrayList<Transform.Observation> observations = new CopyOnWriteArrayList<Transform.Observation>();

    // Logging of directions
    private float declination;
    private float compass_dir, tango_dir = Float.MAX_VALUE, compass_az;

    // The transform
    private Transform xform = new Transform();
    private volatile boolean xformOk = false;
    private volatile Location lastLocation = null;


    private class PrivPredictionMatrixUpdater implements KalmanFilter.PredictionMatrixUpdater {
        /**
         * Time interval since last prediction
         */
        public double T;

        public void updateMatrix(DMatrixRMaj F, DMatrixRMaj Q) {
            final double var = 0.01;

            // F - predict transition model matrix
            for (int i = 0; i < 3; ++i) {
                F.set(i, 3 + i, T);
                F.set(3 + i, 6 + i, T);
                F.set(i, 6 + i, 0.5 * T * T);
            }

            // Q - predict covariance matrix
            // Wiener process "Piecewice constant acceleration" noise
            double a00 = (1.0 / 4.0) * T * T * T * T * var;
            double a01 = (1.0 / 2.0) * T * T * T * var;
            double a02 = (1.0 / 2.0) * T * T * var;
            double a11 = T * T * var;
            double a12 = T * var;
            double a22 = var;

            for (int i = 0; i < 3; i++) {
                Q.set(i, i, a00);
                Q.set(i, 3 + i, a01);
                Q.set(i, 6 + i, a02);
                Q.set(3 + i, 3 + i, a11);
                Q.set(3 + i, 6 + i, a12);
                Q.set(6 + i, 6 + i, a22);
            }

            // Symmetric expansion
            for (int y = 1; y < 9; y++) {
                for (int x = 0; x < y; x++) {
                    Q.set(y, x, Q.get(x, y));
                }
            }
        }
    }

    private PrivPredictionMatrixUpdater predictionMatrixUpdater =
            new PrivPredictionMatrixUpdater();


    /**
     * Create and initialize
     */
    public TangoPositionOrientationProvider() {
        // Number of parameters and observations
        int numTangoObservations = 3;

        // Tango observation vector
        z_tango = new DMatrixRMaj(numTangoObservations, 1);

        // Tango observation covariance matrix
        R_tango = new DMatrixRMaj(numTangoObservations, numTangoObservations);
        for (int i = 0; i < numTangoObservations; ++i)
            R_tango.set(i, i, tangoVariance);

        // Tango observation model matrix
        H_tango = new DMatrixRMaj(numTangoObservations, numParameters);
        H_tango.set(0, 0, 1);
        H_tango.set(1, 1, 1);
        H_tango.set(2, 2, 1);

        reset();

        transformWorkerThread = new Thread(transformWorker, "TransformWorker");
        transformWorkerThread.start();
    }

    public void onDestroy() {
        synchronized (transformWorker) {
            transformWorker.running = false;
            transformWorker.notifyAll();
        }
    }


    @Override
    public void reset() {
        // Initialize
        double[] x_init = {
                0, 0, 0,
                0, 0, 0,
                0, 0, 0};
        double[] P_init = {
                0.01, 0.01, 0.01,
                0.2, 0.2, 0.2,
                0.1, 0.1, 0.1};

        kalmanFilter.initialize(x_init, P_init);

        observations.clear();
        prevCompTime = -1;

        origin = null;

        lastLocation = null;

        transformMatrix = new float[] {
                1,0,0,0,
                0,1,0,0,
                0,0,1,0,
                0,0,0,1
        };
    }

    @Override
    public float[] getTransformationMatrix() {
        return transformMatrix;
    }

    private Thread transformWorkerThread;
    private TransformWorker transformWorker = new TransformWorker();

    /**
     * Worker run in the background, recomputing the transform parameters when new data is present
     */
    private class TransformWorker implements Runnable {
        volatile boolean running = true;

        @Override
        public void run() {
            int prevObsSize = 0;
            boolean sufficientObs = false;
            while (running) {
                synchronized (this) {
                    try {
                        wait(5000);
                    } catch (InterruptedException e) {

                    }
                }
                if (!running)
                    break;

                // Check if number of observations have changed
                if (observations.size() == prevObsSize)
                    continue;
                prevObsSize = observations.size();

                // Check if we have at least one 3D position and one orientation observation
                if (!sufficientObs) {
                    boolean hasPos = false;
                    boolean hasOrient = false;
                    for (Transform.Observation obs : observations) {
                        if (obs instanceof Transform.PositionObservation3D)
                            hasPos = true;
                        if (obs instanceof Transform.OrientationObservation)
                            hasOrient = true;
                        if (hasPos && hasOrient) {
                            sufficientObs = true;
                            break;
                        }
                    }
                }
                if (!sufficientObs)
                    continue;

                // Do the work
                xform.adjust(new ArrayList<Transform.Observation>(observations));

                // Don't accept transform unless estimated standard deviations of parameters are within limits
                xformOk = Math.sqrt(xform.xySigma2()) < 10.0 &&
                        Math.sqrt(xform.zSigma2()) < 20.0 &&
                        Math.sqrt(xform.azSigma2()) < 0.5;

                if (xformOk) {
                    // Create a new transform matrix
                    transformMatrix = new float[]{
                            (float) xform.cosAz(), -(float) xform.sinAz(), 0, 0,
                            (float) xform.sinAz(), (float) xform.cosAz(), 0, 0,
                            0, 0, 1, 0,
                            (float) (-xform.x0() * xform.cosAz() - xform.y0() * xform.sinAz()),
                            (float) (xform.x0() * xform.sinAz() - xform.y0() * xform.cosAz()),
                            -(float) xform.z0(), 1
                    };
                }

                // Log results
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(" N ");
                stringBuilder.append(observations.size());
                stringBuilder.append(" x ");
                stringBuilder.append(xform.x0());
                stringBuilder.append(" y ");
                stringBuilder.append(xform.y0());
                stringBuilder.append(" z ");
                stringBuilder.append(xform.z0());
                stringBuilder.append(" az ");
                stringBuilder.append(xform.az());

                stringBuilder.append(" xy_sd ");
                stringBuilder.append(Math.sqrt(xform.xySigma2()));
                stringBuilder.append(" az_sd ");
                stringBuilder.append(Math.sqrt(xform.azSigma2()));

                stringBuilder.append(" sigma ");
                stringBuilder.append(Math.sqrt(xform.sigma2()));

                stringBuilder.append(" compass compass_az ");
                stringBuilder.append(compass_az);

                Log.e(TAG, stringBuilder.toString());
            }
        }
    };

    @Override
    public Location getLocation() {
        synchronized (kalmanFilter) {
            if (prevCompTime < 0)
                return null;

            long currTime = System.currentTimeMillis();
            if (currTime - prevCompTime > 200) {
                // Predict
                predictionMatrixUpdater.T = (currTime - prevCompTime) / 1000.0;
                kalmanFilter.predict(predictionMatrixUpdater);
                prevCompTime = currTime;
            }

            if (!xformOk) {
                return lastLocation;
            }

            Location location = new Location(TAG);
            DMatrixRMaj x = kalmanFilter.getX();
            DMatrixRMaj P = kalmanFilter.getP();
            final double az = xform.az();
            final double x0 = xform.x0();
            final double y0 = xform.y0();
            final double z0 = xform.z0();
            final double tango_x = x.get(0, 0);
            final double tango_y = x.get(1, 0);
            final double sin_az = xform.sinAz();
            final double cos_az = xform.cosAz();

            location.setLongitude(((tango_x*cos_az - tango_y*sin_az + x0) / origin.lon_scale) + origin.lon_0);
            location.setLatitude( ((tango_x*sin_az + tango_y*cos_az + y0) / origin.lat_scale) + origin.lat_0);
            location.setAltitude(x.get(2, 0) + z0 + origin.h_0);

            location.setAccuracy((float) Math.sqrt(xform.xySigma2() + P.get(0,0) + P.get(1,1)));

            final double vx = x.get(3, 0);
            final double vy = x.get(4, 0);
            location.setSpeed((float) Math.hypot(vx, vy));
            location.setBearing((float) Math.toDegrees(Math.atan2(vx, vy) - az));

            location.setTime(prevCompTime);

            return location;
        }
    }

    public OriginData getOrigin() { return  origin; }


    /**
     * Create a {@link no.kartverket.positionorientation.Transform.PositionObservation3D Transform.PositionObservation3D}
     * object from a GPS (or similar observation). This method is
     * typically called from a {@link android.location.LocationListener} registred to a
     * {@link android.location.LocationManager}.
     *
     * @param location location data from GPS or similar location services
     * @return the created {@link no.kartverket.positionorientation.Transform.PositionObservation3D} object
     */
    public Transform.Observation handleLocationObservation(final Location location) {
        synchronized (kalmanFilter) {
            lastLocation = location;

            long currTime = System.currentTimeMillis();

            if (origin == null) {
                // Initialize
                geoidH_0 = Geodesy.GetGeoidHeight(location.getLatitude(), location.getLongitude());

                origin = new OriginData(location.getLatitude(), location.getLongitude(), location.getAltitude() - geoidH_0);

                GeomagneticField geoField =
                        new GeomagneticField((float) origin.lat_0, (float) origin.lon_0,
                                (float) location.getAltitude(),
                                System.currentTimeMillis());
                declination = (float) Math.toRadians(geoField.getDeclination());

                final double sin_az = Math.sin(compass_az);
                final double cos_az = Math.cos(compass_az);
                transformMatrix = new float[]{
                        (float) cos_az, -(float) sin_az, 0, 0,
                        (float) sin_az, (float) cos_az, 0, 0,
                        0, 0, 1, 0,
                        0, 0, 0, 1
                };

                callOriginUpdateListeners(origin);
            }

            // Grab observations
            double x = origin.longitudeToLocalOrigin(location.getLongitude());
            double y = origin.latitudeToLocalOrigin(location.getLatitude());
            double z = origin.heightToLocalOrigin(location.getAltitude()) - geoidH_0;

            Transform.PositionObservation3D pos_obs = xform.new PositionObservation3D();
            pos_obs.x_w = (float) x;
            pos_obs.y_w = (float) y;
            pos_obs.z_w = (float) z;
            pos_obs.x_w_sd = pos_obs.y_w_sd = location.getAccuracy();
            pos_obs.z_w_sd = location.getAccuracy() * 2;

            if (prevCompTime > 0) {
                predictionMatrixUpdater.T = (currTime - prevCompTime) / 1000.0;
                kalmanFilter.predict(predictionMatrixUpdater);

                pos_obs.x_t = (float) kalmanFilter.getX().get(0, 0);
                pos_obs.y_t = (float) kalmanFilter.getX().get(1, 0);
                pos_obs.z_t = (float) kalmanFilter.getX().get(2, 0);
                pos_obs.x_t_sd = pos_obs.y_t_sd = pos_obs.z_t_sd = 0.1f;

                observations.add(pos_obs);
            }

            prevCompTime = currTime;

            return pos_obs;
        }
    }

    /**
     * Handle an observation of the current 2D device position. Technically very similar to
     * {@link #handleLocationObservation(Location)}. Can be used for manually adding a position from map
     *
     * @param latitude latitude of geograpic position, in degrees
     * @param longitude longitude of geograpic position, in degrees
     * @param accuracy apriori standard deviation of position, in meters
     * @return the created {@link no.kartverket.positionorientation.Transform.PositionObservation2D} object
     */
    public Transform.Observation handleLatLngObservation(final double latitude, final double longitude, final float accuracy) {
        synchronized (kalmanFilter) {

            long currTime = System.currentTimeMillis();

            if (origin == null) {
                // Initialize
                geoidH_0 = Geodesy.GetGeoidHeight(latitude, longitude);

                origin = new OriginData(latitude, longitude, 0);

                GeomagneticField geoField =
                        new GeomagneticField((float) origin.lat_0, (float) origin.lon_0,
                                (float) 0,
                                System.currentTimeMillis());
                declination = (float) Math.toRadians(geoField.getDeclination());

                final double sin_az = Math.sin(compass_az);
                final double cos_az = Math.cos(compass_az);
                transformMatrix = new float[]{
                        (float) cos_az, -(float) sin_az, 0, 0,
                        (float) sin_az, (float) cos_az, 0, 0,
                        0, 0, 1, 0,
                        0, 0, 0, 1
                };

                callOriginUpdateListeners(origin);
            }

            // Grab observations
            double x = origin.longitudeToLocalOrigin(longitude);
            double y = origin.latitudeToLocalOrigin(latitude);

            Transform.PositionObservation2D pos_obs = xform.new PositionObservation2D();
            pos_obs.x_w = (float) x;
            pos_obs.y_w = (float) y;
            pos_obs.x_w_sd = pos_obs.y_w_sd = accuracy;
            if (prevCompTime > 0) {
                predictionMatrixUpdater.T = (currTime - prevCompTime) / 1000.0;
                kalmanFilter.predict(predictionMatrixUpdater);

                pos_obs.x_t = (float) kalmanFilter.getX().get(0, 0);
                pos_obs.y_t = (float) kalmanFilter.getX().get(1, 0);
                pos_obs.x_t_sd = pos_obs.y_t_sd  = 0.1f;

                observations.add(pos_obs);
            }

            prevCompTime = currTime;
            return pos_obs;
        }
    }

    /**
     * Handle an observation of the current 3D device position. Technically very similar to
     * {@link #handleLocationObservation(Location)}. Can be used for manually adding a position
     * from map, combined with an elevation observation from terrain model.
     *
     * @param latitude latitude of geograpic position, in degrees
     * @param longitude longitude of geograpic position, in degrees
     * @param height height of geograpic position, in meters
     * @param horizontal_accuracy apriori standard deviation of horizontal position, in meters
     * @param vertical_accuracy apriori standard deviation of height, in meters
     * @return the created {@link no.kartverket.positionorientation.Transform.PositionObservation3D} object
     */
    public Transform.Observation handleLatLngHObservation(final double latitude, final double longitude, final double height, final float horizontal_accuracy, final float vertical_accuracy) {
        synchronized (kalmanFilter) {

            long currTime = System.currentTimeMillis();

            if (origin == null) {
                // Initialize
                geoidH_0 = Geodesy.GetGeoidHeight(latitude, longitude);

                origin = new OriginData(latitude, longitude, height);

                GeomagneticField geoField =
                        new GeomagneticField((float) origin.lat_0, (float) origin.lon_0,
                                (float) height,
                                System.currentTimeMillis());
                declination = (float) Math.toRadians(geoField.getDeclination());

                final double sin_az = Math.sin(compass_az);
                final double cos_az = Math.cos(compass_az);
                transformMatrix = new float[]{
                        (float) cos_az, -(float) sin_az, 0, 0,
                        (float) sin_az, (float) cos_az, 0, 0,
                        0, 0, 1, 0,
                        0, 0, 0, 1
                };

                callOriginUpdateListeners(origin);
            }

            // Grab observations
            double x = origin.longitudeToLocalOrigin(longitude);
            double y = origin.latitudeToLocalOrigin(latitude);
            double z = origin.heightToLocalOrigin(height);

            Transform.PositionObservation3D pos_obs = xform.new PositionObservation3D();
            pos_obs.x_w = (float) x;
            pos_obs.y_w = (float) y;
            pos_obs.z_w = (float) z;
            pos_obs.x_w_sd = pos_obs.y_w_sd = horizontal_accuracy;
            pos_obs.z_w_sd = vertical_accuracy;

            if (prevCompTime > 0) {
                predictionMatrixUpdater.T = (currTime - prevCompTime) / 1000.0;
                kalmanFilter.predict(predictionMatrixUpdater);

                pos_obs.x_t = (float) kalmanFilter.getX().get(0, 0);
                pos_obs.y_t = (float) kalmanFilter.getX().get(1, 0);
                pos_obs.z_t = (float) kalmanFilter.getX().get(2, 0);
                pos_obs.x_t_sd = pos_obs.y_t_sd = pos_obs.z_t_sd = 0.1f;

                observations.add(pos_obs);
            }

            prevCompTime = currTime;

            return pos_obs;
        }
    }

    /**
     * Create a {@link no.kartverket.positionorientation.Transform.OrientationObservation Transform.OrientationObservation}
     * from a rotationVector as given from a {@link android.hardware.Sensor#TYPE_ROTATION_VECTOR Rotation Vector Sensor}.
     * This method is typically called from a {@link android.hardware.SensorEventListener SensorEventListener},
     * where the rotationVector is read from a {@link android.hardware.SensorEvent SensorEvent}.
     *
     * @param rotationVector value from a {@link android.hardware.SensorEvent SensorEvent}
     * @return the created {@link no.kartverket.positionorientation.Transform.OrientationObservation} object
     */
    public Transform.Observation handleRotationVectorObservation(float[] rotationVector) {

        float[] rot_a = new float[3];
        float[] rotMatrix = new float[16];
        SensorManager.getRotationMatrixFromVector(rotMatrix, rotationVector);
        SensorManager.getOrientation(rotMatrix, rot_a);
        compass_dir = rot_a[0] + declination;

        if (tango_dir < Float.MAX_VALUE) {
            compass_az = (float) Transform.normalizeAngle(tango_dir - compass_dir);

            Transform.OrientationObservation orient_obs = xform.new OrientationObservation();
            orient_obs.orient = compass_az;
            if (rotationVector.length > 4)
                orient_obs.orient_sd = rotationVector[4]/2;
            else
                orient_obs.orient_sd = (float)Math.PI/2;
            observations.add(orient_obs);
            return orient_obs;
            /*
            if (observations.size() > 20)
                xform.adjust(observations);
                */
        }
        return null;

        // Log results
        /*
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("rv ");
        stringBuilder.append(" compass ");
        stringBuilder.append(compass_dir);
        stringBuilder.append(" tango ");
        stringBuilder.append(tango_dir);
        stringBuilder.append(" compass_az ");
        stringBuilder.append(compass_az);
        if (rotationVector.length > 4) {
            stringBuilder.append(" rot sd ");
            stringBuilder.append(rotationVector[4]);
            orientation_sd = rotationVector[4];
        }

        Log.e(TAG, stringBuilder.toString());
        */
    }

    //private double geoidHeight(double lon_0, double lat_0) {        return 40;    }
    private final float[] tmpRotA =  new float[3], tmpRotMatrix = new float[16];

    /**
     * Update the provider with the most recent pose (position and orientation) in the Tango coordinate system<p>
     *
     * Typically called from {@link com.google.atap.tangoservice.Tango.OnTangoUpdateListener#onPoseAvailable(TangoPoseData)}
     *
     * @param pose the position orientation structure given from Tango
     */
    public void handlePoseObservation(final TangoPoseData pose) {
        synchronized (kalmanFilter) {
            long currTime = System.currentTimeMillis();

            // Predict
            if (prevCompTime > 0) {
                predictionMatrixUpdater.T = (currTime - prevCompTime) / 1000.0;
                kalmanFilter.predict(predictionMatrixUpdater);
            }

            // Update with new observations
            for (int i = 0; i < 3; ++i)
                z_tango.set(i, 0, pose.translation[i]);

            kalmanFilter.update(H_tango, z_tango, R_tango);

            float[] rotVec = pose.getRotationAsFloats();
            SensorManager.getRotationMatrixFromVector(tmpRotMatrix, rotVec);
            SensorManager.getOrientation(tmpRotMatrix, tmpRotA);

            tango_dir = tmpRotA[0];

            prevCompTime = currTime;
        }
    }

    /**
     * Create a set of {@link no.kartverket.positionorientation.Transform.PointInTerrainObservation} from
     * a point cloud observation and a gridded terrain model.<p>
     *
     * Typically called from {@link com.google.atap.tangoservice.Tango.OnTangoUpdateListener#onPointCloudAvailable}
     *
     * @param numPoints the number of points
     * @param points a FloatBuffer of point data, with stride 4 as points are stored in the buffer as
     *               x1, y1, z1, w1, x2, y2, z2, w2.... where w is a "reliability" measure (not used here)
     * @param grid A gridded terrain model
     * @param cloud_accuracy apriori standard deviation of a point to terrain model distance observation
     */
    public ArrayList<Transform.Observation> handlePointCloudObservation(int numPoints, FloatBuffer points, DTMGrid grid, float cloud_accuracy) {
        ArrayList<Transform.Observation> obs_list = new ArrayList<>(numPoints);
        for (int i = 0; i < numPoints; ++i) {
            float x = points.get(i*4 + 0);
            float y = points.get(i*4 + 1);
            float z = points.get(i*4 + 2);

            Transform.PointInTerrainObservation obs = xform.new PointInTerrainObservation();
            obs.x_t = x;
            obs.y_t = y;
            obs.z_t = z;
            obs.sd = cloud_accuracy;
            obs.grid = grid;
            obs_list.add(obs);
        }
        observations.addAll(obs_list);

        synchronized (transformWorker) {
            transformWorker.notifyAll();
        }
        return obs_list;
    }

    /**
     * Remove an observation and initialize recompute of parameters
     *
     * @param obs the observation to remove
     * @return true if the observation was in the observation list
     */
    public boolean removeObservation(Transform.Observation obs) {
        if (observations.remove(obs)) {
            synchronized (transformWorker) {
                transformWorker.notifyAll();
            }
            return true;
        }
        return false;
    }

    /**
     * Remove a set of observations and initialize recompute of parameters
     *
     * @param obs_set the list of observations to remove
     * @return true if at least one of the argument observations was removed from the observation list
     */
    public boolean removeObservations(Collection<Transform.Observation> obs_set) {
        if (observations.removeAll(obs_set)) {
            synchronized (transformWorker) {
                transformWorker.notifyAll();
            }
            return true;
        }
        return false;
    }
}
