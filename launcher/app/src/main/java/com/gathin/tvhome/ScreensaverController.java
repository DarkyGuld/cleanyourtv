package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the TV's system screensaver. AerialViews is screensaver-only; this class never
 * starts a live wallpaper. All writes are verified by reading the secure settings back.
 */
final class ScreensaverController {
    static final String AERIAL_PACKAGE = "com.neilturner.aerialviews";
    static final String AERIAL_COMPONENT = "com.neilturner.aerialviews/.ui.screensaver.DreamActivity";
    static final String CLOCK_COMPONENT = "com.gathin.tvhome/.ClockDream";
    static final String BACKDROP_COMPONENT = "com.google.android.apps.tv.dreamx/.service.Backdrop";
    static final String AERIAL_NOTIFICATION =
            "com.neilturner.aerialviews/com.neilturner.aerialviews.services.NotificationService";
    private static final String SOMNAMBULATOR = "com.android.systemui/.Somnambulator";

    enum Status { OK, NO_PERMISSION, NOT_INSTALLED, NO_DREAM_SERVICE, WRITE_FAILED }

    private ScreensaverController() { }

    static ComponentName aerialComponent() {
        return ComponentName.unflattenFromString(AERIAL_COMPONENT);
    }

    static ComponentName clockComponent() {
        return ComponentName.unflattenFromString(CLOCK_COMPONENT);
    }

    static ComponentName backdropComponent() {
        return ComponentName.unflattenFromString(BACKDROP_COMPONENT);
    }

    /** Every component in screensaver_components, in order, parsed exactly (not by substring). */
    static List<ComponentName> selectedComponents(Context context) {
        List<ComponentName> out = new ArrayList<>();
        String raw = secure(context, "screensaver_components");
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(":")) {
            ComponentName name = ComponentName.unflattenFromString(part.trim());
            if (name != null) out.add(name);
        }
        return out;
    }

    static boolean isEnabled(Context context) {
        return secureInt(context, "screensaver_enabled", 0) == 1;
    }

    static boolean isInstalled(Context context, String pkg) {
        try {
            context.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException missing) {
            return false;
        }
    }

    static boolean hasDreamService(Context context, ComponentName component) {
        if (component == null) return false;
        Intent intent = new Intent(DreamService.SERVICE_INTERFACE).setPackage(component.getPackageName());
        try {
            for (ResolveInfo info : context.getPackageManager()
                    .queryIntentServices(intent, PackageManager.GET_SERVICES)) {
                ServiceInfo service = info.serviceInfo;
                if (service != null && component.getClassName().equals(service.name)) return service.enabled;
            }
        } catch (RuntimeException ignored) {
            // A package that vanished between the install check and the query has no dream service.
        }
        return false;
    }

    static boolean hasWriteSecure(Context context) {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** True only when AerialViews is the selected component and the screensaver is enabled. */
    static boolean aerialViewsOwnsDream(Context context) {
        if (!isEnabled(context)) return false;
        for (ComponentName name : selectedComponents(context)) {
            if (aerialComponent().equals(name)) return true;
        }
        return false;
    }

    static boolean clockOwnsDream(Context context) {
        if (!isEnabled(context)) return false;
        for (ComponentName name : selectedComponents(context)) {
            if (clockComponent().equals(name)) return true;
        }
        return false;
    }

    static Status select(Context context, ComponentName component) {
        if (component == null) return Status.NOT_INSTALLED;
        if (!isInstalled(context, component.getPackageName())) return Status.NOT_INSTALLED;
        if (!hasDreamService(context, component)) return Status.NO_DREAM_SERVICE;
        if (!hasWriteSecure(context)) return Status.NO_PERMISSION;
        try {
            // Keep the old selection so Restore can return to it; do not touch screensaver_default_component.
            ComponentName old = selectedComponents(context).isEmpty() ? null : selectedComponents(context).get(0);
            if (old != null && !old.equals(component)) {
                prefs(context).edit().putString("screensaver_previous", old.flattenToString()).apply();
            }
            boolean written = Settings.Secure.putString(context.getContentResolver(),
                    "screensaver_components", component.flattenToString())
                    && Settings.Secure.putInt(context.getContentResolver(), "screensaver_enabled", 1)
                    && Settings.Secure.putInt(context.getContentResolver(), "screensaver_activate_on_sleep", 1)
                    && Settings.Secure.putInt(context.getContentResolver(), "screensaver_activate_on_dock", 1);
            if (!written) return Status.WRITE_FAILED;
            // Read everything back: a vendor setting provider can accept a write and ignore it.
            if (!isEnabled(context) || !selectedComponents(context).contains(component)) return Status.WRITE_FAILED;
            if (secureInt(context, "screensaver_activate_on_sleep", 0) != 1
                    || secureInt(context, "screensaver_activate_on_dock", 0) != 1) return Status.WRITE_FAILED;
            return Status.OK;
        } catch (SecurityException | IllegalArgumentException denied) {
            return Status.NO_PERMISSION;
        }
    }

    static Status selectAerialViews(Context context) {
        return select(context, aerialComponent());
    }

    static Status selectClock(Context context) {
        return select(context, clockComponent());
    }

    /** Restore the previous selection, then the firmware default. Never writes screensaver_default_component. */
    static Status restoreDefault(Context context) {
        ComponentName previous = ComponentName.unflattenFromString(
                prefs(context).getString("screensaver_previous", ""));
        // Never "restore" AerialViews: the owner pressed Restore TV default.
        if (previous != null && previous.equals(aerialComponent())) previous = null;
        if (previous != null && isInstalled(context, previous.getPackageName())
                && hasDreamService(context, previous)) {
            Status restored = select(context, previous);
            if (restored == Status.OK) return restored;
        }
        String raw = secure(context, "screensaver_default_component");
        ComponentName fallback = ComponentName.unflattenFromString(raw == null ? "" : raw);
        if (fallback == null) fallback = backdropComponent();
        if (!isInstalled(context, fallback.getPackageName())) return Status.NOT_INSTALLED;
        if (!hasDreamService(context, fallback)) return Status.NO_DREAM_SERVICE;
        if (!hasWriteSecure(context)) return Status.NO_PERMISSION;
        try {
            boolean written = Settings.Secure.putString(context.getContentResolver(),
                    "screensaver_components", fallback.flattenToString())
                    && Settings.Secure.putInt(context.getContentResolver(), "screensaver_enabled", 1);
            return written && selectedComponents(context).contains(fallback) ? Status.OK : Status.WRITE_FAILED;
        } catch (SecurityException | IllegalArgumentException denied) {
            return Status.NO_PERMISSION;
        }
    }

    static boolean startDreamNow(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName.unflattenFromString(SOMNAMBULATOR));
        if (context.getPackageManager().resolveActivity(intent, 0) == null) return false;
        try {
            if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (RuntimeException denied) {
            return false;
        }
    }

    static boolean openDreamSettings(Context context) {
        for (String action : new String[]{Settings.ACTION_DREAM_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS}) {
            Intent intent = new Intent(action);
            if (context.getPackageManager().resolveActivity(intent, 0) == null) continue;
            try {
                if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (RuntimeException ignored) {
                // Try the next settings page.
            }
        }
        return false;
    }

    static boolean openAerialViews(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(AERIAL_PACKAGE);
        if (intent == null) intent = pm.getLaunchIntentForPackage(AERIAL_PACKAGE);
        if (intent == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException denied) {
            return false;
        }
    }

    static String activeMediaSummary(Context context) {
        MediaArt.Entry entry = MediaArt.current(context);
        if (entry == null) return null;
        String title = entry.title == null ? "" : entry.title.trim();
        String artist = entry.artist == null ? "" : entry.artist.trim();
        if (title.isEmpty() && artist.isEmpty()) return null;
        if (title.isEmpty()) return artist;
        return artist.isEmpty() ? title : title + " — " + artist;
    }

    static boolean notificationAccessEnabled(Context context) {
        ComponentName wanted = ComponentName.unflattenFromString(AERIAL_NOTIFICATION);
        for (ComponentName name : notificationListeners(context)) {
            if (name.equals(wanted)) return true;
        }
        return false;
    }

    /** Add or remove only AerialViews; preserve GlassOS, GuardRevival and every other listener. */
    static boolean setNotificationAccess(Context context, boolean enabled) {
        if (!hasWriteSecure(context)) return false;
        ComponentName wanted = ComponentName.unflattenFromString(AERIAL_NOTIFICATION);
        List<String> parts = new ArrayList<>();
        boolean found = false;
        for (String part : listenerRaw(context).split(":")) {
            if (part.isEmpty()) continue;
            ComponentName name = ComponentName.unflattenFromString(part);
            if (wanted.equals(name)) {
                found = true;
                if (!enabled) continue;
            }
            parts.add(part);
        }
        if (enabled && !found) parts.add(wanted.flattenToString());
        String value = TextUtils.join(":", parts);
        try {
            if (!Settings.Secure.putString(context.getContentResolver(),
                    "enabled_notification_listeners", value)) return false;
            return notificationAccessEnabled(context) == enabled;
        } catch (SecurityException | IllegalArgumentException denied) {
            return false;
        }
    }

    /** Minutes until the TV's sleep timeout, or -1 when it is unset/never. */
    static int sleepTimerMinutes(Context context) {
        try {
            int ms = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, -1);
            // 0/negative is "never"; emulators and some firmware use Integer.MAX_VALUE for the same.
            if (ms <= 0 || ms > 24 * 60 * 60 * 1000) return -1;
            return Math.max(1, ms / 60000);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    static String selectedLabel(Context context) {
        List<ComponentName> selected = selectedComponents(context);
        if (selected.isEmpty()) return "";
        ComponentName name = selected.get(0);
        if (aerialComponent().equals(name)) return "AerialViews";
        if (clockComponent().equals(name)) return "Clock";
        ComponentName fallback = ComponentName.unflattenFromString(
                secure(context, "screensaver_default_component"));
        if (name.equals(fallback) || backdropComponent().equals(name)) return "TV default";
        return name.flattenToShortString();
    }

    private static List<ComponentName> notificationListeners(Context context) {
        List<ComponentName> out = new ArrayList<>();
        for (String part : listenerRaw(context).split(":")) {
            ComponentName name = ComponentName.unflattenFromString(part);
            if (name != null) out.add(name);
        }
        return out;
    }

    private static String listenerRaw(Context context) {
        String value = secure(context, "enabled_notification_listeners");
        return value == null ? "" : value;
    }

    private static String secure(Context context, String key) {
        try {
            return Settings.Secure.getString(context.getContentResolver(), key);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int secureInt(Context context, String key, int fallback) {
        try {
            return Settings.Secure.getInt(context.getContentResolver(), key, fallback);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
}
