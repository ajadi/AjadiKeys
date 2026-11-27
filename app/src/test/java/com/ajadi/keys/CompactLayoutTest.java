package com.ajadi.keys;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompactLayoutTest {
    @Test public void defaultFullWidthInAnyOrientation(){
        assertEquals(900,CompactLayout.width(900,320,55,false,true));
        assertEquals(400,CompactLayout.width(400,320,55,false,false));
    }
    @Test public void portraitNeverCompacts(){
        assertEquals(400,CompactLayout.width(400,320,40,true,false));
    }
    @Test public void landscapeUsesConfiguredPercentage(){
        assertEquals(495,CompactLayout.width(900,320,55,true,true));
        assertEquals(360,CompactLayout.width(900,320,40,true,true));
        assertEquals(630,CompactLayout.width(900,320,70,true,true));
    }
    @Test public void narrowWindowsPreserveUsableKeys(){
        assertEquals(320,CompactLayout.width(600,320,40,true,true));
        assertEquals(270,CompactLayout.width(270,320,40,true,true));
        assertEquals(0,CompactLayout.width(0,320,55,true,true));
    }
    @Test public void invalidStoredPercentClamped(){
        assertEquals(400,CompactLayout.width(1000,320,-1,true,true));
        assertEquals(700,CompactLayout.width(1000,320,99,true,true));
    }
}
