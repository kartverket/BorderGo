package no.kartverket.data.api;

import no.kartverket.data.model.WfsResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Query;

/**
 * Representation of API
 *
 * Created by Kristian on 15.05.2017.
 */
public interface WfsApi {
    //String BASE_URL = "https://www.test.matrikkel.no/";

    @GET("geoservergeo/wfs/MATRIKKEL?VERSION=1.1.0&SERVICE=WFS&REQUEST=GetFeature&typename=TEIGGRENSEWFS")
    Call<WfsResponse> getCoordinates(@Query("BBOX") String boundingBoxString);

}
