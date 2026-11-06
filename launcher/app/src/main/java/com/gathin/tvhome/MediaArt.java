package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The current media artwork and label, read locally for the screensaver and the Now Playing shelf. */
final class MediaArt {
    static final class Entry {
        Bitmap bitmap;
        String title, artist, pkg;
        boolean playing, buffering;
        /** The position at the moment this entry was read, already advanced while playing. */
        long position, duration;
        /** The raw position from the session and what is needed to advance it from now on. */
        long basePosition;
        float speed;
        long lastPositionUpdateTime;
        PendingIntent sessionActivity;

        /** The position this session would show at a later elapsed-realtime value. */
        long livePosition(long elapsedRealtime) {
            long value = basePosition;
            if (playing && speed > 0f && lastPositionUpdateTime > 0) {
                long since = Math.max(0L, elapsedRealtime - lastPositionUpdateTime);
                value += (long) (since * speed);
            }
            if (duration > 0) value = Math.min(duration, value);
            return Math.max(0L, value);
        }
    }

    private MediaArt() { }

    static Entry current(Context context) {
        try {
            MediaController controller = bestController(sessions(context));
            return controller == null ? null : entry(controller);
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /** The active session for one app, or null. Used by the Continue Watching shelf for YouTube and Spotify. */
    static Entry forPackage(Context context, String pkg) {
        try {
            List<MediaController> matching = new ArrayList<>();
            for (MediaController controller : sessions(context)) {
                if (pkg.equals(controller.getPackageName())) matching.add(controller);
            }
            MediaController controller = bestController(matching);
            return controller == null ? null : entry(controller);
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /**
     * The one selection rule shared by the bar, the full-screen player and the shelves: a playing session
     * wins, then one that is buffering, then a paused session that still has metadata. Stopped, errored and
     * empty sessions are never chosen. Equal priorities keep the session list's recency order, with the
     * last playback update as the tie-break.
     */
    static MediaController bestController(List<MediaController> controllers) {
        if (controllers == null || controllers.isEmpty()) return null;
        MediaController best = null;
        int bestRank = 0;
        long bestRecency = Long.MIN_VALUE;
        for (MediaController controller : controllers) {
            if (controller == null) continue;
            PlaybackState state;
            MediaMetadata metadata;
            try {
                state = controller.getPlaybackState();
                metadata = controller.getMetadata();
            } catch (RuntimeException ignored) {
                continue;
            }
            int playback = state == null ? PlaybackState.STATE_NONE : state.getState();
            if (playback == PlaybackState.STATE_STOPPED || playback == PlaybackState.STATE_ERROR
                    || playback == PlaybackState.STATE_NONE) continue;
            int rank;
            if (playback == PlaybackState.STATE_PLAYING) {
                rank = 3;
            } else if (playback == PlaybackState.STATE_BUFFERING) {
                rank = 2;
            } else {
                try {
                    if (metadata == null || !hasMetadata(metadata)) continue;
                } catch (RuntimeException ignored) {
                    continue;
                }
                rank = 1;
            }
            long recency = state == null ? 0L : state.getLastPositionUpdateTime();
            if (rank > bestRank || (rank == bestRank && recency > bestRecency)) {
                best = controller;
                bestRank = rank;
                bestRecency = recency;
            }
        }
        return best;
    }

    private static boolean hasMetadata(MediaMetadata metadata) {
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (title != null && !title.trim().isEmpty()) return true;
        return metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null
                || metadata.getBitmap(MediaMetadata.METADATA_KEY_ART) != null
                || metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) != null;
    }

    static List<MediaController> sessions(Context context) {
        MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
        if (manager == null) return Collections.emptyList();
        ComponentName listener = new ComponentName(context, GuardRevival.class);
        List<MediaController> active = manager.getActiveSessions(listener);
        return active == null ? Collections.<MediaController>emptyList() : active;
    }

    private static Entry entry(MediaController controller) {
        MediaMetadata metadata = controller.getMetadata();
        Entry entry = new Entry();
        entry.pkg = controller.getPackageName();
        if (metadata != null) {
            entry.bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (entry.bitmap == null) entry.bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (entry.bitmap == null) entry.bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            entry.title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            entry.artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (entry.artist == null) entry.artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            entry.duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        }
        PlaybackState state = controller.getPlaybackState();
        if (state != null) {
            entry.playing = state.getState() == PlaybackState.STATE_PLAYING;
            entry.buffering = state.getState() == PlaybackState.STATE_BUFFERING;
            entry.basePosition = state.getPosition();
            entry.speed = state.getPlaybackSpeed();
            entry.lastPositionUpdateTime = state.getLastPositionUpdateTime();
        }
        entry.position = entry.livePosition(SystemClock.elapsedRealtime());
        entry.sessionActivity = controller.getSessionActivity();
        return entry;
    }
}
