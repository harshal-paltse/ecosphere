package com.example.carboncalculator;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class NGOsActivity extends BaseActivity {

    static class NGO {
        String name, city, phone, email;
        int projects, volunteers;
        String rating;

        NGO(String n, String c, int p, int v, String r, String phone, String email) {
            name = n; city = c; projects = p; volunteers = v; rating = r;
            this.phone = phone; this.email = email;
        }
    }

    private static final NGO[] LOCAL_NGOS = {
        new NGO("Green Foundation",   "Nashik", 25, 120, "4.8", "7058379806", "green.foundation@gmail.com"),
        new NGO("Earth Protectors",   "Pune",   18,  85, "4.5", "9876543210", "earth.protectors@gmail.com"),
        new NGO("Clean Rivers India", "Mumbai", 32, 200, "4.7", "8329077911", "cleanrivers@gmail.com"),
        new NGO("Eco Warriors",       "Nagpur", 12,  60, "4.3", "9123456780", "ecowarriors@gmail.com"),
    };

    private static final NGO[] ECO_LEAGUES = {
        new NGO("Green Champions League", "Pan India", 50, 500, "4.9", "9000000001", "greenchampions@gmail.com"),
        new NGO("Carbon Zero Alliance",   "Delhi",     40, 320, "4.6", "9000000002", "carbonzero@gmail.com"),
    };

    private static final NGO[] SUPPORT = {
        new NGO("Climate Help Desk", "Online", 10, 30, "4.8", "9000000003", "climatehelp@gmail.com"),
        new NGO("Eco Counselors",    "Pune",    8, 20, "4.4", "9000000004", "ecocounselors@gmail.com"),
    };

    private TextView tabLocal, tabLeagues, tabSupport;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngos);

        tabLocal   = findViewById(R.id.tabLocalNGOs);
        tabLeagues = findViewById(R.id.tabEcoLeagues);
        tabSupport = findViewById(R.id.tabSupport);
        container  = findViewById(R.id.ngoListContainer);

        tabLocal.setOnClickListener(v -> switchTab(0));
        tabLeagues.setOnClickListener(v -> switchTab(1));
        tabSupport.setOnClickListener(v -> switchTab(2));

        switchTab(0);
        setupNav();
    }

    private void switchTab(int tab) {
        int green = getResources().getColor(R.color.greenNavActive, null);
        int gray  = getResources().getColor(R.color.textGray, null);

        tabLocal.setTextColor(tab == 0 ? green : gray);
        tabLeagues.setTextColor(tab == 1 ? green : gray);
        tabSupport.setTextColor(tab == 2 ? green : gray);

        tabLocal.setBackgroundResource(tab == 0 ? R.drawable.bg_tab_active : 0);
        tabLeagues.setBackgroundResource(tab == 1 ? R.drawable.bg_tab_active : 0);
        tabSupport.setBackgroundResource(tab == 2 ? R.drawable.bg_tab_active : 0);

        NGO[] data = tab == 0 ? LOCAL_NGOS : tab == 1 ? ECO_LEAGUES : SUPPORT;
        populateList(data);
    }

    private void populateList(NGO[] ngos) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (NGO ngo : ngos) {
            View card = inflater.inflate(R.layout.item_ngo, container, false);
            ((TextView) card.findViewById(R.id.tvNgoName)).setText(ngo.name);
            ((TextView) card.findViewById(R.id.tvNgoCity)).setText(ngo.city);
            ((TextView) card.findViewById(R.id.tvProjects)).setText(String.valueOf(ngo.projects));
            ((TextView) card.findViewById(R.id.tvVolunteers)).setText(String.valueOf(ngo.volunteers));
            ((TextView) card.findViewById(R.id.tvRating)).setText(ngo.rating);

            // Message button — opens SMS app pre-filled
            card.findViewById(R.id.btnMessageNgo).setOnClickListener(v -> {
                Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
                smsIntent.setData(Uri.parse("smsto:" + ngo.phone));
                smsIntent.putExtra("sms_body",
                    "Hello " + ngo.name + ", I am reaching out via Carbon Calculator app "
                    + "to collaborate on eco initiatives in " + ngo.city + ".");
                if (smsIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(smsIntent);
                } else {
                    Toast.makeText(this, "No SMS app found", Toast.LENGTH_SHORT).show();
                }
            });

            // Call button — opens dialer
            card.findViewById(R.id.btnCallNgo).setOnClickListener(v -> {
                Intent callIntent = new Intent(Intent.ACTION_DIAL);
                callIntent.setData(Uri.parse("tel:" + ngo.phone));
                startActivity(callIntent);
            });

            container.addView(card);
        }
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
        findViewById(R.id.navProfile).setOnClickListener(v ->
            startActivity(new Intent(this, ProfileActivity.class)));
    }
}
