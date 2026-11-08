package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The launcher's settings: the values in res/values/config.xml, with an optional overlay read from
 * {@code config.json} in the app's external files directory
 * ({@code /sdcard/Android/data/com.gathin.tvhome/files/}). Every key in the JSON is optional and an
 * unknown or badly typed key is ignored, so a theme can change the dock and the look without a rebuild:
 * {@code tools/theme.sh themes/<name>} pushes a theme folder there and restarts Home.
 *
 * Read once in {@link MainActivity#onCreate}; nothing here touches the network.
 */
final class Config {
    private static final String TAG = "TvHome.Config";
    static final String FILE_NAME = "config.json";
    static final String WALLPAPER_NAME = "wallpaper.jpg";
    static final String SETTINGS_NAME = "launcher_settings";
    static final String ROWS_BELOW_DOCK_KEY = "rows_below_dock";
    static final String WALLPAPER_CHOICE_KEY = "wallpaper_choice";
    static final String DOCK_OPACITY_KEY = "dock_opacity";
    static final String CLOCK_FORMAT_KEY = "clock_format";
    static final String LIQUID_GLASS_KEY = "liquid_glass";
    static final String CLOCK_STYLE_KEY = "clock_style";
    static final String GLASS_TRANSPARENCY_KEY = "glass_transparency";
    static final String ICON_PACK_KEY = "icon_pack";
    static final String ICON_GLOW_KEY = "icon_glow";
    static final int ICON_GLOW_DEFAULT = 40;
    static final String TOP_BAR_CAPSULE_KEY = "top_bar_capsule";
    static final String DOCK_GAP_KEY = "dock_gap";
    static final String DOCK_BOTTOM_KEY = "dock_bottom";
    static final String SHELF_SHADOW_KEY = "shelf_shadow";
    static final String HERO_TITLE_KEY = "hero_title";
    static final String TILE_BORDER_KEY = "tile_border";
    static final String GLASS_MOTION_KEY = "glass_motion";
    static final String FOCUS_MOTION_KEY = "focus_motion";
    static final String FOCUS_GLOSS_KEY = "focus_gloss";
    static final String EDIT_JIGGLE_KEY = "edit_jiggle";
    static final String PARALLAX_KEY = "parallax";
    static final String TOP_SHELF_KEY = "top_shelf";
    static final String TOP_SHELF_PROGRESS_KEY = "top_shelf_progress";
    static final String TOP_SHELF_AUTO_HIDE_KEY = "top_shelf_auto_hide";
    static final String TOP_SHELF_SOURCES_KEY = "top_shelf_sources";
    static final String TOP_SHELF_NOW_PLAYING_KEY = "top_shelf_now_playing";
    static final String TOP_SHELF_ALL_SOURCES = "*";
    static final String MEDIA_BACKGROUND_KEY = "media_background";
    static final String LIVING_WALLPAPER_KEY = "living_wallpaper";
    static final String NOW_PLAYING_FULL_KEY = "now_playing_full";
    static final String SMART_STACK_KEY = "smart_stack";
    static final String SMART_STACK_SECONDS_KEY = "smart_stack_seconds";
    static final String CONTROL_CENTER_KEY = "control_center";
    static final String APP_NAMES_KEY = "app_names";
    static final String SCREENSAVER_MEDIA_KEY = "screensaver_media";
    static final String SCREENSAVER_CLOCK_SONG_KEY = "screensaver_clock_song";
    static final String AMBIENT_MODE_KEY = "ambient_mode";
    static final String NP_WAVEFORM_KEY = "np_waveform";
    static final String NP_TIMES_KEY = "np_times";
    static final String NP_ALBUM_ART_KEY = "np_album_art";
    static final String NP_SOURCE_KEY = "np_source";
    static final String NP_CLOSE_KEY = "np_close";
    static final String NP_VISUAL_MODE_KEY = "np_visual_mode";
    static final String NP_BG_STYLE_KEY = "np_bg_style";
    static final String NP_BG_OPACITY_KEY = "np_bg_opacity";
    static final String NP_ACCENT_KEY = "np_accent";
    static final String NP_GLOW_KEY = "np_glow";
    // One migration flag for the redesigned bar. The older np_design_v2/v3/v4 flags were written but
    // never read; clearing them keeps the prefs file honest.
    private static final String NP_DESIGN_V5_KEY = "np_design_v5";
    static final String NP_SIZE_KEY = "np_size";
    static final String NP_CORNER_RADIUS_KEY = "np_corner_radius";
    static final String NP_FULL_STYLE_KEY = "np_full_style";
    static final String NP_OVERLAY_KEY = "np_overlay";
    static final String SCREENSAVER_PLAYER_MUSIC_ONLY_KEY = "screensaver_player_music_only";
    static final String NP_BAR_VARIANT_KEY = "np_bar_variant";
    static final String NP_PREV_KEY = "np_prev";
    static final String NP_NEXT_KEY = "np_next";
    static final String AMBIENT_NOW_PLAYING_KEY = "ambient_now_playing";
    static final String AMBIENT_IDLE_MINUTES_KEY = "ambient_idle_minutes";
    static final int NP_BAR_STACKED = 0;
    static final int NP_BAR_EDGE = 1;
    /** Default 40 % see-through: the same glass strength as before, now adjustable. */
    static final int GLASS_TRANSPARENCY_DEFAULT = 15;

    /** Every preference a theme push or an explicit reset clears; SETTINGS_REVISION_KEY stays. */
    private static final Set<String> LOCAL_CHOICE_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "columns", ROWS_BELOW_DOCK_KEY, "animations", "advanced_effects",
            WALLPAPER_CHOICE_KEY, DOCK_OPACITY_KEY, CLOCK_FORMAT_KEY, OpenOnStart.KEY,
            CLOCK_STYLE_KEY, LIQUID_GLASS_KEY, GLASS_TRANSPARENCY_KEY, ICON_PACK_KEY,
            ICON_GLOW_KEY, GLASS_MOTION_KEY, PARALLAX_KEY, TOP_SHELF_KEY,
            TOP_SHELF_PROGRESS_KEY, TOP_SHELF_AUTO_HIDE_KEY, TOP_SHELF_SOURCES_KEY,
            TOP_SHELF_NOW_PLAYING_KEY, LIVING_WALLPAPER_KEY, NOW_PLAYING_FULL_KEY,
            APP_NAMES_KEY, SMART_STACK_KEY, SMART_STACK_SECONDS_KEY, CONTROL_CENTER_KEY,
            MEDIA_BACKGROUND_KEY, SCREENSAVER_MEDIA_KEY, SCREENSAVER_CLOCK_SONG_KEY,
            FOCUS_MOTION_KEY, FOCUS_GLOSS_KEY,
            EDIT_JIGGLE_KEY, TOP_BAR_CAPSULE_KEY, DOCK_GAP_KEY, DOCK_BOTTOM_KEY,
            SHELF_SHADOW_KEY, HERO_TITLE_KEY, TILE_BORDER_KEY, AMBIENT_MODE_KEY,
            NP_WAVEFORM_KEY, NP_TIMES_KEY, NP_ALBUM_ART_KEY, NP_SOURCE_KEY, NP_CLOSE_KEY,
            NP_VISUAL_MODE_KEY, NP_BG_STYLE_KEY, NP_BG_OPACITY_KEY, NP_ACCENT_KEY,
            NP_GLOW_KEY, NP_SIZE_KEY, NP_CORNER_RADIUS_KEY, NP_FULL_STYLE_KEY, NP_OVERLAY_KEY,
            SCREENSAVER_PLAYER_MUSIC_ONLY_KEY, "np_source_text", "np_visualizer", "np_bar_variant",
            "np_prev", "np_next", "ambient_now_playing", "ambient_idle_minutes",
            "screensaver_previous")));

    static Set<String> localChoiceKeys() {
        return LOCAL_CHOICE_KEYS;
    }

    static boolean hasLocalChoices(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE);
        for (String key : LOCAL_CHOICE_KEYS) if (prefs.contains(key)) return true;
        return IconChoices.globalStyle(ctx) != IconChoices.ORIGINAL || IconChoices.hasPerAppChoices(ctx);
    }

    /** Back to the theme's values: the owner's on-TV picks and per-app icon styles. */
    static void clearLocalChoices(Context ctx) {
        SharedPreferences.Editor editor =
                ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE).edit();
        for (String key : LOCAL_CHOICE_KEYS) editor.remove(key);
        // Mark the music-bar migration done; otherwise Home re-adds the default NP keys on the next
        // start and Follow theme looks enabled again right after a reset.
        editor.putBoolean(NP_DESIGN_V5_KEY, true);
        editor.apply();
        IconChoices.clearAll(ctx);
    }

    private static void clearChoiceKeys(SharedPreferences.Editor editor) {
        for (String key : LOCAL_CHOICE_KEYS) editor.remove(key);
        editor.putBoolean(NP_DESIGN_V5_KEY, true);
    }

    /** iOS-style Liquid Glass surfaces; on by default, switchable in Launcher settings. */
    static boolean liquidGlass(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(LIQUID_GLASS_KEY, true);
    }

    /** How much of the wallpaper shows through the glass, 0..100 %. */
    static int glassTransparency(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(GLASS_TRANSPARENCY_KEY, GLASS_TRANSPARENCY_DEFAULT);
        return Math.max(0, Math.min(100, value));
    }

    /**
     * Baked-glass opacity for the current transparency setting. The curve is perceptual rather than
     * linear: 90 % and 95 % still keep a visible glass edge and interior instead of looking identical.
     * 100 % remains fully transparent.
     */
    static float glassOpacity(Context ctx) {
        int transparency = glassTransparency(ctx);
        if (transparency >= 100) return 0f;
        double opacity = Math.sqrt(1f - transparency / 100f);
        // Keep 95 % clearly lighter than 90 %: above 90 the surface fades out faster, while
        // 100 % still means no fixed glass at all.
        if (transparency > 90) opacity *= (100 - transparency) / 10.0;
        return (float) opacity;
    }

    /** 100 %: the glass surface disappears completely; only the content stays. */
    static boolean glassFullyTransparent(Context ctx) {
        return glassTransparency(ctx) >= 100;
    }

    /** Package name of the installed icon pack, or "" for the launcher's own art. */
    static String iconPack(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(ICON_PACK_KEY, "");
    }

    /** Strength of the focus shadow around an app icon, 0..100. */
    static int iconGlow(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(ICON_GLOW_KEY, ICON_GLOW_DEFAULT);
        return Math.max(0, Math.min(100, value));
    }

    /** One glass capsule behind the four top-bar buttons instead of four separate pills. */
    static boolean topBarCapsule(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(TOP_BAR_CAPSULE_KEY, true);
    }

    /** Extra air between the Now Playing bar and the dock, 0/8/16/24 dp. */
    static int dockGap(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(DOCK_GAP_KEY, 8);
        return Math.max(0, Math.min(32, value));
    }

    /** Space under the dock band in dp (0-96): lets the floating dock sit as low or high as the owner wants. */
    static int dockBottom(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(DOCK_BOTTOM_KEY, 8);
        return Math.max(0, Math.min(96, value));
    }

    /** A soft shadow under the icons when the dock band is hidden at 100 % transparency. */
    static boolean shelfShadow(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(SHELF_SHADOW_KEY, true);
    }

    /** 0 = large dock app name, 1 = small, 2 = hidden. */
    static int heroTitle(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(HERO_TITLE_KEY, 1);
        return Math.max(0, Math.min(2, value));
    }

    /** Optional 1 px resting border on every app tile; off unless the owner asks for it. */
    static boolean tileBorder(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(TILE_BORDER_KEY, false);
    }

    /** Use the current media artwork in the clock screensaver. */
    static boolean screensaverMedia(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(SCREENSAVER_MEDIA_KEY, false);
    }

    /** Show the current song title under the Standard clock. */
    static boolean screensaverClockSong(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(SCREENSAVER_CLOCK_SONG_KEY, true);
    }

    /** 0 = still, 1 = slow drift, 2 = quicker drift: local wallpapers move and fade like Aura. */
    static int livingWallpaper(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(LIVING_WALLPAPER_KEY, 0);
        return Math.max(0, Math.min(2, value));
    }

    /** One rotating Continue Watching card instead of the three-card shelf (Aura's Smart Stack). */
    static boolean smartStack(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(SMART_STACK_KEY, false);
    }

    /** Seconds a Smart Stack card stays before the next one. */
    static int smartStackSeconds(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(SMART_STACK_SECONDS_KEY, 8);
        return value < 5 ? 5 : Math.min(30, value);
    }

    /** 0 = under the focused tile, 1 = always, 2 = never. */
    static int appNames(Context ctx, boolean configAlways) {
        int fallback = configAlways ? 1 : 0;
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(APP_NAMES_KEY, fallback);
        return Math.max(0, Math.min(2, value));
    }

    /** The remote's Menu button opens the Control Center panel. */
    static boolean controlCenter(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(CONTROL_CENTER_KEY, false);
    }

    /** Long-press the Now Playing bar to open the full-screen player. */
    static boolean nowPlayingFull(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(NOW_PLAYING_FULL_KEY, true);
    }

    /** Use the current media artwork as a dimmed Home background. */
    static boolean mediaBackground(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(MEDIA_BACKGROUND_KEY, false);
    }

    /** A hairline progress bar along the bottom of each Continue Watching card. */
    static boolean topShelfProgress(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(TOP_SHELF_PROGRESS_KEY, true);
    }

    /** Apps allowed in the Continue Watching shelf; ["*"] means every app. */
    static java.util.Set<String> topShelfSources(Context ctx) {
        java.util.Set<String> value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getStringSet(TOP_SHELF_SOURCES_KEY, java.util.Collections.singleton(TOP_SHELF_ALL_SOURCES));
        return value == null || value.isEmpty() ? java.util.Collections.emptySet()
                : new java.util.HashSet<>(value);
    }

    static boolean topShelfSourceAllowed(Context ctx, String pkg) {
        java.util.Set<String> sources = topShelfSources(ctx);
        return sources.contains(TOP_SHELF_ALL_SOURCES) || sources.contains(pkg);
    }

    /** Show the active media session for the focused app when Watch Next has no row for it. */
    static boolean topShelfNowPlaying(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(TOP_SHELF_NOW_PLAYING_KEY, true);
    }

    /** Hide the Continue Watching shelf after ten idle seconds; focusing another app brings it back. */
    static boolean topShelfAutoHide(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(TOP_SHELF_AUTO_HIDE_KEY, true);
    }

    /** 0 = off, 1 = Continue Watching shelf while a dock app is focused, 2 = always if there is content. */
    static int topShelf(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(TOP_SHELF_KEY, 1);
        return Math.max(0, Math.min(2, value));
    }

    /** 0 = still wallpaper, 1 = subtle Aura-style focus parallax, 2 = full. */
    static int parallax(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(PARALLAX_KEY, 1);
        return Math.max(0, Math.min(2, value));
    }

    /** The Edit front row mode makes every tile jiggle, like GlassOS. */
    static boolean editJiggle(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(EDIT_JIGGLE_KEY, true);
    }

    /** 0 = off, 1 = subtle, 2 = full: the glassy glare sweeping over the focused tile. */
    static int focusGloss(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(FOCUS_GLOSS_KEY, 1);
        return Math.max(0, Math.min(2, value));
    }

    /** 0 = standard focus fades, 1 = Lumen-style interruptible focus springs. */
    static int focusMotion(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(FOCUS_MOTION_KEY, 1);
        return Math.max(0, Math.min(1, value));
    }

    /** 0 = off, 1 = subtle, 2 = full: how much the focus glass reacts to spring motion. */
    static int glassMotion(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(GLASS_MOTION_KEY, 1);
        return Math.max(0, Math.min(2, value));
    }

    /** 0 = app-coloured ambient glow, 1 = calm neutral, 2 = off. */
    static int ambientMode(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(AMBIENT_MODE_KEY, 1);
        return Math.max(0, Math.min(2, value));
    }

    /** One-time migration to the compact redesigned bar: all parts on, source badge on the cover. */
    static void ensureMusicBarDefaults(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(NP_DESIGN_V5_KEY, false)) return;
        // One clean reset for the redesigned bar. After this the owner's switches are respected.
        prefs.edit()
                .putInt(NP_VISUAL_MODE_KEY, 1)
                .putInt(NP_BG_STYLE_KEY, 1)
                .putInt(NP_BG_OPACITY_KEY, 75)
                .putInt(NP_ACCENT_KEY, 0)
                .putBoolean(NP_GLOW_KEY, true)
                .putBoolean(NP_WAVEFORM_KEY, true)
                .putBoolean(NP_TIMES_KEY, true)
                .putBoolean(NP_ALBUM_ART_KEY, true)
                .putBoolean(NP_SOURCE_KEY, true)
                .putBoolean(NP_CLOSE_KEY, true)
                .putBoolean(NOW_PLAYING_FULL_KEY, true)
                .putInt(NP_SIZE_KEY, 1)
                .putBoolean(NP_DESIGN_V5_KEY, true)
                .apply();
    }

    /** The waveform progress row in the Now Playing bar. */
    static boolean npWaveform(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(NP_WAVEFORM_KEY, true);
    }

    /** The elapsed and total time labels beside the waveform. */
    static boolean npTimes(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(NP_TIMES_KEY, true);
    }

    /** The album cover on the left of the Now Playing bar. */
    static boolean npAlbumArt(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(NP_ALBUM_ART_KEY, true);
    }

    /** The source app badge under the artist. Off by default: the new bar is cleaner without it. */
    static boolean npSource(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(NP_SOURCE_KEY, true);
    }

    /** The compact launcher bar over AerialViews' DreamService (SYSTEM_ALERT_WINDOW). */
    static boolean npOverlay(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(NP_OVERLAY_KEY, true);
    }

    /** 0 = compact, 1 = balanced, 2 = roomy, 3 = large. */
    static int npBarSize(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(NP_SIZE_KEY, 1);
        return Math.max(0, Math.min(3, value));
    }

    /** Corner radius in dp for both ends of the bar and the cover's left corners, 0..28. */
    static int npCornerRadius(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(NP_CORNER_RADIUS_KEY, 18);
        return Math.max(0, Math.min(28, value));
    }

    static boolean screensaverPlayerMusicOnly(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(SCREENSAVER_PLAYER_MUSIC_ONLY_KEY, true);
    }

    /** 0 = ambient full-screen player, 1 = classic panel full-screen player. */
    static int npFullStyle(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(NP_FULL_STYLE_KEY, 0) == 1 ? 1 : 0;
    }

    /** 0 = the original stacked bar, 1 = the new edge bar. */
    static int npBarVariant(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(NP_BAR_VARIANT_KEY, NP_BAR_EDGE);
        return value == NP_BAR_STACKED ? NP_BAR_STACKED : NP_BAR_EDGE;
    }

    static boolean npPrev(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(NP_PREV_KEY, true);
    }

    static boolean npNext(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(NP_NEXT_KEY, true);
    }

    /** The ambient full-screen Now Playing screen on Home idle; off unless the owner opts in. */
    static boolean ambientNowPlaying(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(AMBIENT_NOW_PLAYING_KEY, false);
    }

    /** Minutes Home may sit idle with music before the ambient player; 0 keeps it off. */
    static int ambientIdleMinutes(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(AMBIENT_IDLE_MINUTES_KEY, 10);
        return Math.max(0, Math.min(60, value));
    }

    /** 0 Off, 1 Waves, 2 Oscilloscope, 3 Radial Aura, 4 Pulse/orb, 5 Minimal. */
    static int npVisualMode(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(NP_VISUAL_MODE_KEY, 1);
        return Math.max(0, Math.min(5, value));
    }

    /** 0 transparent, 1 glass, 2 solid. */
    static int npBackgroundStyle(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(NP_BG_STYLE_KEY, 1);
        return Math.max(0, Math.min(2, value));
    }

    /** Background opacity 0..100. */
    static int npBackgroundOpacity(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(NP_BG_OPACITY_KEY, 75);
        return Math.max(0, Math.min(100, value));
    }

    /** 0 white, 1 green, 2 cyan, 3 amber. */
    static int npAccentIndex(Context ctx) {
        int value = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(NP_ACCENT_KEY, 0);
        return Math.max(0, Math.min(3, value));
    }

    static int npAccentColor(Context ctx) {
        int[] colors = {0xFFFFFFFF, 0xFF22C55E, 0xFF06B6D4, 0xFFF59E0B};
        return colors[npAccentIndex(ctx)];
    }

    static boolean npGlow(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(NP_GLOW_KEY, true);
    }

    /** The always-visible X in the Now Playing bar. Back still closes the bar when it has focus. */
    static boolean npClose(Context ctx) {
        return ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(NP_CLOSE_KEY, true);
    }

    /** "standard" is the plain header; "hitech" wraps the clock in a Liquid Glass card. */
    static String clockStyle(Context ctx) {
        String style = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(CLOCK_STYLE_KEY, "standard");
        return "hitech".equals(style) ? "hitech" : "standard";
    }
    /**
     * Counts the changes made in Launcher settings. Home remembers the count it was built with and rebuilds on
     * resume when it differs, however the settings were left (see MainActivity#onResume).
     */
    static final String SETTINGS_REVISION_KEY = "settings_revision";

    /** True when Launcher settings changed something since Home was built at revision {@code seen}. */
    static boolean settingsChanged(long seen, long current) {
        return seen != current;
    }
    private static final String THEME_REVISION_NAME = "theme_revision";
    private static final String THEME_SIGNATURE_KEY = "theme_signature";

    final List<String> dockApps;
    /** Grid apps fill the dock slots dock_apps leaves free (config.xml, fill_dock); on by default, false leaves the slots empty. */
    final boolean fillDock;
    final List<String> hiddenApps;
    /** Packages pre-seeded into the App library's "added" set (config.xml, library_apps); empty by default. */
    final List<String> libraryApps;
    /** Packages the App library catalogue never offers, even with app_library on (config.xml, library_excluded_apps). */
    final List<String> libraryExcludedApps;
    final String liveTvPackage;
    final String liveTvAction;
    final String liveTvInputId;
    /** Package to the action its tile sends instead of opening the start screen (config.xml, launch_actions). */
    final Map<String, String> launchActions;
    /** Package to the name its tile shows instead of the app's label (config.xml, app_labels). */
    final Map<String, String> appLabels;
    /** Package to a bundled banner_* drawable its tile shows instead of the app's art (config.xml, app_banners). */
    final Map<String, String> appBanners;
    final String sourcePanelService;
    final String sourcePanelAction;
    /** One int extra for the source panel, "name=value" (config.xml source_panel_extra); empty for none. */
    final String sourcePanelExtra;
    /** List Coocaa OS sources from com.skyworth.tv in the Inputs menu (config.xml coocaa_sources). */
    final boolean coocaaSources;
    final String inputAction;
    /** Vendor source numbers for passthrough inputs (config.xml or theme input_sources). */
    final List<String> inputSources;
    /** Scheme an Inputs row's passthrough URI is sent with instead of content:// (config.xml or theme input_uri_scheme). */
    final String inputUriScheme;
    final int columns;
    final boolean animations;
    final boolean advancedEffects;
    final int rowsBelowDock;
    final boolean shamsiDate;
    /** Lunisolar date under the date (config.json lunar_date), computed for lunarUtcOffset hours east of UTC (-1: by locale). */
    final boolean lunarDate;
    final int lunarUtcOffset;
    /** The date on its own line under the clock instead of beside it (config.xml, date_below_clock). */
    final boolean dateBelowClock;
    /** The VPN state as its own pill after the network pill, instead of a badge inside it (config.xml, vpn_pill). */
    final boolean vpnPill;
    /** Language tag the launcher shows itself in instead of the TV's, or "" for the TV's (config.xml, locale). */
    final String locale;
    /** Name after the greeting ("Good morning, Mr. Trịnh"); empty for the greeting alone. */
    final String greetingName;
    final boolean moduleCards;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    final boolean reducedMotion;
    /** Open the Live TV app on the first Home after the TV starts (config.xml, boot_to_live_tv). */
    final boolean bootToLiveTv;
    /** Show the Open on start row in Launcher settings (config.xml, open_on_start_choice). */
    final boolean openOnStartChoice;
    /** Seconds the boot's app waits for a validated network; 0 opens it at once (open_on_start_network_wait). */
    final int openOnStartNetworkWait;
    /** The Open on start app also opens when the screen comes back on (config.xml, open_on_start_on_wake). */
    final boolean openOnStartOnWake;
    /** The focused dock app's name in large type above the dock (config.xml, dock_title). */
    final boolean dockTitle;
    /** The dock as its own row: band, no names, parked low (config.xml dock_row). false: one plain grid. */
    final boolean dockRow;
    /** Inputs, Wi-Fi and Settings folded into one gear button with a dropdown, like Google TV (config.xml top_menu). */
    final boolean topMenu;
    /** Direct launcher-settings header action; off by default (launcher_settings_button). */
    final boolean launcherSettingsButton;
    /** Names under every tile, dimmed until focused (config.xml always_show_names); the dock band grows for them. */
    final boolean alwaysShowNames;
    /** Reorder button in the top bar (config.xml reorder_button); the Settings long-press menu offers it either way. */
    final boolean reorderButton;
    /** Show the Wi-Fi/Ethernet pill in the top bar (config.xml, show_network_pill). */
    final boolean showNetworkPill;
    /** Show the Inputs pill when the TV has inputs to list (config.xml, show_inputs_pill). */
    final boolean showInputsPill;
    /** Wi-Fi and Settings pills as round icon buttons, Inputs keeps its label (config.xml icon_only_buttons). */
    final boolean iconOnlyButtons;
    /** The Inputs pill as a round icon button too (config.xml icon_only_inputs). */
    final boolean iconOnlyInputs;
    /** With vpn_pill, the VPN pill as a round lock button (config.xml / theme icon_only_vpn). */
    final boolean iconOnlyVpn;
    /** Back on the Home screen opens the clock screensaver instead of doing nothing (config.xml / theme back_opens_clock). */
    final boolean backOpensClock;
    /** The ambient glow takes a new app's colour in one frame instead of dipping and fading (config.xml / theme glow_snap). */
    final boolean glowSnap;
    /** App library as a round button in the top bar (config.xml library_button); needs app_library. */
    final boolean libraryButton;
    /** The App library pill after the last Home row; false hides it while libraryButton is on (show_library_pill). */
    final boolean showLibraryPill;
    /** With icon_only_buttons, the Wi-Fi pill keeps the network name up to this width in dp (0: icon only). */
    final int wifiLabelWidth;
    /** Decode wallpapers in 32-bit colour instead of RGB_565 (config.xml wallpaper_true_color). */
    final boolean wallpaperTrueColor;
    /** The focused tile and the glass highlight swing past their target before settling (config.xml, focus_overshoot). */
    final boolean focusOvershoot;
    final String vpnApp;
    final boolean showHomeSettingsShortcut;
    /** One line under the clock: CPU, free memory, free storage. Off unless config or a theme turns it on. */
    final boolean showStats;
    /** game_day: the package of an add-on that publishes each team's next game (GameDay); empty = off. */
    final String gameDay;
    /** App library pill after the last Home row (config.xml app_library). */
    final boolean appLibrary;
    /** OK directly toggles Home membership in the library (app_library_selection). */
    final boolean appLibrarySelection;
    /** OK opens every app in the library; Add to Home stays in the hold menu (library_ok_opens). */
    final boolean libraryOkOpens;
    /** Icon on a card tinted from that icon, instead of the app's banner (config.xml uniform_tiles). */
    final boolean uniformTiles;
    /** Background colour behind everything, #RRGGBB; black unless a theme says otherwise. */
    final int backgroundColor;
    /** Time-of-day backdrop, 0 to 100 (config.xml, sky_intensity); 0 keeps the plain background. */
    final int skyIntensity;
    /** Remote keycodes that open Settings through HomeKeyService (config.xml, settings_key_keycodes). */
    final int[] settingsKeycodes;
    /** Extra keycodes that open the input switcher in InputKeyService (config.xml input_key_keycodes). */
    final int[] inputKeycodes;
    /** Remote buttons that open an app, "keycode:package" or "KEYCODE_NAME:package" (config.xml app_keys). */
    final List<String> appKeys;
    /** Whether the key services request key filtering (config.xml or theme key_filter); false keeps key
     *  auto-repeat working on firmware whose AccessibilityInputFilter drops repeats (TCL Android 12). */
    final boolean keyFilter;
    /** With keyFilter on, the key services stop filtering while a game controller is connected (gamepad_passthrough). */
    final boolean gamepadPassthrough;
    /** With keyFilter on, the key services swallow Volume Up/Down and repeat a held one themselves (consume_volume_keys). */
    final boolean consumeVolumeKeys;
    /** Stock launcher whose home screen is sent back here if the firmware revives it (config.xml stock_home_redirect). */
    final String stockHomeRedirect;
    /** A Live TV window the firmware opens by itself at power-on is sent back to Home (power_on_home). */
    final boolean powerOnHome;
    /** Every Home press opens this launcher, a held one too, instead of Android's long-press action (home_long_press_home). */
    final boolean homeLongPressHome;
    /** Home at every power-on, whatever the firmware put in front: an input, Live TV or an app (power_on_home_always). */
    final boolean powerOnHomeAlways;
    /** The Inputs menu lists only inputs reporting connected; the rest fold behind a More inputs row (config.xml / theme inputs_connected_only). */
    final boolean inputsConnectedOnly;
    /** Only CEC device rows in the Inputs menu show a connection state, never a bare HDMI port (config.xml / theme inputs_cec_state_only). */
    final boolean inputsCecStateOnly;
    /** "12", "24" or "system" (theme clock_format, or Launcher settings' Clock row); see ClockFormat. */
    final String clockFormat;
    /** The Settings button opens the quick settings panel instead of the TV's Settings (settings_key_panel). */
    final boolean settingsKeyPanel;
    /** Home puts the remote's accessibility helpers back after a force stop (restore_key_services; ServiceRepair). */
    final boolean restoreKeyServices;
    /** Whether themes may show a wallpaper (config.json, wallpaper). */
    final boolean wallpaperEnabled;
    /** Full-screen picture behind the launcher, or null for the plain colour. */
    final File wallpaper;
    /** Rotating theme images, or the single wallpaper when no collection is configured. */
    final List<File> wallpapers;
    final int wallpaperRotationMinutes;
    final int wallpaperDim;
    /** The dock band's opacity in percent (config.json dock_opacity, 30-100): below 100 the wallpaper shows through. */
    final int dockOpacity;
    /**
     * Playback order for a wallpaper collection: {@code sequential} or {@code shuffle}. Missing or
     * invalid values keep the historical shuffle so existing themes rotate exactly as before.
     */
    final String wallpaperOrder;
    /** Whether the overlay file was present and parsed. */
    final boolean overlayApplied;
    /** Festive skin from a theme (festival, festival_until, greeting, accent_color); {@link Festival#NONE} if none. */
    final Festival festival;
    /** F1 weekends from a theme's race_calendar (see RaceWeekend); empty when the theme has none. */
    final List<RaceWeekend.Race> raceCalendar;
    /** Cinematic top shelf (config.xml, cinematic_home): the focused dock app's artwork behind a larger shelf. */
    final boolean cinematicHome;
    /** The glow in the focused app's colour behind the dock (config.xml, ambient_glow); false keeps the background plain. */
    final boolean ambientGlow;
    /** The focus highlight in the focused app's colour on every tile (config.xml, app_glow), as cinematic_home does for the dock. */
    final boolean appGlow;
    /** The rows fade out under the clock instead of being cut off (config.xml, fade_rows); TopFade. */
    final boolean fadeRows;
    /** Minutes Home may sit untouched before the clock screensaver (config.xml, home_idle_clock_minutes); 0 is off. */
    final int homeIdleClockMinutes;
    /** The dock band as light frosted glass like the pills (config.xml, dock_glass); false keeps the solid band. */
    final boolean dockGlass;
    /** The dock band's padding equals the gap between its tiles (config.xml, dock_even_padding); false keeps the fixed padding. */
    final boolean dockEvenPadding;
    /** The dock band and its edge draw (dock_band); false leaves the tiles floating on the wallpaper. */
    final boolean dockBand;
    /** The dock position dots under the cinematic shelf's name (config.xml, cinematic_dots); false leaves them out. */
    final boolean cinematicDots;
    /** The cinematic shelf keeps the white focus highlight instead of the app's colour (config.xml, cinematic_white_focus). */
    final boolean cinematicWhiteFocus;
    /** The Wi-Fi pill's icon green while the TV is online (config.xml, wifi_status_color); false keeps it white. */
    final boolean wifiStatusColor;
    /** Opacity of the app's artwork behind the cinematic shelf, 0 to 100 (config.xml, cinematic_art); 50 as before. */
    final int cinematicArt;
    /** Strength of the cinematic shelf's dark scrim, 0 to 100 percent (config.xml, cinematic_scrim); 100 as before. */
    final int cinematicScrim;
    /** A rotating collection's groups by time of day, as file names (config.xml, wallpaper_moods); empty rotates through all of it. */
    final Map<String, Set<String>> wallpaperMoods;
    /** Shape of the glow behind the focused app (config.xml, glow_style): {@link #GLOW_RADIAL} or {@link #GLOW_HORIZON}. */
    final String glowStyle;
    /** The focus highlight's look (config.xml, glass_style): {@link #GLASS_DEFAULT}, {@link #GLASS_RIM} or {@link #GLASS_HALO}. */
    final String glassStyle;

    static final String GLOW_RADIAL = "radial", GLOW_HORIZON = "horizon";
    static final String GLASS_DEFAULT = "glass", GLASS_RIM = "rim", GLASS_HALO = "halo";
    /** Show each rotating wallpaper's name from wallpapers/captions.txt (config.xml / theme wallpaper_captions). */
    final boolean wallpaperCaptions;
    /** Answer VIEW_INPUTS with the Inputs menu (handle_view_inputs); see InputsRequestActivity. */
    final boolean handleViewInputs;
    /** Hold OK on a tile opens a menu (Move, Remove from Home, App info) instead of App info (config.xml / theme tile_menu). */
    final boolean tileMenu;
    /** With wifi_status_color, the icon orange while connected without internet (a captive portal) and red while offline (config.xml, wifi_status_warnings). */
    final boolean wifiStatusWarnings;
    /** Apps shown as round logo buttons in the top bar, left of the network pill, and no longer as tiles (config.xml, top_bar_apps). */
    final List<String> topBarApps;
    /** The greeting above the clock (Good evening); false hides it (config.xml, show_greeting). */
    final boolean showGreeting;
    /** The date and the line between it and the clock in the clock's full white instead of dimmed (config.xml, header_white). */
    final boolean headerWhite;
    /** The clock screensaver over Home's wallpaper instead of the black glow (config.xml, clock_wallpaper). */
    final boolean clockWallpaper;
    /** How dark the clock screensaver's wallpaper is, 0 to 90 % (config.xml, clock_wallpaper_dim). */
    final int clockWallpaperDim;
    /** The clock screensaver's coloured glow; false leaves the clock on plain black, e.g. for an OLED (config.xml, clock_glow). */
    final boolean clockGlow;
    /** Every app in the dock's row, which scrolls sideways, and no grid below it (config.xml, single_row); needs dock_row. */
    final boolean singleRow;
    /** With single_row, the band runs behind every app in the row, not only the dock apps (config.xml, dock_band_all). */
    final boolean dockBandAll;

    private Config(List<String> dockApps, boolean fillDock, List<String> hiddenApps, List<String> libraryApps, List<String> libraryExcludedApps, String liveTvPackage,
                   String liveTvAction, String liveTvInputId, Map<String, String> launchActions, Map<String, String> appLabels,
                   Map<String, String> appBanners, String sourcePanelService, String sourcePanelAction,
                   String sourcePanelExtra, boolean coocaaSources, String inputAction, List<String> inputSources,
                   String inputUriScheme, int columns, int rowsBelowDock, boolean animations, boolean advancedEffects,
                   boolean shamsiDate, String greetingName, boolean moduleCards, boolean reducedMotion, boolean bootToLiveTv, boolean openOnStartChoice, boolean dockTitle, boolean dockRow, boolean topMenu, boolean launcherSettingsButton, boolean reorderButton, boolean showNetworkPill, boolean showInputsPill, boolean iconOnlyButtons, boolean iconOnlyInputs, boolean libraryButton, boolean showLibraryPill, int wifiLabelWidth, boolean alwaysShowNames, boolean wallpaperTrueColor, boolean focusOvershoot, String vpnApp,
                   boolean showHomeSettingsShortcut, boolean showStats, String gameDay, boolean appLibrary, boolean appLibrarySelection, boolean libraryOkOpens, boolean uniformTiles, int backgroundColor, int skyIntensity,
                   int[] settingsKeycodes, boolean settingsKeyPanel, boolean restoreKeyServices, boolean wallpaperEnabled, File wallpaper, List<File> wallpapers,
                   int wallpaperRotationMinutes, int wallpaperDim, int dockOpacity, String wallpaperOrder, boolean overlayApplied,
                   Festival festival, int[] inputKeycodes, List<String> appKeys, String stockHomeRedirect,
                   List<RaceWeekend.Race> raceCalendar, boolean cinematicHome, boolean ambientGlow, boolean appGlow, boolean fadeRows, int homeIdleClockMinutes,
                   boolean keyFilter, boolean dockGlass, String glowStyle, boolean wallpaperCaptions,
                   boolean handleViewInputs, boolean tileMenu, boolean dockEvenPadding, boolean dockBand, boolean cinematicDots, boolean cinematicWhiteFocus, boolean wifiStatusColor, int cinematicScrim, int cinematicArt,
                   Map<String, Set<String>> wallpaperMoods, boolean gamepadPassthrough, boolean consumeVolumeKeys, boolean powerOnHome, boolean homeLongPressHome, boolean wifiStatusWarnings, List<String> topBarApps, boolean showGreeting, boolean headerWhite, boolean clockWallpaper, int clockWallpaperDim, boolean clockGlow, String glassStyle, boolean singleRow, boolean dockBandAll, boolean lunarDate, int lunarUtcOffset, boolean dateBelowClock, boolean vpnPill, String locale, boolean powerOnHomeAlways, boolean inputsConnectedOnly, boolean inputsCecStateOnly,
                   int openOnStartNetworkWait, boolean openOnStartOnWake, boolean iconOnlyVpn, String clockFormat, boolean backOpensClock, boolean glowSnap) {
        this.handleViewInputs = handleViewInputs;
        this.tileMenu = tileMenu;
        this.inputKeycodes = inputKeycodes;
        this.appKeys = Collections.unmodifiableList(appKeys);
        this.keyFilter = keyFilter;
        this.stockHomeRedirect = stockHomeRedirect;
        this.dockApps = Collections.unmodifiableList(dockApps);
        this.fillDock = fillDock;
        this.hiddenApps = Collections.unmodifiableList(hiddenApps);
        this.libraryApps = Collections.unmodifiableList(libraryApps);
        this.libraryExcludedApps = Collections.unmodifiableList(libraryExcludedApps);
        this.liveTvPackage = liveTvPackage;
        this.liveTvAction = liveTvAction;
        this.liveTvInputId = liveTvInputId;
        this.launchActions = Collections.unmodifiableMap(launchActions);
        this.appLabels = Collections.unmodifiableMap(appLabels);
        this.appBanners = Collections.unmodifiableMap(appBanners);
        this.sourcePanelService = sourcePanelService;
        this.sourcePanelAction = sourcePanelAction;
        this.sourcePanelExtra = sourcePanelExtra;
        this.coocaaSources = coocaaSources;
        this.inputAction = inputAction;
        this.inputSources = Collections.unmodifiableList(inputSources);
        this.inputUriScheme = inputUriScheme;
        this.columns = columns;
        this.rowsBelowDock = rowsBelowDock;
        this.animations = animations;
        this.advancedEffects = advancedEffects;
        this.shamsiDate = shamsiDate;
        this.lunarDate = lunarDate;
        this.lunarUtcOffset = lunarUtcOffset;
        this.dateBelowClock = dateBelowClock;
        this.vpnPill = vpnPill;
        this.locale = locale;
        this.greetingName = greetingName;
        this.moduleCards = moduleCards;
        this.reducedMotion = reducedMotion;
        this.bootToLiveTv = bootToLiveTv;
        this.openOnStartChoice = openOnStartChoice;
        this.dockTitle = dockTitle;
        this.dockRow = dockRow;
        this.topMenu = topMenu;
        this.launcherSettingsButton = launcherSettingsButton;
        this.reorderButton = reorderButton;
        this.showNetworkPill = showNetworkPill;
        this.showInputsPill = showInputsPill;
        this.iconOnlyButtons = iconOnlyButtons;
        this.iconOnlyInputs = iconOnlyInputs;
        this.iconOnlyVpn = iconOnlyVpn;
        this.backOpensClock = backOpensClock;
        this.glowSnap = glowSnap;
        this.libraryButton = libraryButton;
        this.showLibraryPill = showLibraryPill;
        this.wifiLabelWidth = wifiLabelWidth;
        this.alwaysShowNames = alwaysShowNames;
        this.wallpaperTrueColor = wallpaperTrueColor;
        this.focusOvershoot = focusOvershoot;
        this.vpnApp = vpnApp;
        this.showHomeSettingsShortcut = showHomeSettingsShortcut;
        this.showStats = showStats;
        this.gameDay = gameDay;
        this.appLibrary = appLibrary;
        this.appLibrarySelection = appLibrarySelection;
        this.libraryOkOpens = libraryOkOpens;
        this.uniformTiles = uniformTiles;
        this.backgroundColor = backgroundColor;
        this.skyIntensity = skyIntensity;
        this.settingsKeycodes = settingsKeycodes;
        this.settingsKeyPanel = settingsKeyPanel;
        this.restoreKeyServices = restoreKeyServices;
        this.wallpaperEnabled = wallpaperEnabled;
        this.wallpaper = wallpaper;
        this.wallpapers = Collections.unmodifiableList(wallpapers);
        this.wallpaperRotationMinutes = wallpaperRotationMinutes;
        this.wallpaperDim = wallpaperDim;
        this.dockOpacity = dockOpacity;
        this.wallpaperOrder = wallpaperOrder;
        this.overlayApplied = overlayApplied;
        this.festival = festival;
        this.raceCalendar = Collections.unmodifiableList(raceCalendar);
        this.cinematicHome = cinematicHome;
        this.ambientGlow = ambientGlow;
        this.appGlow = appGlow;
        this.fadeRows = fadeRows;
        this.homeIdleClockMinutes = homeIdleClockMinutes;
        this.dockGlass = dockGlass;
        this.dockEvenPadding = dockEvenPadding;
        this.dockBand = dockBand;
        this.cinematicDots = cinematicDots;
        this.cinematicWhiteFocus = cinematicWhiteFocus;
        this.wifiStatusColor = wifiStatusColor;
        this.cinematicArt = cinematicArt;
        this.cinematicScrim = cinematicScrim;
        this.wallpaperMoods = wallpaperMoods;
        this.gamepadPassthrough = gamepadPassthrough;
        this.consumeVolumeKeys = consumeVolumeKeys;
        this.powerOnHome = powerOnHome;
        this.homeLongPressHome = homeLongPressHome;
        this.powerOnHomeAlways = powerOnHomeAlways;
        this.inputsConnectedOnly = inputsConnectedOnly;
        this.inputsCecStateOnly = inputsCecStateOnly;
        this.openOnStartNetworkWait = openOnStartNetworkWait;
        this.openOnStartOnWake = openOnStartOnWake;
        this.clockFormat = clockFormat;
        this.wifiStatusWarnings = wifiStatusWarnings;
        this.topBarApps = Collections.unmodifiableList(topBarApps);
        this.showGreeting = showGreeting;
        this.headerWhite = headerWhite;
        this.clockWallpaper = clockWallpaper;
        this.clockWallpaperDim = clockWallpaperDim;
        this.clockGlow = clockGlow;
        this.singleRow = singleRow;
        this.dockBandAll = dockBandAll;
        this.glowStyle = glowStyle(glowStyle);
        this.wallpaperCaptions = wallpaperCaptions;
        this.glassStyle = glassStyle(glassStyle);
    }

    /** A known glow_style, or radial (the default) for anything else. */
    static String glowStyle(String value) {
        return value != null && GLOW_HORIZON.equalsIgnoreCase(value.trim()) ? GLOW_HORIZON : GLOW_RADIAL;
    }

    /** A known glass_style, or glass (the default) for anything else. */
    static String glassStyle(String value) {
        if (value == null) return GLASS_DEFAULT;
        String v = value.trim();
        if (GLASS_RIM.equalsIgnoreCase(v)) return GLASS_RIM;
        return GLASS_HALO.equalsIgnoreCase(v) ? GLASS_HALO : GLASS_DEFAULT;
    }

    /** The folder the theme tools push into; null when external storage is unavailable. */
    static File dir(Context ctx) {
        return ctx.getExternalFilesDir(null);
    }

    static Config load(Context ctx) {
        Resources res = ctx.getResources();
        List<String> dock = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.dock_apps)));
        boolean fillDock = res.getBoolean(R.bool.fill_dock);
        List<String> hidden = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.hidden_apps)));
        List<String> libraryApps = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.library_apps)));
        List<String> libraryExcludedApps = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.library_excluded_apps)));
        String liveTv = res.getString(R.string.live_tv_package).trim();
        String liveTvAction = res.getString(R.string.live_tv_action).trim();
        String liveTvInputId = res.getString(R.string.live_tv_input_id).trim();
        List<String> launchActions = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.launch_actions)));
        List<String> appLabels = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.app_labels)));
        List<String> appBanners = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.app_banners)));
        String panelService = res.getString(R.string.source_panel_service).trim();
        String panelAction = res.getString(R.string.source_panel_action).trim();
        String panelExtra = res.getString(R.string.source_panel_extra).trim();
        boolean coocaaSources = res.getBoolean(R.bool.coocaa_sources);
        String inputAction = res.getString(R.string.input_action).trim();
        List<String> inputSources = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.input_sources)));
        String inputUriScheme = res.getString(R.string.input_uri_scheme).trim();
        int columns = res.getInteger(R.integer.columns);
        int rowsBelow = res.getInteger(R.integer.rows_below_dock);
        boolean animations = res.getBoolean(R.bool.animations);
        boolean advancedEffects = res.getBoolean(R.bool.advanced_effects);
        boolean shamsi = res.getBoolean(R.bool.shamsi_date);
        boolean lunar = res.getBoolean(R.bool.lunar_date);
        int lunarOffset = -1;
        String greetingName = res.getString(R.string.greeting_name).trim();
        boolean moduleCards = res.getBoolean(R.bool.module_cards);
        boolean reducedMotion = res.getBoolean(R.bool.reduced_motion);
        boolean bootToLiveTv = res.getBoolean(R.bool.boot_to_live_tv);
        boolean openOnStartChoice = res.getBoolean(R.bool.open_on_start_choice);
        int openOnStartNetworkWait = res.getInteger(R.integer.open_on_start_network_wait);
        boolean openOnStartOnWake = res.getBoolean(R.bool.open_on_start_on_wake);
        boolean dockTitle = res.getBoolean(R.bool.dock_title);
        boolean dockRow = res.getBoolean(R.bool.dock_row);
        boolean topMenu = res.getBoolean(R.bool.top_menu);
        boolean launcherSettingsButton = res.getBoolean(R.bool.launcher_settings_button);
        boolean reorderButton = res.getBoolean(R.bool.reorder_button);
        boolean showNetworkPill = res.getBoolean(R.bool.show_network_pill);
        boolean showInputsPill = res.getBoolean(R.bool.show_inputs_pill);
        boolean iconOnlyButtons = res.getBoolean(R.bool.icon_only_buttons);
        boolean iconOnlyInputs = res.getBoolean(R.bool.icon_only_inputs);
        boolean iconOnlyVpn = res.getBoolean(R.bool.icon_only_vpn);
        boolean backOpensClock = res.getBoolean(R.bool.back_opens_clock);
        boolean glowSnap = res.getBoolean(R.bool.glow_snap);
        boolean libraryButton = res.getBoolean(R.bool.library_button);
        boolean showLibraryPill = res.getBoolean(R.bool.show_library_pill);
        int wifiLabelWidth = res.getInteger(R.integer.wifi_label_width);
        boolean alwaysShowNames = res.getBoolean(R.bool.always_show_names);
        boolean wallpaperTrueColor = res.getBoolean(R.bool.wallpaper_true_color);
        boolean focusOvershoot = res.getBoolean(R.bool.focus_overshoot);
        boolean wallpaperCaptions = res.getBoolean(R.bool.wallpaper_captions);
        boolean tileMenu = res.getBoolean(R.bool.tile_menu);
        String vpn = res.getString(R.string.vpn_app).trim();
        boolean homeSettings = res.getBoolean(R.bool.show_home_settings_shortcut);
        boolean showStats = res.getBoolean(R.bool.show_stats);
        String gameDay = res.getString(R.string.game_day).trim();
        boolean library = res.getBoolean(R.bool.app_library);
        boolean librarySelection = res.getBoolean(R.bool.app_library_selection);
        boolean libraryOkOpens = res.getBoolean(R.bool.library_ok_opens);
        boolean uniformTiles = res.getBoolean(R.bool.uniform_tiles);
        int background = 0xFF000000;
        int skyIntensity = res.getInteger(R.integer.sky_intensity);
        int[] settingsKeycodes = res.getIntArray(R.array.settings_key_keycodes);
        boolean settingsKeyPanel = res.getBoolean(R.bool.settings_key_panel);
        boolean restoreKeyServices = res.getBoolean(R.bool.restore_key_services);
        int[] inputKeycodes = res.getIntArray(R.array.input_key_keycodes);
        boolean handleViewInputs = res.getBoolean(R.bool.handle_view_inputs);
        List<String> appKeys = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.app_keys)));
        String stockHomeRedirect = res.getString(R.string.stock_home_redirect).trim();
        boolean cinematic = res.getBoolean(R.bool.cinematic_home);
        boolean ambientGlow = res.getBoolean(R.bool.ambient_glow);
        boolean appGlow = res.getBoolean(R.bool.app_glow);
        boolean fadeRows = res.getBoolean(R.bool.fade_rows);
        int idleClockMinutes = res.getInteger(R.integer.home_idle_clock_minutes);
        boolean keyFilter = res.getBoolean(R.bool.key_filter);
        boolean gamepadPassthrough = res.getBoolean(R.bool.gamepad_passthrough);
        boolean consumeVolumeKeys = res.getBoolean(R.bool.consume_volume_keys);
        boolean powerOnHome = res.getBoolean(R.bool.power_on_home);
        boolean homeLongPressHome = res.getBoolean(R.bool.home_long_press_home);
        boolean powerOnHomeAlways = res.getBoolean(R.bool.power_on_home_always);
        boolean inputsConnectedOnly = res.getBoolean(R.bool.inputs_connected_only);
        boolean inputsCecStateOnly = res.getBoolean(R.bool.inputs_cec_state_only);
        String clockFormat = res.getString(R.string.clock_format);
        boolean dockGlass = res.getBoolean(R.bool.dock_glass);
        boolean dockEvenPadding = res.getBoolean(R.bool.dock_even_padding);
        boolean dockBand = res.getBoolean(R.bool.dock_band);
        boolean cinematicDots = res.getBoolean(R.bool.cinematic_dots);
        boolean cinematicWhiteFocus = res.getBoolean(R.bool.cinematic_white_focus);
        boolean wifiStatusColor = res.getBoolean(R.bool.wifi_status_color);
        int cinematicArt = res.getInteger(R.integer.cinematic_art);
        int cinematicScrim = res.getInteger(R.integer.cinematic_scrim);
        Map<String, Set<String>> wallpaperMoods = moods(res.getString(R.string.wallpaper_moods));
        String glowStyle = res.getString(R.string.glow_style);
        boolean wifiStatusWarnings = res.getBoolean(R.bool.wifi_status_warnings);
        boolean dateBelowClock = res.getBoolean(R.bool.date_below_clock);
        boolean vpnPill = res.getBoolean(R.bool.vpn_pill);
        String locale = res.getString(R.string.locale).trim();
        List<String> topBarApps = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.top_bar_apps)));
        boolean showGreeting = res.getBoolean(R.bool.show_greeting);
        boolean headerWhite = res.getBoolean(R.bool.header_white);
        boolean clockWallpaper = res.getBoolean(R.bool.clock_wallpaper);
        int clockWallpaperDim = res.getInteger(R.integer.clock_wallpaper_dim);
        boolean clockGlow = res.getBoolean(R.bool.clock_glow);
        String glassStyle = res.getString(R.string.glass_style);
        boolean singleRow = res.getBoolean(R.bool.single_row);
        boolean dockBandAll = res.getBoolean(R.bool.dock_band_all);
        boolean applied = false;

        File dir = dir(ctx);
        File file = dir != null ? new File(dir, FILE_NAME) : null;
        boolean mounted = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
        Overlay overlay = readOverlay(ctx, file, mounted);
        JSONObject json = overlay.json;
        if (json != null) {
            applied = true;
            dock = stringList(json, "dock_apps", dock);
            fillDock = bool(json, "fill_dock", fillDock);
            hidden = stringList(json, "hidden_apps", hidden);
            libraryApps = stringList(json, "library_apps", libraryApps);
            libraryExcludedApps = stringList(json, "library_excluded_apps", libraryExcludedApps);
            liveTv = string(json, "live_tv_package", liveTv);
            liveTvAction = string(json, "live_tv_action", liveTvAction);
            liveTvInputId = string(json, "live_tv_input_id", liveTvInputId);
            launchActions = stringList(json, "launch_actions", launchActions);
            appLabels = stringList(json, "app_labels", appLabels);
            appBanners = stringList(json, "app_banners", appBanners);
            panelService = string(json, "source_panel_service", panelService);
            panelAction = string(json, "source_panel_action", panelAction);
            panelExtra = string(json, "source_panel_extra", panelExtra).trim();
            coocaaSources = bool(json, "coocaa_sources", coocaaSources);
            inputAction = string(json, "input_action", inputAction);
            inputSources = stringList(json, "input_sources", inputSources);
            inputUriScheme = string(json, "input_uri_scheme", inputUriScheme).trim();
            columns = integer(json, "columns", columns, 1, 8);
            rowsBelow = integer(json, "rows_below_dock", rowsBelow, 0, 3);
            animations = bool(json, "animations", animations);
            advancedEffects = bool(json, "advanced_effects", advancedEffects);
            shamsi = bool(json, "shamsi_date", shamsi);
            lunar = bool(json, "lunar_date", lunar);
            lunarOffset = integer(json, "lunar_utc_offset", lunarOffset, -1, 14);
            greetingName = string(json, "greeting_name", greetingName).trim();
            moduleCards = bool(json, "module_cards", moduleCards);
            reducedMotion = bool(json, "reduced_motion", reducedMotion);
            bootToLiveTv = bool(json, "boot_to_live_tv", bootToLiveTv);
            openOnStartChoice = bool(json, "open_on_start_choice", openOnStartChoice);
            openOnStartNetworkWait = integer(json, "open_on_start_network_wait", openOnStartNetworkWait, 0, 600);
            openOnStartOnWake = bool(json, "open_on_start_on_wake", openOnStartOnWake);
            dockTitle = bool(json, "dock_title", dockTitle);
            dockRow = bool(json, "dock_row", dockRow);
            topMenu = bool(json, "top_menu", topMenu);
            launcherSettingsButton = bool(json, "launcher_settings_button", launcherSettingsButton);
            reorderButton = bool(json, "reorder_button", reorderButton);
            showNetworkPill = bool(json, "show_network_pill", showNetworkPill);
            showInputsPill = bool(json, "show_inputs_pill", showInputsPill);
            iconOnlyButtons = bool(json, "icon_only_buttons", iconOnlyButtons);
            iconOnlyInputs = bool(json, "icon_only_inputs", iconOnlyInputs);
            iconOnlyVpn = bool(json, "icon_only_vpn", iconOnlyVpn);
            backOpensClock = bool(json, "back_opens_clock", backOpensClock);
            glowSnap = bool(json, "glow_snap", glowSnap);
            libraryButton = bool(json, "library_button", libraryButton);
            showLibraryPill = bool(json, "show_library_pill", showLibraryPill);
            wifiLabelWidth = integer(json, "wifi_label_width", wifiLabelWidth, 0, 300);
            alwaysShowNames = bool(json, "always_show_names", alwaysShowNames);
            wallpaperTrueColor = bool(json, "wallpaper_true_color", wallpaperTrueColor);
            focusOvershoot = bool(json, "focus_overshoot", focusOvershoot);
            wallpaperCaptions = bool(json, "wallpaper_captions", wallpaperCaptions);
            tileMenu = bool(json, "tile_menu", tileMenu);
            vpn = string(json, "vpn_app", vpn);
            homeSettings = bool(json, "show_home_settings_shortcut", homeSettings);
            showStats = bool(json, "show_stats", showStats);
            gameDay = string(json, "game_day", gameDay);
            library = bool(json, "app_library", library);
            librarySelection = bool(json, "app_library_selection", librarySelection);
            libraryOkOpens = bool(json, "library_ok_opens", libraryOkOpens);
            uniformTiles = bool(json, "uniform_tiles", uniformTiles);
            background = color(json, "background_color", background);
            skyIntensity = integer(json, "sky_intensity", skyIntensity, 0, 100);
            settingsKeycodes = intArray(json, "settings_key_keycodes", settingsKeycodes);
            settingsKeyPanel = bool(json, "settings_key_panel", settingsKeyPanel);
            restoreKeyServices = bool(json, "restore_key_services", restoreKeyServices);
            inputKeycodes = intArray(json, "input_key_keycodes", inputKeycodes);
            handleViewInputs = bool(json, "handle_view_inputs", handleViewInputs);
            appKeys = stringList(json, "app_keys", appKeys);
            stockHomeRedirect = string(json, "stock_home_redirect", stockHomeRedirect);
            cinematic = bool(json, "cinematic_home", cinematic);
            ambientGlow = bool(json, "ambient_glow", ambientGlow);
            appGlow = bool(json, "app_glow", appGlow);
            fadeRows = bool(json, "fade_rows", fadeRows);
            idleClockMinutes = integer(json, "home_idle_clock_minutes", idleClockMinutes, 0, 1440);
            keyFilter = bool(json, "key_filter", keyFilter);
            gamepadPassthrough = bool(json, "gamepad_passthrough", gamepadPassthrough);
            consumeVolumeKeys = bool(json, "consume_volume_keys", consumeVolumeKeys);
            powerOnHome = bool(json, "power_on_home", powerOnHome);
            homeLongPressHome = bool(json, "home_long_press_home", homeLongPressHome);
            powerOnHomeAlways = bool(json, "power_on_home_always", powerOnHomeAlways);
            inputsConnectedOnly = bool(json, "inputs_connected_only", inputsConnectedOnly);
            inputsCecStateOnly = bool(json, "inputs_cec_state_only", inputsCecStateOnly);
            clockFormat = string(json, CLOCK_FORMAT_KEY, clockFormat);
            dockGlass = bool(json, "dock_glass", dockGlass);
            dockEvenPadding = bool(json, "dock_even_padding", dockEvenPadding);
            dockBand = bool(json, "dock_band", dockBand);
            cinematicDots = bool(json, "cinematic_dots", cinematicDots);
            cinematicWhiteFocus = bool(json, "cinematic_white_focus", cinematicWhiteFocus);
            wifiStatusColor = bool(json, "wifi_status_color", wifiStatusColor);
            cinematicArt = integer(json, "cinematic_art", cinematicArt, 0, 100);
            cinematicScrim = integer(json, "cinematic_scrim", cinematicScrim, 0, 100);
            JSONObject moods = json.optJSONObject("wallpaper_moods");
            if (moods != null) wallpaperMoods = moods(moods);
            wifiStatusWarnings = bool(json, "wifi_status_warnings", wifiStatusWarnings);
            dateBelowClock = bool(json, "date_below_clock", dateBelowClock);
            vpnPill = bool(json, "vpn_pill", vpnPill);
            locale = string(json, "locale", locale).trim();
            topBarApps = stringList(json, "top_bar_apps", topBarApps);
            showGreeting = bool(json, "show_greeting", showGreeting);
            headerWhite = bool(json, "header_white", headerWhite);
            clockWallpaper = bool(json, "clock_wallpaper", clockWallpaper);
            clockWallpaperDim = integer(json, "clock_wallpaper_dim", clockWallpaperDim, 0, 90);
            clockGlow = bool(json, "clock_glow", clockGlow);
            singleRow = bool(json, "single_row", singleRow);
            dockBandAll = bool(json, "dock_band_all", dockBandAll);
            glowStyle = string(json, "glow_style", glowStyle);
            glassStyle = string(json, "glass_style", glassStyle);
        }
        // A top-bar app is moved there, not copied: it leaves the grid.
        for (String pkg : topBarApps) if (!hidden.contains(pkg)) hidden.add(pkg);
        // A theme push or reset invalidates choices made on the TV. On first use, remember the
        // current theme without clearing choices from an older launcher version.
        SharedPreferences choices = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE);
        // Unmounted storage looks like a theme reset; compare only once the card is mounted.
        if (mounted && dir != null && syncThemeChoices(choices, dir, overlay.present, overlay.valid)) {
            // A theme push/reset also drops per-app icon picks, which live in their own prefs file.
            IconChoices.clearAll(ctx);
        }
        int chosenColumns = choices.getInt("columns", columns);
        if (chosenColumns >= 4 && chosenColumns <= 7) columns = chosenColumns;
        int chosenRows = choices.getInt(ROWS_BELOW_DOCK_KEY, rowsBelow);
        if (chosenRows >= 0 && chosenRows <= 3) rowsBelow = chosenRows;
        animations = choices.getBoolean("animations", animations);
        advancedEffects = choices.getBoolean("advanced_effects", advancedEffects);
        File wallpaper = dir != null ? new File(dir, WALLPAPER_NAME) : null;
        if (wallpaper != null && !(wallpaper.isFile() && wallpaper.length() > 0)) wallpaper = null;
        // A theme can also switch the wallpaper off while the file is still there.
        boolean wallpaperEnabled = json == null || bool(json, "wallpaper", true);
        if (!wallpaperEnabled) wallpaper = null;
        int rotationMinutes = json == null ? 0 : integer(json, "wallpaper_rotation_minutes", 0, 0, 1440);
        int wallpaperDim = json == null ? 0 : integer(json, "wallpaper_dim", 0, 0, 90);
        int dockOpacity = json == null ? 100 : integer(json, "dock_opacity", 100, 30, 100);
        dockOpacity = chosenDockOpacity(choices.getInt(DOCK_OPACITY_KEY, -1), dockOpacity);
        clockFormat = choices.getString(CLOCK_FORMAT_KEY, clockFormat);
        String wallpaperOrder = json == null ? WallpaperRotation.ORDER_SHUFFLE
                : string(json, "wallpaper_order", WallpaperRotation.ORDER_SHUFFLE);
        // An unknown order must not silently change how an existing collection rotates.
        if (!WallpaperRotation.validOrder(wallpaperOrder)) wallpaperOrder = WallpaperRotation.ORDER_SHUFFLE;
        List<File> wallpapers = new ArrayList<>();
        if (rotationMinutes > 0 && dir != null && wallpaperEnabled) {
            wallpapers.addAll(WallpaperRotation.images(new File(dir, "wallpapers")));
        }
        if (wallpapers.isEmpty() && wallpaper != null) wallpapers.add(wallpaper);
        // An on-TV wallpaper pick overrides the theme: a filename from the wallpapers/
        // collection, or "none". Unset means the theme decides (off by default).
        String wallpaperChoice = choices.getString(WALLPAPER_CHOICE_KEY, null);
        List<File> chosen = WallpaperRotation.chosen(dir == null ? Collections.emptyList()
                : WallpaperRotation.images(new File(dir, "wallpapers")), wallpaper, wallpaperChoice);
        if (chosen != null) {
            wallpapers.clear();
            wallpapers.addAll(chosen);
            wallpaper = chosen.isEmpty() ? null : chosen.get(0);
            wallpaperEnabled = !chosen.isEmpty();
        }
        // A festive skin comes only from a theme; config.xml has no festival. seasonal_skin names one the same way
        // but launchers before it ignore the key, so a theme can carry it without older ones showing the diya skin.
        Festival festival = json == null ? Festival.NONE : Festival.parse(festivalName(json),
                string(json, "festival_until", ""), string(json, "greeting", ""), string(json, "accent_color", ""));

        return new Config(dock, fillDock, hidden, libraryApps, libraryExcludedApps, liveTv, liveTvAction, liveTvInputId, packageMap(launchActions), packageMap(appLabels),
                packageMap(appBanners), panelService, panelAction, panelExtra, coocaaSources, inputAction, inputSources, inputUriScheme, Math.max(1, columns),
                Math.max(0, rowsBelow), animations, advancedEffects, shamsi, greetingName, moduleCards, reducedMotion, bootToLiveTv,
                openOnStartChoice, dockTitle, dockRow, topMenu, launcherSettingsButton, reorderButton, showNetworkPill, showInputsPill,
                iconOnlyButtons, iconOnlyInputs, libraryButton, showLibraryPill, wifiLabelWidth, alwaysShowNames, wallpaperTrueColor, focusOvershoot,
                vpn, homeSettings, showStats, gameDay, library, librarySelection, libraryOkOpens, uniformTiles, background,
                Math.max(0, Math.min(100, skyIntensity)), settingsKeycodes, settingsKeyPanel, restoreKeyServices, wallpaperEnabled, wallpaper,
                wallpapers, rotationMinutes, wallpaperDim, dockOpacity, wallpaperOrder, applied, festival, inputKeycodes, appKeys, stockHomeRedirect,
                json == null ? Collections.emptyList() : raceCalendar(json), cinematic, ambientGlow, appGlow, fadeRows,
                Math.max(0, idleClockMinutes), keyFilter, dockGlass, glowStyle, wallpaperCaptions, handleViewInputs, tileMenu, dockEvenPadding,
                dockBand, cinematicDots, cinematicWhiteFocus, wifiStatusColor, cinematicScrim, cinematicArt, wallpaperMoods, gamepadPassthrough,
                consumeVolumeKeys, powerOnHome, homeLongPressHome, wifiStatusWarnings, topBarApps, showGreeting, headerWhite, clockWallpaper,
                Math.max(0, Math.min(90, clockWallpaperDim)), clockGlow, glassStyle, singleRow && dockRow, dockBandAll, lunar, lunarOffset,
                dateBelowClock, vpnPill, locale, powerOnHomeAlways, inputsConnectedOnly, inputsCecStateOnly,
                Math.max(0, Math.min(600, openOnStartNetworkWait)), openOnStartOnWake, iconOnlyVpn, clockFormat, backOpensClock, glowSnap);
    }

    /**
     * {@code "race_calendar": [{"round": 18, "name": "United States Grand Prix", "place": "Austin",
     * "sessions": [{"name": "FP1", "start": "2026-10-23T17:30:00Z"}, ...]}, ...]}. A race or session that does
     * not parse is left out on its own; the rest of the calendar still counts.
     */
    private static List<RaceWeekend.Race> raceCalendar(JSONObject json) {
        List<RaceWeekend.Race> races = new ArrayList<>();
        JSONArray list = json.optJSONArray("race_calendar");
        if (list == null) return races;
        for (int i = 0; i < list.length(); i++) {
            JSONObject r = list.optJSONObject(i);
            JSONArray sessions = r == null ? null : r.optJSONArray("sessions");
            if (sessions == null) continue;
            List<RaceWeekend.Session> parsed = new ArrayList<>();
            for (int j = 0; j < sessions.length(); j++) {
                JSONObject s = sessions.optJSONObject(j);
                if (s == null) continue;
                String name = s.optString("name", "").trim();
                try {
                    if (!name.isEmpty()) parsed.add(new RaceWeekend.Session(name, java.time.Instant.parse(s.optString("start", ""))));
                } catch (java.time.format.DateTimeParseException ignored) {
                    // a session without a readable start time cannot be counted down
                }
            }
            if (!parsed.isEmpty()) races.add(new RaceWeekend.Race(r.optInt("round", 0),
                    r.optString("name", "Grand Prix").trim(), r.optString("place", "").trim(), parsed));
        }
        return races;
    }

    private static boolean syncThemeChoices(SharedPreferences choices, File dir,
                                            boolean present, boolean valid) {
        // A present but unreadable file is a failed push or a failing card, not a reset.
        if (present && !valid) return false;
        String token = revisionToken(dir == null ? null : new File(dir, THEME_REVISION_NAME));
        if (TOKEN_UNREADABLE.equals(token)) return false;
        String signature = (present ? "present:" : "absent:") + token;
        String seen = choices.getString(THEME_SIGNATURE_KEY, null);
        SharedPreferences.Editor editor = choices.edit();
        if (seen == null) {
            // Existing preferences survive an app upgrade, unless the first launch follows an
            // explicit theme push or reset by the updated tools.
            boolean reset = !TOKEN_ABSENT.equals(token);
            if (reset) clearChoiceKeys(editor);
            editor.putString(THEME_SIGNATURE_KEY, signature).commit();
            return reset;
        }
        // Only an explicit revision token change or a confirmed delete resets the owner's
        // choices; a re-pushed config.json with the same token keeps them.
        boolean reset = (!present && seenPresent(seen)) || !token.equals(seenToken(seen));
        if (reset) clearChoiceKeys(editor);
        if (!signature.equals(seen)) editor.putString(THEME_SIGNATURE_KEY, signature);
        if (reset || !signature.equals(seen)) editor.commit();
        return reset;
    }

    private static String seenToken(String signature) {
        int colon = signature.lastIndexOf(':');
        return colon >= 0 ? signature.substring(colon + 1) : signature;
    }

    /** Old signatures were "length@time:token", with a literal "absent" stamp when the file was missing. */
    private static boolean seenPresent(String signature) {
        return !signature.startsWith("absent:");
    }

    /** The dock band opacity chosen in Launcher settings, or the theme's when none (or an out-of-range one) is saved. */
    static int chosenDockOpacity(int chosen, int theme) {
        return chosen >= 30 && chosen <= 100 ? chosen : theme;
    }

    private static final String TOKEN_ABSENT = "absent";
    private static final String TOKEN_UNREADABLE = "unreadable";

    private static String revisionToken(File file) {
        if (file == null || !file.exists()) return TOKEN_ABSENT;
        if (!file.isFile() || file.length() > 64) return TOKEN_UNREADABLE;
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int n = in.read(bytes);
            return n > 0 ? new String(bytes, 0, n, StandardCharsets.US_ASCII) : TOKEN_UNREADABLE;
        } catch (Exception ignored) {
            // A failed read is not evidence of a new theme.
            return TOKEN_UNREADABLE;
        }
    }

    /**
     * The overlay, or before external storage is mounted, the copy kept from the last time it was read. Home can
     * start before the card is mounted (seen at every boot on a Xiaomi MiTV-MTEQ0), and without the copy it comes
     * up with the built-in dock order. A missing file on mounted storage still means no overlay; a present but
     * unreadable one keeps the last good copy and leaves the theme choices alone.
     */
    private static Overlay readOverlay(Context ctx, File file, boolean mounted) {
        File copy = new File(ctx.getFilesDir(), FILE_NAME);
        if (!mounted) {
            Overlay cached = read(copy);
            return new Overlay(false, false, cached.json);
        }
        Overlay overlay = read(file);
        if (!overlay.present) {
            copy.delete();
            new File(copy.getPath() + ".tmp").delete();
            return overlay;
        }
        if (!overlay.valid) {
            // Half-written push, bad card or locked file: keep the last good theme and its signature.
            return new Overlay(true, false, read(copy).json);
        }
        writeCopy(copy, overlay.json);
        return overlay;
    }

    /** The copy is replaced atomically too: a kill mid-write must not lose the last good theme. */
    private static void writeCopy(File copy, JSONObject json) {
        File temp = new File(copy.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(json.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (Exception e) {
            temp.delete();
            Log.w(TAG, "could not keep a copy of " + copy + ": " + e.getMessage());
            return;
        }
        if (!temp.renameTo(copy)) {
            temp.delete();
            Log.w(TAG, "could not replace " + copy);
        }
    }

    /** The overlay as found: present says the external file exists, valid that it parsed as an object. */
    private static final class Overlay {
        final boolean present;
        final boolean valid;
        final JSONObject json;
        Overlay(boolean present, boolean valid, JSONObject json) {
            this.present = present;
            this.valid = valid;
            this.json = json;
        }
    }

    private static Overlay read(File file) {
        if (file == null || !file.exists()) return new Overlay(false, false, null);
        if (!file.isFile()) {
            warnOnce(file, "not a file");
            return new Overlay(true, false, null);
        }
        if (file.length() > 64 * 1024) {
            warnOnce(file, "larger than 64 KB");
            return new Overlay(true, false, null);
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);   // Windows editors add a BOM
            return new Overlay(true, true, new JSONObject(text));
        } catch (Exception e) {
            warnOnce(file, e.getMessage());
            return new Overlay(true, false, null);
        }
    }

    private static volatile String warnedOverlay;

    /** One log per version of the file: Config.load runs on every menu, not on every bad parse. */
    private static void warnOnce(File file, String reason) {
        String stamp = file.getPath() + "@" + file.length() + "@" + file.lastModified();
        if (stamp.equals(warnedOverlay)) return;
        warnedOverlay = stamp;
        Log.w(TAG, "ignoring " + file + ": " + reason);
    }

    /** wallpaper_moods as JSON text (config.xml); empty or unreadable is no groups. */
    private static Map<String, Set<String>> moods(String text) {
        if (text.trim().isEmpty()) return Collections.emptyMap();
        try {
            return moods(new JSONObject(text));
        } catch (JSONException e) {
            return Collections.emptyMap();
        }
    }

    /** {"night": ["03-moon-lake.jpg", ...], ...}: each mood's picture names; other keys and names are left out. */
    private static Map<String, Set<String>> moods(JSONObject o) {
        Map<String, Set<String>> groups = new HashMap<>();
        for (String mood : WallpaperMood.ALL) {
            JSONArray names = o.optJSONArray(mood);
            if (names == null) continue;
            Set<String> group = new HashSet<>();
            for (int i = 0; i < names.length(); i++) {
                String name = names.optString(i, "").trim();
                if (ThemeFiles.collectionImage(name)) group.add(name);
            }
            if (!group.isEmpty()) groups.put(mood, Collections.unmodifiableSet(group));
        }
        return Collections.unmodifiableMap(groups);
    }

    private static String string(JSONObject o, String key, String fallback) {
        Object v = o.opt(key);
        return v instanceof String ? ((String) v).trim() : fallback;
    }

    /** The theme's festival: {@code festival}, or {@code seasonal_skin} when that is empty or missing. */
    static String festivalName(JSONObject o) {
        String name = string(o, "festival", "");
        return name.isEmpty() ? string(o, "seasonal_skin", "") : name;
    }

    private static boolean bool(JSONObject o, String key, boolean fallback) {
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static int integer(JSONObject o, String key, int fallback, int min, int max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return fallback;
        int i = ((Number) v).intValue();
        return i < min || i > max ? fallback : i;
    }

    private static int color(JSONObject o, String key, int fallback) {
        Object v = o.opt(key);
        if (!(v instanceof String)) return fallback;
        try {
            int c = Color.parseColor(((String) v).trim());
            return c | 0xFF000000;   // always opaque: the window behind is black anyway
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Positive keycodes only (vendor remotes use codes in the thousands); a list with anything else in it is ignored as a whole. */
    private static int[] intArray(JSONObject o, String key, int[] fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        int[] out = new int[a.length()];
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (!(item instanceof Number)) return fallback;
            out[i] = ((Number) item).intValue();
            if (out[i] < 1) return fallback;
        }
        return out;
    }

    /** "package=value" entries; an entry without both halves is skipped, and a later entry for a package wins. */
    static Map<String, String> packageMap(List<String> entries) {
        Map<String, String> out = new HashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            String pkg = entry.substring(0, eq).trim(), value = entry.substring(eq + 1).trim();
            if (!pkg.isEmpty() && !value.isEmpty()) out.put(pkg, value);
        }
        return out;
    }

    private static List<String> stringList(JSONObject o, String key, List<String> fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (item instanceof String && !((String) item).trim().isEmpty()) out.add(((String) item).trim());
        }
        return out;
    }
}
