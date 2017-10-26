package no.kartverket.data.repository;

import android.support.annotation.Nullable;

import java.io.IOException;

import no.kartverket.data.model.WfsResponse;

/**
 * Created by Kristian on 16.05.2017.
 */

public interface WfsDataRepository {

    /**
     * Get data, performed async and posted back on the UI thread.
     * @param boundingBoxString
     * @param callback
     * @throws IOException
     */
    void getDataAsync(String boundingBoxString, WfsResponseCallback callback) throws IOException;

    interface WfsResponseCallback {
        void onSuccess(@Nullable WfsResponse response);
        void onError(Throwable error, int code);
    }

}
