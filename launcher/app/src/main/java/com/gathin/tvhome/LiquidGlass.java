package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * Liquid Glass baked on the CPU: the wallpaper behind a rounded slab is blurred, pulled in at the rim
 * like a thick glass edge, and given a lit rim. Android 11 has no runtime shader, but the wallpaper is
 * a still picture, so this can be computed whenever the picture changes and shown as a plain bitmap.
 * The algorithm follows the AndroidLiquidGlass / Tigris approach.
 */
final class LiquidGlass {
    private LiquidGlass() {
    }

    /**
     * Bakes a glass slab covering screen [rect] over [source], which the screen shows centre-cropped.
     * [dimColor] is painted over the wallpaper first so the glass matches the dimmed Home background.
     */
    static Bitmap bake(Bitmap source, int screenW, int screenH, Rect rect, float radius,
                       int dimColor, float frostStrength, float glassOpacity) {
        int w = rect.width();
        int h = rect.height();
        if (w <= 0 || h <= 0 || source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return null;
        }
        float cornerRadius = Math.max(0f, Math.min(radius, Math.min(w, h) / 2f));
        float zone = Math.min(h * 0.30f, cornerRadius * 1.6f);
        float pull = zone;
        int blurR = 7;
        int margin = (int) (pull + blurR * 2 + 2);
        int x0 = rect.left - margin;
        int y0 = rect.top - margin;
        int cw = w + margin * 2;
        int ch = h + margin * 2;
        if (cw <= 0 || ch <= 0) return null;

        Bitmap region = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(region);
        canvas.drawColor(Color.BLACK);
        canvas.translate(-x0, -y0);
        float s = Math.max(screenW / (float) source.getWidth(), screenH / (float) source.getHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(s, s);
        matrix.postTranslate((screenW - source.getWidth() * s) / 2f,
                (screenH - source.getHeight() * s) / 2f);
        canvas.drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
        if (Color.alpha(dimColor) != 0) canvas.drawColor(dimColor);

        int[] src = new int[cw * ch];
        region.getPixels(src, 0, cw, 0, 0, cw, ch);
        region.recycle();
        for (int pass = 0; pass < 2; pass++) boxBlur(src, cw, ch, blurR);

        int[] out = new int[w * h];
        float hw = w / 2f;
        float hh = h / 2f;
        float rimW = 2.8f;
        float frost = Math.max(0f, Math.min(1f, frostStrength));
        float opacity = Math.max(0f, Math.min(1f, glassOpacity));
        float transmission = 1f - 0.11f * frost;
        float lift = 26f * frost;
        for (int j = 0; j < h; j++) {
            float py = j + 0.5f;
            for (int i = 0; i < w; i++) {
                float px = i + 0.5f;
                // Signed distance to the rounded rectangle (negative inside).
                float qx = Math.abs(px - hw) - (hw - cornerRadius);
                float qy = Math.abs(py - hh) - (hh - cornerRadius);
                float ox = Math.max(qx, 0f);
                float oy = Math.max(qy, 0f);
                float sdf = (float) Math.sqrt(ox * ox + oy * oy)
                        + Math.min(Math.max(qx, qy), 0f) - cornerRadius;
                float a = Math.max(0f, Math.min(1f, 0.5f - sdf));
                if (a <= 0f) continue;
                float depth = -sdf;
                float nx;
                float ny;
                if (qx > 0f && qy > 0f) {
                    float l = Math.max(1e-3f, (float) Math.sqrt(ox * ox + oy * oy));
                    nx = ox / l;
                    ny = oy / l;
                } else if (qx > qy) {
                    nx = 1f;
                    ny = 0f;
                } else {
                    nx = 0f;
                    ny = 1f;
                }
                if (px < hw) nx = -nx;
                if (py < hh) ny = -ny;

                float t = Math.max(0f, Math.min(1f, 1f - depth / zone));
                float off = t * t * pull;
                int c = sample(src, cw, ch, px + nx * off + margin, py + ny * off + margin);
                float r = Color.red(c);
                float g = Color.green(c);
                float b = Color.blue(c);
                // A little milky and a little more saturated than what is behind.
                float luma = r * 0.3f + g * 0.59f + b * 0.11f;
                r = luma + (r - luma) * 1.28f;
                g = luma + (g - luma) * 1.28f;
                b = luma + (b - luma) * 1.28f;
                r = r * transmission + lift;
                g = g * transmission + lift;
                b = b * transmission + lift;

                // Broad top-left gloss: the lit face of a thick slab, not only its edge.
                float glossX = 1f - px / w;
                float glossY = 1f - py / h;
                float gloss = Math.max(0f, glossX * 0.65f + glossY * 0.35f - 0.34f) / 0.66f;
                gloss = gloss * gloss * 0.065f * frost;
                r += (255f - r) * gloss;
                g += (255f - g) * gloss;
                b += (255f - b) * gloss;

                // A faint bottom shade gives the slab weight where it meets the wallpaper.
                float bottom = Math.max(0f, py / h - 0.48f) / 0.52f;
                float shade = bottom * bottom * 0.055f * frost;
                r *= 1f - shade;
                g *= 1f - shade;
                b *= 1f - shade;

                // Specular rim: strongest where it faces the light (top left), softer on the opposite edge.
                float facing = -(nx * 0.45f + ny * 0.9f);
                float light = 0.3f + 0.7f * Math.max(facing, 0f) + 0.35f * Math.max(-facing, 0f);
                if (depth < rimW) {
                    float k = Math.max(0f, Math.min(1f, 1f - depth / rimW)) * (0.35f + 0.6f * light);
                    // A cool fringe on the lit edge reads as refraction without a per-channel sample.
                    r += (255f - r) * k * 0.92f;
                    g += (255f - g) * k;
                    b += (255f - b) * k * 1.06f;
                }
                // Soft inner glow just inside the lit edge.
                if (depth < zone) {
                    float k = (1f - depth / zone) * 0.18f * Math.max(facing, 0f);
                    r += (255f - r) * k;
                    g += (255f - g) * k;
                    b += (255f - b) * k;
                }
                out[j * w + i] = Color.argb((int) (a * opacity * 255f), clamp(r), clamp(g), clamp(b));
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888);
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, (int) v));
    }

    private static int sample(int[] px, int w, int h, float x, float y) {
        int sx = Math.max(0, Math.min(w - 1, (int) x));
        int sy = Math.max(0, Math.min(h - 1, (int) y));
        return px[sy * w + sx];
    }

    /** Separable box blur in place; three channels, opaque output. */
    static void boxBlur(int[] px, int w, int h, int r) {
        int[] tmp = new int[px.length];
        pass(px, tmp, w, h, r, true);
        pass(tmp, px, w, h, r, false);
    }

    private static void pass(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w;
        int len = horizontal ? w : h;
        int div = r * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int sr = 0, sg = 0, sb = 0;
            for (int k = -r; k <= r; k++) {
                int c = at(src, w, h, line, len, k, horizontal);
                sr += (c >> 16) & 255;
                sg += (c >> 8) & 255;
                sb += c & 255;
            }
            for (int k = 0; k < len; k++) {
                int idx = horizontal ? line * w + k : k * w + line;
                dst[idx] = (0xFF << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = at(src, w, h, line, len, k + r + 1, horizontal);
                int sub = at(src, w, h, line, len, k - r, horizontal);
                sr += ((add >> 16) & 255) - ((sub >> 16) & 255);
                sg += ((add >> 8) & 255) - ((sub >> 8) & 255);
                sb += (add & 255) - (sub & 255);
            }
        }
    }

    private static int at(int[] src, int w, int h, int line, int len,
                          int pos, boolean horizontal) {
        int p = Math.max(0, Math.min(len - 1, pos));
        return horizontal ? src[line * w + p] : src[p * w + line];
    }
}
