package com.example.carboncalculator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class RegisterActivity extends BaseActivity {

    private EditText etName, etUsername, etEmail, etPassword, etConfirm;
    private Spinner spinnerArea;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etName      = findViewById(R.id.etRegName);
        etUsername  = findViewById(R.id.etRegUsername);
        etEmail     = findViewById(R.id.etRegEmail);
        etPassword  = findViewById(R.id.etRegPassword);
        etConfirm   = findViewById(R.id.etRegConfirm);
        spinnerArea = findViewById(R.id.spinnerRegArea);

        findViewById(R.id.btnRegister).setOnClickListener(v -> register());
        findViewById(R.id.tvGoLogin).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void register() {
        String name     = etName.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirm  = etConfirm.getText().toString();
        String area     = spinnerArea.getSelectedItem().toString();

        if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirm)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 4) {
            Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);

        if (prefs.contains("pwd_" + username)) {
            Toast.makeText(this, "Username already taken", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit()
            .putString("pwd_"   + username, password)
            .putString("name_"  + username, name)
            .putString("email_" + username, email)
            .putString("area_"  + username, area)
            .putString("loggedInUser", username)
            .putString("username", name)
            .putString("userEmail", email)
            .putString("userArea", area)
            .putInt("trees_"  + username, 0)
            .putInt("points_" + username, 0)
            .apply();

        Toast.makeText(this, "Account created! Welcome " + name, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
