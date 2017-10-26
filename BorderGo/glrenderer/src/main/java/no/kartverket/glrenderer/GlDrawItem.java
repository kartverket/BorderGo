package no.kartverket.glrenderer;

/**
 * Created by janvin on 15/05/17.
 */

public class GlDrawItem {



    // Set color with red, green, blue and alpha (opacity) values
    float[] color = { 0.0f, 0.0f, 0.0f, 1.0f };
    float width = 1.0f;

    /**
     *
     * @param c
     */
    public void setColor(GlColor c){
        color[0] = c.r;
        color[1] = c.g;
        color[2] = c.b;
        color[3] = c.a;
    }

    /**
     *
     * @param red
     * @param green
     * @param blue
     * @param alpha
     */
    public void setColor(float red, float green, float blue, float alpha) {
        color[0] = red;
        color[1] = green;
        color[2] = blue;
        color[3] = alpha;
    }

    /**
     *
     * @param width
     */
    public void setWidth(float width){
        this.width = width;
    }


    /**
     *
     * @param mvpMatrix
     */
    public void draw(float[] mvpMatrix) {

    }
}
