package com.example.carboncalculator;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TreeLogActivity extends BaseActivity {

    private TextView tvTotalTrees, tvCO2Reduced, tvTreeBadge, tvLocation;
    private ProgressBar pbTrees;
    private ImageView ivTreePhoto;
    private Button btnCapture, btnLogTrees;

    private static final double TREE_OFFSET = 21.0;
    private static final int    TREE_GOAL   = 10;
    private static final int    PERM_REQ    = 200;

    private Uri    photoUri;
    private double currentLat = 19.9975, currentLon = 73.7898; // default Nashik

    // Camera launcher
    private final ActivityResultLauncher<Uri> cameraLauncher =
        registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && photoUri != null) {
                ivTreePhoto.setImageURI(photoUri);
                ivTreePhoto.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Photo captured!", Toast.LENGTH_SHORT).show();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tree_log);

        tvTotalTrees = findViewById(R.id.tvTotalTrees);
        tvCO2Reduced = findViewById(R.id.tvCO2Reduced);
        tvTreeBadge  = findViewById(R.id.tvTreeBadge);
        pbTrees      = findViewById(R.id.pbTrees);
        ivTreePhoto  = findViewById(R.id.ivTreePhoto);
        tvLocation   = findViewById(R.id.tvLocation);
        btnCapture   = findViewById(R.id.btnCapture);
        btnLogTrees  = findViewById(R.id.btnLogTrees);

        btnCapture.setOnClickListener(v -> checkCameraAndCapture());
        btnLogTrees.setOnClickListener(v -> logTree());
        findViewById(R.id.btnBackTree).setOnClickListener(v -> finish());

        requestPermissionsAndLocation();
        refreshStats();
        setupNav();
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private void requestPermissionsAndLocation() {
        String[] needed = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.SEND_SMS,
        };
        boolean allGranted = true;
        for (String p : needed) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (allGranted) {
            fetchLocation();
        } else {
            ActivityCompat.requestPermissions(this, needed, PERM_REQ);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == PERM_REQ) fetchLocation();
    }

    // ── Location via standard LocationManager (no Play Services needed) ───────

    private void fetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            tvLocation.setText("📍 Location permission denied");
            tvLocation.setVisibility(View.VISIBLE);
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location loc = null;

        // Try GPS first, fall back to network
        if (lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (loc == null && lm != null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        if (loc != null) {
            currentLat = loc.getLatitude();
            currentLon = loc.getLongitude();
            tvLocation.setText(String.format(Locale.getDefault(),
                "📍 %.5f, %.5f", currentLat, currentLon));
        } else {
            // Emulator fallback — use a default coordinate so logging still works
            currentLat = 19.9975;
            currentLon = 73.7898;
            tvLocation.setText("📍 Using default location (enable GPS for accuracy)");
        }
        tvLocation.setVisibility(View.VISIBLE);
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private void checkCameraAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, PERM_REQ);
            return;
        }
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
            File dir  = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File file = File.createTempFile("TREE_" + stamp, ".jpg", dir);
            photoUri  = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", file);
            cameraLauncher.launch(photoUri);
        } catch (IOException e) {
            Toast.makeText(this, "Cannot open camera", Toast.LENGTH_SHORT).show();
        }
    }

    // ── Log tree ──────────────────────────────────────────────────────────────

    private void logTree() {
        if (photoUri == null) {
            Toast.makeText(this, "Please capture or upload a photo first",
                Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String user    = prefs.getString("loggedInUser", "");
        int existing   = prefs.getInt("trees_" + user, 0);
        int newTotal   = existing + 1;
        int points     = prefs.getInt("points_" + user, 0) + 50;

        prefs.edit()
            .putInt("trees_"   + user, newTotal)
            .putInt("points_"  + user, points)
            .putFloat("tree_lat_" + user + "_" + existing, (float) currentLat)
            .putFloat("tree_lon_" + user + "_" + existing, (float) currentLon)
            .apply();

        // Update territorial leaderboard
        String area      = prefs.getString("userArea", "Mumbai");
        String namesRaw  = prefs.getString("names_" + area, "");
        if (!namesRaw.contains(user)) {
            namesRaw = namesRaw.isEmpty() ? user : namesRaw + "," + user;
            prefs.edit().putString("names_" + area, namesRaw).apply();
        }
        prefs.edit().putInt("score_" + area + "_" + user, newTotal).apply();

        photoUri = null;
        ivTreePhoto.setVisibility(View.GONE);
        Toast.makeText(this, "🌳 Tree logged! +50 pts", Toast.LENGTH_SHORT).show();

        // 1. Send SMS report to NGOs (only if permission granted)
        String reporter = prefs.getString("username", "Anonymous");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            PollutionAlertHelper.sendTreePlantedSmsToNgos(this, currentLat, currentLon, reporter, newTotal);
        } else {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.SEND_SMS}, PERM_REQ);
        }

        // 2. Send appreciation email — look up email by username if userEmail is empty
        String userEmail = prefs.getString("userEmail", "");
        if (userEmail.isEmpty()) {
            userEmail = prefs.getString("email_" + user, "");
        }
        if (!userEmail.isEmpty()) {
            PollutionAlertHelper.sendAppreciationEmail(this, userEmail, reporter, newTotal);
        } else {
            android.util.Log.w("TreeLog", "No email found for user — skipping appreciation email");
        }

        refreshStats();
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    private void refreshStats() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String user  = prefs.getString("loggedInUser", "");
        int total    = prefs.getInt("trees_" + user, 0);
        double co2   = total * TREE_OFFSET;
        int progress = Math.min((total * 100) / TREE_GOAL, 100);

        tvTotalTrees.setText(String.format(Locale.getDefault(), "Total trees planted: %d", total));
        tvCO2Reduced.setText(String.format(Locale.getDefault(), "You reduced %.1f kg CO₂ so far!", co2));
        pbTrees.setProgress(progress);

        String badge;
        if      (total >= 20) badge = "🌳 Forest Guardian";
        else if (total >= 10) badge = "🌿 Green Champion";
        else if (total >= 5)  badge = "🌱 Eco Warrior";
        else if (total >= 1)  badge = "🪴 Tree Planter";
        else                  badge = "";

        if (!badge.isEmpty()) {
            tvTreeBadge.setVisibility(View.VISIBLE);
            tvTreeBadge.setText(badge);
        } else {
            tvTreeBadge.setVisibility(View.GONE);
        }
    }

    // ── Nav ───────────────────────────────────────────────────────────────────

    private void setupNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        findViewById(R.id.navScan).setOnClickListener(v ->
            startActivity(new Intent(this, ScanActivity.class)));
        findViewById(R.id.navTrack).setOnClickListener(v ->
            startActivity(new Intent(this, TrackActivity.class)));
        findViewById(R.id.navTerritory).setOnClickListener(v ->
            startActivity(new Intent(this, TerritoryActivity.class)));
        findViewById(R.id.navNGOs).setOnClickListener(v ->
            startActivity(new Intent(this, NGOsActivity.class)));
        findViewById(R.id.navProfile).setOnClickListener(v ->
            startActivity(new Intent(this, ProfileActivity.class)));
    }
}
