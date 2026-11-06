package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Puts a baked Liquid Glass bitmap behind a view, using the wallpaper Home is showing. The bake runs
 * off the UI thread; until it lands the view keeps its normal translucent fallback drawable.
 */
final class GlassBackground {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<View, Integer> GENERATIONS = new WeakHashMap<>();
    private static Bitmap cache;
    private static String cacheKey = "";

    interface BitmapReady {
        void onBitmap(Bitmap bitmap);
    }

    interface DrawableReady {
        void onDrawable(Drawable drawable);
    }

    private GlassBackground() {
    }

    /** Turns the baked bitmap into the drawable the view needs (plain, or with a focus state). */
    interface Decorator {
        Drawable decorate(Context context, Bitmap glass);
    }

    /** Call after a layout pass; [radiusDp] is the view's corner radius. */
    static void apply(Context context, View view, float radiusDp) {
        apply(context, view, radiusDp, null);
    }

    static void apply(Context context, View view, float radiusDp, Decorator decorator) {
        if (view == null || !Config.liquidGlass(context)) return;
        if (Config.glassFullyTransparent(context)) {
            next(view);
            view.setBackground(null);
            return;
        }
        int generation = next(view);
        view.post(() -> bakeWhenMeasured(context, view, radiusDp, generation, 0, decorator));
    }

    /** Drops a pending bake, for example when the clock style leaves the HiTech card. */
    static void cancel(View view) {
        if (view != null) next(view);
    }

    /** Bakes a slab for a screen rect; used by the dock, which draws its band in an ItemDecoration. */
    static void bakeRect(Context context, Rect rect, float radiusPx, BitmapReady ready) {
        if (!Config.liquidGlass(context) || Config.glassFullyTransparent(context)) return;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int screenW = metrics.widthPixels;
        int screenH = metrics.heightPixels;
        WORKER.execute(() -> {
            Source source = source(context);
            if (source == null) return;
            Bitmap glass = LiquidGlass.bake(source.bitmap, screenW, screenH, rect, radiusPx,
                    source.dimColor, 1f, Config.glassOpacity(context));
            if (glass != null) MAIN.post(() -> ready.onBitmap(glass));
        });
    }

    private static void bakeWhenMeasured(Context context, View view, float radiusDp,
                                         int generation, int attempt, Decorator decorator) {
        if (generation != generation(view)) return;
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            if (attempt < 8) {
                view.postDelayed(() -> bakeWhenMeasured(context, view, radiusDp, generation, attempt + 1,
                        decorator), 32);
            }
            return;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        Rect rect = new Rect(location[0], location[1], location[0] + width, location[1] + height);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float radius = radiusDp * metrics.density;
        int screenW = metrics.widthPixels;
        int screenH = metrics.heightPixels;
        WORKER.execute(() -> {
            Source source = source(context);
            if (source == null) return;
            Bitmap glass = LiquidGlass.bake(source.bitmap, screenW, screenH, rect, radius,
                    source.dimColor, 1f, Config.glassOpacity(context));
            if (glass == null) return;
            view.post(() -> {
                if (generation != generation(view)) {
                    glass.recycle();
                    return;
                }
                view.setBackground(decorator == null
                        ? new BitmapDrawable(context.getResources(), glass)
                        : decorator.decorate(context, glass));
            });
        });
    }

    static Drawable capsule(Context context, Bitmap glass, float radiusDp) {
        return capsule(context, glass, radiusDp, 0);
    }

    /** The clock capsule's finish: a brighter rim and a top specular sheen over the baked glass. */
    static Drawable capsule(Context context, Bitmap glass, float radiusDp, int tint) {
        Resources res = context.getResources();
        float density = res.getDisplayMetrics().density;
        float radius = radiusDp * density;
        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x4DFFFFFF, 0x00FFFFFF});
        shine.setCornerRadius(radius);
        GradientDrawable depth = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x12000000});
        depth.setCornerRadius(radius);
        java.util.ArrayList<Drawable> layers = new java.util.ArrayList<>();
        layers.add(new BitmapDrawable(res, glass));
        if (Color.alpha(tint) != 0) {
            GradientDrawable tintLayer = new GradientDrawable();
            tintLayer.setColor(tint);
            tintLayer.setCornerRadius(radius);
            layers.add(tintLayer);
        }
        layers.add(shine);
        layers.add(depth);
        return new LayerDrawable(layers.toArray(new Drawable[0]));
    }

    /** A soft blurred wallpaper for a big surface such as Settings, under a dark readable scrim. */
    static void backdrop(Context context, DrawableReady ready) {
        if (!Config.liquidGlass(context)) return;
        WORKER.execute(() -> {
            Source source = source(context);
            if (source == null) return;
            Bitmap blurred = source.bitmap.copy(Bitmap.Config.ARGB_8888, true);
            if (blurred == null) return;
            int width = blurred.getWidth();
            int height = blurred.getHeight();
            int[] pixels = new int[width * height];
            blurred.getPixels(pixels, 0, width, 0, 0, width, height);
            for (int pass = 0; pass < 3; pass++) LiquidGlass.boxBlur(pixels, width, height, 2);
            blurred.setPixels(pixels, 0, width, 0, 0, width, height);
            Drawable image = new BitmapDrawable(context.getResources(), blurred);
            int scrim = 0x30 + Math.round(0x80 * Config.glassOpacity(context));
            LayerDrawable layer = new LayerDrawable(new Drawable[]{
                    image, new ColorDrawable((scrim << 24) | 0x000A0E14)});
            MAIN.post(() -> ready.onDrawable(layer));
        });
    }

    /** The current wallpaper decoded small enough to blur, plus the dim painted over it on Home. */
    private static synchronized Source source(Context context) {
        Config config;
        try {
            config = Config.load(context);
        } catch (RuntimeException unavailable) {
            return null;
        }
        List<File> wallpapers = config.wallpapers;
        if (wallpapers.isEmpty()) return null;
        SharedPreferences prefs = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        File file = WallpaperRotation.find(wallpapers, prefs.getString("image", ""));
        if (file == null) file = wallpapers.get(0);
        int dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        String key = file.getPath() + ':' + file.length() + ':' + file.lastModified() + ':' + dimColor;
        if (key.equals(cacheKey) && cache != null) return new Source(cache, dimColor);

        Bitmap source = decode(file);
        if (source == null) return null;
        cache = source;
        cacheKey = key;
        return new Source(source, dimColor);
    }

    private static Bitmap decode(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 480) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try {
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (OutOfMemoryError | RuntimeException failure) {
            return null;
        }
    }

    private static int next(View view) {
        int value = generation(view) + 1;
        GENERATIONS.put(view, value);
        return value;
    }

    private static int generation(View view) {
        Integer value = GENERATIONS.get(view);
        return value == null ? 0 : value;
    }

    private static final class Source {
        final Bitmap bitmap;
        final int dimColor;

        Source(Bitmap bitmap, int dimColor) {
            this.bitmap = bitmap;
            this.dimColor = dimColor;
        }
    }
}
