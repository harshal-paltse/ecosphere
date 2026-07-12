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
import android.widget.EditText;
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

public class RiverMonitorActivity extends BaseActivity {

    private static final int PERM_REQ = 500;

    private ImageView ivRiverPhoto;
    private LinearLayout riverPhotoPlaceholder;
    private TextView tvRiverLocation, tvTimer, tvTimerStatus, tvNoReports;
    private EditText etRiverDescription;
    private CardView timerCard;
    private LinearLayout reportsContainer;

    private Uri photoUri;
    private double currentLat = 19.9975, currentLon = 73.7898;
    private String currentAddress = "Nashik, Maharashtra";

    private Handler timerHandler;
    private Runnable timerRunnable;
    private long elapsedSeconds = 0;
    private boolean timerRunning = false;

    // Camera launcher
    private final ActivityResultLauncher<Uri> cameraLauncher =
        registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && photoUri != null) {
                ivRiverPhoto.setImageURI(photoUri);
                ivRiverPhoto.setVisibility(View.VISIBLE);
                riverPhotoPlaceholder.setVisibility(View.GONE);
                Toast.makeText(this, "Photo captured!", Toast.LENGTH_SHORT).show();
            }
        });

    // Gallery launcher
    private final ActivityResultLauncher<String> galleryLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                photoUri = uri;
                ivRiverPhoto.setImageURI(uri);
                ivRiverPhoto.setVisibility(View.VISIBLE);
                riverPhotoPlaceholder.setVisibility(View.GONE);
                Toast.makeText(this, "Photo selected!", Toast.LENGTH_SHORT).show();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_river_monitor);

        ivRiverPhoto          = findViewById(R.id.ivRiverPhoto);
        riverPhotoPlaceholder = findViewById(R.id.riverPhotoPlaceholder);
        tvRiverLocation       = findViewById(R.id.tvRiverLocation);
        tvTimer               = findViewById(R.id.tvTimer);
        tvTimerStatus         = findViewById(R.id.tvTimerStatus);
        tvNoReports           = findViewById(R.id.tvNoReports);
        etRiverDescription    = findViewById(R.id.etRiverDescription);
        timerCard             = findViewById(R.id.timerCard);
        reportsContainer      = findViewById(R.id.reportsContainer);

        timerHandler = new Handler(Looper.getMainLooper());

        findViewById(R.id.btnRiverClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnCaptureRiver).setOnClickListener(v -> capturePhoto());
        findViewById(R.id.btnGalleryRiver).setOnClickListener(v -> galleryLauncher.launch("image/*"));
        findViewById(R.id.btnReportRiver).setOnClickListener(v -> submitReport());
        findViewById(R.id.btnMarkResolved).setOnClickListener(v -> markResolved());

        requestPermissionsAndLocation();
        loadPastReports();
        resumeActiveTimer();
    }

    // ── Permissions + Location ────────────────────────────────────────────────

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
                != PackageManager.PERMISSION_GRANTED) {
            tvRiverLocation.setText("📍 Location permission denied");
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location loc = null;
        if (lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
            loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (loc == null && lm != null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (loc != null) {
            currentLat = loc.getLatitude();
            currentLon = loc.getLongitude();
            currentAddress = String.format(Locale.getDefault(), "%.5f, %.5f", currentLat, currentLon);
        }
        tvRiverLocation.setText("📍 " + currentAddress
            + "\n🗺 maps.google.com/?q=" + String.format(Locale.US, "%.5f,%.5f", currentLat, currentLon));
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private void capturePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERM_REQ);
            return;
        }
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File dir  = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File file = File.createTempFile("RIVER_" + stamp, ".jpg", dir);
            photoUri  = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            cameraLauncher.launch(photoUri);
        } catch (IOException e) {
            Toast.makeText(this, "Cannot open camera", Toast.LENGTH_SHORT).show();
        }
    }

    // ── Submit report ─────────────────────────────────────────────────────────

    private void submitReport() {
        if (photoUri == null) {
            Toast.makeText(this, "Please capture or upload a photo first", Toast.LENGTH_SHORT).show();
            return;
        }
        String desc = etRiverDescription.getText().toString().trim();
        if (desc.isEmpty()) desc = "River pollution reported";

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String reporter  = prefs.getString("username", "Anonymous");
        String mapsLink  = String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", currentLat, currentLon);
        String timestamp = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());
        long   sentAt    = System.currentTimeMillis();

        String smsMsg = "RIVER POLLUTION ALERT\n"
            + "Reporter: " + reporter + "\n"
            + "Location: " + currentAddress + "\n"
            + "Details: " + desc + "\n"
            + "Map: " + mapsLink + "\n"
            + "Please take immediate action. - Carbon Calculator App";

        sendSmsToAll(smsMsg);

        // Save report: key = river_report_<millis>, value = timestamp|desc|address|sentAt|active
        String reportKey = "river_report_" + sentAt;
        prefs.edit()
            .putString(reportKey, timestamp + "|" + desc + "|" + currentAddress + "|" + sentAt + "|active")
            .apply();

        timerCard.setVisibility(View.VISIBLE);
        startTimer(sentAt);
        addReportToList(timestamp, desc, currentAddress, sentAt, "active", reportKey);
        tvNoReports.setVisibility(View.GONE);

        Toast.makeText(this, "Report sent to NGO & NMC!", Toast.LENGTH_LONG).show();

        etRiverDescription.setText("");
        photoUri = null;
        ivRiverPhoto.setVisibility(View.GONE);
        riverPhotoPlaceholder.setVisibility(View.VISIBLE);
    }

    // ── SMS ───────────────────────────────────────────────────────────────────

    public static void sendSmsToNumbers(Context context, String msg) {
        String[] numbers = {
            "02532575631",
            PollutionAlertHelper.VASUNDHARA_NGO_PHONE,
            PollutionAlertHelper.GREEN_NASHIK_PHONE,
        };
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                android.telephony.SmsManager sms =
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                        ? context.getSystemService(android.telephony.SmsManager.class)
                        : android.telephony.SmsManager.getDefault();
                if (sms != null) {
                    java.util.ArrayList<String> parts = sms.divideMessage(msg);
                    for (String number : numbers)
                        sms.sendMultipartTextMessage(number, null, parts, null, null);
                }
            } catch (Exception e) {
                android.util.Log.e("RiverMonitor", "SMS failed: " + e.getMessage());
            }
        } else {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:" + PollutionAlertHelper.VASUNDHARA_NGO_PHONE));
            intent.putExtra("sms_body", msg);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    private void sendSmsToAll(String msg) { sendSmsToNumbers(this, msg); }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private void startTimer(long sentAtMillis) {
        stopTimer();
        timerRunning = true;
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (!timerRunning) return;
                elapsedSeconds = (System.currentTimeMillis() - sentAtMillis) / 1000;
                updateTimerDisplay();
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void updateTimerDisplay() {
        long h = elapsedSeconds / 3600;
        long m = (elapsedSeconds % 3600) / 60;
        long s = elapsedSeconds % 60;
        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));
        if (elapsedSeconds < 1800) {
            tvTimer.setTextColor(Color.parseColor("#1DB954"));
            tvTimerStatus.setText("Report sent — awaiting response");
        } else if (elapsedSeconds < 7200) {
            tvTimer.setTextColor(Color.parseColor("#FFA726"));
            tvTimerStatus.setText("No response yet — escalating...");
        } else {
            tvTimer.setTextColor(Color.parseColor("#EF5350"));
            tvTimerStatus.setText("URGENT — No action taken!");
        }
    }

    private void stopTimer() {
        timerRunning = false;
        if (timerHandler != null && timerRunnable != null)
            timerHandler.removeCallbacks(timerRunnable);
    }

    private void markResolved() {
        stopTimer();
        timerCard.setVisibility(View.GONE);
        // Mark all active reports as resolved
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        for (java.util.Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith("river_report_")) {
                String val = (String) entry.getValue();
                if (val != null && val.endsWith("|active")) {
                    prefs.edit().putString(entry.getKey(), val.replace("|active", "|resolved")).apply();
                }
            }
        }
        Toast.makeText(this, "Issue marked as resolved. Thank you!", Toast.LENGTH_LONG).show();
    }

    // Resume timer if there's an active report from a previous session
    private void resumeActiveTimer() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        for (java.util.Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith("river_report_")) {
                String val = (String) entry.getValue();
                if (val != null && val.endsWith("|active")) {
                    String[] parts = val.split("\\|");
                    if (parts.length >= 5) {
                        try {
                            long sentAt = Long.parseLong(parts[3]);
                            timerCard.setVisibility(View.VISIBLE);
                            startTimer(sentAt);
                            return;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
    }

    // ── Reports list ──────────────────────────────────────────────────────────

    private void addReportToList(String time, String desc, String location,
                                  long sentAt, String status, String reportKey) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(Color.parseColor("#111F2E"));
        int p = dpToPx(12);
        row.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dpToPx(8));
        row.setLayoutParams(lp);

        TextView tvTime = new TextView(this);
        tvTime.setText("🕐 " + time + (status.equals("resolved") ? "  ✅ Resolved" : "  🔴 Active"));
        tvTime.setTextColor(status.equals("resolved") ? Color.parseColor("#1DB954") : Color.parseColor("#FFA726"));
        tvTime.setTextSize(10f);

        TextView tvDesc = new TextView(this);
        tvDesc.setText("🌊 " + desc);
        tvDesc.setTextColor(Color.WHITE);
        tvDesc.setTextSize(13f);

        TextView tvLoc = new TextView(this);
        tvLoc.setText("📍 " + location);
        tvLoc.setTextColor(Color.parseColor("#8A9BB0"));
        tvLoc.setTextSize(11f);

        row.addView(tvTime);
        row.addView(tvDesc);
        row.addView(tvLoc);
        reportsContainer.addView(row, 0);
    }

    private void loadPastReports() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        int count = 0;
        for (java.util.Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith("river_report_")) {
                String val = (String) entry.getValue();
                String[] parts = val.split("\\|");
                if (parts.length >= 3) {
                    long sentAt = parts.length >= 4 ? parseLong(parts[3]) : 0;
                    String status = parts.length >= 5 ? parts[4] : "active";
                    addReportToList(parts[0], parts[1], parts[2], sentAt, status, entry.getKey());
                    count++;
                }
            }
        }
        if (count > 0) tvNoReports.setVisibility(View.GONE);
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }

    private int dpToPx(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
    }
}
