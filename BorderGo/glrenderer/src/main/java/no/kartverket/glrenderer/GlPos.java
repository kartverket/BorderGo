package no.kartverket.glrenderer;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import no.kartverket.geometry.Pos;

/**
 * Created by janvin on 11/05/17.
 *
 * based on code from  http://stackoverflow.com/questions/16027455/what-is-the-easiest-way-to-draw-line-using-opengl-es-android
 * by the users Rodney Lambert and antonio
 *
 *  A single pos with a width and color (
 *
 */

public class GlPos extends GlDrawItem{



    private FloatBuffer VertexBuffer;

    private final String VertexShaderCode =
            // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader // WHY vec4 vPosition?
            "uniform mat4 uMVPMatrix;" +
            "uniform float pointWidth;" +
            "attribute vec4 vPosition;" +

            "void main() {" +
            // the matrix must be included as a modifier of gl_Position
            "  gl_Position = uMVPMatrix * vPosition;" +
            "  gl_PointSize = pointWidth;" +
            "}";

    private final String FragmentShaderCode =
            "precision mediump float;" +
            "uniform vec4 vColor;" +
            "void main() {" +
            "  gl_FragColor = vColor;" +
            "}";

    protected int GlProgram;
    protected int PositionHandle;
    protected int WidthHandle;
    protected int ColorHandle;
    protected int MVPMatrixHandle;

    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 3;
    static float[] Coord = {
            0.0f, 0.0f, 0.0f
    };

    private final int VertexCount = 1;
    private final int VertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex



    public GlPos() {
        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb = ByteBuffer.allocateDirect(
                // (number of coordinate values * 4 bytes per float)
                Coord.length * 4);
        // use the device hardware's native byte order
        bb.order(ByteOrder.nativeOrder());

        // create a floating point buffer from the ByteBuffer
        VertexBuffer = bb.asFloatBuffer();
        // add the coordinates to the FloatBuffer
        VertexBuffer.put(Coord);
        // set the buffer to read the first coordinate
        VertexBuffer.position(0);

        GlProgram = OpenGlHelper.createProgram(VertexShaderCode,FragmentShaderCode);

        //int vertexShader = OpenGlHelper.loadShader(GLES20.GL_VERTEX_SHADER, VertexShaderCode);
        //int fragmentShader = OpenGlHelper.loadShader(GLES20.GL_FRAGMENT_SHADER, FragmentShaderCode);

        //GlProgram = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        //GLES20.glAttachShader(GlProgram, vertexShader);   // add the vertex shader to program
        //GLES20.glAttachShader(GlProgram, fragmentShader); // add the fragment shader to program
        //GLES20.glLinkProgram(GlProgram);                  // creates OpenGL ES program executables
    }

    /**
     *
     * @param p1
     */
    public void setPos(Pos p1) {
        setVertex(p1.x,p1.y,p1.z);
    }

    /**
     *
     * @param x
     * @param y
     * @param z
     */
    public void setVertex(float x, float y, float z) {
        Coord[0] = x;
        Coord[1] = y;
        Coord[2] = z;
        VertexBuffer.clear();
        VertexBuffer.put(Coord);
        // set the buffer to read the first coordinate
        VertexBuffer.position(0);
    }

    /**
     * 
     * @param mvpMatrix
     */
    public void draw(float[] mvpMatrix) {
        // Add program to OpenGL ES environment
        GLES20.glUseProgram(GlProgram);

        GLES20.glEnable(GLES20.GL_BLEND);

        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // get handle to vertex shader's vPosition member
        PositionHandle = GLES20.glGetAttribLocation(GlProgram, "vPosition");

        WidthHandle = GLES20.glGetUniformLocation(GlProgram, "pointWidth");
        GLES20.glUniform1f(WidthHandle,width);

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



        // get handle to shape's transformation matrix
        MVPMatrixHandle = GLES20.glGetUniformLocation(GlProgram, "uMVPMatrix");
        //ArRenderer.checkGlError("glGetUniformLocation");

        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(MVPMatrixHandle, 1, false, mvpMatrix, 0);
        //ArRenderer.checkGlError("glUniformMatrix4fv");

        // Draw the triangle
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, VertexCount);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(PositionHandle);
    }
}