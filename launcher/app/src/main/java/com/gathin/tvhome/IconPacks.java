package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Installed icon packs: read the pack APK's drawable resources and map them onto apps. */
final class IconPacks {
    /** Decoded drawables, bounded so a pack cannot fill a 1 GB TV's heap. */
    private static final Map<String, Drawable> CACHE = new HashMap<>();
    /** appfilter.xml parsed once per pack version, not once per icon. */
    private static final Map<String, Filter> FILTERS = new HashMap<>();
    private static final Map<String, Long> VERSIONS = new HashMap<>();
    /** "No icon here" answers, version-scoped and cleared on update/trim; never permanent. */
    private static final Set<String> MISSES = new HashSet<>();
    private static final int MAX_CACHE = 256;
    private static final int MAX_MISSES = 512;
    private static final Object LOCK = new Object();

    private static List<Pack> installedCache;
    private static boolean watching;

    private static final Pattern ITEM = Pattern.compile("<item\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPONENT = Pattern.compile(
            "component\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern DRAWABLE = Pattern.compile(
            "drawable\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    static final class Pack {
        final String pkg;
        final String label;

        Pack(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    /** Called after discovery scans; lets a caller move the scan off the UI thread. */
    interface PackListener {
        void onInstalled(List<Pack> packs);
    }

    private IconPacks() {
    }

    /**
     * Packs on this TV, detected by the standard appfilter/drawable assets. The result is cached until
     * a package changes; use {@link #installedAsync} from an Activity to keep the first scan off the
     * UI thread.
     */
    static List<Pack> installed(Context context) {
        watch(context);
        synchronized (LOCK) {
            if (installedCache != null) return new ArrayList<>(installedCache);
        }
        List<Pack> packs = discover(context);
        synchronized (LOCK) {
            installedCache = packs;
        }
        return new ArrayList<>(packs);
    }

    /**
     * Scans for packs on a worker thread, then calls the listener on that same worker thread. A caller
     * that touches views must hop back to the main thread itself.
     */
    static void installedAsync(Context context, PackListener listener) {
        Context app = context.getApplicationContext();
        Thread thread = new Thread(() -> listener.onInstalled(installed(app)), "tvhome-icon-packs");
        thread.setDaemon(true);
        thread.start();
    }

    private static List<Pack> discover(Context context) {
        List<Pack> packs = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        // On API 30+ this only sees packages made visible by the manifest's <queries>. Icon packs are
        // usually not launcher apps, so the MAIN/LAUNCHER query alone does not expose all of them; the
        // rest still need a <queries> entry for their theme action or package name.
        List<PackageInfo> candidates;
        try {
            candidates = pm.getInstalledPackages(0);
        } catch (RuntimeException e) {
            return packs;
        }
        for (PackageInfo info : candidates) {
            ApplicationInfo app = info.applicationInfo;
            if (app == null || app.packageName.equals(context.getPackageName())) continue;
            Resources res;
            try {
                res = pm.getResourcesForApplication(app);
                if (!isPack(res)) continue;
            } catch (Exception ignored) {
                continue;
            }
            packs.add(new Pack(app.packageName, String.valueOf(pm.getApplicationLabel(app))));
        }
        Collections.sort(packs, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return packs;
    }

    private static boolean isPack(Resources res) throws Exception {
        for (String name : res.getAssets().list("")) {
            if ("appfilter.xml".equals(name) || "drawable.xml".equals(name)) return true;
        }
        return false;
    }

    /** The pack's icon for an app, or null when this pack has no match. */
    static Drawable icon(Context context, String packPkg, String label, String pkg,
                         String activity) {
        if (packPkg == null || packPkg.isEmpty()) return null;
        watch(context);
        PackageManager pm = context.getPackageManager();
        long version = version(pm, packPkg);
        if (version < 0) return null;
        String key = packPkg + '|' + version + '|' + pkg + '|' + activity + '|' + label;
        synchronized (LOCK) {
            if (CACHE.containsKey(key)) return CACHE.get(key);
            if (MISSES.contains(key)) return null;
        }
        Drawable found = load(context, packPkg, label, pkg, activity, version);
        synchronized (LOCK) {
            if (found != null) {
                if (CACHE.size() >= MAX_CACHE) CACHE.clear();
                CACHE.put(key, found);
            } else {
                if (MISSES.size() >= MAX_MISSES) MISSES.clear();
                MISSES.add(key);
            }
        }
        return found;
    }

    private static Drawable load(Context context, String packPkg, String label, String pkg,
                                 String activity, long version) {
        try {
            PackageManager pm = context.getPackageManager();
            Resources res = pm.getResourcesForApplication(packPkg);
            if (!isPack(res)) return null;
            Filter filter = filter(res, packPkg, version);
            List<String> candidates = new ArrayList<>();
            String mapped = appFilter(filter, pkg, activity);
            if (mapped != null && !mapped.isEmpty()) candidates.add(mapped);
            String fromLabel = normalize(label);
            if (!fromLabel.isEmpty()) candidates.add(fromLabel);
            String[] parts = pkg.split("\\.");
            List<String> useful = new ArrayList<>();
            for (String part : parts) {
                if (!isGeneric(part)) useful.add(part);
            }
            if (!useful.isEmpty()) candidates.add(String.join("_", useful));
            for (int i = parts.length - 2; i >= 0; i--) {
                candidates.add(normalize(parts[i]) + "_" + normalize(parts[i + 1]));
            }
            for (String part : parts) candidates.add(normalize(part));
            for (String part : useful) candidates.add(normalize(part));
            if (label != null) {
                for (String word : label.split("\\s+")) candidates.add(normalize(word));
            }
            for (String candidate : candidates) {
                if (candidate.isEmpty()) continue;
                int id = res.getIdentifier(candidate, "drawable", packPkg);
                if (id != 0) return res.getDrawable(id, null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** The parsed appfilter for this pack version, reading and scanning the XML only once. */
    private static Filter filter(Resources res, String packPkg, long version) {
        synchronized (LOCK) {
            Filter cached = FILTERS.get(packPkg);
            if (cached != null && cached.version == version) return cached;
        }
        Filter parsed = parseAppFilter(res, version);
        synchronized (LOCK) {
            FILTERS.put(packPkg, parsed);
        }
        return parsed;
    }

    /**
     * Standard icon-pack mapping: <item component="ComponentInfo{pkg/activity}" drawable="name"/>.
     * Both attributes are read from each tag, in either order, and a package-only entry still matches
     * any activity of that package.
     */
    private static Filter parseAppFilter(Resources res, long version) {
        Filter filter = new Filter(version);
        try (InputStream in = res.getAssets().open("appfilter.xml")) {
            String xml = read(in);
            Matcher item = ITEM.matcher(xml);
            while (item.find()) {
                String tag = item.group();
                Matcher component = COMPONENT.matcher(tag);
                Matcher drawable = DRAWABLE.matcher(tag);
                if (!component.find() || !drawable.find()) continue;
                String name = drawable.group(1);
                String value = component.group(1).trim();
                String pkg;
                String activity = null;
                if (value.startsWith("ComponentInfo{")) {
                    int open = value.indexOf('{');
                    int close = value.indexOf('}', open + 1);
                    if (close < 0) continue;
                    value = value.substring(open + 1, close).trim();
                }
                int slash = value.indexOf('/');
                if (slash >= 0) {
                    pkg = value.substring(0, slash).trim();
                    activity = value.substring(slash + 1).trim();
                    if (activity.startsWith(".")) activity = pkg + activity;
                    if (activity.isEmpty()) activity = null;
                } else {
                    pkg = value;
                }
                if (pkg.isEmpty()) continue;
                filter.packages.putIfAbsent(pkg, name);
                if (activity != null) filter.components.putIfAbsent(key(pkg, activity), name);
            }
        } catch (Exception ignored) {
        }
        return filter;
    }

    private static String appFilter(Filter filter, String pkg, String activity) {
        if (pkg == null) return null;
        if (activity != null) {
            String mapped = filter.components.get(key(pkg, activity));
            if (mapped != null) return mapped;
        }
        return filter.packages.get(pkg);
    }

    private static String key(String pkg, String activity) {
        return (pkg + '/' + activity).toLowerCase(Locale.ROOT);
    }

    /** The pack's last update time, or -1 when it is not installed or not visible to us. */
    private static long version(PackageManager pm, String packPkg) {
        synchronized (LOCK) {
            Long known = VERSIONS.get(packPkg);
            if (known != null) return known;
        }
        try {
            long version = pm.getPackageInfo(packPkg, 0).lastUpdateTime;
            if (version == 0) version = 1;
            synchronized (LOCK) {
                VERSIONS.put(packPkg, version);
            }
            return version;
        } catch (PackageManager.NameNotFoundException e) {
            // Remember the miss too, so a stale Config.iconPack does not cost a binder call per tile;
            // package broadcasts invalidate this with everything else.
            synchronized (LOCK) {
                VERSIONS.put(packPkg, -1L);
            }
            return -1;
        }
    }

    /**
     * Drops every cached drawable, parsed filter and version. Safe to call from any thread; the
     * receiver below does it when a pack is installed, replaced, changed or removed, and low memory
     * does it through {@link ComponentCallbacks2}.
     */
    static void invalidate() {
        synchronized (LOCK) {
            CACHE.clear();
            FILTERS.clear();
            VERSIONS.clear();
            MISSES.clear();
            installedCache = null;
        }
    }

    private static void watch(Context context) {
        synchronized (LOCK) {
            if (watching) return;
            watching = true;
        }
        try {
            Context app = context.getApplicationContext();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addDataScheme("package");
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ignored, Intent intent) {
                    invalidate();
                }
            };
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                app.registerReceiver(receiver, filter);
            }
            app.registerComponentCallbacks(new ComponentCallbacks2() {
                @Override
                public void onTrimMemory(int level) {
                    if (level >= TRIM_MEMORY_RUNNING_LOW) invalidate();
                }

                @Override
                public void onLowMemory() {
                    invalidate();
                }

                @Override
                public void onConfigurationChanged(Configuration newConfig) {
                }
            });
        } catch (RuntimeException ignored) {
            // Without a receiver the caches still expire on process death; never fail an icon over it.
        }
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        return out.toString("UTF-8");
    }

    private static final class Filter {
        final long version;
        final Map<String, String> components = new HashMap<>();
        final Map<String, String> packages = new HashMap<>();

        Filter(long version) {
            this.version = version;
        }
    }

    private static boolean isGeneric(String part) {
        switch (part.toLowerCase(Locale.ROOT)) {
            case "com":
            case "org":
            case "net":
            case "io":
            case "dev":
            case "android":
            case "google":
            case "app":
            case "apps":
            case "tv":
            case "mobile":
            case "ninja":
            case "no":
                return true;
            default:
                return false;
        }
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
