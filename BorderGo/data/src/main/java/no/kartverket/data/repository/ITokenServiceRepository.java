package no.kartverket.data.repository;

import android.support.annotation.Nullable;

import java.io.IOException;

/**
 * Created by janvin on 29.09.2017.
 */

public interface ITokenServiceRepository {

    /**
     * Get data, performed async and posted back on the UI thread.
     * @param token
     * @param callback
     * @throws IOException
     */
    void getSessionKeyAsync(String token, SessionKeyCallback callback) throws IOException;

    interface SessionKeyCallback {
        void onSuccess(@Nullable String response);
        void onError(Throwable error, int code);
    }
}
