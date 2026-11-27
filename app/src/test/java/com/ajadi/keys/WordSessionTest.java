package com.ajadi.keys;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static com.ajadi.keys.KeyLayout.Language.*;
import static org.junit.Assert.*;

public class WordSessionTest {
    @Test public void completionAppendsOnlySuffixAndKeepsContext() {
        WordSession s=new WordSession();
        List<String> learned=new ArrayList<>();
        WordSession.Observer o=(lang,p2,p1,w)->learned.add(p2+"/"+p1+"/"+w);
        s.accept(EN,"See yo",o);
        assertEquals("yo",s.prefix());
        assertEquals("u ",s.completion(EN,"you",false,false," "));
        assertNull(s.completion(EN,"yourselfx".substring(3),false,false," "));
        s.accept(EN,"u ",o);
        assertEquals("see",s.previous2());
        assertEquals("you",s.previous1());
        s.accept(EN,"later!",o);
        assertEquals(Arrays.asList("//see","/see/you","see/you/later"),learned);
        assertEquals("",s.previous1());
    }
    @Test public void digitsDiscardAndBackspaceResetsUnknownContext() {
        WordSession s=new WordSession();
        List<String> learned=new ArrayList<>();
        WordSession.Observer o=(lang,p2,p1,w)->learned.add(w);
        s.accept(EN,"test123more fine ",o);
        assertEquals(Arrays.asList("fine"),learned);
        s.accept(EN,"No",o);
        s.backspace();
        assertEquals("N",s.prefix());
        assertEquals("EXT ",s.completion(EN,"next",true,false," "));
        s.reset();
        assertEquals("Next ",s.completion(EN,"next",false,true," "));
    }
    @Test public void completionRespectsExistingSeparator() {
        WordSession s=new WordSession();
        s.accept(EN,"hel",null);
        assertEquals("lo",s.completion(EN,"hello",false,false,PunctuationSpacing.suggestionBoundary(" ")));
        assertEquals("lo",s.completion(EN,"hello",false,false,PunctuationSpacing.suggestionBoundary(",")));
        assertEquals("lo ",s.completion(EN,"hello",false,false,PunctuationSpacing.suggestionBoundary("")));
        assertEquals("",s.completion(EN,"hel",false,false,PunctuationSpacing.suggestionBoundary(" ")));
    }
    @Test public void adjacentGrammarContextStopsAtPunctuationAndSurvivesSnapshot() {
        WordSession s=new WordSession();s.accept(RU,"будет не учится",null);
        assertEquals("будет",s.adjacentPrevious2());assertEquals("не",s.adjacentPrevious1());
        s.reset();s.accept(RU,"будет, не учится",null);
        assertEquals("будет",s.previous2());assertEquals("",s.adjacentPrevious2());
        WordSession.Snapshot old=s.snapshot();s.reset();s.restore(old);
        assertEquals("не",s.adjacentPrevious1());assertEquals("",s.adjacentPrevious2());
        s.reset();s.accept(RU,"может, учится",null);assertEquals("",s.adjacentPrevious1());
    }
    @Test public void noUnpromptedSuggestionsWithoutTypedContext(){
        WordSession s=new WordSession();
        assertFalse(s.hasSuggestionContext());
        s.accept(EN,"h",null);
        assertTrue(s.hasSuggestionContext());
        s.backspace();
        assertFalse(s.hasSuggestionContext());
        s.accept(EN,"hello ",null);
        assertEquals("hello",s.previous1());
        assertTrue(s.hasSuggestionContext());
        s.accept(EN,"w",null);
        assertTrue(s.hasSuggestionContext());
        s.reset();
        assertFalse(s.hasSuggestionContext());
    }
    @Test public void negationJoinRequiresOwnSingleSpaceAndRestoresOnUndo(){
        WordSession s=new WordSession();
        s.accept(RU,"я Не навижу",null);
        assertEquals("Не ",s.negationJoinPrefix());
        WordSession.Snapshot before=s.snapshot();
        s.rewindNegationForCorrection();s.accept(RU,"Ненавижу ",null);
        assertEquals("я",s.previous2());assertEquals("ненавижу",s.previous1());
        s.restore(before);assertEquals("Не ",s.negationJoinPrefix());assertEquals("навижу",s.prefix());
        for(String phrase:new String[]{"не  навижу","не, навижу","не\tнавижу","не\nнавижу"}){
            s.reset();s.accept(RU,phrase,null);assertEquals(phrase,"",s.negationJoinPrefix());
        }
    }
}
