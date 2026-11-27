package com.ajadi.keys;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.function.Consumer;

/** Presentation only. Receives items from its caller; never accesses or tracks the clipboard. */
final class ClipboardPanel {
    private final Context context;
    private final KeyboardPalette palette;

    ClipboardPanel(Context context, KeyboardPalette palette) {
        this.context = context;
        this.palette = palette;
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private TextView label(String value, int size) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(palette.text);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private TextView action(String text, String description, Runnable clicked) {
        TextView v = label(text, 16);
        v.setGravity(Gravity.CENTER);
        v.setContentDescription(description);
        card(v, palette.action);
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnClickListener(ignored -> clicked.run());
        return v;
    }

    private void card(View view, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(8));
        view.setBackground(bg);
    }

    LinearLayout create(int listHeightDp, String title, String clearLabel, String emptyMessage,
                        List<String> recentItems, Runnable goBack, Runnable clear,
                        Consumer<String> select, boolean saved, Runnable toggleSaved, Runnable manage,
                        Consumer<String> remove,Consumer<String> pin) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = action("←", "Back to keyboard", goBack);
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(40)));
        TextView heading = label(title, 18);
        heading.setPadding(dp(10), 0, dp(8), 0);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView erase = action(clearLabel, "Clear recent items", clear);
        header.addView(erase, new LinearLayout.LayoutParams(dp(90), dp(40)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout tabs=new LinearLayout(context);
        tabs.addView(action(saved?"Недавние":"Сохранённые",saved?"Recent clipboard":"Saved phrases",toggleSaved),new LinearLayout.LayoutParams(0,dp(34),1));
        tabs.addView(action("Шаблоны","Manage templates",manage),new LinearLayout.LayoutParams(0,dp(34),1));
        root.addView(tabs);


        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(6), 0, 0);
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
        if (recentItems.isEmpty()) {
            TextView empty = label(emptyMessage, 16);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(14), dp(18), dp(14));
            grid.addView(empty, new LinearLayout.LayoutParams(-1, dp(100)));
        }
        for (int i = 0; i < recentItems.size(); i += 2) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + 2, recentItems.size()); j++) {
                final String item = recentItems.get(j);
                TextView v = label(item, 15);
                v.setGravity(Gravity.TOP | Gravity.START);
                v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
                v.setMaxLines(3);
                v.setEllipsize(TextUtils.TruncateAt.END);
                v.setPadding(dp(10),dp(8),dp(10),dp(8));
                v.setFocusable(true);
                v.setClickable(true);
                card(v, palette.key);
                v.setOnClickListener(ignored -> select.accept(item));
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(74), 1);
                size.setMargins(dp(3), dp(3), dp(3), dp(3));
                LinearLayout cell=new LinearLayout(context);cell.setOrientation(LinearLayout.VERTICAL);
                cell.addView(v,new LinearLayout.LayoutParams(-1,dp(62)));
                LinearLayout actions=new LinearLayout(context);
                if(!saved)actions.addView(action("★","Закрепить",()->pin.accept(item)),new LinearLayout.LayoutParams(0,dp(29),1));
                actions.addView(action("×","Удалить элемент",()->remove.accept(item)),new LinearLayout.LayoutParams(0,dp(29),1));
                cell.addView(actions);
                size.height=-2;row.addView(cell,size);
            }
            if (i + 1 == recentItems.size()) row.addView(new View(context),
                    new LinearLayout.LayoutParams(0, dp(74), 1));
            grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(Math.max(76, listHeightDp-34))));
        return root;
    }
}
