package com.ajadi.keys;
import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** End-to-end policy on actual packaged tables and fixed, independently authored examples. */
public class CorrectionQualityTest {
    private static Path root(){return Files.isDirectory(Path.of("language-data/words"))?Path.of("."):Path.of("..");}
    private static SuggestionModel load()throws Exception{
        SuggestionModel m=new SuggestionModel();Path a=root().resolve("language-data/words");
        for(KeyLayout.Language l:KeyLayout.Language.values()){
            String n=l.name().toLowerCase(Locale.ROOT);
            for(String line:Files.readAllLines(a.resolve(n+".tsv"),StandardCharsets.UTF_8)){
                String[] p=line.split("\t",2);if(p.length==2)m.addCorpusWord(l,p[0],Integer.parseInt(p[1]));
            }
            try(var in=Files.newInputStream(a.resolve(n+".akn"))){m.loadChat(l,in);}
            try(var in=Files.newInputStream(a.resolve(n+".morph.tsv"))){m.loadMorphology(l,in);}
            try(var in=Files.newInputStream(a.resolve(n+".akp"))){m.loadProtection(l,in);}
            m.prepare(l);
        }
        for(String line:Files.readAllLines(a.resolve("contexts.tsv"),StandardCharsets.UTF_8)){
            String[] p=line.split("\t",2);if(p.length==2)m.seedPhrase(KeyLayout.Language.valueOf(p[0]),p[1]);
        }
        return m;
    }
    @Test public void ordinaryTyposAndProtectedWordsAcrossAllLanguages()throws Exception{
        SuggestionModel m=load();CorrectionEngine engine=new CorrectionEngine(m);
        for(String line:Files.readAllLines(root().resolve("qa/prediction/correction_cases.tsv"),StandardCharsets.UTF_8)){
            String[] a=line.split("\t",-1);KeyLayout.Language l=KeyLayout.Language.valueOf(a[0]);
            CorrectionEngine.Candidate c=engine.best(l,a[1],a[2],a[3],true,true,true);
            if("protect".equals(a[5]))assertTrue(a[3],c==null||!c.automatic);
            else{
                assertNotNull(a[3],c);assertEquals(a[3],a[4],c.word);
                assertEquals(a[3],"auto".equals(a[5]),c.automatic);
            }
        }
        assertNull(engine.best(KeyLayout.Language.EN,"","","teh",true,true,false));
        assertTrue(engine.best(KeyLayout.Language.EN,"","","teh",true,true,true).automatic);
        m.learn(KeyLayout.Language.EN,"","","teh");
        assertNull(engine.best(KeyLayout.Language.EN,"","","teh",true,true,true));
    }
    @Test public void genuineInflectionsOutsideSuggestionVocabularyAreProtected()throws Exception{
        SuggestionModel m=load();
        for(String word:new String[]{"возможна","косит","бужу"}){
            assertTrue(word,m.protectedWord(KeyLayout.Language.RU,word));
            assertNull(new CorrectionEngine(m).best(KeyLayout.Language.RU,"","",word,true,true,true));
        }
    }
    @Test public void independentNaturalSentencesAreNotSilentlyChanged()throws Exception{
        SuggestionModel m=load();CorrectionEngine e=new CorrectionEngine(m);
        for(String line:Files.readAllLines(root().resolve("qa/prediction/holdout.tsv"),StandardCharsets.UTF_8)){
            String[] p=line.split("\t",2);KeyLayout.Language l=KeyLayout.Language.valueOf(p[0]);String p2="",p1="";
            for(String word:p[1].split(" ")){
                String w=SuggestionModel.normalise(l,word);
                CorrectionEngine.Candidate c=e.best(l,p2,p1,w,true,true,true);
                assertTrue(l+":"+w,c==null||!c.automatic);p2=p1;p1=w;
            }
        }
    }
    @Test public void punctuationBoundariesDoNotIncludeWordInternalMarks(){
        for(String x:new String[]{" ",".",",","!","?",";",":"})assertTrue(CorrectionEngine.automaticBoundary(x));
        for(String x:new String[]{"'","-","a","1","","ab"})assertFalse(CorrectionEngine.automaticBoundary(x));
    }
    @Test public void russianReflexiveEndingUsesRealMorphologyAndContext()throws Exception{
        SuggestionModel m=load();CorrectionEngine engine=new CorrectionEngine(m);
        KeyLayout.Language ru=KeyLayout.Language.RU;
        String[][] repairs={
                {"он","учиться","учится"}, {"будет","учится","учиться"},
                {"он хочет","учится","учиться"}, {"надо","готовится","готовиться"},
                {"она","готовиться","готовится"}, {"хочу","готовится","готовиться"},
                {"он не","нравиться","нравится"}, {"будет не","учится","учиться"},
                {"он может","учится","учиться"}, {"он","смееться","смеется"},
                {"будет","заниматся","заниматься"}
        };
        for(String[] example:repairs){
            WordSession session=new WordSession();session.accept(ru,example[0]+" "+example[1],null);
            CorrectionEngine.Candidate c=engine.best(ru,session.previous2(),session.previous1(),session.prefix(),
                    true,true,true,session.adjacentPrevious2(),session.adjacentPrevious1());
            assertNotNull(Arrays.toString(example),c);assertEquals(example[2],c.word);
            assertTrue(Arrays.toString(example),c.automatic);
        }
        for(String phrase:new String[]{"он учится","она готовится","будет учиться","хочу готовиться",
                "мне нравится","мне учиться","они учиться","может учится","может, учится","он, учиться",
                "он, не учиться","он вернуться","он УЧИТЬСЯ","будет делатся"}){
            WordSession session=new WordSession();session.accept(ru,phrase,null);
            CorrectionEngine.Candidate c=engine.best(ru,session.previous2(),session.previous1(),session.prefix(),
                    true,true,true,session.adjacentPrevious2(),session.adjacentPrevious1());
            assertTrue(phrase,c==null||!c.automatic);
        }
        CorrectionEngine.Candidate ambiguous=engine.best(ru,"","может","учится",true,true,true);
        assertNotNull(ambiguous);assertEquals("учиться",ambiguous.word);assertFalse(ambiguous.automatic);
        assertNull(engine.best(ru,"","он","учиться",false,false,true));
        m.block(ru,"учится");
        assertNull(engine.best(ru,"","он","учиться",true,false,true));
    }

    @Test public void missingMorphologyNeverSilentlyTogglesReflexiveEnding(){
        KeyLayout.Language ru=KeyLayout.Language.RU;
        SuggestionModel m=new SuggestionModel();m.addCorpusWord(ru,"учиться",100000);
        CorrectionEngine.Candidate c=new CorrectionEngine(m).best(ru,"","будет","учится",true,false,true);
        assertTrue(c==null||!c.automatic);
    }
    private static CorrectionEngine.Candidate negation(CorrectionEngine e,String phrase,boolean typos){
        KeyLayout.Language ru=KeyLayout.Language.RU;
        WordSession session=new WordSession();session.accept(ru,phrase,null);
        return e.best(ru,session.previous2(),session.previous1(),session.prefix(),typos,true,true,
                session.adjacentPrevious2(),session.adjacentPrevious1(),session.negationJoinPrefix());
    }
    @Test public void russianNegationRepairsOnlyConfidentDictionaryForms()throws Exception{
        SuggestionModel m=load();CorrectionEngine e=new CorrectionEngine(m);
        String[][] examples={{"нехочу","не хочу"},{"я незнаю","не знаю"},{"небуду","не буду"},
                {"не навижу","ненавижу"},{"я не навижу","ненавижу"},{"Не навижу","ненавижу"}};
        for(String[] pair:examples){
            CorrectionEngine.Candidate c=negation(e,pair[0],true);
            assertNotNull(Arrays.toString(pair),c);assertEquals(Arrays.toString(pair),pair[1],c.word);
            assertTrue(Arrays.toString(pair),c.automatic);
        }
        for(String phrase:new String[]{"не хочу","не знаю","ненавижу","ненавидеть","неправда","не правда",
                "неверно","невозможно","не возможно","не  навижу","не, навижу","не\nнавижу","не доделал",
                "НЕХОЧУ","не Навижу"}){
            CorrectionEngine.Candidate c=negation(e,phrase,true);
            assertTrue(phrase,c==null||!c.automatic);
        }
        CorrectionEngine.Candidate split=negation(e,"неправда",true);
        assertNotNull(split);assertEquals("не правда",split.word);assertFalse(split.automatic);
        CorrectionEngine.Candidate join=negation(e,"не правда",true);
        assertNotNull(join);assertEquals("неправда",join.word);assertFalse(join.automatic);
        assertNull(negation(e,"нехочу",false));
        assertNull(negation(e,"не навижу",false));
        m.block(KeyLayout.Language.RU,"ненавижу");
        CorrectionEngine.Candidate blocked=negation(e,"не навижу",true);
        assertTrue(blocked==null||!blocked.word.equals("ненавижу"));
    }
    @Test public void russianSoftSignRequiresClearContext()throws Exception{
        CorrectionEngine e=new CorrectionEngine(load());
        for(String[] pair:new String[][]{{"ты пишеш","пишешь"},{"ты знаеш","знаешь"},{"ты не говориш","говоришь"},
                {"этот карандашь","карандаш"},{"моя мыш","мышь"}}){
            CorrectionEngine.Candidate c=negation(e,pair[0],true);
            assertNotNull(Arrays.toString(pair),c);assertEquals(pair[1],c.word);assertTrue(pair[0],c.automatic);
        }
        for(String phrase:new String[]{"ты пишешь","ты знаешь","он пишеш","пишеш","ты, пишеш",
                "карандаш","мышь","плач","плачь","ты ПИШЕШ"}){
            CorrectionEngine.Candidate c=negation(e,phrase,true);
            assertTrue(phrase,c==null||!c.automatic);
        }
        CorrectionEngine.Candidate c=negation(e,"пишеш",true);
        assertNotNull(c);assertEquals("пишешь",c.word);assertFalse(c.automatic);
    }
    @Test public void omittedInteriorLetterInColloquialRussian() throws Exception{
        SuggestionModel m=load();CorrectionEngine e=new CorrectionEngine(m);
        KeyLayout.Language ru=KeyLayout.Language.RU;
        CorrectionEngine.Candidate c=e.best(ru,"","да","похду",true,true,true);
        assertNotNull(c);assertEquals("походу",c.word);assertTrue(c.automatic);
        assertNull(e.best(ru,"","да","походу",true,true,true));
    }
    @Test public void explicitLayoutRepairPreservesTheSelectedText(){
        assertEquals("Привет мир!",CorrectionEngine.convertSelectedLayout(KeyLayout.Language.EN,KeyLayout.Language.RU,"Ghbdtn vbh!"));
        assertNull(CorrectionEngine.convertSelectedLayout(KeyLayout.Language.EN,KeyLayout.Language.RU,"hello мир"));
    }
}
