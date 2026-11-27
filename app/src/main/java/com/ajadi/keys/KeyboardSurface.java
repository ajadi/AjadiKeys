package com.ajadi.keys;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Intercepts deliberate horizontal gestures before a child key can commit a tap. */
// Constructed programmatically with the IME's gesture callback, never from XML.
@SuppressLint("ViewConstructor")
final class KeyboardSurface extends LinearLayout {
    private final BooleanSupplier swipeAllowed;
    private final IntConsumer switchLanguage;
    private final Runnable touchStarted;
    private final float threshold;
    private float downX, downY;
    private boolean swiping;
    private int direction;

    KeyboardSurface(Context context, BooleanSupplier swipeAllowed, IntConsumer switchLanguage,Runnable touchStarted) {
        super(context);
        this.swipeAllowed = swipeAllowed;
        this.switchLanguage = switchLanguage;
        this.touchStarted=touchStarted;
        threshold = 42f * getResources().getDisplayMetrics().density;
    }

    private boolean detect(MotionEvent event) {
        float dx = event.getX() - downX, dy = event.getY() - downY;
        if (swipeAllowed.getAsBoolean() && event.getPointerCount() == 1
                && Math.abs(dx) >= threshold && Math.abs(dx) > Math.abs(dy) * 1.5f) {
            direction = dx > 0 ? 1 : -1;
            swiping = true;
            return true;
        }
        return false;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStarted.run();
                downX = event.getX(); downY = event.getY(); swiping = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                return swiping || detect(event);
            case MotionEvent.ACTION_CANCEL:
                swiping = false;
                return false;
            default:
                return false;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(); downY = event.getY(); swiping = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!swiping) detect(event);
                return true;
            case MotionEvent.ACTION_UP:
                if (swiping || detect(event)) {
                    int completedDirection = direction;
                    swiping = false;
                    switchLanguage.accept(completedDirection);
                } else {
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                swiping = false;
                return true;
            default:
                return true;
        }
    }
}
