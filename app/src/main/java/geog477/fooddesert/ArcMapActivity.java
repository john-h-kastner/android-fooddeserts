package geog477.fooddesert;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.Point;
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
import com.google.maps.model.LatLng;
import com.google.maps.model.PlaceType;
import com.google.maps.model.PlacesSearchResponse;
import com.google.maps.model.PlacesSearchResult;

import java.util.ArrayList;
import java.util.List;

public class ArcMapActivity extends AppCompatActivity {

    /* Constant value for number of meters in a mile*/
    private static final double METERS_IN_MILE = 1609.34;

    private MapView mMapView;
    private List<Geometry> groceryStores;
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

        /* Initial size of this array list is 60 so because that's the maximum number of elements
         * returned by the Places API*/
        groceryStores = new ArrayList<>(60);

        /* There should be some way to do this call async but I can't be bothered to find out.
         * Simply starting it as a new Thread does not work. Another concern is that this function
         * will be called every time onCreate is called. This includes when the screen is rotated
         * among other things. */
        fillStoresCollections(initialView, 5*(int)METERS_IN_MILE);
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

    private void fillStoresCollections(Point queryCenter, int radius) {
        GeoApiContext context = new GeoApiContext.Builder().apiKey(getString(R.string.google_maps_key)).build();
        NearbySearchRequest searchRequest = PlacesApi.nearbySearchQuery(context, pointToLatLng(queryCenter));
        try {
            PlacesSearchResponse response = searchRequest
                    .radius(radius)
                    .type(PlaceType.GROCERY_OR_SUPERMARKET)
                    .await();

            fillStoresCollections(context, response);
        } catch (Exception e){
            Log.e(e.getClass().toString(), e.toString());
        }
    }

    private void fillStoresCollections(GeoApiContext context, String nextPage){
        NearbySearchRequest searchRequest = PlacesApi.nearbySearchNextPage(context, nextPage);
        try {
            /* Sleep for 2 seconds because a delay is required between calls to the API.
             * Sleeping on the main thread is a bad idea but,
             * it's required to make this paging request valid.
             * Once this is moved off of the main thread the sleep will
             * be OK.
             */
            Thread.sleep(2000);
            PlacesSearchResponse response = searchRequest
                    .pageToken(nextPage)
                    .await();

            fillStoresCollections(context, response);

        } catch (Exception e){
            Log.e(e.getClass().toString(), e.toString());
        }
    }

    private void fillStoresCollections(GeoApiContext context, PlacesSearchResponse response){
        for (PlacesSearchResult result : response.results) {
            /*reproject the the point returned by the places API. This code seems to work but I'm
             *not quite sure it's 100% correct.*/
            LatLng store = result.geometry.location;
            Geometry storeProj = GeometryEngine.project(latLngToPoint(store), SpatialReferences.getWebMercator());
            groceryStores.add(storeProj);
        }

        if(response.nextPageToken != null){
            fillStoresCollections(context, response.nextPageToken);
        } else {

            //Symbols used to draw data
            SimpleMarkerSymbol redCircle = new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, 0xFFFF0000, 10);
            FillSymbol blueFill = new SimpleFillSymbol(SimpleFillSymbol.Style.CROSS, Color.BLUE, null);

            //Add a point for each store to the map
            for(Geometry p : groceryStores){
                Graphic g = new Graphic(p,redCircle);
                groceryStoresBufferOverlay.getGraphics().add(g);
            }

            //Draw a 1 mile buffer around all grocery stores
            groceryStoresBuffer = GeometryEngine.buffer(GeometryEngine.union(groceryStores), METERS_IN_MILE);
            Graphic graphic1 = new Graphic(groceryStoresBuffer, blueFill);
            groceryStoresBufferOverlay.getGraphics().add(graphic1);
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
