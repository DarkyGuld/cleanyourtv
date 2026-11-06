package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Per-app choice between the bundled Glitch banner, the app's own art, a theme icon and the Glass pack. */
final class IconChoices {
    static final int GLITCH = 0;
    static final int ORIGINAL = 1;
    static final int THEME = 2;
    static final int GLASS = 3;
    /** The first frame of the animated Glitch banners: the same artwork, still and complete. */
    static final int GLITCH_STILL = 4;

    static final String GLOBAL_KEY = "global";
    private static final String PREFS = "icon_choices";
    private static final String STYLE = "style:";

    private IconChoices() {}

    static int style(Context context, String pkg) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int value = prefs.getInt(STYLE + pkg, -1);
        if (value < GLITCH || value > GLITCH_STILL) value = prefs.getInt(GLOBAL_KEY, ORIGINAL);
        if (value == GLASS) return ORIGINAL;   // the experimental glass icon pack is retired
        return value >= GLITCH && value <= GLITCH_STILL ? value : ORIGINAL;
    }

    /** The pack chosen for every app; a per-app choice still wins for that one tile. */
    static int globalStyle(Context context) {
        int value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(GLOBAL_KEY, ORIGINAL);
        if (value == GLASS) return ORIGINAL;
        return value >= GLITCH && value <= GLITCH_STILL ? value : ORIGINAL;
    }

    static void setStyle(Context context, String pkg, int style) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(STYLE + pkg, style >= GLITCH && style <= GLITCH_STILL ? style : ORIGINAL)
                .apply();
    }

    static void setGlobalStyle(Context context, int style) {
        int value = style >= GLITCH && style <= GLITCH_STILL ? style : ORIGINAL;
        // A global pack replaces earlier per-app picks, so every app really changes.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .clear().putInt(GLOBAL_KEY, value).apply();
    }

    static void clearAll(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** True when at least one tile has its own icon style, so Follow theme has something to reset. */
    static boolean hasPerAppChoices(Context context) {
        for (String key : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getAll().keySet()) {
            if (key.startsWith(STYLE)) return true;
        }
        return false;
    }

    static void setAllStyle(Context context, Set<String> packages, int style) {
        SharedPreferences.Editor edit =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        int value = style >= GLITCH && style <= GLITCH_STILL ? style : ORIGINAL;
        for (String pkg : packages) edit.putInt(STYLE + pkg, value);
        edit.apply();
    }

    static boolean allStyle(Context context, Set<String> packages, int style) {
        for (String pkg : packages) if (style(context, pkg) != style) return false;
        return true;
    }

    /** How many different styles the packages use; the Settings "Mixed" row needs more than one. */
    static int styleCount(Context context, Set<String> packages) {
        Set<Integer> styles = new HashSet<>();
        for (String pkg : packages) styles.add(style(context, pkg));
        return styles.size();
    }

    /** True when the apps do not all use one style (their per-app picks differ from each other). */
    static boolean mixedStyles(Context context, Set<String> packages) {
        int first = -1;
        for (String pkg : packages) {
            int style = style(context, pkg);
            if (first < 0) first = style;
            else if (style != first) return true;
        }
        return false;
    }
}
