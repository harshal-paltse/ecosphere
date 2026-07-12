package com.example.carboncalculator;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerritoryActivity extends BaseActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private TextView tvMapSubtitle, tvSearchResult;
    private EditText etCitySearch;
    private Handler mainHandler;
    private ExecutorService executor;

    // Key cities to fetch pollution data for
    private static final double[][] HOTSPOT_LOCATIONS = {
        {19.9975, 73.7898, 0}, // Nashik
        {18.5204, 73.8567, 0}, // Pune
        {19.0760, 72.8777, 0}, // Mumbai
        {21.1458, 79.0882, 0}, // Nagpur
        {28.6139, 77.2090, 0}, // Delhi
        {12.9716, 77.5946, 0}, // Bangalore
        {17.3850, 78.4867, 0}, // Hyderabad
    };
    private static final String[] HOTSPOT_NAMES = {
        "Nashik", "Pune", "Mumbai", "Nagpur", "Delhi", "Bangalore", "Hyderabad"
    };

    // NGO eco markers
    private static final String[] MARKER_TITLES = {
        "Green Foundation — Nashik",
        "Earth Protectors — Pune",
        "Clean Rivers India — Mumbai",
        "Eco Warriors — Nagpur",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Configuration.getInstance().load(this,
                PreferenceManager.getDefaultSharedPreferences(this));
            Configuration.getInstance().setUserAgentValue(getPackageName());
        } catch (Exception ignored) {}

        setContentView(R.layout.activity_territory);

        mainHandler   = new Handler(Looper.getMainLooper());
        executor      = Executors.newFixedThreadPool(3);
        tvMapSubtitle  = findViewById(R.id.tvMapSubtitle);
        tvSearchResult = findViewById(R.id.tvSearchResult);
        etCitySearch   = findViewById(R.id.etCitySearch);

        // Search button
        findViewById(R.id.btnSearch).setOnClickListener(v -> performSearch());

        // Search on keyboard "Search" action
        etCitySearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                performSearch();
                return true;
            }
            return false;
        });

        try {
            mapView = findViewById(R.id.mapView);
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setMultiTouchControls(true);
            mapView.getController().setZoom(5.5);
            mapView.getController().setCenter(new GeoPoint(20.5937, 78.9629));
            addEcoMarkers();
            setupLocationOverlay();
        } catch (Exception ignored) {}

        tvMapSubtitle.setText("Loading pollution zones...");
        fetchAllHotspots();

        findViewById(R.id.btnMyLocation).setOnClickListener(v -> centerOnMyLocation());
        setupNav();
    }

    // ── City search ───────────────────────────────────────────────────────────

    private void performSearch() {
        String query = etCitySearch.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Enter a city or location name", Toast.LENGTH_SHORT).show();
            return;
        }
        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etCitySearch.getWindowToken(), 0);

        tvMapSubtitle.setText("Searching " + query + "...");
        tvSearchResult.setVisibility(android.view.View.GONE);

        executor.execute(() -> geocodeAndFetch(query));
    }

    private void geocodeAndFetch(String cityName) {
        try {
            // Nominatim geocoding — free, no key needed
            String encoded = java.net.URLEncoder.encode(cityName, "UTF-8");
            String urlStr = "https://nominatim.openstreetmap.org/search?q=" + encoded
                + "&format=json&limit=1";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", getPackageName());
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            if (conn.getResponseCode() != 200) {
                mainHandler.post(() -> {
                    tvMapSubtitle.setText("Location not found");
                    Toast.makeText(this, "Could not find: " + cityName, Toast.LENGTH_SHORT).show();
                });
                return;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONArray results = new JSONArray(sb.toString());
            if (results.length() == 0) {
                mainHandler.post(() -> {
                    tvMapSubtitle.setText("Location not found");
                    Toast.makeText(this, "No results for: " + cityName, Toast.LENGTH_SHORT).show();
                });
                return;
            }

            JSONObject place = results.getJSONObject(0);
            double lat = place.getDouble("lat");
            double lon = place.getDouble("lon");
            String displayName = place.optString("display_name", cityName);
            // Shorten display name to city, country
            String shortName = displayName.contains(",")
                ? displayName.substring(0, displayName.indexOf(",")).trim()
                : cityName;

            // Move map to location
            mainHandler.post(() -> {
                if (mapView != null) {
                    mapView.getController().animateTo(new GeoPoint(lat, lon));
                    mapView.getController().setZoom(10.0);
                }
                tvMapSubtitle.setText("Fetching pollution for " + shortName + "...");
            });

            // Fetch pollution data
            fetchSearchedLocation(lat, lon, shortName);

        } catch (Exception e) {
            android.util.Log.e("Territory", "Geocode failed: " + e.getMessage());
            mainHandler.post(() -> {
                tvMapSubtitle.setText("Search failed");
                Toast.makeText(this, "Search error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void fetchSearchedLocation(double lat, double lon, String cityName) {
        try {
            String urlStr = String.format(Locale.US,
                "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=%.4f&longitude=%.4f" +
                "&hourly=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,ozone" +
                "&forecast_days=1", lat, lon);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() != 200) {
                mainHandler.post(() -> tvMapSubtitle.setText("Pollution data unavailable"));
                return;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject json   = new JSONObject(sb.toString());
            JSONObject hourly = json.getJSONObject("hourly");
            double pm25 = getFirstValid(hourly.getJSONArray("pm2_5"));
            double pm10 = getFirstValid(hourly.getJSONArray("pm10"));
            double no2  = getFirstValid(hourly.getJSONArray("nitrogen_dioxide"));
            double o3   = getFirstValid(hourly.getJSONArray("ozone"));
            double aqi  = calcAqi(pm25);

            String level;
            int fillColor;
            if      (aqi <= 50)  { level = "Good";           fillColor = Color.argb(80, 29, 185, 84); }
            else if (aqi <= 100) { level = "Moderate";       fillColor = Color.argb(80, 255, 213, 79); }
            else if (aqi <= 150) { level = "Unhealthy";      fillColor = Color.argb(80, 255, 167, 38); }
            else if (aqi <= 200) { level = "Very Unhealthy"; fillColor = Color.argb(100, 239, 83, 80); }
            else                 { level = "Hazardous";      fillColor = Color.argb(120, 198, 40, 40); }

            final String resultText = String.format(Locale.getDefault(),
                "%s — AQI: %.0f (%s)  |  PM2.5: %.1f  PM10: %.1f  NO₂: %.1f  O₃: %.1f",
                cityName, aqi, level, pm25, pm10, no2, o3);
            final int fc = fillColor;
            final String lvl = level;

            mainHandler.post(() -> {
                if (mapView == null) return;
                drawHotspotCircle(lat, lon, 20000, fc);

                Marker m = new Marker(mapView);
                m.setPosition(new GeoPoint(lat, lon));
                m.setTitle(cityName + " — " + lvl);
                m.setSnippet(String.format(Locale.getDefault(),
                    "AQI: %.0f | PM2.5: %.1f | PM10: %.1f\nNO₂: %.1f | O₃: %.1f",
                    aqi, pm25, pm10, no2, o3));
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                mapView.getOverlays().add(m);
                mapView.invalidate();

                tvMapSubtitle.setText("Pollution zones loaded");
                tvSearchResult.setText("📍 " + resultText);
                tvSearchResult.setVisibility(android.view.View.VISIBLE);
            });

        } catch (Exception e) {
            android.util.Log.e("Territory", "Pollution fetch failed: " + e.getMessage());
            mainHandler.post(() -> tvMapSubtitle.setText("Could not load pollution data"));
        }
    }

    // ── Fetch pollution data for all cities ───────────────────────────────────

    private void fetchAllHotspots() {
        for (int i = 0; i < HOTSPOT_LOCATIONS.length; i++) {
            final int idx = i;
            final double lat = HOTSPOT_LOCATIONS[i][0];
            final double lon = HOTSPOT_LOCATIONS[i][1];
            executor.execute(() -> fetchAndDrawHotspot(lat, lon, HOTSPOT_NAMES[idx]));
        }
    }

    private void fetchAndDrawHotspot(double lat, double lon, String cityName) {
        try {
            // Open-Meteo Air Quality API — free, no key needed
            String urlStr = String.format(Locale.US,
                "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=%.4f&longitude=%.4f" +
                "&hourly=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,ozone" +
                "&forecast_days=1",
                lat, lon);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() != 200) return;

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject json    = new JSONObject(sb.toString());
            JSONObject hourly  = json.getJSONObject("hourly");
            JSONArray  pm25Arr = hourly.getJSONArray("pm2_5");
            JSONArray  pm10Arr = hourly.getJSONArray("pm10");
            JSONArray  coArr   = hourly.getJSONArray("carbon_monoxide");
            JSONArray  no2Arr  = hourly.getJSONArray("nitrogen_dioxide");
            JSONArray  o3Arr   = hourly.getJSONArray("ozone");

            // Use the most recent non-null value (index 0 = current hour)
            double pm25 = getFirstValid(pm25Arr);
            double pm10 = getFirstValid(pm10Arr);
            double co   = getFirstValid(coArr);
            double no2  = getFirstValid(no2Arr);
            double o3   = getFirstValid(o3Arr);

            // Calculate AQI from PM2.5
            double aqi = calcAqi(pm25);

            // Determine color and level
            int fillColor;
            String level;
            if (aqi <= 50) {
                fillColor = Color.argb(80, 29, 185, 84);   // green
                level = "Good";
            } else if (aqi <= 100) {
                fillColor = Color.argb(80, 255, 213, 79);  // yellow
                level = "Moderate";
            } else if (aqi <= 150) {
                fillColor = Color.argb(80, 255, 167, 38);  // orange
                level = "Unhealthy";
            } else if (aqi <= 200) {
                fillColor = Color.argb(100, 239, 83, 80);  // red
                level = "Very Unhealthy";
            } else {
                fillColor = Color.argb(120, 198, 40, 40);  // dark red
                level = "Hazardous";
            }

            String snippet = String.format(Locale.getDefault(),
                "AQI: %.0f (%s)\nPM2.5: %.1f | PM10: %.1f\nNO₂: %.1f | O₃: %.1f",
                aqi, level, pm25, pm10, no2, o3);

            final int fc = fillColor;
            final String snip = snippet;
            final String lvl = level;

            mainHandler.post(() -> {
                if (mapView == null) return;
                // Draw filled circle (polygon approximation)
                drawHotspotCircle(lat, lon, 25000, fc); // 25km radius
                // Add marker with data
                Marker m = new Marker(mapView);
                m.setPosition(new GeoPoint(lat, lon));
                m.setTitle(cityName + " — " + lvl);
                m.setSnippet(snip);
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                mapView.getOverlays().add(m);
                mapView.invalidate();
                tvMapSubtitle.setText("Pollution zones loaded");
            });

        } catch (Exception e) {
            android.util.Log.e("Territory", "Hotspot fetch failed for " + cityName + ": " + e.getMessage());
        }
    }

    // ── Draw a filled circle on the map ──────────────────────────────────────

    private void drawHotspotCircle(double lat, double lon, double radiusMeters, int fillColor) {
        List<GeoPoint> points = new ArrayList<>();
        int steps = 36;
        double earthRadius = 6371000.0;
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians((double) i * 360 / steps);
            double dLat  = (radiusMeters / earthRadius) * Math.cos(angle);
            double dLon  = (radiusMeters / earthRadius) * Math.sin(angle)
                           / Math.cos(Math.toRadians(lat));
            points.add(new GeoPoint(
                lat + Math.toDegrees(dLat),
                lon + Math.toDegrees(dLon)));
        }

        Polygon circle = new Polygon(mapView);
        circle.setPoints(points);
        circle.getFillPaint().setColor(fillColor);
        circle.getOutlinePaint().setColor(fillColor);
        circle.getOutlinePaint().setStrokeWidth(2f);
        circle.getOutlinePaint().setStyle(Paint.Style.STROKE);
        mapView.getOverlays().add(0, circle); // add below markers
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double getFirstValid(JSONArray arr) {
        try {
            for (int i = 0; i < Math.min(arr.length(), 6); i++) {
                if (!arr.isNull(i)) return arr.getDouble(i);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private double calcAqi(double pm25) {
        double[][] bp = {
            {0.0, 12.0, 0, 50}, {12.1, 35.4, 51, 100}, {35.5, 55.4, 101, 150},
            {55.5, 150.4, 151, 200}, {150.5, 250.4, 201, 300},
            {250.5, 350.4, 301, 400}, {350.5, 500.4, 401, 500},
        };
        for (double[] b : bp)
            if (pm25 >= b[0] && pm25 <= b[1])
                return ((b[3] - b[2]) / (b[1] - b[0])) * (pm25 - b[0]) + b[2];
        return pm25 > 500 ? 500 : 0;
    }

    // ── Eco NGO markers ───────────────────────────────────────────────────────

    private void addEcoMarkers() {
        double[][] coords = {
            {19.9975, 73.7898}, {18.5204, 73.8567},
            {19.0760, 72.8777}, {21.1458, 79.0882},
        };
        for (int i = 0; i < MARKER_TITLES.length; i++) {
            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(coords[i][0], coords[i][1]));
            marker.setTitle(MARKER_TITLES[i]);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(marker);
        }
    }

    // ── Location overlay ──────────────────────────────────────────────────────

    private void setupLocationOverlay() {
        locationOverlay = new MyLocationNewOverlay(
            new GpsMyLocationProvider(this), mapView);
        locationOverlay.enableMyLocation();
        mapView.getOverlays().add(locationOverlay);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                             Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST);
        }
    }

    private void centerOnMyLocation() {
        if (locationOverlay == null || mapView == null) return;
        if (locationOverlay.getMyLocation() != null) {
            mapView.getController().animateTo(locationOverlay.getMyLocation());
            mapView.getController().setZoom(12.0);
        } else {
            Toast.makeText(this, "Locating...", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == LOCATION_PERMISSION_REQUEST && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            locationOverlay.enableMyLocation();
        }
    }

    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause()  { super.onPause();  if (mapView != null) mapView.onPause(); }
    @Override protected void onDestroy() { super.onDestroy(); if (executor != null) executor.shutdown(); }

    private void setupNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)); finish();
        });
        findViewById(R.id.navScan).setOnClickListener(v ->
            startActivity(new Intent(this, ScanActivity.class)));
        findViewById(R.id.navTrack).setOnClickListener(v ->
            startActivity(new Intent(this, TrackActivity.class)));
        findViewById(R.id.navTrees).setOnClickListener(v ->
            startActivity(new Intent(this, TreeLogActivity.class)));
        findViewById(R.id.navNGOs).setOnClickListener(v ->
            startActivity(new Intent(this, NGOsActivity.class)));
        findViewById(R.id.navProfile).setOnClickListener(v ->
            startActivity(new Intent(this, ProfileActivity.class)));
    }
}
