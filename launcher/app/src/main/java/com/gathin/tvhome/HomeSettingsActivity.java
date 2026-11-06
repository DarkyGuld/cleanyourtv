package com.gathin.tvhome;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Lets the user change HOME through the system UI, without shell or privileged permissions. The picker is translucent
 *  on Android 14 (permissioncontroller), so this stays behind it as a black backdrop: over Home, its text sat on the
 *  tiles. */
public class HomeSettingsActivity extends Activity {
    private static final int REQUEST_HOME_ROLE = 1;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    private boolean left;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { left = true; return; }
        // Not every TV has the dedicated Home picker; some only expose default apps, the RoleManager
        // request, or the launcher's App info page. Try each in turn instead of only toasting.
        if (!startOrFalse(new Intent(Settings.ACTION_HOME_SETTINGS))
                && !startOrFalse(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                && !requestHomeRole()
                && !startOrFalse(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())))) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
    }

    /** Starts an activity the system resolves; false when this TV has no handler for it. */
    private boolean startOrFalse(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }

    /** Android 10+: ask the system to make this launcher the Home role holder. */
    private boolean requestHomeRole() {
        if (Build.VERSION.SDK_INT < 29) return false;
        RoleManager roles = getSystemService(RoleManager.class);
        if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_HOME)) return false;
        try {
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE);
            return true;
        } catch (ActivityNotFoundException | SecurityException | IllegalStateException unavailable) {
            return false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        left = true;
    }

    /** Back from the picker: this screen was only the backdrop. */
    @Override
    protected void onResume() {
        super.onResume();
        if (left) finish();
    }
}
