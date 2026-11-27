package com.ajadi.keys;

/** Per-pointer timing; a successful tap+hold word deletion consumes the entire press. */
final class BackspacePress {
    static final long REPEAT_MS=350;
    static final int DEFAULT_CLEAR_MS=2000;
    static final String CLEAR_PREF="clearFieldHoldMs";
    static final String MODE_PREF="clearFieldGesture", HOLD_MODE="hold", TRIPLE_MODE="triple";
    static final int[] CLEAR_OPTIONS_MS={800,1000,1500,2000,2500,3000,4000,5000};
    static int clearDelay(int saved){
        for(int value:CLEAR_OPTIONS_MS)if(saved==value)return value;
        return DEFAULT_CLEAR_MS;
    }
    private int clearMs=DEFAULT_CLEAR_MS;
    private boolean clearOnHold=true;
    private long down;
    private boolean active,acted,word,cleared;
    void begin(long time){begin(time,DEFAULT_CLEAR_MS);}
    void begin(long time,int delay){begin(time,delay,true);}
    void begin(long time,int delay,boolean clearOnHold){
        down=time;clearMs=clearDelay(delay);this.clearOnHold=clearOnHold;active=true;acted=word=cleared=false;
    }
    int clearDelay(){return clearMs;}
    boolean active(){return active;}
    long down(){return down;}
    boolean canTryWord(){return active&&!acted;}
    void wordDone(){if(canTryWord()){acted=word=true;}}
    boolean repeat(long now){
        if(!active||word||cleared||now-down<REPEAT_MS||clearOnHold&&now-down>=clearMs)return false;
        acted=true;return true;
    }
    boolean clear(long now){
        if(!clearOnHold||!active||word||cleared||now-down<clearMs)return false;
        acted=cleared=true;return true;
    }
    boolean release(){boolean tap=active&&!acted;cancel();return tap;}
    void cancel(){active=false;}
}
