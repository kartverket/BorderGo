package no.kartverket.data.utils;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by runaas on 29.09.2017.
 */

public abstract class DTMRequest {
    public abstract URL createRequest(double minx, double miny, double maxx, double maxy, int width, int height) throws MalformedURLException;
}
