package no.kartverket.glrenderer;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL;

import no.kartverket.geometry.Pos;

/**
 * Created by janvin on 11/05/17.
 *
 * based on code from  http://stackoverflow.com/questions/16027455/what-is-the-easiest-way-to-draw-line-using-opengl-es-android
 * by the users Rodney Lambert and antonio
 *
 */

public class GlPolyLine extends GlDrawItem{



    private FloatBuffer VertexBuffer;

    private boolean depthFading = false;
    private static final String VertexShaderCode =
            // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader // WHY vec4 vPosition?
            "precision mediump float;" +
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "void main() {" +
                // the matrix must be included as a modifier of gl_Position
                "  gl_Position = uMVPMatrix * vPosition;" +
            "}";

    private static final String FragmentShaderCode =
            "precision mediump float;" +
            "uniform vec4 vColor;" +
            "void main() {"+
            "  gl_FragColor = vColor;"+
            "}";

    //http://www.songho.ca/opengl/gl_projectionmatrix.html
    // stack overflow discussion: https://stackoverflow.com/questions/7777913/how-to-render-depth-linearly-in-modern-opengl-with-gl-fragcoord-z-in-fragment-sh
    private static final String FragmentShaderDepthFaded =
            "precision mediump float;" +
                    "uniform vec4 vColor;" +
                    "uniform float fn2;" +
                    "uniform float f;" +
                    "uniform float n;" +
                    "uniform mat4 projInv;"+
                    "vec4 unprojM;"+
                    "float z0;" +
                    "float z;" +
                    "void main() {"+
                    "  z0 = gl_FragCoord.z*2.0f - 1.0f;" +
                    "  unprojM = projInv*vec4(0.0f, 0.0f, z0, 1.0f);" +
                    "  unprojM /= unprojM.w;" +
                    "  z = -n + 2.0f*unprojM.z/(f-n);" +
                    "  gl_FragColor = vec4(1.0f +z, 0.0f, 0.0f, 1.0f +z);"+
                    "}";

    static int GlProgram; //= OpenGlHelper.createProgram(VertexShaderCode,FragmentShaderCode);
    protected int PositionHandle;
    protected int ColorHandle;
    protected int MVPMatrixHandle;

    protected int Fn2Handle;
    protected int FHandle;
    protected int NHandle;
    protected int IPHandle;

    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 3;
    static float[] Coords = {
            0.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f
    };

    private int VertexCount = Coords.length / COORDS_PER_VERTEX;
    private final int VertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex


    public GlPolyLine() {
        resetBuffers();
        GlProgram = OpenGlHelper.createProgram(VertexShaderCode,FragmentShaderCode);

    }

    /**
     *
     * @param depthFading
     */
    public GlPolyLine(boolean depthFading) {
        resetBuffers();
        GlProgram = OpenGlHelper.createProgram(VertexShaderCode,FragmentShaderDepthFaded);
        this.depthFading = depthFading;
    }



    /**
     *
     */
    private void resetBuffers(){


        ByteBuffer bb = ByteBuffer.allocateDirect(
                // (number of coordinate values * 4 bytes per float)
                Coords.length * 4);
        // use the device hardware's native byte order
        bb.order(ByteOrder.nativeOrder());

        if(VertexBuffer != null){VertexBuffer.clear();};
        // create a floating point buffer from the ByteBuffer
        VertexBuffer = bb.asFloatBuffer();
        // add the coordinates to the FloatBuffer
        VertexBuffer.put(Coords);
        // set the buffer to read the first coordinate
        VertexBuffer.position(0);

        VertexCount = Coords.length / COORDS_PER_VERTEX;
    }

    /**
     *
     * @param positions
     */
    public void setPositions(Pos[] positions){

        Coords = new float[positions.length*3];
        for(int i = 0;i<positions.length;i++){
            int offset = i*3;
            Pos p = positions[i];
            Coords[offset] = p.x;
            Coords[offset+1] = p.y;
            Coords[offset+2] = p.z;

        }

        resetBuffers();

    }
    private float[] inverseProjection = {
        1f,     0,      0,      0,
        0,      1f,     0,      0,
        0,      0,      1f,     0,
        0,      0,      0,      1f
    }; // initializes as unity

    /**
     *
     * @param mvpMatrix
     * @param inverseProjection
     */
    public void draw(float[] mvpMatrix, float[] inverseProjection) {
        this.inverseProjection = inverseProjection;
        draw(mvpMatrix);
    }

    /**
     *
     * @param mvpMatrix
     */
    public void draw(float[] mvpMatrix) {
        // Add program to OpenGL ES environment
        GLES20.glUseProgram(GlProgram);


        GLES20.glLineWidth(width);
        //GLES20.glClearColor(0f,0f,0f,0f);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);



        // get handle to vertex shader's vPosition member
        PositionHandle = GLES20.glGetAttribLocation(GlProgram, "vPosition");

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(PositionHandle);

        // Prepare the triangle coordinate data
        GLES20.glVertexAttribPointer(PositionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                VertexStride, VertexBuffer);

        // get handle to fragment shader's vColor member
        ColorHandle = GLES20.glGetUniformLocation(GlProgram, "vColor");

                // Set color for drawing the triangle
        GLES20.glUniform4fv(ColorHandle, 1, color, 0);

        if(depthFading){
            Fn2Handle = GLES20.glGetUniformLocation(GlProgram, "fn2");
            FHandle = GLES20.glGetUniformLocation(GlProgram, "f");
            NHandle = GLES20.glGetUniformLocation(GlProgram, "n");
            IPHandle = GLES20.glGetUniformLocation(GlProgram, "projInv");
            float near = ArScene.NEAR;
            float far = ArScene.FAR;

            GLES20.glUniform1f(Fn2Handle,  2.0f*near*far);
            GLES20.glUniform1f(FHandle,  far);
            GLES20.glUniform1f(NHandle,  near);
            GLES20.glUniformMatrix4fv(IPHandle,1,false,inverseProjection,0);
        }



       /* "uniform float fn2;" +
                "uniform float fmn;" +
                "uniform float fpn;" +

        */

        // get handle to shape's transformation matrix
        MVPMatrixHandle = GLES20.glGetUniformLocation(GlProgram, "uMVPMatrix");
        //ArRenderer.checkGlError("glGetUniformLocation");

        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(MVPMatrixHandle, 1, false, mvpMatrix, 0);
        //ArRenderer.checkGlError("glUniformMatrix4fv");

        // Draw the triangle
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, VertexCount);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(PositionHandle);
    }
}