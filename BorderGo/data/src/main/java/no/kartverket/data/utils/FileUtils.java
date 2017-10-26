package no.kartverket.data.utils;

import android.content.Context;
import android.content.res.Resources;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Kristian on 16.05.2017.
 */

public class FileUtils {

    /**
     * Read a file from the <code>assets</code> folder as a <code>String</code>.
     * @param context
     * @param filename
     * @return file content as a string
     */
    public static String readFileFromAssets(Context context, String filename) {
        String json = null;
        try {
            InputStream is = context.getAssets().open(filename);

            int size = is.available();

            byte[] buffer = new byte[size];

            is.read(buffer);

            is.close();

            json = new String(buffer, "UTF-8");


        } catch (IOException ex) {
            return null;
        } catch (NullPointerException npe) {
            return null;
        }
        return json;
    }

    /**
     * Read a file from the <code>Resources</code> folder.
     * Used mainly for unit testing.
     * @param caller
     * @param fileUri
     * @return file content as a string
     */
    public static String readFileFromResources(Class caller, String fileUri) {
        String json = null;
        try {
            InputStream is = caller.getClassLoader().getResourceAsStream(fileUri);

            int size = is.available();

            byte[] buffer = new byte[size];

            is.read(buffer);

            is.close();

            json = new String(buffer, "UTF-8");


        } catch (IOException ex) {
            return null;
        } catch (NullPointerException npe) {
            return null;
        }
        return json;

    }
}
