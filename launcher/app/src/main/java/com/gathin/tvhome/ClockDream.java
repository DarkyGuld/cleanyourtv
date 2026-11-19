package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.widget.TextView;

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow (ClockScene).
 * No network, no images: cheap enough for a 1 GB TV.
 *
 * The current song is re-read while dreaming, so a track change updates the title (and the
 * artwork when the clock-art setting is on) instead of keeping the song that started the dream.
 */
public class ClockDream extends DreamService {

    private ClockScene scene;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean dreaming;
    private String lastSong;
    private Bitmap lastArt;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            loadMedia();
            if (dreaming) handler.postDelayed(this, 1200);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);
        ClockFormat.apply(getWindow().getDecorView(), Config.load(this).clockFormat);
        scene = new ClockScene(getWindow().getDecorView());
        // Start with the wallpaper/glow at once; the media session is read off the main thread.
        scene.useHomeWallpaper(this);
        loadMedia();
    }

    private void loadMedia() {
        if (scene == null) return;
        scene.loadMediaArt(this, media -> {
            TextView title = findViewById(R.id.dreamMediaTitle);
            if (title == null) return;
            if (media == null || !Config.screensaverClockSong(this)) {
                lastSong = null;
                title.setVisibility(View.GONE);
                return;
            }
            if (Config.screensaverMedia(this) && media.bitmap != null && media.bitmap != lastArt) {
                lastArt = media.bitmap;
                scene.useMediaArt(media.bitmap);
            }
            String text = media.title == null ? "" : media.title;
            if (media.artist != null && !media.artist.trim().isEmpty()) {
                text += (text.isEmpty() ? "" : "  ·  ") + media.artist;
            }
            if (text.trim().isEmpty()) {
                lastSong = null;
                title.setVisibility(View.GONE);
                return;
            }
            if (!text.equals(lastSong)) {
                lastSong = text;
                title.setText(text);
            }
            title.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        scene.start();
        dreaming = true;
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override
    public void onDreamingStopped() {
        dreaming = false;
        handler.removeCallbacks(refresh);
        scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        dreaming = false;
        handler.removeCallbacks(refresh);
        lastArt = null;
        if (scene != null) scene.close();
        super.onDetachedFromWindow();
    }
}
