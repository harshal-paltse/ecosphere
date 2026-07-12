package com.example.carboncalculator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

public class LoginActivity extends BaseActivity {

    private EditText etUsername, etPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Auto-login if already logged in
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String loggedIn = prefs.getString("loggedInUser", "");
        if (!loggedIn.isEmpty()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etLoginUsername);
        etPassword = findViewById(R.id.etLoginPassword);

        findViewById(R.id.btnLogin).setOnClickListener(v -> login());
        findViewById(R.id.tvGoRegister).setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });
    }

    private void login() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter username and password", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String stored = prefs.getString("pwd_" + username, null);

        if (stored == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!stored.equals(password)) {
            Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
            return;
        }

        // Restore user session
        String name  = prefs.getString("name_"  + username, username);
        String area  = prefs.getString("area_"  + username, "Mumbai");
        String email = prefs.getString("email_" + username, "");
        prefs.edit()
            .putString("loggedInUser", username)
            .putString("username", name)
            .putString("userEmail", email)
            .putString("userArea", area)
            .apply();

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
