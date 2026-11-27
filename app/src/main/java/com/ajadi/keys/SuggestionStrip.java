package com.ajadi.keys;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

/** Scroll long suggestion labels independently of keyboard-wide language gestures. */
final class SuggestionStrip extends HorizontalScrollView {
    SuggestionStrip(Context context) {
        super(context);
        setFillViewport(true);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        android.view.ViewParent parent=getParent();
        if(parent!=null && event.getActionMasked()==MotionEvent.ACTION_DOWN)
            parent.requestDisallowInterceptTouchEvent(true);
        boolean handled=super.dispatchTouchEvent(event);
        if(parent!=null && (event.getActionMasked()==MotionEvent.ACTION_UP || event.getActionMasked()==MotionEvent.ACTION_CANCEL))
            parent.requestDisallowInterceptTouchEvent(false);
        return handled;
    }
}
