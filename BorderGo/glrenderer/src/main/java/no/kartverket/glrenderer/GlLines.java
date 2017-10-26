package no.kartverket.glrenderer;

import java.util.ArrayList;

import no.kartverket.geometry.Line;
import no.kartverket.geometry.Pos;

/**
 * Created by janvin on 11/05/17.
 */

public class GlLines {
    ArrayList<GlLine> lines = new ArrayList<GlLine>();

    public GlLines(){

    }

    /**
     *
     * @param lines
     */
    public void setLines(Line[] lines){
        this.lines.clear();
        for(int i = 0; i<lines.length;i++){
            GlLine l= new GlLine();
            Line line = lines[i];
            l.setPositions(line.p1,line.p2);
            l.setColor(ArScene.LINE_COLOR);
            l.setWidth(ArScene.LINE_WIDTH);
            this.lines.add(l);
        }
    }

    /**
     *
     * @param lines
     */
    public void setLines(ColorLine[] lines){
        this.lines.clear();
        for(int i = 0; i<lines.length;i++){
            GlLine l= new GlLine();
            ColorLine line = lines[i];
            l.setPositions(line.p1,line.p2);
            l.setColor(line.color);
            l.setWidth(line.width);
            this.lines.add(l);
        }
    }

    /**
     *
     * @param mvpMatrix
     */
    public void draw(float[] mvpMatrix) {

        for (GlLine line: lines) {
            line.draw(mvpMatrix);
        }
    }

}
