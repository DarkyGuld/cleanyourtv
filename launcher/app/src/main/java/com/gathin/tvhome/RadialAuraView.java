package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.View;

/** A 32-ray accent aura around the album cover, visible only in Radial Aura mode. */
public final class RadialAuraView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private SyntheticVisualizerSource source;
    private int accent = 0xFFFFFFFF;
    private boolean glow = true;
    private boolean playing;

    public RadialAuraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setXfermode(add);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    void setSource(SyntheticVisualizerSource value) { source = value; }
    void setAccent(int color) { accent = 0xFF000000 | (color & 0xFFFFFF); invalidate(); }
    void setGlow(boolean value) { glow = value; invalidate(); }
    void setPlaying(boolean value) { playing = value; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        if (!isShown() || source == null) return;
        if (playing) source.frame();
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        float radius = Math.min(w, h) / 2f;
        float inner = Math.max(0f, radius - getResources().getDisplayMetrics().density * 11f);
        for (int i = 0; i < SyntheticVisualizerSource.BANDS; i++) {
            float value = source.band(i);
            float peak = source.peak(i);
            double angle = (Math.PI * 2.0 * i / SyntheticVisualizerSource.BANDS) - Math.PI / 2.0;
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            // The cover is edge-to-edge, so the aura is an additive ring just inside its edge: it is
            // always visible instead of being hidden behind the artwork or clipped by the bar.
            float len = getResources().getDisplayMetrics().density * (2f + value * 7f + peak * 2f);
            paint.setStrokeWidth(getResources().getDisplayMetrics().density * (glow ? 3.4f : 2f));
            paint.setColor(((glow ? 0x55 : 0x33) << 24) | (accent & 0xFFFFFF));
            canvas.drawLine(cx + cos * inner, cy + sin * inner,
                    cx + cos * (inner + len), cy + sin * (inner + len), paint);
            paint.setStrokeWidth(getResources().getDisplayMetrics().density * 1.1f);
            paint.setColor((0xAA << 24) | (accent & 0xFFFFFF));
            canvas.drawLine(cx + cos * inner, cy + sin * inner,
                    cx + cos * (inner + len * 0.74f), cy + sin * (inner + len * 0.74f), paint);
        }
        if (playing && isShown() && getWindowVisibility() == VISIBLE) postInvalidateDelayed(33);
    }
}
