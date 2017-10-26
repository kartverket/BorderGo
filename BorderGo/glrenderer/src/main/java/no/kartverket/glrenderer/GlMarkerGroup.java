package no.kartverket.glrenderer;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by runaas on 06.09.2017.
 */

public class GlMarkerGroup extends GlDrawItem {
    private CopyOnWriteArrayList<float[]> mMatrixList = new CopyOnWriteArrayList<>();
    private GlDrawItem marker;

    public GlMarkerGroup(GlDrawItem marker)
    {
        this.marker = marker;
    }

    /**
     *
      * @param vpMatrix
     */
    public void draw(float[] vpMatrix) {
       float[] mvpMatrix = new float[16];

        for (float[] m: mMatrixList) {
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, m, 0);
            marker.draw(mvpMatrix);
        }
    }

    public List<float[]> modelMatrixList() {
        return mMatrixList;
    }

}
