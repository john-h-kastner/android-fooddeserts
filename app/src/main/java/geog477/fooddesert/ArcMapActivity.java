package geog477.fooddesert;

import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.Multipoint;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.PointCollection;
import com.esri.arcgisruntime.geometry.Polygon;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.FillSymbol;
import com.esri.arcgisruntime.symbology.SimpleFillSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;
import com.google.maps.GeoApiContext;
import com.google.maps.NearbySearchRequest;
import com.google.maps.PlacesApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlaceType;
import com.google.maps.model.PlacesSearchResponse;
import com.google.maps.model.PlacesSearchResult;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ArcMapActivity extends AppCompatActivity {

    /* Constant value for number of meters in a mile*/
    private static final double METERS_IN_MILE = 1609.34;

    /* Default query radius for places API */
    /* TODO: make query radius dynamic */
    private static final int PLACES_QUERY_RADIUS = 5 * (int) METERS_IN_MILE;

    /*Symbols used to draw data*/
    private static final SimpleMarkerSymbol RED_CIRCLE_SYMBOL = new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, Color.RED, 10);
    private static final FillSymbol BLUE_FILL_SYMBOL = new SimpleFillSymbol(SimpleFillSymbol.Style.CROSS, Color.BLUE, null);

    private static final String POINT_STORE_FILE = "pointStorage";

    private MapView mMapView;
    private Button getStoresButton;

    private GeoApiContext context;

    private PointCollection groceryStores;
    private Polygon groceryStoresBuffer;
    private GraphicsOverlay groceryStoresBufferOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arc_map);

        /* Initial location is currently college park. This should probably attempt to detect user
         * location from gps and use that instead.*/
        Point initialView = new Point(-76.927, 38.996,  SpatialReferences.getWebMercator());
        mMapView = findViewById(R.id.mapView);
        ArcGISMap map = new ArcGISMap(Basemap.Type.TOPOGRAPHIC, initialView.getY(), initialView.getX(), 11);
        mMapView.setMap(map);

        getStoresButton = findViewById(R.id.getStoresButton);
        getStoresButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Geometry viewGeometry = mMapView.getCurrentViewpoint(Viewpoint.Type.CENTER_AND_SCALE).getTargetGeometry();
                Point center = viewGeometry.getExtent().getCenter();
                Point projCenter = (Point) GeometryEngine.project(center, SpatialReferences.getWgs84());

                makePlacesCallAsync(projCenter, PLACES_QUERY_RADIUS);
            }
        });

        groceryStoresBufferOverlay = new GraphicsOverlay();
        mMapView.getGraphicsOverlays().add(groceryStoresBufferOverlay);

        groceryStores = new PointCollection(SpatialReferences.getWebMercator());

        context = new GeoApiContext
                .Builder()
                .apiKey(getString(R.string.google_maps_key))
                .build();

        /*load and render saved points*/
        loadPointCollection();
        updatePointsGraphics();
    }

    @Override
    protected void onPause(){
        mMapView.pause();
        savePointCollection();
        super.onPause();
    }

    @Override
    protected void onResume(){
        super.onResume();
        mMapView.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.dispose();
    }

    private void makePlacesCallAsync(Point center, int radiusMeters){
        /*This should cause the API call to happen outside the UI thread*/
        PlacesAsyncTask placesTask = new PlacesAsyncTask();
        PlacesAsyncTask.Params params = placesTask.buildParams(center, radiusMeters);
        placesTask.execute(params);
    }

    /* Update the graphics displayed on the mapView to reflect points added since
     * the las call to updatePointsGraphics.*/
    private void updatePointsGraphics(){
        Multipoint groceryMultipoint = new Multipoint(groceryStores);
        groceryStoresBuffer = GeometryEngine.buffer(groceryMultipoint, METERS_IN_MILE);

        //remove old graphics
        groceryStoresBufferOverlay.getGraphics().clear();

        //add new graphics
        Graphic graphic0 = new Graphic(groceryMultipoint, RED_CIRCLE_SYMBOL);
        groceryStoresBufferOverlay.getGraphics().add(graphic0);

        Graphic graphic1 = new Graphic(groceryStoresBuffer, BLUE_FILL_SYMBOL);
        groceryStoresBufferOverlay.getGraphics().add(graphic1);
    }

    /* Save the current collection of points to a private file.
     * This method is called in onPause to record any points obtained from places API.
     * I don't think it's worth using a database at the moment but, if we want to store more data
     * (name of store, type of store, etc.), it might be good to use one.*/
    private void savePointCollection(){
        ObjectOutputStream objOut = null;
        try {
            FileOutputStream file = openFileOutput(POINT_STORE_FILE, Context.MODE_PRIVATE);
            objOut = new ObjectOutputStream(file);

            //file starts with the number of points that will be in the file
            objOut.writeInt(groceryStores.size());

            //followed by the coordinates of each point
            for (Point p : groceryStores) {
                objOut.writeDouble(p.getX());
                objOut.writeDouble(p.getY());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(objOut != null) {
                try {
                    objOut.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /* Load points into the point collection from a private file.
     * This method is called in onCreate to retrieve points obtained from
     * Places API in prior runs. */
    private void loadPointCollection() {
        ObjectInput objIn = null;
        try {
            FileInputStream file = openFileInput(POINT_STORE_FILE);
            objIn = new ObjectInputStream(file);

            /* number of points in the file is in the first bytes in the file */
            int numPoints = objIn.readInt();

            for(int i = 0; i < numPoints; i++){
                double x = objIn.readDouble();
                double y = objIn.readDouble();
                Point p = new Point(x,y,SpatialReferences.getWebMercator());
                groceryStores.add(p);
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

    private class PlacesAsyncTask extends AsyncTask<PlacesAsyncTask.Params, Integer, PointCollection> {

        public class Params {
            public final Point center;
            public final int radiusMeters;

            private Params(Point center, int radiusMeters) {
                this.center = center;
                this.radiusMeters = radiusMeters;
            }
        }

        public PlacesAsyncTask.Params buildParams(Point center, int radiusMeters){
            return new Params(center, radiusMeters);
        }

        @Override
        protected PointCollection doInBackground(Params... params) {
            PointCollection storeLocations = new PointCollection(SpatialReferences.getWebMercator());
            for (Params p : params) {
                NearbySearchRequest searchRequest = PlacesApi.nearbySearchQuery(context, pointToLatLng(p.center));

                try {
                    PlacesSearchResponse response = searchRequest
                            .radius(p.radiusMeters)
                            .type(PlaceType.GROCERY_OR_SUPERMARKET)
                            .await();

                    fillStoresSet(response, storeLocations);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return storeLocations;
        }

        @Override
        protected void onPostExecute(PointCollection storeLocations) {
            //Update data structures with new points
            //maybe move this loop off of the main thread?
            for(Point p : storeLocations){
                if(!groceryStores.contains(p)){
                    groceryStores.add(p);
                }
            }

            updatePointsGraphics();
        }

        private void fillStoresSet(PlacesSearchResponse response, PointCollection storeLocations) throws InterruptedException, IOException, ApiException {
            for (PlacesSearchResult result : response.results) {
                /*reproject the the point returned by the places API. This code seems to work but I'm
                 *not quite sure it's 100% correct.*/
                LatLng store = result.geometry.location;
                Point storeProj = (Point) GeometryEngine.project(latLngToPoint(store), SpatialReferences.getWebMercator());
                storeLocations.add(storeProj);
            }

            if (response.nextPageToken != null) {
                NearbySearchRequest searchRequest = PlacesApi.nearbySearchNextPage(context, response.nextPageToken);

                /* A 2 second interval is required between calls to the places API.
                 * This should only ever be called off of the UI thread so, sleeping shouldn't
                 * cause frame rate issues */
                Thread.sleep(2000);
                PlacesSearchResponse pagingResponse = searchRequest
                        .pageToken(response.nextPageToken)
                        .await();

                fillStoresSet(pagingResponse, storeLocations);
            }
        }
    }

    /* These functions convert between the Point type used by Arc and the LatLng type used by the
     * places API.
     */

    private static LatLng pointToLatLng(Point p){
        return new LatLng(p.getY(), p.getX());
    }

    private static Point latLngToPoint(LatLng ll){
        //Not sure if wgs84 is the correct spatial reference but, it seems to work as it is.
        return new Point(ll.lng, ll.lat, SpatialReferences.getWgs84());
    }
}
