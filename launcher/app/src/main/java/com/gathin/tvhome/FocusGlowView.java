package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** A soft halo outside the focused tile, driven by the Ikonglød setting. */
public final class FocusGlowView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private View card;

    public FocusGlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setCard(View card) {
        this.card = card;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (card == null || card.getWidth() <= 0 || card.getHeight() <= 0) return;
        float cx = card.getLeft() + card.getWidth() / 2f;
        float cy = card.getTop() + card.getHeight() / 2f;
        float radius = Math.max(card.getWidth(), card.getHeight()) * 0.75f;
        paint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{0x55FFFFFF, 0x22FFFFFF, 0x00FFFFFF},
                new float[]{0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
    }
}
