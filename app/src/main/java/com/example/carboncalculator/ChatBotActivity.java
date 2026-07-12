package com.example.carboncalculator;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatBotActivity extends BaseActivity {

    private static final String API_KEY = "REPLACE_WITH_YOUR_API_KEY";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-3.5-turbo";

    private LinearLayout chatMessages, chipContainer;
    private ScrollView chatScroll;
    private EditText etInput;
    private Handler mainHandler;
    private ExecutorService executor;
    private View btnSend;
    private TextView typingView;

    private int userTrees, userPoints;
    private float userCO2Today;
    private String userName, userArea;

    private static final String[] CHIPS = {
        "My Eco Stats", "Reduce CO₂", "River Cleaning", "Plant Trees",
        "Science Help", "Math Problem", "History Fact", "General Knowledge",
        "Health Tips", "Tech Explain", "Eco Transport", "Save Electricity"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
        chatMessages = findViewById(R.id.chatMessages);
        chatScroll = findViewById(R.id.chatScroll);
        chipContainer = findViewById(R.id.chipContainer);
        etInput = findViewById(R.id.etChatInput);
        btnSend = findViewById(R.id.btnSend);

        loadUserData();
        buildChips();

        btnSend.setOnClickListener(v -> sendMessage());
        findViewById(R.id.btnChatClose).setOnClickListener(v -> finish());

        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                sendMessage();
                return true;
            }
            return false;
        });

        addBotMessage("👋 Hi " + userName + "! I'm EcoBot — powered by ChatGPT.\n\n"
                + "Ask me ANYTHING:\n"
                + "• 🌿 Carbon footprint & eco tips\n"
                + "• 🌊 River & water body cleaning\n"
                + "• 📚 Science, Math, History, GK\n"
                + "• 💡 Tech, Health, or any topic!\n\n"
                + "I'm here to help with everything. 🤖");
    }

    private void loadUserData() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String loggedIn = prefs.getString("loggedInUser", "");
        userName = prefs.getString("username", "Friend");
        userArea = prefs.getString("userArea", "India");
        userTrees = prefs.getInt("trees_" + loggedIn, 0);
        userPoints = prefs.getInt("points_" + loggedIn, 0);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(new java.util.Date());
        userCO2Today = prefs.getFloat("co2_" + loggedIn + "_" + today, 0f);
    }

    private void buildChips() {
        for (String chip : CHIPS) {
            TextView tv = new TextView(this);
            tv.setText(chip);
            tv.setTextColor(Color.parseColor("#1DB954"));
            tv.setTextSize(12);
            tv.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_badge_green));
            int h = dpToPx(8), v2 = dpToPx(5);
            tv.setPadding(h * 2, v2, h * 2, v2);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dpToPx(8));
            tv.setLayoutParams(lp);
            tv.setOnClickListener(view -> handleInput(chip));
            chipContainer.addView(tv);
        }
    }

    private void sendMessage() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        etInput.setText("");
        handleInput(text);
    }

    private void handleInput(String input) {
        addUserMessage(input);
        showTypingIndicator();
        setBtnEnabled(false);
        executor.execute(() -> {
            String reply = callOpenAI(input);
            mainHandler.post(() -> {
                removeTypingIndicator();
                setBtnEnabled(true);
                addBotMessage(reply);
            });
        });
    }

    private String callOpenAI(String userMessage) {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);

            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", buildSystemPrompt());

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);

            JSONArray messages = new JSONArray();
            messages.put(systemMsg);
            messages.put(userMsg);

            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("messages", messages);
            body.put("max_tokens", 500);
            body.put("temperature", 0.7);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();
            BufferedReader br;
            if (code == 200) {
                br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                StringBuilder err = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    err.append(line);
                }
                br.close();
                return "⚠️ API error " + code + ".\n\nOffline answer:\n\n" + offlineReply(userMessage.toLowerCase());
            }

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();

            JSONObject json = new JSONObject(response.toString());
            return json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();

        } catch (Exception e) {
            return "📡 Connection error. Offline answer:\n\n" + offlineReply(userMessage.toLowerCase());
        }
    }

    private String buildSystemPrompt() {
        return "You are EcoBot, a helpful AI assistant inside a Carbon Calculator Android app. "
                + "Answer ANY question the user asks — science, math, history, general knowledge, health, technology, or anything else. "
                + "You also have special expertise in eco topics like carbon footprint, river cleaning, tree planting, and sustainable living.\n\n"
                + "User profile:\n"
                + "- Name: " + userName + "\n"
                + "- Area: " + userArea + "\n"
                + "- Trees planted: " + userTrees + "\n"
                + "- Eco points: " + userPoints + "\n"
                + "- Today's CO₂: " + String.format("%.1f", userCO2Today) + " kg\n\n"
                + "Guidelines:\n"
                + "- Answer concisely (max 150 words), use bullet points where helpful\n"
                + "- Use emojis to keep it friendly\n"
                + "- For eco questions, personalise using the user's data above\n"
                + "- For any other topic, just answer helpfully and accurately";
    }

    private String offlineReply(String q) {
        if (q.contains("stat") || q.contains("my data") || q.contains("progress")) {
            return "📊 Your stats:\n🌳 Trees: " + userTrees + "\n⭐ Points: " + userPoints
                    + "\n💨 Today's CO₂: " + String.format("%.1f", userCO2Today) + " kg\n📍 Area: " + userArea;
        }
        if (q.contains("river") || q.contains("water") || q.contains("clean")) {
            return "🌊 River Cleaning:\n1. Report dumping to NMC: 0253-2575631\n2. Join weekend clean drives\n3. Plant trees along riverbanks\n4. Use eco-friendly detergents";
        }
        if (q.contains("carbon") || q.contains("co2") || q.contains("reduce")) {
            return "💡 Reduce CO₂:\n• Use public transport\n• Switch to LED bulbs\n• Plant trees (you have " + userTrees + ")\n• Reduce meat meals";
        }
        if (q.contains("tree") || q.contains("plant")) {
            return "🌳 You have " + userTrees + " trees. Need " + Math.max(0, 10 - userTrees) + " more for Green Champion badge.";
        }
        if (q.contains("electric") || q.contains("power")) {
            return "⚡ Save electricity:\n• LED bulbs save 75%\n• AC at 24°C saves 6% per degree\n• Unplug chargers when idle";
        }
        if (q.contains("transport") || q.contains("car") || q.contains("travel")) {
            return "🚗 Eco transport:\n• Bus: 0.089 kg CO₂/km\n• Car: 0.192 kg CO₂/km\n• Cycle: 0 kg CO₂/km ✅";
        }
        return "🤖 Connect to the internet for live AI answers on any topic!";
    }

    private void showTypingIndicator() {
        typingView = new TextView(this);
        typingView.setText("🤖 EcoBot is thinking...");
        typingView.setTextColor(Color.parseColor("#8A9BB0"));
        typingView.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dpToPx(4), 0, dpToPx(4));
        typingView.setLayoutParams(lp);
        chatMessages.addView(typingView);
        scrollToBottom();
    }

    private void removeTypingIndicator() {
        if (typingView != null) {
            chatMessages.removeView(typingView);
            typingView = null;
        }
    }

    private void addUserMessage(String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(dpToPx(48), dpToPx(6), 0, dpToPx(6));
        row.setLayoutParams(rowLp);
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        tv.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_nav_active_pill));
        tv.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));
        row.addView(tv);
        chatMessages.addView(row);
        scrollToBottom();
    }

    private void addBotMessage(String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dpToPx(6), dpToPx(48), dpToPx(6));
        row.setLayoutParams(rowLp);
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#B0C4D8"));
        tv.setTextSize(13);
        tv.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_rank_pill));
        tv.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));
        row.addView(tv);
        chatMessages.addView(row);
        scrollToBottom();
    }

    private void setBtnEnabled(boolean enabled) {
        btnSend.setEnabled(enabled);
        btnSend.setAlpha(enabled ? 1f : 0.5f);
    }

    private void scrollToBottom() {
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
