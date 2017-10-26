package no.kartverket.bordergo.data;

import android.app.Activity;
import android.os.Bundle;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.Collection;

import no.kartverket.bordergo.BorderGoApp;
import no.kartverket.bordergo.R;
import no.kartverket.data.DataLogger;

import static android.R.attr.data;

/**
 * Created by janvin on 16.06.2017.
 */

public class GraphActivity extends Activity {
    private BorderGoApp  app;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);


        String logname = getIntent().getStringExtra("LOG_NAME");

        app = (BorderGoApp) getApplication();

        DataLogger logger = app.getDataLogger();

        if(logger.hasLog(logname)) {


            DataLogger.LogInfoItem item = logger.getLogInfoItem(logname);
            if (item.size > 0) {
                GraphView graph = (GraphView) findViewById(R.id.graph);
                setData(item,graph,logger);


            } else {
                this.app.shortToast("Log " + logname +" has no data");
            }
        } else {
            this.app.shortToast("No log with name: " + logname);
        }

    }

    private void setData(DataLogger.LogInfoItem  item, GraphView graph, DataLogger logger){
        DataPoint[] dataPoints = new DataPoint[item.size];

        switch (item.logType) {
            case DOUBLE:
                double[] doubleValues = logger.getDoubleArray(item.name);
                for(int i =0; i<item.size; i++){
                    dataPoints[i] =  new DataPoint(i,doubleValues[i]);
                }
                break;
            case TIME_SERIES_DOUBLE:
                DataLogger.TimeStampedData<Double>[] tsValues = logger.getTimeStampedDoubleArray(item.name);
                for(int i =0; i<item.size; i++){
                    DataLogger.TimeStampedData<Double> tsData =  tsValues[i];
                    dataPoints[i] =  new DataPoint(tsData.timestamp,tsData.value);
                }
                break;

            default:
                this.app.shortToast("no graph view for log: " + item.name + " with type: " + item.logType.toString());
        }

        LineGraphSeries<DataPoint> series = new LineGraphSeries<>(dataPoints);
        graph.addSeries(series);
        graph.getViewport().setScalable(true);
        graph.getViewport().setScalableY(true);
    }
}
