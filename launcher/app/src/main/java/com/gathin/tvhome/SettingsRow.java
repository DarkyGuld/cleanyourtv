package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.TextView;

/**
 * A settings row with a title and a quieter second line for its current value. The rest of the
 * settings code stores one "Title: value" string per row, so this view splits that string instead
 * of forcing every caller to learn a new API.
 */
public final class SettingsRow extends TextView {
    // TextView's constructor calls setText() for android:text before field initializers run. Keep this null
    // (not "") so the XML text survives until the initializer; style() handles the null case.
    private String raw;
    private boolean focused;
    private static boolean lightMode;

    static void setLightMode(boolean light) {
        lightMode = light;
    }

    /** A chevron for rows that open a dialog; ignored for toggles. */
    void setChevron(boolean show) {
        if (show) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron, 0);
            setCompoundDrawablePadding(Math.round(8 * getResources().getDisplayMetrics().density));
        } else {
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    /** A non-Material pill on the right for boolean rows. */
    void setToggle(boolean on) {
        setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
                on ? R.drawable.settings_toggle_on : R.drawable.settings_toggle_off, 0);
        setCompoundDrawablePadding(Math.round(12 * getResources().getDisplayMetrics().density));
    }

    void clearTrailing() {
        setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
    }

    public SettingsRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        setGravity(Gravity.CENTER_VERTICAL);
        setLineSpacing(0f, 1.02f);
        setSingleLine(false);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        raw = text == null ? "" : text.toString();
        super.setText(style(raw, focused), type);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        focused = gainFocus;
        super.setText(style(raw, focused), BufferType.NORMAL);
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        super.setText(style(raw, focused), BufferType.NORMAL);
    }

    private CharSequence style(String text, boolean isFocused) {
        if (text == null) text = "";
        int split = text.indexOf(':');
        String title = split < 0 ? text.trim() : text.substring(0, split).trim();
        String value = split < 0 ? "" : text.substring(split + 1).trim();
        SpannableString out = new SpannableString(value.isEmpty() ? title : title + "\n" + value);
        if (!value.isEmpty()) {
            int start = title.length() + 1;
            out.setSpan(new RelativeSizeSpan(0.72f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            boolean dark = isFocused || isSelected();
            out.setSpan(new ForegroundColorSpan(dark ? 0xB3080A0D
                    : lightMode ? 0x99000000 : 0xB3FFFFFF),
                    start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        out.setSpan(new StyleSpan(Typeface.NORMAL), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return out;
    }
}
