package com.ajadi.keys;

import org.junit.Test;
import static org.junit.Assert.*;

public class BackspacePressTest {
    @Test public void thresholdAndOneShot(){
        BackspacePress p=new BackspacePress();p.begin(100);
        assertFalse(p.repeat(449));assertTrue(p.repeat(450));
        assertTrue(p.repeat(2099));assertFalse(p.clear(2099));
        assertFalse(p.repeat(2100));assertTrue(p.clear(2100));
        assertFalse(p.clear(2500));assertFalse(p.repeat(2500));assertFalse(p.release());
    }
    @Test public void successfulWordHoldNeverClearsField(){
        BackspacePress p=new BackspacePress();p.begin(100);
        assertTrue(p.canTryWord());p.wordDone();
        assertFalse(p.canTryWord());assertFalse(p.repeat(450));assertFalse(p.clear(2100));assertFalse(p.release());
    }
    @Test public void shortTapAndCancellation(){
        BackspacePress p=new BackspacePress();p.begin(100);assertTrue(p.release());
        assertFalse(p.clear(900));p.begin(200);p.cancel();assertFalse(p.repeat(550));
        assertFalse(p.clear(2300));assertFalse(p.release());
    }
    @Test public void configurableDelayAndInvalidPreference(){
        BackspacePress p=new BackspacePress();p.begin(100,1500);
        assertEquals(1500,p.clearDelay());
        assertTrue(p.repeat(1599));assertFalse(p.clear(1599));assertTrue(p.clear(1600));assertFalse(p.clear(1800));
        p.begin(100,2500);p.wordDone();assertFalse(p.clear(2700));
        p.begin(100,10);assertEquals(2000,p.clearDelay());assertFalse(p.clear(2099));assertTrue(p.clear(2100));
    }
    @Test public void tripleModeHoldRepeatsWithoutClearing(){
        BackspacePress p=new BackspacePress();p.begin(100,2000,false);
        assertTrue(p.repeat(450));assertTrue(p.repeat(2100));assertTrue(p.repeat(3500));
        assertFalse(p.clear(2100));assertFalse(p.clear(4000));assertFalse(p.release());
        p.begin(100,2000,false);p.wordDone();assertFalse(p.repeat(5000));assertFalse(p.clear(5000));
    }
}
