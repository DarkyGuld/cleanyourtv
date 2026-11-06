package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * One progress/visualizer view for all six Now Playing styles. A shared SyntheticVisualizerSource
 * drives every mode; no audio capture, no allocation in onDraw and no timer unless the bar is
 * visible, playing and the selected mode is not Off.
 */
public final class WaveformSeekBar extends View {
    public interface OnSeekListener { void onSeek(float progress); }

    public static final int MODE_OFF = 0;
    public static final int MODE_WAVES = 1;
    public static final int MODE_OSCILLOSCOPE = 2;
    public static final int MODE_AURA = 3;
    public static final int MODE_ORB = 4;
    public static final int MODE_MINIMAL = 5;

    private final Paint played = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unplayed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();
    private final float density;
    private SyntheticVisualizerSource source;
    private float progress = 0.45f;
    private int accent = 0xFFFFFFFF;
    private int mode = MODE_WAVES;
    private boolean playing;
    private boolean attached = true;
    private boolean glow = true;
    private OnSeekListener seekListener;

    public WaveformSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        setFocusableInTouchMode(false);
        played.setColor(0xFFFFFFFF);
        played.setStrokeCap(Paint.Cap.ROUND);
        unplayed.setColor(0x55FFFFFF);
        unplayed.setStrokeCap(Paint.Cap.ROUND);
        accentPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setSource(SyntheticVisualizerSource value) { source = value; }
    public void setMode(int value) {
        int next = Math.max(MODE_OFF, Math.min(MODE_MINIMAL, value));
        if (mode == next) return;
        mode = next;
        invalidate();
    }
    public void setGlow(boolean value) { glow = value; invalidate(); }
    public void setProgress(float value) { progress = Math.max(0f, Math.min(1f, value)); invalidate(); }
    public void setPlaying(boolean value) {
        if (playing == value) return;
        playing = value;
        invalidate();
    }
    public void setAccent(int color) {
        accent = 0xFF000000 | (color & 0xFFFFFF);
        invalidate();
    }
    public void setOnSeekListener(OnSeekListener listener) { seekListener = listener; }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; }
    @Override protected void onDetachedFromWindow() { attached = false; super.onDetachedFromWindow(); }
    @Override protected void onFocusChanged(boolean gainFocus, int direction, Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        invalidate();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Disabled means the track has no duration (or the bar has no room): never move or notify.
        if (!isEnabled()) return false;
        float direction = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1f
                : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1f : 0f;
        if (direction == 0f) return super.onKeyDown(keyCode, event);
        float step = 0.02f + Math.min(0.06f, event.getRepeatCount() * 0.008f);
        setProgress(progress + direction * step);
        if (seekListener != null) seekListener.onSeek(progress);
        return true;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (!isEnabled()) return;
        info.setClassName(android.widget.SeekBar.class.getName());
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, 1f, progress));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
    }

    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()) {
            if (!isEnabled() || arguments == null) return false;
            float value = arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, -1f);
            if (value < 0f) return false;
            setProgress(value);
            if (seekListener != null) seekListener.onSeek(progress);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (!attached) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float mid = h / 2f;
        // Each draw path sets its own stroke style; start from filled so Minimal stays filled dots.
        played.setStyle(Paint.Style.FILL);
        unplayed.setStyle(Paint.Style.FILL);
        accentPaint.setStyle(Paint.Style.FILL);
        if (mode != MODE_OFF && source != null && playing) source.frame();
        if (isFocused()) {
            accentPaint.setColor((0x22 << 24) | (accent & 0xFFFFFF));
            canvas.drawRoundRect(0, 0, w, h, h / 2f, h / 2f, accentPaint);
        }
        switch (mode) {
            case MODE_OFF: drawCleanLine(canvas, w, h, mid); break;
            case MODE_WAVES: drawWaves(canvas, w, h, mid); break;
            case MODE_OSCILLOSCOPE: drawOscilloscope(canvas, w, h, mid); break;
            case MODE_AURA: drawCleanLine(canvas, w, h, mid); break;
            case MODE_ORB: drawOrb(canvas, w, h, mid); break;
            case MODE_MINIMAL: drawMinimal(canvas, w, h, mid); break;
        }
        if (isFocused()) {
            long now = SystemClock.uptimeMillis();
            float pulse = 0.72f + 0.28f * (float) Math.sin(now / 300f);
            float lineY = h - Math.max(1f, density * 1.5f);
            accentPaint.setColor((Math.round(0xA0 * pulse) << 24) | (accent & 0xFFFFFF));
            canvas.drawRoundRect(0, lineY, w, h, h / 4f, h / 4f, accentPaint);
            accentPaint.setColor((Math.round(0x55 * pulse) << 24) | (accent & 0xFFFFFF));
            canvas.drawCircle(progress * w, mid, density * 8f * pulse, accentPaint);
        }
        if (mode != MODE_OFF && playing && isShown() && getWindowVisibility() == VISIBLE) {
            postInvalidateDelayed(33);
        }
    }

    private void drawCleanLine(Canvas canvas, int w, int h, float mid) {
        float line = Math.max(3f, density * 4f);
        played.setStrokeWidth(line);
        unplayed.setStrokeWidth(line);
        float x = progress * w;
        float knob = line * 1.1f;
        float drawX = Math.max(knob, Math.min(w - knob, x));
        canvas.drawLine(x, mid, w, mid, unplayed);
        canvas.drawLine(0, mid, x, mid, played);
        if (isFocused()) {
            float pulse = 0.8f + 0.2f * (float) Math.sin(SystemClock.uptimeMillis() / 260f);
            accentPaint.setColor((0x66 << 24) | (accent & 0xFFFFFF));
            canvas.drawCircle(drawX, mid, line * 2.6f * pulse, accentPaint);
        }
        canvas.drawCircle(drawX, mid, knob, played);
    }

    private void drawWaves(Canvas canvas, int w, int h, float mid) {
        int count = SyntheticVisualizerSource.BANDS;
        float step = w / (float) count;
        float barWidth = Math.max(density * 1.6f, step * 0.42f);
        float maxHeight = h * 0.82f;
        float progressX = progress * w;
        played.setStrokeWidth(barWidth);
        unplayed.setStrokeWidth(barWidth);
        for (int i = 0; i < count; i++) {
            float value = source == null ? 0.3f : source.band(i);
            float peak = source == null ? value : source.peak(i);
            float x = step * (i + 0.5f);
            float barHeight = Math.max(barWidth, value * maxHeight);
            Paint paint = x <= progressX ? played : unplayed;
            canvas.drawLine(x, mid - barHeight / 2f, x, mid + barHeight / 2f, paint);
            if (glow) {
                accentPaint.setColor((0x99 << 24) | (accent & 0xFFFFFF));
                canvas.drawCircle(x, mid - Math.max(barHeight, peak * maxHeight) / 2f - density * 2f,
                        Math.max(1f, barWidth * 0.34f), accentPaint);
            }
        }
    }

    private void drawOscilloscope(Canvas canvas, int w, int h, float mid) {
        float progressX = progress * w;
        int count = SyntheticVisualizerSource.BANDS;
        float step = w / (float) (count - 1);
        wavePath.reset();
        wavePath.moveTo(0, mid);
        for (int i = 0; i < count; i++) {
            float value = source == null ? 0.3f : source.band(i);
            wavePath.lineTo(i * step, mid + (value - 0.5f) * h * 0.72f);
        }
        unplayed.setStrokeWidth(density * 1.6f);
        unplayed.setStyle(Paint.Style.STROKE);
        canvas.drawPath(wavePath, unplayed);
        // The full waveform stays visible from the first second to the last; only its colour
        // follows the played position.
        int save = canvas.save();
        canvas.clipRect(0, 0, progressX, h);
        accentPaint.setStyle(Paint.Style.STROKE);
        accentPaint.setStrokeWidth(density * 2.2f);
        accentPaint.setColor((glow ? 0xCC : 0x99) << 24 | (accent & 0xFFFFFF));
        canvas.drawPath(wavePath, accentPaint);
        canvas.restoreToCount(save);
        float knob = density * 3f;
        float x = Math.max(knob, Math.min(w - knob, progressX));
        accentPaint.setColor((0xCC << 24) | (accent & 0xFFFFFF));
        canvas.drawCircle(x, mid, knob, accentPaint);
        accentPaint.setStyle(Paint.Style.FILL);
    }

    private void drawOrb(Canvas canvas, int w, int h, float mid) {
        drawCleanLine(canvas, w, h, mid);
        float bass = source == null ? 0.3f : source.bass();
        float midValue = source == null ? 0.3f : source.mid();
        float radius = Math.min(h * 0.34f, density * (4f + bass * 9f));
        float x = Math.max(radius, Math.min(w - radius, progress * w));
        if (glow) {
            accentPaint.setColor((0x33 << 24) | (accent & 0xFFFFFF));
            canvas.drawCircle(x, mid, Math.min(h * 0.48f, radius * 1.8f), accentPaint);
        }
        accentPaint.setColor((0xCC << 24) | (accent & 0xFFFFFF));
        canvas.drawCircle(x, mid, radius, accentPaint);
        canvas.drawCircle(x, mid, Math.max(1.5f, radius * 0.32f), played);
        float orbit = radius + density * (3f + midValue * 4f);
        // Keep the orbiting particle inside the 26-28 dp view instead of clipping it top/bottom.
        float orbitY = Math.min(orbit, Math.max(0f, h / 2f - radius - density * 2f));
        float angle = (SystemClock.uptimeMillis() / 900f) % 6.2831855f;
        canvas.drawCircle(x + (float) Math.cos(angle) * orbit, mid + (float) Math.sin(angle) * orbitY,
                density * 2f, accentPaint);
    }

    private void drawMinimal(Canvas canvas, int w, int h, float mid) {
        int count = 10;
        float step = w / (float) (count + 1);
        float progressX = progress * w;
        for (int i = 0; i < count; i++) {
            float x = step * (i + 1);
            float value = source == null ? 0.35f : (source.band(i * 3) + source.band(i * 3 + 1)) * 0.5f;
            float radius = Math.max(density * 1.5f, value * h * 0.30f);
            Paint paint = x <= progressX ? played : unplayed;
            canvas.drawCircle(x, mid, radius, paint);
        }
    }
}
