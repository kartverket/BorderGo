package no.kartverket.positionorientation;

import no.kartverket.geodesy.OriginData;

/**
 * Objects that handle geographic objects should implement this interface. As OpenGL works with
 * 32 bit float of limited numerical resolution coordinates should be transformed to a local origin.
 * The {@link OriginData} structure contains necessary information about the origin, together
 * with convenience methods for transformation<p>
 *
 * The {@link PositionOrientationProvider} will maintain the origin, and call the
 * {@link OriginUpdateListener#originChanged(OriginData)} method as necessary. Implementing classes
 * should recompute (and possibly reload) all their geographic data when this is called.
 *
 * @author runaas on 15.05.2017.
 */
public interface OriginUpdateListener {

    /**
     * The origin is changed, please recompute model coordinates and model transforms
     *
     * @param origin
     */
    void originChanged(OriginData origin);
}
