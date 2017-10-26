package no.kartverket.geometry;

/**
 * Created by janvin on 01.06.2017.
 */

public class GeomUtils {

    public static double bilinear(double h0, double hx, double hy, double hxy, double x, double y){
        return h0*(1-x)*(1-y) + hx*x*(1-y) + hy*y*(1-x) + hxy*x*y;
    }

    public static float bilinear(float h0, float hx, float hy, float hxy, float x, float y){
        return h0*(1-x)*(1-y) + hx*x*(1-y) + hy*y*(1-x) + hxy*x*y;
    }
}
