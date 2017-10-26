package no.kartverket.bordergo.data;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import no.kartverket.bordergo.BorderGoApp;
import no.kartverket.bordergo.R;
import no.kartverket.data.DataLogger;

public class DataActivity extends Activity {

    private BorderGoApp app;
    private DataLogger logger;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);
        app = (BorderGoApp) getApplication();

        logger = app.getDataLogger();
        //String[] myLogs = logger.getLogs();

        /*ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, myLogs);*/

        LogItemAdapter adapter = new LogItemAdapter(this, logger);

        ListView listView = (ListView) findViewById(R.id.logList);
        listView.setAdapter(adapter);

        // altitude log;
        /*double[] altitudes = logger.getDoubleArray(BorderGoApp.LoggNames.ALT_INTERPOLATED);
        if(altitudes != null){
            GraphView graph = (GraphView) findViewById(R.id.graph);
            DataPoint[] dataPoints = new DataPoint[altitudes.length];
            for(int i = 0; i< altitudes.length;i++){
                dataPoints[i] = new DataPoint(i,altitudes[i]);
            }

            LineGraphSeries<DataPoint> series = new LineGraphSeries<>(dataPoints);
            graph.addSeries(series);
            graph.getViewport().setScalable(true);
            graph.getViewport().setScalableY(true);

            /* for(DataLogger.TimeStampedData<Double> data : zTimeSeries){
                if (i == 0)
                    startms = data.timestamp;
                dataPoints[i++] = new DataPoint(data.timestamp -startms ,data.value);
            }

        }*/
        /*Collection<DataLogger.TimeStampedData<Double>> zTimeSeries = logger.getDataTimeSeriesDouble(BorderGoApp.LoggNames.Z_TIMESTAMPED_DATA);

        if(zTimeSeries != null){

            Object[] timeSeries = zTimeSeries.toArray();
            int l = timeSeries.length;
            int i = 0;
            DataLogger.TimeStampedData<Double> first = (DataLogger.TimeStampedData<Double>)timeSeries[0];
            long startms = first.timestamp;
            DataPoint[] dataPoints = new DataPoint[l];
            for(Object timestamped:timeSeries){
                DataLogger.TimeStampedData<Double> timeStampedData = (DataLogger.TimeStampedData<Double>)timestamped;
                dataPoints[i++] = new DataPoint(timeStampedData.timestamp  - startms ,timeStampedData.value);
            }

            String[] logs = logger.getLogs();

            GraphView graph = (GraphView) findViewById(R.id.graph);

            LineGraphSeries<DataPoint> series = new LineGraphSeries<>(dataPoints);
            graph.addSeries(series);
            graph.getViewport().setScalable(true);
            graph.getViewport().setScalableY(true);

            File tileZ_TS = logger.makeTextFile(BorderGoApp.LoggNames.Z_TIMESTAMPED_DATA);
        }*/


    }
}
