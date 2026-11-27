package com.ajadi.keys;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.ajadi.keys.KeyLayout.Language.*;

public class CursorWordTest {
    @Test public void beginningMiddleAndEndOfWord(){
        CursorWord start=CursorWord.parse("say ","helo world");
        assertEquals("",start.left);assertEquals("helo",start.right);assertEquals("say",start.previous1);assertEquals(" ",start.next);
        CursorWord middle=CursorWord.parse("say he","lo world");
        assertEquals("helo",middle.word());assertEquals("he",middle.left);assertEquals("lo",middle.right);
        CursorWord end=CursorWord.parse("say helo"," world");
        assertEquals("helo",end.left);assertEquals("",end.right);assertEquals(" ",end.next);
        assertFalse(start.sameSpan(middle));
    }
    @Test public void cyrillicHebrewAndPunctuation(){
        CursorWord ru=CursorWord.parse("я сказал при","вт, ещё");
        assertEquals("привт",ru.word());assertEquals("сказал",ru.previous1);assertEquals("я",ru.previous2);
        assertEquals(",",ru.next);assertEquals("",PunctuationSpacing.suggestionBoundary(ru.next));
        CursorWord he=CursorWord.parse("אמרתי שלו","ם חבר");
        assertEquals("שלום",he.word());assertEquals("אמרתי",he.previous1);
        assertEquals("ג׳ון",CursorWord.parse("ג׳","ון").word());
        CursorWord comma=CursorWord.parse("hello, wor","ld");assertEquals("hello",comma.previous1);assertTrue(comma.grammarGap);
        assertEquals("",CursorWord.parse("hello. wor","ld").previous1);
    }
    @Test public void previousContextWithoutCurrentWordAndUnsafeTruncation(){
        CursorWord gap=CursorWord.parse("one two  ","");
        assertEquals("",gap.word());assertEquals("two",gap.previous1);assertEquals("one",gap.previous2);
        assertFalse(CursorWord.parse("","").hasSurrounding);
        assertNull(CursorWord.parse("x".repeat(CursorWord.BEFORE_LIMIT),""));
        assertNull(CursorWord.parse("a","x".repeat(CursorWord.AFTER_LIMIT)));
        assertNull(CursorWord.parse("abc1","23"));
        assertNull(CursorWord.parse("a","в"));
        assertNull(CursorWord.parse(null,"x"));
    }
    @Test public void loadCompleteWordWithoutCreatingLearnableNegationJoin(){
        WordSession s=new WordSession();s.loadCursorContext(RU,CursorWord.parse("он не на","вижу"));
        assertEquals("навижу",s.prefix());assertEquals("не",s.previous1());assertEquals("он",s.previous2());
        assertEquals("",s.negationJoinPrefix());assertTrue(s.hasSuggestionContext());
    }
}
