package com.example.carboncalculator;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class PollutionAlertHelper {

    // ── NGO contacts ──────────────────────────────────────────────────────────
    public static final String VASUNDHARA_NGO_PHONE = "7058379806";
    public static final String VASUNDHARA_NGO_NAME  = "Vasundhara NGO";
    public static final String GREEN_NASHIK_PHONE   = "8329077911";
    public static final String GREEN_NASHIK_NAME    = "Green Nashik NGO";

    private static final String[][] NGO_CONTACTS = {
        {VASUNDHARA_NGO_NAME, VASUNDHARA_NGO_PHONE},
        {GREEN_NASHIK_NAME,   GREEN_NASHIK_PHONE},
    };

    // ── Gmail SMTP — spaces removed from app password ─────────────────────────
    private static final String SENDER_EMAIL    = "harshalb415@gmail.com";
    private static final String SENDER_APP_PASS = "wgyh kmpa xbmd uoxd";

    private static final String OPENAQ_URL =
        "https://api.openaq.org/v2/latest?limit=5&coordinates=%.6f,%.6f&radius=50000&order_by=distance&sort=asc";

    // ── AQI ───────────────────────────────────────────────────────────────────

    public interface AqiCallback {
        void onResult(AqiData data);
        void onError(String message);
    }

    public static class AqiData {
        public String locationName;
        public double lat, lon;
        public double pm25 = -1, pm10 = -1, aqi = -1;
        public String dominantPollutant = "PM2.5";
        public String level;
        public int    levelColor;
        public String distanceKm;
        public boolean isHazardous() { return aqi >= 150 || pm25 >= 55.5; }
    }

    public static void fetchNearestAqi(double lat, double lon, AqiCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(String.format(Locale.US, OPENAQ_URL, lat, lon));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() != 200) {
                    callback.onError("AQI fetch failed: HTTP " + conn.getResponseCode());
                    return;
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONArray results = new JSONObject(sb.toString()).getJSONArray("results");
                if (results.length() == 0) { callback.onError("No nearby stations found"); return; }

                AqiData data = new AqiData();
                JSONObject station = results.getJSONObject(0);
                data.locationName = station.optString("location", "Nearby Station");

                JSONObject coords = station.optJSONObject("coordinates");
                if (coords != null) {
                    data.lat = coords.optDouble("latitude", lat);
                    data.lon = coords.optDouble("longitude", lon);
                }
                double dLat = data.lat - lat, dLon = data.lon - lon;
                data.distanceKm = String.format(Locale.getDefault(), "%.1f km away",
                    Math.sqrt(dLat * dLat + dLon * dLon) * 111.0);

                JSONArray measurements = station.getJSONArray("measurements");
                for (int i = 0; i < measurements.length(); i++) {
                    JSONObject m = measurements.getJSONObject(i);
                    String param = m.optString("parameter", "");
                    double value = m.optDouble("value", -1);
                    if (param.equals("pm25") && value >= 0) data.pm25 = value;
                    if (param.equals("pm10") && value >= 0) data.pm10 = value;
                }

                if      (data.pm25 >= 0) { data.aqi = calcAqiFromPm25(data.pm25); data.dominantPollutant = "PM2.5"; }
                else if (data.pm10 >= 0) { data.aqi = data.pm10; data.dominantPollutant = "PM10"; }

                if      (data.aqi < 0)    { data.level = "Unknown";                 data.levelColor = 0xFF8A9BB0; }
                else if (data.aqi <= 50)  { data.level = "Good";                    data.levelColor = 0xFF1DB954; }
                else if (data.aqi <= 100) { data.level = "Moderate";                data.levelColor = 0xFFFFD54F; }
                else if (data.aqi <= 150) { data.level = "Unhealthy for Sensitive"; data.levelColor = 0xFFFFA726; }
                else if (data.aqi <= 200) { data.level = "Unhealthy";               data.levelColor = 0xFFEF5350; }
                else if (data.aqi <= 300) { data.level = "Very Unhealthy";          data.levelColor = 0xFF9C27B0; }
                else                      { data.level = "Hazardous";               data.levelColor = 0xFFC62828; }

                callback.onResult(data);
            } catch (Exception e) {
                Log.e("PollutionAlert", "AQI fetch error", e);
                callback.onError("Could not fetch air quality data");
            }
        }).start();
    }

    private static double calcAqiFromPm25(double pm25) {
        double[][] bp = {
            {0.0, 12.0, 0, 50}, {12.1, 35.4, 51, 100}, {35.5, 55.4, 101, 150},
            {55.5, 150.4, 151, 200}, {150.5, 250.4, 201, 300},
            {250.5, 350.4, 301, 400}, {350.5, 500.4, 401, 500},
        };
        for (double[] b : bp)
            if (pm25 >= b[0] && pm25 <= b[1])
                return ((b[3] - b[2]) / (b[1] - b[0])) * (pm25 - b[0]) + b[2];
        return 500;
    }

    // ── Auto SMS — sends directly without opening any app ────────────────────

    private static void sendSmsToAll(Context context, String msg) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e("PollutionAlert", "SEND_SMS permission DENIED — SMS not sent. Grant it in app settings.");
            return;
        }
        try {
            SmsManager sms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? context.getSystemService(SmsManager.class)
                : SmsManager.getDefault();

            if (sms == null) { Log.e("PollutionAlert", "SmsManager is null"); return; }

            ArrayList<String> parts = sms.divideMessage(msg);
            for (String[] ngo : NGO_CONTACTS) {
                sms.sendMultipartTextMessage(ngo[1], null, parts, null, null);
                Log.i("PollutionAlert", "✅ SMS sent to " + ngo[0] + " (" + ngo[1] + ")");
            }
        } catch (Exception e) {
            Log.e("PollutionAlert", "SMS send failed: " + e.getMessage(), e);
        }
    }

    public static void sendNgoSmsAlert(Context context, double lat, double lon,
                                        String reporterName, String wasteType) {
        new Thread(() -> {
            String mapsLink = String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", lat, lon);
            String msg = String.format(
                "POLLUTION ALERT\nReporter: %s\nType: %s\nLocation: %.4f,%.4f\nMap: %s\n- Carbon Calculator App",
                reporterName, wasteType, lat, lon, mapsLink);
            sendSmsToAll(context, msg);
        }).start();
    }

    public static void sendPollutedLocationAlert(Context context, double lat, double lon,
                                                  String reporterName) {
        sendNgoSmsAlert(context, lat, lon, reporterName, "Polluted Location Reported");
    }

    public static void sendTreePlantedSmsToNgos(Context context, double lat, double lon,
                                                  String reporterName, int totalTrees) {
        new Thread(() -> {
            String mapsLink = String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", lat, lon);
            String msg = String.format(
                "TREE PLANTED\nPlanter: %s\nTotal trees: %d\nLocation: %.4f,%.4f\nMap: %s\n- Carbon Calculator App",
                reporterName, totalTrees, lat, lon, mapsLink);
            sendSmsToAll(context, msg);
        }).start();
    }

    // ── Auto email via SMTP — fully silent, no user interaction ──────────────

    public static void sendAppreciationEmail(android.app.Activity activity,
                                              String toEmail, String userName, int totalTrees) {
        if (toEmail == null || toEmail.isEmpty()) {
            Log.w("PollutionAlert", "No user email, skipping");
            return;
        }
        new Thread(() -> {
            try {
                double co2 = totalTrees * 21.0;
                String certNumber = "CC-" + System.currentTimeMillis() % 1000000;
                String date = new java.text.SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                    .format(new java.util.Date());

                String subject = "🌳 Tree Planting Certificate — " + userName;
                String body =
                    "Dear " + userName + ",\n\n"
                    + "Congratulations! Your tree planting certificate is attached.\n\n"
                    + "Certificate No: " + certNumber + "\n"
                    + "Date: " + date + "\n"
                    + "Trees planted: " + totalTrees + "\n"
                    + "CO2 offset: " + String.format(Locale.getDefault(), "%.1f", co2) + " kg/year\n\n"
                    + getBadgeMessage(totalTrees) + "\n\n"
                    + "Keep up the amazing work. Every tree counts!\n\n"
                    + "-- Carbon Calculator App\n"
                    + "   Vasundhara NGO | Green Nashik NGO";

                // Generate PDF certificate
                java.io.File pdfFile = generateCertificatePdf(activity, userName, totalTrees,
                    co2, certNumber, date);

                Properties props = new Properties();
                props.put("mail.smtp.auth",              "true");
                props.put("mail.smtp.starttls.enable",   "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.host",              "smtp.gmail.com");
                props.put("mail.smtp.port",              "587");
                props.put("mail.smtp.ssl.trust",         "smtp.gmail.com");
                props.put("mail.smtp.ssl.protocols",     "TLSv1.2");

                Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(SENDER_EMAIL, SENDER_APP_PASS);
                    }
                });

                MimeMessage email = new MimeMessage(session);
                email.setFrom(new InternetAddress(SENDER_EMAIL, "Carbon Calculator App"));
                email.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
                email.setSubject(subject);

                // Build multipart — body text + PDF attachment
                javax.mail.internet.MimeMultipart multipart = new javax.mail.internet.MimeMultipart();

                javax.mail.internet.MimeBodyPart textPart = new javax.mail.internet.MimeBodyPart();
                textPart.setText(body);
                multipart.addBodyPart(textPart);

                if (pdfFile != null && pdfFile.exists()) {
                    javax.mail.internet.MimeBodyPart attachPart = new javax.mail.internet.MimeBodyPart();
                    attachPart.attachFile(pdfFile);
                    attachPart.setFileName("TreeCertificate_" + userName.replaceAll("\\s+", "_") + ".pdf");
                    multipart.addBodyPart(attachPart);
                }

                email.setContent(multipart);
                Transport.send(email);
                Log.i("PollutionAlert", "Certificate email sent to " + toEmail);

                // Clean up temp PDF
                if (pdfFile != null) pdfFile.delete();

            } catch (Exception e) {
                Log.e("PollutionAlert", "Certificate email failed: " + e.getMessage(), e);
            }
        }).start();
    }

    // ── Generate professional PDF certificate with QR code ────────────────────

    private static java.io.File generateCertificatePdf(Context context, String userName,
                                                         int trees, double co2,
                                                         String certNumber, String date) {
        try {
            android.graphics.pdf.PdfDocument doc = new android.graphics.pdf.PdfDocument();
            // A4 landscape for a wider certificate feel
            android.graphics.pdf.PdfDocument.PageInfo pageInfo =
                new android.graphics.pdf.PdfDocument.PageInfo.Builder(842, 595, 1).create();
            android.graphics.pdf.PdfDocument.Page page = doc.startPage(pageInfo);
            android.graphics.Canvas c = page.getCanvas();

            android.graphics.Paint p = new android.graphics.Paint();
            p.setAntiAlias(true);

            // ── Background: white ─────────────────────────────────────────────
            p.setColor(android.graphics.Color.WHITE);
            p.setStyle(android.graphics.Paint.Style.FILL);
            c.drawRect(0, 0, 842, 595, p);

            // ── Outer border — dark green double line ─────────────────────────
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setColor(android.graphics.Color.parseColor("#1A5C2A"));
            p.setStrokeWidth(6f);
            c.drawRect(18, 18, 824, 577, p);
            p.setStrokeWidth(2f);
            c.drawRect(26, 26, 816, 569, p);

            // ── Top green header band ─────────────────────────────────────────
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setColor(android.graphics.Color.parseColor("#1A5C2A"));
            c.drawRect(26, 26, 816, 100, p);

            // ── Emblem circle (left of header) ────────────────────────────────
            p.setColor(android.graphics.Color.parseColor("#F5F5DC")); // cream
            c.drawCircle(80, 63, 30, p);
            p.setColor(android.graphics.Color.parseColor("#1A5C2A"));
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            c.drawCircle(80, 63, 30, p);
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setColor(android.graphics.Color.parseColor("#1A5C2A"));
            p.setTextSize(28f);
            p.setTextAlign(android.graphics.Paint.Align.CENTER);
            c.drawText("🌳", 80, 72, p);

            // ── Header text ───────────────────────────────────────────────────
            p.setColor(android.graphics.Color.WHITE);
            p.setTextSize(22f);
            p.setFakeBoldText(true);
            p.setTextAlign(android.graphics.Paint.Align.CENTER);
            c.drawText("Carbon Calculator App", 421, 55, p);
            p.setTextSize(13f);
            p.setFakeBoldText(false);
            c.drawText("Vasundhara NGO  ·  Green Nashik NGO", 421, 80, p);

            // ── Certificate title ─────────────────────────────────────────────
            p.setColor(android.graphics.Color.parseColor("#1A5C2A"));
            p.setTextSize(28f);
            p.setFakeBoldText(true);
            c.drawText("CERTIFICATE OF TREE PLANTATION", 421, 145, p);

            // Decorative underline
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            c.drawLine(180, 152, 662, 152, p);
            p.setStyle(android.graphics.Paint.Style.FILL);

            // ── "This is to certify" ──────────────────────────────────────────
            p.setColor(android.graphics.Color.parseColor("#333333"));
            p.setTextSize(14f);
            p.setFakeBoldText(false);
            c.drawText("This is to certify that", 421, 185, p);

            // ── Recipient name ────────────────────────────────────────────────
            p.setColor(android.graphics.Color.parseColor("#1A3A6A")); // navy blue
            p.setTextSize(34f);
            p.setFakeBoldText(true);
            c.drawText(userName, 421, 232, p);

            // Name underline
            android.graphics.Paint measureP = new android.graphics.Paint();
            measureP.setTextSize(34f);
            float nw = measureP.measureText(userName);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setColor(android.graphics.Color.parseColor("#1A3A6A"));
            p.setStrokeWidth(1.5f);
            c.drawLine(421 - nw / 2, 238, 421 + nw / 2, 238, p);
            p.setStyle(android.graphics.Paint.Style.FILL);

            // ── Body text ─────────────────────────────────────────────────────
            p.setColor(android.graphics.Color.parseColor("#333333"));
            p.setTextSize(13f);
            p.setFakeBoldText(false);
            c.drawText("has actively participated in the National Tree Plantation Drive and has successfully planted", 421, 268, p);

            // ── Tree count ────────────────────────────────────────────────────
            p.setColor(android.graphics.Color.parseColor("#1A5C2A"));
            p.setTextSize(42f);
            p.setFakeBoldText(true);
            c.drawText(trees + (trees == 1 ? " Tree" : " Trees"), 421, 322, p);

            // ── CO2 line ──────────────────────────────────────────────────────
            p.setColor(android.graphics.Color.parseColor("#555555"));
            p.setTextSize(12f);
            p.setFakeBoldText(false);
            c.drawText("thereby contributing to offset approximately " +
                String.format(Locale.getDefault(), "%.1f", co2) +
                " kg of CO\u2082 per year from the atmosphere.", 421, 348, p);

            // ── Badge ─────────────────────────────────────────────────────────
            String badge = trees >= 20 ? "Forest Guardian"
                         : trees >= 10 ? "Green Champion"
                         : trees >= 5  ? "Eco Warrior"
                         : "Tree Planter";
            // Badge pill background
            p.setColor(android.graphics.Color.parseColor("#E8F5E9"));
            p.setStyle(android.graphics.Paint.Style.FILL);
            android.graphics.RectF badgeRect = new android.graphics.RectF(321, 360, 521, 385);
            c.drawRoundRect(badgeRect, 12, 12, p);
            p.setColor(android.graphics.Color.parseColor("#1A5C2A"));
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(1f);
            c.drawRoundRect(badgeRect, 12, 12, p);
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setTextSize(13f);
            p.setFakeBoldText(true);
            c.drawText("🏅  " + badge, 421, 378, p);

            // ── Horizontal divider ────────────────────────────────────────────
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setColor(android.graphics.Color.parseColor("#CCCCCC"));
            p.setStrokeWidth(1f);
            c.drawLine(60, 400, 782, 400, p);
            p.setStyle(android.graphics.Paint.Style.FILL);

            // ── Cert number + date (bottom left) ──────────────────────────────
            p.setColor(android.graphics.Color.parseColor("#555555"));
            p.setTextSize(11f);
            p.setFakeBoldText(false);
            p.setTextAlign(android.graphics.Paint.Align.LEFT);
            c.drawText("Certificate No: " + certNumber, 60, 425, p);
            c.drawText("Date of Issue: " + date, 60, 442, p);
            c.drawText("Issued by: Carbon Calculator App", 60, 459, p);

            // ── Signature blocks ──────────────────────────────────────────────
            // Left signature
            p.setColor(android.graphics.Color.parseColor("#1A5C2A"));
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(1f);
            c.drawLine(60, 510, 220, 510, p);
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setTextSize(12f);
            p.setFakeBoldText(true);
            p.setTextAlign(android.graphics.Paint.Align.LEFT);
            c.drawText("Vasundhara NGO", 60, 525, p);
            p.setFakeBoldText(false);
            p.setColor(android.graphics.Color.parseColor("#555555"));
            p.setTextSize(10f);
            c.drawText("Authorized Signatory", 60, 540, p);

            // Center signature
            p.setColor(android.graphics.Color.parseColor("#1A5C2A"));
            p.setStyle(android.graphics.Paint.Style.STROKE);
            c.drawLine(321, 510, 521, 510, p);
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setTextSize(12f);
            p.setFakeBoldText(true);
            p.setTextAlign(android.graphics.Paint.Align.CENTER);
            c.drawText("Green Nashik NGO", 421, 525, p);
            p.setFakeBoldText(false);
            p.setColor(android.graphics.Color.parseColor("#555555"));
            p.setTextSize(10f);
            c.drawText("Authorized Signatory", 421, 540, p);

            // ── QR Code (bottom right) ────────────────────────────────────────
            String qrContent = "Certificate No: " + certNumber
                + "\nName: " + userName
                + "\nTrees: " + trees
                + "\nCO2 Offset: " + String.format(Locale.getDefault(), "%.1f", co2) + " kg/year"
                + "\nDate: " + date
                + "\nIssued by: Carbon Calculator App";

            android.graphics.Bitmap qrBitmap = generateQrBitmap(qrContent, 120);
            if (qrBitmap != null) {
                c.drawBitmap(qrBitmap, 662, 400, null);
                p.setColor(android.graphics.Color.parseColor("#555555"));
                p.setTextSize(9f);
                p.setTextAlign(android.graphics.Paint.Align.CENTER);
                c.drawText("Scan to verify", 722, 530, p);
            }

            // ── Footer band ───────────────────────────────────────────────────
            p.setColor(android.graphics.Color.parseColor("#E8F5E9"));
            p.setStyle(android.graphics.Paint.Style.FILL);
            c.drawRect(26, 555, 816, 569, p);
            p.setColor(android.graphics.Color.parseColor("#1A5C2A"));
            p.setTextSize(10f);
            p.setFakeBoldText(false);
            p.setTextAlign(android.graphics.Paint.Align.CENTER);
            c.drawText("\"Plant a tree today — breathe cleaner air tomorrow.\"  |  Carbon Calculator App — Making India Greener", 421, 565, p);

            doc.finishPage(page);

            java.io.File file = new java.io.File(context.getCacheDir(),
                "certificate_" + System.currentTimeMillis() + ".pdf");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            doc.writeTo(fos);
            fos.close();
            doc.close();
            return file;

        } catch (Exception e) {
            Log.e("PollutionAlert", "PDF generation failed: " + e.getMessage(), e);
            return null;
        }
    }

    // ── Generate QR code bitmap using ZXing ───────────────────────────────────

    private static android.graphics.Bitmap generateQrBitmap(String content, int size) {
        try {
            com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix matrix = writer.encode(
                content,
                com.google.zxing.BarcodeFormat.QR_CODE,
                size, size);

            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size,
                android.graphics.Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bmp.setPixel(x, y, matrix.get(x, y)
                        ? android.graphics.Color.parseColor("#1A3A6A")
                        : android.graphics.Color.WHITE);
                }
            }
            return bmp;
        } catch (Exception e) {
            Log.e("PollutionAlert", "QR generation failed: " + e.getMessage());
            return null;
        }
    }

    // ── Carbon emissions report email to user ─────────────────────────────────

    public static void sendCarbonReportEmail(android.app.Activity activity,
                                              String toEmail, String userName,
                                              double total, double travelKg, double elecKg,
                                              double meatKg, double waterKg, double wasteKg,
                                              double shopKg, double digitalKg) {
        if (toEmail == null || toEmail.isEmpty()) {
            Log.w("PollutionAlert", "No user email — skipping carbon report");
            return;
        }
        new Thread(() -> {
            try {
                String level = total < 5 ? "Low Impact - Great job!"
                             : total < 15 ? "Moderate Impact - Keep going!"
                             : "High Impact - Try to reduce!";

                double treesNeeded = total / 21.0;

                String subject = "Your Carbon Footprint Report - " + userName;
                String body =
                    "Dear " + userName + ",\n\n"
                    + "Here is your daily carbon emissions report:\n\n"
                    + "  Total CO2: " + String.format(Locale.getDefault(), "%.2f", total) + " kg\n"
                    + "  Status: " + level + "\n\n"
                    + "Breakdown:\n"
                    + "  Travel:      " + String.format(Locale.getDefault(), "%.2f", travelKg)  + " kg\n"
                    + "  Electricity: " + String.format(Locale.getDefault(), "%.2f", elecKg)    + " kg\n"
                    + "  Meat:        " + String.format(Locale.getDefault(), "%.2f", meatKg)    + " kg\n"
                    + "  Water:       " + String.format(Locale.getDefault(), "%.2f", waterKg)   + " kg\n"
                    + "  Waste:       " + String.format(Locale.getDefault(), "%.2f", wasteKg)   + " kg\n"
                    + "  Shopping:    " + String.format(Locale.getDefault(), "%.2f", shopKg)    + " kg\n"
                    + "  Digital:     " + String.format(Locale.getDefault(), "%.2f", digitalKg) + " kg\n\n"
                    + "To offset today's emissions, plant "
                    + String.format(Locale.getDefault(), "%.1f", treesNeeded) + " tree(s).\n\n"
                    + "Tips to reduce:\n"
                    + "  - Use public transport or cycle for short trips\n"
                    + "  - Switch to LED bulbs and unplug idle devices\n"
                    + "  - Reduce meat meals to 3 per week\n"
                    + "  - Segregate waste for recycling\n\n"
                    + "Keep tracking and keep improving!\n\n"
                    + "-- Carbon Calculator App\n"
                    + "   Vasundhara NGO | Green Nashik NGO";

                Properties props = new Properties();
                props.put("mail.smtp.auth",              "true");
                props.put("mail.smtp.starttls.enable",   "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.host",              "smtp.gmail.com");
                props.put("mail.smtp.port",              "587");
                props.put("mail.smtp.ssl.trust",         "smtp.gmail.com");
                props.put("mail.smtp.ssl.protocols",     "TLSv1.2");

                Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(SENDER_EMAIL, SENDER_APP_PASS);
                    }
                });

                MimeMessage email = new MimeMessage(session);
                email.setFrom(new InternetAddress(SENDER_EMAIL, "Carbon Calculator App"));
                email.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
                email.setSubject(subject);
                email.setText(body);
                Transport.send(email);
                Log.i("PollutionAlert", "Carbon report email sent to " + toEmail);

            } catch (Exception e) {
                Log.e("PollutionAlert", "Carbon report email failed: " + e.getMessage(), e);
            }
        }).start();
    }

    private static String getBadgeMessage(int trees) {
        if (trees >= 20) return "Badge: Forest Guardian - You're a true eco hero!";
        if (trees >= 10) return "Badge: Green Champion - Outstanding dedication!";
        if (trees >= 5)  return "Badge: Eco Warrior - Keep growing!";
        if (trees >= 1)  return "Badge: Tree Planter - Great start!";
        return "";
    }
}
