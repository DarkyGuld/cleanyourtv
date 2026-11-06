package com.gathin.tvhome;

import android.os.SystemClock;

import java.util.function.LongSupplier;

/**
 * One allocation-free, playback-driven visualizer source shared by the progress bar and the cover
 * aura. It advances at most once per 33 ms; when paused or hidden the views stop calling it, so no
 * timer or audio capture runs. The track hash changes the pattern per song.
 */
final class SyntheticVisualizerSource {
    static final int BANDS = 32;

    private final float[] bands = new float[BANDS];
    private final float[] peaks = new float[BANDS];
    private int trackHash = 1;
    private long phaseMs;
    private long lastFrame;
    private long positionMs;
    private boolean playing;
    private float bass;
    private float mid;
    private float treble;
    /** Injectable for JVM tests; production always reads the monotonic uptime clock. */
    private final LongSupplier clock;

    SyntheticVisualizerSource() {
        this(SystemClock::uptimeMillis);
    }

    SyntheticVisualizerSource(LongSupplier clock) {
        this.clock = clock;
    }

    /** Call when metadata changes. A different title/artist resets the musical phase. */
    void setTrack(String title, String artist) {
        int hash = 1;
        if (title != null) for (int i = 0; i < title.length(); i++) hash = 31 * hash + title.charAt(i);
        if (artist != null) for (int i = 0; i < artist.length(); i++) hash = 31 * hash + artist.charAt(i);
        if (hash == trackHash) return;
        trackHash = hash == 0 ? 1 : hash;
        phaseMs = 0;
        // Keep the next frame's delta small instead of treating all of uptime as one huge jump.
        lastFrame = clock.getAsLong();
        for (int i = 0; i < BANDS; i++) {
            bands[i] = 0;
            peaks[i] = 0;
        }
    }

    void setPlayback(boolean playing, long positionMs) {
        if (this.playing != playing) lastFrame = clock.getAsLong();
        this.playing = playing;
        this.positionMs = Math.max(0, positionMs);
    }

    void setPlaying(boolean playing) {
        setPlayback(playing, positionMs);
    }

    /** Advances the shared pattern once per 30 fps frame, with no allocations. */
    void frame() {
        if (!playing) return;
        long now = clock.getAsLong();
        long dt = now - lastFrame;
        if (dt < 33) return;
        lastFrame = now;
        phaseMs += dt;
        float t = (phaseMs + positionMs) / 1000f;
        float bassTotal = 0, midTotal = 0, trebleTotal = 0;
        for (int i = 0; i < BANDS; i++) {
            int seed = (trackHash >>> ((i % 16) * 2)) & 0xF;
            float rate = 0.85f + seed * 0.16f;
            float v = 0.16f + 0.84f * Math.abs((float) Math.sin(t * rate + i * 0.37f + (trackHash & 0x3F)));
            v *= 0.76f + 0.24f * (float) Math.sin(t * 0.63f + i * 0.11f);
            if (i < 8) v *= 1.12f;
            if (i >= 24) v *= 0.78f;
            v = Math.max(0.08f, Math.min(1f, v));
            bands[i] = v;
            peaks[i] = Math.max(v, peaks[i] - 0.012f);
            if (i < 8) bassTotal += v;
            else if (i < 20) midTotal += v;
            else trebleTotal += v;
        }
        bass = bassTotal / 8f;
        mid = midTotal / 12f;
        treble = trebleTotal / 12f;
    }

    float band(int index) { return bands[index]; }
    float peak(int index) { return peaks[index]; }
    float bass() { return bass; }
    float mid() { return mid; }
    float treble() { return treble; }
}
