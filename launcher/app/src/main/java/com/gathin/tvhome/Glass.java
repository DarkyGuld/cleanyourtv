package com.gathin.tvhome;

import android.content.Context;
import android.view.View;

/** Shared glass panels (menus and popups), switched by Config.liquidGlass(). */
final class Glass {
    private Glass() {
    }

    static void panel(Context context, View view) {
        view.setBackgroundResource(Config.liquidGlass(context)
                ? R.drawable.glass_panel_liquid : R.drawable.glass_panel);
    }
}
