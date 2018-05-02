package geog477.fooddesert;

import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.Multipoint;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.PointCollection;
import com.esri.arcgisruntime.geometry.Polygon;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ArcMapActivity extends AppCompatActivity {

    /* Constant value for number of meters in a mile*/
    private static final double METERS_IN_MILE = 1609.34;

    private MapView mMapView;

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
        ArcGISMap map = new ArcGISMap(Basemap.Type.TOPOGRAPHIC, initialView.getY(), initialView.getX(), 16);
        mMapView.setMap(map);

        groceryStoresBufferOverlay = new GraphicsOverlay();
        mMapView.getGraphicsOverlays().add(groceryStoresBufferOverlay);

        groceryStores = new PointCollection(SpatialReferences.getWebMercator());

        /*This should cause the API call to happen outside the UI thread*/
        PlacesAsyncTask placesTask = new PlacesAsyncTask();
        GeoApiContext context = new GeoApiContext
                .Builder()
                .apiKey(getString(R.string.google_maps_key))
                .build();
        PlacesAsyncTask.Params params = placesTask.buildParams(context, initialView, 5*(int)METERS_IN_MILE);
        placesTask.execute(params);
    }

    @Override
    protected void onPause(){
        mMapView.pause();
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

    private class PlacesAsyncTask extends AsyncTask<PlacesAsyncTask.Params, Integer, PointCollection> {

        public class Params {
            public final GeoApiContext context;
            public final Point center;
            public final int radiusMeters;

            private Params(GeoApiContext context, Point center, int radiusMeters) {
                this.context = context;
                this.center = center;
                this.radiusMeters = radiusMeters;
            }
        }

        public PlacesAsyncTask.Params buildParams(GeoApiContext context, Point center, int radiusMeters){
            return new Params(context, center, radiusMeters);
        }

        @Override
        protected PointCollection doInBackground(Params... params) {
            PointCollection storeLocations = new PointCollection(SpatialReferences.getWebMercator());
            for (Params p : params) {
                NearbySearchRequest searchRequest = PlacesApi.nearbySearchQuery(p.context, pointToLatLng(p.center));

                try {
                    PlacesSearchResponse response = searchRequest
                            .radius(p.radiusMeters)
                            .type(PlaceType.GROCERY_OR_SUPERMARKET)
                            .await();

                    fillStoresSet(p.context, response, storeLocations);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return storeLocations;
        }

        @Override
        protected void onPostExecute(PointCollection storeLocations) {
            //set up data structures for main class
            groceryStores.addAll(storeLocations);
            groceryStoresBuffer = GeometryEngine.buffer(new Multipoint(groceryStores), METERS_IN_MILE);

            //Symbols used to draw data
            SimpleMarkerSymbol redCircle = new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, 0xFFFF0000, 10);
            FillSymbol blueFill = new SimpleFillSymbol(SimpleFillSymbol.Style.CROSS, Color.BLUE, null);

            for (Point p : storeLocations) {
                Graphic g = new Graphic(p, redCircle);
                groceryStoresBufferOverlay.getGraphics().add(g);
            }

            Graphic graphic1 = new Graphic(groceryStoresBuffer, blueFill);
            groceryStoresBufferOverlay.getGraphics().add(graphic1);
        }

        private void fillStoresSet(GeoApiContext context, PlacesSearchResponse response, PointCollection storeLocations) throws InterruptedException, IOException, ApiException {
            for (PlacesSearchResult result : response.results) {
            /*reproject the the point returned by the places API. This code seems to work but I'm
             *not quite sure it's 100% correct.*/
                LatLng store = result.geometry.location;

                //casting could cause runtime error?
                Point storeProj = (Point) GeometryEngine.project(latLngToPoint(store), SpatialReferences.getWebMercator());
                storeLocations.add(storeProj);
            }

            if (response.nextPageToken != null) {
                NearbySearchRequest searchRequest = PlacesApi.nearbySearchNextPage(context, response.nextPageToken);

                Thread.sleep(2000);
                PlacesSearchResponse pagingResponse = searchRequest
                        .pageToken(response.nextPageToken)
                        .await();

                fillStoresSet(context, pagingResponse, storeLocations);
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
