package com.ajadi.keys;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Local emoji picker: Unicode text, no network, no tracking or persisted selection history. */
final class EmojiPanel {
    private static final String[][] GROUPS={
        {"\ud83d\ude00","Лица","\ud83d\ude00 \ud83d\ude03 \ud83d\ude04 \ud83d\ude01 \ud83d\ude06 \ud83d\ude05 \ud83d\ude02 \ud83e\udd23 \ud83d\ude42 \ud83d\ude43 \ud83d\ude09 \ud83d\ude0a \ud83d\ude07 \ud83e\udd70 \ud83d\ude0d \ud83e\udd29 \ud83d\ude18 \ud83d\ude17 \ud83d\ude1a \ud83d\ude19 \ud83d\ude0b \ud83d\ude1b \ud83d\ude1c \ud83e\udd2a \ud83d\ude1d \ud83e\udd11 \ud83e\udd17 \ud83e\udd2d \ud83e\udd2b \ud83e\udd14 \ud83e\udee1 \ud83e\udd10 \ud83e\udd28 \ud83d\ude10 \ud83d\ude11 \ud83d\ude36 \ud83d\ude0f \ud83d\ude12 \ud83d\ude44 \ud83d\ude2c \ud83d\ude2e‍\ud83d\udca8 \ud83e\udd25 \ud83d\ude0c \ud83d\ude14 \ud83d\ude2a \ud83e\udd24 \ud83d\ude34 \ud83d\ude37 \ud83e\udd12 \ud83e\udd15 \ud83e\udd22 \ud83e\udd2e \ud83e\udd27 \ud83e\udd75 \ud83e\udd76 \ud83e\udd74 \ud83d\ude35 \ud83e\udd2f \ud83d\ude33 \ud83e\udd7a \ud83d\ude22 \ud83d\ude2d \ud83d\ude24 \ud83d\ude20 \ud83d\ude21 \ud83e\udd2c \ud83d\ude31 \ud83d\ude28 \ud83d\ude30 \ud83d\ude25 \ud83d\ude13 \ud83e\udd13 \ud83d\ude0e \ud83e\uddd0 \ud83e\udd20 \ud83e\udd73 \ud83d\ude08 \ud83d\udc7b \ud83d\udc80"},
        {"\ud83d\udc4d","Жесты","\ud83d\udc4d \ud83d\udc4e \ud83d\udc4c \ud83e\udd0c \ud83e\udd0f ✌️ \ud83e\udd1e \ud83e\udef0 \ud83e\udd1f \ud83e\udd18 \ud83e\udd19 \ud83d\udc48 \ud83d\udc49 \ud83d\udc46 \ud83d\udc47 ☝️ ✋ \ud83e\udd1a \ud83d\udd90️ \ud83d\udc4b \ud83e\udd1d \ud83d\ude4c \ud83d\udc4f \ud83d\udcaa \ud83d\ude4f \ud83e\udef6 \ud83d\udc4a ✊ \ud83e\udd1b \ud83e\udd1c \ud83e\udd32 \ud83e\udef4 \ud83e\udef3 \ud83e\udd37 \ud83e\udd26 \ud83d\ude4b \ud83d\ude46 \ud83d\ude45 \ud83d\ude47 \ud83e\uddd1 \ud83d\udc68 \ud83d\udc69 \ud83d\udc76 \ud83d\udc74 \ud83d\udc75 \ud83e\uddd4 \ud83e\uddd1‍\ud83d\udcbb \ud83d\udc68‍\ud83c\udf73 \ud83d\udc69‍⚕️ \ud83d\udd7a \ud83d\udc83 \ud83c\udfc3 \ud83d\udeb6 \ud83e\udec2"},
        {"❤️","Сердца","❤️ \ud83e\ude77 \ud83e\udde1 \ud83d\udc9b \ud83d\udc9a \ud83d\udc99 \ud83e\ude75 \ud83d\udc9c \ud83e\udd0e \ud83d\udda4 \ud83e\ude76 \ud83e\udd0d \ud83d\udc94 ❤️‍\ud83d\udd25 ❤️‍\ud83e\ude79 \ud83d\udc95 \ud83d\udc9e \ud83d\udc93 \ud83d\udc97 \ud83d\udc96 \ud83d\udc98 \ud83d\udc9d \ud83d\udc9f ♥️ ❣️ \ud83d\udc8b \ud83e\udef0 \ud83d\udc8c \ud83d\udc90 \ud83c\udf39 \ud83d\udd25 ✨ ⭐ \ud83c\udf1f \ud83d\udcab \ud83d\udca5 \ud83c\udf89 \ud83c\udf8a \ud83c\udf81 \ud83c\udf88 \ud83c\udfc6 \ud83e\udd47"},
        {"\ud83d\udc31","Природа","\ud83d\udc36 \ud83d\udc31 \ud83d\udc2d \ud83d\udc39 \ud83d\udc30 \ud83e\udd8a \ud83d\udc3b \ud83d\udc3c \ud83d\udc28 \ud83d\udc2f \ud83e\udd81 \ud83d\udc2e \ud83d\udc37 \ud83d\udc38 \ud83d\udc35 \ud83d\ude48 \ud83d\ude49 \ud83d\ude4a \ud83d\udc14 \ud83d\udc27 \ud83d\udc26 \ud83d\udc24 \ud83e\udd86 \ud83e\udd89 \ud83e\udd8b \ud83d\udc1d \ud83d\udc1e \ud83d\udc22 \ud83d\udc0d \ud83e\udd8e \ud83d\udc19 \ud83d\udc20 \ud83d\udc2c \ud83d\udc33 \ud83e\udd88 \ud83d\udc18 \ud83e\udd92 \ud83d\udc15 \ud83d\udc08 \ud83d\udc3e \ud83c\udf35 \ud83c\udf32 \ud83c\udf33 \ud83c\udf34 \ud83c\udf3f ☘️ \ud83c\udf40 \ud83c\udf37 \ud83c\udf38 \ud83c\udf3c \ud83c\udf3b \ud83c\udf43 \ud83c\udf42 \ud83c\udf08 ☀️ \ud83c\udf24️ ☁️ \ud83c\udf27️ ⛈️ ❄️ \ud83c\udf19 \ud83c\udf0d"},
        {"\ud83c\udf55","Еда","\ud83c\udf4f \ud83c\udf4e \ud83c\udf50 \ud83c\udf4a \ud83c\udf4b \ud83c\udf4c \ud83c\udf49 \ud83c\udf47 \ud83c\udf53 \ud83e\uded0 \ud83c\udf52 \ud83c\udf51 \ud83e\udd6d \ud83c\udf4d \ud83e\udd51 \ud83c\udf45 \ud83e\udd55 \ud83c\udf3d \ud83e\udd66 \ud83e\uddc4 \ud83e\uddc5 \ud83e\udd50 \ud83c\udf5e \ud83e\udd56 \ud83e\uddc0 \ud83e\udd5a \ud83c\udf73 \ud83e\udd53 \ud83e\udd69 \ud83c\udf57 \ud83c\udf54 \ud83c\udf5f \ud83c\udf55 \ud83c\udf2d \ud83e\udd6a \ud83c\udf2e \ud83c\udf2f \ud83c\udf5d \ud83c\udf5c \ud83c\udf72 \ud83c\udf63 \ud83c\udf71 \ud83e\udd5f \ud83c\udf64 \ud83c\udf5a \ud83c\udf66 \ud83c\udf70 \ud83c\udf82 \ud83c\udf6b \ud83c\udf6a \ud83c\udf69 \ud83c\udf6c \ud83c\udf7f ☕ \ud83e\uded6 \ud83c\udf75 \ud83e\udd64 \ud83e\uddc3 \ud83c\udf7a \ud83c\udf7b \ud83c\udf77 \ud83c\udf78 \ud83c\udf79 \ud83e\udd42"},
        {"\ud83d\ude97","Места и занятия","\ud83d\ude97 \ud83d\ude95 \ud83d\ude8c \ud83d\ude8e \ud83c\udfce️ \ud83d\ude93 \ud83d\ude91 \ud83d\ude92 \ud83d\ude9a \ud83d\udeb2 \ud83d\udef4 \ud83c\udfcd️ \ud83d\ude86 \ud83d\ude87 ✈️ \ud83d\udeeb \ud83d\udeec \ud83d\ude81 \ud83d\ude80 \ud83d\udea2 ⛵ \ud83d\uddfa️ \ud83c\udfe0 \ud83c\udfe1 \ud83c\udfe2 \ud83c\udfe5 \ud83c\udfe8 \ud83c\udfd6️ \ud83c\udfdd️ \ud83c\udfd4️ \ud83c\udfd5️ \ud83d\uddfd \ud83d\uddfc ⛪ \ud83d\udd4c \ud83d\udd4d ⚽ \ud83c\udfc0 \ud83c\udfc8 ⚾ \ud83c\udfbe \ud83c\udfd0 \ud83e\udd4e \ud83c\udfd3 \ud83c\udff8 \ud83e\udd4a \ud83c\udfaf \ud83c\udfae \ud83c\udfa7 \ud83c\udfb8 \ud83c\udfb9 \ud83c\udfa4 \ud83c\udfac \ud83c\udfa8 \ud83d\udcf7 \ud83e\udde9 \ud83c\udfb2 \ud83c\udfcb️ \ud83d\udeb4 \ud83c\udfca"},
        {"\ud83d\udd23","Символы","✅ ❌ ❎ ✔️ ☑️ ⚠️ \ud83d\udeab ❗ ❕ ❓ ❔ \ud83d\udcaf \ud83d\udd14 \ud83d\udd15 \ud83d\udccc \ud83d\udccd \ud83d\udd17 \ud83d\udd12 \ud83d\udd13 \ud83d\udd11 \ud83d\udca1 \ud83d\udd26 \ud83d\udcbb \ud83d\udcf1 ⌚ \ud83d\udcde ✉️ \ud83d\udce8 \ud83d\udce9 \ud83d\udcc5 ⏰ ⏳ \ud83d\udd0b \ud83d\udcb0 \ud83d\udcb3 \ud83e\uddfe \ud83d\uded2 \ud83e\uddf0 \ud83e\uddf9 \ud83e\uddfa \ud83e\uddf4 \ud83d\udc8a \ud83e\ude79 \ud83e\uddea \ud83e\uddf2 ♻️ ♠️ ♣️ ♥️ ♦️ ➕ ➖ ➗ ✖️ ➡️ ⬅️ ⬆️ ⬇️ ↔️ \ud83d\udd04 \ud83d\udd01 \ud83d\udd25 \ud83c\udd97 \ud83c\udd95 \ud83c\udd98 \ud83c\uddee\ud83c\uddf1 \ud83c\uddf7\ud83c\uddfa \ud83c\uddfa\ud83c\uddf8 \ud83c\uddec\ud83c\udde7 \ud83c\udf10"}
    };
    private final Context context;
    private final KeyboardPalette palette;
    EmojiPanel(Context context,KeyboardPalette palette){this.context=context;this.palette=palette;}
    private int dp(float n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private void style(TextView v,int color){
        GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(8));v.setBackground(bg);
        v.setTextColor(palette.text);v.setGravity(Gravity.CENTER);
    }
    private TextView button(String value,String desc,int size,int color,Runnable onClick){
        TextView v=new TextView(context);v.setText(value);v.setTextSize(size);v.setSingleLine(true);style(v,color);
        v.setContentDescription(desc);v.setFocusable(true);v.setClickable(true);v.setOnClickListener(w->onClick.run());return v;
    }
    private void add(LinearLayout row,View v,float weight,int height){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(height),weight);p.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(v,p);
    }
    LinearLayout create(int category,int gridHeight,String title,Runnable close,Runnable erase,IntConsumer chooseGroup,Consumer<String> insert,Runnable search,java.util.function.Function<java.util.List<String>,java.util.List<String>> rank){
        int group=Math.max(0,Math.min(GROUPS.length-1,category));
        LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);
        add(header,button("←","Back to keyboard",18,palette.action,close),1,34);
        TextView heading=new TextView(context);heading.setText(title);heading.setTextSize(16);heading.setTextColor(palette.text);heading.setGravity(Gravity.CENTER);
        add(header,heading,3,34);
        add(header,button("⌕","Поиск эмодзи",22,palette.action,search),1,34);
        add(header,button("\u232b","Delete one character",17,palette.action,erase),1,34);root.addView(header);
        LinearLayout tabs=new LinearLayout(context);
        for(int i=0;i<GROUPS.length;i++){
            final int index=i;boolean chosen=i==group;
            TextView tab=button(GROUPS[i][0],GROUPS[i][1],20,chosen?palette.accent:palette.key,()->chooseGroup.accept(index));
            if(chosen)tab.setSelected(true);add(tabs,tab,1,33);
        }
        root.addView(tabs);
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(true);
        LinearLayout grid=new LinearLayout(context);grid.setOrientation(LinearLayout.VERTICAL);
        String[] emojis=rank.apply(java.util.Arrays.asList(GROUPS[group][2].split(" "))).toArray(new String[0]);
        for(int i=0;i<emojis.length;i+=8){
            LinearLayout row=new LinearLayout(context);
            for(int j=0;j<8;j++){
                if(i+j<emojis.length){String emoji=emojis[i+j];add(row,button(emoji,emoji,25,palette.key,()->insert.accept(emoji)),1,40);}
                else add(row,new View(context),1,40);
            }
            grid.addView(row);
        }
        scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(gridHeight)));
        return root;
    }
}
