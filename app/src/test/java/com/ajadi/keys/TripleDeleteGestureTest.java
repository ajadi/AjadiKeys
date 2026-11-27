package com.ajadi.keys;

import org.junit.Test;
import static org.junit.Assert.*;

public class TripleDeleteGestureTest {
    private final Object editor=new Object(),connection=new Object();
    private boolean tap(TripleDeleteGesture g,long time,long seq,int caret){
        return g.tap(time-20,time,seq,editor,connection,false,caret,caret);
    }
    @Test public void threeFastTapsClearOnceButTwoDoNot(){
        TripleDeleteGesture g=new TripleDeleteGesture();
        assertFalse(tap(g,100,1,20));assertFalse(tap(g,220,2,19));
        assertTrue(tap(g,340,3,18));assertFalse(tap(g,450,4,0));
    }
    @Test public void slowInterruptedOrMovedTapsDoNotClear(){
        TripleDeleteGesture g=new TripleDeleteGesture();
        assertFalse(tap(g,100,1,20));assertFalse(tap(g,500,2,19));assertFalse(tap(g,900,3,18));
        g.clear();assertFalse(tap(g,100,1,30));assertFalse(tap(g,200,2,29));assertFalse(tap(g,300,4,28));
        g.clear();assertFalse(tap(g,100,1,30));assertFalse(tap(g,200,2,29));assertFalse(tap(g,300,3,10));
    }
    @Test public void holdingOrChangingEditorsBreaksSequence(){
        TripleDeleteGesture g=new TripleDeleteGesture();
        assertFalse(tap(g,100,1,20));assertFalse(tap(g,200,2,19));
        assertFalse(g.tap(230,450,3,editor,connection,false,18,18));
        assertFalse(tap(g,500,4,17));assertFalse(tap(g,600,5,16));
        assertFalse(g.tap(660,680,6,new Object(),connection,false,15,15));
    }
    @Test public void survivesUnicodeDeletionAndSelectedRange(){
        TripleDeleteGesture g=new TripleDeleteGesture();
        assertFalse(g.tap(80,100,1,editor,connection,false,6,10));
        assertFalse(tap(g,200,2,6));assertTrue(tap(g,300,3,4));
    }
}
