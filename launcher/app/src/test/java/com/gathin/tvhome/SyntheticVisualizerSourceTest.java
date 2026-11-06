package com.gathin.tvhome;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SyntheticVisualizerSourceTest {
    private static final class Clock implements java.util.function.LongSupplier {
        final AtomicLong now = new AtomicLong(10_000);
        @Override public long getAsLong() { return now.get(); }
        void advance(long ms) { now.addAndGet(ms); }
    }

    @Test public void pausedSourceDoesNotAdvance() {
        Clock clock = new Clock();
        SyntheticVisualizerSource source = new SyntheticVisualizerSource(clock);
        source.setPlayback(false, 0);
        source.frame();
        float before = source.band(0);
        clock.advance(1_000);
        source.frame();
        assertEquals("a paused visualizer must not allocate or animate bands",
                before, source.band(0), 0f);
    }

    @Test public void playingSourceAdvancesAtMostOncePerThirtyFpsFrame() {
        Clock clock = new Clock();
        SyntheticVisualizerSource source = new SyntheticVisualizerSource(clock);
        source.setPlayback(true, 0);
        float first = source.band(0);
        clock.advance(16);
        source.frame();
        assertEquals("16 ms is too early for the next frame", first, source.band(0), 0f);
        clock.advance(17);
        source.frame();
        float second = source.band(0);
        assertTrue("33 ms should produce a new frame", Math.abs(second - first) > 0.0001f);
        clock.advance(16);
        source.frame();
        assertEquals("the next 16 ms is still inside the same 30 fps frame", second, source.band(0), 0f);
        clock.advance(17);
        source.frame();
        assertTrue("the following frame changes the pattern again",
                Math.abs(source.band(0) - second) > 0.0001f);
    }

    @Test public void everyBandStaysInRange() {
        Clock clock = new Clock();
        SyntheticVisualizerSource source = new SyntheticVisualizerSource(clock);
        source.setPlayback(true, 0);
        for (int frame = 0; frame < 300; frame++) {
            clock.advance(33);
            source.frame();
            for (int band = 0; band < SyntheticVisualizerSource.BANDS; band++) {
                float value = source.band(band);
                assertTrue("band " + band + " below range: " + value, value >= 0.08f);
                assertTrue("band " + band + " above range: " + value, value <= 1f);
                assertTrue("peak " + band + " below range", source.peak(band) >= 0.08f);
                assertTrue("peak " + band + " above range", source.peak(band) <= 1f);
            }
        }
    }

    @Test public void overallLevelsAreAveragesOfTheirBands() {
        Clock clock = new Clock();
        SyntheticVisualizerSource source = new SyntheticVisualizerSource(clock);
        source.setPlayback(true, 0);
        clock.advance(33);
        source.frame();
        float bass = 0, mid = 0, treble = 0;
        for (int i = 0; i < SyntheticVisualizerSource.BANDS; i++) {
            if (i < 8) bass += source.band(i);
            else if (i < 20) mid += source.band(i);
            else treble += source.band(i);
        }
        assertEquals(bass / 8f, source.bass(), 0.0001f);
        assertEquals(mid / 12f, source.mid(), 0.0001f);
        assertEquals(treble / 12f, source.treble(), 0.0001f);
    }
}
