package com.example.carboncalculator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class ViewfinderView extends View {

    private Paint cornerPaint, bgPaint, iconPaint;

    public ViewfinderView(Context context) { super(context); init(); }
    public ViewfinderView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setColor(Color.parseColor("#1DB954"));
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(6f);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#1A2A3A"));
        bgPaint.setStyle(Paint.Style.FILL);

        iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setColor(Color.parseColor("#4A5A6A"));
        iconPaint.setTextSize(60f);
        iconPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float pad = 20f, len = 50f, r = 16f;

        // Background
        canvas.drawRoundRect(pad, pad, w - pad, h - pad, r, r, bgPaint);

        // Camera icon (unicode)
        canvas.drawText("📷", w / 2f, h / 2f + 20f, iconPaint);

        // Corner brackets
        float l = pad, t = pad, ri = w - pad, bo = h - pad;

        // Top-left
        canvas.drawLine(l, t + len, l, t + r, cornerPaint);
        canvas.drawLine(l + r, t, l + len, t, cornerPaint);

        // Top-right
        canvas.drawLine(ri - len, t, ri - r, t, cornerPaint);
        canvas.drawLine(ri, t + r, ri, t + len, cornerPaint);

        // Bottom-left
        canvas.drawLine(l, bo - len, l, bo - r, cornerPaint);
        canvas.drawLine(l + r, bo, l + len, bo, cornerPaint);

        // Bottom-right
        canvas.drawLine(ri - len, bo, ri - r, bo, cornerPaint);
        canvas.drawLine(ri, bo - r, ri, bo - len, cornerPaint);
    }
}
