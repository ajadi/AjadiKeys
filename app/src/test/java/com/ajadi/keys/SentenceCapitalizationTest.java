package com.ajadi.keys;
import org.junit.Test;
import static org.junit.Assert.*;
public class SentenceCapitalizationTest {
    @Test public void recognizesBeginningAndSentenceEnds(){
        assertTrue(SentenceCapitalization.atStart("",0));
        assertTrue(SentenceCapitalization.atStart("  ",2));
        assertTrue(SentenceCapitalization.atStart("Done. ",6));
        assertTrue(SentenceCapitalization.atStart("Да! ",4));
        assertTrue(SentenceCapitalization.atStart("one\n",4));
        assertTrue(SentenceCapitalization.atStart("«Да?» ",6));
    }
    @Test public void doesNotMistakeUnknownOrMiddleForStart(){
        assertFalse(SentenceCapitalization.atStart(null,0));
        assertFalse(SentenceCapitalization.atStart("",100));
        assertFalse(SentenceCapitalization.atStart(" ",100));
        assertFalse(SentenceCapitalization.atStart("или ",4));
        assertFalse(SentenceCapitalization.atStart("one, ",5));
    }
}
