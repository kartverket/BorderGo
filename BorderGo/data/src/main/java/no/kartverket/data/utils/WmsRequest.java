package no.kartverket.data.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Created by runaas on 27.06.2017.
 */

public final class WmsRequest extends DTMRequest {
    private String base_url_str;

    public WmsRequest(String service_url_str) {
        base_url_str = service_url_str;
    }

    public WmsRequest(String service_url_str, String srs_str, String img_format_str, List<String> layers, List<String> styles) {
        base_url_str = service_url_str + "?REQUEST=GetMap&SRS=" + srs_str + "&FORMAT=" + img_format_str + "&LAYERS=";
        for (int i = 0; i < layers.size(); ++i) {
            if (i > 0)
                base_url_str += ",";
            base_url_str += layers.get(i);
        }
        if (styles != null && styles.size() == layers.size()) {
            base_url_str += "&STYLES=";
            for (int i = 0; i < styles.size(); ++i) {
                if (i > 0)
                    base_url_str += ",";
                base_url_str += styles.get(i);
            }
        }
    }
    public WmsRequest(String service_url_str, String srs_str, String img_format_str, String layers) {
        this(service_url_str, srs_str, img_format_str, layers, null);
    }

    public WmsRequest(String service_url_str, String srs_str, String img_format_str, String layers, String styles) {
        base_url_str = service_url_str + "?REQUEST=GetMap&SRS=" + srs_str + "&FORMAT=" + img_format_str + "&LAYERS=" + layers;

        if (styles != null) {
            base_url_str += "&STYLES=" + styles;
        }
    }

    public URL createRequest(double minx, double miny, double maxx, double maxy, int width, int height) throws MalformedURLException {

        String url_str = base_url_str +
                String.format("&WIDTH=%d&HEIGHT=%d&BBOX=%f,%f,%f,%f", width, height, minx, miny, maxx, maxy);

        return new URL(url_str);
    }
}
