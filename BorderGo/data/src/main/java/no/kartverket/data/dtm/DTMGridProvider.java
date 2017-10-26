package no.kartverket.data.dtm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.FloatBuffer;
import java.util.StringTokenizer;
//import java.util.stream.Collectors;

import no.kartverket.data.utils.DTMRequest;
import no.kartverket.data.utils.WmsRequest;
import no.kartverket.geodesy.Geodesy;


/**
 *
 * <p>
 * Created by hg on 30.05.2017.
 */

public class DTMGridProvider {
    static final String TAG = DTMGridProvider.class.getSimpleName();

    static final String gridfile_norkart = "Sandvika_23.XYZ";
    static final String gridfile_kartverket = "Kartverket_23.XYZ";


    static private DTMGrid read_grid(Context context, String grid_file, double lat, double lon) throws IOException {

        double prev_N = -99.e99;


        StringTokenizer token1 = null;
        {
            InputStream is = context.getAssets().open(grid_file);
            int size = is.available();

            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            String str = new String(buffer);

            token1 = new StringTokenizer(str, "\n");
        }
        int num_points = 0, rows = 0, cols = 0;
        double ul_N = 0, ul_E = 0, space = 0;
        FloatBuffer grid_buf = FloatBuffer.allocate(1024);
        grid_buf.clear();

        while (token1.hasMoreTokens()) {
            int num_d = 0;
            double[] xyz = {0, 0, 0};
            String line = token1.nextToken();
            StringTokenizer token2 = new StringTokenizer(line, " \r\n");
            while (token2.hasMoreTokens() && num_d < 3) {
                String strd = token2.nextToken();
                xyz[num_d] = Double.parseDouble(strd);
                num_d++;
            }

            if (num_d == 3) {
                // If necessary, increase buffer size
                if (grid_buf.position() >= grid_buf.capacity()) {
                    FloatBuffer tmp = FloatBuffer.allocate(grid_buf.capacity() * 2);
                    tmp.clear();
                    grid_buf.flip();
                    tmp.put(grid_buf);
                    grid_buf = tmp;
                }
                // Add point
                num_points++;
                grid_buf.put((float) xyz[2]);
            }

            // First point, save upper left
            if (num_points == 1) {
                ul_N = xyz[0];
                ul_E = xyz[1];
            }

            // Second point, save spacing in grid
            if (num_points == 2) {
                space = xyz[1] - ul_E;
            }

            // New row, find out grid size
            if (xyz[0] != prev_N) {
                rows++;
                if (cols == 0 && num_points > 1)
                    cols = num_points - 1;
            }

            prev_N = xyz[0];
        }

        grid_buf.flip();

        DTMGrid grid = new DTMGrid(rows, cols);
        grid.arrH.put(grid_buf);
        grid.arrH.flip();
        grid.setCoordinateParams(ul_N, ul_E, space);

        return grid;

    }

    static private DTMGrid readAsciiGrid(Reader inputReader) throws IOException {

        BufferedReader reader = new BufferedReader(inputReader);

        int ncols = -1;
        int nrows = -1;
        double xllcorner = Double.NEGATIVE_INFINITY;
        double yllcorner = Double.NEGATIVE_INFINITY;
        double cellsize = -1;
        double nodata_value = Double.NEGATIVE_INFINITY;


        String line = reader.readLine();

        while (line != null) {
            String[] linesplit = line.trim().split("\\s+");

            if (linesplit[0].equals("ncols"))
                ncols = Integer.parseInt(linesplit[1]);
            else if (linesplit[0].equals("nrows"))
                nrows = Integer.parseInt(linesplit[1]);
            else if (linesplit[0].equals("cellsize"))
                cellsize = Double.parseDouble(linesplit[1]);
            else if (linesplit[0].equals("xllcorner"))
                xllcorner = Double.parseDouble(linesplit[1]);
            else if (linesplit[0].equals("yllcorner"))
                yllcorner = Double.parseDouble(linesplit[1]);
            else if (linesplit[0].equals("nodata_value"))
                nodata_value = Double.parseDouble(linesplit[1]);
            else
                break;

            line = reader.readLine();
        }

        double minx = xllcorner;
        double maxy = yllcorner + nrows * cellsize;

        DTMGrid grid = new DTMGrid(nrows, ncols);
        while (line != null) {
            String[] linesplit = line.trim().split("\\s+");
            for (String s : linesplit) {
                float h = Float.parseFloat(s);
                grid.arrH.put(h);
            }

            line = reader.readLine();
        }

        grid.arrH.rewind();
        grid.setCoordinateParams(maxy, minx, cellsize);

        return grid;
    }

    static private DTMGrid wms_grid(DTMRequest request, double lat, double lon) throws IOException {
        int dim = 180;
        float wms_max_h = 25000, wms_min_h = -25000;

        Geodesy.Coords_xy lat_lon = new Geodesy.Coords_xy(lat, lon);
        Geodesy.Coords_xy xy = new Geodesy.Coords_xy();
        Geodesy.latlon2utm(lat_lon, xy);


        double minx = xy._y - dim / 2.;
        double miny = xy._x - dim / 2.;
        double maxx = xy._y + dim / 2.;
        double maxy = xy._x + dim / 2.;

        java.net.URL url = request.createRequest(minx, miny, maxx, maxy, dim, dim);
        HttpURLConnection connection = (HttpURLConnection) url
                .openConnection();
        connection.setDoInput(true);
        connection.connect();
        String content_type = connection.getContentType();

        if ("image/png".equals(content_type)) {
            InputStream input = connection.getInputStream();
            Bitmap bm = BitmapFactory.decodeStream(input);

            DTMGrid grid = new DTMGrid(bm.getHeight(), bm.getWidth());

            for (int j = 0; j < bm.getHeight(); ++j) {
                for (int i = 0; i < bm.getWidth(); ++i) {
                    int pixel = bm.getPixel(i, j);
                    int r = Color.red(pixel);
                    int g = Color.green(pixel);
                    int b = Color.blue(pixel);

                    float h = (wms_max_h - wms_min_h) * ((r << 16) | (g << 8) | b) / 16777215.f + wms_min_h;
                    grid.arrH.put(h);
                }
            }
            grid.arrH.rewind();
            grid.setCoordinateParams(maxy, minx, 1);

            return grid;
        }
        else if ("application/arcgrid".equals(content_type) || "image/x-aaigrid".equals(content_type)) {
            InputStream input = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            return readAsciiGrid(reader);
        }
        else if (content_type.startsWith("multipart/related")) {

            String boundary = "";
            int boundary_ix = content_type.indexOf("boundary=");
            if (boundary_ix >= 0) {
                boundary_ix += "boundary=".length();
                boundary = "--" + content_type.substring(boundary_ix);
            }



            InputStream inputStream = connection.getInputStream();
            String newLine = System.getProperty("line.separator");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder result = new StringBuilder();
            String line = "";

            while (line != null) {
                String sub_content_type = "";
                while ((line = reader.readLine()) != null) {
                    if (line.equals(""))
                        break;

                    if (line.startsWith("Content-Type:")) {
                        sub_content_type = line.substring("Content-Type:".length()).trim();
                    }
                }

                if (sub_content_type.equals("application/arcgrid") ||
                    sub_content_type.equals("image/x-aaigrid")) {

                    boolean flag = false;
                    while ((line = reader.readLine()) != null) {
                        if (line.equals(boundary))
                            break;
                        result.append(flag ? newLine : "").append(line);
                        flag = true;
                    }

                    BufferedReader gridReader = new BufferedReader(new StringReader(result.toString()));

                    return readAsciiGrid(gridReader);
                }
            }

            return null;
        }
        else {
            InputStream inputStream = connection.getInputStream();
            String newLine = System.getProperty("line.separator");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder result = new StringBuilder();
            String line; boolean flag = false;
            while ((line = reader.readLine()) != null) {
                result.append(flag? newLine: "").append(line);
                flag = true;
            }

            throw new Error(result.toString());
        }


    }

    static public DTMGrid get_grid(Context context, double lat, double lon, DTMRequest request) throws IOException {
        if (request != null) {
            return wms_grid(request, lat, lon);
        }

        if ( Geodesy.IsPositionCloseNorkart(lat, lon) )
            return read_grid(context, gridfile_norkart, lat, lon);

        else if ( Geodesy.IsPositionCloseKartverket(lat, lon) )
            return read_grid(context, gridfile_kartverket, lat, lon);

        else
            throw new IllegalArgumentException("Unable to create a DTMGrid from data");
    }
}
