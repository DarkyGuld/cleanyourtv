package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    private final FrameLayout root;
    private final ImageView image;
    private final ImageView fade;
    private final ImageView media;
    private final int living;
    private android.animation.ValueAnimator livingAnim;
    private long lastLivingFrame;
    /** Focus parallax added on top of the living pan instead of replacing it. */
    private float parallaxX, parallaxY, parallaxScale = 1f;
    private float parallaxTargetX, parallaxTargetY, parallaxTargetScale = 1f;
    private final int backgroundColor;
    private final List<File> files;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    /** Told after a new picture is on screen, so the baked glass can be refreshed. */
    interface Listener {
        void onWallpaperChanged();
    }

    private Listener listener;
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    /** wallpaper_dim as an ARGB black, painted into each picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The picture on screen, released once the next one has been drawn. */
    private Bitmap current;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private int showGeneration;
    private int mediaGeneration;
    private String displayed = "";
    /** wallpaper_captions: the shown picture's name from wallpapers/captions.txt; null when off or there are none. */
    private final TextView caption;
    private final Map<String, String> names;
    /** wallpaper_moods: the collection's groups by time of day; empty rotates through all of it. */
    private final Map<String, Set<String>> moods;
    /** config.xml latitude and longitude for the sun's times, and today's {sunrise, sunset} once worked out. */
    private final String latitude, longitude;
    private LocalDate sunDay;
    private int[] sun;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        backgroundColor = config.backgroundColor;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        // The dim is painted into the picture (decode), so no full-screen shade layer is drawn on every frame.
        dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        moods = config.wallpaperMoods;
        latitude = moods.isEmpty() ? "" : context.getString(R.string.latitude);
        longitude = moods.isEmpty() ? "" : context.getString(R.string.longitude);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        living = Config.livingWallpaper(context);
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        fade = living > 0 ? new ImageView(context) : null;
        if (fade != null) {
            fade.setScaleType(ImageView.ScaleType.CENTER_CROP);
            fade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            fade.setAlpha(0f);
        }
        media = new ImageView(context);
        media.setScaleType(ImageView.ScaleType.CENTER_CROP);
        media.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        media.setAlpha(0f);
        media.setColorFilter(new android.graphics.PorterDuffColorFilter(0x99101419, android.graphics.PorterDuff.Mode.SRC_OVER));
        names = config.wallpaperCaptions && config.wallpaperEnabled && !files.isEmpty()
                ? WallpaperCaptions.read(files.get(0).getParentFile()) : Collections.emptyMap();
        caption = names.isEmpty() ? null : makeCaption(context, root);
        if (config.wallpaperEnabled && !files.isEmpty()) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (fade != null) root.addView(fade, 1, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            showFirst();
        }
        root.addView(media, Math.min(fade == null ? 1 : 2, root.getChildCount()),
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        List<File> playable = playable();
        File first = WallpaperRotation.find(playable, previous);
        if (first == null) first = WallpaperRotation.pick(playable, previous, order, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                show(bitmap);
                // The picture covers the whole screen: the root's plain colour under it is overdraw.
                root.setBackground(null);
                displayed = first.getName();
                showCaption(false);
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                notifyWallpaperChanged();
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            playable = playable();
            first = WallpaperRotation.pick(playable, previous, order, random);
        }
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Aura-style full-screen focus motion: a slow, subtle counter-move of the photograph. */
    void focus(float normalizedX, float normalizedY, boolean animate) {
        if (image.getDrawable() == null) return;
        float dx = -Math.max(-1f, Math.min(1f, normalizedX)) * 10f * image.getResources().getDisplayMetrics().density;
        float dy = -Math.max(-1f, Math.min(1f, normalizedY)) * 6f * image.getResources().getDisplayMetrics().density;
        if (living > 0) {
            // Keep the living pan running; the parallax becomes a small offset on top of it. Stopping the
            // animator here made "Living wallpaper" look broken whenever a tile had focus.
            parallaxTargetX = dx;
            parallaxTargetY = dy;
            parallaxTargetScale = 1.02f;
            return;
        }
        stopLiving();
        image.animate().cancel();
        if (!animate) {
            image.setTranslationX(dx);
            image.setTranslationY(dy);
            image.setScaleX(1.02f);
            image.setScaleY(1.02f);
        } else {
            image.animate().translationX(dx).translationY(dy).scaleX(1.02f).scaleY(1.02f)
                    .setDuration(620).setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f)).start();
        }
    }

    /** Stops the parallax when focus leaves the tiles. */
    void resetFocus(boolean animate) {
        if (image.getDrawable() == null) return;
        if (living > 0) {
            parallaxTargetX = 0f;
            parallaxTargetY = 0f;
            parallaxTargetScale = 1f;
            return;
        }
        image.animate().cancel();
        if (!animate) {
            image.setTranslationX(0f);
            image.setTranslationY(0f);
            image.setScaleX(1f);
            image.setScaleY(1f);
            startLiving();
        } else {
            image.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(520).setInterpolator(new android.view.animation.DecelerateInterpolator(1.2f))
                    .withEndAction(this::startLiving).start();
        }
    }

    /** Aura's living photograph: a slow pan and zoom that keeps the still wallpaper breathing. */
    private void startLiving() {
        // active is false while Home is being built behind Launcher settings: do not animate there until
        // onResume's start() says Home is really in front again.
        if (!active || living <= 0 || current == null || image.getDrawable() == null) return;
        stopLiving();
        float density = image.getResources().getDisplayMetrics().density;
        livingAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        livingAnim.setDuration(living == 2 ? 18_000L : 32_000L);
        livingAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        livingAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        livingAnim.setInterpolator(new android.view.animation.LinearInterpolator());
        livingAnim.addUpdateListener(a -> {
            // The pan is extremely slow (18-32 s each way), so 35 fps is visually identical to 60 and halves the
            // number of full-screen wallpaper rescales the TV's GPU has to composite.
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastLivingFrame < 28) return;
            lastLivingFrame = now;
            // Ease the focus offset on top of the slow pan; a separate 620 ms view animation would fight this one.
            parallaxX += (parallaxTargetX - parallaxX) * 0.16f;
            parallaxY += (parallaxTargetY - parallaxY) * 0.16f;
            parallaxScale += (parallaxTargetScale - parallaxScale) * 0.16f;
            float f = (float) a.getAnimatedValue();
            float zoom = living == 2 ? 0.065f : 0.038f;
            float dx = (f - 0.5f) * (living == 2 ? 18f : 10f) * density;
            float dy = (f - 0.5f) * (living == 2 ? 10f : 6f) * density;
            float scale = (1f + zoom * 0.55f + zoom * f * 0.45f) * parallaxScale;
            image.setScaleX(scale);
            image.setScaleY(scale);
            image.setTranslationX(dx + parallaxX);
            image.setTranslationY(dy + parallaxY);
        });
        livingAnim.start();
    }

    private void stopLiving() {
        if (livingAnim != null) {
            livingAnim.cancel();
            livingAnim = null;
        }
    }

    void start() {
        active = true;
        update();
        // Resume the slow pan without restarting it: a paused ValueAnimator keeps its phase and costs no frames.
        if (living > 0) {
            if (livingAnim != null && livingAnim.isPaused()) livingAnim.resume();
            else if (livingAnim == null) startLiving();
        }
    }

    void stop() {
        active = false;
        // Home is behind an app or the settings overlay: a full-screen transform at 60 fps here would keep the
        // whole TV busy for a wallpaper nobody can see. Pause, don't cancel, so returning to Home is seamless.
        if (livingAnim != null && livingAnim.isRunning()) livingAnim.pause();
    }

    /** Drops the wallpaper texture when Home is hidden and Android asks the process to free memory. */
    void releaseForMemoryPressure() {
        stopLiving();
        mediaGeneration++;
        media.animate().cancel();
        media.setImageDrawable(null);
        media.setAlpha(0f);
        showGeneration++;
        image.setImageDrawable(null);
        if (fade != null) {
            fade.animate().cancel();
            fade.setImageDrawable(null);
            fade.setAlpha(0f);
        }
        // The renderer may still have a queued frame that references this hardware bitmap. Drop our references and
        // let Android reclaim its native buffer after rendering releases it instead of recycling it mid-frame.
        current = null;
        displayed = "";
        root.setBackgroundColor(backgroundColor);
    }

    /** The pictures the rotation picks from now: wallpaper_moods' group for the time of day, or the whole collection. */
    private List<File> playable() {
        if (moods.isEmpty()) return files;
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(sunDay)) {
            sunDay = now.toLocalDate();
            sun = WallpaperMood.sun(sunDay, latitude, longitude, ZoneId.systemDefault());
        }
        return WallpaperMood.select(files, moods, WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]));
    }

    /** Glassio-style media backdrop: the current album or title artwork, dimmed behind Home. */
    void setMediaArt(Bitmap bitmap) {
        final int generation = ++mediaGeneration;
        media.animate().cancel();
        if (bitmap == null) {
            fadeMediaDrawable(generation);
            return;
        }
        media.setImageBitmap(bitmap);
        media.setAlpha(0f);
        media.animate().alpha(1f).setDuration(420).start();
    }

    void clearMediaArt() {
        final int generation = ++mediaGeneration;
        media.animate().cancel();
        fadeMediaDrawable(generation);
    }

    private void fadeMediaDrawable(int generation) {
        media.animate().alpha(0f).setDuration(260).withEndAction(() -> {
            if (generation == mediaGeneration) media.setImageDrawable(null);
        }).start();
    }

    void close() {
        active = false;
        closed = true;
        showGeneration++;
        mediaGeneration++;
        stopLiving();
        image.animate().cancel();
        if (fade != null) fade.animate().cancel();
        media.animate().cancel();
        if (caption != null) caption.animate().cancel();
        decoder.shutdownNow();
    }

    void update() {
        if (!active || closed || loading || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        // A picture outside the time of day's group (the group changed) is replaced at once, not after the interval.
        List<File> playable = playable();
        File selected = WallpaperRotation.find(playable, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(playable, previous, order, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        try {
            decoder.execute(() -> {
                Bitmap bitmap = decode(next);
                root.post(() -> {
                    loading = false;
                    if (closed || !active) {
                        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                        return;
                    }
                    if (bitmap == null) {
                        files.remove(next);
                        Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                        update();
                        return;
                    }
                    show(bitmap);
                    displayed = next.getName();
                    showCaption(true);
                    if (!displayed.equals(previous) || changed == 0) {
                        preferences.edit().putString("image", displayed)
                                .putLong("changed", System.currentTimeMillis()).apply();
                    }
                    notifyWallpaperChanged();
                });
            });
        } catch (RuntimeException gone) {
            loading = false;
        }
    }

    private void notifyWallpaperChanged() {
        if (listener != null) listener.onWallpaperChanged();
    }

    /**
     * Small light text under the top-right buttons, above everything else and never focusable. It sits where the
     * top bar ends in every layout, so it does not cover the clock, the stats line or the tiles.
     */
    private static TextView makeCaption(Context context, FrameLayout root) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(0xD9FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Math.round(520 * dp));
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(UiFont.get(context, R.string.font_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = Math.round(110 * dp);
        params.setMarginEnd(Math.round(62 * dp));
        root.addView(text, params);
        return text;
    }

    /** The displayed picture's name, faded across a change; a picture without a name hides the caption. */
    private void showCaption(boolean fade) {
        if (caption == null) return;
        String name = names.get(displayed);
        caption.animate().cancel();
        if (name == null) {
            caption.setAlpha(0f);
            return;
        }
        caption.setContentDescription(name);
        if (!fade || caption.getAlpha() == 0f) {
            caption.setText(name);
            caption.setAlpha(1f);
            return;
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            caption.setText(name);
            caption.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    /** Puts a picture on screen and releases the previous one after a frame has drawn the new one. */
    private void show(Bitmap bitmap) {
        Bitmap old = current;
        current = bitmap;
        final int generation = ++showGeneration;
        // The wallpaper covers the whole root; avoid drawing the fallback colour under it.
        root.setBackground(null);
        if (living > 0 && fade != null && old != null && old != bitmap) {
            stopLiving();
            fade.animate().cancel();
            fade.setImageBitmap(bitmap);
            fade.setAlpha(0f);
            fade.animate().alpha(1f).setDuration(living == 2 ? 700 : 1100).withEndAction(() -> {
                if (generation != showGeneration || closed) {
                    releaseLater(old);
                    return;
                }
                image.setImageBitmap(bitmap);
                fade.setAlpha(0f);
                fade.setImageDrawable(null);
                releaseLater(old);
                startLiving();
            }).start();
        } else {
            stopLiving();
            if (fade != null) {
                fade.animate().cancel();
                fade.setImageDrawable(null);
                fade.setAlpha(0f);
            }
            image.setImageBitmap(bitmap);
            releaseLater(old);
            startLiving();
        }
    }

    /**
     * A replaced hardware bitmap may still be queued in RenderThread. Keep it alive long enough for the next
     * frames, and never recycle the picture that is current again by then.
     */
    private void releaseLater(Bitmap bitmap) {
        if (bitmap == null || bitmap == current || bitmap.isRecycled()) return;
        long delay = bitmap.getConfig() == Bitmap.Config.HARDWARE ? 1200L : 500L;
        root.postDelayed(() -> {
            if (!bitmap.isRecycled() && bitmap != current) bitmap.recycle();
        }, delay);
    }

    private Bitmap decode(File file) {
        Bitmap decoded = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            options.inMutable = dimColor != 0;
            decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            if (dimColor != 0) new Canvas(decoded).drawColor(dimColor);
            // One copy in GPU memory instead of a software bitmap plus its texture; the decode stays off the UI
            // thread for rotations, and Home's return no longer uploads 4-8 MB to the GPU.
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null || gpu == decoded) {
                Bitmap result = decoded;
                decoded = null;
                return result;
            }
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        } finally {
            if (decoded != null && !decoded.isRecycled()) decoded.recycle();
        }
    }
}
