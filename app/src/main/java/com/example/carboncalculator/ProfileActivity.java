package com.example.carboncalculator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProfileActivity extends BaseActivity {

    private LinearLayout riverReportsContainer;
    private TextView tvNoRiverReports;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final List<Runnable> timerRunnables = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        riverReportsContainer = findViewById(R.id.riverReportsContainer);
        tvNoRiverReports      = findViewById(R.id.tvNoRiverReports);

        loadProfile();
        loadRiverReports();
        setupButtons();
        setupNav();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (Runnable r : timerRunnables) timerHandler.removeCallbacks(r);
    }

    private void loadProfile() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String loggedIn = prefs.getString("loggedInUser", "");
        String name     = prefs.getString("username", "User");
        String area     = prefs.getString("userArea", "Mumbai");
        int trees       = prefs.getInt("trees_" + loggedIn, 0);
        int points      = prefs.getInt("points_" + loggedIn, 0);

        ((TextView) findViewById(R.id.tvProfileName)).setText(name);
        ((TextView) findViewById(R.id.tvProfileUsername)).setText("@" + loggedIn);
        ((TextView) findViewById(R.id.tvProfileArea)).setText("📍 " + area);
        ((TextView) findViewById(R.id.tvProfileTrees)).setText(String.valueOf(trees));
        ((TextView) findViewById(R.id.tvProfilePoints)).setText(String.valueOf(points));

        String emoji, label;
        if      (trees >= 20) { emoji = "🌳"; label = "Forest Guardian"; }
        else if (trees >= 10) { emoji = "🌿"; label = "Green Champion"; }
        else if (trees >= 5)  { emoji = "🌱"; label = "Eco Warrior"; }
        else if (trees >= 1)  { emoji = "🪴"; label = "Tree Planter"; }
        else                  { emoji = "🌍"; label = "Eco Starter"; }

        ((TextView) findViewById(R.id.tvProfileBadge)).setText(emoji);
        ((TextView) findViewById(R.id.tvProfileBadgeLabel)).setText(label);
    }

    private void loadRiverReports() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String reporter  = prefs.getString("username", "Anonymous");
        int count = 0;

        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith("river_report_")) continue;
            String val = (String) entry.getValue();
            if (val == null) continue;
            String[] parts = val.split("\\|");
            if (parts.length < 5) continue;

            String time     = parts[0];
            String desc     = parts[1];
            String location = parts[2];
            long   sentAt   = parseLong(parts[3]);
            String status   = parts[4];
            String reportKey = entry.getKey();

            addReportCard(reporter, time, desc, location, sentAt, status, reportKey, prefs);
            count++;
        }

        if (count > 0) tvNoRiverReports.setVisibility(View.GONE);
    }

    private void addReportCard(String reporter, String time, String desc, String location,
                                long sentAt, String status, String reportKey, SharedPreferences prefs) {
        boolean active = status.equals("active");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#111F2E"));
        int p = dp(12);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);

        // Status + time row
        TextView tvStatus = new TextView(this);
        tvStatus.setText((active ? "🔴 Active" : "✅ Resolved") + "  •  " + time);
        tvStatus.setTextColor(active ? Color.parseColor("#FFA726") : Color.parseColor("#1DB954"));
        tvStatus.setTextSize(11f);
        card.addView(tvStatus);

        // Description
        TextView tvDesc = new TextView(this);
        tvDesc.setText("🌊 " + desc);
        tvDesc.setTextColor(Color.WHITE);
        tvDesc.setTextSize(13f);
        tvDesc.setPadding(0, dp(4), 0, 0);
        card.addView(tvDesc);

        // Location
        TextView tvLoc = new TextView(this);
        tvLoc.setText("📍 " + location);
        tvLoc.setTextColor(Color.parseColor("#8A9BB0"));
        tvLoc.setTextSize(11f);
        tvLoc.setPadding(0, dp(2), 0, 0);
        card.addView(tvLoc);

        // Live timer (only for active reports)
        Runnable[] cardTimerRef = {null}; // mutable ref so Done button can cancel it
        if (active && sentAt > 0) {
            TextView tvTimer = new TextView(this);
            tvTimer.setTextSize(13f);
            tvTimer.setTypeface(android.graphics.Typeface.MONOSPACE);
            tvTimer.setPadding(0, dp(6), 0, 0);
            card.addView(tvTimer);

            cardTimerRef[0] = new Runnable() {
                @Override public void run() {
                    long elapsed = (System.currentTimeMillis() - sentAt) / 1000;
                    long h = elapsed / 3600, m = (elapsed % 3600) / 60, s = elapsed % 60;
                    tvTimer.setText(String.format(Locale.getDefault(), "⏱ %02d:%02d:%02d since report", h, m, s));
                    if (elapsed < 1800)       tvTimer.setTextColor(Color.parseColor("#1DB954"));
                    else if (elapsed < 7200)  tvTimer.setTextColor(Color.parseColor("#FFA726"));
                    else                      tvTimer.setTextColor(Color.parseColor("#EF5350"));
                    timerHandler.postDelayed(this, 1000);
                }
            };
            timerRunnables.add(cardTimerRef[0]);
            timerHandler.post(cardTimerRef[0]);
        }

        // Remind + Done buttons row (only for active reports)
        if (active) {
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, dp(8), 0, 0);
            btnRow.setLayoutParams(rowLp);

            // Remind button
            com.google.android.material.button.MaterialButton btnRemind =
                new com.google.android.material.button.MaterialButton(this);
            btnRemind.setText("🔔 Remind");
            btnRemind.setTextSize(12f);
            btnRemind.setTextColor(Color.WHITE);
            btnRemind.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#1565C0")));
            LinearLayout.LayoutParams remindLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            remindLp.setMargins(0, 0, dp(8), 0);
            btnRemind.setLayoutParams(remindLp);
            btnRemind.setCornerRadius(dp(19));
            btnRemind.setOnClickListener(v -> {
                String mapsLink = String.format(Locale.US,
                    "https://maps.google.com/?q=%s", location.replace(", ", ","));
                String username = prefs.getString("username", "Anonymous");
                String smsMsg = "REMINDER: RIVER POLLUTION ALERT\n"
                    + "Reporter: " + username + "\n"
                    + "Location: " + location + "\n"
                    + "Details: " + desc + "\n"
                    + "Map: " + mapsLink + "\n"
                    + "This issue has NOT been resolved yet. Please take immediate action. - Carbon Calculator App";
                RiverMonitorActivity.sendSmsToNumbers(this, smsMsg);
                Toast.makeText(this, "Reminder sent to NMC & NGO!", Toast.LENGTH_SHORT).show();
            });

            // Done button
            com.google.android.material.button.MaterialButton btnDone =
                new com.google.android.material.button.MaterialButton(this);
            btnDone.setText("✅ Done");
            btnDone.setTextSize(12f);
            btnDone.setTextColor(Color.WHITE);
            btnDone.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32")));
            LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            btnDone.setLayoutParams(doneLp);
            btnDone.setCornerRadius(dp(19));
            btnDone.setOnClickListener(v -> {
                // Stop this card's timer
                if (cardTimerRef[0] != null) {
                    timerHandler.removeCallbacks(cardTimerRef[0]);
                    timerRunnables.remove(cardTimerRef[0]);
                    cardTimerRef[0] = null;
                }
                // Mark resolved in SharedPreferences
                String oldVal = prefs.getString(reportKey, "");
                if (oldVal.endsWith("|active")) {
                    prefs.edit()
                        .putString(reportKey, oldVal.replace("|active", "|resolved"))
                        .apply();
                }
                // Update status label
                tvStatus.setText("✅ Resolved  •  " + time);
                tvStatus.setTextColor(Color.parseColor("#1DB954"));
                // Hide both buttons
                btnRow.setVisibility(View.GONE);
                Toast.makeText(this, "Report marked as resolved!", Toast.LENGTH_SHORT).show();
            });

            btnRow.addView(btnRemind);
            btnRow.addView(btnDone);
            card.addView(btnRow);
        }

        riverReportsContainer.addView(card, 0);
    }

    private void setupButtons() {
        updateLanguageButtonLabel();
        findViewById(R.id.btnLanguage).setOnClickListener(v -> toggleLanguage());

        findViewById(R.id.btnViewLeaderboard).setOnClickListener(v ->
            startActivity(new Intent(this, LeaderboardActivity.class)));

        findViewById(R.id.btnLogout).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out", (d, w) -> logout())
                .setNegativeButton("Cancel", null)
                .show());
    }

    private void updateLanguageButtonLabel() {
        String current = LocaleHelper.getPersistedLocale(this);
        String label;
        switch (current) {
            case "mr": label = "🌐  Switch to Hindi / हिंदी"; break;
            case "hi": label = "🌐  Switch to English"; break;
            default:   label = "🌐  Switch to Marathi / मराठी"; break;
        }
        ((com.google.android.material.button.MaterialButton) findViewById(R.id.btnLanguage)).setText(label);
    }

    private void toggleLanguage() {
        String current = LocaleHelper.getPersistedLocale(this);
        String next;
        switch (current) {
            case "en": next = "mr"; break;
            case "mr": next = "hi"; break;
            default:   next = "en"; break;
        }
        LocaleHelper.setLocale(this, next);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void logout() {
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .edit().remove("loggedInUser").apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void setupNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        findViewById(R.id.navScan).setOnClickListener(v ->
            startActivity(new Intent(this, ScanActivity.class)));
        findViewById(R.id.navTrack).setOnClickListener(v ->
            startActivity(new Intent(this, TrackActivity.class)));
        findViewById(R.id.navTrees).setOnClickListener(v ->
            startActivity(new Intent(this, TreeLogActivity.class)));
        findViewById(R.id.navTerritory).setOnClickListener(v ->
            startActivity(new Intent(this, TerritoryActivity.class)));
        findViewById(R.id.navNGOs).setOnClickListener(v ->
            startActivity(new Intent(this, NGOsActivity.class)));
        findViewById(R.id.navProfile).setOnClickListener(v -> { /* already here */ });
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }

    private int dp(int val) {
        return (int)(val * getResources().getDisplayMetrics().density);
    }
}
