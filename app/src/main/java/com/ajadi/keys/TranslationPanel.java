package com.ajadi.keys;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A local draft and explicit preview/insert controls, above the normal letter keys. */
final class TranslationPanel {
    private final Context context;
    private final KeyboardPalette palette;
    private final LinearLayout root;
    private final TextView from,to,run,insert,draft,result,status;
    private final boolean russian;
    TranslationPanel(Context context,KeyboardPalette palette,boolean russian,Runnable close,Runnable source,Runnable target,
                     Runnable paste,Runnable translate,Runnable apply) {
        this.context=context;this.palette=palette;this.russian=russian;
        root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout controls=row();
        button(controls,"\u2190",.65f,close,false);
        from=button(controls,"RU",.85f,source,false);to=button(controls,"\u2192 EN",1.1f,target,false);
        button(controls,russian?"\u0412\u0441\u0442\u0430\u0432\u0438\u0442\u044c":"Paste",1.25f,paste,false);
        run=button(controls,russian?"\u041f\u0435\u0440\u0435\u0432\u0435\u0441\u0442\u0438":"Translate",1.7f,translate,true);
        draft=textBox();result=textBox();
        LinearLayout footer=row();status=new TextView(context);status.setTextColor(palette.text);status.setTextSize(12);status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(6),0,dp(4),0);footer.addView(status,new LinearLayout.LayoutParams(0,dp(35),3));
        insert=button(footer,russian?"\u0412\u0441\u0442\u0430\u0432\u0438\u0442\u044c":"Insert",1.4f,apply,true);
    }
    LinearLayout view(){return root;}
    private int dp(float n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private boolean landscape(){return context.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(context);v.setGravity(Gravity.CENTER_VERTICAL);root.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    private void background(View view,int color){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(8));view.setBackground(b);}
    private TextView button(LinearLayout row,String label,float weight,Runnable click,boolean accent){
        TextView v=new TextView(context);v.setText(label);v.setGravity(Gravity.CENTER);v.setSingleLine(true);v.setTextSize(13);
        v.setTextColor(accent?palette.accentText:palette.text);background(v,accent?palette.accent:palette.action);
        v.setContentDescription(label);v.setFocusable(true);v.setOnClickListener(w->click.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(32),weight);lp.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(v,lp);return v;
    }
    private TextView textBox(){
        ScrollView scroll=new ScrollView(context);background(scroll,palette.key);scroll.setFillViewport(true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(landscape()?30:46));lp.setMargins(dp(2),dp(2),dp(2),dp(2));root.addView(scroll,lp);
        TextView text=new TextView(context);text.setTextColor(palette.text);text.setTextSize(15);text.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        text.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);text.setPadding(dp(9),dp(4),dp(9),dp(4));scroll.addView(text,new ScrollView.LayoutParams(-1,-2));return text;
    }
    void update(KeyLayout.Language source,KeyLayout.Language target,String input,String output,String message,boolean busy,boolean installed){
        from.setText(source.name());from.setContentDescription("Source language "+source.name());to.setText("\u2192 "+target.name());to.setContentDescription("Target language "+target.name());
        draft.setText(input.isEmpty()?(russian?"\u041d\u0430\u0431\u0435\u0440\u0438\u0442\u0435 \u0442\u0435\u043a\u0441\u0442":"Type a local draft"):input);
        draft.setTextColor(input.isEmpty()?palette.hint:palette.text);
        result.setText(output.isEmpty()?(russian?"\u041f\u0440\u0435\u0434\u043f\u0440\u043e\u0441\u043c\u043e\u0442\u0440 \u043f\u0435\u0440\u0435\u0432\u043e\u0434\u0430":"Translation preview"):output);
        result.setTextColor(output.isEmpty()?palette.hint:palette.text);
        run.setText(installed?(russian?"\u041f\u0435\u0440\u0435\u0432\u0435\u0441\u0442\u0438":"Translate"):(russian?"\u041c\u043e\u0434\u0435\u043b\u0438":"Models"));
        run.setEnabled(!busy&&(!installed||!input.trim().isEmpty()));run.setAlpha(run.isEnabled()?1f:.5f);
        insert.setEnabled(!busy&&!output.isEmpty());insert.setAlpha(insert.isEnabled()?1f:.45f);
        status.setText(message);
    }
}
