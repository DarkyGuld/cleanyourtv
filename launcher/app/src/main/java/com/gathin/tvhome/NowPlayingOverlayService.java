package com.gathin.tvhome;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;

/**
 * Draws a compact, display-only Now Playing bar over AerialViews' DreamService.
 * The AerialViews overlay cannot read Spotify TV's MediaStyle notification on this firmware
 * (its title/text extras are null), so the launcher supplies its own window while the dream runs.
 */
public final class NowPlayingOverlayService extends Service {
    private static final long TICK_MS = 500;

    private final Handler handler = new Handler();
    private final BroadcastReceiver dreamReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_DREAMING_STARTED.equals(intent.getAction())) {
                if (ScreensaverController.aerialViewsOwnsDream(NowPlayingOverlayService.this)) {
                    dreaming = true;
                    attach();
                    refreshNow();
                }
            } else if (Intent.ACTION_DREAMING_STOPPED.equals(intent.getAction())) {
                dreaming = false;
                detach();
            }
        }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged =
            this::refreshControllers;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (active != null) {
                // Same session, new track: the active-session listener does not fire for this.
                refreshControllers(java.util.Collections.singletonList(active));
            }
            updateProgress();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private WindowManager windowManager;
    private View bar;
    private ImageView art;
    private TextView title;
    private TextView artist;
    private TextView time;
    private ProgressBar progress;
    private MediaSessionManager sessions;
    private ComponentName listener;
    private boolean dreaming;
    private boolean attached;
    private boolean registered;
    private Bitmap shownArt;
    private Bitmap sourceArt;
    private MediaController active;
    private long durationMs;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        sessions = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        listener = new ComponentName(this, GuardRevival.class);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DREAMING_STARTED);
        filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(dreamReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(dreamReceiver, filter);
            }
        } catch (RuntimeException ignored) {
            // A firmware that does not export the dream broadcasts simply never shows the overlay.
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Keep listening for the next dream; Home can be paused for a long time.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        detach();
        try {
            unregisterReceiver(dreamReceiver);
        } catch (RuntimeException ignored) {
        }
        super.onDestroy();
    }

    private void attach() {
        if (attached || !Config.npOverlay(this) || !Settings.canDrawOverlays(this)) return;
        bar = LayoutInflater.from(this).inflate(R.layout.view_now_playing_overlay, null, false);
        art = bar.findViewById(R.id.overlayArt);
        title = bar.findViewById(R.id.overlayTitle);
        artist = bar.findViewById(R.id.overlayArtist);
        time = bar.findViewById(R.id.overlayTime);
        progress = bar.findViewById(R.id.overlayProgress);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                getResources().getDisplayMetrics().widthPixels - dp(112),
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM;
        // Float above the AerialViews clock/caption instead of covering them.
        lp.y = dp(118);
        try {
            windowManager.addView(bar, lp);
            attached = true;
            startSessions();
        } catch (RuntimeException denied) {
            bar = null;
        }
    }

    private void detach() {
        handler.removeCallbacks(tick);
        stopSessions();
        if (attached && bar != null) {
            try {
                windowManager.removeView(bar);
            } catch (RuntimeException ignored) {
            }
        }
        attached = false;
        bar = null;
        active = null;
        durationMs = 0;
        releaseArt();
    }

    private void startSessions() {
        if (registered || sessions == null) return;
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler);
            registered = true;
            refreshControllers(sessions.getActiveSessions(listener));
            handler.postDelayed(tick, TICK_MS);
        } catch (SecurityException denied) {
            registered = false;
        }
    }

    private void stopSessions() {
        if (sessions != null && registered) {
            try {
                sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
            } catch (RuntimeException ignored) {
            }
        }
        registered = false;
    }

    private void refreshNow() {
        if (sessions == null) return;
        try {
            refreshControllers(sessions.getActiveSessions(listener));
        } catch (SecurityException ignored) {
        }
    }

    private void refreshControllers(List<MediaController> controllers) {
        if (!dreaming || !attached) return;
        MediaController chosen = best(controllers);
        active = chosen;
        if (chosen == null) {
            bar.setVisibility(View.GONE);
            return;
        }
        bar.setVisibility(View.VISIBLE);
        try {
            MediaMetadata metadata = chosen.getMetadata();
            PlaybackState state = chosen.getPlaybackState();
            String name = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            String by = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (by == null || by.trim().isEmpty()) {
                by = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            }
            if (name == null || name.trim().isEmpty()) name = appLabel(chosen.getPackageName());
            if (by == null || by.trim().isEmpty() || by.equalsIgnoreCase(name)) {
                by = appLabel(chosen.getPackageName());
            }
            title.setText(name);
            artist.setText(by);
            durationMs = metadata == null ? 0 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            Bitmap bitmap = metadata == null ? null : metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (bitmap == null && metadata != null) bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            setArt(bitmap, chosen.getPackageName());
            updateProgress();
        } catch (RuntimeException gone) {
            bar.setVisibility(View.GONE);
        }
    }

    private MediaController best(List<MediaController> controllers) {
        MediaController chosen = MediaArt.bestController(controllers);
        return chosen;
    }

    private void updateProgress() {
        if (!attached || active == null || bar == null || bar.getVisibility() != View.VISIBLE) return;
        try {
            PlaybackState state = active.getPlaybackState();
            if (state == null) return;
            long position = state.getPosition();
            if (state.getState() == PlaybackState.STATE_PLAYING && state.getLastPositionUpdateTime() > 0) {
                position += Math.max(0L,
                        (long) ((SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime())
                                * state.getPlaybackSpeed()));
            }
            if (durationMs > 0) position = Math.min(durationMs, Math.max(0, position));
            progress.setProgress(durationMs <= 0 ? 0 : (int) (1000L * position / durationMs));
            time.setText(format(position) + " / " + (durationMs > 0 ? format(durationMs) : "--:--"));
        } catch (RuntimeException ignored) {
        }
    }

    private void setArt(Bitmap bitmap, String pkg) {
        if (bitmap == sourceArt) return;
        sourceArt = bitmap;
        art.setImageDrawable(null);
        if (shownArt != null) {
            shownArt.recycle();
            shownArt = null;
        }
        if (bitmap != null) {
            try {
                shownArt = Bitmap.createScaledBitmap(bitmap, dp(72), dp(72), true);
                art.setImageBitmap(shownArt);
            } catch (RuntimeException ignored) {
                art.setImageDrawable(appIcon(pkg));
            }
        } else {
            art.setImageDrawable(appIcon(pkg));
        }
    }

    private void releaseArt() {
        if (art != null) art.setImageDrawable(null);
        if (shownArt != null && shownArt != sourceArt) shownArt.recycle();
        shownArt = null;
        sourceArt = null;
    }

    private Drawable appIcon(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Exception unavailable) {
            return getDrawable(R.drawable.ic_apps);
        }
    }

    private String appLabel(String pkg) {
        try {
            return String.valueOf(getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)));
        } catch (Exception unavailable) {
            return getString(R.string.now_playing);
        }
    }

    private String format(long ms) {
        long seconds = Math.max(0, ms) / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
