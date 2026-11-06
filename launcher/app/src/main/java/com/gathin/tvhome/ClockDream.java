package com.gathin.tvhome;

import android.service.dreams.DreamService;

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow (ClockScene).
 * No network, no images: cheap enough for a 1 GB TV.
 */
public class ClockDream extends DreamService {

    private ClockScene scene;

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
        scene.loadMediaArt(this, media -> {
            if (media == null || media.bitmap == null) return;
            scene.useMediaArt(media.bitmap);
            android.widget.TextView mediaTitle = findViewById(R.id.dreamMediaTitle);
            if (mediaTitle != null) {
                String text = media.title == null ? "" : media.title;
                if (media.artist != null && !media.artist.trim().isEmpty()) {
                    text += (text.isEmpty() ? "" : "  ·  ") + media.artist;
                }
                if (!text.trim().isEmpty()) {
                    mediaTitle.setText(text);
                    mediaTitle.setVisibility(android.view.View.VISIBLE);
                }
            }
        });
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        scene.start();
    }

    @Override
    public void onDreamingStopped() {
        scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (scene != null) scene.close();
        super.onDetachedFromWindow();
    }
}
