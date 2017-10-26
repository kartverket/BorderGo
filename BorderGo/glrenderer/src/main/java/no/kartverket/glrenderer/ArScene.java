package no.kartverket.glrenderer;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import no.kartverket.geometry.IndexedTriangleMesh;
import no.kartverket.geometry.Line;
import no.kartverket.geometry.PolyLine;
import no.kartverket.geometry.PolyLineGroup;
import no.kartverket.geometry.Pos;
import no.kartverket.geometry.PosGroup;


/**
 * Created by janvin on 09/05/17.
 */



public class ArScene {
    private CopyOnWriteArrayList<ColorLine> colorLines = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<ColorPos> colorPositions = new CopyOnWriteArrayList<ColorPos>();
    private CopyOnWriteArrayList<ColorPosGroup> colorPosGroups = new CopyOnWriteArrayList<ColorPosGroup>();
    private CopyOnWriteArrayList<ColorPolyLine> colorPolyLines = new CopyOnWriteArrayList<ColorPolyLine>();
    private CopyOnWriteArrayList<ColorPolyLine> hiddenColorPolyLines = new CopyOnWriteArrayList<ColorPolyLine>();

    private CopyOnWriteArrayList<ColorPolyLine> borderPolyLines = new CopyOnWriteArrayList<ColorPolyLine>();
    //private ColorPolyLineGroup borderPolyLines = new ColorPolyLineGroup(); // do later
    private CopyOnWriteArrayList<IndexedTriangleMesh> depthFillMeshes = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Pos> calibrationMarkers = new CopyOnWriteArrayList<>();

    private boolean linesDirty = true;
    private boolean polyLinesDirty = true;
    private boolean hiddenPolyLinesDirty = true;
    private boolean positionsDirty = true;
    private boolean posGroupsDirty = true;
    private boolean depthFillMeshesDirty = true;
    private boolean markerGroupsDirty = true;

    private boolean hiddenLineRemoval = true;

    private GlLines glLines = new GlLines();
    private ArrayList<GlPolyLine> glPolyLines = new ArrayList<GlPolyLine>();
    private ArrayList<GlPolyLine> glHiddenPolyLines = new ArrayList<GlPolyLine>();
    private ArrayList<GlPolyLine> glBorderPolyLines = new ArrayList<GlPolyLine>();
    private ArrayList<GlMarkerGroup> glMarkerGroups = new ArrayList<>();

    private ArrayList<GlPos> glPositions = new ArrayList<GlPos>();
    private ArrayList<GlPosGroup> glPosGroups = new ArrayList<GlPosGroup>();

    private ArrayList<GlIndexedTriangleMesh> glDepthFillSurfaces = new ArrayList<>();

    private GlPos highlightPos = new GlPos(){};

    private GlIndexedTriangleMesh calibrationMarkerMesh =
            new GlIndexedTriangleMesh();

    private final float[] mViewMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mTangoWorldMatrix = new float[] {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1,
    };
    private final float[] mFlipMatrix = new float[] {
            1, 0, 0, 0,
            0, 0, -1, 0,
            0, 1, 0, 0,
            0, 0, 0, 1
    };
    private final float[] mvMatrix = new float[16];
    private final float[] tmpMatrix = new float[16];

    public static final float NEAR = 0.1f;
    public static final float FAR = 600f;

    public ArScene(){
        Pos[] markerMeshVertex = new Pos[] { new Pos(), new Pos(), new Pos(), new Pos()};
        markerMeshVertex[0].x = 0;
        markerMeshVertex[0].y = 0;
        markerMeshVertex[0].z = 0;
        markerMeshVertex[1].x = 0;
        markerMeshVertex[1].y = -0.15f;
        markerMeshVertex[1].z = 0.5f;
        markerMeshVertex[2].x = 0.13f;
        markerMeshVertex[2].y = 0.075f;
        markerMeshVertex[2].z = 0.5f;
        markerMeshVertex[3].x = -0.13f;
        markerMeshVertex[3].y = 0.075f;
        markerMeshVertex[3].z = 0.5f;

        short[] markerMeshIndex = new short[] {
                1, 2, 3,
                0, 2, 1,
                0, 3, 2,
                0, 1, 3
        };

        calibrationMarkerMesh.setData(markerMeshVertex, markerMeshIndex);
        calibrationMarkerMesh.setColor(1, 1, 0, 1);
    }

    /**
     * The draw method calls draw on all types of objects in the scene
     */
    public void draw() {

        this.updateScene();

        Matrix.multiplyMM(mvMatrix, 0, mViewMatrix, 0, mFlipMatrix, 0);
        Matrix.multiplyMM(tmpMatrix, 0, mvMatrix, 0, mTangoWorldMatrix, 0);
        Matrix.multiplyMM(mvMatrix, 0, mProjectionMatrix, 0, tmpMatrix, 0);

        // POINTS
        for (GlPos pos: glPositions) {
            pos.draw(mvMatrix);
        }

        // POS GROUPS
        for(GlPosGroup pg: glPosGroups){
            pg.draw(mvMatrix);
        }

        // LINES
        glLines.draw(mvMatrix);

        // POLYLINES
        //float[] invProj = ArGlRenderer.invert4x4Matrix(mProjectionMatrix);
        for (GlPolyLine polyLine: glPolyLines) {
            //polyLine.draw(mvMatrix, invProj);
            polyLine.draw(mvMatrix);
        }

        // markers
        for (GlMarkerGroup markerGroup: glMarkerGroups) {
            markerGroup.draw(mvMatrix);
        }

        if(hiddenLineRemoval) {
            // Fill depth buffer
            GLES20.glDepthMask(true);
            GLES20.glColorMask(false, false, false, false);
            GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL);
            GLES20.glPolygonOffset(2.0f, 2.0f);
            for(GlIndexedTriangleMesh mesh: glDepthFillSurfaces) {
                mesh.draw(mvMatrix);
            }
            GLES20.glColorMask(true, true, true, true);
            GLES20.glPolygonOffset(0.0f, 0.0f);


        }
        // "Hidden" polylines
        for (GlPolyLine polyLine: glHiddenPolyLines) {
            //polyLine.draw(mvMatrix, invProj);
            polyLine.draw(mvMatrix);
        }


    }

    public void updateScene(){
        this.updateDepthFillSurfaces();
        this.updateGlLines();
        this.updateGlPolyLines();
        this.updateGlHiddenPolyLines();
        this.updateGlPositions();
        this.updateGlPosGroups();
        this.updateMarkers();
    }

    public void forceSceneUpdate() {
        linesDirty = true;
        polyLinesDirty = true;
        hiddenPolyLinesDirty = true;
        positionsDirty = true;
        posGroupsDirty = true;
        depthFillMeshesDirty = true;
        markerGroupsDirty = true;
        calibrationMarkerMesh.clearGl();
    }

    public void setHiddenLineRemoval(boolean hiddenLineRemoval){
        this.hiddenLineRemoval = hiddenLineRemoval;
    }

    public boolean hasHiddenLineRemoval(){
        return this.hiddenLineRemoval;
    }


    /**
     *
     * @param matrix
     */
    public void setViewMatrix(float[] matrix) {
        System.arraycopy(matrix, 0, mViewMatrix, 0, 16);
    }

    /**
     *
     * @param matrix
     */
    public void setProjectionMatrix(float[] matrix) {
        System.arraycopy(matrix, 0, mProjectionMatrix, 0, 16);
    }

    /**
     *
     * @param matrix
     */
    public void setTangoWorldMatrix(float[] matrix) {
        System.arraycopy(matrix, 0, mTangoWorldMatrix, 0, 16);
    }


    /**
     * removes object in scene
     */
    public void clearScene(){
        // Todo: hook up to gl renderer
        depthFillMeshes.clear();
        colorLines.clear();
        colorPositions.clear();
        colorPolyLines.clear();
        hiddenColorPolyLines.clear();
        calibrationMarkers.clear();
    }

    public static final float alpha = 0.5f;

    public static final GlColor     LINE_COLOR      = new GlColor() {        {r=0f; g=0.8f; b=0.1f; a=alpha;}    };

    public static final GlColor     POINT_COLOR     = new GlColor() {        {r=0.3f; g=1f; b=0.1f; a=alpha;}    };

    public static final GlColor     BORDER_COLOR    = new GlColor() {        {r=1.0f; g=0f; b=0.5f; a=alpha;}    };

    public static final GlColor     BORDER_POINT_COLOR    = new GlColor() {        {r=0f; g=0f; b=1f; a=alpha;}    };

    public static final float       LINE_WIDTH      = 4.0f;

    public static final float       POINT_WIDTH     = 4.0f;

    public static final float       BORDER_WIDTH    = 8.0f;

    public static final float       BORDER_POINT_WIDTH    = 16.0f;

    /**
     *
     * @param borders
     */
    public void setBorderPolyLines(PolyLineGroup borders){
        int l = borders.polylines.length;
        PolyLine[] polys = borders.polylines;

        for(int i= 0; i < l;i++){
            //TODO: implement

        }
    }

    /**
     *
     * @param borders
     */
    public void setBorderPolyLines(PolyLine[] borders){
        PolyLineGroup group = new PolyLineGroup();
        group.polylines = borders;
        setBorderPolyLines(group);
    }

    //MASTER METHOD addPos

    /**
     *
     * MASTER METHOD addPos
     *
     * @param pos
     * @param color
     * @param width
     * @return
     */
    public ColorPos addPos(Pos pos, GlColor color, float width){
        ColorPos p = new ColorPos();
        p.x = pos.x;
        p.y = pos.y;
        p.z = pos.z;
        p.color = color;
        p.width = width;
        colorPositions.add(p);
        positionsDirty = true;
        return p;
    }

    /**
     *
     * @param x
     * @param y
     * @param z
     * @param color
     * @param width
     * @return
     */
    public ColorPos addPos(float x, float y, float z,  GlColor color, float width) {
        Pos p = new Pos();
        p.x = x;
        p.y = y;
        p.z = z;
        return addPos(p,color,width);
    }

    /**
     *
     * @param pos
     * @return
     */
    public ColorPos addPos(Pos pos){
        return addPos(pos, POINT_COLOR, POINT_WIDTH);
    }

    public ColorPos addPos(float x, float y, float z){
        return addPos(x,y,z, POINT_COLOR, POINT_WIDTH);
    }

    /**
     *
     * MASTER METHOD addLine
     *
     * @param line
     * @param color
     * @param width
     * @return
     */
    public ColorLine addLine(Line line, GlColor color, float width){
        ColorLine l = new ColorLine();
        l.p1 = line.p1;
        l.p2 = line.p2;
        l.color = color;
        l.width = width;
        colorLines.add(l);
        linesDirty = true;
        return l;

    }

    /**
     *
     * @param p1
     * @param p2
     * @param color
     * @param width
     * @return
     */
    public ColorLine addLine(Pos p1, Pos p2, GlColor color, float width){
        Line l = new Line();
        l.p1 = p1;
        l.p2 = p2;
        return addLine(l,color,width);
    }

    /**
     *
     * @param x1
     * @param y1
     * @param z1
     * @param x2
     * @param y2
     * @param z2
     * @param color
     * @param width
     * @return
     */
    public ColorLine addLine( float x1,float y1,float z1,float x2,float y2,float z2, GlColor color, float width){
        Pos p1 = new Pos();
        p1.x = x1;
        p1.y = y1;
        p1.z = z1;

        Pos p2 = new Pos();
        p2.x = x2;
        p2.y = y2;
        p2.z = z2;
        return addLine(p1,p2,color,width);
    }

    /**
     *
     * @param line
     * @return
     */
    public ColorLine addLine(Line line){
        return addLine(line, LINE_COLOR, LINE_WIDTH);
    }

    /**
     *
     * @param p1
     * @param p2
     * @return
     */
    public ColorLine addLine(Pos p1, Pos p2){
        return addLine(p1,p2, LINE_COLOR, LINE_WIDTH);
    }

    public ColorLine addLine(float x1,float y1,float z1,float x2,float y2,float z2){
        return addLine(x1,y1,z1,x2,y2,z2, LINE_COLOR, LINE_WIDTH);
    }


    /**
     *
     * MASTER METHOD addPosGroup
     *
     * @param posGroup
     * @param color
     * @param width
     * @return
     */
    public ColorPosGroup addPosGroup(PosGroup posGroup,GlColor color, float width){
        ColorPosGroup pg = new ColorPosGroup();
        pg.positions = posGroup.positions;
        pg.color = color;
        pg.width = width;
        posGroupsDirty = true;
        colorPosGroups.add(pg);
        return pg;
    }

    /**
     *
     * @param posGroup
     * @return
     */
    public ColorPosGroup addPosGroup(PosGroup posGroup){
        return addPosGroup(posGroup,POINT_COLOR,POINT_WIDTH);
    }

    /**
     *
     * @param positions
     * @param color
     * @param width
     * @return
     */
    public ColorPosGroup addPosGroup(Pos[] positions, GlColor color, float width){
        PosGroup pg = new PosGroup();
        pg.positions = positions;
        return addPosGroup(pg,color,width);
    }

    /**
     *
     * @param positions
     * @return
     */
    public ColorPosGroup addPosGroup(Pos[] positions){
        return addPosGroup(positions,POINT_COLOR,POINT_WIDTH);
    }

    /**
     *
     * @param mesh
     */
    public void addDepthSurface(IndexedTriangleMesh mesh) {
        depthFillMeshes.add(mesh);
        depthFillMeshesDirty = true;
    }



    /**
     *
     * MASTER METHOD addPolyLine
     *
     * @param polyLine
     * @param color
     * @param width
     * @return
     */
    public ColorPolyLine addPolyLine(PolyLine polyLine, GlColor color, float width){
        ColorPolyLine l = new ColorPolyLine();
        l.positions = polyLine.positions;
        l.color = color;
        l.width = width;
        polyLinesDirty = true;
        colorPolyLines.add(l);
        return l;
    }

    /**
     *
     * @param positions
     * @param color
     * @param width
     * @return
     */
    public ColorPolyLine addPolyLine(Pos[] positions, GlColor color, float width){
        PolyLine l = new PolyLine();
        l.positions = positions;
        return addPolyLine(l,color,width);
    }

    /**
     *
     * @param polyLine
     * @return
     */
    public ColorPolyLine addPolyLine(PolyLine polyLine){
        return addPolyLine(polyLine, LINE_COLOR, LINE_WIDTH);
    }

    /**
     *
     * @param positions
     * @return
     */
    public ColorPolyLine addPolyLine(Pos[] positions){
        return addPolyLine(positions, LINE_COLOR, LINE_WIDTH);
    }

    /**
     *
     * MASTER METHOD addHiddenPolyLine
     *
     * @param polyLine
     * @param color
     * @param width
     * @return
     */
    public ColorPolyLine addHiddenPolyLine(PolyLine polyLine, GlColor color, float width){
        ColorPolyLine l = new ColorPolyLine();
        l.positions = polyLine.positions;
        l.color = color;
        l.width = width;
        hiddenPolyLinesDirty = true;
        hiddenColorPolyLines.add(l);
        return l;
    }

    /**
     *
     * @param positions
     * @param color
     * @param width
     * @return
     */
    public ColorPolyLine addHiddenPolyLine(Pos[] positions, GlColor color, float width){
        PolyLine l = new PolyLine();
        l.positions = positions;
        return addHiddenPolyLine(l,color,width);
    }

    public void addCalibrationMarker(Pos p) {
        calibrationMarkers.add(p);
        markerGroupsDirty = true;
    }

    public void clearCalibrationMarkers() {
        calibrationMarkers.clear();
        markerGroupsDirty = true;
    }

    public void updateGlLines(){
        if(linesDirty){
            glLines.setLines(colorLines.toArray(new ColorLine[colorLines.size()]));
            linesDirty = false;
        }
    }


    public void updateGlPolyLines(){
        if(polyLinesDirty){

            //glLines.setLines(colorLines.toArray(new ColorLine[colorLines.size()]));
            //linesDirty = false;
            glPolyLines.clear();
            for(ColorPolyLine l:colorPolyLines){
                GlPolyLine line = new GlPolyLine();
                line.setPositions(l.positions);
                line.setWidth(l.width);
                line.setColor(l.color);
                glPolyLines.add(line);
            }

            polyLinesDirty = false;

        }
    }

    public void updateGlHiddenPolyLines(){
        if(hiddenPolyLinesDirty){

            //glLines.setLines(colorLines.toArray(new ColorLine[colorLines.size()]));
            //linesDirty = false;
            glHiddenPolyLines.clear();
            for(ColorPolyLine l:hiddenColorPolyLines){
                GlPolyLine line = new GlPolyLine();
                line.setPositions(l.positions);
                line.setWidth(l.width);
                line.setColor(l.color);
                glHiddenPolyLines.add(line);
            }

            hiddenPolyLinesDirty = false;

        }
    }

    public void updateGlPositions(){
        if(positionsDirty){
            glPositions.clear();
            for(ColorPos p:colorPositions){
                GlPos pos = new GlPos();
                pos.setPos(p);
                pos.setColor(p.color);
                pos.setWidth(p.width);
                glPositions.add(pos);
            }

            positionsDirty = false;
        }
    }

    public void updateGlPosGroups(){
        if(posGroupsDirty){
            glPosGroups.clear();
            for(ColorPosGroup pg:colorPosGroups){
                GlPosGroup posGroup = new GlPosGroup();
                posGroup.setPositions(pg.positions);
                posGroup.setColor(pg.color);
                posGroup.setWidth(pg.width);
                glPosGroups.add(posGroup);
            }
            posGroupsDirty = false;
        }
    }

    private void updateDepthFillSurfaces() {
        if (depthFillMeshesDirty) {
            glDepthFillSurfaces.clear();
            for (IndexedTriangleMesh mesh : depthFillMeshes) {
                GlIndexedTriangleMesh glMesh = new GlIndexedTriangleMesh();
                glMesh.setData(mesh.positions, mesh.indexes);
                glDepthFillSurfaces.add(glMesh);
            }
            depthFillMeshesDirty = false;
        }
    }

    private void updateMarkers() {
        if (markerGroupsDirty) {
            glMarkerGroups.clear();

            GlMarkerGroup calibrationMarkerGroup =
                    new GlMarkerGroup(calibrationMarkerMesh);
            for (Pos p: calibrationMarkers) {
                float[] m = new float[] {
                        1, 0, 0, 0,
                        0, 1, 0, 0,
                        0, 0, 1, 0,
                        p.x, p.y, p.z, 1
                };
                calibrationMarkerGroup.modelMatrixList().add(m);
            }
            glMarkerGroups.add(calibrationMarkerGroup);

            markerGroupsDirty = false;
        }
    }
}
