package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The Now Playing bar above the dock: artwork and track text on the left, a waveform progress row with
 * the transport controls underneath it, and fullscreen/close on the right. It reads media sessions
 * through the notification-listener access granted to GuardRevival; it never reads notification content.
 */
final class NowPlaying {
    interface Visibility {
        void onVisibilityChanged(boolean visible);
    }

    /** Opens the same glass popup used by a held Home tile, filled with Now Playing options. */
    interface BarSettings {
        void show(View anchor);
    }

    private final Activity activity;
    private final Runnable focusTop;
    private final Runnable focusDock;
    private final Visibility visibility;
    private final BarSettings barSettings;
    private final View card;
    private final View open;
    private final ImageView artwork;
    private final TextView title;
    private final TextView subtitle;
    private final View coverBox;
    private final View artFrame;
    private final View trackInfo;
    private final RadialAuraView aura;
    private final TextView elapsed;
    private final TextView duration;
    private final WaveformSeekBar waveform;
    private final SyntheticVisualizerSource visualSource = new SyntheticVisualizerSource();
    private final GradientDrawable barBackground = new GradientDrawable();
    private final int[] gradientColors = new int[2];
    private final ImageButton previous;
    private final ImageButton play;
    private final ImageButton next;
    private final ImageButton fullscreen;
    private final ImageButton close;
    private final LinearLayout controls;
    private final ImageView sourceBadge;
    private final View coverFocus;
    interface ArtworkListener { void onArtwork(Bitmap bitmap); }
    private static final long METADATA_GRACE_MS = 900L;
    private ArtworkListener artworkListener;
    private Bitmap lastSentArt;
    private final MediaSessionManager sessions;
    private final ComponentName listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MediaController> controllers = new ArrayList<>();
    private MediaController active;
    private boolean started;
    /** Media exists and the bar should be shown. */
    private boolean visible;
    /** The bar fits between the top bar and the dock. */
    private boolean spaceVisible = true;
    /** What the owner last heard, so Home's next-focus target matches the bar on screen. */
    private boolean reportedVisible;
    private long durationMs;
    private long seekPreviewMs;
    private long seekPreviewUntil;
    private long pendingSeekMs = -1;
    private float dockTop = -1f;
    private boolean dockScrolled;
    private int barHeight;
    private String lastTitle;
    private String lastMediaKey;
    private Bitmap coverSource;
    private Bitmap scaledCover;
    private int coverAccent = 0xFFFF9E7A;
    private boolean metadataGracePending;
    /** The session the owner dismissed with the bar's X; the bar returns when the track changes. */
    private String dismissedKey;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { update(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { update(); }
        @Override public void onSessionDestroyed() { refreshSessions(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged =
            this::refreshControllers;

    /** On a cold start the notification listener can connect a moment after Home resumes. */
    private final Runnable retrySessions = () -> {
        if (started) refreshSessions();
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible || !spaceVisible) return;
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    /** Keeps one progress timer alive whenever the bar is on screen, including after onResume. */
    private void startTicking() {
        handler.removeCallbacks(tick);
        if (visible && spaceVisible) handler.post(tick);
    }

    /** Coalesces a held D-pad seek into one transport call every few frames. */
    private final Runnable sendSeek = () -> {
        MediaController target = active;
        long to = pendingSeekMs;
        pendingSeekMs = -1;
        if (target == null || to < 0) return;
        transport(target, controls -> controls.seekTo(to));
    };

    /** A track change can clear the metadata for a moment; hold the previous card instead of flickering. */
    private final Runnable metadataGraceExpired = () -> {
        metadataGracePending = false;
        if (!started) return;
        MediaController chosen = MediaArt.bestController(controllers);
        if (chosen != null && !metadataEmpty(chosen)) {
            update();
            return;
        }
        if (chosen == null && !hasLiveSession()) {
            show(null);
            return;
        }
        // The session is still there but its metadata stayed empty past the grace: hide once.
        active = chosen;
        show(null);
    };

    NowPlaying(Activity activity, Runnable focusTop, Runnable focusDock, BarSettings barSettings,
               Visibility visibility) {
        this.activity = activity;
        this.focusTop = focusTop;
        this.focusDock = focusDock;
        this.barSettings = barSettings;
        this.visibility = visibility;
        card = LayoutInflater.from(activity).inflate(R.layout.view_now_playing, null, false);
        barBackground.setShape(GradientDrawable.RECTANGLE);
        barBackground.setCornerRadius(dp(18));
        card.setBackground(barBackground);
        card.setOnLongClickListener(v -> openFullScreen());
        FrameLayout root = activity.findViewById(R.id.root);
        // The same width as the dock band: the bar sits directly above it and lines up with the app row.
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(56);
        params.rightMargin = dp(56);
        // The bar stays above the dock row, in front of the grid.
        root.addView(card, params);
        open = card.findViewById(R.id.npOpen);
        artwork = card.findViewById(R.id.npArtwork);
        title = card.findViewById(R.id.npTitle);
        subtitle = card.findViewById(R.id.npSubtitle);
        coverBox = card.findViewById(R.id.npCoverBox);
        artFrame = card.findViewById(R.id.npArtFrame);
        trackInfo = card.findViewById(R.id.npTrackInfo);
        aura = card.findViewById(R.id.npAura);
        elapsed = card.findViewById(R.id.npElapsed);
        duration = card.findViewById(R.id.npDuration);
        waveform = card.findViewById(R.id.npWaveform);
        waveform.setOnSeekListener(progress -> {
            if (active == null || durationMs <= 0) return;
            long target = (long) (durationMs * progress);
            seekPreviewMs = target;
            seekPreviewUntil = SystemClock.elapsedRealtime() + 900;
            elapsed.setText(format(target));
            pendingSeekMs = target;
            handler.removeCallbacks(sendSeek);
            handler.postDelayed(sendSeek, 80);
        });
        waveform.setSource(visualSource);
        previous = card.findViewById(R.id.npPrev);
        play = card.findViewById(R.id.npPlay);
        next = card.findViewById(R.id.npNext);
        fullscreen = card.findViewById(R.id.npFullscreen);
        close = card.findViewById(R.id.npClose);
        controls = card.findViewById(R.id.npControls);
        sourceBadge = card.findViewById(R.id.npSourceBadge);
        coverFocus = card.findViewById(R.id.npCoverFocus);
        sessions = (MediaSessionManager) activity.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(activity, GuardRevival.class);
        title.setSelected(true);   // marquee when the title overflows
        applyConfig();
        wireControls();
    }

    /** Reads the bar's per-part switches; called at start too, so a Home rebuild is never required. */
    private void applyConfig() {
        applySize(Config.npBarSize(activity));
        coverBox.setVisibility(Config.npAlbumArt(activity) ? View.VISIBLE : View.GONE);
        waveform.setVisibility(Config.npWaveform(activity) ? View.VISIBLE : View.GONE);
        int times = Config.npTimes(activity) ? View.VISIBLE : View.GONE;
        elapsed.setVisibility(times);
        duration.setVisibility(times);
        sourceBadge.setVisibility(Config.npSource(activity) && Config.npAlbumArt(activity)
                ? View.VISIBLE : View.GONE);
        int visualMode = Config.npVisualMode(activity);
        waveform.setMode(visualMode);
        waveform.setGlow(Config.npGlow(activity));
        int accentColor = Config.npAccentColor(activity);
        waveform.setAccent(accentColor);
        aura.setSource(visualSource);
        aura.setAccent(accentColor);
        aura.setGlow(Config.npGlow(activity));
        aura.setPlaying(playing());
        aura.setVisibility(visualMode == WaveformSeekBar.MODE_AURA && Config.npAlbumArt(activity)
                ? View.VISIBLE : View.GONE);
        applyBackground();
        boolean full = Config.nowPlayingFull(activity);
        boolean closable = Config.npClose(activity);
        fullscreen.setVisibility(full ? View.VISIBLE : View.GONE);
        close.setVisibility(closable ? View.VISIBLE : View.GONE);
        // Only the visible buttons are in the right-hand focus chain.
        next.setNextFocusRightId(full ? R.id.npFullscreen : closable ? R.id.npClose : R.id.npNext);
        fullscreen.setNextFocusLeftId(R.id.npNext);
        fullscreen.setNextFocusRightId(closable ? R.id.npClose : R.id.npFullscreen);
        close.setNextFocusLeftId(full ? R.id.npFullscreen : R.id.npNext);
        close.setNextFocusRightId(R.id.npClose);
    }

    /** Compact, balanced, roomy or large: one layout, no view recreation. */
    private void applySize(int size) {
        int[] heights = {96, 112, 128, 144};
        int height = dp(heights[size]);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        if (cardLp.height != height) {
            cardLp.height = height;
            card.setLayoutParams(cardLp);
        }
        int cover = dp(size == 0 ? 96 : size == 1 ? 112 : size == 2 ? 128 : 144);
        ViewGroup.LayoutParams boxLp = coverBox.getLayoutParams();
        if (boxLp.width != cover || boxLp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
            boxLp.width = cover;
            boxLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            coverBox.setLayoutParams(boxLp);
        }
        ViewGroup.LayoutParams artLp = artFrame.getLayoutParams();
        if (artLp.width != ViewGroup.LayoutParams.MATCH_PARENT
                || artLp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
            artLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            artLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            artFrame.setLayoutParams(artLp);
        }
        ViewGroup.LayoutParams auraLp = aura.getLayoutParams();
        int auraSize = cover;
        if (auraLp.width != auraSize || auraLp.height != auraSize) {
            auraLp.width = auraSize;
            auraLp.height = auraSize;
            aura.setLayoutParams(auraLp);
        }
        int track = dp(size == 0 ? 150 : size == 1 ? 172 : size == 2 ? 190 : 210);
        ViewGroup.LayoutParams trackLp = trackInfo.getLayoutParams();
        if (trackLp.width != track) {
            trackLp.width = track;
            trackInfo.setLayoutParams(trackLp);
        }
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,
                size == 0 ? 16 : size == 1 ? 18 : size == 2 ? 19 : 20);
        subtitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,
                size == 0 ? 12 : size == 1 ? 13 : size == 2 ? 14 : 15);
        int badge = dp(size == 0 ? 22 : size == 1 ? 24 : size == 2 ? 26 : 28);
        ViewGroup.LayoutParams badgeLp = sourceBadge.getLayoutParams();
        if (badgeLp.width != badge || badgeLp.height != badge) {
            badgeLp.width = badge;
            badgeLp.height = badge;
            sourceBadge.setLayoutParams(badgeLp);
        }
        int playSize = dp(size >= 2 ? 48 : size == 0 ? 40 : 44);
        ViewGroup.LayoutParams playLp = play.getLayoutParams();
        if (playLp.width != playSize || playLp.height != playSize) {
            playLp.width = playSize;
            playLp.height = playSize;
            play.setLayoutParams(playLp);
        }
    }

    /** Background style/opacity/accent/glow, changed live without recreating the view. */
    private void applyBackground() {
        int style = Config.npBackgroundStyle(activity);
        int alpha = Math.round(Config.npBackgroundOpacity(activity) * 2.55f);
        barBackground.setCornerRadius(dp(18));
        if (style == 0) {
            gradientColors[0] = 0x00000000;
            gradientColors[1] = 0x00000000;
            barBackground.setColors(gradientColors);
            barBackground.setStroke(Math.max(1, dp(1)), 0x14FFFFFF);
        } else if (style == 2) {
            gradientColors[0] = (alpha << 24) | 0x101116;
            gradientColors[1] = (alpha << 24) | 0x101116;
            barBackground.setColors(gradientColors);
            barBackground.setStroke(Math.max(1, dp(1)), 0x14FFFFFF);
        } else {
            int top = (Math.min(alpha, 230) << 24) | 0x14151C;
            int bottom = (Math.min(alpha, 200) << 24) | 0x0D0E13;
            gradientColors[0] = top;
            gradientColors[1] = bottom;
            barBackground.setColors(gradientColors);
            barBackground.setStroke(Math.max(1, dp(1)), Math.min(0x33, alpha / 3) << 24 | 0xFFFFFF);
        }
        card.setBackground(barBackground);
    }

    private void wireControls() {
        open.setOnClickListener(v -> openApp());
        open.setOnFocusChangeListener((v, focused) -> coverFocus.setVisibility(focused ? View.VISIBLE : View.INVISIBLE));
        previous.setOnClickListener(v -> skip(false));
        play.setOnClickListener(v -> toggle());
        next.setOnClickListener(v -> skip(true));
        close.setOnClickListener(v -> dismiss());
        fullscreen.setOnClickListener(v -> openFullScreen());
        // Short press opens the playing app; hold opens the bar's settings popup, styled like a tile menu.
        open.setOnLongClickListener(v -> {
            if (barSettings != null) barSettings.show(open);
            return true;
        });
        open.setOnKeyListener((v, key, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                previous.requestFocus();
                return true;
            }
            return leaveKey(key, event);
        });
        waveform.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                play.requestFocus();
                return true;
            }
            return leaveKey(key, event);
        });
        play.setOnKeyListener((v, key, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_UP) {
                waveform.requestFocus();
                return true;
            }
            return leaveKey(key, event);
        });
        previous.setOnKeyListener((v, key, event) -> leaveKey(key, event));
        next.setOnKeyListener((v, key, event) -> leaveKey(key, event));
        fullscreen.setOnKeyListener((v, key, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)) {
                openFullScreen();
                return true;
            }
            return leaveKey(key, event);
        });
        close.setOnKeyListener((v, key, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)) {
                dismiss();
                return true;
            }
            return leaveKey(key, event);
        });
        focusButton(open, false);
        focusButton(waveform, false);
        focusButton(previous, false);
        focusButton(play, false);
        focusButton(next, false);
        focusButton(fullscreen, false);
        focusButton(close, false);
        for (View v : new View[]{waveform, previous, play, next, fullscreen, close, open}) {
            v.setFocusableInTouchMode(false);
        }
        open.setNextFocusRightId(R.id.npPrev);
        previous.setNextFocusLeftId(R.id.npOpen);
        previous.setNextFocusRightId(R.id.npPlay);
        play.setNextFocusLeftId(R.id.npPrev);
        play.setNextFocusRightId(R.id.npNext);
        waveform.setNextFocusDownId(R.id.npPlay);
        next.setNextFocusLeftId(R.id.npPlay);
    }

    /** Up from the bar goes to the top pills, Down back to the dock. */
    private boolean leaveKey(int key, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            focusTop.run();
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            focusDock.run();
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK) {
            dismiss();
            return true;
        }
        return false;
    }

    /** The whole bar, artwork included, opens the full-screen player on a held OK. */
    private boolean openFullScreen() {
        if (!Config.nowPlayingFull(activity)) return false;
        try {
            activity.startActivity(new android.content.Intent(activity, NowPlayingFullActivity.class));
        } catch (RuntimeException ignored) {
        }
        return true;
    }

    /** Hide the bar until the track changes. The owner can bring it back with any new media. */
    private void dismiss() {
        if (active != null) dismissedKey = sessionKey(active);
        show(null);
        focusDock.run();
    }

    private String sessionKey(MediaController controller) {
        try {
            MediaMetadata metadata = controller.getMetadata();
            String title = metadata == null ? "" : String.valueOf(metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
            String artist = metadata == null ? "" : String.valueOf(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
            return controller.getPackageName() + '\n' + title + '\n' + artist;
        } catch (RuntimeException ignored) {
            return controller.getPackageName();
        }
    }

    private void focusButton(View view, boolean scale) {
        view.setOnFocusChangeListener((v, focused) -> {
            if (scale) {
                v.animate().scaleX(focused ? 1.1f : 1f).scaleY(focused ? 1.1f : 1f)
                        .setDuration(120).start();
            }
            if (focused) showControls();
        });
    }

    private void showControls() {
        // The redesigned bar keeps its controls visible: the X and transport keys must always be one
        // D-pad move away, not hidden by a timer.
        position();
    }

    void setArtworkListener(ArtworkListener listener) {
        artworkListener = listener;
    }

    /**
     * Home went behind another app: stop the progress timer and the session callbacks, but keep the bar
     * as it is. It is not drawn while another app is in front, and returning to Home must not make it pop in.
     */
    void pause() {
        if (!started) return;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(retrySessions);
        handler.removeCallbacks(sendSeek);
        cancelMetadataGrace();
        pendingSeekMs = -1;
        visualSource.setPlaying(false);
        waveform.setPlaying(false);
        aura.setPlaying(false);
        detachSessions();
        started = false;
    }

    void start() {
        if (started || sessions == null) return;
        boolean registered = false;
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler);
            registered = true;
            started = true;
            refreshControllers(sessions.getActiveSessions(listener));
            if (visible && spaceVisible) startTicking();
            // A cold start can bind GuardRevival after this resume; try once more without waiting for a track change.
            handler.postDelayed(retrySessions, 800);
            handler.postDelayed(retrySessions, 2500);
        } catch (RuntimeException denied) {
            if (registered) {
                try {
                    sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
                } catch (RuntimeException ignored) {
                }
            }
            started = false;
            show(null);
        }
    }

    private void detachSessions() {
        if (sessions != null) {
            try {
                sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
            } catch (RuntimeException ignored) {
            }
        }
        for (MediaController controller : controllers) {
            try {
                controller.unregisterCallback(callback);
            } catch (RuntimeException ignored) {
            }
        }
        controllers.clear();
    }

    /** Drops the scaled cover after a memory trim; the next playback update rebuilds it. */
    void releaseArtwork() {
        scaledCover = null;
        lastSentArt = null;
        artwork.setImageDrawable(null);
        if (artworkListener != null) artworkListener.onArtwork(null);
    }

    void stop() {
        if (!started) return;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(retrySessions);
        handler.removeCallbacks(sendSeek);
        cancelMetadataGrace();
        pendingSeekMs = -1;
        visualSource.setPlaying(false);
        waveform.setPlaying(false);
        aura.setPlaying(false);
        detachSessions();
        started = false;
        hide(false);
    }

    void setDockTop(float y) {
        dockTop = y;
        position();
    }



    boolean isVisible() {
        return visible && spaceVisible;
    }

    /** True when any control in the bar owns focus; Home uses this to restore the dock after start. */
    boolean hasFocus() {
        return card.hasFocus();
    }

    /** Applies a change made in the bar's settings popup without recreating Home. */
    void refreshConfig() {
        applyConfig();
        position();
        refreshGlass();
    }

    /** Re-bakes the glass over the current wallpaper; cheap enough to call when it rotates. */
    void refreshGlass() {
        if (visible && spaceVisible) applyBackground();
    }

    /** Focus the controls from the dock; false when the bar is not on screen. */
    boolean focus() {
        if (!isVisible() || card.getVisibility() != View.VISIBLE) return false;
        showControls();
        // Up from the dock lands on the progress bar, so Left/Right can scrub immediately.
        if (waveform.getVisibility() == View.VISIBLE) waveform.requestFocus();
        else play.requestFocus();
        return true;
    }

    boolean handleKey(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                && code != KeyEvent.KEYCODE_MEDIA_PLAY
                && code != KeyEvent.KEYCODE_MEDIA_PAUSE
                && code != KeyEvent.KEYCODE_MEDIA_NEXT
                && code != KeyEvent.KEYCODE_MEDIA_PREVIOUS) return false;
        if (active == null) return false;
        if (event.getAction() != KeyEvent.ACTION_UP || event.getRepeatCount() > 0) return true;
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY) transport(active, MediaController.TransportControls::play);
        else if (code == KeyEvent.KEYCODE_MEDIA_PAUSE) transport(active, MediaController.TransportControls::pause);
        else if (code == KeyEvent.KEYCODE_MEDIA_NEXT) skip(true);
        else if (code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) skip(false);
        else if (playing()) transport(active, MediaController.TransportControls::pause);
        else transport(active, MediaController.TransportControls::play);
        return true;
    }

    private boolean playing() {
        MediaController controller = active;
        if (controller == null) return false;
        try {
            PlaybackState state = controller.getPlaybackState();
            return state != null && state.getState() == PlaybackState.STATE_PLAYING;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void toggle() {
        MediaController controller = active;
        if (controller == null) return;
        if (playing()) transport(controller, MediaController.TransportControls::pause);
        else transport(controller, MediaController.TransportControls::play);
    }

    private void skip(boolean nextTrack) {
        MediaController controller = active;
        if (controller == null) return;
        if (nextTrack) transport(controller, MediaController.TransportControls::skipToNext);
        else transport(controller, MediaController.TransportControls::skipToPrevious);
    }

    /** Every transport call goes through here: no null controls and no session crash reaches Home. */
    private void transport(MediaController controller, Consumer<MediaController.TransportControls> action) {
        if (controller == null || action == null) return;
        try {
            MediaController.TransportControls controls = controller.getTransportControls();
            if (controls != null) action.accept(controls);
        } catch (RuntimeException ignored) {
        }
    }

    private void openApp() {
        if (active == null) return;
        String pkg = active.getPackageName();
        PackageManager pm = activity.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) return;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            activity.startActivity(launch);
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    private void refreshSessions() {
        if (!started) return;
        if (sessions == null) {
            refreshControllers(null);
            return;
        }
        try {
            refreshControllers(sessions.getActiveSessions(listener));
        } catch (SecurityException denied) {
            refreshControllers(null);
        }
    }

    private void refreshControllers(List<MediaController> list) {
        for (MediaController controller : controllers) {
            try {
                controller.unregisterCallback(callback);
            } catch (RuntimeException ignored) {
            }
        }
        controllers.clear();
        if (list != null) {
            for (MediaController controller : list) {
                if (controller == null || controllers.contains(controller)) continue;
                try {
                    controller.registerCallback(callback, handler);
                    controllers.add(controller);
                } catch (RuntimeException ignored) {
                }
            }
        }
        update();
    }

    private void update() {
        MediaController chosen = MediaArt.bestController(controllers);
        if (chosen == null) {
            // A session that is gone or stopped hides the bar. A live session whose metadata is momentarily
            // empty keeps the old card during the grace period instead of animating out and back in.
            if (hasLiveSession() && lastTitle != null) {
                active = null;
                scheduleMetadataGrace();
                return;
            }
            cancelMetadataGrace();
            show(null);
            return;
        }
        active = chosen;
        if (dismissedKey != null && dismissedKey.equals(sessionKey(chosen))) {
            cancelMetadataGrace();
            active = null;
            show(null);
            return;
        }
        MediaMetadata metadata;
        try {
            metadata = chosen.getMetadata();
        } catch (RuntimeException ignored) {
            metadata = null;
        }
        String name = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null || artist.trim().isEmpty()) {
            artist = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }
        boolean empty = name == null || name.trim().isEmpty();
        if (empty && lastTitle != null) {
            // Slow track changes often clear metadata for a moment. Keep the previous title, art and seek
            // preview; only playback state/progress move until the new metadata arrives or the grace ends.
            scheduleMetadataGrace();
            updatePlayState(chosen);
            if (!visible) show(chosen);
            else position();
            updateProgress();
            return;
        }
        if (empty) {
            name = appLabel(chosen.getPackageName());
            if (artist == null || artist.trim().isEmpty()) artist = activity.getString(R.string.now_playing);
        }
        if (artist == null || artist.trim().isEmpty() || artist.trim().equalsIgnoreCase(name.trim())) {
            artist = appLabel(chosen.getPackageName());
        }
        cancelMetadataGrace();
        durationMs = metadata == null ? 0 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        String key = chosen.getPackageName() + '\n' + name + '\n' + artist;
        if (!key.equals(lastMediaKey)) {
            lastMediaKey = key;
            seekPreviewMs = 0;
            seekPreviewUntil = 0;
            pendingSeekMs = -1;
            handler.removeCallbacks(sendSeek);
        }
        lastTitle = name;
        title.setText(name);
        subtitle.setText(artist);
        visualSource.setTrack(name, artist);
        title.setSelected(true);
        open.setContentDescription(activity.getString(R.string.now_playing) + ": " + name);
        updatePlayState(chosen);
        sourceBadge.setImageDrawable(appIcon(chosen.getPackageName()));

        Bitmap bitmap = metadata == null ? null : metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bitmap == null && metadata != null) bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bitmap != coverSource) {
            coverSource = bitmap;
            scaledCover = null;
            coverAccent = 0xFFFF9E7A;
            if (bitmap != null) {
                scaledCover = scaleCover(bitmap);
                coverAccent = artworkColor(bitmap);
            }
        }
        Bitmap shownArt = scaledCover != null ? scaledCover : bitmap;
        if (shownArt != null) artwork.setImageBitmap(shownArt);
        else artwork.setImageDrawable(appIcon(chosen.getPackageName()));
        if (artworkListener != null && shownArt != lastSentArt) {
            lastSentArt = shownArt;
            artworkListener.onArtwork(shownArt);
        }
        show(chosen);
        waveform.setAccent(coverAccent);
        updateProgress();
    }

    private void updatePlayState(MediaController controller) {
        PlaybackState state;
        try {
            state = controller == null ? null : controller.getPlaybackState();
        } catch (RuntimeException ignored) {
            state = null;
        }
        boolean isPlaying = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        play.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(activity.getString(isPlaying ? R.string.pause : R.string.play));
        visualSource.setPlaying(isPlaying);
        waveform.setPlaying(isPlaying);
        aura.setPlaying(isPlaying);
    }

    private boolean hasLiveSession() {
        for (MediaController controller : controllers) {
            PlaybackState state;
            try {
                state = controller.getPlaybackState();
            } catch (RuntimeException ignored) {
                continue;
            }
            if (state == null) continue;
            int playback = state.getState();
            if (playback != PlaybackState.STATE_STOPPED && playback != PlaybackState.STATE_ERROR
                    && playback != PlaybackState.STATE_NONE) return true;
        }
        return false;
    }

    private boolean metadataEmpty(MediaController controller) {
        MediaMetadata metadata;
        try {
            metadata = controller.getMetadata();
        } catch (RuntimeException ignored) {
            return true;
        }
        if (metadata == null) return true;
        String name = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        return name == null || name.trim().isEmpty();
    }

    private void scheduleMetadataGrace() {
        if (metadataGracePending) return;
        metadataGracePending = true;
        handler.removeCallbacks(metadataGraceExpired);
        handler.postDelayed(metadataGraceExpired, METADATA_GRACE_MS);
    }

    private void cancelMetadataGrace() {
        metadataGracePending = false;
        handler.removeCallbacks(metadataGraceExpired);
    }

    private Bitmap scaleCover(Bitmap bitmap) {
        try {
            int max = Math.round(128 * activity.getResources().getDisplayMetrics().density);
            if (bitmap.getWidth() <= max && bitmap.getHeight() <= max) return bitmap;
            float scale = Math.min(max / (float) bitmap.getWidth(), max / (float) bitmap.getHeight());
            return Bitmap.createScaledBitmap(bitmap,
                    Math.max(1, Math.round(bitmap.getWidth() * scale)),
                    Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
        } catch (RuntimeException | OutOfMemoryError ignored) {
            return bitmap;
        }
    }

    private int artworkColor(Bitmap bitmap) {
        Bitmap readable = bitmap;
        boolean copy = false;
        try {
            if (bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                readable = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                copy = readable != null;
            }
            if (readable == null) return 0xFFFF9E7A;
            long r = 0, g = 0, b = 0;
            int count = 0;
            int stepX = Math.max(1, readable.getWidth() / 12);
            int stepY = Math.max(1, readable.getHeight() / 12);
            for (int y = 0; y < readable.getHeight(); y += stepY) {
                for (int x = 0; x < readable.getWidth(); x += stepX) {
                    int p = readable.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    int pr = Color.red(p), pg = Color.green(p), pb = Color.blue(p);
                    int max = Math.max(pr, Math.max(pg, pb));
                    int min = Math.min(pr, Math.min(pg, pb));
                    if (max - min < 24) continue;   // skip white/grey details
                    r += pr; g += pg; b += pb; count++;
                }
            }
            if (count == 0) return 0xFFFF9E7A;
            return Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        } catch (RuntimeException | OutOfMemoryError ignored) {
            return 0xFFFF9E7A;
        } finally {
            if (copy && readable != null) readable.recycle();
        }
    }

    private void updateProgress() {
        if (active == null) return;
        if (!visible || !spaceVisible) {
            visualSource.setPlaying(false);
            waveform.setPlaying(false);
            aura.setPlaying(false);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now < seekPreviewUntil) {
            waveform.setProgress(durationMs > 0 ? seekPreviewMs / (float) durationMs : 0f);
            elapsed.setText(format(seekPreviewMs));
            duration.setText(durationMs > 0 ? format(durationMs) : "");
            waveform.setEnabled(durationMs > 0);
            return;
        }
        PlaybackState state;
        try {
            state = active.getPlaybackState();
        } catch (RuntimeException ignored) {
            state = null;
        }
        long position = state == null ? 0 : Math.max(0, state.getPosition());
        if (state != null && state.getState() == PlaybackState.STATE_PLAYING
                && state.getLastPositionUpdateTime() > 0) {
            long since = Math.max(0, now - state.getLastPositionUpdateTime());
            position += (long) (since * state.getPlaybackSpeed());
        }
        if (durationMs > 0) position = Math.min(durationMs, position);
        boolean isPlaying = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        visualSource.setPlayback(isPlaying, position);
        waveform.setPlaying(isPlaying);
        aura.setPlaying(isPlaying);
        waveform.setProgress(durationMs > 0 ? position / (float) durationMs : 0f);
        elapsed.setText(durationMs > 0 ? format(position) : "");
        duration.setText(durationMs > 0 ? format(durationMs) : "");
        waveform.setEnabled(durationMs > 0);
    }

    private String format(long ms) {
        long total = Math.max(0, ms) / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60);
    }

    private void show(MediaController controller) {
        active = controller;
        if (controller == null) {
            hide(true);
            return;
        }
        boolean wasVisible = visible;
        visible = true;
        applyConfig();
        if (spaceVisible) {
            setBarFocusable(true);
            card.animate().cancel();
            card.setVisibility(View.VISIBLE);
            card.setAlpha(1f);
            card.setTranslationY(0f);
            showControls();
            startTicking();
            notifyVisibility(true);
            if (!wasVisible) {
                refreshGlass();
                card.setTranslationY(dp(12));
                card.animate().translationY(0f).setDuration(240).start();
            }
        } else {
            setBarFocusable(false);
            card.setVisibility(View.GONE);
            card.setAlpha(0f);
            card.setTranslationY(0f);
            stopVisual();
            notifyVisibility(false);
        }
        position();
    }

    /** Slides the bar down and fades it out; Back and the X both use this. */
    private void hide(boolean animate) {
        if (!visible) return;
        boolean hadFocus = card.hasFocus();
        visible = false;
        cancelMetadataGrace();
        stopVisual();
        notifyVisibility(false);
        lastTitle = null;
        lastMediaKey = null;
        durationMs = 0;
        setBarFocusable(false);
        if (artworkListener != null && lastSentArt != null) {
            lastSentArt = null;
            artworkListener.onArtwork(null);
        }
        card.animate().cancel();
        if (animate && card.getVisibility() == View.VISIBLE && spaceVisible) {
            card.animate().alpha(0f).translationY(dp(12)).setDuration(220)
                    .withEndAction(() -> {
                        card.setVisibility(View.GONE);
                        card.setTranslationY(0f);
                    }).start();
        } else {
            card.setVisibility(View.GONE);
            card.setAlpha(0f);
            card.setTranslationY(0f);
        }
        if (hadFocus) focusDock.run();
    }

    private void position() {
        if (!visible) return;
        card.post(() -> {
            if (!visible || card.getParent() == null) return;
            View root = (View) card.getParent();
            if (card.getHeight() > 0) barHeight = card.getHeight();
            int fallback = card.getLayoutParams() != null ? card.getLayoutParams().height : 0;
            int height = card.getHeight() > 0 ? card.getHeight()
                    : fallback > 0 ? fallback : barHeight > 0 ? barHeight : dp(80);
            float bottom = dockTop > 0 ? dockTop : root.getHeight() * 0.55f;
            float y = bottom - height - dp(12 + Config.dockGap(activity));
            boolean fits = y >= dp(100);
            if (fits != spaceVisible) {
                spaceVisible = fits;
                card.animate().cancel();
                if (fits) {
                    setBarFocusable(true);
                    card.setVisibility(View.VISIBLE);
                    card.setAlpha(1f);
                    card.setTranslationY(0f);
                    notifyVisibility(true);
                    startTicking();
                } else {
                    boolean hadFocus = card.hasFocus();
                    setBarFocusable(false);
                    if (hadFocus) {
                        card.clearFocus();
                        focusDock.run();
                    }
                    stopVisual();
                    card.setVisibility(View.GONE);
                    card.setAlpha(0f);
                    card.setTranslationY(0f);
                    notifyVisibility(false);
                }
            }
            if (spaceVisible) card.setY(Math.max(dp(100), y));
        });
    }

    /** No invisible control may keep focus while the bar is hidden or has no room. */
    private void setBarFocusable(boolean enabled) {
        for (View v : new View[]{open, waveform, previous, play, next, fullscreen, close}) {
            v.setFocusable(enabled);
        }
    }

    private void stopVisual() {
        handler.removeCallbacks(tick);
        visualSource.setPlaying(false);
        waveform.setPlaying(false);
        aura.setPlaying(false);
    }

    private void notifyVisibility(boolean want) {
        if (reportedVisible == want) return;
        reportedVisible = want;
        if (visibility != null) visibility.onVisibilityChanged(want);
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = activity.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            return pkg;
        }
    }

    private Drawable appIcon(String pkg) {
        try {
            return activity.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            return activity.getDrawable(R.drawable.ic_apps);
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
