package no.kartverket.bordergo;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

import no.kartverket.data.dtm.DTMGrid;
import no.kartverket.data.model.WfsResponse;
import no.kartverket.data.repository.DataRepository;
import no.kartverket.data.repository.WfsDataRepository;
import no.kartverket.geodesy.Geodesy;

/**
 * Created by hg on 11.05.2017.
 */



public class DataToDrawProvider {

    static final String dekfile_norkart = "sandvika_dek_geo.tmo";
    static final String dekfile_kartverket = "kartverket_dek_geo.tmo";


    public static String _wfs_user = "";
    public static String _wfs_pass = "";
    public static String _wfs_url = "";

    static final double _dek_square_size = 160.0;    // Create matrikkel boundaries for 160x160 meters
    private double _center_N = 0.0;
    private double _center_E = 0.0;
    private double _min_N = 0.0;
    private double _min_E = 0.0;
    private double _max_N = 0.0;
    private double _max_E = 0.0;
    private boolean _wfs_finished = false;
    private DataToDraw _data;
    private Object _lock = new Object();
    private DTMGrid _dtmGrid;

    // Testdata, brygge Sandvika
    static final double Brygge[][] = {
            { 59.89109589 ,   10.52315944 ,    1.399 },
            { 59.89110027 ,   10.52315321 ,    0.859 },
            { 59.89104336 ,   10.52297939 ,    1.009 },
            { 59.89106305 ,   10.52296259 ,    1.539 },
            { 59.89120144 ,   10.52285383 ,    1.389 },
            { 59.89123388 ,   10.52294539 ,    1.439 },
            { 59.89123342 ,   10.52294662 ,    1.499 },
            { 59.89117494 ,   10.52302509 ,    1.389 },
            { 59.89118993 ,   10.52307242 ,    1.359 },
            { 59.89110218 ,   10.52318958 ,    1.359 }
    };

    // Testdata, støttemur Sandvika
    static final double Mur[][] = {
            {   59.89156826 ,   10.52232996 ,    3.059 },
            {   59.89159342 ,   10.52242244 ,    3.279 },
            {   59.89161168 ,   10.52250513 ,    3.509 },
            {   59.89162555 ,   10.52256403 ,    3.539 },
            {   59.89162836 ,   10.52258472 ,    3.539 },
            {   59.89162993 ,   10.52260445 ,    3.509 },
            {   59.89162916 ,   10.52262443 ,    3.359 },
            {   59.89157596 ,   10.52265113 ,    3.239 },
            {   59.89143828 ,   10.52275224 ,    3.239 },
            {   59.89134846 ,   10.52282320 ,    3.169 },
            {   59.89127445 ,   10.52288541 ,    3.239 },
            {   59.89123388 ,   10.52294539 ,    3.279 },
            {   59.89124296 ,   10.52296832 ,    3.279 },
    };

    // Testdata, bygningslinje 1 Sandvika
    static final double Tak1[][] = {
            {    59.89171779  ,  10.52288961  ,  24.619 },
            {    59.89132956  ,  10.52328471  ,  24.469 },
            {    59.89140631  ,  10.52358348  ,  24.369 },
            {    59.89179463  ,  10.52318839  ,  24.569 },
            {    59.89171779  ,  10.52288961  ,  24.619 },
    };

    // Testdata, bygningslinje 2 Sandvika
    static final double Tak2[][] = {
            {    59.89176667 ,   10.52316012 ,   24.239 },
            {    59.89141053 ,   10.52352148 ,   24.239 },
            {    59.89135621 ,   10.52330988 ,   24.239 },
            {    59.89171235 ,   10.52294834 ,   24.239 },
            {    59.89176667 ,   10.52316012 ,   24.239 },
    };

    // Testdata, veglinje inngang, Kartverket
    static final double VeglinjeInngang[][] = {
            { 60.14407245547153   , 10.249038274901086 , 116.73 },
            { 60.144064613784586  , 10.249098487171691 , 116.73 },
            { 60.144059712800299  , 10.249142062739322 , 116.73 },
            { 60.144060383214523  , 10.249156675307578 , 116.73 },
            { 60.144063214881172  , 10.249170649489916 , 116.73 },
            { 60.144066995170164  , 10.249179257005189 , 116.73 },
            { 60.144075714468052  , 10.24918787175042  , 116.73 },
            { 60.144084147689533  , 10.249188731852779 , 116.73 },
            { 60.144089025790556  , 10.249185675269047 , 116.73 },
            { 60.144094772932064  , 10.249176168461881 , 116.73 },
            { 60.144097991253354  , 10.249168186540787 , 116.73 },
            { 60.144100418626991  , 10.249158373737341 , 116.73 },
            { 60.144102990093884  , 10.249142803567727 , 116.73 },
            { 60.144107967267217  , 10.249100671539921 , 116.73 },
            { 60.144111674076669  , 10.249059932032702 , 116.73 },
            { 60.144111977534152  , 10.249046797082421 , 116.73 },
            { 60.14410975556725   , 10.249034826983863 , 116.73 },
            { 60.144105628074016  , 10.249024945688817 , 116.73 },
            { 60.144099330839374  , 10.2490166029138   , 116.73 },
            { 60.144092815023981  , 10.249012393885593 , 116.73 },
            { 60.144087056208456  , 10.249013616214679 , 116.73 },
            { 60.144081898618552  , 10.24901774275501  , 116.73 },
            { 60.144077166110414  , 10.24902440664936  , 116.73 },
            { 60.144074776091422  , 10.249030258930297 , 116.73 },
            { 60.14407245547153   , 10.249038274901086 , 116.73 },
    };

    // Testdata, veglinje 1 parkering, Kartverket
    static final double VeglinjePark01[][] = {
            { 60.14401893286788  , 10.250665861430475 , 116.84 },
            { 60.144017363981803 , 10.25067984872317  , 116.84 },
            { 60.143998610331998 , 10.250839955126155 , 116.84 },
    };

    // Testdata, veglinje 2 parkering, Kartverket
    static final double VeglinjePark02[][] = {
            { 60.143982524378664 , 10.250698515574262 , 116.93 },
            { 60.143980198240357 , 10.250716616115106 , 116.93 },
            { 60.143973011051315 , 10.250783515938341 , 116.93 },
    };

    // Testdata, veglinje 3 parkering, Kartverket
    static final double VeglinjePark03[][] = {
            { 60.144051667309441 , 10.250375541606195 , 116.78 },
            { 60.144031768507119 , 10.250552353053232 , 116.78 },
    };

    // Testdata, veglinje 4 parkering, Kartverket
    static final double VeglinjePark04[][] = {
            { 60.143982524378664 , 10.250698515574262 , 116.93 },
            { 60.143980198240357 , 10.250716616115106 , 116.93 },
            { 60.143973011051315 , 10.250783515938341 , 116.93 },
    };

    // Testdata, veglinje 5 parkering, Kartverket
    static final double VeglinjePark05[][] = {
            { 60.144017027382979 , 10.250401599985011 , 116.85 },
            { 60.144020003659271 , 10.250390727603772 , 116.85 },
            { 60.144023301626632 , 10.250383829369223 , 116.85 },
            { 60.144034994016685 , 10.250372387423289 , 116.85 },
            { 60.144036887667774 , 10.250371558869169 , 116.85 },
            { 60.144048024154465 , 10.250371441365536 , 116.85 },
            { 60.144051667309441 , 10.250375541606195 , 116.85 },
    };

    // Testdata, veglinje 6 parkering, Kartverket
    static final double VeglinjePark06[][] = {
            { 60.143973011051315 , 10.250783515938341 , 116.96 },
            { 60.143973644211627 , 10.250792544370556 , 116.96 },
            { 60.143975347802794 , 10.250802333803575 , 116.96 },
            { 60.143978045654578 , 10.250811440643549 , 116.96 },
            { 60.143981753066406 , 10.250818244681801 , 116.96 },
            { 60.143986258201558 , 10.250826159542546 , 116.96 },
            { 60.143992116674724 , 10.250833405448006 , 116.96 },
            { 60.143998610331998 , 10.250839955126155 , 116.96 },
    };

    // Testdata, veglinje 7 parkering, Kartverket
    static final double VeglinjePark07[][] = {
            { 60.144031768507119 , 10.250552353053232 , 116.82 },
            { 60.144025195263509 , 10.250544719875558 , 116.82 },
            { 60.14401740433901  , 10.250532898445558 , 116.82 },
            { 60.144011588337165 , 10.250521152001639 , 116.82 },
            { 60.144007931894983 , 10.250508947287706 , 116.82 },
            { 60.144006590758146 , 10.250498811445622 , 116.82 },
            { 60.144005862710145 , 10.25049031967189  , 116.82 },
            { 60.144006277869551 , 10.250484392531611 , 116.82 },
    };

    // Testdata, veglinje 8 parkering, Kartverket
    static final double VeglinjePark08[][] = {
            { 60.144017027382979 , 10.250401599985011 , 116.85 },
            { 60.144008043267931 , 10.250468611815901 , 116.85 },
            { 60.144006277869551 , 10.250484392531611 , 116.85 },
    };

    // Testdata, veglinje 9 parkering, Kartverket
    static final double VeglinjePark09[][] = {
            { 60.143982524378664 , 10.250698515574262 , 116.93 },
            { 60.143985053532681 , 10.25068744616495  , 116.93 },
            { 60.143989825217439 , 10.25067664199284  , 116.93 },
            { 60.143994020892151 , 10.250669777879448 , 116.93 },
            { 60.143998189371196 , 10.250665794138081 , 116.93 },
            { 60.144002528890148 , 10.250662717329806 , 116.93 },
            { 60.144007935441884 , 10.25066076156418  , 116.93 },
            { 60.144013136959543 , 10.250661499332404 , 116.93 },
            { 60.14401893286788  , 10.250665861430475 , 116.93 },
    };

    // Testdata, veglinje 10 parkering, Kartverket
    static final double VeglinjePark10[][] = {
            { 60.144017027382979 , 10.250401599985011 , 116.85 },
            { 60.144008043267931 , 10.250468611815901 , 116.85 },
            { 60.144006277869551 , 10.250484392531611 , 116.85 },
    };





    private final double rho = 180/Math.PI;
    private final double RN = 6380000;


    public class DataPoint{
        public double _x[];     // lat, lon, H
        DataPoint(){ _x = new double[3]; }

    }


    public class DataObject{
        public ArrayList<DataPoint> _points;
        DataObject(){_points = new ArrayList<DataPoint>(); }

    }


    public class DataToDraw{
        public ArrayList<DataObject> _objects;  // polylines
        public ArrayList<DataPoint> _points;    // singlepoints

        DataToDraw(){
            _objects = new ArrayList<DataObject>();
            _points = new ArrayList<DataPoint>();
        }


        // Add polyline
        public void add( double Obj[][] )
        {
            DataObject Object = new DataObject();
            for ( int i=0 ; i<Obj.length ; i++ )
            {
                DataPoint P = new DataPoint();
                for ( int j=0 ; j<Obj[i].length ; j++ )
                {
                    P._x[j] = Obj[i][j];
                }
                Object._points.add(P);
            }
            _objects.add(Object);
        }

        // Add point
        public void addPoint(DataPoint P)
        {
            _points.add(P);
        }
    }


    // constructor
    DataToDrawProvider(DTMGrid grid)
    {
        _dtmGrid = grid;
    }
    DataToDrawProvider(DTMGrid grid, String user, String pass, String url)
    {
        _dtmGrid = grid;
        setWFSUser(user,pass,url);
    }

    public void setWFSUser(String user, String pass, String url){
        _wfs_user = user;
        _wfs_pass = pass;
        _wfs_url = url;
    }

    /**
     * Read dek data from tmod format.
     * Normally not in use. Standard is reading from wfs.
     * @param context
     * @param data Data to return.
     * @param dek_file
     */
    void loadDek(Context context, DataToDraw data, String dek_file)
    {
        try {

            InputStream is = context.getAssets().open(dek_file);
            int size = is.available();

            DataObject Object = new DataObject();
            Scanner s = new Scanner(is);
            s.useLocale(Locale.US);
            while (s.hasNext()){
                DataPoint P = new DataPoint();
                long dummy = s.nextLong();
                P._x[0] = s.nextDouble();
                P._x[1] = s.nextDouble();
                P._x[2] = s.nextDouble();
                int code = s.nextInt();
                if (code > 0){
                    Object._points.add(P);
                    if (code == 4){
                        DataObject Object_copy = new DataObject();
                        for ( int i=0 ; i<Object._points.size() ; i++ ) {
                            Object_copy._points.add(Object._points.get(i));
                        }
                        data._objects.add(Object_copy);
                        Object._points.clear();
                    }
                }
            }

            is.close();
        }
        catch(IOException ex){
            return;
        }
        catch(NullPointerException npe){
            return;
        }

    }


    /**
     * Calculate height from dtmgrid and lat/lon.
     * @param lat
     * @param lon
     * @return Height.
     */
    private double calcH(double lat, double lon)
    {
        double H = 0.0;

        if ( null != _dtmGrid )
        {
            H = _dtmGrid.getInterpolatedAltitude(lat, lon);
            if (H == Double.NEGATIVE_INFINITY)
                return 0;
        }

        return H;
    }

    private int outsideAreaCount = 0;

    /**
     * Calculate sampled positions with max distance 1 meter.
     * Height on each position is calculated from dtmgrid.
     * @param data Data to write to.
     * @param data_object Polyline to draw to.
     * @param lat1
     * @param lon1
     * @param lat2
     * @param lon2
     * @param isFirstVector
     * @param isLastVector
     */
    private void wfsCalcVector(DataToDraw data, DataObject data_object, double lat1, double lon1, double lat2, double lon2, boolean isFirstVector, boolean isLastVector)
    {
        Geodesy.Coords_xy NE1 = new Geodesy.Coords_xy(0,0), NE2 = new Geodesy.Coords_xy(0,0);
        Geodesy.latlon2utm( new Geodesy.Coords_xy(lat1,lon1), NE1);
        Geodesy.latlon2utm( new Geodesy.Coords_xy(lat2,lon2), NE2);
        double dN = NE2._x - NE1._x;
        double dE = NE2._y - NE1._y;
        double length = Math.sqrt( dN*dN + dE*dE);

        double max_dist = 1.0;
        int numP = (int)Math.floor(length / max_dist) + 2;
        double dist = length / (numP-1);
        double deltaN = dN / (numP-1);
        double deltaE = dE / (numP-1);

        for ( int i=0 ; i<numP ; i++ )          // for all sampled positions
        {
            if ( (!isFirstVector) && (i==0))
                continue;       // Polyline, this position is already sampled

            double N = (i==numP-1) ? (NE2._x) : (NE1._x + i*deltaN);
            double E = (i==numP-1) ? (NE2._y) : (NE1._y + i*deltaE);

            if ( N>_min_N && E>_min_E && N<_max_N && E<_max_E )     // if position is within area
            {
                Geodesy.Coords_xy latlon = new Geodesy.Coords_xy(0,0);
                Geodesy.utm2latlon( new Geodesy.Coords_xy(N,E), latlon );
                DataPoint P = new DataPoint();
                P._x[0] = latlon._x;
                P._x[1] = latlon._y;
                P._x[2] = calcH(latlon._x, latlon._y);
                data_object._points.add(P);

                if ( (i==0 && isFirstVector) || (i==numP-1 && isLastVector) ) {   // end position of vector or polyline -> save singlepoint
                    data.addPoint(P);
                }
            } else {
                outsideAreaCount++;
                Log.i("DataToDrawProvider", "Outside drawing area nr: " + outsideAreaCount );
            }
        }
    }


    /**
     * Boundary vector from 2 positions, calculate polyline
     * @param data    Data to write to.
     * @param lat1
     * @param lon1
     * @param lat2
     * @param lon2
     * @return
     */
    private boolean wfsAddVector(DataToDraw data, double lat1, double lon1, double lat2, double lon2)
    {
        DataObject data_object = new DataObject();

        wfsCalcVector(data, data_object, lat1, lon1, lat2, lon2, true, true);

        boolean ok = false;

        if ( data_object._points.size() >= 2 ) {
            data._objects.add(data_object);
            ok = true;
        }

        return ok;
    }

    private int polyFaults = 0;

    /**
     * Normally boundary arc. Many positions. Already sampled from wfs-server.
     * @param data          Data to write to.
     * @param listLatLon    Input positions from wfs.
     * @return
     */
    private boolean wfsAddPolyline(DataToDraw data, List<Double> listLatLon)
    {
        DataObject data_object = new DataObject();

        int numCoords = listLatLon.size();          // = 2*numPoints
        for ( int i=0 ; (i+3)<numCoords ; i+=2 )
        {
            wfsCalcVector(data, data_object, listLatLon.get(i), listLatLon.get(i+1), listLatLon.get(i+2), listLatLon.get(i+3), (i==0), (i==numCoords-4));
        }

        boolean ok=false;

        if ( data_object._points.size() >= 2 ) {
            data._objects.add(data_object);
            ok = true;
        } else {
            polyFaults++;
            Log.i("DataToDrawProvider", "Faulty polyline mumber: " + polyFaults);
        }

        return ok;
    }


    /**
     * Calculate wfs response to polylines and points to draw.
     * @param data        Data to write to.
     * @param response
     * @return            Number of polylines to draw
     */
    private int wfsAddResult(DataToDraw data, WfsResponse response)
    {
        int membersSize = response.getMembers().size();
        List<WfsResponse.Member> members = response.getMembers();
        WfsResponse.Member member;
        int boundrariesSize;
        List<WfsResponse.Curve> curves;
        int curvesSize;
        WfsResponse.Curve curve;
        List<WfsResponse.LineString> lineStrings;
        int lineStringsSize;

        for ( int i=0 ; i< membersSize ; i++ )
        {
            member = members.get(i);
            boundrariesSize = member.getBoundaries().size();
            for ( int j=0 ; j < boundrariesSize ; j++ )
            {
                curves =  member.getBoundaries().get(j).getCurves();
                curvesSize = curves.size();
                for ( int k=0 ; k < curvesSize ; k++ )
                {
                    curve =  curves.get(k);
                    lineStrings = curve.getLineStrings();
                    lineStringsSize = lineStrings.size();

                    for ( int l=0 ; l < lineStringsSize ; l++ )
                    {
                        List<Double> listLatLon = lineStrings.get(l).getParsedPosList();

                        if ( listLatLon.size() == 4 ) {       // vector
                            wfsAddVector(data, listLatLon.get(0), listLatLon.get(1), listLatLon.get(2), listLatLon.get(3));
                        }
                        else if ( listLatLon.size() >= 6 ) {  // polyline (arc)
                            wfsAddPolyline(data, listLatLon);
                        }
                    }
                }
            }
        }

        return data._objects.size();
    }



    /**
     * Get dek data from wfs
     * @param context
     * @param lat
     * @param lon
     * @return  Number of polylines to draw
     */
    private int getWFSdata(Context context, double lat, double lon){
        double coslat = Math.cos(lat/rho);
        double delta_lat = rho*(0.5*_dek_square_size/RN);
        double delta_lon = delta_lat / coslat;

        double lat_min = lat - delta_lat;
        double lon_min = lon - delta_lon;
        double lat_max = lat + delta_lat;
        double lon_max = lon + delta_lon;
        String bbox_string = String.format("%.6f,%.6f,%.6f,%.6f", lat_min, lon_min, lat_max, lon_max);
        //String bbox_string = "59.891187,10.522436,59.892085,10.524226";

        if ( (_wfs_user == null) || (_wfs_pass == null) || (_wfs_url == null) )
        {
            return 0;       // Have not user and password for matrikkel wfs
        }

        DataRepository repo = DataRepository.newInstance(_wfs_user, _wfs_pass, _wfs_url);

        boolean finished = false;

        repo.getDataAsync(bbox_string, new WfsDataRepository.WfsResponseCallback() {
            @Override
            public void onSuccess(@Nullable WfsResponse response) {
                //Log.d(DataToDrawProvider.this.getClass().getSimpleName(), "Response: " + response.getMembers().size());
                wfsAddResult(_data, response);
                _wfs_finished = true;
            }

            @Override
            public void onError(Throwable error, int code) {
                Log.d(DataToDrawProvider.this.getClass().getSimpleName(), "Error: " + error.getLocalizedMessage());
                _wfs_finished = true;
            }
        });

        int numWait=0;
        synchronized(_lock) {
            while ( ! _wfs_finished )
            {
                try {
                    numWait++;
                    _lock.wait(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        return _data._objects.size();
    }


    /**
     * Get dek data to draw. Normally from wfs.
     * @param context
     * @param lat
     * @param lon
     * @return  Data to draw.
     */
    public DataToDraw getData(Context context, double lat, double lon) {

        DataToDraw data = new DataToDraw();

        // Calc extents for area of boundaries
        Geodesy.Coords_xy center_NE = new Geodesy.Coords_xy(0,0);
        Geodesy.latlon2utm( new Geodesy.Coords_xy(lat,lon), center_NE);
        _center_N = center_NE._x;
        _center_E = center_NE._y;
        double delta = 0.5*_dek_square_size;
        _min_N = _center_N - delta;
        _min_E = _center_E - delta;
        _max_N = _center_N + delta;
        _max_E = _center_E + delta;

        int param = 0;  // 0: Matrikkel wfs, 1: Testdata Sandvika, 2: Testdata Kartverket, 3=dek+testdata Sandvika or Kartverket

        // wfs
        if (param == 0) {
            _data = new DataToDraw();
            getWFSdata(context, lat, lon);
            return _data;
        }

        // Testdata Sandvika and Kartverket
        else if (param == 1) {
            data.add(Brygge);
            data.add(Mur);
            data.add(Tak1);
            data.add(Tak2);

            data.add(VeglinjeInngang);
            data.add(VeglinjePark01);
            data.add(VeglinjePark02);
            data.add(VeglinjePark03);
            data.add(VeglinjePark04);
            data.add(VeglinjePark05);
            data.add(VeglinjePark06);
            data.add(VeglinjePark07);
            data.add(VeglinjePark08);
            data.add(VeglinjePark09);
            data.add(VeglinjePark10);
        }

        // Not in use
        else if (param == 2) {
        }

        // dek and testdata, Norkart or Kartverket depending on position
        else if (param == 3) {
            if ( Geodesy.IsPositionCloseNorkart(lat, lon) ) {
                loadDek(context, data, dekfile_norkart);

                data.add(Brygge);   // testdata
                data.add(Mur);
            }

            else if ( Geodesy.IsPositionCloseKartverket(lat, lon) ) {
                loadDek(context, data, dekfile_kartverket);

                data.add(VeglinjeInngang);   // testdata
                data.add(VeglinjePark01);
                data.add(VeglinjePark02);
                data.add(VeglinjePark03);
                data.add(VeglinjePark04);
                data.add(VeglinjePark05);
                data.add(VeglinjePark06);
                data.add(VeglinjePark07);
                data.add(VeglinjePark08);
                data.add(VeglinjePark09);
                data.add(VeglinjePark10);
            }
        }


        return data;
    }


}
