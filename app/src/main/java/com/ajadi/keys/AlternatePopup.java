package com.ajadi.keys;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/** Transient local hold-and-slide choice strip: never reads editor text. */
final class AlternatePopup {
    private final PopupWindow window;
    private final LinearLayout strip;
    private final TextView[] choices;
    private final String[] values;
    private final KeyboardPalette palette;
    private final int cellWidth, padding;
    private final int estimatedLeft;
    private int selected;

    AlternatePopup(View anchor, View keyboard, String[] values, KeyboardPalette palette) {
        this.values = values;
        this.palette = palette;
        Context context = anchor.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        padding = Math.round(3 * density);
        int standard=Math.round(50*density);
        int available=keyboard.getWidth()-2*padding;
        cellWidth=available>0?Math.min(standard,Math.max(1,available/values.length)):standard;
        int h = Math.round(49 * density);
        strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        strip.setPadding(padding, padding, padding, padding);
        GradientDrawable shell = new GradientDrawable();
        shell.setColor(palette.background);
        shell.setCornerRadius(10 * density);
        shell.setStroke(Math.max(1, Math.round(density)), palette.actionPressed);
        strip.setBackground(shell);
        int w = cellWidth * values.length + 2 * padding;
        choices = new TextView[values.length];
        for (int i = 0; i < values.length; i++) {
            TextView t = new TextView(context);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(21);
            t.setText(values[i]);
            strip.addView(t, new LinearLayout.LayoutParams(cellWidth, h));
            choices[i] = t;
        }
        select(0);
        int[] key = new int[2], kb = new int[2];
        anchor.getLocationOnScreen(key);
        keyboard.getLocationOnScreen(kb);
        estimatedLeft = Math.max(kb[0], Math.min(key[0] + anchor.getWidth()/2
                - cellWidth/2 - padding, kb[0] + keyboard.getWidth() - w));
        window = new PopupWindow(strip, w, h + padding * 2, false);
        window.setTouchable(false);
        window.setClippingEnabled(false);
        window.setElevation(6 * density);
        window.showAsDropDown(anchor, estimatedLeft - key[0],
                -anchor.getHeight() - h - 2 * padding, Gravity.START);
    }

    void move(float screenX) {
        int[] location = new int[2];
        int left = estimatedLeft;
        if (strip.isAttachedToWindow() && strip.getWidth() > 0) {
            strip.getLocationOnScreen(location);
            left = location[0];
        }
        int i = (int)Math.floor((screenX - left - padding) / cellWidth);
        select(Math.max(0, Math.min(values.length-1, i)));
    }

    private void select(int index) {
        selected = index;
        for (int i=0; i<choices.length; i++) {
            boolean active = i == index;
            choices[i].setTextColor(active ? palette.accentText : palette.text);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(active ? palette.accent : palette.key);
            bg.setCornerRadius(6 * choices[i].getResources().getDisplayMetrics().density);
            choices[i].setBackground(bg);
        }
    }

    String value() { return values[selected]; }
    void dismiss() { window.dismiss(); }
}
