package geog477.fooddesert;

import android.content.Context;
import android.os.AsyncTask;

import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.PointCollection;
import com.esri.arcgisruntime.geometry.SpatialReferences;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Created by john on 5/7/18.
 * This class contains utility functions for saving and loading collections of points to
 * files in private storage. Currently uses raw file IO but, it will probably be a good idea
 * to start using a database if data becomes significantly more complex.
 */

public class PointFileUtil {

    /* Save the current collection of points to a private file.
     * This method should be called in onPause to record any points obtained from places API.
     * I don't think it's worth using a database at the moment but, if we want to store more data
     * (name of store, type of store, etc.), it might be good to use one.*/
    public static void savePointCollection(Context c, String fileName, PointCollection points) {
        ObjectOutputStream objOut = null;
        try {
            FileOutputStream file = c.openFileOutput(fileName, Context.MODE_PRIVATE);
            objOut = new ObjectOutputStream(file);

            //file starts with the number of points that will be in the file
            objOut.writeInt(points.size());

            //followed by the coordinates of each point
            for (Point p : points) {
                objOut.writeDouble(p.getX());
                objOut.writeDouble(p.getY());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (objOut != null) {
                try {
                    objOut.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /* Load points into the point collection from a private file.
     * This method should be called in onCreate to retrieve points obtained from
     * Places API in prior runs. */
    public static void loadPointCollection(Context c, String fileName,  PointCollection points) {
        ObjectInput objIn = null;
        try {
            FileInputStream file = c.openFileInput(fileName);
            objIn = new ObjectInputStream(file);

            /* number of points in the file is in the first bytes in the file */
            int numPoints = objIn.readInt();

            for (int i = 0; i < numPoints; i++) {
                double x = objIn.readDouble();
                double y = objIn.readDouble();
                Point p = new Point(x, y, SpatialReferences.getWebMercator());
                points.add(p);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (objIn != null) {
                try {
                    objIn.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class LoadPointsAsyncTask extends AsyncTask<String, Integer, PointCollection> {
        private final PointCollection finalPoints;
        private final Context context;
        private final Runnable postExecTask;

        public LoadPointsAsyncTask(Context context, PointCollection finalPoints, Runnable postExecTask){
            this.finalPoints = finalPoints;
            this.context = context;
            this.postExecTask = postExecTask;
        }

        @Override
        protected PointCollection doInBackground(String... params) {
            PointCollection points = new PointCollection(SpatialReferences.getWebMercator());
            for(String str : params){
                PointFileUtil.loadPointCollection(context, str, points);
            }
            return points;
        }

        @Override
        protected void onPostExecute(PointCollection storeLocations) {
            //Update data structures with new points
            //maybe move this loop off of the main thread?
            finalPoints.addAll(storeLocations);

            postExecTask.run();
        }
    }
}
