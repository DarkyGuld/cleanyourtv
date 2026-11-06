package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Aura's full-screen Now Playing: large artwork, the album's colour and the transport controls. */
public final class NowPlayingFullActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    private MediaController controller;
    private ImageView background, art;
    private TextView title, artist, elapsed, duration;
    private WaveformSeekBar waveform;
    private final SyntheticVisualizerSource visualSource = new SyntheticVisualizerSource();
    private ImageButton play;
    private View rootView;
    private long previewMs;
    private long previewUntil;
    private long pendingSeekMs = -1;
    private String mediaKey;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService artExecutor = Executors.newSingleThreadExecutor();
    private int artGeneration;
    private Bitmap artSource;
    private Bitmap scaledArt;
    private int artColour = 0xFF101419;
    private boolean resumed;
    private boolean callbackRegistered;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    private final Runnable sendSeek = () -> {
        MediaController target = controller;
        long to = pendingSeekMs;
        pendingSeekMs = -1;
        if (target == null || to < 0) return;
        transport(target, controls -> controls.seekTo(to));
    };

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { update(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { update(); }
        @Override public void onSessionDestroyed() { refreshSessions(); }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_now_playing_full);
        rootView = findViewById(R.id.fullRoot);
        background = findViewById(R.id.fullBackground);
        art = findViewById(R.id.fullArt);
        title = findViewById(R.id.fullTitle);
        artist = findViewById(R.id.fullArtist);
        elapsed = findViewById(R.id.fullElapsed);
        duration = findViewById(R.id.fullDuration);
        waveform = findViewById(R.id.fullWaveform);
        play = findViewById(R.id.fullPlay);
        ImageButton previous = findViewById(R.id.fullPrev);
        ImageButton next = findViewById(R.id.fullNext);
        ImageButton close = findViewById(R.id.fullClose);

        close.setOnClickListener(v -> finishIfAllowed());
        next.setNextFocusRightId(R.id.fullClose);
        close.setNextFocusLeftId(R.id.fullNext);

        previous.setOnClickListener(v -> transport(controller, controls -> controls.skipToPrevious()));
        next.setOnClickListener(v -> transport(controller, controls -> controls.skipToNext()));
        play.setOnClickListener(v -> transport(controller, controls -> {
            PlaybackState playback = controller == null ? null : controller.getPlaybackState();
            if (playback != null && playback.getState() == PlaybackState.STATE_PLAYING) {
                controls.pause();
            } else {
                controls.play();
            }
        }));

        waveform.setMode(Config.npVisualMode(this));
        waveform.setSource(visualSource);
        waveform.setGlow(Config.npGlow(this));
        waveform.setAccent(Config.npAccentColor(this));
        waveform.setOnSeekListener(progress -> {
            long total = total();
            if (total <= 0) return;
            previewMs = (long) (total * progress);
            previewUntil = SystemClock.elapsedRealtime() + 900;
            elapsed.setText(format(previewMs));
            pendingSeekMs = previewMs;
            handler.removeCallbacks(sendSeek);
            handler.postDelayed(sendSeek, 80);
        });

        refreshSessions();
        if (isFinishing() || isDestroyed()) return;
        rootView.setAlpha(0f);
        rootView.animate().alpha(1f).setDuration(260).start();
        art.setScaleX(0.94f);
        art.setScaleY(0.94f);
        art.setAlpha(0.7f);
        art.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(380)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f)).start();
        play.requestFocus();
    }

    private void transport(MediaController target, Consumer<MediaController.TransportControls> action) {
        if (target == null || action == null) return;
        try {
            MediaController.TransportControls controls = target.getTransportControls();
            if (controls != null) action.accept(controls);
        } catch (RuntimeException ignored) {
        }
    }

    /** Picks the same session as the bar; the activity only closes when there are no sessions at all. */
    private void refreshSessions() {
        List<MediaController> sessions;
        try {
            sessions = MediaArt.sessions(this);
        } catch (RuntimeException denied) {
            sessions = Collections.emptyList();
        }
        if (sessions.isEmpty()) {
            unregisterCallback();
            controller = null;
            finishIfAllowed();
            return;
        }
        MediaController chosen = MediaArt.bestController(sessions);
        if (chosen == null) chosen = sessions.get(0);
        if (chosen != controller) {
            unregisterCallback();
            controller = chosen;
            artSource = null;
            scaledArt = null;
            artGeneration++;
            mediaKey = null;
            previewUntil = 0;
            pendingSeekMs = -1;
            handler.removeCallbacks(sendSeek);
            if (resumed) registerCallback();
        }
        update();
    }

    private void registerCallback() {
        if (controller == null || callbackRegistered) return;
        try {
            controller.registerCallback(callback, handler);
            callbackRegistered = true;
        } catch (RuntimeException ignored) {
        }
    }

    private void unregisterCallback() {
        if (controller != null && callbackRegistered) {
            try {
                controller.unregisterCallback(callback);
            } catch (RuntimeException ignored) {
            }
        }
        callbackRegistered = false;
    }

    private long total() {
        MediaController current = controller;
        if (current == null) return 0;
        try {
            MediaMetadata metadata = current.getMetadata();
            long value = metadata == null ? 0 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            return Math.max(0, value);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private void update() {
        MediaController current = controller;
        if (current == null) return;
        MediaMetadata metadata;
        try {
            metadata = current.getMetadata();
        } catch (RuntimeException ignored) {
            metadata = null;
        }
        String name = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String by = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (by == null && metadata != null) by = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (name == null || name.trim().isEmpty()) name = appLabel(current.getPackageName());
        if (by == null || by.trim().isEmpty() || by.trim().equalsIgnoreCase(name.trim())) {
            by = appLabel(current.getPackageName());
        }
        title.setText(name);
        artist.setText(by);
        visualSource.setTrack(name, by);

        String key = current.getPackageName() + '\n' + name + '\n' + by;
        if (!key.equals(mediaKey)) {
            mediaKey = key;
            previewUntil = 0;
            pendingSeekMs = -1;
            handler.removeCallbacks(sendSeek);
        }

        Bitmap bitmap = metadata == null ? null : metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bitmap == null && metadata != null) bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bitmap == null && metadata != null) bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (bitmap == null) {
            if (artSource != null || art.getVisibility() == View.VISIBLE) {
                artSource = null;
                scaledArt = null;
                artGeneration++;
                background.setImageDrawable(null);
                art.setImageDrawable(null);
                art.setVisibility(View.GONE);
                resetGradient();
            }
        } else if (bitmap != artSource) {
            artSource = bitmap;
            loadArt(bitmap);
        } else if (scaledArt != null) {
            applyArt(scaledArt, artColour);
        }

        updateProgress();
    }

    /** Decode/scale once off the main thread; both the background and the cover use the same bitmap. */
    private void loadArt(Bitmap source) {
        final int generation = ++artGeneration;
        try {
            artExecutor.execute(() -> {
                Bitmap scaled = scaleArt(source);
                int colour = sampleColour(scaled != null ? scaled : source);
                handler.post(() -> {
                    if (generation != artGeneration || source != artSource
                            || isFinishing() || isDestroyed()) {
                        if (scaled != null && scaled != source) scaled.recycle();
                        return;
                    }
                    scaledArt = scaled != null ? scaled : source;
                    artColour = colour;
                    applyArt(scaledArt, artColour);
                });
            });
        } catch (RuntimeException ignored) {
            // The executor is gone: keep the last artwork.
        }
    }

    private Bitmap scaleArt(Bitmap source) {
        try {
            int max = Math.min(1280,
                    Math.round(400 * getResources().getDisplayMetrics().density));
            if (source.getWidth() <= max && source.getHeight() <= max) return source;
            float scale = Math.min(max / (float) source.getWidth(), max / (float) source.getHeight());
            return Bitmap.createScaledBitmap(source,
                    Math.max(1, Math.round(source.getWidth() * scale)),
                    Math.max(1, Math.round(source.getHeight() * scale)), true);
        } catch (RuntimeException | OutOfMemoryError ignored) {
            return source;
        }
    }

    /** A tiny copy is enough for the album colour, and it keeps HARDWARE bitmaps readable. */
    private int sampleColour(Bitmap bitmap) {
        Bitmap tiny = null;
        Bitmap readable = null;
        try {
            int w = Math.max(1, Math.min(8, bitmap.getWidth()));
            int h = Math.max(1, Math.min(8, bitmap.getHeight()));
            tiny = Bitmap.createScaledBitmap(bitmap, w, h, false);
            Bitmap from = tiny != null ? tiny : bitmap;
            readable = from.getConfig() == Bitmap.Config.HARDWARE
                    ? from.copy(Bitmap.Config.ARGB_8888, false) : from;
            if (readable == null) return 0xFF101419;
            long r = 0, g = 0, b = 0;
            int count = 0;
            for (int y = 0; y < readable.getHeight(); y++) {
                for (int x = 0; x < readable.getWidth(); x++) {
                    int pixel = readable.getPixel(x, y);
                    r += Color.red(pixel);
                    g += Color.green(pixel);
                    b += Color.blue(pixel);
                    count++;
                }
            }
            if (count == 0) return 0xFF101419;
            return Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        } catch (RuntimeException | OutOfMemoryError ignored) {
            return 0xFF101419;
        } finally {
            if (readable != null && readable != tiny && readable != bitmap) readable.recycle();
            if (tiny != null && tiny != bitmap) tiny.recycle();
        }
    }

    private void applyArt(Bitmap bitmap, int colour) {
        background.setImageBitmap(bitmap);
        background.setAlpha(0.24f);
        art.setImageBitmap(bitmap);
        art.setVisibility(View.VISIBLE);
        rootView.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{colour, 0xFF06080B}));
    }

    private void resetGradient() {
        rootView.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF101419, 0xFF06080B}));
    }

    /** Position is extrapolated from the last playback update, so the seconds keep moving. */
    private void updateProgress() {
        MediaController current = controller;
        if (current == null) return;
        long total = total();
        PlaybackState playback;
        try {
            playback = current.getPlaybackState();
        } catch (RuntimeException ignored) {
            playback = null;
        }
        long now = SystemClock.elapsedRealtime();
        long position = previewUntil > now ? previewMs : (playback == null ? 0 : playback.getPosition());
        if (previewUntil <= now && playback != null && playback.getState() == PlaybackState.STATE_PLAYING
                && playback.getLastPositionUpdateTime() > 0) {
            long since = Math.max(0, now - playback.getLastPositionUpdateTime());
            position += (long) (since * playback.getPlaybackSpeed());
        }
        if (total > 0) position = Math.min(total, Math.max(0, position));
        boolean playing = playback != null && playback.getState() == PlaybackState.STATE_PLAYING;
        visualSource.setPlayback(playing, position);
        waveform.setPlaying(playing);
        waveform.setEnabled(total > 0);
        waveform.setProgress(total > 0 ? position / (float) total : 0f);
        elapsed.setText(format(position));
        duration.setText(format(total));
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(getString(playing ? R.string.pause : R.string.play));
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            return pkg;
        }
    }

    private String format(long ms) {
        long total = Math.max(0, ms) / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60);
    }

    private void finishIfAllowed() {
        if (!isFinishing() && !isDestroyed()) finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        registerCallback();
        handler.removeCallbacks(tick);
        handler.post(tick);
        refreshSessions();
    }

    @Override
    protected void onPause() {
        resumed = false;
        pendingSeekMs = -1;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(sendSeek);
        unregisterCallback();
        super.onPause();
    }

    @Override
    protected void onStop() {
        unregisterCallback();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(sendSeek);
        artGeneration++;
        unregisterCallback();
        artExecutor.shutdownNow();
        super.onDestroy();
    }
}
