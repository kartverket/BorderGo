package no.kartverket.geodesy;

import android.content.Context;

/**
 * Created by hg on 30.05.2017.
 */

public class Geodesy {

    static public class Coords_xy{
        public Coords_xy(){ _x=0; _y=0; }
        public Coords_xy( double x, double y ){ _x=x; _y=y; }

        public double _x;
        public double _y;
    }


    static final double lat_Kartverket = 60.1440;
    static final double lon_Kartverket = 10.2490;

    static final double lat_Norkart = 59.8914;
    static final double lon_Norkart = 10.5230;

    static final double GeoidH_Norkart = 39.62;
    static final double GeoidH_Kartverket = 40.08;


    static final double WGS84_a = 6378137.0;
    static final double WGS84_f = (1./298.25722);
    static final int UTM_zone = 33;

    static final double UTM_f = 0.9996;
    static final double UTM_N0 = 0.0;
    static final double UTM_E0 = 500000.0;

    static Href_grid _hrefGrid;


    public static boolean IsPositionCloseNorkart(double lat_deg, double lon_deg){
        boolean is_close = ( Math.abs(lat_deg-lat_Norkart)<0.01 && Math.abs(lon_deg-lon_Norkart)<0.02 );
        return is_close;
    }


    public static boolean IsPositionCloseKartverket(double lat_deg, double lon_deg){
        boolean is_close = ( Math.abs(lat_deg-lat_Kartverket)<0.01 && Math.abs(lon_deg-lon_Kartverket)<0.02 );
        return is_close;
    }


    /**
     * From lat,lon (deg) to UTM (north, east)
     * @param latlon_deg
     * @param NE
     */
    public static void latlon2utm( Coords_xy latlon_deg, Coords_xy NE){
        double a = WGS84_a;
        double f = WGS84_f;
        double ee = f*(2.-f);
        double e = Math.sqrt(ee);

        double lat = Math.toRadians(latlon_deg._x);

        double l0 = (UTM_zone - 30.5)*6.0;
        double dl = Math.toRadians( latlon_deg._y - l0 );

        double	sf	= Math.sin( lat );
        double	esf = e*sf;
        double	p = (1.-esf)/(1.+esf);

        double	dHS	= Math.tan( lat/2. + Math.PI/4 ) * Math.pow( p , e/2. );
        double	C = 2.0 * ( Math.atan(dHS) - Math.PI/4 );	// Conformal latitude


        double	cl = Math.cos(dl);
        double	sl = Math.sin(dl);

        double	cc = Math.cos(C);
        double	tc = Math.tan(C);

        // Sphere to transverse mercator (Gauss-Schreiber)
        double	sp = cc*sl;					// sin(p), p=

        double u = Math.atan( tc / cl );
        double v  = 0.5 * Math.log( (1.+sp)/(1.-sp) );		// = atanh( sp );


        double	ff = f*f;
        double	fff = f*ff;
        double b0 = a * ( 1. - f/2. + ff/16. + fff/32. );	// 6367449.1458..
        double b1 = a * ( f/4. - ff/6. - 11./384.*fff );
        double b2 = a * ( 13./192.*ff - 79./1920.*fff );
        double b3 = a * ( 61./1920. * fff );

        // Gauss Kruger
        double x = b0*u + b1*Math.sin(2*u)*Math.cosh(2*v) + b2*Math.sin(4*u)*Math.cosh(4*v) + b3*Math.sin(6*u)*Math.cosh(6*v);
        double y = b0*v + b1*Math.cos(2*u)*Math.sinh(2*v) + b2*Math.cos(4*u)*Math.sinh(4*v) + b3*Math.cos(6*u)*Math.sinh(6*v);

        // UTM
        NE._x = UTM_f*x + UTM_N0;
        NE._y = UTM_f*y + UTM_E0;
    }


    /**
     * From UTM (north, east) to lat,lon (deg)
     * @param NE
     * @param latlon_deg
     */
    public static void utm2latlon( Coords_xy NE, Coords_xy latlon_deg) {
        double a = WGS84_a;
        double f = WGS84_f;
        double ee = f*(2.-f);
        double e = Math.sqrt(ee);

        double l0 = (UTM_zone - 30.5)*6.0;

        double N = NE._x;
        double E = NE._y;

        // Gauss Kruger
        double x = (N - UTM_N0) / UTM_f;
        double y = (E - UTM_E0) / UTM_f;

        double	ff = f*f;
        double	fff = f*ff;

        double b0 = a * ( 1.0 - f/2.0 + ff/16.0 + fff/32.0 );
        double c1 = f/4.0 - ff/24.0 - fff*43.0/768.0;
        double c2 = ff/192.0 + fff*13.0/960.0;
        double c3 = fff*17.0/3840.0;

        double x2 = 2.0*x;
        double y2 = 2.0*y;
        double x4 = 4.0*x;
        double y4 = 4.0*y;
        double x6 = 6.0*x;
        double y6 = 6.0*y;

        double u = x/b0 - c1*Math.sin(x2/b0)*Math.cosh(y2/b0) - c2*Math.sin(x4/b0)*Math.cosh(y4/b0) - c3*Math.sin(x6/b0)*Math.cosh(y6/b0);
        double v = y/b0 - c1*Math.cos(x2/b0)*Math.sinh(y2/b0) - c2*Math.cos(x4/b0)*Math.sinh(y4/b0) - c3*Math.cos(x6/b0)*Math.sinh(y6/b0);

        double p = (Math.atan(Math.exp(v)) - Math.PI/4.0)*2.0;

        double	cu = Math.cos(u);
        double	su = Math.sin(u);
        double	tp = Math.tan(p);

        double dl = Math.atan2(tp,cu);	// Longitude relative to the central meridian

        double w = Math.atan2( su , cu*Math.cos(dl) + tp*Math.sin(dl) );

        double lat = w + (f + ff/3.0 - fff/6.0) * Math.sin(2.0*w) + (ff*7.0/12.0 + fff*23.0/60.0) * Math.sin(4.0*w) + fff* 7.0/15.0 * Math.sin(6.0*w);

        latlon_deg._x = Math.toDegrees(lat);
        latlon_deg._y = Math.toDegrees(dl) + l0;
    }


    /**
     * Calculate meridian convergence from lat/lon
     * @param lat_deg
     * @param lon_deg
     * @return Meridian covergence in radians
     */
    public static double meridian_convergence( double lat_deg, double lon_deg ){

        double f = WGS84_f;
        double ee = f*(2.-f);

        double l0 = (UTM_zone - 30.5)*6.0;

        double lat = Math.toRadians(lat_deg);

        double	dl		= Math.toRadians(lon_deg - l0);
        double	dl2		= dl*dl;
        double	dl3		= dl2*dl;
        double	dl5	    = dl3*dl2;
        double	sf		= Math.sin( lat );
        double	cf		= Math.cos( lat );
        double	cf2		= cf*cf;
        double	cf4		= cf2*cf2;
        double	Eps2    = ee*cf2 / ( 1.0-ee );
        double	Eps4	= Eps2*Eps2;
        double	tf		= Math.tan( lat );
        double	tf2		= tf*tf;

        double	d1 = dl*sf;
        double	d3 = dl3/3.0*sf*cf2*( 1.0 + 3.0*Eps2 + 2.0*Eps4 );
        double	d5 = dl5/15.0*sf*cf4*( 2.0 - tf2 );

        double	gamma	= d1 + d3 + d5;

        return gamma;
    }


    /**
     * Calculate scale from spheroid to UTM
     * @param lat_deg
     * @param lon_deg
     */
    public static double scale( double lat_deg, double lon_deg ) {

        double f = WGS84_f;
        double ee = f*(2.-f);

        double l0 = (UTM_zone - 30.5)*6.0;

        double lat = Math.toRadians(lat_deg);

        double	dl		= Math.toRadians(lon_deg - l0);
        double	dl2		= dl*dl;
        double	dl4		= dl2*dl2;
        double	cf		= Math.cos( lat );
        double	cf2		= cf*cf;
        double	cf4		= cf2*cf2;
        double	Eps2    = ee*cf2 / ( 1.0-ee );
        double	tf		= Math.tan( lat );
        double	tf2		= tf*tf;

        double	k2 = 0.5*dl2*cf2*( 1.0 + Eps2 );
        double	k4 = 1./24.0*dl4*cf4*( 5.0 - 4.0*tf2 );
        double	dScale	= UTM_f*( 1.0 + k2 + k4 );

        return dScale;
    }


    public static double GetGeoidHeight_old( double lat_deg, double lon_deg ) {
        double N = 40;

        if ( IsPositionCloseNorkart(lat_deg, lon_deg) )
            N = GeoidH_Norkart;

        else if ( IsPositionCloseKartverket(lat_deg, lon_deg) )
            N = GeoidH_Kartverket;

        return N;
    }

    public static void initHREF(Context context)
    {
        _hrefGrid = new Href_grid();
        _hrefGrid.ReadHrefFile(context.getAssets());
    }

    /**
     * Read geoid height from href file.
     * @param lat_deg
     * @param lon_deg
     * @return Geoid height
     */
    public static double GetGeoidHeight( double lat_deg, double lon_deg ) {
        double N = 40;

        if (null != _hrefGrid)
        {
            double dVal = _hrefGrid.GetVal(lat_deg, lon_deg);
            if ( Math.abs(dVal) < 200 )
                N = dVal;
        }

        return N;
    }


    public static double meridionalRadius(double lat_deg) {
        final double a = WGS84_a;
        final double b = WGS84_a * (1 - WGS84_f);
        final double lat = Math.toRadians(lat_deg);
        final double sin_lat = Math.sin(lat);
        final double cos_lat = Math.cos(lat);

        return a*a*b*b / Math.pow(a*a*cos_lat*cos_lat + b*b*sin_lat*sin_lat , 3.0/2.0);
    }

    public static double normalRadius(double lat_deg) {
        final double a = WGS84_a;
        final double b = WGS84_a * (1 - WGS84_f);
        final double lat = Math.toRadians(lat_deg);
        final double sin_lat = Math.sin(lat);
        final double cos_lat = Math.cos(lat);

        return a*a / Math.sqrt(a*a*cos_lat*cos_lat + b*b*sin_lat*sin_lat);
    }
}
