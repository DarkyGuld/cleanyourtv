package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    static final String EXTRA_SECTION = "com.gathin.tvhome.extra.SECTION";
    static final String SECTION_NOW_PLAYING = "now_playing";
    /** Debug builds only: src/debug's DebugActionsActivity opens any of the five sections directly. */
    static final String EXTRA_DEBUG_SECTION = "com.gathin.tvhome.extra.DEBUG_SECTION";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private static final String[] CLOCK_STYLES = {"standard", "hitech"};
    private TextView wallpaperChoice;
    private TextView clockStyleChoice;
    private TextView liquidGlassChoice;
    private TextView glassTransparencyChoice;
    private static final int[] GLASS_TRANSPARENCIES = {15, 30, 40, 45, 60, 75, 90, 95, 100};
    private TextView iconPackChoice;
    private TextView installedIconPackChoice;
    private TextView iconGlowChoice;
    // 40 is Config.ICON_GLOW_DEFAULT: without it the dialog opened on 0 while the row said 40.
    private static final int[] ICON_GLOWS = {0, 25, 40, 50, 75, 100};
    private TextView glassMotionChoice;
    private TextView parallaxChoice;
    private TextView topShelfChoice;
    private TextView topShelfSourcesChoice;
    private TextView topShelfNowPlayingChoice;
    private TextView topShelfProgressChoice;
    private TextView topShelfAutoHideChoice;
    private TextView livingWallpaperChoice;
    private TextView nowPlayingFullChoice;
    private TextView npWaveformChoice;
    private TextView npSizeChoice;
    private TextView npVisualizerChoice;
    private TextView npTimesChoice;
    private TextView npAlbumArtChoice;
    private TextView npSourceBadgeChoice;
    private TextView npCloseChoice;
    private TextView smartStackChoice;
    private TextView smartStackSecondsChoice;
    private TextView controlCenterChoice;
    private TextView mediaBackgroundChoice;
    private TextView screensaverMediaChoice;
    private TextView aerialViewsChoice;
    private TextView screensaverStatusChoice;
    private TextView screensaverTestChoice;
    private TextView screensaverNotificationChoice;
    private TextView screensaverOverlayChoice;
    private TextView screensaverRestoreChoice;
    private TextView screensaverMediaAccessChoice;
    private TextView screensaverSleepChoice;
    private TextView focusMotionChoice;
    private TextView focusGlossChoice;
    private TextView editJiggleChoice;
    private TextView topBarCapsuleChoice;
    private TextView dockGapChoice;
    private TextView shelfShadowChoice;
    private TextView heroTitleChoice;
    private TextView appNamesChoice;
    private TextView tileBorderChoice;
    private TextView ambientModeChoice;
    private static final int[] DOCK_GAPS = {0, 8, 16, 24, 32};
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private TextView defaultHomeChoice;
    private TextView tvSettingsChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;
    private boolean backdropRequested;
    private int activeSection = -1;
    private ScrollView settingsScroll;
    private TextView sectionTitle;
    private TextView sectionHint;
    private final TextView[] navItems = new TextView[7];
    private final int[] navIds = {R.id.navAppearance, R.id.navBackground, R.id.navMotion,
            R.id.navShelf, R.id.navNowPlaying, R.id.navScreensaver, R.id.navSystem};
    private final int[] navSectionIds = {R.id.sectionAppearance, R.id.sectionBackground, R.id.sectionMotion,
            R.id.sectionShelf, R.id.sectionNowPlaying, R.id.sectionScreensaver, R.id.sectionSystem};
    private final int[] navFirstRowIds = {R.id.columns4, R.id.livingWallpaperChoice, R.id.focusMotionChoice,
            R.id.topShelfChoice, R.id.npSizeChoice, R.id.aerialViewsChoice, R.id.defaultHomeChoice};
    private static final int[] SECTION_TITLES = {R.string.settings_section_appearance,
            R.string.settings_section_background, R.string.settings_section_motion,
            R.string.settings_section_shelf, R.string.settings_section_now_playing,
            R.string.settings_section_screensaver, R.string.settings_section_system};
    private static final int[] SECTION_HINTS = {R.string.settings_hint_appearance,
            R.string.settings_hint_background, R.string.settings_hint_motion,
            R.string.settings_hint_shelf, R.string.settings_hint_now_playing,
            R.string.settings_hint_screensaver, R.string.settings_hint_system};

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SettingsRow.setLightMode(true);
        setContentView(R.layout.activity_launcher_settings);
        applySettingsBackground();
        settingsScroll = findViewById(R.id.settingsScroll);
        sectionTitle = findViewById(R.id.sectionTitle);
        sectionHint = findViewById(R.id.sectionHint);
        setUpSections();
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
            reverseRow(clockChoices);
        }
        clockStyleChoice = findViewById(R.id.clockStyleChoice);
        clockStyleChoice.setOnClickListener(v -> showClockStylePicker());
        liquidGlassChoice = findViewById(R.id.liquidGlassChoice);
        liquidGlassChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.LIQUID_GLASS_KEY, !Config.liquidGlass(this)).apply();
            backdropRequested = false;
            changed();
            refresh();
        });
        glassTransparencyChoice = findViewById(R.id.glassTransparencyChoice);
        glassTransparencyChoice.setOnClickListener(v -> showGlassTransparencyPicker());
        iconPackChoice = findViewById(R.id.iconPackChoice);
        iconPackChoice.setOnClickListener(v -> showIconPackPicker());
        installedIconPackChoice = findViewById(R.id.installedIconPackChoice);
        installedIconPackChoice.setOnClickListener(v -> showInstalledIconPackPicker());
        iconGlowChoice = findViewById(R.id.iconGlowChoice);
        iconGlowChoice.setOnClickListener(v -> showIconGlowPicker());
        glassMotionChoice = findViewById(R.id.glassMotionChoice);
        glassMotionChoice.setOnClickListener(v -> showGlassMotionPicker());
        parallaxChoice = findViewById(R.id.parallaxChoice);
        parallaxChoice.setOnClickListener(v -> showParallaxPicker());
        topShelfChoice = findViewById(R.id.topShelfChoice);
        topShelfChoice.setOnClickListener(v -> showTopShelfPicker());
        topShelfSourcesChoice = findViewById(R.id.topShelfSourcesChoice);
        topShelfSourcesChoice.setOnClickListener(v -> showTopShelfSourcesPicker());
        topShelfNowPlayingChoice = findViewById(R.id.topShelfNowPlayingChoice);
        topShelfNowPlayingChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.TOP_SHELF_NOW_PLAYING_KEY,
                    !Config.topShelfNowPlaying(this)).apply();
            changed(); refresh();
        });
        topShelfProgressChoice = findViewById(R.id.topShelfProgressChoice);
        topShelfProgressChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.TOP_SHELF_PROGRESS_KEY, !Config.topShelfProgress(this)).apply();
            changed(); refresh();
        });
        topShelfAutoHideChoice = findViewById(R.id.topShelfAutoHideChoice);
        topShelfAutoHideChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.TOP_SHELF_AUTO_HIDE_KEY, !Config.topShelfAutoHide(this)).apply();
            changed(); refresh();
        });
        nowPlayingFullChoice = findViewById(R.id.nowPlayingFullChoice);
        nowPlayingFullChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.NOW_PLAYING_FULL_KEY, !Config.nowPlayingFull(this)).apply();
            changed(); refresh();
        });
        npSizeChoice = findViewById(R.id.npSizeChoice);
        npSizeChoice.setOnClickListener(v -> showNpSizePicker());
        npWaveformChoice = findViewById(R.id.npWaveformChoice);
        npWaveformChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.NP_WAVEFORM_KEY, !Config.npWaveform(this)).apply();
            changed(); refresh();
        });
        npVisualizerChoice = findViewById(R.id.npVisualizerChoice);
        npVisualizerChoice.setOnClickListener(v -> showNpVisualPicker());
        npTimesChoice = findViewById(R.id.npTimesChoice);
        npTimesChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.NP_TIMES_KEY, !Config.npTimes(this)).apply();
            changed(); refresh();
        });
        npAlbumArtChoice = findViewById(R.id.npAlbumArtChoice);
        npAlbumArtChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.NP_ALBUM_ART_KEY, !Config.npAlbumArt(this)).apply();
            changed(); refresh();
        });
        npSourceBadgeChoice = findViewById(R.id.npSourceBadgeChoice);
        npSourceBadgeChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.NP_SOURCE_KEY, !Config.npSource(this)).apply();
            changed(); refresh();
        });
        npCloseChoice = findViewById(R.id.npCloseChoice);
        npCloseChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.NP_CLOSE_KEY, !Config.npClose(this)).apply();
            changed(); refresh();
        });
        smartStackChoice = findViewById(R.id.smartStackChoice);
        smartStackChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.SMART_STACK_KEY, !Config.smartStack(this)).apply();
            changed(); refresh();
        });
        smartStackSecondsChoice = findViewById(R.id.smartStackSecondsChoice);
        smartStackSecondsChoice.setOnClickListener(v -> showSmartStackSecondsPicker());
        controlCenterChoice = findViewById(R.id.controlCenterChoice);
        controlCenterChoice.setOnClickListener(v -> {
            // Turning it on without the helper would leave a switch that cannot work. Send the owner
            // to the accessibility list instead of toggling silently.
            if (Config.controlCenter(this) && !HomeKeyService.isRunning()) {
                Toast.makeText(this, R.string.control_center_service_hint, Toast.LENGTH_LONG).show();
                open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            boolean next = !Config.controlCenter(this);
            choices.edit().putBoolean(Config.CONTROL_CENTER_KEY, next).apply();
            changed(); refresh();
        });
        livingWallpaperChoice = findViewById(R.id.livingWallpaperChoice);
        livingWallpaperChoice.setOnClickListener(v -> showLivingWallpaperPicker());
        screensaverMediaChoice = findViewById(R.id.screensaverMediaChoice);
        screensaverMediaChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.SCREENSAVER_MEDIA_KEY, !Config.screensaverMedia(this)).apply();
            changed(); refresh();
        });
        aerialViewsChoice = findViewById(R.id.aerialViewsChoice);
        aerialViewsChoice.setOnClickListener(v -> showScreensaverPicker());
        screensaverStatusChoice = findViewById(R.id.screensaverStatusChoice);
        screensaverTestChoice = findViewById(R.id.screensaverTestChoice);
        screensaverTestChoice.setOnClickListener(v -> {
            Toast.makeText(this, R.string.screensaver_test_started, Toast.LENGTH_SHORT).show();
            if (!ScreensaverController.startDreamNow(this)) {
                Toast.makeText(this, R.string.screensaver_test_failed, Toast.LENGTH_LONG).show();
                ScreensaverController.openDreamSettings(this);
            }
        });
        screensaverNotificationChoice = findViewById(R.id.screensaverNotificationChoice);
        screensaverNotificationChoice.setOnClickListener(v -> toggleNotificationAccess());
        screensaverOverlayChoice = findViewById(R.id.screensaverOverlayChoice);
        screensaverOverlayChoice.setOnClickListener(v ->
                new AlertDialog.Builder(this, R.style.SettingsDialog)
                        .setTitle(R.string.screensaver_overlay)
                        .setMessage(R.string.screensaver_overlay_help)
                        .setPositiveButton(R.string.screensaver_engine_aerial, (dialog, which) -> {
                            if (!ScreensaverController.openAerialViews(this)) {
                                Toast.makeText(this, R.string.aerial_views_missing, Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());
        screensaverRestoreChoice = findViewById(R.id.screensaverRestoreChoice);
        screensaverRestoreChoice.setOnClickListener(v -> {
            ScreensaverController.Status status = ScreensaverController.restoreDefault(this);
            showScreensaverStatus(status, getString(R.string.screensaver_engine_tv));
            refresh();
        });
        screensaverMediaAccessChoice = findViewById(R.id.screensaverMediaAccessChoice);
        screensaverMediaAccessChoice.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + ScreensaverController.AERIAL_PACKAGE));
            open(intent);
        });
        screensaverSleepChoice = findViewById(R.id.screensaverSleepChoice);
        mediaBackgroundChoice = findViewById(R.id.mediaBackgroundChoice);
        mediaBackgroundChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.MEDIA_BACKGROUND_KEY, !Config.mediaBackground(this)).apply();
            changed(); refresh();
        });
        focusMotionChoice = findViewById(R.id.focusMotionChoice);
        focusMotionChoice.setOnClickListener(v -> showFocusMotionPicker());
        focusGlossChoice = findViewById(R.id.focusGlossChoice);
        focusGlossChoice.setOnClickListener(v -> showFocusGlossPicker());
        editJiggleChoice = findViewById(R.id.editJiggleChoice);
        editJiggleChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.EDIT_JIGGLE_KEY, !Config.editJiggle(this)).apply();
            changed(); refresh();
        });
        topBarCapsuleChoice = findViewById(R.id.topBarCapsuleChoice);
        topBarCapsuleChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.TOP_BAR_CAPSULE_KEY, !Config.topBarCapsule(this)).apply();
            changed(); refresh();
        });
        dockGapChoice = findViewById(R.id.dockGapChoice);
        dockGapChoice.setOnClickListener(v -> showDockGapPicker());
        shelfShadowChoice = findViewById(R.id.shelfShadowChoice);
        shelfShadowChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.SHELF_SHADOW_KEY, !Config.shelfShadow(this)).apply();
            changed(); refresh();
        });
        heroTitleChoice = findViewById(R.id.heroTitleChoice);
        heroTitleChoice.setOnClickListener(v -> showHeroTitlePicker());
        appNamesChoice = findViewById(R.id.appNamesChoice);
        appNamesChoice.setOnClickListener(v -> showAppNamesPicker());
        tileBorderChoice = findViewById(R.id.tileBorderChoice);
        tileBorderChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Config.TILE_BORDER_KEY, !Config.tileBorder(this)).apply();
            changed(); refresh();
        });
        ambientModeChoice = findViewById(R.id.ambientModeChoice);
        ambientModeChoice.setOnClickListener(v -> showAmbientModePicker());
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this, R.style.SettingsDialog)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            new AlertDialog.Builder(this, R.style.SettingsDialog)
                    .setTitle(R.string.follow_theme)
                    .setMessage(R.string.follow_theme_confirm)
                    .setPositiveButton(R.string.follow_theme, (dialog, which) -> resetLocalChoices())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
        defaultHomeChoice = findViewById(R.id.defaultHomeChoice);
        defaultHomeChoice.setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        tvSettingsChoice = findViewById(R.id.tvSettingsChoice);
        tvSettingsChoice.setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
        // The first focus can be set before the scroll content has its final tops.
        settingsScroll.post(() -> {
            View focus = getCurrentFocus();
            if (focus != null) updateSectionForFocus(focus);
        });
        if (SECTION_NOW_PLAYING.equals(getIntent().getStringExtra(EXTRA_SECTION))) {
            settingsScroll.post(() -> {
                scrollToSection(4);
                npSizeChoice.requestFocus();
            });
        }
        if (BuildConfig.DEBUG) {
            String debugSection = getIntent().getStringExtra(EXTRA_DEBUG_SECTION);
            int index = debugSectionIndex(debugSection);
            if (index >= 0) {
                settingsScroll.post(() -> {
                    scrollToSection(index);
                    View target = "now_playing".equals(debugSection) ? npSizeChoice : navItems[index];
                    target.requestFocus();
                });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // AerialViews selection, notification access, media state and the Home-key helper can change
        // while this panel is closed (or behind a system dialog). Show the real state on return.
        refresh();
    }

    /** Debug-only section names; -1 keeps release behaviour and rejects unknown debug values. */
    private static int debugSectionIndex(String name) {
        if (name == null) return -1;
        switch (name) {
            case "appearance": return 0;
            case "background": return 1;
            case "motion": return 2;
            case "shelf": return 3;
            case "now_playing": return 4;
            case "screensaver": return 5;
            case "system": return 6;
            default: return -1;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
            View focus = getCurrentFocus();
            if (focus instanceof SettingsRow && inside(focus, settingsScroll)
                    && navItems[Math.max(0, activeSection)].isShown()) {
                navItems[Math.max(0, activeSection)].requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean inside(View child, View parent) {
        View view = child;
        while (view != null) {
            if (view == parent) return true;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return false;
    }

    private void setUpSections() {
        for (int i = 0; i < navIds.length; i++) {
            final int index = i;
            navItems[i] = findViewById(navIds[i]);
            navItems[i].setOnClickListener(v -> scrollToSection(index));
            navItems[i].setNextFocusRightId(navFirstRowIds[i]);
        }
        settingsScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldX, oldY) ->
                updateSectionFromScroll());
        // A D-pad Right jump can focus a row in a section that was never scrolled through; the
        // scroll listener alone leaves the sticky title on the previous section.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(
                (oldFocus, newFocus) -> {
                    if (newFocus != null && inside(newFocus, settingsScroll)) {
                        updateSectionForFocus(newFocus);
                    }
                });
        selectSection(0);
        settingsScroll.post(this::updateSectionFromScroll);
    }

    private void scrollToSection(int index) {
        View header = findViewById(navSectionIds[index]);
        if (header != null) {
            settingsScroll.smoothScrollTo(0, Math.max(0, header.getTop() - dp(4)));
        }
        selectSection(index);
    }

    private void updateSectionForFocus(View focus) {
        View content = findViewById(R.id.settingsContent);
        if (content == null) return;
        int[] focusAt = new int[2];
        int[] contentAt = new int[2];
        focus.getLocationOnScreen(focusAt);
        content.getLocationOnScreen(contentAt);
        int top = focusAt[1] - contentAt[1];
        int active = 0;
        for (int i = 0; i < navSectionIds.length; i++) {
            View header = findViewById(navSectionIds[i]);
            if (header != null && header.getTop() <= top) active = i;
        }
        selectSection(active);
    }

    private void updateSectionFromScroll() {
        if (settingsScroll.getHeight() == 0) return;
        int y = settingsScroll.getScrollY() + dp(28);
        int active = 0;
        for (int i = 0; i < navSectionIds.length; i++) {
            View header = findViewById(navSectionIds[i]);
            if (header != null && header.getTop() <= y) active = i;
        }
        selectSection(active);
    }

    private void selectSection(int index) {
        if (index < 0 || index >= navItems.length) return;
        if (index == activeSection) return;
        activeSection = index;
        sectionTitle.setText(getString(SECTION_TITLES[index]));
        sectionHint.setText(getString(SECTION_HINTS[index]));
        for (int i = 0; i < navItems.length; i++) navItems[i].setSelected(i == index);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private int currentIconPackStyle() {
        return IconChoices.globalStyle(this);
    }

    private void showSmartStackSecondsPicker() {
        int[] values = {5, 8, 12, 20, 30};
        String[] labels = new String[values.length];
        int checked = 0;
        int current = Config.smartStackSeconds(this);
        for (int i = 0; i < values.length; i++) {
            labels[i] = getString(R.string.smart_stack_seconds, values[i]);
            if (values[i] == current) checked = i;
        }
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.smart_stack_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.SMART_STACK_SECONDS_KEY, values[which]).apply();
                    changed(); refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showScreensaverPicker() {
        String[] labels = {getString(R.string.screensaver_engine_aerial),
                getString(R.string.screensaver_engine_clock), getString(R.string.screensaver_engine_tv)};
        int checked = 2;
        if (ScreensaverController.clockOwnsDream(this)) checked = 1;
        else if (ScreensaverController.aerialViewsOwnsDream(this)) checked = 0;
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.aerial_views_dialog)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    String label = labels[which];
                    ScreensaverController.Status status;
                    if (which == 0) {
                        status = ScreensaverController.selectAerialViews(this);
                    } else if (which == 1) {
                        status = ScreensaverController.selectClock(this);
                    } else {
                        status = ScreensaverController.restoreDefault(this);
                        if (status == ScreensaverController.Status.OK) label = getString(R.string.aerial_views_tv);
                    }
                    showScreensaverStatus(status, label);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void toggleNotificationAccess() {
        if (!ScreensaverController.isInstalled(this, ScreensaverController.AERIAL_PACKAGE)) {
            Toast.makeText(this, R.string.aerial_views_missing, Toast.LENGTH_LONG).show();
            return;
        }
        boolean enabled = ScreensaverController.notificationAccessEnabled(this);
        if (enabled) {
            if (!ScreensaverController.setNotificationAccess(this, false)) {
                Toast.makeText(this, R.string.screensaver_notification_failed, Toast.LENGTH_LONG).show();
                openNotificationListenerSettings();
            }
            refresh();
            return;
        }
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.screensaver_notification)
                .setMessage(R.string.screensaver_notification_confirm)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (!ScreensaverController.setNotificationAccess(this, true)) {
                        Toast.makeText(this, R.string.screensaver_notification_failed, Toast.LENGTH_LONG).show();
                        openNotificationListenerSettings();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openNotificationListenerSettings() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        if (intent.resolveActivity(getPackageManager()) == null) {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + ScreensaverController.AERIAL_PACKAGE));
        }
        open(intent);
    }

    private void showScreensaverStatus(ScreensaverController.Status status, String label) {
        int message;
        switch (status) {
            case OK: message = 0; break;
            case NOT_INSTALLED: message = R.string.aerial_views_missing; break;
            case NO_DREAM_SERVICE: message = R.string.screensaver_no_dream; break;
            case NO_PERMISSION: message = R.string.aerial_views_needs_permission; break;
            default: message = R.string.aerial_views_failed; break;
        }
        Toast.makeText(this, message == 0 ? getString(R.string.screensaver_changed, label)
                : getString(message), Toast.LENGTH_LONG).show();
    }

    private int visualModeLabel(int mode) {
        return mode == 0 ? R.string.np_mode_off : mode == 1 ? R.string.np_mode_waves
                : mode == 2 ? R.string.np_mode_oscilloscope : mode == 3 ? R.string.np_mode_aura
                : mode == 4 ? R.string.np_mode_orb : R.string.np_mode_minimal;
    }

    private void showNpVisualPicker() {
        String[] labels = {getString(R.string.np_mode_off), getString(R.string.np_mode_waves),
                getString(R.string.np_mode_oscilloscope), getString(R.string.np_mode_aura),
                getString(R.string.np_mode_orb), getString(R.string.np_mode_minimal)};
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.np_visualizer_label)
                .setSingleChoiceItems(labels, Config.npVisualMode(this), (dialog, which) -> {
                    dialog.dismiss();
                    SharedPreferences.Editor editor = choices.edit()
                            .putInt(Config.NP_VISUAL_MODE_KEY, which);
                    if (which != 0) editor.putBoolean(Config.NP_WAVEFORM_KEY, true);
                    if (which == 3) editor.putBoolean(Config.NP_ALBUM_ART_KEY, true);
                    editor.apply();
                    changed(); refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showNpSizePicker() {
        String[] labels = {getString(R.string.np_size_compact), getString(R.string.np_size_balanced),
                getString(R.string.np_size_roomy), getString(R.string.np_size_large)};
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.np_size_label)
                .setSingleChoiceItems(labels, Config.npBarSize(this), (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.NP_SIZE_KEY, which).apply();
                    changed(); refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showLivingWallpaperPicker() {
        String[] labels = {getString(R.string.living_wallpaper_off),
                getString(R.string.living_wallpaper_subtle), getString(R.string.living_wallpaper_full)};
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.living_wallpaper_label)
                .setMessage(R.string.living_wallpaper_hint)
                .setSingleChoiceItems(labels, Config.livingWallpaper(this), (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.LIVING_WALLPAPER_KEY, which).apply();
                    changed(); refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showTopShelfSourcesPicker() {
        PackageManager pm = getPackageManager();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<String[]> apps = new ArrayList<>();
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        java.util.Set<String> sources = Config.topShelfSources(this);
        boolean all = sources.contains(Config.TOP_SHELF_ALL_SOURCES);
        String[] labels = new String[apps.size() + 1];
        boolean[] checked = new boolean[apps.size() + 1];
        labels[0] = getString(R.string.top_shelf_sources_all);
        checked[0] = all;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            checked[i + 1] = all || sources.contains(apps.get(i)[1]);
        }
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.top_shelf_sources_dialog)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (checked[0]) {
                        // Only the explicit "All apps" choice means future apps are included.
                        choices.edit().putStringSet(Config.TOP_SHELF_SOURCES_KEY,
                                java.util.Collections.singleton(Config.TOP_SHELF_ALL_SOURCES)).apply();
                    } else {
                        java.util.Set<String> selected = new java.util.HashSet<>();
                        for (int i = 0; i < apps.size(); i++) if (checked[i + 1]) selected.add(apps.get(i)[1]);
                        choices.edit().putStringSet(Config.TOP_SHELF_SOURCES_KEY, selected).apply();
                    }
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showTopShelfPicker() {
        String[] labels = {getString(R.string.top_shelf_off), getString(R.string.top_shelf_focus),
                getString(R.string.top_shelf_always)};
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.top_shelf_label)
                .setSingleChoiceItems(labels, Config.topShelf(this), (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.TOP_SHELF_KEY, which).apply();
                    changed(); refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showParallaxPicker() {
        String[] labels = {getString(R.string.parallax_off), getString(R.string.parallax_subtle),
                getString(R.string.parallax_full)};
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.parallax_label)
                .setSingleChoiceItems(labels, Config.parallax(this), (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.PARALLAX_KEY, which).apply();
                    changed(); refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showFocusMotionPicker() {
        String[] labels = {getString(R.string.focus_motion_standard), getString(R.string.focus_motion_lumen)};
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.focus_motion_label)
                .setSingleChoiceItems(labels, Config.focusMotion(this), (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.FOCUS_MOTION_KEY, which).apply();
                    changed(); refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showFocusGlossPicker() {
        String[] labels = {getString(R.string.focus_gloss_off), getString(R.string.focus_gloss_subtle),
                getString(R.string.focus_gloss_full)};
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.focus_gloss_label)
                .setSingleChoiceItems(labels, Config.focusGloss(this), (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.FOCUS_GLOSS_KEY, which).apply();
                    changed(); refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showGlassMotionPicker() {
        String[] labels = {
                getString(R.string.glass_motion_off),
                getString(R.string.glass_motion_subtle),
                getString(R.string.glass_motion_full)
        };
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.glass_motion_label)
                .setSingleChoiceItems(labels, Config.glassMotion(this), (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.GLASS_MOTION_KEY, which).apply();
                    changed(); refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDockGapPicker() {
        String[] labels = new String[DOCK_GAPS.length];
        int current = Config.dockGap(this);
        int checked = 0;
        for (int i = 0; i < DOCK_GAPS.length; i++) {
            labels[i] = DOCK_GAPS[i] + " dp";
            if (DOCK_GAPS[i] == current) checked = i;
        }
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.dock_gap_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.DOCK_GAP_KEY, DOCK_GAPS[which]).apply();
                    changed(); refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private boolean tilesAlwaysShowNames() {
        return Config.load(this).alwaysShowNames;
    }

    private void showAppNamesPicker() {
        String[] labels = {getString(R.string.app_names_focus), getString(R.string.app_names_always),
                getString(R.string.app_names_never)};
        int current = Config.appNames(this, tilesAlwaysShowNames());
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.app_names_label)
                .setSingleChoiceItems(labels, current, (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.APP_NAMES_KEY, which).apply();
                    changed(); refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showHeroTitlePicker() {
        String[] labels = {getString(R.string.hero_title_large), getString(R.string.hero_title_small),
                getString(R.string.hero_title_hidden)};
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.hero_title_label)
                .setSingleChoiceItems(labels, Config.heroTitle(this), (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.HERO_TITLE_KEY, which).apply();
                    changed(); refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAmbientModePicker() {
        String[] labels = {getString(R.string.ambient_mode_standard), getString(R.string.ambient_mode_calm),
                getString(R.string.ambient_mode_off)};
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.ambient_mode_label)
                .setSingleChoiceItems(labels, Config.ambientMode(this), (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.AMBIENT_MODE_KEY, which).apply();
                    changed(); refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showIconGlowPicker() {
        String[] labels = new String[ICON_GLOWS.length];
        int current = Config.iconGlow(this);
        int checked = 0;
        for (int i = 0; i < ICON_GLOWS.length; i++) {
            labels[i] = getString(R.string.glass_transparency_value, ICON_GLOWS[i]);
            if (ICON_GLOWS[i] == current) checked = i;
        }
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.icon_glow_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.ICON_GLOW_KEY, ICON_GLOWS[which]).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showInstalledIconPackPicker() {
        java.util.List<IconPacks.Pack> packs = new java.util.ArrayList<>(IconPacks.installed(this));
        String current = Config.iconPack(this);
        boolean currentListed = false;
        for (IconPacks.Pack pack : packs) currentListed |= pack.pkg.equals(current);
        if (!current.isEmpty() && !currentListed) {
            // Visibility filtering or a pack without a launcher activity can hide the selected pack.
            // Keep it in the picker so the dialog shows the real current value.
            String label;
            try {
                PackageManager pm = getPackageManager();
                label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(current, 0)));
            } catch (PackageManager.NameNotFoundException missing) {
                label = current;
            }
            packs.add(new IconPacks.Pack(current, label));
        }
        if (packs.isEmpty()) {
            Toast.makeText(this, R.string.icon_pack_none_found, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[packs.size() + 1];
        labels[0] = getString(R.string.icon_pack_none);
        int checked = 0;
        for (int i = 0; i < packs.size(); i++) {
            labels[i + 1] = packs.get(i).label;
            if (packs.get(i).pkg.equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.icon_pack_installed_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(Config.ICON_PACK_KEY).apply();
                    else choices.edit().putString(Config.ICON_PACK_KEY, packs.get(which - 1).pkg).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showGlassTransparencyPicker() {
        String[] labels = new String[GLASS_TRANSPARENCIES.length];
        int current = Config.glassTransparency(this);
        int checked = 0;
        for (int i = 0; i < GLASS_TRANSPARENCIES.length; i++) {
            labels[i] = getString(R.string.glass_transparency_value, GLASS_TRANSPARENCIES[i]);
            if (GLASS_TRANSPARENCIES[i] == current) checked = i;
        }
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.glass_transparency_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.GLASS_TRANSPARENCY_KEY, GLASS_TRANSPARENCIES[which]).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showClockStylePicker() {
        String[] labels = {
                getString(R.string.clock_style_standard),
                getString(R.string.clock_style_hitech)
        };
        int checked = "hitech".equals(Config.clockStyle(this)) ? 1 : 0;
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.clock_style_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putString(Config.CLOCK_STYLE_KEY, CLOCK_STYLES[which]).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showIconPackPicker() {
        String[] labels = {
                getString(R.string.icon_pack_glitch),
                getString(R.string.icon_pack_original),
                getString(R.string.icon_pack_theme),
                getString(R.string.icon_pack_glitch_still)
        };
        int[] styles = {IconChoices.GLITCH, IconChoices.ORIGINAL, IconChoices.THEME,
                IconChoices.GLITCH_STILL};
        int checked = 0;
        int current = currentIconPackStyle();
        for (int i = 0; i < styles.length; i++) if (styles[i] == current) checked = i;
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.icon_pack_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    IconChoices.setGlobalStyle(this, styles[which]);
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Off, then every app with a TV or phone entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo r : pm.queryIntentActivities(intent, 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                boolean seen = false;
                for (String[] a : apps) seen |= a[1].equals(pkg);
                if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
            }
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String current = choices.getString(OpenOnStart.KEY, "");
        boolean currentListed = false;
        for (String[] a : apps) currentListed |= a[1].equals(current);
        if (!current.isEmpty() && !currentListed) {
            // A package saved before the picker also listed phone-only apps must stay selectable.
            String label;
            try {
                label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(current, 0)));
            } catch (PackageManager.NameNotFoundException missing) {
                label = current;
            }
            apps.add(new String[]{label, current});
            Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        }
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this, R.style.SettingsDialog)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /** The current default launcher's label, so the row shows what the picker will change. */
    private String defaultHomeLabel() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo info = getPackageManager().resolveActivity(home, 0);
            if (info == null || info.activityInfo == null) return getString(R.string.not_available);
            return String.valueOf(info.loadLabel(getPackageManager()));
        } catch (RuntimeException unavailable) {
            return getString(R.string.not_available);
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        // Remote-key helpers cache their config; let them see Control Center and the other switches at once.
        HomeKeyService.reloadConfig();
        MainActivity.liveSettingsChanged();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void applySettingsBackground() {
        // The settings panel is translucent over the live Home screen; no opaque backdrop.
        View root = findViewById(R.id.settingsRoot);
        root.setBackgroundResource(R.drawable.settings_scrim);
    }

    /** Disabled rows must not stay focusable; the reason is shown in the value line. */
    private void setRowEnabled(View row, boolean enabled) {
        row.setEnabled(enabled);
        row.setFocusable(enabled);
        row.setClickable(enabled);
        row.setAlpha(enabled ? 1f : 0.45f);
        if (!enabled && row.hasFocus()) row.clearFocus();
    }

    private String withReason(String value, boolean enabled, int reason) {
        return enabled ? value : value + " · " + getString(reason);
    }

    private void refresh() {
        applySettingsBackground();
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        clockStyleChoice.setText(getString(R.string.clock_style_state,
                getString("hitech".equals(Config.clockStyle(this))
                        ? R.string.clock_style_hitech : R.string.clock_style_standard)));
        liquidGlassChoice.setText(getString(R.string.liquid_glass_state,
                getString(Config.liquidGlass(this) ? R.string.on : R.string.off)));
        boolean glassOn = Config.liquidGlass(this);
        glassTransparencyChoice.setText(getString(R.string.glass_transparency_state,
                Config.glassTransparency(this))
                + (glassOn ? "" : " · " + getString(R.string.requires_liquid_glass)));
        setRowEnabled(glassTransparencyChoice, glassOn);
        int iconStyle = currentIconPackStyle();
        String iconLabel = IconChoices.hasPerAppChoices(this) ? getString(R.string.icon_pack_mixed)
                : iconStyle == IconChoices.GLITCH ? getString(R.string.icon_pack_glitch)
                : iconStyle == IconChoices.THEME ? getString(R.string.icon_pack_theme)
                : iconStyle == IconChoices.GLASS ? getString(R.string.icon_pack_glass)
                : iconStyle == IconChoices.GLITCH_STILL ? getString(R.string.icon_pack_glitch_still)
                : iconStyle == IconChoices.ORIGINAL ? getString(R.string.icon_pack_original)
                : getString(R.string.icon_pack_mixed);
        iconPackChoice.setText(getString(R.string.icon_pack_state, iconLabel));
        String packPkg = Config.iconPack(this);
        String packLabel = getString(R.string.icon_pack_none);
        if (!packPkg.isEmpty()) {
            try {
                android.content.pm.PackageManager pm = getPackageManager();
                packLabel = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(packPkg, 0)));
            } catch (android.content.pm.PackageManager.NameNotFoundException missing) {
                packLabel = packPkg;
            }
        }
        installedIconPackChoice.setText(getString(R.string.icon_pack_installed_state, packLabel));
        iconGlowChoice.setText(getString(R.string.icon_glow_state, Config.iconGlow(this)));
        int motion = Config.glassMotion(this);
        glassMotionChoice.setText(getString(R.string.glass_motion_state,
                getString(motion == 0 ? R.string.glass_motion_off
                        : motion == 1 ? R.string.glass_motion_subtle : R.string.glass_motion_full)));
        boolean clockSelected = ScreensaverController.selectedComponents(this)
                .contains(ScreensaverController.clockComponent());
        screensaverMediaChoice.setText(getString(R.string.screensaver_clock_artwork) + ": "
                + getString(Config.screensaverMedia(this) ? R.string.on : R.string.off)
                + (clockSelected ? "" : " · " + getString(R.string.screensaver_clock_only)));
        setRowEnabled(screensaverMediaChoice, clockSelected);
        boolean secure = ScreensaverController.hasWriteSecure(this);
        boolean aerialInstalled = ScreensaverController.isInstalled(this,
                ScreensaverController.AERIAL_PACKAGE);
        String selected = ScreensaverController.selectedLabel(this);
        if (!aerialInstalled) {
            aerialViewsChoice.setText(getString(R.string.aerial_views_missing));
        } else if (!secure) {
            aerialViewsChoice.setText(getString(R.string.aerial_views_no_permission));
        } else {
            String state = selected.isEmpty() ? getString(R.string.aerial_views_off)
                    : selected + (ScreensaverController.isEnabled(this) ? "" : " (disabled)");
            aerialViewsChoice.setText(getString(R.string.aerial_views_state, state));
        }
        String dreamLabel = selected.isEmpty() ? getString(R.string.screensaver_engine_tv)
                : selected + (ScreensaverController.isEnabled(this) ? "" : " (disabled)");
        screensaverStatusChoice.setText(getString(R.string.screensaver_changed, dreamLabel));
        screensaverTestChoice.setText(R.string.screensaver_test);
        boolean notificationAccess = ScreensaverController.notificationAccessEnabled(this);
        screensaverNotificationChoice.setText(getString(R.string.screensaver_notification_state,
                getString(notificationAccess ? R.string.on : R.string.off)));
        screensaverOverlayChoice.setText(R.string.screensaver_overlay);
        screensaverRestoreChoice.setText(R.string.screensaver_restore);
        boolean mediaAllowed = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
                || getPackageManager().checkPermission("android.permission.READ_MEDIA_VIDEO",
                ScreensaverController.AERIAL_PACKAGE) == PackageManager.PERMISSION_GRANTED
                || getPackageManager().checkPermission("android.permission.READ_MEDIA_IMAGES",
                ScreensaverController.AERIAL_PACKAGE) == PackageManager.PERMISSION_GRANTED;
        screensaverMediaAccessChoice.setText(getString(R.string.screensaver_media_access,
                getString(mediaAllowed ? R.string.on : R.string.off))
                + (mediaAllowed ? "" : " · " + getString(R.string.screensaver_media_denied)));
        int sleep = ScreensaverController.sleepTimerMinutes(this);
        screensaverSleepChoice.setText(sleep < 0 ? getString(R.string.screensaver_sleep_none)
                : getString(R.string.screensaver_sleep, sleep));
        boolean mediaBackground = Config.mediaBackground(this);
        boolean mediaWaiting = mediaBackground && MediaArt.current(this) == null;
        mediaBackgroundChoice.setText(getString(R.string.media_background_state,
                getString(mediaBackground ? R.string.on : R.string.off))
                + (mediaWaiting ? getString(R.string.media_background_waiting) : ""));
        boolean shelfOn = Config.topShelf(this) != 0;
        int shelfCount = TopShelf.lastItemCount;
        boolean smartOn = Config.smartStack(this);
        String shelfReason = shelfOn ? "" : " · " + getString(R.string.requires_top_shelf);
        int smartReason = shelfCount <= 0 ? R.string.smart_stack_wait
                : shelfCount == 1 ? R.string.smart_stack_requires_two : 0;
        String smartSecondsReason = !shelfOn ? " · " + getString(R.string.requires_top_shelf)
                : !smartOn ? " · " + getString(R.string.requires_smart_stack)
                : smartReason != 0 ? " · " + getString(smartReason) : "";
        topShelfProgressChoice.setText(getString(R.string.top_shelf_progress_state,
                getString(Config.topShelfProgress(this) ? R.string.on : R.string.off)) + shelfReason);
        topShelfAutoHideChoice.setText(getString(R.string.top_shelf_auto_hide_state,
                getString(Config.topShelfAutoHide(this) ? R.string.on : R.string.off)) + shelfReason);
        java.util.Set<String> sources = Config.topShelfSources(this);
        String sourcesLabel = sources.contains(Config.TOP_SHELF_ALL_SOURCES)
                ? getString(R.string.top_shelf_sources_all)
                : sources.isEmpty() ? getString(R.string.top_shelf_sources_none)
                : getString(R.string.top_shelf_sources_count, sources.size());
        topShelfSourcesChoice.setText(getString(R.string.top_shelf_sources_state, sourcesLabel) + shelfReason);
        topShelfNowPlayingChoice.setText(getString(R.string.top_shelf_now_playing_state,
                getString(Config.topShelfNowPlaying(this) ? R.string.on : R.string.off)) + shelfReason);
        nowPlayingFullChoice.setText(getString(R.string.now_playing_full_state,
                getString(Config.nowPlayingFull(this) ? R.string.on : R.string.off)));
        npWaveformChoice.setText(getString(R.string.np_waveform_state,
                getString(Config.npWaveform(this) ? R.string.on : R.string.off)));
        int size = Config.npBarSize(this);
        npSizeChoice.setText(getString(R.string.np_size_state,
                getString(size == 0 ? R.string.np_size_compact : size == 1 ? R.string.np_size_balanced
                        : size == 2 ? R.string.np_size_roomy : R.string.np_size_large)));
        npVisualizerChoice.setText(getString(R.string.np_visualizer_state,
                getString(visualModeLabel(Config.npVisualMode(this)))));
        npTimesChoice.setText(getString(R.string.np_times_state,
                getString(Config.npTimes(this) ? R.string.on : R.string.off)));
        npAlbumArtChoice.setText(getString(R.string.np_album_art_state,
                getString(Config.npAlbumArt(this) ? R.string.on : R.string.off)));
        npSourceBadgeChoice.setText(getString(R.string.np_source_badge_state,
                getString(Config.npSource(this) ? R.string.on : R.string.off)));
        npCloseChoice.setText(getString(R.string.np_close_state,
                getString(Config.npClose(this) ? R.string.on : R.string.off)));
        String smartShelfReason = !shelfOn ? shelfReason
                : smartReason != 0 ? " · " + getString(smartReason) : "";
        smartStackChoice.setText(getString(R.string.smart_stack_state,
                getString(smartOn ? R.string.on : R.string.off)) + smartShelfReason);
        smartStackSecondsChoice.setText(getString(R.string.smart_stack_seconds_state,
                Config.smartStackSeconds(this)) + smartSecondsReason);
        setRowEnabled(topShelfSourcesChoice, shelfOn);
        setRowEnabled(topShelfNowPlayingChoice, shelfOn);
        setRowEnabled(topShelfProgressChoice, shelfOn);
        setRowEnabled(topShelfAutoHideChoice, shelfOn);
        // Keep an already-enabled Smart Stack switchable so the owner is not trapped when only one card exists.
        setRowEnabled(smartStackChoice, shelfOn && (shelfCount > 1 || smartOn));
        setRowEnabled(smartStackSecondsChoice, shelfOn && smartOn && shelfCount > 1);
        controlCenterChoice.setText(getString(Config.controlCenter(this) && !HomeKeyService.isRunning()
                ? R.string.control_center_service_off : R.string.control_center_state,
                getString(Config.controlCenter(this) ? R.string.on : R.string.off)));
        int living = Config.livingWallpaper(this);
        String livingLabel = getString(living == 0 ? R.string.living_wallpaper_off
                : living == 1 ? R.string.living_wallpaper_subtle : R.string.living_wallpaper_full);
        int wallpapers = Config.load(this).wallpapers.size();
        livingWallpaperChoice.setText(getString(R.string.living_wallpaper_state, livingLabel)
                + (living > 0 && wallpapers <= 1 ? getString(R.string.living_wallpaper_single) : ""));
        int shelf = Config.topShelf(this);
        topShelfChoice.setText(getString(R.string.top_shelf_state,
                getString(shelf == 0 ? R.string.top_shelf_off
                        : shelf == 1 ? R.string.top_shelf_focus : R.string.top_shelf_always)));
        int parallax = Config.parallax(this);
        parallaxChoice.setText(getString(R.string.parallax_state,
                getString(parallax == 0 ? R.string.parallax_off
                        : parallax == 1 ? R.string.parallax_subtle : R.string.parallax_full)));
        focusMotionChoice.setText(getString(R.string.focus_motion_state,
                getString(Config.focusMotion(this) == 1 ? R.string.focus_motion_lumen : R.string.focus_motion_standard)));
        int gloss = Config.focusGloss(this);
        focusGlossChoice.setText(getString(R.string.focus_gloss_state,
                getString(gloss == 0 ? R.string.focus_gloss_off
                        : gloss == 1 ? R.string.focus_gloss_subtle : R.string.focus_gloss_full)));
        editJiggleChoice.setText(getString(R.string.edit_jiggle_state,
                getString(Config.editJiggle(this) ? R.string.on : R.string.off)));
        topBarCapsuleChoice.setText(getString(R.string.top_bar_capsule_state,
                getString(Config.topBarCapsule(this) ? R.string.on : R.string.off)));
        dockGapChoice.setText(getString(R.string.dock_gap_state, Config.dockGap(this)));
        shelfShadowChoice.setText(getString(R.string.shelf_shadow_state,
                getString(Config.shelfShadow(this) ? R.string.on : R.string.off)));
        int hero = Config.heroTitle(this);
        heroTitleChoice.setText(getString(R.string.hero_title_state,
                getString(hero == 0 ? R.string.hero_title_large
                        : hero == 1 ? R.string.hero_title_small : R.string.hero_title_hidden)));
        tileBorderChoice.setText(getString(R.string.tile_border_state,
                getString(Config.tileBorder(this) ? R.string.on : R.string.off)));
        int names = Config.appNames(this, tilesAlwaysShowNames());
        appNamesChoice.setText(getString(R.string.app_names_state,
                getString(names == 0 ? R.string.app_names_focus
                        : names == 1 ? R.string.app_names_always : R.string.app_names_never)));
        int ambient = Config.ambientMode(this);
        ambientModeChoice.setText(getString(R.string.ambient_mode_state,
                getString(ambient == 0 ? R.string.ambient_mode_standard
                        : ambient == 1 ? R.string.ambient_mode_calm : R.string.ambient_mode_off)));
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off))
                + (animations ? "" : " · " + getString(R.string.requires_animations)));
        setRowEnabled(advancedChoice, animations);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        defaultHomeChoice.setText(getString(R.string.home_settings) + ": " + defaultHomeLabel());
        boolean choicesAvailable = hasOnTvChoices();
        followThemeChoice.setText(getString(R.string.follow_theme)
                + (choicesAvailable ? "" : " · " + getString(R.string.no_local_choices)));
        setRowEnabled(followThemeChoice, choicesAvailable);
    }

    /** Clears every on-TV choice through Config's one shared registry. */
    private void resetLocalChoices() {
        Config.clearLocalChoices(this);
        Config current = Config.load(this);
        columns = current.columns;
        rowsBelowDock = current.rowsBelowDock;
        dockOpacity = current.dockOpacity;
        clockFormat = current.clockFormat;
        animations = current.animations;
        advancedEffects = current.animations && current.advancedEffects;
        changed();
        refresh();
    }

    private boolean hasOnTvChoices() {
        return Config.hasLocalChoices(this);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
