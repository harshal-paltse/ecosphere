package com.example.carboncalculator;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
public class TrackActivity extends BaseActivity {

    // Accurate emission factors (kg CO2e per unit) — India-specific / IPCC 2023
    // Car: India avg petrol car ~0.192 kg CO2/km (IPCC AR6)
    // Electricity: India grid emission factor 0.716 kg CO2/kWh (CEA 2022)
    // Meat: avg mixed serving ~3.3 kg CO2e (beef 27 kg/kg, chicken 6.9 kg/kg)
    // Water: 0.000298 kg CO2/litre (indirect heating energy)
    private static final double KG_PER_KM_CAR  = 0.192;
    private static final double KG_PER_KWH     = 0.716;
    private static final double KG_PER_SERVING = 3.3;
    private static final double KG_PER_LITER   = 0.000298;
    // Waste: 0.5 kg CO2 per kg waste (landfill methane, IPCC)
    private static final double KG_PER_KG_WASTE   = 0.5;
    // Shopping: 6.0 kg CO2 per item (avg manufactured goods, lifecycle)
    private static final double KG_PER_ITEM        = 6.0;
    // Digital: 0.036 kg CO2 per hour (streaming + device energy, avg)
    private static final double KG_PER_HOUR_DIGITAL = 0.036;

    private TextView tvFootprintValue, tvImpactLabel;
    private TextView tvTravelKm, tvTravelKg;
    private TextView tvElecKwh, tvElecKg;
    private TextView tvMeatServings, tvMeatKg;
    private TextView tvWaterL, tvWaterKg;
    private TextView tvWasteKgLabel, tvWasteKg;
    private TextView tvShoppingItems, tvShoppingKg;
    private TextView tvDigitalHours, tvDigitalKg;
    private TextView tvWeekAvg;
    private SeekBar seekTravel, seekElectricity, seekMeat, seekWater;
    private SeekBar seekWaste, seekShopping, seekDigital;
    private WeeklyChartView weeklyChart;
    private Button btnCalculate;

    // Today's date key e.g. "co2_2024-04-25"
    private String todayKey;
    private String user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track);

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        user     = prefs.getString("loggedInUser", "guest");
        todayKey = "co2_" + user + "_" + todayDate();

        bindViews();
        setupSliders();
        loadWeeklyChart();
        setupNav();
    }

    private void bindViews() {
        tvFootprintValue = findViewById(R.id.tvFootprintValue);
        tvImpactLabel    = findViewById(R.id.tvImpactLabel);
        tvTravelKm       = findViewById(R.id.tvTravelKm);
        tvTravelKg       = findViewById(R.id.tvTravelKg);
        tvElecKwh        = findViewById(R.id.tvElecKwh);
        tvElecKg         = findViewById(R.id.tvElecKg);
        tvMeatServings   = findViewById(R.id.tvMeatServings);
        tvMeatKg         = findViewById(R.id.tvMeatKg);
        tvWaterL         = findViewById(R.id.tvWaterL);
        tvWaterKg        = findViewById(R.id.tvWaterKg);
        tvWasteKgLabel   = findViewById(R.id.tvWasteKgLabel);
        tvWasteKg        = findViewById(R.id.tvWasteKg);
        tvShoppingItems  = findViewById(R.id.tvShoppingItems);
        tvShoppingKg     = findViewById(R.id.tvShoppingKg);
        tvDigitalHours   = findViewById(R.id.tvDigitalHours);
        tvDigitalKg      = findViewById(R.id.tvDigitalKg);
        seekTravel       = findViewById(R.id.seekTravel);
        seekElectricity  = findViewById(R.id.seekElectricity);
        seekMeat         = findViewById(R.id.seekMeat);
        seekWater        = findViewById(R.id.seekWater);
        seekWaste        = findViewById(R.id.seekWaste);
        seekShopping     = findViewById(R.id.seekShopping);
        seekDigital      = findViewById(R.id.seekDigital);
        weeklyChart      = findViewById(R.id.weeklyChart);
        tvWeekAvg        = findViewById(R.id.tvWeekAvg);
        btnCalculate     = findViewById(R.id.btnCalculate);
    }

    private void setupSliders() {
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { recalculate(); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}  // email only on Calculate button
        };
        seekTravel.setOnSeekBarChangeListener(listener);
        seekElectricity.setOnSeekBarChangeListener(listener);
        seekMeat.setOnSeekBarChangeListener(listener);
        seekWater.setOnSeekBarChangeListener(listener);
        seekWaste.setOnSeekBarChangeListener(listener);
        seekShopping.setOnSeekBarChangeListener(listener);
        seekDigital.setOnSeekBarChangeListener(listener);

        btnCalculate.setOnClickListener(v -> {
            recalculate();
            showResultDialog();
            // Disable button immediately to prevent duplicate emails
            btnCalculate.setEnabled(false);
            btnCalculate.setAlpha(0.5f);
            sendCarbonReportToUser();
            // Re-enable after 5 seconds
            btnCalculate.postDelayed(() -> {
                btnCalculate.setEnabled(true);
                btnCalculate.setAlpha(1f);
            }, 5000);
        });
    }

    private void recalculate() {
        int km       = seekTravel.getProgress();
        int kwh      = seekElectricity.getProgress();
        int servings = seekMeat.getProgress();
        int liters   = seekWater.getProgress();
        int wasteKg  = seekWaste.getProgress();
        int items    = seekShopping.getProgress();
        int hours    = seekDigital.getProgress();

        double travelKg   = km * KG_PER_KM_CAR;
        double elecKg     = kwh * KG_PER_KWH;
        double meatKg     = servings * KG_PER_SERVING;
        double waterKg    = liters * KG_PER_LITER;
        double wasteEmit  = wasteKg * KG_PER_KG_WASTE;
        double shopEmit   = items * KG_PER_ITEM;
        double digitalEmit = hours * KG_PER_HOUR_DIGITAL;
        double total = travelKg + elecKg + meatKg + waterKg + wasteEmit + shopEmit + digitalEmit;

        tvTravelKm.setText(km + " km");
        tvTravelKg.setText(String.format(Locale.getDefault(), "%.1f kg", travelKg));
        tvElecKwh.setText(kwh + " kWh");
        tvElecKg.setText(String.format(Locale.getDefault(), "%.1f kg", elecKg));
        tvMeatServings.setText(servings + " servings");
        tvMeatKg.setText(String.format(Locale.getDefault(), "%.1f kg", meatKg));
        tvWaterL.setText(liters + " L");
        tvWaterKg.setText(String.format(Locale.getDefault(), "%.1f kg", waterKg));
        tvWasteKgLabel.setText(wasteKg + " kg");
        tvWasteKg.setText(String.format(Locale.getDefault(), "%.1f kg", wasteEmit));
        tvShoppingItems.setText(items + " items");
        tvShoppingKg.setText(String.format(Locale.getDefault(), "%.1f kg", shopEmit));
        tvDigitalHours.setText(hours + " hours");
        tvDigitalKg.setText(String.format(Locale.getDefault(), "%.1f kg", digitalEmit));
        tvFootprintValue.setText(String.format(Locale.getDefault(), "%.1f", total));

        if (total < 5) {
            tvImpactLabel.setText("🌿 Low Impact — Great job!");
        } else if (total < 15) {
            tvImpactLabel.setText("⚠️ Moderate Impact — Keep going!");
        } else {
            tvImpactLabel.setText("🔴 High Impact — Try to reduce!");
        }

        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .edit().putFloat(todayKey, (float) total).apply();
        updateStreak();
        loadWeeklyChart();
    }

    // ── Weekly chart ──────────────────────────────────────────────────────────

    private void loadWeeklyChart() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        float[] values = new float[7];
        String[] labels = new String[7];

        // Build last 7 days (index 6 = today)
        long now = System.currentTimeMillis();
        long DAY = 86400000L;
        SimpleDateFormat dateFmt  = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat labelFmt = new SimpleDateFormat("EEE", Locale.ENGLISH); // always 3-char English day

        float sum = 0;
        for (int i = 0; i < 7; i++) {
            long ts   = now - (6 - i) * DAY;
            Date d    = new Date(ts);
            String key = "co2_" + user + "_" + dateFmt.format(d);
            values[i]  = prefs.getFloat(key, 0f);
            labels[i]  = labelFmt.format(d); // Mon, Tue… always 3 chars in Locale.ENGLISH
            sum += values[i];
        }

        weeklyChart.setData(values, labels);

        float avg = sum / 7f;
        tvWeekAvg.setText(String.format(Locale.getDefault(), "Avg: %.1f kg/day", avg));
    }

    private String todayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private void updateStreak() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String lastTracked = prefs.getString("lastTracked_" + user, "");
        String today = todayDate();
        if (today.equals(lastTracked)) return; // already tracked today

        // Check if yesterday was tracked (streak continues)
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
        String yesterday = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

        int streak = prefs.getInt("streak_" + user, 0);
        if (yesterday.equals(lastTracked)) {
            streak++; // consecutive day
        } else if (!today.equals(lastTracked)) {
            streak = 1; // streak broken or first time
        }
        prefs.edit()
            .putInt("streak_" + user, streak)
            .putString("lastTracked_" + user, today)
            .apply();
    }

    // ── Result dialog ─────────────────────────────────────────────────────────

    private void showResultDialog() {
        int km       = seekTravel.getProgress();
        int kwh      = seekElectricity.getProgress();
        int servings = seekMeat.getProgress();
        int liters   = seekWater.getProgress();
        int wasteKg  = seekWaste.getProgress();
        int items    = seekShopping.getProgress();
        int hours    = seekDigital.getProgress();

        double travelKg    = km * KG_PER_KM_CAR;
        double elecKg      = kwh * KG_PER_KWH;
        double meatKg      = servings * KG_PER_SERVING;
        double waterKg     = liters * KG_PER_LITER;
        double wasteEmit   = wasteKg * KG_PER_KG_WASTE;
        double shopEmit    = items * KG_PER_ITEM;
        double digitalEmit = hours * KG_PER_HOUR_DIGITAL;
        double total = travelKg + elecKg + meatKg + waterKg + wasteEmit + shopEmit + digitalEmit;

        String level = total < 5  ? "🌿 Low Impact — Great job!"
                     : total < 15 ? "⚠️ Moderate Impact — Keep going!"
                     : "🔴 High Impact — Try to reduce!";

        double treesNeeded = total / 21.0;

        String msg =
            "Total CO₂: " + String.format(Locale.getDefault(), "%.2f", total) + " kg\n\n"
            + level + "\n\n"
            + "Breakdown:\n"
            + "  🚗 Travel:      " + String.format(Locale.getDefault(), "%.2f", travelKg)  + " kg\n"
            + "  ⚡ Electricity: " + String.format(Locale.getDefault(), "%.2f", elecKg)    + " kg\n"
            + "  🍽 Meat:        " + String.format(Locale.getDefault(), "%.2f", meatKg)    + " kg\n"
            + "  💧 Water:       " + String.format(Locale.getDefault(), "%.2f", waterKg)   + " kg\n"
            + "  🗑 Waste:       " + String.format(Locale.getDefault(), "%.2f", wasteEmit) + " kg\n"
            + "  🛒 Shopping:    " + String.format(Locale.getDefault(), "%.2f", shopEmit)  + " kg\n"
            + "  💻 Digital:     " + String.format(Locale.getDefault(), "%.2f", digitalEmit) + " kg\n\n"
            + "🌳 Plant " + String.format(Locale.getDefault(), "%.1f", treesNeeded)
            + " tree(s) to offset today.\n\n"
            + "📧 Report sent to your email!";

        new AlertDialog.Builder(this)
            .setTitle("Your CO₂ Report")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show();
    }

    // ── Send carbon report email to user ─────────────────────────────────────

    private void sendCarbonReportToUser() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String loggedIn = prefs.getString("loggedInUser", "");
        String userName = prefs.getString("username", "User");
        String email    = prefs.getString("userEmail", "");
        if (email.isEmpty()) email = prefs.getString("email_" + loggedIn, "");
        if (email.isEmpty()) return; // no email saved, skip silently

        int km       = seekTravel.getProgress();
        int kwh      = seekElectricity.getProgress();
        int servings = seekMeat.getProgress();
        int liters   = seekWater.getProgress();
        int wasteKg  = seekWaste.getProgress();
        int items    = seekShopping.getProgress();
        int hours    = seekDigital.getProgress();

        double travelKg    = km * KG_PER_KM_CAR;
        double elecKg      = kwh * KG_PER_KWH;
        double meatKg      = servings * KG_PER_SERVING;
        double waterKg     = liters * KG_PER_LITER;
        double wasteEmit   = wasteKg * KG_PER_KG_WASTE;
        double shopEmit    = items * KG_PER_ITEM;
        double digitalEmit = hours * KG_PER_HOUR_DIGITAL;
        double total = travelKg + elecKg + meatKg + waterKg + wasteEmit + shopEmit + digitalEmit;

        if (total <= 0) return; // nothing tracked yet

        PollutionAlertHelper.sendCarbonReportEmail(this, email, userName,
            total, travelKg, elecKg, meatKg, waterKg, wasteEmit, shopEmit, digitalEmit);
    }

    // ── Nav ───────────────────────────────────────────────────────────────────

    private void setupNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        findViewById(R.id.navScan).setOnClickListener(v ->
            startActivity(new Intent(this, ScanActivity.class)));
        findViewById(R.id.navTrees).setOnClickListener(v ->
            startActivity(new Intent(this, TreeLogActivity.class)));
        findViewById(R.id.navTerritory).setOnClickListener(v ->
            startActivity(new Intent(this, TerritoryActivity.class)));
        findViewById(R.id.navNGOs).setOnClickListener(v ->
            startActivity(new Intent(this, NGOsActivity.class)));
        findViewById(R.id.navProfile).setOnClickListener(v ->
            startActivity(new Intent(this, ProfileActivity.class)));
    }
}
