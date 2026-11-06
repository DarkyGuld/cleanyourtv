package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** The glass highlight that travels with focus: a bright rim, a soft halo and a top sheen. */
public final class FocusRingView extends View {
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final float density;

    public FocusRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeWidth(10 * density);
        halo.setColor(0x40FFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.2f * density);
        ring.setColor(0x99FFFFFF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float radius = AppAdapter.cornerRadius(w, density);
        float inset = 1.5f * density;
        bounds.set(inset, inset, w - inset, h - inset);
        canvas.drawRoundRect(bounds, radius, radius, halo);
        canvas.drawRoundRect(bounds, radius, radius, ring);
        sheen.setShader(new LinearGradient(0, 0, 0, h * 0.42f,
                new int[]{0x22FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(bounds, radius, radius, sheen);
    }
}
