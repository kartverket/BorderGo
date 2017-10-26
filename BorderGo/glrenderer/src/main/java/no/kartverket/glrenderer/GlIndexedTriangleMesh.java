package no.kartverket.glrenderer;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import no.kartverket.geometry.Pos;

/**
 * Created by runaas on 06.07.2017.
 */

public class GlIndexedTriangleMesh extends GlDrawItem {



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
                    "uniform vec4 uColor;" +
                    "void main() {"+
                    "  gl_FragColor = uColor;"+
                    "}";

    private int GlProgram = -1;

    static final int COORDS_PER_VERTEX = 3;
    private float[] Coords = null;
    private short[] Indexes = null;

    private int IndexCount = 0;
    private final int VertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    private FloatBuffer VertexBuffer;
    private ShortBuffer IndexBuffer;

    private int[] bufId = null;

    private boolean buffersDirty = false;

    void clearGl() {
        bufId = null;
        GlProgram = -1;
        buffersDirty = true;
    }
    private void resetBuffers(){

        if (Coords != null) {
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
        }
        else {
            VertexBuffer = null;
        }
        if (Indexes != null) {
            ByteBuffer bb = ByteBuffer.allocateDirect(
                    // (number of index values * 4 bytes per int)
                    Indexes.length * 4);
            // use the device hardware's native byte order
            bb.order(ByteOrder.nativeOrder());

            // create a floating point buffer from the ByteBuffer
            IndexBuffer = bb.asShortBuffer();
            // add the coordinates to the FloatBuffer
            IndexBuffer.put(Indexes);
            // set the buffer to read the first coordinate
            IndexBuffer.position(0);

            IndexCount = Indexes.length;
        }
        else {
            IndexBuffer = null;
            IndexCount = 0;
        }
        buffersDirty = true;
    }

    public GlIndexedTriangleMesh() {
        resetBuffers();


    }

    /**
     *
     * @param positions
     * @param indexes
     */
    public void setData(Pos[] positions, short[] indexes){

        Coords = new float[positions.length*3];
        for(int i = 0;i<positions.length;i++){
            int offset = i*3;
            Pos p = positions[i];
            Coords[offset] = p.x;
            Coords[offset+1] = p.y;
            Coords[offset+2] = p.z;

        }

        Indexes = (short[])indexes.clone();

        resetBuffers();
    }

    /**
     *
     * @param mvpMatrix
     */
    public void draw(float[] mvpMatrix) {

        if (IndexBuffer == null && VertexBuffer == null)
            return;

        if (buffersDirty) {
            if (bufId == null) {
                bufId = new int[2];
                GLES20.glGenBuffers(2, bufId, 0);
            }

            if (bufId[0] > 0 && bufId[1] > 0) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufId[0]);
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, VertexBuffer.capacity() * Float.BYTES, VertexBuffer, GLES20.GL_STATIC_DRAW);

                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, bufId[1]);
                GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, IndexBuffer.capacity() * Short.BYTES, IndexBuffer, GLES20.GL_STATIC_DRAW);

                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
            } else {
                Log.e(getClass().getSimpleName() + ".draw()", "glGenBuffers");
            }

            buffersDirty = false;
        }

        if (GlProgram < 0)
            GlProgram = OpenGlHelper.createProgram(VertexShaderCode, FragmentShaderCode);

        // Add program to OpenGL ES environment
        GLES20.glUseProgram(GlProgram);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);


        // get handle to fragment shader's vColor member
        int ColorHandle = GLES20.glGetUniformLocation(GlProgram, "uColor");

        // Set color for drawing the triangle
        GLES20.glUniform4fv(ColorHandle, 1, color, 0);

        // get handle to shape's transformation matrix
        int MVPMatrixHandle = GLES20.glGetUniformLocation(GlProgram, "uMVPMatrix");

        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(MVPMatrixHandle, 1, false, mvpMatrix, 0);

        // Bind Attributes
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufId[0]);

        // get handle to vertex shader's vPosition member
        int PositionHandle = GLES20.glGetAttribLocation(GlProgram, "vPosition");

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(PositionHandle);

        // Prepare the triangle coordinate data
        GLES20.glVertexAttribPointer(PositionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                VertexStride, 0);


        // Bind indexes
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, bufId[1]);

        // Draw
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, IndexCount, GLES20.GL_UNSIGNED_SHORT, 0);

        // Unbind buffers
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(PositionHandle);
    }

}
