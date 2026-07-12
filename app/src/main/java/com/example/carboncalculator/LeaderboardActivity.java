package com.example.carboncalculator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LeaderboardActivity extends BaseActivity {

    private Spinner spinnerAreaFilter;
    private ListView listLeaderboard;
    private TextView tvEmpty, tvAreaTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        spinnerAreaFilter = findViewById(R.id.spinnerAreaFilter);
        listLeaderboard   = findViewById(R.id.listLeaderboard);
        tvEmpty           = findViewById(R.id.tvEmpty);
        tvAreaTitle       = findViewById(R.id.tvAreaTitle);

        // Pre-select user's area
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String userArea = prefs.getString("userArea", "Mumbai");
        String[] areas = getResources().getStringArray(R.array.area_list);
        int selectedIndex = 0;
        for (int i = 0; i < areas.length; i++) {
            if (areas[i].equals(userArea)) {
                selectedIndex = i;
                break;
            }
        }

        spinnerAreaFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                loadLeaderboard(parent.getItemAtPosition(pos).toString());
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerAreaFilter.setSelection(selectedIndex);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        // loadLeaderboard is triggered by setSelection via the listener above
    }

    private void loadLeaderboard(String area) {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String loggedIn = prefs.getString("loggedInUser", "");

        tvAreaTitle.setText("🗺 " + area + " Territory — Tree Leaderboard");

        String namesRaw = prefs.getString("names_" + area, "");
        if (namesRaw.isEmpty()) {
            listLeaderboard.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        String[] names = namesRaw.split(",");
        List<String[]> entries = new ArrayList<>();
        for (String name : names) {
            if (name.trim().isEmpty()) continue;
            int trees = prefs.getInt("score_" + area + "_" + name, 0);
            String displayName = prefs.getString("name_" + name, name);
            entries.add(new String[]{name, displayName, String.valueOf(trees)});
        }

        Collections.sort(entries, (a, b) -> Integer.parseInt(b[2]) - Integer.parseInt(a[2]));

        List<String> rows = new ArrayList<>();
        String[] medals = {"🥇", "🥈", "🥉"};
        for (int i = 0; i < entries.size(); i++) {
            String medal = i < medals.length ? medals[i] : (i + 1) + ".";
            int trees = Integer.parseInt(entries.get(i)[2]);
            String badge = getBadge(trees);
            String you = entries.get(i)[0].equals(loggedIn) ? " ← You" : "";
            rows.add(String.format("%s  %s  %s  🌳 %d trees%s",
                medal, entries.get(i)[1], badge, trees, you));
        }

        listLeaderboard.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        listLeaderboard.setAdapter(new ArrayAdapter<>(
            this, android.R.layout.simple_list_item_1, rows));
    }

    private String getBadge(int trees) {
        if (trees >= 20) return "🌳";
        if (trees >= 10) return "🌿";
        if (trees >= 5)  return "🌱";
        if (trees >= 1)  return "🪴";
        return "";
    }
}
