package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/**
 * The clock screensaver shown by Home itself after home_idle_clock_minutes without input. Some TVs' own
 * screens (CVTE's live TV) do not keep the screen on, so the system screensaver would cut into programmes;
 * this one only ever starts from Home. Any key returns to Home.
 */
public class ClockActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }


    private ClockScene scene;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dream_clock);
        if ("hitech".equals(Config.clockStyle(this))) {
            // Glass only around the time; the date stays on the wallpaper below it.
            View timeRow = findViewById(R.id.dreamTimeRow);
            timeRow.setBackgroundResource(R.drawable.clock_hitech_bg);
            int h = dp(26), v = dp(14);
            timeRow.setPadding(h, v, h, v);
        }
        ClockFormat.apply(getWindow().getDecorView(), Config.load(this).clockFormat);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);
        scene = new ClockScene(getWindow().getDecorView());
        // Start with the wallpaper/glow at once; the media session is read off the main thread.
        scene.useHomeWallpaper(this);
        scene.loadMediaArt(this, media -> {
            if (isFinishing() || isDestroyed()) return;
            if (media == null || media.bitmap == null) return;
            scene.useMediaArt(media.bitmap);
            TextView mediaTitle = findViewById(R.id.dreamMediaTitle);
            if (mediaTitle != null) {
                String text = media.title == null ? "" : media.title;
                if (media.artist != null && !media.artist.trim().isEmpty()) {
                    text += (text.isEmpty() ? "" : "  ·  ") + media.artist;
                }
                if (!text.trim().isEmpty()) {
                    mediaTitle.setText(text);
                    mediaTitle.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        scene.start();
    }

    @Override
    protected void onPause() {
        scene.stop();
        super.onPause();
        finish();   // never left behind in the back stack
    }

    @Override
    protected void onDestroy() {
        if (scene != null) scene.close();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // The key that wakes the clock should not also act on Home, so it is consumed here.
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        finish();
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Touch TVs/projectors have no key to wake the clock.
        finish();
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
