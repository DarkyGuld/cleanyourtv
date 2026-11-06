package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * A small Continue Watching shelf above the dock, in the spirit of Aura's Top Shelf. It reads
 * Android TV's local Watch Next list when READ_TV_LISTINGS is granted; artwork is only loaded from
 * content:// providers and local files, because the launcher does not do network I/O. Nothing is
 * sent anywhere.
 */
final class TopShelf {
    /** Last Watch Next/media shelf size, for settings that only make sense with 2+ cards. */
    static volatile int lastItemCount;
    private static final int MAX = 3;
    private static final long AUTO_HIDE_MS = 10_000;
    /** Hard cap on bytes read from one artwork source, before the platform's Content-Length. */
    private static final int MAX_ART_BYTES = 8 * 1024 * 1024;
    /** A card is at most 288dp wide, so never keep artwork much larger than that on a 2x screen. */
    private static final int TARGET_ART_SIDE = 640;
    private static final int ART_CACHE_BYTES = 6 * 1024 * 1024;

    static final class Item {
        String title, subtitle, pkg;
        Bitmap art;
        Intent intent;
        PendingIntent session;
        float progress = -1f;
    }

    private final Context ctx;
    private final FrameLayout root;
    private final LinearLayout panel;
    private final LinearLayout cards;
    private final TextView title;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final float density;
    private static final android.util.LruCache<String, Bitmap> ART =
            new android.util.LruCache<String, Bitmap>(ART_CACHE_BYTES) {
                @Override protected int sizeOf(String key, Bitmap value) {
                    return value == null ? 0 : value.getByteCount();
                }
            };
    private volatile int generation;
    private String showingPkg = "";
    /** Package whose shelf the viewer hid from Home; cleared when another app is focused. */
    private String dismissedPkg;
    private int lastPosition;
    private String lastPkg = "";
    private float lastBottom;
    private boolean focusWhenReady;
    private boolean stackMode;
    private boolean shelfVisible;
    private boolean pendingAnimate;
    private final java.util.List<Item> stackItems = new java.util.ArrayList<>();
    private int stackIndex;
    private final Runnable advance = this::advanceStack;
    private ExecutorService loader;
    private Future<?> loadFuture;
    private View.OnLayoutChangeListener autoHideLayout;
    private boolean memoryRegistered;

    interface CardMenu { void onLongPress(View card, Item item); }

    private final Runnable focusDock;
    private final CardMenu cardMenu;
    private final float focusGlossStrength;
    private final int cardW, cardH;
    private final boolean large;
    private final Runnable autoHide = this::autoHideNow;

    private final ComponentCallbacks2 memoryCallbacks = new ComponentCallbacks2() {
        @Override public void onTrimMemory(int level) {
            // Artwork can be re-read from the provider; a card view that is still on screen keeps
            // its own bitmap reference, so dropping the cache never blanks a visible card.
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) ART.evictAll();
        }
        @Override public void onLowMemory() { ART.evictAll(); }
        @Override public void onConfigurationChanged(Configuration newConfig) { }
    };

    TopShelf(Context ctx, FrameLayout root, Runnable focusDock, CardMenu cardMenu) {
        this.focusDock = focusDock;
        this.cardMenu = cardMenu;
        this.focusGlossStrength = Config.focusGloss(ctx) == 2 ? 1f
                : Config.focusGloss(ctx) == 1 ? 0.78f : 0f;
        // Hiding the dock app name frees the line above the dock; spend it on bigger episode pictures.
        this.large = Config.heroTitle(ctx) == 2;
        this.cardW = large ? 288 : 260;
        this.cardH = large ? 162 : 146;
        this.ctx = ctx;
        this.root = root;
        density = ctx.getResources().getDisplayMetrics().density;
        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setVisibility(View.GONE);
        panel.setAlpha(0f);
        title = new TextView(ctx);
        title.setText(ctx.getString(R.string.top_shelf_continue));
        title.setTextColor(0xD9FFFFFF);
        title.setTextSize(13);
        title.setLetterSpacing(0.14f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(dp(2), 0, 0, dp(5));
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cards = new LinearLayout(ctx);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setClipChildren(false);
        cards.setClipToPadding(false);
        panel.addView(cards, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        lp.leftMargin = dp(58);
        root.addView(panel, lp);
    }

    boolean isVisible() {
        return panel.getVisibility() == View.VISIBLE;
    }

    /** Focus the first shelf card; true when the shelf has a card to focus. */
    boolean focus() {
        if (!shelfVisible || panel.getVisibility() != View.VISIBLE
                || cards.getChildCount() == 0) return false;
        cards.getChildAt(0).requestFocus();
        return true;
    }

    /** Shows the shelf with its bottom at [bottomPx] for [pkg], or hides it when there is nothing. */
    void show(int position, String pkg, float bottomPx, boolean animate) {
        if (Config.topShelf(ctx) == 0 || pkg == null || pkg.isEmpty()
                || !Config.topShelfSourceAllowed(ctx, pkg)) {
            hide(animate);
            return;
        }
        if (dismissedPkg != null) {
            if (pkg.equals(dismissedPkg)) {
                hide(animate);
                return;
            }
            dismissedPkg = null;
        }
        lastPosition = position;
        lastPkg = pkg;
        boolean sameShelf = pkg.equals(showingPkg) && shelfVisible
                && panel.getVisibility() == View.VISIBLE && cards.getChildCount() > 0;
        lastBottom = bottomPx;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        lp.bottomMargin = Math.max(0, root.getHeight() - Math.round(bottomPx));
        panel.setLayoutParams(lp);

        // Already showing this app: the same dock position needs nothing at all, and a new one is
        // handled by the layout params above. Either way the cards do not need reloading.
        if (sameShelf) return;

        // Never leave the previous app's cards and title under the new app while its rows load.
        int gen = ++generation;
        showingPkg = pkg;
        pendingAnimate = animate;
        main.removeCallbacks(autoHide);
        main.removeCallbacks(advance);
        clearAutoHideLayout();
        shelfVisible = false;
        stackMode = false;
        stackIndex = 0;
        stackItems.clear();
        panel.animate().cancel();
        panel.setAlpha(0f);
        panel.setTranslationY(0f);
        panel.setVisibility(View.GONE);
        clearCards();
        registerMemoryCallbacks();
        submitLoad(pkg, gen);
    }

    private void submitLoad(String pkg, int gen) {
        if (loadFuture != null) loadFuture.cancel(true);
        ExecutorService exec = ensureLoader();
        try {
            loadFuture = exec.submit(() -> {
                Thread.interrupted();   // clear an interrupt left by a superseded load
                if (gen != generation) return;
                List<Item> items;
                try {
                    items = load(pkg);
                } catch (Throwable ignored) {
                    items = new ArrayList<>();
                }
                final List<Item> result = items;
                main.post(() -> {
                    if (gen != generation) return;
                    try {
                        bind(result);
                    } catch (RuntimeException ignored) {
                        bindFailed();
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            // stop() shut the loader down between the check and the submit.
        }
    }

    private ExecutorService ensureLoader() {
        if (loader == null || loader.isShutdown()) {
            loader = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "canvas-top-shelf");
                thread.setDaemon(true);
                return thread;
            });
        }
        return loader;
    }

    private void bindFailed() {
        main.removeCallbacks(autoHide);
        main.removeCallbacks(advance);
        clearAutoHideLayout();
        shelfVisible = false;
        stackMode = false;
        stackIndex = 0;
        focusWhenReady = false;
        stackItems.clear();
        lastItemCount = 0;
        panel.animate().cancel();
        panel.setAlpha(0f);
        panel.setVisibility(View.GONE);
        clearCards();
    }

    /** Starts the auto-hide timer only once cards have been attached and laid out. */
    private void postAutoHide() {
        main.removeCallbacks(autoHide);
        clearAutoHideLayout();
        if (!Config.topShelfAutoHide(ctx)) return;
        if (panel.getWidth() > 0 && panel.getHeight() > 0) {
            scheduleAutoHide();
            return;
        }
        autoHideLayout = new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                 int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (right - left <= 0 || bottom - top <= 0) return;
                panel.removeOnLayoutChangeListener(this);
                if (autoHideLayout == this) autoHideLayout = null;
                scheduleAutoHide();
            }
        };
        panel.addOnLayoutChangeListener(autoHideLayout);
    }

    private void clearAutoHideLayout() {
        if (autoHideLayout == null) return;
        panel.removeOnLayoutChangeListener(autoHideLayout);
        autoHideLayout = null;
    }

    private void scheduleAutoHide() {
        main.removeCallbacks(autoHide);
        if (!Config.topShelfAutoHide(ctx) || !shelfVisible
                || panel.getVisibility() != View.VISIBLE
                || cards.getChildCount() == 0
                || panel.getWidth() <= 0
                || panel.hasFocus()) return;
        main.postDelayed(autoHide, AUTO_HIDE_MS);
    }

    private void autoHideNow() {
        if (shelfVisible && panel.getVisibility() == View.VISIBLE
                && cards.getChildCount() > 0 && !panel.hasFocus()) {
            dismiss();
        } else {
            main.removeCallbacks(autoHide);
        }
    }

    void hide(boolean animate) {
        generation++;
        showingPkg = "";
        main.removeCallbacks(autoHide);
        main.removeCallbacks(advance);
        clearAutoHideLayout();
        shelfVisible = false;
        stackMode = false;
        stackIndex = 0;
        stackItems.clear();
        if (panel.getVisibility() != View.VISIBLE) {
            clearCards();
            panel.setAlpha(0f);
            panel.setTranslationY(0f);
            return;
        }
        int gen = generation;
        panel.animate().cancel();
        if (animate) {
            panel.animate().alpha(0f).translationY(-dp(6)).setDuration(150)
                    .withEndAction(() -> {
                        if (gen != generation) return;
                        panel.setVisibility(View.GONE);
                        clearCards();
                    }).start();
        } else {
            panel.setAlpha(0f);
            panel.setTranslationY(0f);
            panel.setVisibility(View.GONE);
            clearCards();
        }
    }

    /** Hides the shelf from Home until another app is focused, or Up brings it back. */
    boolean dismiss() {
        if (!shelfVisible || panel.getVisibility() != View.VISIBLE) return false;
        dismissedPkg = showingPkg;
        hide(true);
        return true;
    }

    boolean isDismissed() {
        return dismissedPkg != null;
    }

    /** Brings a dismissed shelf back for the current app, without moving focus. */
    void reopen() {
        if (dismissedPkg == null || lastPkg.isEmpty()) return;
        dismissedPkg = null;
        focusWhenReady = true;
        show(lastPosition, lastPkg, lastBottom, true);
    }

    /**
     * Stops all shelf work: pending callbacks, the in-flight load and the loader thread. The
     * coordinator calls this from onPause/onDestroy; a later show() starts a fresh loader.
     */
    void stop() {
        generation++;
        main.removeCallbacks(autoHide);
        main.removeCallbacks(advance);
        clearAutoHideLayout();
        if (loadFuture != null) {
            loadFuture.cancel(true);
            loadFuture = null;
        }
        if (loader != null) {
            loader.shutdownNow();
            loader = null;
        }
        unregisterMemoryCallbacks();
        ART.evictAll();   // closing the shelf lets go of its artwork as well
        shelfVisible = false;
        stackMode = false;
        stackIndex = 0;
        stackItems.clear();
        focusWhenReady = false;
        showingPkg = "";
        panel.animate().cancel();
        panel.setAlpha(0f);
        panel.setTranslationY(0f);
        panel.setVisibility(View.GONE);
        clearCards();
    }

    private void bind(List<Item> items) {
        cards.removeAllViews();
        stackItems.clear();
        if (items != null) stackItems.addAll(items);
        lastItemCount = items == null ? 0 : items.size();
        if (items == null || items.isEmpty()) {
            focusWhenReady = false;
            main.removeCallbacks(autoHide);
            main.removeCallbacks(advance);
            stackMode = false;
            stackIndex = 0;
            shelfVisible = false;
            panel.animate().cancel();
            panel.setAlpha(0f);
            panel.setVisibility(View.GONE);
            clearCards();
            return;
        }
        stackMode = Config.smartStack(ctx) && items.size() > 1;
        if (stackMode) {
            stackIndex = 0;
            renderStack();
            return;
        }
        main.removeCallbacks(advance);
        title.setText(ctx.getString(R.string.top_shelf_continue) + " · "
                + appLabel(showingPkg).toUpperCase());
        for (Item item : items) cards.addView(card(item));
        reveal(pendingAnimate);
        if (focusWhenReady && cards.getChildCount() > 0) {
            focusWhenReady = false;
            requestFocus(cards.getChildAt(0));
        }
        postAutoHide();
    }

    private void renderStack() {
        if (stackItems.isEmpty()) return;
        boolean wasVisible = shelfVisible;
        // Removing the card while it holds focus would send focus back to the dock before the new
        // card exists. Remember it and give it back once the replacement is attached.
        boolean hadFocus = cards.hasFocus();
        cards.removeAllViews();
        title.setText(ctx.getString(R.string.top_shelf_continue) + " · "
                + appLabel(showingPkg).toUpperCase());
        View cardView = card(stackItems.get(stackIndex));
        cards.addView(cardView);
        reveal(pendingAnimate);
        boolean needFocus = hadFocus;
        if (!needFocus && focusWhenReady) {
            needFocus = true;
            focusWhenReady = false;
        }
        if (needFocus) requestFocus(cardView);
        scheduleStack();
        // A timer advance must not postpone auto-hide forever: only the first bind arms it.
        if (!wasVisible) postAutoHide();
    }

    private void requestFocus(View view) {
        if (view == null) return;
        if (!view.requestFocus()) view.post(view::requestFocus);
    }

    private void reveal(boolean animate) {
        if (shelfVisible && panel.getVisibility() == View.VISIBLE) {
            // Already on screen (a stack step): don't fade the whole panel on every card change.
            panel.setAlpha(1f);
            panel.setTranslationY(0f);
            return;
        }
        panel.setVisibility(View.VISIBLE);
        panel.animate().cancel();
        if (animate) {
            panel.setAlpha(0f);
            panel.setTranslationY(-dp(8));
            panel.animate().alpha(1f).translationY(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        } else {
            panel.setAlpha(1f);
            panel.setTranslationY(0f);
        }
        shelfVisible = true;
    }

    private void clearCards() {
        cards.removeAllViews();
        title.setText("");
    }

    private void scheduleStack() {
        main.removeCallbacks(advance);
        if (!stackMode || !shelfVisible || panel.getVisibility() != View.VISIBLE
                || stackItems.size() < 2 || panel.hasFocus()) return;
        long seconds = Config.smartStackSeconds(ctx) * 1000L;
        if (seconds <= 0) return;
        main.postDelayed(advance, seconds);
    }

    private void advanceStack() {
        if (!stackMode || !shelfVisible || stackItems.size() < 2) return;
        if (panel.hasFocus()) {
            scheduleStack();
            return;
        }
        stackIndex = (stackIndex + 1) % stackItems.size();
        renderStack();
    }

    private void stepStack(int direction) {
        if (!stackMode || !shelfVisible || stackItems.size() < 2) return;
        stackIndex = (stackIndex + direction + stackItems.size()) % stackItems.size();
        renderStack();
    }

    private View card(Item item) {
        FrameLayout card = new FrameLayout(ctx);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF252A31, 0xFF101318});
        bg.setCornerRadius(dp(13));
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(13));
            }
        });
        ImageView art = new ImageView(ctx);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (item.art != null) {
            art.setImageBitmap(item.art);
        } else {
            try {
                art.setImageDrawable(ctx.getPackageManager().getApplicationIcon(item.pkg));
                art.setAlpha(0.30f);
            } catch (Exception ignored) { }
        }
        card.addView(art, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View scrim = new View(ctx);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x33000000, 0xD905070A}));
        card.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FocusGlossView gloss = new FocusGlossView(ctx, null);
        gloss.setStrength(focusGlossStrength);
        card.addView(gloss, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(12), 0, dp(12), dp(16));
        TextView name = new TextView(ctx);
        name.setText(item.title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(large ? 16 : 14);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView sub = new TextView(ctx);
        sub.setText(item.subtitle);
        sub.setTextColor(0xB3FFFFFF);
        sub.setTextSize(large ? 12 : 11);
        sub.setSingleLine(true);
        text.addView(name);
        text.addView(sub);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        card.addView(text, tlp);
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                main.removeCallbacks(autoHide);
                main.removeCallbacks(advance);
            } else {
                scheduleAutoHide();
                scheduleStack();
            }
            gloss.focus(focused, false);
            v.animate().cancel();
            v.animate().scaleX(focused ? 1.045f : 1f).scaleY(focused ? 1.045f : 1f)
                    .translationY(focused ? -dp(3) : 0f)
                    .setDuration(focused ? 180 : 140).start();
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                dismiss();
                if (focusDock != null) focusDock.run();
                return true;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (stackMode) {
                    stepStack(keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    return true;
                }
                int index = cards.indexOfChild(v);
                int next = index + (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                if (next >= 0 && next < cards.getChildCount()) {
                    cards.getChildAt(next).requestFocus();
                    return true;
                }
                return true;
            }
            return false;
        });
        if (Config.topShelfProgress(ctx) && item.progress >= 0f) {
            FrameLayout bar = new FrameLayout(ctx);
            View track = new View(ctx);
            GradientDrawable trackBg = new GradientDrawable();
            trackBg.setColor(0x3DFFFFFF);
            trackBg.setCornerRadius(dp(1));
            track.setBackground(trackBg);
            bar.addView(track, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
            View fill = new View(ctx);
            GradientDrawable fillBg = new GradientDrawable();
            fillBg.setColor(0xE6FFFFFF);
            fillBg.setCornerRadius(dp(1));
            fill.setBackground(fillBg);
            int barWidth = dp(cardW - 24);
            bar.addView(fill, new FrameLayout.LayoutParams(
                    Math.max(0, Math.round(barWidth * item.progress)), dp(2), Gravity.START));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.BOTTOM);
            blp.leftMargin = dp(12);
            blp.rightMargin = dp(12);
            blp.bottomMargin = dp(8);
            card.addView(bar, blp);
        }
        card.setOnClickListener(v -> launch(item));
        card.setOnLongClickListener(v -> {
            if (cardMenu == null) return false;
            cardMenu.onLongPress(v, item);
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(cardW), dp(cardH));
        lp.rightMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    /**
     * Plays an episode or opens its app; used by a short click and the episode menu. A stale Watch
     * Next row falls back from its session to its intent, then to the app itself and its App info.
     */
    void launch(Item item) {
        if (item == null) return;
        if (item.session != null) {
            try {
                item.session.send();
                return;
            } catch (PendingIntent.CanceledException | RuntimeException ignored) { }
        }
        if (item.intent != null) {
            try {
                ctx.startActivity(item.intent);
                return;
            } catch (RuntimeException ignored) { }
        }
        Intent fallback = launchIntent(item.pkg);
        if (fallback != null) {
            try {
                ctx.startActivity(fallback);
                return;
            } catch (RuntimeException ignored) { }
        }
        Intent info = appInfoIntent(item.pkg);
        if (info != null) {
            try {
                ctx.startActivity(info);
                return;
            } catch (RuntimeException ignored) { }
        }
        Toast.makeText(ctx, R.string.not_available, Toast.LENGTH_SHORT).show();
    }

    private Intent launchIntent(String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        try {
            Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Intent appInfoIntent(String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean hasTarget(Item item) {
        return item != null
                && (item.session != null || item.intent != null || launchIntent(item.pkg) != null);
    }

    private List<Item> load(String pkg) {
        List<Item> out = new ArrayList<>();
        try {
            if (ctx.checkSelfPermission("android.permission.READ_TV_LISTINGS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) return out;
        } catch (RuntimeException ignored) {
            return out;
        }
        ContentResolver resolver = ctx.getContentResolver();
        String[] cols = {
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
                TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS
        };
        try (Cursor c = resolver.query(TvContract.WatchNextPrograms.CONTENT_URI, cols, null, null,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")) {
            if (c == null) return out;
            while (c.moveToNext() && out.size() < MAX) {
                String rowPkg = c.getString(5);
                if (rowPkg == null || !rowPkg.equals(pkg)) continue;
                long position = c.getLong(6), duration = c.getLong(7);
                String intentUri = c.getString(4);
                Item item = new Item();
                item.pkg = rowPkg;
                item.title = c.getString(0);
                if (item.title == null || item.title.trim().isEmpty()) continue;
                item.subtitle = c.getString(1);
                if (duration > 0) item.progress = Math.max(0f, Math.min(1f, position / (float) duration));
                if (item.subtitle == null || item.subtitle.trim().isEmpty()) {
                    if (duration > 0) {
                        long left = Math.max(1, (duration - position) / 60_000);
                        item.subtitle = ctx.getString(R.string.top_shelf_min_left, left);
                    } else item.subtitle = appLabel(rowPkg);
                }
                if (intentUri != null) {
                    try {
                        item.intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
                        item.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    } catch (java.net.URISyntaxException | RuntimeException ignored) {
                        item.intent = null;
                    }
                }
                // A row whose app is gone has no usable target: don't draw a card that cannot open.
                if (!hasTarget(item)) continue;
                String uri = c.getString(2) != null ? c.getString(2) : c.getString(3);
                item.art = art(resolver, uri);
                out.add(item);
            }
        } catch (RuntimeException ignored) {
        }
        // YouTube and Spotify do not always publish Watch Next rows. Their active media session is
        // local and already carries title, artist and artwork, so use it as a single live card.
        if (out.isEmpty() && Config.topShelfNowPlaying(ctx)) {
            MediaArt.Entry media = MediaArt.forPackage(ctx, pkg);
            if (media != null) {
                Item item = new Item();
                item.pkg = pkg;
                item.title = media.title == null || media.title.trim().isEmpty()
                        ? appLabel(pkg) : media.title;
                String state = ctx.getString(media.playing ? R.string.now_playing : R.string.top_shelf_paused);
                item.subtitle = media.artist == null || media.artist.trim().isEmpty()
                        ? state : media.artist + " · " + state;
                item.art = media.bitmap;
                item.session = media.sessionActivity;
                if (item.session == null) item.intent = launchIntent(pkg);
                // A paused session with no launcher activity leaves nothing to open.
                if (item.session == null && item.intent == null) return out;
                if (media.duration > 0) {
                    item.progress = Math.max(0f, Math.min(1f, media.position / (float) media.duration));
                }
                out.add(item);
            }
        }
        return out;
    }

    private Bitmap art(ContentResolver resolver, String value) {
        if (value == null || value.isEmpty()) return null;
        Bitmap cached = ART.get(value);
        if (cached != null && !cached.isRecycled()) return cached;
        Uri uri;
        try {
            uri = Uri.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null) uri = Uri.fromFile(new java.io.File(value));
        else if (!"content".equals(scheme) && !"file".equals(scheme)) {
            // http(s) artwork is deliberately skipped: the shelf only reads local providers/files.
            return null;
        }
        Bitmap bitmap = decode(resolver, uri);
        if (bitmap != null) ART.put(value, bitmap);
        return bitmap;
    }

    /**
     * Local/Content-Length byte budget for one artwork stream. A provider may report its size; the
     * hard cap still applies when it does not (or lies).
     */
    private long artByteLimit(ContentResolver resolver, Uri uri) {
        long limit = MAX_ART_BYTES;
        try (AssetFileDescriptor afd = resolver.openAssetFileDescriptor(uri, "r")) {
            if (afd != null) {
                long length = afd.getLength();
                if (length > 0 && length < limit) limit = length;
            }
        } catch (Exception ignored) { }
        return limit;
    }

    private InputStream openCapped(ContentResolver resolver, Uri uri, long limit) throws IOException {
        InputStream raw = resolver.openInputStream(uri);
        return raw == null ? null : new CappedInputStream(raw, limit);
    }

    /**
     * Two passes: dimensions only (allocates no pixels), then one sampled decode. The stream is
     * reopened rather than buffering the file, so a 4K JPEG never becomes a byte[] plus a full
     * bitmap in memory at the same time.
     */
    private Bitmap decode(ContentResolver resolver, Uri uri) {
        long limit = artByteLimit(resolver, uri);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = openCapped(resolver, uri, limit)) {
            if (in == null) return null;
            Bitmap boundsBitmap = BitmapFactory.decodeStream(in, null, bounds);
            // inJustDecodeBounds normally returns null; recycle defensively if a decoder differs.
            if (boundsBitmap != null) boundsBitmap.recycle();
        } catch (Exception | OutOfMemoryError ignored) {
            return null;
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (longest / (sample * 2) >= TARGET_ART_SIDE) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try (InputStream in = openCapped(resolver, uri, limit)) {
            if (in == null) return null;
            return BitmapFactory.decodeStream(in, null, options);
        } catch (Exception | OutOfMemoryError ignored) {
            return null;
        }
    }

    /** Stops a stream at [limit] bytes so a hostile or huge file cannot be read into memory. */
    private static final class CappedInputStream extends FilterInputStream {
        private long remaining;

        CappedInputStream(InputStream in, long limit) {
            super(in);
            remaining = Math.max(1, limit);
        }

        @Override public int read() throws IOException {
            if (remaining <= 0) return -1;
            int value = super.read();
            if (value >= 0) remaining--;
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) return -1;
            int count = super.read(buffer, offset, (int) Math.min(length, remaining));
            if (count > 0) remaining -= count;
            return count;
        }

        @Override public long skip(long count) throws IOException {
            long skipped = super.skip(Math.min(count, remaining));
            if (skipped > 0) remaining -= skipped;
            return skipped;
        }
    }

    private void registerMemoryCallbacks() {
        if (memoryRegistered) return;
        Context app = ctx.getApplicationContext();
        if (app == null) app = ctx;
        try {
            app.registerComponentCallbacks(memoryCallbacks);
            memoryRegistered = true;
        } catch (RuntimeException ignored) { }
    }

    private void unregisterMemoryCallbacks() {
        if (!memoryRegistered) return;
        Context app = ctx.getApplicationContext();
        if (app == null) app = ctx;
        try {
            app.unregisterComponentCallbacks(memoryCallbacks);
        } catch (RuntimeException ignored) { }
        memoryRegistered = false;
    }

    private String appLabel(String pkg) {
        try {
            return String.valueOf(ctx.getPackageManager().getApplicationLabel(
                    ctx.getPackageManager().getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    private int dp(int value) {
        return Math.round(value * density);
    }

}
