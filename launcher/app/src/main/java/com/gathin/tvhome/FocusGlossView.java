package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * A short glassy glare that sweeps over the focused tile, like the selected icons in GlassOS.
 * It is decorative only and never takes focus.
 */
public final class FocusGlossView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float amount;
    private float sweep;
    private float strength = 1f;
    private ValueAnimator amountAnim, sweepAnim;
    private LinearGradient bandShader, liftShader;
    private float shaderWidth, shaderHeight;

    public FocusGlossView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setStrength(float strength) {
        this.strength = Math.max(0f, Math.min(1f, strength));
        invalidate();
    }

    void focus(boolean focused, boolean instant) {
        if (amountAnim != null) amountAnim.cancel();
        if (sweepAnim != null) sweepAnim.cancel();
        float target = focused ? 1f : 0f;
        if (instant || getWidth() == 0) {
            amount = target;
            sweep = focused ? 0.72f : 0f;
            invalidate();
            return;
        }
        amountAnim = ValueAnimator.ofFloat(amount, target);
        amountAnim.setDuration(focused ? 220 : 180);
        amountAnim.setInterpolator(new DecelerateInterpolator());
        amountAnim.addUpdateListener(a -> {
            amount = (float) a.getAnimatedValue();
            invalidate();
        });
        amountAnim.start();
        if (focused) {
            sweep = 0f;
            sweepAnim = ValueAnimator.ofFloat(0f, 0.72f);
            sweepAnim.setDuration(430);
            sweepAnim.setInterpolator(new DecelerateInterpolator(1.4f));
            sweepAnim.addUpdateListener(a -> {
                sweep = (float) a.getAnimatedValue();
                invalidate();
            });
            sweepAnim.start();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (amount <= 0.01f || strength <= 0f) return;
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        ensureShaders(w, h);
        float a = amount * strength;
        float band = w * 0.34f;
        float x = -band + sweep * (w + band * 2f);
        int save = canvas.save();
        canvas.rotate(-18f, w / 2f, h / 2f);
        canvas.translate(x, 0f);
        paint.setShader(bandShader);
        paint.setAlpha(Math.min(255, Math.round(255 * a)));
        canvas.drawRect(0, -h, band, h * 2f, paint);
        canvas.restoreToCount(save);
        // A permanent, very soft top-left lift makes the focused tile read as glass even after the sweep.
        paint.setShader(liftShader);
        paint.setAlpha(Math.min(255, Math.round(255 * a)));
        canvas.drawRect(0, 0, w, h, paint);
    }

    private void ensureShaders(float w, float h) {
        if (bandShader != null && shaderWidth == w && shaderHeight == h) return;
        shaderWidth = w;
        shaderHeight = h;
        float band = w * 0.34f;
        bandShader = new LinearGradient(0, 0, band, 0,
                new int[]{0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP);
        liftShader = new LinearGradient(0, 0, w * 0.85f, h,
                0x18FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
    }
}
