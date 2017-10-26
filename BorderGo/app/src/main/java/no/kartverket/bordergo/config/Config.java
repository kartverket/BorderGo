package no.kartverket.bordergo.config;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by janvin on 07.09.2017.
 */

public class Config {
    private static HashMap<String,String> keyValues = new HashMap<String,String>();
    private Context context;
    private AssetManager assetManager;
    private static final String DEBUG__CONFIG_FILENAME = "debug_config_strings.txt";
    private static final String CONFIG_FILENAME = "config_strings.txt";


    public static final class Keys{
        public static final String BASE_WMS_SERVICE_URL  = "BASE_WMS_SERVICE_URL";
        public static final String ORTHO_WMS_SERVICE_URL  = "ORTHO_WMS_SERVICE_URL";
        public static final String KARTVERKET_TOKEN_KEY  = "KARTVERKET_TOKEN_KEY";
        public static final String KARTVERKET_NORGESKART_URL  = "KARTVERKET_NORGESKART_URL";
        public static final String WFS_USER = "WFS_USER";
        public static final String WFS_PASSWRD = "WFS_PASSWRD";
        public static final String WFS_BASE_URL = "WFS_BASE_URL";
        public static final String HEIGHT_SERVICE_URL = "HEIGHT_SERVICE_URL";


    }

    public Config(Context context){
        this.context = context;
        init();

    }

    public void init(){
        assetManager = context.getAssets();
        // Read keys and values from config_strings.txt
        readAndParseConfigFile(CONFIG_FILENAME);

        // Read keys and values from debug_config_strings.txt // will overwrite CONFIG_FILENAME parts
        readAndParseConfigFile(DEBUG__CONFIG_FILENAME);
    }

    private static final String splitter = ":::";

    private void readAndParseConfigFile(String filename){
        try{
            InputStream inputStream = assetManager.open(filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            String key;
            String val;
            String[] parts;
            line = reader.readLine();
            while(line != null){

                parts = line.split(splitter);
                key = parts[0].trim();
                parts = Arrays.copyOfRange(parts, 1, parts.length);
                val = Arrays.toString(parts); // contains parenthesis
                if(val.length()>2){ // checks if there is more than the parenthesis == actual value content
                    val = val.substring(1,val.length()-1).trim();
                    setConfigValue(key,val);
                }

                line = reader.readLine();
            }


        } catch (IOException e){
            return;
        }

    }

    private void setConfigValue(String key, String value){
        keyValues.put(key,value);
    }


    public static String getConfigValue(String key){
        return keyValues.get(key);
    }





}
