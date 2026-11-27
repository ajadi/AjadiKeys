package com.ajadi.keys;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ClipboardStoreTest {
    @Test public void recentOrderDedupeExpiryAndClear() {
        ClipboardHistory.Store s=new ClipboardHistory.Store();
        s.remember("synthetic A",1); s.remember("synthetic B",2);
        assertEquals(Arrays.asList("synthetic B","synthetic A"),s.recent(2));
        s.remember("synthetic A",3);
        assertEquals(Arrays.asList("synthetic A","synthetic B"),s.recent(3));
        assertEquals(Arrays.asList("synthetic A"),s.recent(2+ClipboardHistory.MAX_AGE_MS));
        s.clear(); assertTrue(s.recent(3).isEmpty());
    }
    @Test public void boundedItemCountAndSize() {
        ClipboardHistory.Store s=new ClipboardHistory.Store();
        for(int i=0;i<30;i++)s.remember("synthetic "+i,i);
        assertEquals(20,s.recent(30).size());
        assertEquals("synthetic 29",s.recent(30).get(0));
        String item=new String(new char[ClipboardHistory.MAX_TOTAL_CHARS]).replace('\0','x');
        s.remember(item,31);
        assertEquals(Arrays.asList(item),s.recent(31));
        s.remember(item+"y",32);
        assertEquals(Arrays.asList(item),s.recent(32));
    }
}
