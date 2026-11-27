package com.ajadi.keys;

import org.junit.Test;
import static com.ajadi.keys.KeyLayout.Language.*;
import static org.junit.Assert.*;

public class CorrectionEngineTest {
    private static final String PRIVET="\u043f\u0440\u0438\u0432\u0435\u0442";
    private static final String SHALOM="\u05e9\u05dc\u05d5\u05dd";
    @Test public void physicalLayoutsInBothDirections() {
        assertEquals(PRIVET,CorrectionEngine.convertLayout(EN,RU,"ghbdtn"));
        assertEquals("ghbdtn",CorrectionEngine.convertLayout(RU,EN,PRIVET));
        assertEquals("hello",CorrectionEngine.convertLayout(RU,EN,"\u0440\u0443\u0434\u0434\u0449"));
        assertEquals(SHALOM,CorrectionEngine.convertLayout(EN,HE,"akuo"));
        assertEquals("akuo",CorrectionEngine.convertLayout(HE,EN,SHALOM));
        assertEquals(SHALOM,CorrectionEngine.convertLayout(RU,HE,"\u0444\u043b\u0433\u0449"));
        assertEquals("\u0444\u043b\u0433\u0449",CorrectionEngine.convertLayout(HE,RU,SHALOM));
        assertNull(CorrectionEngine.convertLayout(EN,HE,"qww"));
    }
    @Test public void detectsOnlyUnambiguousSupportedScript() {
        assertEquals(RU,CorrectionEngine.detectScript(PRIVET));
        assertEquals(EN,CorrectionEngine.detectScript("Hello"));
        assertEquals(HE,CorrectionEngine.detectScript(SHALOM));
        assertNull(CorrectionEngine.detectScript("a\u0430"));
        assertNull(CorrectionEngine.detectScript("123"));
    }
    @Test public void oneEditHandlesInsertDeleteSubstituteTranspose() {
        assertTrue(CorrectionEngine.oneEdit("wrold","world"));
        assertTrue(CorrectionEngine.oneEdit("word","world"));
        assertTrue(CorrectionEngine.oneEdit("worlds","world"));
        assertTrue(CorrectionEngine.oneEdit("wprld","world"));
        assertFalse(CorrectionEngine.oneEdit("world","world"));
        assertFalse(CorrectionEngine.oneEdit("wrd","world"));
        assertFalse(CorrectionEngine.oneEdit("dorlw","world"));
    }
    @Test public void highConfidenceTypoAndUndoFriendlyCase() {
        SuggestionModel model=new SuggestionModel();model.addCorpusWord(RU,PRIVET,180000);
        CorrectionEngine engine=new CorrectionEngine(model);
        String typo="\u043f\u0440\u0432\u0438\u0435\u0442";
        CorrectionEngine.Candidate best=engine.best(RU,"","",typo,true,false);
        assertNotNull(best);assertEquals(PRIVET,best.word);assertEquals(CorrectionEngine.Kind.TYPO,best.kind);assertTrue(best.automatic);
        assertEquals("\u041f\u0440\u0438\u0432\u0435\u0442",CorrectionEngine.caseLike("\u041f\u0440\u0432\u0438\u0435\u0442",PRIVET,RU));
        assertNull(engine.best(RU,"","",PRIVET,true,true));
        assertNull(engine.best(RU,"","",PRIVET.substring(0,4),true,false));
        model.learn(RU,"","",typo);
        assertNull(engine.best(RU,"","",typo,true,true));
    }
    @Test public void layoutOptionAndKnownWordProtection() {
        SuggestionModel model=new SuggestionModel();model.addCorpusWord(RU,PRIVET,180000);
        model.addCorpusWord(HE,SHALOM,120000);model.addCorpusWord(EN,"hello",400000);
        CorrectionEngine engine=new CorrectionEngine(model);
        var c=engine.best(EN,"","","ghbdtn",true,true);
        assertNotNull(c);assertEquals(PRIVET,c.word);assertEquals(RU,c.language);assertTrue(c.automatic);
        assertNull(engine.best(EN,"","","ghbdtn",false,false));
        c=engine.best(EN,"","","akuo",false,true);assertEquals(HE,c.language);assertEquals(SHALOM,c.word);
        c=engine.best(RU,"","","\u0440\u0443\u0434\u0434\u0449",false,true);assertEquals(EN,c.language);assertEquals("hello",c.word);
        model.learn(EN,"","","ghbdtn");assertNull(engine.best(EN,"","","ghbdtn",true,true));
    }
    @Test public void ambiguityAndContextualPersonalRanking() {
        SuggestionModel model=new SuggestionModel();model.addCorpusWord(EN,"world",100000);
        model.addCorpusWord(EN,"would",100000);
        CorrectionEngine engine=new CorrectionEngine(model);
        var c=engine.best(EN,"","","wold",true,false);
        assertNotNull(c);assertFalse(c.automatic);
        for(int i=0;i<3;i++)model.learn(EN,"hello","my","world");
        c=engine.best(EN,"hello","my","wold",true,false);
        assertEquals("world",c.word);assertTrue(c.automatic);
    }
    @Test public void sessionSnapshotRestoresOriginalWordAndContext() {
        WordSession session=new WordSession();session.accept(EN,"my wrold",null);
        WordSession.Snapshot before=session.snapshot();
        session.replacePrefix("world");session.accept(EN," ",null);
        assertEquals("world",session.previous1());
        session.restore(before);assertEquals("wrold",session.prefix());assertEquals("my",session.previous1());
    }
}
