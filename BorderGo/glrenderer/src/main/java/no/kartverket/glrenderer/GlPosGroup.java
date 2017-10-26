package no.kartverket.glrenderer;


import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import no.kartverket.geometry.Pos;

import static android.R.attr.lines;

/**
 * Created by janvin on 15/05/17.
 *
 *  A group of positions/points with the same color and width to be rendered in bulk
 *
 */

public class GlPosGroup extends GlDrawItem{
    private FloatBuffer VertexBuffer;

    private final String VertexShaderCode =
            // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader
            // WHY vec4 vPosition?
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

    private int GlProgram; //= OpenGlHelper.createProgram(VertexShaderCode,FragmentShaderCode);
    protected int PositionHandle;
    protected int WidthHandle;
    protected int ColorHandle;
    protected int MVPMatrixHandle;

    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 3;
    static float[] Coords = {
            0.0f, 0.0f, 0.0f
    };

    private int VertexCount = Coords.length / COORDS_PER_VERTEX;
    private final int VertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    public GlPosGroup() {

        resetBuffers();
        GlProgram = OpenGlHelper.createProgram(VertexShaderCode,FragmentShaderCode);

        /*// initialize vertex byte buffer for shape coordinates
        ByteBuffer bb = ByteBuffer.allocateDirect(
                // (number of coordinate values * 4 bytes per float)
                Coords.length * 4);
        // use the device hardware's native byte order
        bb.order(ByteOrder.nativeOrder());

        // create a floating point buffer from the ByteBuffer
        VertexBuffer = bb.asFloatBuffer();
        // add the coordinates to the FloatBuffer
        VertexBuffer.put(Coords);
        // set the buffer to read the first coordinate
        VertexBuffer.position(0);
        */



        //int vertexShader = OpenGlHelper.loadShader(GLES20.GL_VERTEX_SHADER, VertexShaderCode);
        //int fragmentShader = OpenGlHelper.loadShader(GLES20.GL_FRAGMENT_SHADER, FragmentShaderCode);

        //GlProgram = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        //GLES20.glAttachShader(GlProgram, vertexShader);   // add the vertex shader to program
        //GLES20.glAttachShader(GlProgram, fragmentShader); // add the fragment shader to program
        //GLES20.glLinkProgram(GlProgram);                  // creates OpenGL ES program executables
    }


    /**
     *
     * @param positions
     */
    public void setPositions(Pos[] positions){
        //this.lines.clear();
        Coords = new float[positions.length*3];

        for(int i = 0;i<positions.length;i++){
               int offset = i*3;
               Pos p = positions[i];
               Coords[offset] =   p.x;
               Coords[offset+1] = p.y;
               Coords[offset+2] = p.z;

        }

        resetBuffers();

    }


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
