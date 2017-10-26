package no.kartverket.data.dtm;

import java.nio.FloatBuffer;

import no.kartverket.geodesy.Geodesy;
import no.kartverket.geodesy.OriginData;
import no.kartverket.geometry.GeomUtils;
import no.kartverket.geometry.Triangle;

/**
 * A terrain model implemented as a regular grid, positioned and oriented to a geographical position
 *
 */
public class DTMGrid {

    /**
     * Create grid and allocate data
     * @param rows Number of rows
     * @param cols Number of columns
     */
    DTMGrid(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        arrH = FloatBuffer.allocate(rows * cols);
        arrH.clear();
    }

    // Grid dimensions
    private long rows;
    private long cols;

    /**
     * All heights, line by line, from upper left.
     */
    FloatBuffer arrH;

    private double ul_lat = 0.0;
    private double ul_lon = 0.0;
    private double ul_x = 0.0;
    private double ul_y = 0.0;
    private double cosAngle = 1.0;
    private double sinAngle = 0.0;
    private double space = 0.0;


    private OriginData origin;

    /**
     * Set grid position (given in UTM33/ETRS89) and compute geodetic data
     *
     * @param upperLeftN northing position of upper left corner
     * @param upperLeftE easting position of upper left corner
     * @param space horizontal spacing of grid
     */
    void setCoordinateParams(final double upperLeftN, final double upperLeftE, final double space)
    {
        Geodesy.Coords_xy NE = new Geodesy.Coords_xy(upperLeftN, upperLeftE);
        Geodesy.Coords_xy latlon = new Geodesy.Coords_xy();
        Geodesy.utm2latlon(NE, latlon);

        ul_lat = latlon._x;
        ul_lon = latlon._y;
        this.space = space;

        // Compute meridian convergence
        double angle = Geodesy.meridian_convergence(latlon._x, latlon._y);
        cosAngle = Math.cos(angle);
        sinAngle = Math.sin(angle);

        // Adjust for projection error
        double scale = Geodesy.scale(latlon._x, latlon._y);
        this.space /= scale;   // Adjust spacing by scale

        // Compute origin data if available
        if (origin != null) {
            ul_x = origin.longitudeToLocalOrigin(ul_lon);
            ul_y = origin.latitudeToLocalOrigin(ul_lat);
        }
    }

    public void setOrigin(OriginData origin) {
        this.origin = origin;
        ul_x = origin.longitudeToLocalOrigin(ul_lon);
        ul_y = origin.latitudeToLocalOrigin(ul_lat);
    }

    /**
     * Find value of a grid cell
     *
     * @param x_i column index
     * @param y_i row index
     * @return
     */
    public float gridValue(int x_i, int y_i) {
        return arrH.get(y_i * (int) cols + x_i);
    }

    /**
     * Compute interpolated value for a position
     *
     * @param lat latitude of position
     * @param lng longitude of position
     * @return height value
     */
    public double getInterpolatedAltitude(double lat, double lng) {
        int w = (int) cols;
        int h = (int) rows;


        float x_w = (float) origin.longitudeToLocalOrigin(lng);
        float y_w = (float) origin.latitudeToLocalOrigin(lat);
        float x = worldToGridX(x_w, y_w);
        float y = worldToGridY(x_w, y_w);

        int cellX = (int) Math.floor(x);
        float dX = x % 1;

        int cellY = (int) Math.floor(y);
        float dY = y % 1;

        if ((cellY < h - 1) && (cellX < w - 1) && (cellY >= 0) && (cellX >= 0)) {
            int i0 = w * cellY + cellX;
            int ix = w * cellY + cellX + 1;
            int iy = w * (cellY + 1) + cellX;
            int ixy = w * (cellY + 1) + cellX + 1;

            return GeomUtils.bilinear(
                    arrH.get(i0),
                    arrH.get(ix),
                    arrH.get(iy),
                    arrH.get(ixy),
                    dX,
                    dY);
        }
        // Log.i("BorderGoApp", "Outside height grid area");
        return Double.NEGATIVE_INFINITY;

    }

    private static int[] neighCellIx = new int[]{
            0, 0, 1, 0, 1, 1, 0, 1, -1, 1, -1, 0, -1, -1, 0, -1, 1, -1, // radius = 1
            2, 0, 2, 1, 2, 2, 1, 2, 0, 2, -1, 2, -2, 2, -2, 1, -2, 0, -2, -1, -2, -2, -1, -2, 0, -2, 1, -2, 2, -2, 2, -1, // Radius = 2
            3, 0, 3, 1, 3, 2, 2, 3, 1, 3, 0, 3, -1, 3, -2, 3, -3, 2, -3, 1, -3, 0, -3, -1, -3, -2, -2, -3, -1, -3, 0, -3, 1, -3, 2, -3, 3, -2, 3, -1, // Radius = 3
            4, 0, 4, 1, 4, 2, 4, 3, 3, 3, 3, 4, 2, 4, 1, 4, 0, 4, -1, 4, -2, 4, -3, 4, -3, 3, -4, 3, -4, 2, -4, 1,
            -4, 0, -4, -1, -4, -2, -4, -3, -3, -3, -3, -4, -2, -4, -1, -4, 0, -4, 1, -4, 2, -4, 3, -4, 3, -3, 4, -3, 4, -2, 4, -1 // Radius = 4
    };

    /**
     * Compute plane equation for the closest point in a DTM surface.
     *
     * @param x coordiante of point
     * @param y coordiante of point
     * @param z coordiante of point
     * @return plane equation as given in {@link Triangle#pointTriangle(float[], float[], float[], float[], float[])}
     */
    public float[] getSurfacePlane(float x, float y, float z) {
        int x_i = (int) Math.floor(worldToGridX(x, y));
        int y_i = (int) Math.floor(worldToGridY(x, y));
        if (x_i < 0 || y_i < 0 || x_i >= cols - 1 || y_i >= rows - 1)
            return null;

        float[] retval = new float[4];
        float[] tmp = new float[4];
        float[] p00 = new float[3];
        float[] p01 = new float[3];
        float[] p10 = new float[3];
        float[] p11 = new float[3];
        float[] p = new float[]{x, y, z};

        float best_dist = 2 * (float) space + Math.abs(z - (gridValue(x_i, y_i) - (float) origin.h_0));
        for (int i = 0; i < neighCellIx.length; i += 2) {
            int xi = x_i + neighCellIx[i];
            int yi = y_i + neighCellIx[i + 1];
            if (xi < 0 || yi < 0 || xi >= cols - 1 || yi >= rows - 1)
                continue;

            p00[0] = gridToWorldX(xi, yi);
            p00[1] = gridToWorldY(xi, yi);
            p00[2] = gridValue(xi, yi) - (float) origin.h_0;
            p01[0] = gridToWorldX(xi, yi + 1);
            p01[1] = gridToWorldY(xi, yi + 1);
            p01[2] = gridValue(xi, yi + 1) - (float) origin.h_0;
            p10[0] = gridToWorldX(xi + 1, yi);
            p10[1] = gridToWorldY(xi + 1, yi);
            p10[2] = gridValue(xi + 1, yi) - (float) origin.h_0;
            p11[0] = gridToWorldX(xi + 1, yi + 1);
            p11[1] = gridToWorldY(xi + 1, yi + 1);
            p11[2] = gridValue(xi + 1, yi + 1) - (float) origin.h_0;

            float dx = 0, dy = 0, dz = 0;

            float max_dx = p00[0] - x;
            max_dx = Math.max(max_dx, p01[0] - x);
            max_dx = Math.max(max_dx, p10[0] - x);
            max_dx = Math.max(max_dx, p11[0] - x);
            float min_dx = p00[0] - x;
            min_dx = Math.min(min_dx, p01[0] - x);
            min_dx = Math.min(min_dx, p10[0] - x);
            min_dx = Math.min(min_dx, p11[0] - x);
            if (max_dx > 0 && min_dx > 0)
                dx = min_dx;
            else if (max_dx < 0 && min_dx < 0)
                dx = max_dx;

            float max_dy = p00[1] - y;
            max_dy = Math.max(max_dy, p01[1] - y);
            max_dy = Math.max(max_dy, p10[1] - y);
            max_dy = Math.max(max_dy, p11[1] - y);
            float min_dy = p00[1] - y;
            min_dy = Math.min(min_dy, p01[1] - y);
            min_dy = Math.min(min_dy, p10[1] - y);
            min_dy = Math.min(min_dy, p11[1] - y);
            if (max_dy > 0 && min_dy > 0)
                dy = min_dy;
            else if (max_dy < 0 && min_dy < 0)
                dy = max_dy;

            if (dx * dx + dy * dy > (best_dist + 1.5 * space) * (best_dist + 1.5 * space))
                break;

            float max_dz = p00[2] - z;
            max_dz = Math.max(max_dz, p01[2] - z);
            max_dz = Math.max(max_dz, p10[2] - z);
            max_dz = Math.max(max_dz, p11[2] - z);
            float min_dz = p00[2] - z;
            min_dz = Math.min(min_dz, p01[2] - z);
            min_dz = Math.min(min_dz, p10[2] - z);
            min_dz = Math.min(min_dz, p11[2] - z);
            if (max_dz > 0 && min_dz > 0)
                dz = min_dz;
            else if (max_dz < 0 && min_dz < 0)
                dz = max_dz;

            if (dx * dx + dy * dy + dz * dz > best_dist * best_dist)
                continue;

            float dist = Triangle.pointTriangle(p00, p01, p10, p, tmp);
            if (dist < best_dist) {
                best_dist = dist;
                retval[0] = tmp[0];
                retval[1] = tmp[1];
                retval[2] = tmp[2];
                retval[3] = tmp[3];
            }
            dist = Triangle.pointTriangle(p11, p01, p10, p, tmp);
            if (dist < best_dist) {
                best_dist = dist;
                retval[0] = tmp[0];
                retval[1] = tmp[1];
                retval[2] = tmp[2];
                retval[3] = tmp[3];
            }
        }

        return retval;
    }

    public float gridToWorldX(float x_grid, float y_grid) {
        return (float) (ul_x + space * (x_grid * cosAngle - y_grid * sinAngle));
    }

    public float gridToWorldY(float x_grid, float y_grid) {
        return (float) (ul_y - space * (x_grid * sinAngle + y_grid * cosAngle));
    }

    public float worldToGridX(float x_grid, float y_grid) {
        x_grid -= ul_x;
        y_grid -= ul_y;
        return (float) ((x_grid * cosAngle - y_grid * sinAngle) / space);
    }

    public float worldToGridY(float x_grid, float y_grid) {
        x_grid -= ul_x;
        y_grid -= ul_y;
        return (float) ((x_grid * sinAngle + y_grid * cosAngle) / -space);
    }

    public long getRows() {
        return rows;
    }

    public long getCols() {
        return cols;
    }
}
