package com.example.carboncalculator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom bar chart showing last 7 days of CO₂ emissions (kg).
 * Call setData(float[] values, String[] labels) to populate.
 */
public class WeeklyChartView extends View {

    private Paint barPaint, dimBarPaint, textPaint, labelPaint, valuePaint, gridPaint;
    private float[] data   = new float[7];
    private String[] labels = {"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
    private float maxVal = 1f;

    public WeeklyChartView(Context ctx) { super(ctx); init(); }
    public WeeklyChartView(Context ctx, AttributeSet a) { super(ctx, a); init(); }
    public WeeklyChartView(Context ctx, AttributeSet a, int d) { super(ctx, a, d); init(); }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(Color.parseColor("#1DB954"));
        barPaint.setStyle(Paint.Style.FILL);

        dimBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimBarPaint.setColor(Color.parseColor("#1A3A2A"));
        dimBarPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#8A9BB0"));
        textPaint.setTextSize(28f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#8A9BB0"));
        labelPaint.setTextSize(24f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(Color.WHITE);
        valuePaint.setTextSize(22f);
        valuePaint.setTextAlign(Paint.Align.CENTER);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#1A2A3A"));
        gridPaint.setStrokeWidth(1.5f);
        gridPaint.setStyle(Paint.Style.STROKE);
    }

    public void setData(float[] values, String[] dayLabels) {
        this.data   = values;
        this.labels = dayLabels;
        maxVal = 0.1f;
        for (float v : values) if (v > maxVal) maxVal = v;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        int n = data.length;
        if (n == 0) return;

        float paddingLeft  = 10f;
        float paddingRight = 10f;
        float paddingTop   = 30f;
        float paddingBot   = 48f;

        float chartH = h - paddingTop - paddingBot;
        float totalW = w - paddingLeft - paddingRight;
        if (chartH <= 0 || totalW <= 0) return;

        float barW = totalW / n * 0.55f;
        float gap  = totalW / n;

        // Draw 3 horizontal grid lines
        for (int i = 1; i <= 3; i++) {
            float y = paddingTop + chartH * (1f - i / 4f);
            canvas.drawLine(paddingLeft, y, w - paddingRight, y, gridPaint);
            // Grid value label
            String gridVal = String.format("%.0f", maxVal * i / 4f);
            canvas.drawText(gridVal + "kg", paddingLeft + 2, y - 4, labelPaint);
        }

        for (int i = 0; i < n; i++) {
            float cx = paddingLeft + gap * i + gap / 2f;
            float barH = data[i] > 0 ? (data[i] / maxVal) * chartH : 4f;
            float top  = paddingTop + chartH - barH;
            float left = cx - barW / 2f;
            float right = cx + barW / 2f;

            // Background (dim) bar
            RectF bgRect = new RectF(left, paddingTop, right, paddingTop + chartH);
            canvas.drawRoundRect(bgRect, 8f, 8f, dimBarPaint);

            // Filled bar — color by value magnitude
            if (i == n - 1) {
                barPaint.setColor(Color.parseColor("#1DB954")); // today — always green
            } else if (data[i] > maxVal * 0.7f) {
                barPaint.setColor(Color.parseColor("#EF5350")); // high — red
            } else if (data[i] > maxVal * 0.4f) {
                barPaint.setColor(Color.parseColor("#FFA726")); // mid — orange
            } else {
                barPaint.setColor(Color.parseColor("#1DB954")); // low — green
            }

            RectF fillRect = new RectF(left, top, right, paddingTop + chartH);
            canvas.drawRoundRect(fillRect, 8f, 8f, barPaint);

            // Value on top of bar
            if (data[i] > 0) {
                String val = String.format("%.1f", data[i]);
                canvas.drawText(val, cx, top - 6f, valuePaint);
            }

            // Day label below
            canvas.drawText(labels[i], cx, h - 8f, textPaint);
        }
    }
}
