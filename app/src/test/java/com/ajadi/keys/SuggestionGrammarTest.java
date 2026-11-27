package com.ajadi.keys;

import org.junit.Test;
import java.nio.file.*;
import java.io.*;
import java.util.List;
import static com.ajadi.keys.KeyLayout.Language.*;
import static org.junit.Assert.*;

/** Real downloaded morphology, not invented labels for benchmark phrases. */
public class SuggestionGrammarTest {
    private static Path assets(){
        Path path=Path.of("../language-data/words");
        return Files.isDirectory(path)?path:Path.of("language-data/words");
    }
    private static SuggestionModel withMorph(KeyLayout.Language lang,String... words)throws Exception{
        SuggestionModel m=new SuggestionModel();
        try(InputStream in=Files.newInputStream(assets().resolve(lang.name().toLowerCase(java.util.Locale.ROOT)+".morph.tsv"))){m.loadMorphology(lang,in);}
        for(String w:words)m.addCorpusWord(lang,w,100);
        return m;
    }
    @Test public void russianGovernmentAndAdjectiveAgreement()throws Exception{
        SuggestionModel m=withMorph(RU,"новый","новому","новым","врачом","врача","врачами");
        assertEquals("новому",m.suggest(RU,"","к","нов",3).get(0));
        assertEquals("врачом",m.suggest(RU,"с","новым","врач",3).get(0));
        m.setGrammarEnabled(false);
        assertNotEquals("врачом",m.suggest(RU,"с","новым","врач",3).get(0));
    }
    @Test public void russianSubjectNumberAndPastGender()throws Exception{
        SuggestionModel m=withMorph(RU,"будет","будем","сказал","сказала");
        assertEquals("будем",m.suggest(RU,"","мы","буд",3).get(0));
        assertEquals("сказала",m.suggest(RU,"","она","сказал",3).get(0));
    }
    @Test public void englishModalNumberAndObject()throws Exception{
        SuggestionModel m=withMorph(EN,"go","goes","went","book","books","is","are","me","i");
        assertEquals("go",m.suggest(EN,"we","can","go",3).get(0));
        assertEquals("books",m.suggest(EN,"","these","book",3).get(0));
        assertTrue(m.grammar().score(EN,"","she","is")>m.grammar().score(EN,"","she","are"));
        assertTrue(m.grammar().score(EN,"","with","me")>m.grammar().score(EN,"","with","i"));
    }
    @Test public void hebrewSubjectAndAdjectiveAgreement()throws Exception{
        SuggestionModel m=withMorph(HE,"הלכה","הלך","הגדולה","הגדול","טובות","טובים","הגיע","הגיעו");
        assertEquals("הלכה",m.suggest(HE,"","היא","הל",3).get(0));
        assertEquals("הגדולה",m.suggest(HE,"","הילדה","הגדול",3).get(0));
        assertEquals("טובות",m.suggest(HE,"","נשים","טוב",3).get(0));
        assertEquals("הגיעו",m.suggest(HE,"","הם","הגיע",3).get(0));
    }
    @Test public void packagedTablesActuallyLoadInAllThreeLanguages()throws Exception{
        for(KeyLayout.Language l:KeyLayout.Language.values()){
            SuggestionModel m=new SuggestionModel();String name=l.name().toLowerCase(java.util.Locale.ROOT);
            try(var in=Files.newInputStream(assets().resolve(name+".akn"))){m.loadChat(l,in);}
            try(var in=Files.newInputStream(assets().resolve(name+".morph.tsv"))){m.loadMorphology(l,in);}
            assertEquals(3,m.suggest(l,"","","",3).size());
        }
    }
}
