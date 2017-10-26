package no.kartverket.data;


/**
 * Created by janvin on 08/05/17.
 */


import java.io.DataInputStream;
import java.io.File;
import android.content.Context;

import android.os.Environment;
import android.util.FloatProperty;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Created by janerivi on 07/05/2017.
 */

public class DataLogger {
    private boolean _saveExternal = false;

    private static HashMap<String,LogItem> items = new HashMap<String,LogItem>();

    private Context appContext;

    /**
     *
     * @param appContext
     */
    public DataLogger(Context appContext){
        this.appContext = appContext;
    }

    public enum LogTypes {
        DOUBLE,
        FLOAT,
        LONG,
        INT,
        DATE,
        COMPLEX,
        TIME_SERIES_DOUBLE,
        TIME_SERIES_FLOAT,
        TIME_SERIES_INT
    }

    /**
     *
     * @param <T>
     */
    public class TimeStampedData<T>{
        public long timestamp;
        public T value;
    }

    /**
     *
     * @param name
     * @param logType
     * @return
     */
    public Boolean startLog(String name,LogTypes logType) {
        LogItem logItem = newLogItemFromLogType(logType);
        logItem.logType = logType;
        items.put(name,logItem);
        return true;
    }

    /**
     *
     * @param logType
     * @return
     */
    private LogItem newLogItemFromLogType(LogTypes logType){
        switch(logType){
            case DOUBLE:
                return new LogItem<Double>();
            case FLOAT:
                return new LogItem<Float>();
            case INT:
                return new LogItem<Integer>();
            case LONG:
                return new LogItem<Long>();
            case COMPLEX:
                return new LogItem<Object>();
            case TIME_SERIES_DOUBLE:
                return new LogItem<TimeStampedData<Double>>();
            case TIME_SERIES_FLOAT:
                return new LogItem<TimeStampedData<Float>>();
            case TIME_SERIES_INT:
                return new LogItem<TimeStampedData<Integer>>();
            default:
                return new LogItem<Object>();
        }
    }

    /**
     *
     * @param name
     * @param value
     * @return
     */
    public boolean log(String name, double value){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item, LogTypes.DOUBLE)){
            item.data.add(value);
            item.size++;
            return true;
        }
        return false;
    }

    /**
     *
     * @param name
     * @param value
     * @param timestamp
     * @return
     */
    public boolean log(String name, double value, long timestamp){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item, LogTypes.TIME_SERIES_DOUBLE)){
            TimeStampedData d = new TimeStampedData<Double>();
            d.timestamp = timestamp; d.value =value;
            item.data.add(d);
            item.size++;
            return true;
        }
        return false;
    }

    /**
     *
     * @param name
     * @param value
     * @param timestamp
     * @return
     */
    public boolean log(String name, float value, long timestamp){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item, LogTypes.TIME_SERIES_FLOAT)){
            TimeStampedData d = new TimeStampedData<Float>();
            d.timestamp = timestamp; d.value =value;
            item.data.add(d);
            item.size++;
            return true;
        }
        return false;
    }

    /**
     *
     * @param name
     * @param value
     * @param timestamp
     * @return
     */
    public boolean log(String name, int value, long timestamp){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item, LogTypes.TIME_SERIES_INT)){
            TimeStampedData d = new TimeStampedData<Integer>();
            d.timestamp = timestamp; d.value =value;
            item.data.add(d);
            item.size++;
            return true;
        }
        return false;
    }

    /**
     *
     * @param name
     * @param value
     * @return
     */
    public boolean log(String name, float value){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item,LogTypes.FLOAT)){
            item.data.add(value);
            item.size++;
            return true;
        }
        return false;
    }

    /**
     *
     * @param name
     * @param value
     * @return
     */
    public boolean log(String name, long value){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item,LogTypes.LONG)){
            item.data.add(value);
            item.size++;
            return true;
        }
        return false;
    }


    /**
     *
     * @param name
     * @param value
     * @return
     */
    public boolean log(String name, int value){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item,LogTypes.INT)){
            item.data.add(value);
            item.size++;
            return true;
        }
        return false;
    }

    /**
     *
     * @param name
     * @param values
     * @return
     */
    public boolean log(String name, Object[] values){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item,LogTypes.COMPLEX)){
            item.data.add(values);
            item.size++;
            return true;
        }
        return false;
    }

    /**
     *
     * @param name
     * @return
     */
    public boolean hasLog(String name){
        LogItem item = getItem(name);
        return item !=null;
    }

    /**
     *
     * @return
     */
    public String[] getLogs(){
        return items.keySet().toArray(new String[items.size()]);
    }

    /**
     *
     * @param name
     * @return
     */
    public int logSize(String name){
        LogItem item = getItem(name);
        if(item !=null){
            return item.size;
        }
        return -1; //
    }

    /**
     *
     * @param name
     */
    private static void clearLogByName(String name){
        items.remove(name);
    }

    /**
     *
     * @param name
     * @return
     */
    private FileInputStream getFileInputStream(String name){
        //LogItem item = getItem(name);
        try {

            FileInputStream fileInputStream = this.appContext.openFileInput(name);
            return fileInputStream;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;

    }

    /**
     *
     * @param name
     * @return
     */
    public double[] getDoubleArray(String name){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item,LogTypes.DOUBLE)){
            Object[] res = item.data.toArray();
            int l = res.length;
            double[] result = new double[l];
            int i = 0;
            for(Object o : res){
                 result[i++] = (Double)o;
            }
            return result;
        }
        return null;
    }

    /**
     *
     * @param name
     * @return
     */
    public TimeStampedData<Double>[] getTimeStampedDoubleArray(String name){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item,LogTypes.TIME_SERIES_DOUBLE)){
            Object[] res = item.data.toArray();
            int l = res.length;
            TimeStampedData<Double>[] result = new TimeStampedData[l];
            int i = 0;
            for(Object o : res){
                result[i++] = (TimeStampedData<Double>)o;
            }
            return result;
        }
        return null;
    }

    /**
     *
     * @param name
     * @return
     */
    public float[] getFloatArray(String name){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item,LogTypes.FLOAT)){
            Object[] res = item.data.toArray();
            int l = res.length;
            float[] result = new float[l];
            int i = 0;
            for(Object o : res){
                result[i++] = (Float)o;
            }
            return result;
        }
        return null;
    }

    /**
     *
     * @param name
     * @return
     */
    public long[] getLongArray(String name){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item, LogTypes.LONG)){
            Object[] res = item.data.toArray();
            int l = res.length;
            long[] result = new long[l];
            int i = 0;
            for(Object o : res){
                result[i++] = (Long)o;
            }
            return result;

        }
        return null;
    }

    /**
     *
     * @param name
     * @return
     */
    public Collection getData(String name){
        LogItem item = getItem(name);
        if(item !=null){
            return item.data;
        }
        return null;
    }

    /**
     *
     * @param name
     * @return
     */
    public Collection<Double> getDataDoubles(String name){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item, LogTypes.DOUBLE)){
            return item.data;
        }
        return null;
    }

    /**
     *
     * @param name
     * @return
     */
    public Collection<Float> getDataFloats(String name){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item, LogTypes.FLOAT)){
            return item.data;
        }
        return null;
    }

    /**
     *
     * @param name
     * @return
     */
    public Collection<TimeStampedData<Double>> getDataTimeSeriesDouble(String name){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item, LogTypes.TIME_SERIES_DOUBLE)){
            return item.data;
        }
        return null;
    }

    /**
     *
     * @param name
     * @return
     */
    public Collection<TimeStampedData<Float>> getDataTimeSeriesFloat(String name){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item, LogTypes.TIME_SERIES_FLOAT)){
            return item.data;
        }
        return null;
    }

    /**
     *
     * @param name
     * @return
     */
    public Collection<TimeStampedData<Integer>> getDataTimeSeriesInt(String name){
        LogItem item = getItem(name);
        if(logExistsAndHasCorrectType(item, LogTypes.TIME_SERIES_INT)){
            return item.data;
        }
        return null;
    }

    /**
     *
     * @param name
     * @return
     */
    public File makeTextFile(String name){
        if(externalStorageIsWritable()){
            LogItem item = getItem(name);
            String result = "";
            if(item != null){
                LogTypes type = item.logType;
                switch(type){
                    case DOUBLE:
                        result =  makeStringFromDoubleArray(name);
                        break;
                    case FLOAT:
                        result =  makeStringFromFloatArray(name);
                        break;
                    case TIME_SERIES_DOUBLE:
                        result = makeCsvStringFromTimeStampedDataDouble(name);
                        break;
                }


                if(!result.isEmpty()){ return writeResultToCsv(result,name); }


            }
        }
        return null;
    }

    /**
     *
     *
     * @param result
     * @param name
     * @return
     */
    private File writeResultToCsv(String result, String name){

        // Todo : swap between internal and external storage

        File storage = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        storage.mkdir();
        File folder = new File(storage, "/BorderGo/");
        folder.mkdir();
        String dateTime = new SimpleDateFormat("_dd.MM_HH:mm").format(Calendar.getInstance().getTime());



        File file = new File(folder, name + dateTime+".csv");

        //getExternalStoragePublicDirectory()
        //File storage = Environment.getExternalStorageDirectory();
        //File[] externalMedia = appContext.getExternalMediaDirs();
        //File docDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);

        //docDir.mkdir();

        //File storage1 = Environment.getExternalStorageDirectory();
        //File directory =  new File(storage.getAbsolutePath() + "/data_logger/");
        //directory.mkdir();
        //File file = new File(directory, name + ".txt");
        //File file = new File(docDir, name + ".txt");

        try {
            FileOutputStream os =  new FileOutputStream(file);
            os.write(result.getBytes());
            return file;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     *
     * @param name
     * @return
     */
    private String makeStringFromDoubleArray(String name){
        double[] doubles = getDoubleArray(name);
        if(doubles != null){
            return Arrays.toString(doubles).replaceAll("\\[|\\]|,|\\s", " ").replaceAll("  ", ";").trim();
        }
        return null;
    }

    /**
     *
     * @param name
     * @return
     */
    private String makeStringFromFloatArray(String name){
        float[] floats = getFloatArray(name);
        if(floats != null){
            return Arrays.toString(floats).replaceAll("\\[|\\]|,|\\s", " ").replaceAll("  ", ";").trim();
        }
        return null;
    }

    /**
     *
     * @param name
     * @return
     */
    private String makeCsvStringFromTimeStampedDataDouble(String name){
        Collection<TimeStampedData<Double>> timeStampedData = getDataTimeSeriesDouble(name);


        if(timeStampedData != null){
            String eol = System.getProperty("line.separator");
            String s = "timestamps;"+name+"(values)" + eol;
            for(TimeStampedData<Double> d : timeStampedData){ // better for thread?
                s+= Long.toString(d.timestamp) +";" + d.value.toString() + eol;
            }

            /*for(TimeStampedData<Double> data:timeStampedData){
                s+= Long.toString(data.timestamp) +";" + data.value.toString() + ";";
            }*/
            return s;
        }
        return null;
    }


    /* Checks if external storage is available for read and write */

    /**
     *
     * @return
     */
    public boolean externalStorageIsWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }


    /**
     *
     * @param name
     * @return
     */
    private LogItem getItem(String name){  return items.get(name); }


    /**
     *
     * @param item
     * @param logType
     * @return
     */
    private boolean logExistsAndHasCorrectType(LogItem item, LogTypes logType){
        return (item !=null ) && (item.logType == logType);
    }

    /**
     *
     * @return
     */
    public LogInfoItem[] getLogInfoItems(){
        String[] logNames = getLogs();
        LogInfoItem[] items = new LogInfoItem[logNames.length];

        for(int i =0;i<logNames.length;i++){
            String name = logNames[i];
            items[i] = getLogInfoItem(name);
        }

        return items;

    }

    /**
     *
     * @param name
     * @return
     */
    public LogInfoItem getLogInfoItem(String name){
        LogItem item = getItem(name);

        if(item != null) {
            LogInfoItem infoItem = new LogInfoItem();
            infoItem.name = name;
            infoItem.size = item.size;
            infoItem.logType = item.logType;
            return infoItem;
        }
        return null;
    }

    /**
     *
     */
    public class LogInfoItem {
        public LogTypes logType;
        public int size;
        public String name;


    }


    /**
     * 
     * @param <T>
     */
    private class LogItem<T> {
        public LogTypes logType;
        public ConcurrentLinkedDeque<T> data = new ConcurrentLinkedDeque<T>();
        public int size = 0;
    }


}