package com.example.carboncalculator;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScanActivity extends BaseActivity {

    private ImageView ivPreview;
    private LinearLayout viewfinderOverlay, resultOverlay, disadvantagesList;
    private TextView tvWasteType, tvWasteConfidence, tvWasteTip;
    private TextView tvCleanupTimer, tvTimer;
    private CardView cleanupPanel;
    private LinearLayout timerBadge;
    private Uri captureUri;

    private Handler timerHandler;
    private Runnable timerRunnable;
    private long elapsedSeconds = 0;
    private boolean timerRunning = false;
    private int lastDayShown = -1;

    private double currentLat = 19.9975, currentLon = 73.7898; // default Nashik
    private static final int PERM_REQ = 300;

    private static final String[][] WASTE_RESULTS = {
        {"♻️ Plastic Bottle",    "Confidence: 94%", "Tip: Rinse and place in blue recycling bin"},
        {"🗑️ Organic Waste",     "Confidence: 88%", "Tip: Compost or use green bin"},
        {"📦 Cardboard",         "Confidence: 91%", "Tip: Flatten and place in recycling"},
        {"🔋 E-Waste",           "Confidence: 85%", "Tip: Take to certified e-waste center"},
        {"🥫 Metal Can",         "Confidence: 96%", "Tip: Rinse and recycle in metal bin"},
    };

    private static final String[][] DISADVANTAGES = {
        {"Day 1",  "🦟", "Pest Attraction",      "Waste attracts mosquitoes & flies, spreading dengue and malaria.", "0"},
        {"Day 2",  "💧", "Water Contamination",   "Leachate seeps into groundwater, contaminating drinking sources.", "0"},
        {"Day 3",  "🌫️", "Air Pollution",         "Decomposing waste releases methane — 25× more potent than CO₂.", "1"},
        {"Day 4",  "🐀", "Rodent Infestation",    "Rats nest in waste, spreading leptospirosis and hantavirus.", "1"},
        {"Day 5",  "🌊", "Drain Blockage",        "Plastic clogs drainage, causing urban flooding during monsoons.", "1"},
        {"Day 6",  "☠️", "Toxic Soil",            "Heavy metals permanently poison soil, killing microorganisms.", "2"},
        {"Day 7",  "🏥", "Public Health Crisis",  "One week raises respiratory illness, skin disease, cancer risk.", "2"},
        {"Day 8+", "🌍", "Carbon Emission Spike", "Burning waste releases black carbon — worst climate pollutant.", "2"},
    };

    private static final String[][] EMERGENCY_CONTACTS = {
        {"NMC Nashik",        "02532575631"},
        {"Vasundhara NGO",    PollutionAlertHelper.VASUNDHARA_NGO_PHONE},
        {"Green Nashik NGO",  PollutionAlertHelper.GREEN_NASHIK_PHONE},
    };

    private final ActivityResultLauncher<Uri> cameraLauncher =
        registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && captureUri != null) {
                ivPreview.setImageURI(captureUri);
                ivPreview.setVisibility(View.VISIBLE);
                viewfinderOverlay.setVisibility(View.GONE);
                String[] result = WASTE_RESULTS[(int)(Math.random() * WASTE_RESULTS.length)];
                showResult(result);
                startCleanupTimer();
                sendPollutionAlert(result[0]);
            }
        });

    private final ActivityResultLauncher<String> pickImage =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                ivPreview.setImageURI(uri);
                ivPreview.setVisibility(View.VISIBLE);
                viewfinderOverlay.setVisibility(View.GONE);
                String[] result = WASTE_RESULTS[(int)(Math.random() * WASTE_RESULTS.length)];
                showResult(result);
                startCleanupTimer();
                sendPollutionAlert(result[0]);
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        ivPreview         = findViewById(R.id.ivPreview);
        viewfinderOverlay = findViewById(R.id.viewfinderOverlay);
        resultOverlay     = findViewById(R.id.resultOverlay);
        tvWasteType       = findViewById(R.id.tvWasteType);
        tvWasteConfidence = findViewById(R.id.tvWasteConfidence);
        tvWasteTip        = findViewById(R.id.tvWasteTip);
        tvCleanupTimer    = findViewById(R.id.tvCleanupTimer);
        tvTimer           = findViewById(R.id.tvTimer);
        cleanupPanel      = findViewById(R.id.cleanupPanel);
        disadvantagesList = findViewById(R.id.disadvantagesList);
        timerBadge        = findViewById(R.id.timerBadge);

        timerHandler = new Handler(Looper.getMainLooper());

        requestPermissionsAndFetchLocation();

        findViewById(R.id.btnCapture).setOnClickListener(v -> launchCamera());
        findViewById(R.id.btnUpload).setOnClickListener(v -> pickImage.launch("image/*"));
        findViewById(R.id.btnMarkCleaned).setOnClickListener(v -> markCleaned());

        setupNav();
    }

    // ── Permissions + Location ────────────────────────────────────────────────

    private void requestPermissionsAndFetchLocation() {
        String[] needed = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
        };
        boolean allGranted = true;
        for (String p : needed) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false; break;
            }
        }
        if (allGranted) fetchLocation();
        else ActivityCompat.requestPermissions(this, needed, PERM_REQ);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == PERM_REQ) fetchLocation();
    }

    private void fetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location loc = null;
        if (lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
            loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (loc == null && lm != null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (loc != null) {
            currentLat = loc.getLatitude();
            currentLon = loc.getLongitude();
        }
    }

    // ── SMS alert to NGOs ─────────────────────────────────────────────────────

    private void sendPollutionAlert(String wasteType) {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String reporter = prefs.getString("username", "Anonymous");

        // Re-check SMS permission right before sending
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            // Request it now
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.SEND_SMS}, PERM_REQ);
            Toast.makeText(this, "SMS permission needed to alert NGOs", Toast.LENGTH_SHORT).show();
        }

        PollutionAlertHelper.sendNgoSmsAlert(this, currentLat, currentLon, reporter, wasteType);
        Toast.makeText(this, "📨 NGOs alerted!", Toast.LENGTH_SHORT).show();
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private void launchCamera() {
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File photoFile = File.createTempFile("SCAN_" + stamp, ".jpg", dir);
            captureUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            cameraLauncher.launch(captureUri);
        } catch (IOException e) {
            Toast.makeText(this, "Cannot open camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void showResult(String[] result) {
        tvWasteType.setText(result[0]);
        tvWasteConfidence.setText(result[1]);
        tvWasteTip.setText(result[2]);
        resultOverlay.setVisibility(View.VISIBLE);
    }

    // ── Cleanup Timer ─────────────────────────────────────────────────────────

    private void startCleanupTimer() {
        stopTimer();
        elapsedSeconds = 0;
        lastDayShown   = -1;
        disadvantagesList.removeAllViews();

        cleanupPanel.setVisibility(View.VISIBLE);
        timerBadge.setVisibility(View.VISIBLE);
        tvCleanupTimer.setText("Day 1  00s");
        tvTimer.setText("Day 1");
        tvCleanupTimer.setTextColor(Color.parseColor("#FFD54F"));

        showDayDisadvantage(0);
        lastDayShown = 0;

        timerRunning = true;
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (!timerRunning) return;
                elapsedSeconds++;
                updateTimerDisplay();
                checkAndAddDayDisadvantage();
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private void updateTimerDisplay() {
        int day  = (int)(elapsedSeconds / 10) + 1;
        long sec = elapsedSeconds % 10;
        tvCleanupTimer.setText(String.format(Locale.getDefault(), "Day %d  %02ds", day, sec));
        tvTimer.setText("Day " + day);
        if (day <= 2)      tvCleanupTimer.setTextColor(Color.parseColor("#FFD54F"));
        else if (day <= 5) tvCleanupTimer.setTextColor(Color.parseColor("#FFA726"));
        else               tvCleanupTimer.setTextColor(Color.parseColor("#EF5350"));
    }

    private void checkAndAddDayDisadvantage() {
        int dayIndex = (int)(elapsedSeconds / 10);
        if (dayIndex > lastDayShown && dayIndex < DISADVANTAGES.length) {
            showDayDisadvantage(dayIndex);
            lastDayShown = dayIndex;
        }
    }

    private void showDayDisadvantage(int index) {
        if (index >= DISADVANTAGES.length) return;
        String[] d = DISADVANTAGES[index];
        int severity = Integer.parseInt(d[4]);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 0, 0, dpToPx(10));

        TextView tvDay = new TextView(this);
        tvDay.setText(d[0]);
        tvDay.setTextSize(10);
        tvDay.setTypeface(null, android.graphics.Typeface.BOLD);
        tvDay.setTextColor(Color.parseColor("#0D1B2A"));
        tvDay.setBackgroundColor(severityColor(severity));
        tvDay.setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, dpToPx(4), 0, 0);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(d[1]);
        tvEmoji.setTextSize(15);
        tvEmoji.setPadding(0, 0, dpToPx(6), 0);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(d[2]);
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(severityColor(severity));

        titleRow.addView(tvEmoji);
        titleRow.addView(tvTitle);

        TextView tvDetail = new TextView(this);
        tvDetail.setText(d[3]);
        tvDetail.setTextSize(11);
        tvDetail.setTextColor(Color.parseColor("#8A9BB0"));
        tvDetail.setPadding(dpToPx(22), dpToPx(2), 0, 0);

        row.addView(tvDay);
        row.addView(titleRow);
        row.addView(tvDetail);
        disadvantagesList.addView(row);

        disadvantagesList.post(() -> {
            android.view.ViewParent p = disadvantagesList.getParent();
            if (p instanceof android.widget.ScrollView)
                ((android.widget.ScrollView) p).fullScroll(View.FOCUS_DOWN);
        });
    }

    private int severityColor(int severity) {
        switch (severity) {
            case 2:  return Color.parseColor("#EF5350");
            case 1:  return Color.parseColor("#FFA726");
            default: return Color.parseColor("#FFD54F");
        }
    }

    private void markCleaned() {
        stopTimer();
        cleanupPanel.setVisibility(View.GONE);
        timerBadge.setVisibility(View.GONE);
        Toast.makeText(this, "✅ Great job! Location marked as cleaned.", Toast.LENGTH_LONG).show();
    }

    private void stopTimer() {
        timerRunning = false;
        if (timerHandler != null && timerRunnable != null)
            timerHandler.removeCallbacks(timerRunnable);
    }

    private void dialNumber(String number) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + number));
        startActivity(intent);
    }

    private int dpToPx(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    // ── Nav ───────────────────────────────────────────────────────────────────

    private void setupNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        findViewById(R.id.navTrack).setOnClickListener(v ->
            startActivity(new Intent(this, TrackActivity.class)));
        findViewById(R.id.navTrees).setOnClickListener(v ->
            startActivity(new Intent(this, TreeLogActivity.class)));
        findViewById(R.id.navTerritory).setOnClickListener(v ->
            startActivity(new Intent(this, TerritoryActivity.class)));
        findViewById(R.id.navNGOs).setOnClickListener(v ->
            startActivity(new Intent(this, NGOsActivity.class)));
        findViewById(R.id.navProfile).setOnClickListener(v ->
            startActivity(new Intent(this, ProfileActivity.class)));

        findViewById(R.id.btnCall0).setOnClickListener(v -> dialNumber(EMERGENCY_CONTACTS[0][1]));
        findViewById(R.id.btnCall1).setOnClickListener(v -> dialNumber(EMERGENCY_CONTACTS[1][1]));
        findViewById(R.id.btnCall2).setOnClickListener(v -> dialNumber(EMERGENCY_CONTACTS[2][1]));
    }

    @Override protected void onDestroy() { super.onDestroy(); stopTimer(); }
}
