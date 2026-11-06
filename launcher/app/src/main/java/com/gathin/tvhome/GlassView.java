package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A translucent glass slab: soft sheen fill, bright top specular, crisp rim and a faint outer glow.
 * One instance floats over the grid and is moved with spring animations between tiles.
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint motionSheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;
    /** Glow colour as 0xRRGGBB: white normally, a festival's accent (diya gold) while a festive skin is on. */
    private int glowRgb = 0xFFFFFF;
    /** Glow strength: the white glow stays faint, a coloured one needs more alpha to read as warm light. */
    private int glowAlpha = 0x30;
    /** glass_style rim or halo: no sheen and no fill, only the crisp rim (and, for halo, a lighter outer glow). */
    private boolean flat;
    private boolean halo;
    /** A soft glass lens for the top-bar buttons: a halo and sheen, never a hard white stroke. */
    private boolean lens;
    private Drawable lensIcon;
    private int lensIconSize;
    /** Normalised spring speed 0..1 and direction, used to make the glass react to navigation. */
    private float motion;
    private float motionX;
    private float motionY;
    /** 0.62 for Subtil, 1.35 for Full: makes the difference between the two settings visible. */
    private float motionStrength = 1f;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0xCCFFFFFF);
        glow.setStyle(Paint.Style.STROKE);
        // Cache the slab as a texture. The spring only changes translation, so the GPU can
        // move it without replaying clipPath and the glow on every frame. That path is
        // software-rendered on the MT5895 and is what makes the highlight crawl.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** Tints the rim and the outer glow; 0 restores the plain white glass. */
    void setAccent(int argb) {
        if (argb == 0) {
            glowRgb = 0xFFFFFF;
            glowAlpha = 0x30;
            rim.setColor(0xCCFFFFFF);
        } else {
            glowRgb = argb & 0xFFFFFF;
            glowAlpha = 0x70;
            // The rim is the accent lifted towards white, so the edge still reads as glass, not paint.
            int r = (((glowRgb >> 16) & 0xFF) + 255 * 2) / 3, g = (((glowRgb >> 8) & 0xFF) + 255 * 2) / 3,
                    b = ((glowRgb & 0xFF) + 255 * 2) / 3;
            rim.setColor(0xE6000000 | (r << 16) | (g << 8) | b);
        }
        invalidate();
    }

    /** One of Config's GLASS_* styles. */
    void setStyle(String style) {
        flat = Config.GLASS_RIM.equals(style) || Config.GLASS_HALO.equals(style);
        halo = Config.GLASS_HALO.equals(style);
        rim.setStrokeWidth((flat ? 2f : 1.5f) * density);
        invalidate();
    }

    /** Soft focus lens for the top bar: no stroke, just a glassy halo and a top sheen. */
    void setLens(boolean lens) {
        this.lens = lens;
        invalidate();
    }

    /** The icon drawn inside the top-bar lens, already tinted for the selected state. */
    void setLensIcon(Drawable icon, int sizePx) {
        lensIcon = icon;
        lensIconSize = sizePx;
        invalidate();
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    /**
     * Makes the slab lean and shimmer while the focus spring is moving. [vx] and [vy] are the
     * spring velocities in pixels per second; the speed is normalised so a slow glide stays subtle.
     */
    void setMotionStrength(float strength) {
        motionStrength = Math.max(0f, Math.min(1.5f, strength));
    }

    void setMotion(float vx, float vy) {
        float speed = (float) Math.hypot(vx, vy);
        float next = speed > 1f ? Math.min(1f, speed / 2200f) : 0f;
        float nx = speed > 1f ? vx / speed : 0f;
        float ny = speed > 1f ? vy / speed : 0f;
        if (Math.abs(next - motion) < 0.008f && Math.abs(nx - motionX) < 0.015f
                && Math.abs(ny - motionY) < 0.015f) return;
        motion = next;
        motionX = nx;
        motionY = ny;
        invalidate();
    }

    /** Stops the motion effect when the spring lands or the glass is hidden. */
    void clearMotion() {
        if (motion == 0f && motionX == 0f && motionY == 0f) return;
        motion = 0f;
        motionX = 0f;
        motionY = 0f;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0x3DFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
        specular.setShader(new LinearGradient(0, 0, 0, h * 0.45f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        // A diagonal highlight streak that is only visible while the slab moves. Shifting the draw rather
        // than rebuilding a shader keeps the spring frame cheap.
        motionSheen.setShader(new LinearGradient(0, 0, w, h,
                new int[]{0x00FFFFFF, 0x2AFFFFFF, 0x00FFFFFF},
                new float[]{0.24f, 0.50f, 0.76f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = 6f * density;   // room for the glow
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        float m = Math.min(1.35f, motion * motionStrength);
        float dx = motionX * m;
        float dy = motionY * m;
        // A moving slab stretches a hair in its direction of travel and lifts its live rim. The 6dp inset
        // is exactly the room this needs, so the glow is not clipped at the tile edge.
        float drawRadius = radius + (flat ? 0f : 1.2f * density * m);
        int save = canvas.save();
        if (!flat && m > 0.01f) {
            // A small lean in the direction of travel. Full (up to 1.35) is clearly stronger than Subtil (0.62).
            canvas.rotate(motionX * 2.8f * m, getWidth() / 2f, getHeight() / 2f);
            float sx = 1f + 0.024f * m * Math.abs(dx) - 0.010f * m * Math.abs(dy);
            float sy = 1f + 0.024f * m * Math.abs(dy) - 0.010f * m * Math.abs(dx);
            canvas.scale(sx, sy, getWidth() / 2f, getHeight() / 2f);
        }

        // Outer glow: thin rings across the inset, each a little fainter, so it fades out smoothly. Three wide
        // strokes left visible bands once the glow took an app's colour at a stronger alpha. The hardware layer
        // caches the result, so the rings cost one draw, not one per frame of the spring.
        if (lens) {
            drawLens(canvas, drawRadius, m, dx, dy);
            canvas.restoreToCount(save);
            return;
        }
        if (!flat || halo) {
            int rings = 12;
            float step = inset / rings;
            int alpha = flat ? glowAlpha / 2 : glowAlpha;
            alpha = Math.min(255, Math.round(alpha * (1f + 0.58f * m)));
            glow.setStrokeWidth(step + 0.5f);
            for (int i = 0; i < rings; i++) {
                float o = step * (i + 0.5f);
                float t = 1f - (float) i / rings;
                glow.setColor(Math.round(alpha * t) << 24 | glowRgb);
                canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o,
                        drawRadius + o, drawRadius + o, glow);
            }
        }
        if (flat) {
            int rimAlpha = rim.getAlpha();
            float rimStroke = rim.getStrokeWidth();
            rim.setAlpha(Math.min(255, Math.round(rimAlpha * (1f + 0.30f * m))));
            rim.setStrokeWidth(rimStroke + 0.35f * density * m);
            canvas.drawRoundRect(rect, drawRadius, drawRadius, rim);
            rim.setAlpha(rimAlpha);
            rim.setStrokeWidth(rimStroke);
            canvas.restoreToCount(save);
            return;
        }

        // A faint glass trail follows the slab and fades as it lands: the moving edge reads as liquid rather
        // than as a hard rectangle sliding over the wallpaper.
        if (m > 0.02f) {
            float trailOffset = 10f * density * m;
            trail.setColor(Math.round(0x3C * m) << 24 | 0xFFFFFF);
            canvas.drawRoundRect(rect.left - dx * trailOffset, rect.top - dy * trailOffset,
                    rect.right - dx * trailOffset, rect.bottom - dy * trailOffset,
                    drawRadius, drawRadius, trail);
        }

        canvas.drawRoundRect(rect, drawRadius, drawRadius, fill);

        // Sheen across the top 45%: clipRect stays on the GPU (clipPath did not on the MT5895) and
        // the round rect under it keeps the top corners rounded with a flat bottom edge.
        canvas.save();
        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f);
        canvas.drawRoundRect(rect, drawRadius, drawRadius, specular);
        canvas.restore();

        // A short diagonal highlight travels with the motion, then disappears at rest.
        if (m > 0.02f) {
            canvas.save();
            canvas.clipRect(rect.left, rect.top, rect.right, rect.bottom);
            canvas.translate(-dx * rect.width() * 0.10f - dy * rect.height() * 0.04f,
                    -dy * rect.height() * 0.10f + dx * rect.width() * 0.04f);
            motionSheen.setAlpha(Math.round(255 * 0.70f * m));
            canvas.drawRoundRect(rect, drawRadius, drawRadius, motionSheen);
            canvas.restore();
        }

        int rimAlpha = rim.getAlpha();
        float rimStroke = rim.getStrokeWidth();
        rim.setAlpha(Math.min(255, Math.round(rimAlpha * (1f + 0.42f * m))));
        rim.setStrokeWidth(rimStroke + 0.60f * density * m);
        canvas.drawRoundRect(rect, drawRadius, drawRadius, rim);
        rim.setAlpha(rimAlpha);
        rim.setStrokeWidth(rimStroke);
        canvas.restoreToCount(save);
    }

    /** A filled glass lens for the selected top-bar button; no stroke and no white ring. */
    private void drawLens(Canvas canvas, float drawRadius, float m, float dx, float dy) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float r = Math.min(rect.width(), rect.height()) / 2f;
        if (r <= 1f) return;
        Paint lensPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Opaque enough for a black glyph, with a soft last two pixels instead of a hard outline.
        lensPaint.setShader(new RadialGradient(cx, cy, r,
                new int[]{0xF8FFFFFF, 0xF2FFFFFF, 0xD8FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.78f, 0.955f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, lensPaint);
        // Top specular: a narrow light cap, still inside the lens.
        lensPaint.setShader(new LinearGradient(0, cy - r, 0, cy - r * 0.15f,
                0x32FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.save();
        canvas.clipRect(cx - r, cy - r, cx + r, cy);
        canvas.drawCircle(cx, cy, r, lensPaint);
        canvas.restore();
        if (lensIcon != null && lensIconSize > 0) {
            int half = lensIconSize / 2;
            lensIcon.setBounds(Math.round(cx) - half, Math.round(cy) - half,
                    Math.round(cx) + half, Math.round(cy) + half);
            lensIcon.draw(canvas);
        }
    }
}
