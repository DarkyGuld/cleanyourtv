package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.dreams.DreamService;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Screensaver engine: the current track in a flat, large full-screen layout. With
 * screensaver_player_music_only on, it ends immediately when no media session is playing.
 */
public class NowPlayingDream extends DreamService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SyntheticVisualizerSource visualSource = new SyntheticVisualizerSource();
    private ImageView background, art;
    private TextView title, artist, time;
    private WaveformSeekBar waveform;
    private Bitmap lastArt;
    private Bitmap blurredArt;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            if (dreaming) handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_now_playing);
        background = findViewById(R.id.dreamBackground);
        art = findViewById(R.id.dreamArt);
        title = findViewById(R.id.dreamTitle);
        artist = findViewById(R.id.dreamArtist);
        time = findViewById(R.id.dreamTime);
        waveform = findViewById(R.id.dreamWaveform);
        waveform.setMode(Config.npVisualMode(this));
        waveform.setGlow(Config.npGlow(this));
        waveform.setAccent(Config.npAccentColor(this));
        waveform.setSource(visualSource);
        refresh();
    }

    private void refresh() {
        MediaArt.Entry entry;
        try {
            entry = MediaArt.current(this);
        } catch (RuntimeException unavailable) {
            entry = null;
        }
        if (entry == null || (Config.screensaverPlayerMusicOnly(this) && !entry.playing)) {
            if (Config.screensaverPlayerMusicOnly(this)) {
                finish();
                return;
            }
            title.setText(R.string.screensaver_player_no_music);
            artist.setText("");
            waveform.setPlaying(false);
            waveform.setProgress(0f);
            time.setText("");
            if (blurredArt != null) {
                blurredArt.recycle();
                blurredArt = null;
            }
            background.setImageDrawable(null);
            art.setImageDrawable(null);
            return;
        }
        String name = entry.title == null ? "" : entry.title;
        String by = entry.artist == null ? "" : entry.artist;
        title.setText(name.isEmpty() ? entry.pkg : name);
        artist.setText(by);
        long duration = Math.max(0, entry.duration);
        long position = entry.livePosition(SystemClock.elapsedRealtime());
        waveform.setEnabled(duration > 0);
        waveform.setPlaying(entry.playing);
        waveform.setProgress(duration > 0 ? position / (float) duration : 0f);
        time.setText(format(position) + " / " + format(duration));
        Bitmap bitmap = entry.bitmap;
        if (bitmap != lastArt) {
            lastArt = bitmap;
            if (bitmap != null) {
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                art.setImageBitmap(bitmap);
                art.setVisibility(View.VISIBLE);
                Bitmap blurred = HeroArt.from(new BitmapDrawable(getResources(), bitmap));
                if (blurredArt != null && blurredArt != blurred) blurredArt.recycle();
                blurredArt = blurred;
                background.setImageBitmap(blurred);
                background.setAlpha(0.58f);
            } else {
                art.setImageDrawable(null);
                art.setVisibility(View.GONE);
                if (blurredArt != null) {
                    blurredArt.recycle();
                    blurredArt = null;
                }
                background.setImageDrawable(null);
            }
        }
        visualSource.setTrack(name, by);
        visualSource.setPlayback(entry.playing, position);
    }

    private String format(long ms) {
        long total = Math.max(0, ms) / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60);
    }

    private boolean dreaming;

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        dreaming = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    public void onDreamingStopped() {
        dreaming = false;
        handler.removeCallbacks(tick);
        waveform.setPlaying(false);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        if (blurredArt != null) {
            blurredArt.recycle();
            blurredArt = null;
        }
        super.onDetachedFromWindow();
    }
}
