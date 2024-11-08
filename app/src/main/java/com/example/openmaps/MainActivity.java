package com.example.openmaps;

import android.os.Bundle;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

// imports for geocoding
import android.os.AsyncTask;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private MapView mapView;
    private Marker addressMarker;
    private Marker reverseGeocodeMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load osmdroid configuration
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(this));

        setContentView(R.layout.activity_main);

        // Initialize the map
        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(false);  // Disable built-in zoom controls
        mapView.setMultiTouchControls(true);

        // Set the starting point and zoom level
        GeoPoint startPoint = new GeoPoint(48.8583, 2.2944);  // Example: Eiffel Tower
        mapView.getController().setZoom(15.0);
        mapView.getController().setCenter(startPoint);

        // Request necessary permissions
        requestPermissionsIfNecessary(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        });

        // Initialize map controls
        initializeMapControls();

        // Setup zoom buttons
        setupZoomControls();

        // Enable rotation gestures
        //enableRotationGestures();

        // center the map at user's location
        initializeLocationOverlay();

        // Set up UI components for geocoding
        EditText addressInput = findViewById(R.id.address_input);
        Button searchButton = findViewById(R.id.search_button);

        searchButton.setOnClickListener(v -> {
            String address = addressInput.getText().toString();
            if (!address.isEmpty()) {
                forwardGeocode(address);
            } else {
                Toast.makeText(MainActivity.this, "Please enter an address", Toast.LENGTH_SHORT).show();
            }
        });

        // Long-press listener for reverse geocoding
        mapView.setOnLongClickListener(v -> {
            GeoPoint centerPoint = (GeoPoint) mapView.getMapCenter();
            reverseGeocode(centerPoint);
            return true;
        });

    }

    private void initializeLocationOverlay() {
        MyLocationNewOverlay locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        locationOverlay.enableMyLocation();
        locationOverlay.enableFollowLocation();  // Makes the map center on the user’s location
        mapView.getOverlays().add(locationOverlay);
    }

    private void initializeMapControls() {
        // Compass overlay
        CompassOverlay compassOverlay = new CompassOverlay(this, new InternalCompassOrientationProvider(this), mapView);
        compassOverlay.enableCompass();
        mapView.getOverlays().add(compassOverlay);

        // Scale bar overlay
        ScaleBarOverlay scaleBarOverlay = new ScaleBarOverlay(mapView);
        scaleBarOverlay.setAlignBottom(true);
        mapView.getOverlays().add(scaleBarOverlay);

        // 2 finger rotation
        // Rotation gesture overlay
        RotationGestureOverlay rotationGestureOverlay = new RotationGestureOverlay(mapView);
        rotationGestureOverlay.setEnabled(true);
        mapView.getOverlays().add(rotationGestureOverlay);

    }

    private void setupZoomControls() {
        Button zoomInButton = findViewById(R.id.zoom_in_button);
        Button zoomOutButton = findViewById(R.id.zoom_out_button);

        zoomInButton.setOnClickListener(v -> mapView.getController().zoomIn());
        zoomOutButton.setOnClickListener(v -> mapView.getController().zoomOut());
    }

    private void enableRotationGestures() {
        mapView.setMultiTouchControls(true);
        //mapView.getController().setRotationGestureEnabled(true);
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            boolean permissionGranted = true;
            for (int result : grantResults) {
                permissionGranted &= (result == PackageManager.PERMISSION_GRANTED);
            }
            if (permissionGranted) {
                initializeLocationOverlay();
            } else {
                Toast.makeText(this, "Location permission is required to show user location on map", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    // geocoding
    // Forward Geocoding Method
    private void forwardGeocode(String address) {
        new AsyncTask<String, Void, GeoPoint>() {
            @Override
            protected GeoPoint doInBackground(String... params) {
                try {
                    String encodedAddress = URLEncoder.encode(address, "UTF-8");
                    String urlStr = "https://nominatim.openstreetmap.org/search?q=" + encodedAddress + "&format=json&addressdetails=1";
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "osmdroid-sample-app");

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    reader.close();

                    JSONArray jsonArray = new JSONArray(result.toString());
                    if (jsonArray.length() > 0) {
                        JSONObject jsonObject = jsonArray.getJSONObject(0);
                        double lat = jsonObject.getDouble("lat");
                        double lon = jsonObject.getDouble("lon");
                        return new GeoPoint(lat, lon);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(GeoPoint geoPoint) {
                if (geoPoint != null) {
                    mapView.getController().setCenter(geoPoint);
                    mapView.getController().setZoom(15.0);

                    // Remove previous marker if it exists
                    if (addressMarker != null) {
                        mapView.getOverlays().remove(addressMarker);
                    }

                    // Create and add the new marker
                    addressMarker = new Marker(mapView);
                    addressMarker.setPosition(geoPoint);
                    addressMarker.setTitle("Searched Location");
                    addressMarker.setIcon(getResources().getDrawable(R.drawable.ic_marker)); // Custom icon (optional)
                    addressMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                    mapView.getOverlays().add(addressMarker);
                    mapView.invalidate();
                } else {
                    Toast.makeText(MainActivity.this, "Address not found", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute(address);
    }

    // Reverse Geocoding Method
    private void reverseGeocode(GeoPoint geoPoint) {
        new AsyncTask<GeoPoint, Void, String>() {
            @Override
            protected String doInBackground(GeoPoint... params) {
                try {
                    String urlStr = "https://nominatim.openstreetmap.org/reverse?lat=" + geoPoint.getLatitude() +
                            "&lon=" + geoPoint.getLongitude() + "&format=json&addressdetails=1";
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "osmdroid-sample-app");

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    reader.close();

                    JSONObject jsonObject = new JSONObject(result.toString());
                    return jsonObject.getJSONObject("address").getString("display_name");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String address) {
                if (address != null) {
                    Toast.makeText(MainActivity.this, "Address: " + address, Toast.LENGTH_LONG).show();

                    // Remove previous reverse geocode marker if it exists
                    if (reverseGeocodeMarker != null) {
                        mapView.getOverlays().remove(reverseGeocodeMarker);
                    }

                    // Create and add the new marker for reverse geocode
                    reverseGeocodeMarker = new Marker(mapView);
                    reverseGeocodeMarker.setPosition(geoPoint);
                    reverseGeocodeMarker.setTitle(address); // Set the address as the title
                    reverseGeocodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_marker)); // Custom icon (optional)
                    reverseGeocodeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                    mapView.getOverlays().add(reverseGeocodeMarker);
                    mapView.invalidate();
                } else {
                    Toast.makeText(MainActivity.this, "Address not found", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute(geoPoint);
    }

}
