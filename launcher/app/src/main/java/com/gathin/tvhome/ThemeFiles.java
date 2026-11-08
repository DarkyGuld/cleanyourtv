package com.gathin.tvhome;

import java.util.List;
import java.util.regex.Pattern;

/** The files ThemeProvider may write or delete under Config.dir, by the URI's path segments. */
final class ThemeFiles {
    static final String WALLPAPERS = "wallpapers";
    static final String ICONS = "icons";
    /** The names tools/theme.sh accepts for a collection image, and the types WallpaperRotation reads. */
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);

    private ThemeFiles() {}

    /** The path relative to the theme directory, e.g. "config.json", "wallpapers/dusk.jpg" or "icons/netflix.webp". */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (segments.size() == 1) {
            String name = segments.get(0);
            return Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name) || "theme_revision".equals(name)
                    ? name : null;
        }
        if (segments.size() == 2 && (WALLPAPERS.equals(segments.get(0)) || ICONS.equals(segments.get(0)))
                && (collectionImage(segments.get(1)) || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return segments.get(0) + "/" + segments.get(1);
        }
        return null;
    }

    /** The collection a delete applies to ("wallpapers" or "icons"), or null. */
    static String collection(List<String> segments) {
        if (segments == null || segments.size() != 1) return null;
        return WALLPAPERS.equals(segments.get(0)) || ICONS.equals(segments.get(0)) ? segments.get(0) : null;
    }

    /** True for "wallpapers" or "icons" alone: a delete clears that collection's images. */
    static boolean isCollection(List<String> segments) {
        return collection(segments) != null;
    }

    static boolean collectionImage(String name) {
        // WallpaperRotation.images() lists dotfiles too (.hidden.jpg); refusing them here left a picture
        // that Home shows but ThemeProvider and a theme.sh reset could never delete.
        return name != null && IMAGE.matcher(name).matches();
    }
}
