package no.kartverket.geodesy;

/**
 * Data for the local origin, for transforming to and from local cartesian coordinates.
 *
 *
 */
public class OriginData {
    /**
     * Origin coordinates
     */
    final public double lat_0, lon_0, h_0;
    /**
     * Scaling from degrees to meters
     */
    final public double lat_scale, lon_scale;

    /**
     * Initialize origin
     *
     * @param lat_0
     * @param lon_0
     * @param h_0
     */
    public OriginData(double lat_0, double lon_0, double h_0) {
        this.lat_0 = lat_0;
        this.lon_0 = lon_0;
        this.h_0 = h_0;

        // Compute scaling from degrees to meters
        this.lat_scale = Geodesy.meridionalRadius(lat_0) * Math.PI / 180;
        this.lon_scale = Geodesy.normalRadius(lat_0) * Math.PI / 180 * Math.cos(Math.toRadians(lat_0));
    }

    /**
     * Transform to position relative to a local origin
     *
     * @param lat
     * @return a local coodinate, meters north of origin
     */
    final public double latitudeToLocalOrigin(final double lat) {
        return (lat - lat_0) * lat_scale;
    }

    /**
     * Transform to position relative to a local origin
     *
     * @param lon
     * @return a local coodinate, meters east of origin
     */
    final public double longitudeToLocalOrigin(final double lon) {
        return (lon - lon_0) * lon_scale;
    }

    /**
     * Transform to position relative to a local origin
     *
     * @param h
     * @return a local coodinate, meters vertically above of origin
     */
    final public double heightToLocalOrigin(final double h) {
        return h - h_0;
    }
}
