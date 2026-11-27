package com.ajadi.keys;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.view.inputmethod.InlineSuggestionsResponse;
import android.widget.LinearLayout;
import android.widget.inline.InlineContentView;
import android.widget.inline.InlinePresentationSpec;
import androidx.autofill.inline.UiVersions;
import androidx.autofill.inline.v1.InlineSuggestionUi;
import java.util.Collections;
import java.util.List;

/** Android-owned inline presentation surfaces; no dataset values are read or stored by the IME. */
@TargetApi(Build.VERSION_CODES.R)
final class SystemInlineAutofill {
    private final Context context;
    private long generation;
    private SuggestionStrip strip;
    SystemInlineAutofill(Context context) { this.context=context; }
    private int dp(float n) { return Math.round(n*context.getResources().getDisplayMetrics().density); }
    InlineSuggestionsRequest request() {
        InlinePresentationSpec spec=new InlinePresentationSpec.Builder(new Size(dp(44),dp(32)),new Size(dp(320),dp(38)))
                .setStyle(UiVersions.newStylesBuilder().addStyle(InlineSuggestionUi.newStyleBuilder().build()).build())
                .build();
        return new InlineSuggestionsRequest.Builder(Collections.singletonList(spec)).setMaxSuggestionCount(5).build();
    }
    void receive(InlineSuggestionsResponse response,Runnable show) {
        clear();long current=generation;
        List<InlineSuggestion> suggestions=response.getInlineSuggestions();
        int count=Math.min(5,suggestions.size());
        if(count==0){show.run();return;}
        InlineContentView[] views=new InlineContentView[count];int[] completed={0};
        for(int i=0;i<count;i++) {
            final int index=i;
            suggestions.get(i).inflate(context,new Size(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT),
                    context.getMainExecutor(),view->{
                        if(current!=generation)return;
                        views[index]=view;
                        if(++completed[0]==count) {
                            SuggestionStrip result=new SuggestionStrip(context);
                            result.setFillViewport(false);
                            LinearLayout content=new LinearLayout(context);content.setOrientation(LinearLayout.HORIZONTAL);
                            content.setGravity(android.view.Gravity.CENTER_VERTICAL);
                            result.addView(content,new android.widget.FrameLayout.LayoutParams(-2,-2));
                            for(InlineContentView item:views) if(item!=null) {
                                // InlineSuggestion.inflate supplies the remote surface's actual dimensions.
                                // Replacing them with WRAP_CONTENT can collapse an opaque host view to width zero.
                                ViewGroup.LayoutParams actual=item.getLayoutParams();
                                if(actual==null||actual.width<=0||actual.height<=0)continue;
                                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(actual.width,actual.height);
                                params.setMargins(dp(2),dp(1),dp(2),dp(1));content.addView(item,params);
                            }
                            strip=content.getChildCount()>0?result:null;show.run();
                        }
                    });
        }
    }
    void attach(LinearLayout root) {
        if(strip==null||root==null)return;
        if(strip.getParent()==root)return;
        detach();root.addView(strip,0,new LinearLayout.LayoutParams(-1,dp(40)));
    }
    void detach() {
        if(strip!=null&&strip.getParent() instanceof ViewGroup)((ViewGroup)strip.getParent()).removeView(strip);
    }
    void clear() {generation++;detach();strip=null;}
}
