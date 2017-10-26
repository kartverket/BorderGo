package no.kartverket.positionorientation;

import android.util.Log;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.MatrixFeatures_DDRM;

import java.util.Arrays;
import java.util.List;

import no.kartverket.data.dtm.DTMGrid;

/**
 * Builds and maintains a transform between two coordinate systems by a robust estimation of
 * transform parameters based on various observations of common objects in both coordinate systems.
 *
 * Currently the transform is limited to a 3 dimensional translation and a rotation around the
 * z-axis. Other rotations, scale changes and non-linear deformations may be implemented later. <p>
 *
 * The transform parameters are estimated by minimizing the weighted differences between the observations
 * and an idealized model of the system, a "least squares adjustment".
 * <p>
 *
 * The model equations can generally be
 * written as {@code A * (l + v) + B * dx = d} where {@code A} and {@code B} are the
 * model matrixes for the observations and paramaeters respectively (or the matrixes of the
 * partial derivatives, the jacobi matrixes in case of non-linear model equations). {@code l}
 * is the vector of observations, {@code v} the vector of residuals (the difference between the
 * observations and least-squares adusted observations), {@code dx} the
 * corrections to the approximated parameters added to {@code x0} the vector of approximated
 * parameters (necessary when solving non-linear model equations by iteration) and {@code d} a
 * vector of constants.<p>
 *
 * The problem is solved by finding the vector of corrections to parameters that minimizes the weighted sum
 * squared of the residuals {@code transpose(v) * inverse(Q) * v}, subject to the constraints of the model equations
 * above, which results in the solution {@code dx = inverse(N) * t} where {@code N =
 * transpose(B) * We * B}, {@code t = transpose(B) * We * f}, {@code We = inverse(A * Q * transpose(A))}
 * and {@code f = d - A * l}. {@code Q} is the matrix of cofactors, which is equal to the
 * a priori covariance matrix of the observations, possibly scaled my a constant.<p>
 *
 * For more information about the mathematics behind the formulas please consult a standard text on
 * least squares adjustment for geodesy and photogrammetry, for example "Observations and least
 * squares", by Edward M: Mikhail, 1976<p>
 *
 * Created by runaas on 11.05.2017.
 */
public class Transform {
    /**
     * Number of estimated parameters, currently 4 (x, y, z and orientation)
     */
    static final int num_parameters = 4;

    /**
     * Block of estimated transform parameters
     */
    private class Parameters {
        double x0, y0, z0, sigma2, xy_sigma2, z_sigma2;
        double az = 0, sin_az = 0, cos_az = 1, az_sigma2;
    }

    /**
     * Array of three parameter blocks, distinct for each of the three computation steps in
     * the {@link #adjust(List)} method
     */
    private final Parameters parameters[] = new Parameters[] {new Parameters(), new Parameters(), new Parameters()};

    /** x translation */
    public double x0() { return parameters[2].x0; }
    /** y translation */
    public double y0() { return parameters[2].y0; }
    /** z translation */
    public double z0() { return parameters[2].z0; }
    /** Rotation around z axis */
    public double az() { return parameters[2].az; }
    /** Precomputed sine of roatation around z-axis */
    public double sinAz() { return parameters[2].sin_az; }
    /** Precomputed cosine of roatation around z-axis */
    public double cosAz() { return parameters[2].cos_az; }

    /** Observation sample variance of the unit weight (translation in meters) */
    public double sigma2() { return parameters[2].sigma2; }
    /** Estimated variance of the horizontal (xy) translation component */
    public double xySigma2() { return parameters[2].xy_sigma2; }
    /** Estimated variance of the vertical (z) translation component */
    public double zSigma2() { return parameters[2].z_sigma2; }
    public double azSigma2() { return parameters[2].az_sigma2; }

    /**
     * All matrixes of the model equations for a set of observations.
     */
    private class ModelEquations {
        final DMatrixRMaj A, B, f, Q, We, v, Qvv;

        ModelEquations(int numEquations, int numObservations, int numParameters) {
            A = new DMatrixRMaj(numEquations, numObservations);
            B = new DMatrixRMaj(numEquations, numParameters);
            f = new DMatrixRMaj(numEquations, 1);
            Q = new DMatrixRMaj(numObservations, numObservations);
            We = new DMatrixRMaj(numEquations, numEquations);
            v = new DMatrixRMaj(numObservations, 1);
            Qvv = new DMatrixRMaj(numObservations, numObservations);
        }
    }

    // Matrixes
    /**
     * Normal equations system matrix
     */
    private final DMatrixRMaj N;
    /**
     * Inverted normal equations system matrix
     */
    private final DMatrixRMaj Ninv;
    /**
     * Normal equations system vector
     */
    private final DMatrixRMaj t;
    /**
     * Computed adjustments to parameters ({@code Ninv = inverse(N); x = Ninv * t;})
     */
    private final DMatrixRMaj x;
    /**
     * Computed weighted sum squared of residuals
     */
    private final DMatrixRMaj vWv;

    /**
     *  Model equations for observations
     */
    private final ModelEquations posEquations2D = new ModelEquations(2, 4, num_parameters);
    private final ModelEquations posEquations3D = new ModelEquations(3, 6, num_parameters);
    private final ModelEquations orientEquations = new ModelEquations(1, 1, num_parameters);
    private final ModelEquations planeEquations = new ModelEquations(1, 1, num_parameters);

    /** Temp matrix for internal use */
    private final DMatrixRMaj tmp1 = new DMatrixRMaj(num_parameters, num_parameters);
    private final DMatrixRMaj tmp2 = new DMatrixRMaj(num_parameters, num_parameters);
    private final DMatrixRMaj tmp3 = new DMatrixRMaj(num_parameters, num_parameters);

    /**
     * Generic observation
     */
    public interface Observation {
        /**
         * Add data for this observation to the normal equation system
         *
         * @param p The current model parameters
         * @param weight The current observation weight
         */
        void addToNormalEquations(Parameters p, float weight);

        /**
         * Compute maximum normalized residual for this observation
         *
         * @param p The current model parameters
         * @return the normalized residual
         */
        float computeResiduals(Parameters p);

        /**
         * Return number of condition equations for this observation
         *
         * @return number of conditions
         */
        int numConditions();
    }

    /**
     * A 2D position observation, contains a "tango" and a "world" position describing the same point,
     * and their respecive a-priori standard deviations
     */
    public class PositionObservation2D implements Observation {
        /** x coordinate for world position */
        float x_w;
        /** y coordinate for world position */
        float y_w;
        /** x coordinate for tango position */
        float x_t;
        /** y coordinate for tango position */
        float y_t;
        /** a priori standard deviation of x coordinate for world position */
        float x_w_sd;
        /** a priori standard deviation of y coordinate for world position */
        float y_w_sd;
        /** a priori standard deviation of x coordinate for tango position */
        float x_t_sd;
        /** a priori standard deviation of y coordinate for tango position */
        float y_t_sd;
        /** a posteriori estimate of residual of x coordinate for world position */
        float x_w_v;
        /** a posteriori estimate of residual of y coordinate for world position */
        float y_w_v;
        /** a posteriori estimate of residual of x coordinate for tango position */
        float x_t_v;
        /** a posteriori estimate of residual of y coordinate for tango position */
        float y_t_v;
        /** a posteriori estimate of standard deviation of x coordinate for world position */
        float x_w_v_sd;
        /** a posteriori estimate of standard deviation of y coordinate for world position */
        float y_w_v_sd;
        /** a posteriori estimate of standard deviation of x coordinate for tango position */
        float x_t_v_sd;
        /** a posteriori estimate of standard deviation of y coordinate for tango position */
        float y_t_v_sd;

        @Override
        public void addToNormalEquations(Parameters p, float weight) {
            posEquations2D.B.set(0, 3, -x_t * p.sin_az - y_t * p.cos_az);
            posEquations2D.B.set(1, 3, x_t * p.cos_az - y_t * p.sin_az);

            posEquations2D.f.set(0, 0, x_w - p.cos_az * x_t + p.sin_az * y_t - p.x0);
            posEquations2D.f.set(1, 0, y_w - p.sin_az * x_t - p.cos_az * y_t - p.y0);

            posEquations2D.We.set(0, 0, weight / (x_w_sd * x_w_sd + x_t_sd * x_t_sd));
            posEquations2D.We.set(1, 1, weight / (y_w_sd * y_w_sd + y_t_sd * y_t_sd));

            updateN(posEquations2D.B, posEquations2D.We, N);
            updateT(posEquations2D.B, posEquations2D.We, posEquations2D.f, t);

            updateN(posEquations2D.f, posEquations2D.We, vWv);
        }

        @Override
        public float computeResiduals(Parameters p) {
            posEquations2D.A.set(0, 0, p.cos_az);
            posEquations2D.A.set(1, 1, p.cos_az);
            posEquations2D.A.set(1, 0, p.sin_az);
            posEquations2D.A.set(0, 1, -p.sin_az);

            posEquations2D.B.set(0, 3, -x_t * p.sin_az - y_t * p.cos_az);
            posEquations2D.B.set(1, 3, x_t * p.cos_az - y_t * p.sin_az);

            posEquations2D.f.set(0, 0, x_w - p.cos_az * x_t + p.sin_az * y_t - p.x0);
            posEquations2D.f.set(1, 0, y_w - p.sin_az * x_t - p.cos_az * y_t - p.y0);

            posEquations2D.We.set(0, 0, 1 / (x_w_sd * x_w_sd + x_t_sd * x_t_sd));
            posEquations2D.We.set(1, 1, 1 / (y_w_sd * y_w_sd + y_t_sd * y_t_sd));

            posEquations2D.Q.set(0, 0, x_t_sd * x_t_sd);
            posEquations2D.Q.set(1, 1, y_t_sd * y_t_sd);
            posEquations2D.Q.set(2, 2, x_w_sd * x_w_sd);
            posEquations2D.Q.set(3, 3, y_w_sd * y_w_sd);

            computeQvv(posEquations2D.Q, posEquations2D.A, posEquations2D.We, posEquations2D.B, Ninv, posEquations2D.Qvv);
            computeV(posEquations2D.Q, posEquations2D.A, posEquations2D.We, posEquations2D.f, posEquations2D.B, x, posEquations2D.v);

            x_t_v = (float)posEquations2D.v.get(0,0);
            y_t_v = (float)posEquations2D.v.get(1,0);
            x_w_v = (float)posEquations2D.v.get(2,0);
            y_w_v = (float)posEquations2D.v.get(3,0);

            x_t_v_sd = (float) Math.sqrt(posEquations2D.Qvv.get(0,0) * p.sigma2);
            y_t_v_sd = (float) Math.sqrt(posEquations2D.Qvv.get(1,1) * p.sigma2);
            x_w_v_sd = (float) Math.sqrt(posEquations2D.Qvv.get(2,2) * p.sigma2);
            y_w_v_sd = (float) Math.sqrt(posEquations2D.Qvv.get(3,3) * p.sigma2);

            double max_residual = 0;
            for (int i = 0; i < 4; ++i) {
                double r = Math.abs(posEquations2D.v.get(i,0)) / Math.sqrt(posEquations2D.Qvv.get(i,i) * p.sigma2);
                if (max_residual < r)
                    max_residual = r;
            }
            return (float)max_residual;
        }


        @Override
        public int numConditions() {
            return 2;
        }
    }

    /**
     * A 3D position observation, contains a "tango" and a "world" position describing the same point,
     * and their respecive a-priori standard deviations
     */
    public class PositionObservation3D extends PositionObservation2D {
        /** z coordinate for world position */
        float z_w;
        /** z coordinate for tango position */
        float z_t;
        /** a priori standard deviation of z coordinate for world position */
        float z_w_sd;
        /** a priori standard deviation of z coordinate for tango position */
        float z_t_sd;
        /** a posteriori estimate of residual of z coordinate for world position */
        float z_w_v;
        /** a posteriori estimate of residual of z coordinate for tango position */
        float z_t_v;
        /** a posteriori estimate of standard deviation of z coordinate for world position */
        float z_w_v_sd;
        /** a posteriori estimate of standard deviation of z coordinate for tango position */
        float z_t_v_sd;

        @Override
        public void addToNormalEquations(Parameters p, float weight) {
            posEquations3D.B.set(0, 3, -x_t * p.sin_az - y_t * p.cos_az);
            posEquations3D.B.set(1, 3, x_t * p.cos_az - y_t * p.sin_az);

            posEquations3D.f.set(0, 0, x_w - p.cos_az * x_t + p.sin_az * y_t - p.x0);
            posEquations3D.f.set(1, 0, y_w - p.sin_az * x_t - p.cos_az * y_t - p.y0);
            posEquations3D.f.set(2, 0, z_w - z_t - p.z0);

            posEquations3D.We.set(0, 0, weight / (x_w_sd * x_w_sd + x_t_sd * x_t_sd));
            posEquations3D.We.set(1, 1, weight / (y_w_sd * y_w_sd + y_t_sd * y_t_sd));
            posEquations3D.We.set(2, 2, weight / (z_w_sd * z_w_sd + z_t_sd * z_t_sd));

            updateN(posEquations3D.B, posEquations3D.We, N);
            updateT(posEquations3D.B, posEquations3D.We, posEquations3D.f, t);

            updateN(posEquations3D.f, posEquations3D.We, vWv);
        }

        @Override
        public float computeResiduals(Parameters p) {
            posEquations3D.A.set(0, 0, p.cos_az);
            posEquations3D.A.set(1, 1, p.cos_az);
            posEquations3D.A.set(1, 0, p.sin_az);
            posEquations3D.A.set(0, 1, -p.sin_az);

            posEquations3D.B.set(0, 3, -x_t * p.sin_az - y_t * p.cos_az);
            posEquations3D.B.set(1, 3, x_t * p.cos_az - y_t * p.sin_az);

            posEquations3D.f.set(0, 0, x_w - p.cos_az * x_t + p.sin_az * y_t - p.x0);
            posEquations3D.f.set(1, 0, y_w - p.sin_az * x_t - p.cos_az * y_t - p.y0);
            posEquations3D.f.set(2, 0, z_w - z_t - p.z0);

            posEquations3D.We.set(0, 0, 1 / (x_w_sd * x_w_sd + x_t_sd * x_t_sd));
            posEquations3D.We.set(1, 1, 1 / (y_w_sd * y_w_sd + y_t_sd * y_t_sd));
            posEquations3D.We.set(2, 2, 1 / (z_w_sd * z_w_sd + z_t_sd * z_t_sd));

            posEquations3D.Q.set(0, 0, x_t_sd * x_t_sd);
            posEquations3D.Q.set(1, 1, y_t_sd * y_t_sd);
            posEquations3D.Q.set(2, 2, z_t_sd * z_t_sd);
            posEquations3D.Q.set(3, 3, x_w_sd * x_w_sd);
            posEquations3D.Q.set(4, 4, y_w_sd * y_w_sd);
            posEquations3D.Q.set(5, 5, z_w_sd * z_w_sd);

            computeQvv(posEquations3D.Q, posEquations3D.A, posEquations3D.We, posEquations3D.B, Ninv, posEquations3D.Qvv);
            computeV(posEquations3D.Q, posEquations3D.A, posEquations3D.We, posEquations3D.f, posEquations3D.B, x, posEquations3D.v);

            x_t_v = (float)posEquations3D.v.get(0,0);
            y_t_v = (float)posEquations3D.v.get(1,0);
            z_t_v = (float)posEquations3D.v.get(2,0);
            x_w_v = (float)posEquations3D.v.get(3,0);
            y_w_v = (float)posEquations3D.v.get(4,0);
            z_w_v = (float)posEquations3D.v.get(5,0);

            x_t_v_sd = (float) Math.sqrt(posEquations3D.Qvv.get(0,0) * p.sigma2);
            y_t_v_sd = (float) Math.sqrt(posEquations3D.Qvv.get(1,1) * p.sigma2);
            z_t_v_sd = (float) Math.sqrt(posEquations3D.Qvv.get(2,2) * p.sigma2);
            x_w_v_sd = (float) Math.sqrt(posEquations3D.Qvv.get(3,3) * p.sigma2);
            y_w_v_sd = (float) Math.sqrt(posEquations3D.Qvv.get(4,4) * p.sigma2);
            z_w_v_sd = (float) Math.sqrt(posEquations3D.Qvv.get(5,5) * p.sigma2);

            double max_residual = 0;
            for (int i = 0; i < 6; ++i) {
                double r = Math.abs(posEquations3D.v.get(i,0)) / Math.sqrt(posEquations3D.Qvv.get(i,i) * p.sigma2);
                if (max_residual < r)
                    max_residual = r;
            }
            return (float)max_residual;
        }


        @Override
        public int numConditions() {
            return 3;
        }
    }


    /**
     * Orientation obeservation, typically from a compass
     */
    public class OrientationObservation implements Observation {
        /** observed difference in orientation between tango and world north axis */
        float orient;
        /** a priori standard deviation of orientation */
        float orient_sd;
        /** a posteriori estimated residual of orientation */
        float orient_v;
        /** a posteriori estimated standard deviation of orientation residual */
        float orient_v_sd;

        @Override
        public void addToNormalEquations(Parameters p, float weight) {
            orientEquations.f.set(0, 0, normalizeAngle(orient - p.az));

            orientEquations.We.set(0, 0, weight / (orient_sd * orient_sd + 0.1*0.1));

            updateN(orientEquations.B, orientEquations.We, N);
            updateT(orientEquations.B, orientEquations.We, orientEquations.f, t);

            updateN(orientEquations.f, orientEquations.We, vWv);
        }

        @Override
        public float computeResiduals(Parameters p) {
            orientEquations.f.set(0, 0, normalizeAngle(orient - p.az));
            orientEquations.We.set(0, 0, 1 / (orient_sd * orient_sd + 0.1*0.1));
            orientEquations.Q.set(0, 0, orient_sd * orient_sd + 0.1*0.1);

            computeQvv(orientEquations.Q, orientEquations.A, orientEquations.We, orientEquations.B, Ninv, orientEquations.Qvv);
            computeV(orientEquations.Q, orientEquations.A, orientEquations.We, orientEquations.f, orientEquations.B, x, orientEquations.v);

            orient_v = (float)orientEquations.v.get(0,0);

            orient_v_sd = (float) Math.sqrt(orientEquations.Qvv.get(0,0) * p.sigma2);

            return Math.abs(orient_v) / orient_v_sd;
        }

        @Override
        public int numConditions() {
            return 1;
        }
    }

    /**
     * observation of a tango point that should lie in a plane described by world coordinates
     */
    public class PointInPlaneObservation implements Observation {
        /**
         * Parameters for a plane in world coordinates defined by the equation
         * {@code a*x + b*y + c*z == d} where the
         * vector {@code [a,b,c]} should be normalized to unity
         */
        float a, b, c, d;
        /** x coordinate for tango position */
        float x_t;
        /** y coordinate for tango position */
        float y_t;
        /** z coordinate for tango position */
        float z_t;
        /** a priori standard deviation of distance between plane and point */
        float plane_sd;
        /** a posteriori estimate of residual (distance between plane and point) */
        float plane_v;
        /** a posteriori estimate of standard deviation of residual */
        float plane_v_sd;

        @Override
        public void addToNormalEquations(Parameters p, float weight) {
            planeEquations.B.set(0,0,a);
            planeEquations.B.set(0,1,b);
            planeEquations.B.set(0,2,c);
            planeEquations.B.set(0,3, -a*x_t* p.sin_az - a*y_t* p.cos_az + b*x_t* p.cos_az - b*y_t* p.sin_az);

            planeEquations.f.set(0, 0, d - a*(p.cos_az*x_t - p.sin_az*y_t + p.x0) - b*(p.sin_az*x_t + p.cos_az*y_t + p.y0) - c*(z_t + p.z0));

            planeEquations.We.set(0, 0, weight / (plane_sd * plane_sd));

            updateN(planeEquations.B, planeEquations.We, N);
            updateT(planeEquations.B, planeEquations.We, planeEquations.f, t);

            updateN(planeEquations.f, planeEquations.We, vWv);
        }

        @Override
        public float computeResiduals(Parameters p) {
            planeEquations.B.set(0,0,a);
            planeEquations.B.set(0,1,b);
            planeEquations.B.set(0,2,c);
            planeEquations.B.set(0,3, -a*x_t* p.sin_az - a*y_t* p.cos_az + b*x_t* p.cos_az - b*y_t* p.sin_az);
            planeEquations.f.set(0, 0, d - a*(p.cos_az*x_t - p.sin_az*y_t + p.x0) - b*(p.sin_az*x_t + p.cos_az*y_t + p.y0) - c*(z_t + p.z0));
            planeEquations.We.set(0, 0, 1 / (plane_sd * plane_sd));
            planeEquations.Q.set(0, 0, plane_sd * plane_sd);

            computeQvv(planeEquations.Q, planeEquations.A, planeEquations.We, planeEquations.B, Ninv, planeEquations.Qvv);
            computeV(planeEquations.Q, planeEquations.A, planeEquations.We, planeEquations.f, planeEquations.B, x, planeEquations.v);

            plane_v = (float)planeEquations.v.get(0,0);

            plane_v_sd = (float) Math.sqrt(planeEquations.Qvv.get(0,0) * p.sigma2);

            return Math.abs(plane_v) / plane_v_sd;
        }

        @Override
        public int numConditions() {
            return 1;
        }
    }

    /**
     * observation of a tango point that should lie on a terrain model surface
     */
    public class PointInTerrainObservation implements Observation {
        /** x coordinate for tango position */
        float x_t;
        /** y coordinate for tango position */
        float y_t;
        /** z coordinate for tango position */
        float z_t;
        /** a priori standard deviation of distance between surface and point */
        float sd;
        /** a posteriori estimate of residual (distance between surface and point) */
        float v;
        /** a posteriori estimate of standard deviation of residual */
        float v_sd;
        /**
         * A plane equation in world coordinates for the closest facet of the terrain surface to this point.
         * Points with coordinates {@code x, y, z} in the plane should satisfy the equation
         * {@code x*plane[0] + y*plane[1] + z*plane[2] == plane[3]} and the 3 dimensional vector
         * {@code [plane[0], plane[1], plane[2]]} should be normalized to unity
         */
        float[] plane;

        /**
         * A surface model describing the terrain. The surface grid should be able to find the
         * plane equation of the closest facet to a point in world coordinates using the method
         * {@link DTMGrid#getSurfacePlane(float, float, float)}.
         */
        DTMGrid grid;

        @Override
        public void addToNormalEquations(Parameters p, float weight) {
            float x_w = (float)(x_t* p.cos_az - y_t* p.sin_az + p.x0);
            float y_w = (float)(x_t* p.sin_az + y_t* p.cos_az + p.y0);
            float z_w = (float)(z_t + p.z0);

            plane = grid.getSurfacePlane(x_w, y_w, z_w);

            if (plane != null) {
                planeEquations.B.set(0, 0, plane[0]);
                planeEquations.B.set(0, 1, plane[1]);
                planeEquations.B.set(0, 2, plane[2]);
                planeEquations.B.set(0, 3, -plane[0] * (x_t * p.sin_az + y_t * p.cos_az) + plane[1] * (x_t * p.cos_az - y_t * p.sin_az));

                planeEquations.f.set(0, 0, plane[3] - plane[0] * (p.cos_az * x_t - p.sin_az * y_t + p.x0) - plane[1] * (p.sin_az * x_t + p.cos_az * y_t + p.y0) - plane[2] * (z_t + p.z0));

                planeEquations.We.set(0, 0, weight / (sd * sd));

                updateN(planeEquations.B, planeEquations.We, N);
                updateT(planeEquations.B, planeEquations.We, planeEquations.f, t);

                updateN(planeEquations.f, planeEquations.We, vWv);
            }
        }

        @Override
        public float computeResiduals(Parameters p) {
            if (plane == null)
                return Float.MAX_VALUE;

            planeEquations.B.set(0,0,plane[0]);
            planeEquations.B.set(0,1,plane[1]);
            planeEquations.B.set(0,2,plane[2]);
            planeEquations.B.set(0,3, -plane[0]*(x_t* p.sin_az + y_t* p.cos_az) + plane[1]*(x_t* p.cos_az - y_t* p.sin_az));

            planeEquations.f.set(0, 0, plane[3] - plane[0]*(p.cos_az*x_t - p.sin_az*y_t + p.x0) - plane[1]*(p.sin_az*x_t + p.cos_az*y_t + p.y0) - plane[2]*(z_t + p.z0));

            planeEquations.We.set(0, 0, 1 / (sd * sd));
            planeEquations.Q.set(0, 0, sd * sd);

            computeQvv(planeEquations.Q, planeEquations.A, planeEquations.We, planeEquations.B, Ninv, planeEquations.Qvv);
            computeV(planeEquations.Q, planeEquations.A, planeEquations.We, planeEquations.f, planeEquations.B, x, planeEquations.v);

            v = (float)planeEquations.v.get(0,0);

            v_sd = (float) Math.sqrt(planeEquations.Qvv.get(0,0) * p.sigma2);

            return Math.abs(v) / v_sd;
        }

        @Override
        public int numConditions() {
            return plane != null ? 1 : 0;
        }
    }

    /**
     * Create and initialize a transform object with all relevant temp structures
     */
    public Transform() {
        // Normal equation matrixes
        N = new DMatrixRMaj(num_parameters, num_parameters);
        Ninv = new DMatrixRMaj(num_parameters, num_parameters);
        t = new DMatrixRMaj(num_parameters, 1);
        x = new DMatrixRMaj(num_parameters, 1);
        vWv = new DMatrixRMaj(1, 1);

        // Model equation matrixes for a 2D position observation
        posEquations2D.A.set(0, 0, 1);
        posEquations2D.A.set(1, 1, 1);
        posEquations2D.A.set(0, 2, -1);
        posEquations2D.A.set(1, 3, -1);

        posEquations2D.B.set(0,0,1);
        posEquations2D.B.set(1,1,1);

        // Model equation matrixes for a 3D position observation
        posEquations3D.A.set(0, 0, 1);
        posEquations3D.A.set(1, 1, 1);
        posEquations3D.A.set(2, 2, 1);
        posEquations3D.A.set(0, 3, -1);
        posEquations3D.A.set(1, 4, -1);
        posEquations3D.A.set(2, 5, -1);

        posEquations3D.B.set(0,0,1);
        posEquations3D.B.set(1,1,1);
        posEquations3D.B.set(2,2,1);

        // Model equation matrixes for an orientation observation
        orientEquations.A.set(0,0,-1);
        orientEquations.B.set(0,3,1);

        // Model equations for a point-in-plane observation
        planeEquations.A.set(0,0,-1);
    }

    /**
     * Recompute the transform from a list of observation data.
     *
     * @param observations the list of observations
     * @return false if the computations failed (singular equation system, fail to converge)
     */
    synchronized public boolean adjust(List<Observation> observations) {
        try {
            float [] weight = new float[observations.size()];
            Arrays.fill(weight, 1);

            for (int step = 1; step <= 3; ++step) {
                /*
                 * Step 1: run adjustment with all weights set to 1
                 * Step 2: run adjustment where observations with large normalized residuals are weighted down
                 * Step 3: run final adjustment where observations with very large normalized residuals
                 *             in step 2 are considered probable gross errors and are weighted down
                 */
                for (int iteration = 0; iteration < (step <= 2 ? 3 : 10); ++iteration) {
                    N.zero();
                    t.zero();
                    vWv.zero();

                    int r = -4;

                    // Build normal equations
                    int i = 0;
                    for (Observation obs : observations) {
                        obs.addToNormalEquations(parameters[step-1], weight[i++]);
                        r += obs.numConditions();
                    }

                    // Solve normal equations
                    if (!solveNormal(N, t, Ninv, x))
                        // Singular system
                        return false;
                    if (MatrixFeatures_DDRM.hasNaN(x) || MatrixFeatures_DDRM.hasNaN(Ninv))
                        // Bad...
                        return false;


                    // Extract parameters
                    parameters[step-1].x0 += x.get(0, 0);
                    parameters[step-1].y0 += x.get(1, 0);
                    parameters[step-1].z0 += x.get(2, 0);
                    parameters[step-1].az = normalizeAngle(parameters[step-1].az + x.get(3, 0));
                    parameters[step-1].sin_az = Math.sin(parameters[step-1].az);
                    parameters[step-1].cos_az = Math.cos(parameters[step-1].az);

                    // Compute sample variance
                    computeVWV(x, t, vWv);

                    parameters[step-1].sigma2 = vWv.get(0, 0) / r;
                    parameters[step-1].xy_sigma2 = parameters[step-1].sigma2 * (Ninv.get(0, 0) + Ninv.get(1, 1) + Ninv.get(0, 1) + Ninv.get(1, 0));
                    parameters[step-1].z_sigma2 = parameters[step-1].sigma2 * Ninv.get(2, 2);
                    parameters[step-1].az_sigma2 = parameters[step-1].sigma2 * Ninv.get(3, 3);

                    // Ok?
                    if (step <= 2) {
                        if (Math.abs(x.get(0, 0)) < 0.3 * Math.sqrt(Ninv.get(0, 0) * parameters[step-1].sigma2) &&
                                Math.abs(x.get(1, 0)) < 0.3 * Math.sqrt(Ninv.get(1, 1) * parameters[step-1].sigma2) &&
                                Math.abs(x.get(2, 0)) < 0.3 * Math.sqrt(Ninv.get(2, 2) * parameters[step-1].sigma2) &&
                                Math.abs(x.get(3, 0)) < 0.3 * Math.sqrt(Ninv.get(3, 3) * parameters[step-1].sigma2))
                            break;
                    }
                    else {
                        if (Math.abs(x.get(0, 0)) < 0.1 * Math.sqrt(Ninv.get(0, 0) * parameters[step-1].sigma2) &&
                                Math.abs(x.get(1, 0)) < 0.1 * Math.sqrt(Ninv.get(1, 1) * parameters[step-1].sigma2) &&
                                Math.abs(x.get(2, 0)) < 0.1 * Math.sqrt(Ninv.get(2, 2) * parameters[step-1].sigma2) &&
                                Math.abs(x.get(3, 0)) < 0.1 * Math.sqrt(Ninv.get(3, 3) * parameters[step-1].sigma2))
                            return true;
                    }
                }

                if (step <= 2) {
                    // Check for gross errors
                    int i = 0;
                    double k = step == 1 ? 1 : 0.6;
                    double a = step == 1 ? 4.4 : 6;
                    for (Observation obs : observations) {
                        float res = obs.computeResiduals(parameters[step-1]);// weight[i]);
                        if (res > 1) {
                            weight[i] = (float) Math.max(Math.exp(-0.05 * Math.pow(k * res, a)), 1e-6);
                            // Log.e("Transform", "bad residual in: " + String.valueOf(i));
                        } else
                            weight[i] = 1;
                        ++i;
                    }
                }
            }
        }
        catch (Exception ex) {
            Log.e("Transform", "adjust", ex);
            return false;
        }

        // No convergence
        return false;
    }

    /*
    * Axiliary functions
    */

    /**
     * Normalize an angle to the -pi, pi interval
     *
     * @param a a possibly un-normalized angle
     * @return the normalized angle int the -pi, pi interval
     */
    static public double normalizeAngle(double a) {
        return a - 2 * Math.PI * Math.floor((a + Math.PI) / (2 * Math.PI));
    }

    /**
     * Update normal equation matrix N with observation matrixes B and weights We
     *
     * N = N + B' * We * B
     *
     * @param B
     * @param We
     * @param N
     */
    private void updateN(DMatrixRMaj B, DMatrixRMaj We, DMatrixRMaj N) {
        tmp1.reshape(We.getNumRows(), B.getNumCols());
        CommonOps_DDRM.mult(We, B, tmp1);
        CommonOps_DDRM.multAddTransA(B, tmp1, N);
    }

    /**
     * Update normal equation vector t with observation
     *
     * t = t + B' * We * f
     *
     * @param B
     * @param We
     * @param f
     * @param t
     */
    private void updateT(DMatrixRMaj B, DMatrixRMaj We, DMatrixRMaj f, DMatrixRMaj t) {
        tmp1.reshape(We.getNumRows(), f.getNumCols());
        CommonOps_DDRM.mult(We, f, tmp1);
        CommonOps_DDRM.multAddTransA(B, tmp1, t);
    }


    /**
     * Solve normal equations
     *
     * Ninv = inv(N)
     * x = Ninv * t
     *
     * @param N
     * @param t
     * @param Ninv
     * @param x
     * @return true if N is non-singular
     */
    static private boolean solveNormal(DMatrixRMaj N, DMatrixRMaj t, DMatrixRMaj Ninv, DMatrixRMaj x) {
        if (!CommonOps_DDRM.invert(N, Ninv))
            return false;
        CommonOps_DDRM.mult(Ninv, t, x);
        return true;
    }

    /**
     * Update the weighted sum of residuals with the corrections to the parameters
     *
     * vWv=vWv-x'*t
     *
     * @param x
     * @param t
     * @param vWv
     */
    static private void computeVWV(DMatrixRMaj x, DMatrixRMaj t, DMatrixRMaj vWv) {
        CommonOps_DDRM.multAddTransA(-1, x, t, vWv);
    }

    /**
     * Compute residual vector
     *
     * v = Q * A' * We * (f - B * x)
     *
     * @param Q
     * @param A
     * @param We
     * @param f
     * @param B
     * @param x
     * @param v
     */
    private void computeV(DMatrixRMaj Q, DMatrixRMaj A, DMatrixRMaj We,
                                 DMatrixRMaj f, DMatrixRMaj B, DMatrixRMaj x, DMatrixRMaj v) {
        tmp1.set(f);
        CommonOps_DDRM.multAdd(-1, B, x, tmp1);
        tmp2.reshape(We.getNumRows(), tmp1.getNumCols());
        CommonOps_DDRM.mult(We, tmp1, tmp2);
        tmp1.reshape(A.getNumCols(), tmp2.getNumCols());
        CommonOps_DDRM.multTransA(A, tmp2, tmp1);
        CommonOps_DDRM.mult(Q, tmp1, v);
    }

    /**
     * Compute cofactor matrix for residuals
     *
     * Qvv = Q * A' * (We - We * B * Ninv * B' * We) * A * Q
     *
     * @param Q
     * @param A
     * @param We
     * @param B
     * @param Ninv
     * @param Qvv
     */
    private void computeQvv(DMatrixRMaj Q, DMatrixRMaj A, DMatrixRMaj We, DMatrixRMaj B,
                                   DMatrixRMaj Ninv, DMatrixRMaj Qvv) {
        tmp1.reshape(We.getNumRows(), B.getNumCols());
        CommonOps_DDRM.mult(We, B, tmp1);
        tmp2.reshape(tmp1.getNumRows(), Ninv.getNumCols());
        CommonOps_DDRM.mult(tmp1, Ninv, tmp2);
        tmp3.set(We);
        CommonOps_DDRM.multAddTransB(-1, tmp2, tmp1, tmp3);
        tmp1.reshape(A.getNumRows(), Q.getNumCols());
        CommonOps_DDRM.mult(A, Q, tmp1);
        tmp2.reshape(tmp3.getNumRows(), tmp1.getNumCols());
        CommonOps_DDRM.mult(tmp3, tmp1, tmp2);
        CommonOps_DDRM.multTransA(tmp1, tmp2, Qvv);
    }
}
