package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** The permanent glass finish over a banner tile: top light, thin rim and a soft bottom shade. */
public final class GlassTileView extends View {
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final float density;

    public GlassTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        shade.setShader(new LinearGradient(0, 0, 0, 1, 0x00000000, 0x18000000, Shader.TileMode.CLAMP));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.8f * density);
        rim.setColor(0x73FFFFFF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float radius = AppAdapter.cornerRadius(w, density);
        float inset = 1.5f * density;
        bounds.set(inset, inset, w - inset, h - inset);
        sheen.setShader(new LinearGradient(0, 0, 0, h * 0.50f,
                new int[]{0x33FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(bounds, radius, radius, sheen);
        shade.setShader(new LinearGradient(0, h * 0.58f, 0, h,
                new int[]{0x00000000, 0x18000000}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(bounds, radius, radius, shade);
        canvas.drawRoundRect(bounds, radius, radius, rim);
    }
}
