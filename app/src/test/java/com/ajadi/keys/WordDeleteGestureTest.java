package com.ajadi.keys;
import org.junit.Test;
import static org.junit.Assert.*;

public class WordDeleteGestureTest {
    private void remaining(String text,int units){
        WordDeleteGesture.Plan p=WordDeleteGesture.plan(text,text.length());
        assertNotNull(p);assertEquals(units,p.remaining());
    }
    @Test public void wordAndTrailingSeparators(){
        remaining("hello world",4);
        remaining("hello x",0);
        remaining("hello world  ",6);
        remaining("hello world!",5);
        remaining("a don't",4);
        remaining("a hello-world",10);
        remaining("one 123",2);
    }
    @Test public void unicode(){
        remaining("привет мир",2);
        remaining("שלום עולם",3);
        remaining("שלום שָׁלוֹם",6);
        remaining("word 𐐀",0);
        remaining("a café",3);
    }
    @Test public void boundaries(){
        assertNull(WordDeleteGesture.plan("a\n",2));
        assertNull(WordDeleteGesture.plan("...",3));
        assertNull(WordDeleteGesture.plan("word",99));
        assertNull(WordDeleteGesture.plan("a 😀",4));
        assertEquals(3,WordDeleteGesture.plan(" word",99).remaining());
    }
    @Test public void timingAndCaret(){
        WordDeleteGesture g=new WordDeleteGesture();
        g.arm(100,5,4,false);assertEquals(4,g.consume(450,5,false));
        assertEquals(-1,g.consume(451,5,false));
        g.arm(100,5,4,false);assertEquals(-1,g.consume(451,5,false));
        g.arm(100,5,4,false);assertEquals(-1,g.consume(200,6,false));
        g.arm(100,5,4,false);assertEquals(-1,g.consume(200,5,true));
        g.arm(100,5,0,false);assertEquals(0,g.consume(200,5,false));
        g.arm(100,5,4,true);g.clear();assertEquals(-1,g.consume(200,5,true));
    }
}
