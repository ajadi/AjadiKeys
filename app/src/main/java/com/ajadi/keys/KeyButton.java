package com.ajadi.keys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.TextView;

/** A key label with separately drawn long-press hint and optional globe icon. */
final class KeyButton extends TextView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path iconPath = new Path();
    private String hint = "";
    private int hintColor;
    private boolean cornerMic,cornerGear,cornerGlobe;
    private int cornerMicColor,cornerGearColor,cornerGlobeColor;
    enum Icon { NONE, GLOBE, SHIFT, SHIFT_ON, CAPS, RETURN, SEARCH, NEXT, SEND, DONE, MIC, EMOJI, CLIPBOARD, TRANSLATE, COMPUTER }
    private Icon icon=Icon.NONE;

    KeyButton(Context context) {
        super(context);
    }

    void setKeyHint(String hint, int color) {
        this.hint = hint == null ? "" : hint;
        hintColor = color;
        invalidate();
    }
    void setCornerGear(boolean enabled,int color){cornerGear=enabled;cornerGearColor=color;invalidate();}
    void setCornerMic(boolean enabled,int color){cornerMic=enabled;cornerMicColor=color;invalidate();}
    void setCornerGlobe(boolean enabled,int color){cornerGlobe=enabled;cornerGlobeColor=color;invalidate();}
    void setGlobeIcon(boolean enabled) {
        setIcon(enabled ? Icon.GLOBE : Icon.NONE);
    }

    void setIcon(Icon icon) {
        this.icon=icon;
        invalidate();
    }

    @Override public boolean performClick() { return super.performClick(); }

    @Override protected void onDraw(Canvas canvas) {
        // TextView clips/translates its text canvas. Restore our full key bounds
        // before drawing an independent corner hint or glyph.
        int saved = canvas.save();
        if (icon == Icon.NONE) super.onDraw(canvas);
        canvas.restoreToCount(saved);
        float d = getResources().getDisplayMetrics().density;
        float left = getScrollX(), top = getScrollY();
        if (icon == Icon.GLOBE) {
            float cx = left + getWidth()/2f, cy = top + getHeight()/2f, r = 10.5f*d;
            paint.setColor(getCurrentTextColor());
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.75f*d);
            canvas.drawCircle(cx,cy,r,paint);
            canvas.drawOval(cx-r*.48f,cy-r,cx+r*.48f,cy+r,paint);
            canvas.drawLine(cx-r,cy,cx+r,cy,paint);
            canvas.drawLine(cx-r*.75f,cy-r*.52f,cx+r*.75f,cy-r*.52f,paint);
            canvas.drawLine(cx-r*.75f,cy+r*.52f,cx+r*.75f,cy+r*.52f,paint);
        }
        else if (icon != Icon.NONE) {
            int state = canvas.save();
            canvas.translate(left+getWidth()/2f, top+getHeight()/2f);
            float scale=Math.min(1f,Math.min(getWidth(),getHeight())/(32f*d))*d;
            canvas.scale(scale,scale);
            paint.setColor(getCurrentTextColor());
            paint.setStrokeWidth(2.3f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            Path path=iconPath;
            path.rewind();
            switch (icon) {
                case COMPUTER:
                    canvas.drawRoundRect(-13,-7,13,8,2,2,paint);
                    for(int y=-3;y<=1;y+=4)for(int x=-8;x<=8;x+=4)canvas.drawPoint(x,y,paint);
                    canvas.drawLine(-5,5,5,5,paint);break;
                case SHIFT: case SHIFT_ON: case CAPS:
                    path.moveTo(0,-12); path.lineTo(11,-1); path.lineTo(6,-1);
                    path.lineTo(6,9); path.lineTo(-6,9); path.lineTo(-6,-1); path.lineTo(-11,-1); path.close();
                    if(icon!=Icon.SHIFT) paint.setStyle(Paint.Style.FILL);
                    canvas.drawPath(path,paint);
                    if(icon==Icon.CAPS) { paint.setStyle(Paint.Style.STROKE); canvas.drawLine(-6,12,6,12,paint); }
                    break;
                case RETURN:
                    path.moveTo(11,-9); path.lineTo(11,2); path.lineTo(-11,2);
                    path.moveTo(-4,-5); path.lineTo(-11,2); path.lineTo(-4,9);
                    canvas.drawPath(path,paint); break;
                case SEARCH:
                    canvas.drawCircle(-2,-2,7,paint); canvas.drawLine(3,3,10,10,paint); break;
                case NEXT:
                    path.moveTo(-11,0); path.lineTo(9,0); path.moveTo(2,-7); path.lineTo(9,0); path.lineTo(2,7);
                    canvas.drawPath(path,paint); break;
                case SEND:
                    path.moveTo(-11,0); path.lineTo(0,-10); path.lineTo(11,0); path.moveTo(0,-10); path.lineTo(0,12);
                    canvas.drawPath(path,paint); break;
                case CLIPBOARD:
                    canvas.drawRoundRect(-9,-9,9,12,2,2,paint);
                    canvas.drawRoundRect(-4,-12,4,-6,2,2,paint);
                    canvas.drawLine(-5,-2,5,-2,paint);canvas.drawLine(-5,3,5,3,paint);
                    canvas.drawLine(-5,8,2,8,paint);break;
                case TRANSLATE:
                    // Single square translation icon: overlapping language cards, original vector strokes.
                    paint.setStrokeWidth(1.8f);
                    path.moveTo(3,-5);path.lineTo(3,-10);path.quadTo(3,-13,0,-13);
                    path.lineTo(-10,-13);path.quadTo(-13,-13,-13,-10);path.lineTo(-13,4);
                    path.quadTo(-13,7,-10,7);path.lineTo(-3,7);
                    canvas.drawPath(path,paint);path.rewind();
                    canvas.drawRoundRect(-3,-5,13,13,2.2f,2.2f,paint);
                    path.moveTo(-10,-1);path.lineTo(-6.5f,-9.5f);path.lineTo(-3,-1);
                    path.moveTo(-8.5f,-4.5f);path.lineTo(-4.5f,-4.5f);
                    path.moveTo(4,-2);path.lineTo(5,0);path.moveTo(0,2);path.lineTo(10,2);
                    path.moveTo(2,2);path.quadTo(4,7,10,10);path.moveTo(8,2);path.quadTo(6,7,0,10);
                    canvas.drawPath(path,paint);break;
                case EMOJI:
                    canvas.drawCircle(0,0,11.2f,paint);paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(-4,-3,1.5f,paint);canvas.drawCircle(4,-3,1.5f,paint);paint.setStyle(Paint.Style.STROKE);
                    canvas.drawArc(-6,-2,6,7,8,164,false,paint);break;
                case MIC:
                    canvas.drawRoundRect(-4,-12,4,3,4,4,paint);
                    canvas.drawArc(-8,-5,8,9,0,180,false,paint);canvas.drawLine(0,9,0,13,paint);canvas.drawLine(-4,13,4,13,paint);break;
                case DONE:
                    path.moveTo(-10,0); path.lineTo(-3,7); path.lineTo(11,-8); canvas.drawPath(path,paint); break;
                default: break;
            }
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStrokeJoin(Paint.Join.MITER);
            canvas.restoreToCount(state);
        }
        if(cornerGlobe){
            float cx=left+getWidth()-7*d,cy=top+7*d,r=4.5f*d;
            paint.setColor(cornerGlobeColor);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);
            canvas.drawCircle(cx,cy,r,paint);canvas.drawOval(cx-r*.46f,cy-r,cx+r*.46f,cy+r,paint);
            canvas.drawLine(cx-r,cy,cx+r,cy,paint);
        }
        if(cornerGear){
            int state=canvas.save();
            canvas.translate(left+getWidth()-9*d,top+9*d);
            canvas.scale(.5f*d,.5f*d);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2f);paint.setColor(cornerGearColor);
            Path gear=iconPath;gear.rewind();
            for(int i=0;i<32;i++){
                double angle=2*Math.PI*i/32;
                float radius=(i%4==0||i%4==3)?8f:11f;
                float x=(float)Math.cos(angle)*radius,y=(float)Math.sin(angle)*radius;
                if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);
            }
            gear.close();canvas.drawPath(gear,paint);canvas.drawCircle(0,0,3.2f,paint);
            canvas.restoreToCount(state);
        }
        if(cornerMic){
            int state=canvas.save();
            canvas.translate(left+getWidth()-10*d,top+10*d);
            canvas.scale(.55f*d,.55f*d);
            paint.setColor(cornerMicColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.3f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawRoundRect(-4,-12,4,3,4,4,paint);
            canvas.drawArc(-8,-5,8,9,0,180,false,paint);
            canvas.drawLine(0,9,0,13,paint);canvas.drawLine(-4,13,4,13,paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            canvas.restoreToCount(state);
        }
        if (!hint.isEmpty()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(10f*d);
            paint.setColor(hintColor);
            canvas.drawText(hint,left+getWidth()-4*d,top+2*d-paint.ascent(),paint);
        }
    }
}
