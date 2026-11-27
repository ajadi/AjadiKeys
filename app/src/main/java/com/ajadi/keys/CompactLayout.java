package com.ajadi.keys;

/** Pure width policy, shared by settings, layout and unit tests. */
final class CompactLayout {
    static final int MIN_PERCENT=40,MAX_PERCENT=70,DEFAULT_PERCENT=55,MIN_WIDTH_DP=320;
    private CompactLayout(){}
    static int clampPercent(int value){return Math.max(MIN_PERCENT,Math.min(MAX_PERCENT,value));}
    static int width(int available,int minimum,int percent,boolean enabled,boolean landscape){
        if(available<=0)return 0;
        if(!enabled||!landscape)return available;
        int target=Math.round(available*(clampPercent(percent)/100f));
        return Math.min(available,Math.max(minimum,target));
    }
}
