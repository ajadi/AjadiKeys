package com.ajadi.keys;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/** Full IME background/insets with a measured, physically left/right anchored keyboard. */
final class CompactKeyboardFrame extends FrameLayout {
    private boolean enabled,right=true;
    private int percent=CompactLayout.DEFAULT_PERCENT;
    CompactKeyboardFrame(Context context){super(context);setLayoutDirection(View.LAYOUT_DIRECTION_LTR);}
    void configure(boolean compact,int widthPercent,boolean anchorRight){
        int next=CompactLayout.clampPercent(widthPercent);
        if(enabled==compact&&percent==next&&right==anchorRight)return;
        enabled=compact;percent=next;right=anchorRight;requestLayout();
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        if(getChildCount()>0){
            View child=getChildAt(0);
            LayoutParams lp=(LayoutParams)child.getLayoutParams();
            int available=Math.max(0,MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight());
            int min=Math.round(CompactLayout.MIN_WIDTH_DP*getResources().getDisplayMetrics().density);
            lp.width=enabled&&available>0?CompactLayout.width(available,min,percent,true,true):LayoutParams.MATCH_PARENT;
            // The frame is explicitly LTR, so START/END remain physical left/right even with RTL editors.
            lp.gravity=Gravity.BOTTOM|(enabled&&right?Gravity.END:Gravity.START);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}
