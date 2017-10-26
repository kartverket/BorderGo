package no.kartverket.bordergo;

import android.app.Application;
import android.widget.Toast;

import no.kartverket.bordergo.config.Config;
import no.kartverket.data.DataLogger;
import no.kartverket.glrenderer.ArScene;

/**
 * Created by janvin on 08/05/17.
 */



public class BorderGoApp extends Application {
    static final String TAG = BorderGoApp.class.getSimpleName();

    private DataLogger dataLogger;

    /**
     * A collection of graphical objects to be displayed in the 3D view of {@link MainActivity}.
     * They are stored here to avoid recreation when the user moves to another activity (for
     * example the {@link MapsActivity}) and back. However, as the {@link android.opengl.GLSurfaceView}
     * is reinitialized on restarting the Activity all Gl context in the scene
     * (shaders, VBOs, textures) should be recreated.
     */
    private ArScene scene;

    public static Config config;

    private static boolean _useGPSAndCompass = true;

    private long startTime;
    //private static BorderGoApp bgInstance;
    private static BGState bgState = new BGState();
    private boolean showAerialMap = true;
    private String orthoSessionKey;

    public static final String PREFERENCE_FILE= "BorderGoPreferenceFile";

    public static class PrefNames{
        public static final String DEVICE_HEIGHT = "device_height";
        public static final String DTM_ZIGMA = "dtm_zigma";
        public static final String MAP_CALIBRATION_ZIGMA = "map_calibration_zigma";
        public static final String POINT_CLOUD_ZIGMA = "point_cloud_zigma";
    }

    public static class LoggNames{
        public static final String X = "X";
        public static final String X_TIMESTAMP = "X_TimeStamp";

        public static final String Y = "Y";
        public static final String Y_TIMESTAMP= "Y_TimeStamp";

        public static final String Z = "Z";
        public static final String Z_TIMESTAMPED_DATA = "Z_TimeStamped_Data";

        public static final String Z_TIMESTAMP= "Z_TimeStamp";

        public static final String LAT_GPS = "Latitude_GPS";
        public static final String LNG_GPS = "Longitude_GPS";

        public static final String LAT_POP = "Latitude_pop";
        public static final String LNG_POP= "Longitude_pop";
        public static final String ZIG_POP = "Accuracy_pop";


        public static final String ALT_INTERPOLATED= "Altitude_interpolated_from_grid";


    }

    public BorderGoApp(){
        super();

        dataLogger = new DataLogger(this);
        startTime = System.currentTimeMillis();
    }



    public static BGState getBGState(){
        return bgState;
    }

    public DataLogger getDataLogger() { return dataLogger; }

    public ArScene getScene() { return scene; }
    public void setScene(ArScene scene) { this.scene = scene; }

    public boolean usesAerialMap(){
        return showAerialMap;
    }

    public static boolean usesGPSAndCompass()   {return _useGPSAndCompass;}

    public static void dontUseGpsAndCompass()   { _useGPSAndCompass = false; }

    public static void useGPSAndCompass()       { _useGPSAndCompass = true; }

    public String getOrthoSessionKey(){ return orthoSessionKey; }
    public void setOrthoSessionKey(String orthoSessionKey){ this.orthoSessionKey =  orthoSessionKey; }

    public void toggleAerialMap(){
        showAerialMap = !showAerialMap;
    }

    public long getStartTime(){
        return startTime;
    }

    public void shortToast(String message){
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
