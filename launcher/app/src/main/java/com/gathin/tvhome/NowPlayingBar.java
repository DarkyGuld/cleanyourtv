package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.LinearLayout;

/**
 * The music bar's root. Holds the 500 ms D-pad long-press that opens its settings, anywhere in the
 * bar, without stealing a short press from the focused control.
 */
public final class NowPlayingBar extends LinearLayout {
    interface LongPress {
        void onLongPress();
    }

    private static final long LONG_PRESS_MS = 500L;
    private LongPress listener;
    private boolean fired;
    private final Path clip = new Path();
    private int cornerRadius;
    private long downAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable fire = () -> {
        if (listener == null) return;
        fired = true;
        listener.onLongPress();
    };

    public NowPlayingBar(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public NowPlayingBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    void setOnBarLongPress(LongPress listener) {
        this.listener = listener;
    }

    /** Clip every child to the same rounded rectangle, so the left and right ends match. */
    void setCornerRadius(int radius) {
        if (cornerRadius == radius) return;
        cornerRadius = radius;
        rebuildClip();
        invalidate();
    }

    private void rebuildClip() {
        clip.reset();
        if (cornerRadius > 0 && getWidth() > 0 && getHeight() > 0) {
            clip.addRoundRect(new RectF(0, 0, getWidth(), getHeight()), cornerRadius, cornerRadius,
                    Path.Direction.CW);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildClip();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (cornerRadius <= 0 || clip.isEmpty()) {
            super.dispatchDraw(canvas);
            return;
        }
        int save = canvas.save();
        canvas.clipPath(clip);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.isLongPress() || event.getRepeatCount() > 0) {
                    handler.removeCallbacks(fire);
                    fire.run();
                    return true;
                }
                if (event.getRepeatCount() == 0) {
                    downAt = event.getEventTime();
                    fired = false;
                    handler.removeCallbacks(fire);
                    handler.postDelayed(fire, LONG_PRESS_MS);
                }
                if (fired) return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(fire);
                if (!fired && event.getEventTime() - downAt >= LONG_PRESS_MS) {
                    fire.run();   // some remotes deliver only DOWN and a late UP, without repeats
                }
                if (fired) {
                    fired = false;
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(fire);
        super.onDetachedFromWindow();
    }
}
