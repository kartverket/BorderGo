package no.kartverket.data.repository;

import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import no.kartverket.data.api.GateKeeperApi;
import no.kartverket.data.api.WfsApi;
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
 * Created by janvin on 29.09.2017.
 */

public class TokenServiceRepository implements ITokenServiceRepository {

    GateKeeperApi api;

    /**
     * Create a new instance of this repository with default settings.
     * @return the newly crated repository
     */
    //TODO: injectable retrofit (for interceptors etc)
    public static TokenServiceRepository newInstance(final String tokenServiceUrl) {

        /*OkHttpClient client = new OkHttpClient.Builder()
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
*/


        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(tokenServiceUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();


        return new TokenServiceRepository(retrofit.create(GateKeeperApi.class));
    }


    TokenServiceRepository(GateKeeperApi api) {
        this.api = api;
    }

    @Override
    public void getSessionKeyAsync(String token, final SessionKeyCallback callback) throws IOException {
        if (callback == null) { return; }

        api.getSessionKey(token).enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                String orthoSessionKey;

                if (response.isSuccessful()) {
                    orthoSessionKey = response.body();
                } else {
                    orthoSessionKey = null;
                }
                callback.onSuccess(orthoSessionKey);

            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {

                callback.onError(t,500);

            }
        });
    }
}
