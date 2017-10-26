package no.kartverket.positionorientation;

import org.ejml.data.DMatrixRMaj;
import org.ejml.equation.Equation;
import org.ejml.equation.Sequence;

/**
 * Simple implementation for a Kalman filter.<p>
 *
 * The code is based on the kalman filter example code in the
 * <a href="http://ejml.org/wiki/index.php?title=Example_Kalman_Filter">ejml manual</a>
 *
 */

public class KalmanFilter {
    /**
     * Set model and covariance matrixes for the next Kalman filter prediction step
     */
    public interface PredictionMatrixUpdater {

        /**
         * Fill matrixes with data for next prediction step
         *
         * @param F Prediction model matrix
         * @param Q Prediction covariance matrix
         */
        void updateMatrix(DMatrixRMaj F, DMatrixRMaj Q);
    }

    // Internal state and state change matrixes
    private int numParameters;
    private DMatrixRMaj x,P,Q,F;

    // Equation context
    private final Equation eq = new Equation();

    // Storage for precompiled code for predict and update
    private Sequence predictX,predictP;
    private Sequence updateY,updateK,updateX,updateP;

    public KalmanFilter(int numParameters) {
        this.numParameters = numParameters;

        // Parameter vector
        x = new DMatrixRMaj(numParameters,1);

        // Parameter covariances
        P = new DMatrixRMaj(numParameters,numParameters);

        // Prediction covariance
        Q = new DMatrixRMaj(numParameters,numParameters);

        // Prediction model matrix
        F = org.ejml.dense.row.CommonOps_DDRM.identity(numParameters,numParameters);


        eq.alias(x,"x",P,"P",Q,"Q",F,"F");

        // Observation vector dummy
        eq.alias(new DMatrixRMaj(1,1),"z");

        // Observation model matrix dummy
        eq.alias(new DMatrixRMaj(1,1),"H");

        // Observation covariance matrix dummy
        eq.alias(new DMatrixRMaj(1,1),"R");

        // Pre-compile so that it doesn't have to compile it each time it's invoked.  More cumbersome
        // but for small matrices the overhead is significant
        predictX = eq.compile("x = F*x");
        predictP = eq.compile("P = F*P*F' + Q");

        updateY = eq.compile("y = z - H*x");
        updateK = eq.compile("K = P*H'*inv( H*P*H' + R )");
        updateX = eq.compile("x = x + K*y");
        updateP = eq.compile("P = P-K*(H*P)");
    }

    /**
     * Initialize state
     * @param x_init initial parameters
     * @param P_init initial parameter covariances (diagonal elements of covariance matrix)
     */
    public void initialize(double x_init[], double P_init[]) {
        if (x_init.length != numParameters || P_init.length != numParameters)
            throw new IllegalArgumentException("Input dimensions doesn't match number of parameters");

        P.zero();
        for (int i = 0; i < numParameters; ++i) {
            x.set(i, 0, x_init[i]);
            P.set(i, i, P_init[i]);
        }
    }

    /**
     * Get parameter vector
     * @return parameter vector
     */
    public DMatrixRMaj getX() {
        return x;
    }

    /**
     * Get parameter covariance matrix
     * @return parameter covariance matrix
     */
    public DMatrixRMaj getP() {
        return P;
    }

    /**
     * Prediction step
     *
     * @param updater Set parameter transition model matrix and covariance matrix
     */
    public void predict(PredictionMatrixUpdater updater) {
        if (updater != null)
            updater.updateMatrix(F, Q);

        // Predict
        predictX.perform();
        predictP.perform();
    }

    /**
     * Update with new observations
     *
     * @param H observation model matrix
     * @param z vector of observations
     * @param R covariance matrix of observation vector
     */
    public void update(DMatrixRMaj H, DMatrixRMaj z, DMatrixRMaj R) {

        eq.alias(H,"H", z,"z", R,"R");

        updateY.perform();
        updateK.perform();
        updateX.perform();
        updateP.perform();
    }
}
