package com.ajadi.keys;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Clearly visible capture state, local preview and explicit insert. */
final class VoicePanel {
    private final Context context;
    private final KeyboardPalette palette;
    private final boolean russian;
    private final LinearLayout root;
    private final TextView language,action,status,preview,insert;
    VoicePanel(Context context,KeyboardPalette palette,boolean russian,Runnable close,Runnable chooseLang,Runnable record,Runnable apply){
        this.context=context;this.palette=palette;this.russian=russian;
        root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout top=row();
        button(top,russian?"Отмена":"Cancel",1.0f,close,false);
        language=button(top,"AUTO",.75f,chooseLang,false);
        action=button(top,russian?"Запись":"Record",1.3f,record,true);
        status=new TextView(context);status.setTextColor(palette.text);status.setTextSize(15);
        status.setGravity(Gravity.CENTER_VERTICAL);status.setPadding(dp(9),dp(5),dp(9),dp(5));
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(true);background(scroll,palette.key);
        boolean landscape=context.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout.LayoutParams box=new LinearLayout.LayoutParams(-1,dp(landscape?55:105));box.setMargins(dp(2),dp(3),dp(2),dp(3));root.addView(scroll,box);
        preview=new TextView(context);preview.setTextSize(18);preview.setTextColor(palette.text);preview.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        preview.setGravity(Gravity.TOP|Gravity.START);preview.setPadding(dp(10),dp(8),dp(10),dp(8));
        scroll.addView(preview,new ScrollView.LayoutParams(-1,-2));
        LinearLayout footer=row();
        insert=button(footer,russian?"Вставить":"Insert recognized text",1f,apply,true);
    }
    LinearLayout view(){return root;}
    private int dp(float n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private LinearLayout row(){LinearLayout v=new LinearLayout(context);v.setGravity(Gravity.CENTER_VERTICAL);root.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    private void background(View v,int color){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(8));v.setBackground(b);}
    private TextView button(LinearLayout row,String label,float weight,Runnable callback,boolean accent){
        TextView v=new TextView(context);v.setText(label);v.setGravity(Gravity.CENTER);v.setTextSize(15);v.setSingleLine(true);
        v.setTextColor(accent?palette.accentText:palette.text);background(v,accent?palette.accent:palette.action);
        v.setFocusable(true);v.setOnClickListener(w->callback.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),weight);lp.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(v,lp);return v;
    }
    void update(String lang,String message,String result,boolean recording,boolean busy,boolean ready){
        String label=lang.toUpperCase(java.util.Locale.ROOT);
        language.setText(label);language.setContentDescription("Speech language "+label);
        status.setText(message);
        preview.setText(result.isEmpty()?(russian?"Здесь появится распознанный текст":"Recognized text will appear here"):result);
        preview.setTextColor(result.isEmpty()?palette.hint:palette.text);
        action.setText(recording?(russian?"Стоп":"Stop"):!ready?(russian?"Настройка":"Setup"):(russian?"Запись":"Record"));
        action.setEnabled(!busy);action.setAlpha(busy?.5f:1f);
        language.setEnabled(!busy);language.setAlpha(busy?.5f:1f);
        insert.setEnabled(!recording&&!busy&&!result.isEmpty());insert.setAlpha(insert.isEnabled()?1f:.45f);
    }
}
