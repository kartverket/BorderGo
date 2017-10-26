package no.kartverket.glrenderer;
/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.google.atap.tangoservice.TangoCameraIntrinsics;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * An OpenGL renderer that renders the Tango RGB camera texture on a full-screen background
 * and two spheres representing the earth and the moon in Augmented Reality.
 */
public class ArGlRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = ArGlRenderer.class.getSimpleName();

    /**
     * A small callback to allow the caller to introduce application-specific code to be executed
     * in the OpenGL thread.
     */
    public interface RenderCallback {
        void preRender();
    }

    private ArScene scene;




    private RenderCallback mRenderCallback;
    private OpenGlCameraPreview mOpenGlCameraPreview;
    private boolean mProjectionMatrixConfigured;
    private Context mContext;

    /**
     *
     * @param context
     * @param scene
     * @param callback
     */
    public ArGlRenderer(Context context,ArScene scene, RenderCallback callback) {
        mContext = context;
        this.scene = scene;
        mRenderCallback = callback;
        mOpenGlCameraPreview = new OpenGlCameraPreview();
    }

    /**
     *
     * @param gl10
     * @param eglConfig
     */
    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        // Enable depth test to discard fragments that are behind another fragment.
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        // Enable face culling to discard back-facing triangles.
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);
        mOpenGlCameraPreview.setUpProgramAndBuffers();
        scene.updateGlLines();

    }

    /**
     *
     * Update background texture's UV coordinates when device orientation is changed (i.e., change between landscape and portrait mode).
     *
     *
     * @param rotation
     */
    public void updateColorCameraTextureUv(int rotation) {
        mOpenGlCameraPreview.updateTextureUv(rotation);
        mProjectionMatrixConfigured = false;
    }

    /**
     *
     * @param gl10
     * @param width
     * @param height
     */
    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        mProjectionMatrixConfigured = false;

    }

    /**
     *
     * @param gl10
     */
    @Override
    public void onDrawFrame(GL10 gl10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Call application-specific code that needs to run on the OpenGL thread.
        mRenderCallback.preRender();
        // Don't write depth buffer because we want to draw the camera as background.
        GLES20.glDepthMask(false);
        mOpenGlCameraPreview.drawAsBackground();
        // Enable depth buffer again for AR.
        GLES20.glDepthMask(true);
        GLES20.glCullFace(GLES20.GL_BACK);



        scene.draw();
    }

    /**
     * It returns the ID currently assigned to the texture where the Tango color camera contents
     * should be rendered.
     * NOTE: This must be called from the OpenGL render thread; it is not thread safe.
     *
     * @return
     */
    public int getTextureId() {
        return mOpenGlCameraPreview == null ? -1 : mOpenGlCameraPreview.getTextureId();
    }

    /**
     * Set the Projection matrix matching the Tango RGB camera in order to be able to do
     * Augmented Reality.
     *
     * @param matrixFloats
     */
    public void setProjectionMatrix(float[] matrixFloats) {
        //mEarthSphere.setProjectionMatrix(matrixFloats);
        //mMoonSphere.setProjectionMatrix(matrixFloats);
        mProjectionMatrixConfigured = true;
        scene.setProjectionMatrix(matrixFloats);
    }

    /**
     * Update the View matrix matching the pose of the Tango RGB camera.
     *
     * @param ssTcamera The transform from RGB camera to Start of Service.
     */
    public void updateViewMatrix(float[] ssTcamera) {
        float[] viewMatrix = new float[16];
        Matrix.invertM(viewMatrix, 0, ssTcamera, 0);
        scene.setViewMatrix(viewMatrix);
    }




    /**
     * Use Tango camera intrinsics to calculate the projection Matrix for the OpenGL scene.
     *
     * @param intrinsics camera instrinsics for computing the project matrix.
     */
    public static float[] projectionMatrixFromCameraIntrinsics(TangoCameraIntrinsics intrinsics) {

        Log.i("cx",Double.toString(intrinsics.cx));
        Log.i("cy",Double.toString(intrinsics.cy));
        Log.i("width",Double.toString(intrinsics.width));
        Log.i("height",Double.toString(intrinsics.height));
        float cx = (float) intrinsics.cx;
        float cy = (float) intrinsics.cy;
        float width = (float) intrinsics.width;
        float height = (float) intrinsics.height;
        float fx = (float) intrinsics.fx;
        float fy = (float) intrinsics.fy;

        // Uses frustumM to create a projection matrix, taking into account calibrated camera
        // intrinsic parameter.
        // Reference: http://ksimek.github.io/2013/06/03/calibrated_cameras_in_opengl/
        float near = ArScene.NEAR;// 0.1f;
        float far = ArScene.FAR;;

        float xScale = near / fx;
        float yScale = near / fy;
        float xOffset = (cx - (width / 2.0f)) * xScale;
        // Color camera's coordinates has y pointing downwards so we negate this term.
        float yOffset = -(cy - (height / 2.0f)) * yScale;

        float m[] = new float[16];
        Matrix.frustumM(m, 0,
                xScale * (float) -width / 2.0f - xOffset,
                xScale * (float) width / 2.0f - xOffset,
                yScale * (float) -height / 2.0f - yOffset,
                yScale * (float) height / 2.0f - yOffset,
                near, far);
        return m;
    }

    /**
     *
     * @param matrix
     * @return
     */
    public static float[] invert4x4Matrix( float[] matrix){
        if(matrix.length == 16){
            float m[] = new float[16];
            Matrix.invertM(m,0,matrix,0);
            return m;
        }
        return null;
    }


    /**
     *
     * @return
     */
    public boolean isProjectionMatrixConfigured(){
        return mProjectionMatrixConfigured;
    }
}
