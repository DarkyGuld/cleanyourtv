package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** A small decorative audio-style bar accent under the controls; no audio capture. */
public final class VisualizerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] phase = new float[32];
    private boolean active;
    private long started;

    public VisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(0x99FFFFFF);
        for (int i = 0; i < phase.length; i++) phase[i] = (float) (i * 0.7);
    }

    void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        started = SystemClock.uptimeMillis();
        invalidate();
    }

    void setColor(int color) {
        // Mostly white with a hint of the artwork's colour: a quiet waveform, not a coloured stripe.
        int r = (((color >> 16) & 0xFF) + 510) / 3;
        int g = (((color >> 8) & 0xFF) + 510) / 3;
        int b = ((color & 0xFF) + 510) / 3;
        paint.setColor((0x80000000) | (r << 16) | (g << 8) | b);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        int bars = 28;
        float gap = w * 0.011f;
        float barW = (w - gap * (bars - 1)) / bars;
        if (barW <= 0) return;
        long now = SystemClock.uptimeMillis() - started;
        for (int i = 0; i < bars; i++) {
            float amp;
            if (active) {
                float t = now / 420f + phase[i];
                amp = 0.18f + 0.82f * Math.abs((float) Math.sin(t) * 0.55f + (float) Math.sin(t * 1.7f) * 0.45f);
            } else {
                amp = 0.25f + 0.45f * Math.abs((float) Math.sin(i * 0.9f));
            }
            float bh = Math.max(dp(1.5f), h * Math.min(1f, amp));
            float left = i * (barW + gap);
            canvas.drawRoundRect(left, h - bh, left + barW, h, barW / 2f, barW / 2f, paint);
        }
        if (active) postInvalidateDelayed(90);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
