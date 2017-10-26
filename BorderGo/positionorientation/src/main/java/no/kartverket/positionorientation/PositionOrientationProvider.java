package no.kartverket.positionorientation;

import android.location.Location;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import no.kartverket.geodesy.OriginData;

/**
 * Keeps track of the devices position and orientation
 *
 * @author runaas
 */

public abstract class PositionOrientationProvider  {

    /**
     * Threadsafe list of origin update listeners
     */
    protected List<OriginUpdateListener> originUpdateListeners = new CopyOnWriteArrayList<OriginUpdateListener>();

    /**
     * Initialize everything (when Tango initializes with a new origin)
     */
    abstract public void reset();


    /**
     * Get the current rotation of the device in the rotation matrix format
     *
     * @return float[16]
     */
    abstract public float[] getTransformationMatrix( );

    /**
     * Get the current position of the device
     *
     * @return location
     */
    abstract public Location getLocation();

    /**
     * Add an origin update listener
     *
     * @param l
     */
    public void addOriginUpdateListener(OriginUpdateListener l) {
        originUpdateListeners.add(l);
    }

    /**
     * Remove an origin update listener
     *
     * @param l
     */
    public void removeOriginUpdateListener(OriginUpdateListener l) {
        originUpdateListeners.remove(l);
    }

    /**
     * Call all origin update listeners
     *
     * @param origin
     */
    protected void callOriginUpdateListeners(OriginData origin) {
        for (OriginUpdateListener l: originUpdateListeners) {
            l.originChanged(origin);
        }
    }
}
