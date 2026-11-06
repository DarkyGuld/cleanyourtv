package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Debug-only direct-state entry point. It is compiled into src/debug and declared only in the debug
 * manifest, so the release APK has neither the class nor the exported component.
 *
 * Examples:
 *   adb shell am start -n com.gathin.tvhome/.DebugActionsActivity \
 *       -e action settings -e section now_playing
 *   adb shell am start -n com.gathin.tvhome/.DebugActionsActivity -e action visualizer -ei mode 3
 *   adb shell am start -n com.gathin.tvhome/.DebugActionsActivity -e action aerial -ez on true
 *   adb shell am start -n com.gathin.tvhome/.DebugActionsActivity -e action dock-only
 */
public final class DebugActionsActivity extends Activity {
    static final String EXTRA_ACTION = "action";
    static final String EXTRA_SECTION = "section";
    static final String EXTRA_MODE = "mode";
    static final String EXTRA_ON = "on";

    private static final String AERIAL =
            "com.neilturner.aerialviews/.ui.screensaver.DreamActivity";
    private static final String PREVIOUS_KEY = "screensaver_previous";
    private static final String FALLBACK_SCREENSAVER =
            "com.google.android.apps.tv.dreamx/.service.Backdrop";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!BuildConfig.DEBUG) {
            finish();
            return;
        }
        Intent intent = getIntent();
        String action = intent.getStringExtra(EXTRA_ACTION);
        if (action == null) {
            toast("missing -e action");
            finish();
            return;
        }
        switch (action) {
            case "settings": {
                Intent settings = new Intent(this, LauncherSettingsActivity.class);
                String section = intent.getStringExtra(EXTRA_SECTION);
                if (section != null && !section.isEmpty()) {
                    settings.putExtra(LauncherSettingsActivity.EXTRA_DEBUG_SECTION, section);
                }
                startActivity(settings);
                break;
            }
            case "visualizer": {
                int mode = intent.getIntExtra(EXTRA_MODE, 1);
                if (mode < 0 || mode > 5) {
                    toast("mode must be 0..5");
                    break;
                }
                SharedPreferences.Editor edit = prefs().edit()
                        .putInt(Config.NP_VISUAL_MODE_KEY, mode);
                if (mode != 0) edit.putBoolean(Config.NP_WAVEFORM_KEY, true);
                if (mode == 3) edit.putBoolean(Config.NP_ALBUM_ART_KEY, true);
                edit.apply();
                bumpRevision();
                launchHome();
                toast("visualizer mode " + mode);
                break;
            }
            case "aerial": {
                boolean on = intent.getBooleanExtra(EXTRA_ON, true);
                setAerial(on);
                launchHome();
                break;
            }
            case "dock-only": {
                prefs().edit().putInt(Config.ROWS_BELOW_DOCK_KEY, 0).apply();
                bumpRevision();
                launchHome();
                toast("rows below dock = 0");
                break;
            }
            case "home":
                launchHome();
                break;
            default:
                toast("unknown action: " + action);
                break;
        }
        finish();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
    }

    private void bumpRevision() {
        prefs().edit().putLong(Config.SETTINGS_REVISION_KEY,
                prefs().getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void launchHome() {
        Intent home = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
    }

    private void setAerial(boolean on) {
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            toast("WRITE_SECURE_SETTINGS missing (install with RESTORE_KEY_SERVICES=1)");
            return;
        }
        String previous = prefs().getString(PREVIOUS_KEY, FALLBACK_SCREENSAVER);
        if (previous != null && previous.contains("com.neilturner.aerialviews")) {
            previous = FALLBACK_SCREENSAVER;
        }
        try {
            String component = on ? AERIAL : previous;
            Settings.Secure.putString(getContentResolver(), "screensaver_components", component);
            Settings.Secure.putInt(getContentResolver(), "screensaver_enabled", 1);
            toast("screensaver " + (on ? "AerialViews" : previous));
        } catch (RuntimeException denied) {
            toast("screensaver change refused: " + denied.getMessage());
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
