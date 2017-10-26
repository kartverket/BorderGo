package no.kartverket.data.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Created by runaas on 29.09.2017.
 */

public class WcsRequest extends DTMRequest {
    private String base_url_str;
    private String srs_str;
    boolean old_request_type;

    /*
    http://localhost/cgi-bin/wcstest?
    SERVICE=WCS
    &VERSION=2.0.1
    &REQUEST=GetCoverage
    &COVERAGEID=dem50mutm33
    &FORMAT=image/x-aaigrid
    &SUBSET=x(201000,209000)
    &SUBSET=y(6701000,6709000)
    &SUBSETTINGCRS=EPSG:32633


    http://wcs.geonorge.no/skwms1/wcs.dtm?
    SERVICE=WCS
    &VERSION=2.0.1
    &REQUEST=GetCoverage
    &COVERAGEID=all_50m
    &FORMAT=image/x-aaigrid
    &SUBSET=x,EPSG:32633(201000,209000)
    &SUBSET=y,EPSG:32633(6701000,6709000)
     */

    public WcsRequest(String service_url_str) {
        base_url_str = service_url_str;
        srs_str = null;
        old_request_type = false;
    }

    public WcsRequest(String service_url_str, String srs_str, String format_str, String coverage, boolean old_request_type) {
        this.srs_str = srs_str;
        this.old_request_type = old_request_type;
        base_url_str = service_url_str +
                "?SERVICE=WCS&VERSION=2.0.1&REQUEST=GetCoverage&COVERAGEID=" + coverage +
                "&FORMAT=" + format_str;
        if (!old_request_type)
            base_url_str += "&SUBSETTINGCRS=" + srs_str;
    }

    public URL createRequest(double minx, double miny, double maxx, double maxy, int dum_width, int dum_height) throws MalformedURLException {

        String url_str = base_url_str;
        if (old_request_type)
            url_str += String.format("&SUBSET=x,%s(%f,%f)&SUBSET=y,%s(%f,%f)", srs_str, minx, maxx, srs_str, miny, maxy);
        else
            url_str += String.format("&SUBSET=x(%f,%f)&SUBSET=y(%f,%f)", minx, maxx, miny, maxy);

        return new URL(url_str);
    }
}
