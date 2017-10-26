package no.kartverket.data.api;

import no.kartverket.data.model.WfsResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Query;

/**
 * Created by janvin on 28.09.2017.
 */



public interface GateKeeperApi {
    @GET("ws/gatekeeper.py")
    Call<String> getSessionKey(@Query("key") String token);
}
