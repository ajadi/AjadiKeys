package com.ajadi.keys;
import org.junit.Test;
import static org.junit.Assert.*;

public class PunctuationSpacingTest {
    @Test public void ordinaryProseInEveryLayout(){
        assertEquals(1,PunctuationSpacing.spacesBefore("привет ",","));
        assertEquals(2,PunctuationSpacing.spacesBefore("hello  ","."));
        assertEquals(1,PunctuationSpacing.spacesBefore("שלום ","?"));
        for(String p:new String[]{",",".","!","?",":",";","…",")","]","}","»","”","׃"})
            assertEquals(p,1,PunctuationSpacing.spacesBefore("word ",p));
    }
    @Test public void neverTrimOpeningOrAmbiguousCharacters(){
        for(String p:new String[]{"(","[","{","«","“","-","—","/","'","\"","%"," ","word,",""})
            assertEquals(p,0,PunctuationSpacing.spacesBefore("word ",p));
    }
    @Test public void preserveIntentionalWhitespaceAndBoundedReads(){
        for(String s:new String[]{""," ","  ","word","word\n ","word\t ","word\u00a0","word\n"})
            assertEquals(s,0,PunctuationSpacing.spacesBefore(s,","));
        assertEquals(0,PunctuationSpacing.spacesBefore(null,","));
        StringBuilder huge=new StringBuilder("word");for(int i=0;i<33;i++)huge.append(' ');
        assertEquals(0,PunctuationSpacing.spacesBefore(huge,","));
    }
    @Test public void automaticSuggestionSpaceOnlyWhenNoExistingSeparator(){
        for(String s:new String[]{" ","  "," world","\t","\n","\u00a0",",","!","?",".",":",")","]","}"})
            assertEquals(s,"",PunctuationSpacing.suggestionBoundary(s));
        for(String s:new String[]{"","word","("})
            assertEquals(s," ",PunctuationSpacing.suggestionBoundary(s));
        assertEquals(" ",PunctuationSpacing.suggestionBoundary(null));
    }
}
