package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * The Glass icon pack: one crisp app icon on a dark glass tile, with a quiet glow in the app's
 * accent colour behind it. The tile itself uses the clock capsule's rim and top sheen; there is
 * never a second copy of the logo behind the real one.
 */
final class GlassIcons {
    private static final int SIZE = 256;
    private static final String YOUTUBE_PACKAGE = "io.gh.reisxd.tizentube.cobalt";
    private static Bitmap youtubeIcon;

    private GlassIcons() {
    }

    /** Square glass icon for the app library and lists. */
    static Drawable create(Context context, String pkg, String label, Drawable source) {
        return tile(context, pkg, label, source, SIZE, SIZE);
    }

    /** Full-bleed glass tile for a wide Home tile. */
    static Drawable tile(Context context, String pkg, String label, Drawable source, int width, int height) {
        if (width <= 1 || height <= 1) return null;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float radius = Math.min(width, height) * 0.24f;
        Path shape = new Path();
        shape.addRoundRect(new RectF(0, 0, width, height), radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(shape);

        drawBackdrop(canvas, pkg, label, source, width, height, Config.glassOpacity(context));
        int side = Math.round(Math.min(width, height) * 0.76f);
        if (YOUTUBE_PACKAGE.equals(pkg)) {
            Bitmap mark = youtubeIcon(context);
            if (mark != null) {
                RectF target = new RectF((width - side) / 2f, (height - side) / 2f,
                        (width + side) / 2f, (height + side) / 2f);
                canvas.drawBitmap(mark, null, target, new Paint(Paint.FILTER_BITMAP_FLAG));
            }
        } else {
            Bitmap icon = iconBitmap(pkg, label, source, side);
            if (icon != null) canvas.drawBitmap(icon, (width - side) / 2f, (height - side) / 2f, null);
        }
        drawGlass(canvas, width, height);
        canvas.restore();

        drawRim(canvas, width, height, radius);
        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        drawable.setFilterBitmap(true);
        return drawable;
    }

    /** The chosen transparent glass-play YouTube mark, cached once. */
    private static synchronized Bitmap youtubeIcon(Context context) {
        if (youtubeIcon != null) return youtubeIcon;
        Drawable drawable = context.getDrawable(R.drawable.glass_youtube);
        if (drawable == null) return null;
        int size = 512;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        youtubeIcon = bitmap;
        return bitmap;
    }

    /** Dark glass with a soft accent glow: colour behind the icon, never a blurred logo. */
    private static void drawBackdrop(Canvas canvas, String pkg, String label, Drawable source,
                                     int width, int height, float opacity) {
        int accent = accentColor(pkg, label, source);
        int alpha = Math.max(0, Math.min(255, Math.round(255 * opacity)));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, width * 0.3f, height,
                new int[]{(alpha << 24) | 0x1B2633, (alpha << 24) | 0x0C121A, (alpha << 24) | 0x05080C},
                new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        int glow = Math.max(0, Math.min(255, Math.round(0x44 * opacity / 0.60f)));
        paint.setShader(new RadialGradient(width / 2f, height * 0.46f,
                Math.min(width, height) * 0.62f,
                new int[]{withAlpha(accent, glow), withAlpha(accent, 0x00)},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        int tint = Math.max(0, Math.min(255, Math.round(0x0D * opacity / 0.60f)));
        canvas.drawColor((tint << 24) | 0x000000);
    }

    /** The most saturated colour in the app icon; its average is the fallback. */
    private static int accentColor(String pkg, String label, Drawable source) {
        if (YOUTUBE_PACKAGE.equals(pkg)) return 0xFFFF0033;
        if (source == null) return monogramColor(label);
        int size = 24;
        Bitmap small = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(small);
        source.setBounds(0, 0, size, size);
        source.draw(canvas);
        int[] pixels = new int[size * size];
        small.getPixels(pixels, 0, size, 0, 0, size, size);
        small.recycle();
        int best = 0;
        float bestScore = 0f;
        long red = 0, green = 0, blue = 0;
        int count = 0;
        for (int pixel : pixels) {
            if (Color.alpha(pixel) < 64) continue;
            int r = Color.red(pixel), g = Color.green(pixel), b = Color.blue(pixel);
            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            float saturation = max == 0 ? 0f : (max - min) / (float) max;
            float luminance = (r * 0.3f + g * 0.59f + b * 0.11f) / 255f;
            float score = saturation * Math.min(1f, luminance * 1.7f);
            if (score > bestScore) {
                bestScore = score;
                best = pixel;
            }
            red += r;
            green += g;
            blue += b;
            count++;
        }
        if (bestScore > 0.24f) return 0xFF000000 | (best & 0x00FFFFFF);
        if (count > 0) {
            int average = Color.rgb((int) (red / count), (int) (green / count), (int) (blue / count));
            if (Color.red(average) + Color.green(average) + Color.blue(average) > 72) return average;
        }
        return monogramColor(label);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static Bitmap iconBitmap(String pkg, String label, Drawable source, int side) {
        Bitmap bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float radius = side * 0.32f;
        Path shape = new Path();
        shape.addRoundRect(new RectF(0, 0, side, side), radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(shape);
        if (YOUTUBE_PACKAGE.equals(pkg)) {
            canvas.drawColor(0xFFFF0033);
            drawYouTube(canvas, side, 0, 0);
        } else if (source != null) {
            int over = Math.round(side * 0.02f);
            source.setBounds(-over, -over, side + over, side + over);
            source.draw(canvas);
        } else {
            canvas.drawColor(monogramColor(label));
            drawMonogram(canvas, side, label, 0, 0);
        }
        canvas.restore();
        return bitmap;
    }

    private static void drawYouTube(Canvas canvas, int size, int left, int top) {
        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(Color.WHITE);
        Path play = new Path();
        play.moveTo(left + size * 0.41f, top + size * 0.30f);
        play.lineTo(left + size * 0.41f, top + size * 0.70f);
        play.lineTo(left + size * 0.71f, top + size * 0.50f);
        play.close();
        canvas.drawPath(play, white);
    }

    private static int monogramColor(String label) {
        int hash = label == null ? 0 : label.hashCode();
        float hue = Math.abs(hash % 360);
        return Color.HSVToColor(new float[]{hue, 0.55f, 0.68f});
    }

    private static void drawMonogram(Canvas canvas, int size, String label, int left, int top) {
        String initial = label == null || label.trim().isEmpty()
                ? "?" : label.trim().substring(0, 1).toUpperCase();
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextSize(size * 0.46f);
        text.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = text.getFontMetrics();
        float baseline = top + size / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(initial, left + size / 2f, baseline, text);
    }

    private static void drawGlass(Canvas canvas, int width, int height) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF bounds = new RectF(0, 0, width, height);
        paint.setShader(new LinearGradient(0, 0, 0, height * 0.52f,
                new int[]{0x55FFFFFF, 0x1AFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(bounds, paint);
        paint.setShader(new LinearGradient(0, 0, width, height,
                new int[]{0x24FFFFFF, 0x00FFFFFF, 0x1AFFFFFF},
                new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(bounds, paint);
        paint.setShader(new LinearGradient(0, height * 0.58f, 0, height,
                new int[]{0x00000000, 0x2A000000}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(bounds, paint);
    }

    private static void drawRim(Canvas canvas, int width, int height, float radius) {
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        float min = Math.min(width, height);
        rim.setStrokeWidth(min * 0.014f);
        rim.setColor(0x73FFFFFF);
        float inset = min * 0.008f;
        canvas.drawRoundRect(new RectF(inset, inset, width - inset, height - inset),
                radius, radius, rim);
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(min * 0.02f);
        inner.setColor(0x24FFFFFF);
        float in = min * 0.032f;
        canvas.drawRoundRect(new RectF(in, in, width - in, height - in),
                Math.max(0f, radius - in), Math.max(0f, radius - in), inner);
    }
}
