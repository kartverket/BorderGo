package no.kartverket.data.repository;

import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

//import no.kartverket.data.BuildConfig;
import no.kartverket.data.api.GateKeeperApi;
import no.kartverket.data.api.WfsApi;
import no.kartverket.data.model.WfsResponse;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.simplexml.SimpleXmlConverterFactory;



/**
 * Created by Kristian on 16.05.2017.
 */

public class DataRepository implements WfsDataRepository {

    WfsApi api;

    /**
     * Create a new instance of this repository with default settings.
     * @return the newly crated repository
     */
    //TODO: injectable retrofit (for interceptors etc)
    public static DataRepository newInstance(final String wfs_user, final String wfs_pass, final String wfs_url) {

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public okhttp3.Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        Log.d("HTTP", "Url: " + request.url().toString());

                        Request newRequest = request;
                        if (request.url().toString().contains(wfs_url)) {
                            String userpassword = wfs_user + ":" + wfs_pass;
                            String encodedAuthorization = Base64.encodeToString(userpassword.getBytes(StandardCharsets.ISO_8859_1), Base64.URL_SAFE|Base64.NO_WRAP);

                            newRequest = request.newBuilder()
                                    .addHeader("Authorization", "Basic " + encodedAuthorization)
                                    .build();

                        }
                        return chain.proceed(newRequest);
                    }
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(SimpleXmlConverterFactory.create())
                .baseUrl(wfs_url)
                .client(client)
                .build();

        return new DataRepository(retrofit.create(WfsApi.class));
    }

    DataRepository(WfsApi wfsApi) {
        this.api = wfsApi;

    }


    @Override
    public void getDataAsync(String boundingBoxString, final WfsResponseCallback callback) {
        if (callback == null) { return; }
        api.getCoordinates(boundingBoxString).enqueue(new Callback<WfsResponse>() {
            @Override
            public void onResponse(Call<WfsResponse> call, Response<WfsResponse> response) {
                if (callback == null) { return; }

                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(new Throwable("Couldn't load data"), response.code());
                }
            }

            @Override
            public void onFailure(Call<WfsResponse> call, Throwable t) {
                if (callback == null) { return; }
                callback.onError(t, 500);
            }
        });
    }

}
