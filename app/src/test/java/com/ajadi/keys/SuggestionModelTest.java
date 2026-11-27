package com.ajadi.keys;

import org.junit.Test;
import java.util.Arrays;
import java.util.Map;
import static com.ajadi.keys.KeyLayout.Language.*;
import static org.junit.Assert.*;

public class SuggestionModelTest {
    @Test public void prefixAndLanguageAreRespected() {
        SuggestionModel m=new SuggestionModel();
        m.addCorpusWord(EN,"hello",1000);
        m.addCorpusWord(EN,"help",100);
        m.addCorpusWord(EN,"world",10000);
        m.addCorpusWord(RU,"\u043f\u0440\u0438\u0432\u0435\u0442",10000);
        assertEquals(Arrays.asList("hello","help"),m.suggest(EN,"","","HEL",3));
        assertTrue(m.suggest(HE,"","","h",3).isEmpty());
    }
    @Test public void contextsRankExactWordForms() {
        SuggestionModel m=new SuggestionModel();
        String with="\u0441", you="\u0442\u043e\u0431\u043e\u0439", beautiful="\u043a\u0440\u0430\u0441\u0438\u0432\u044b\u043c";
        m.seedPhrase(RU,with+" "+you);
        m.addCorpusWord(RU,"\u0438",5000000);
        assertEquals(you,m.suggest(RU,"",with,"",3).get(0));
        for(int i=0;i<3;i++) m.learn(RU,"",with,beautiful);
        assertEquals(beautiful,m.suggest(RU,"",with,"",3).get(0));
        SuggestionModel restored=new SuggestionModel();
        restored.restore(m.snapshot());
        assertEquals(beautiful,restored.suggest(RU,"",with,"",3).get(0));
        restored.clear();
        assertTrue(restored.suggest(RU,"",with,"",3).isEmpty());
    }
    @Test public void twoWordContextOutranksUnigram() {
        SuggestionModel m=new SuggestionModel();
        m.seedPhrase(EN,"see you later");
        m.seedPhrase(EN,"thank you very much");
        assertEquals("later",m.suggest(EN,"see","you","",3).get(0));
        assertEquals("very",m.suggest(EN,"thank","you","",3).get(0));
    }
    @Test public void removingPublicDataPreservesLearningBlocksAndOtherLanguages() {
        SuggestionModel m=new SuggestionModel();
        m.addCorpusWord(EN,"publicword",5000);
        m.seedPhrase(EN,"at publicword");
        m.addCorpusWord(RU,"\u0434\u043e\u043c",1000);
        m.learn(EN,"","at","personalword");
        m.block(EN,"blockedword");
        Map<String,Integer> before=m.snapshot();
        m.removeCorpus(EN);
        assertFalse(m.known(EN,"publicword"));
        assertTrue(m.known(RU,"\u0434\u043e\u043c"));
        assertEquals(before,m.snapshot());
        assertTrue(m.isBlocked(EN,"blockedword"));
        assertEquals("personalword",m.suggest(EN,"","at","",3).get(0));
        m.addCorpusWord(EN,"replacement",1000);
        assertTrue(m.known(EN,"replacement"));
        assertFalse(m.known(EN,"publicword"));
    }
    @Test public void modelBoundAndInvalidTokens() {
        SuggestionModel m=new SuggestionModel();
        m.learn(EN,"","","123a");
        assertTrue(m.snapshot().isEmpty());
        for(int i=0;i<9000;i++) {
            StringBuilder w=new StringBuilder("test");
            for(int n=i;;n/=26) { w.append((char)('a'+n%26)); if(n<26)break; }
            m.learn(EN,"","",w.toString());
        }
        assertEquals(SuggestionModel.MAX_PERSONAL_GRAMS,m.snapshot().size());
    }
    @Test public void unpersonalizedSuggestionsUsePublicCorpusWithoutLearnedWordsOrRanking(){
        SuggestionModel m=new SuggestionModel();
        m.addCorpusWord(EN,"hello",1000);
        m.addCorpusWord(EN,"help",500);
        m.seedPhrase(EN,"say hello");
        var normal=m.suggest(EN,"","say","hel",3,false);
        assertEquals(Arrays.asList("hello","help"),normal);
        for(int i=0;i<12;i++){
            m.learn(EN,"","say","help");
            m.learn(EN,"","say","helprivate");
        }
        assertEquals(normal,m.suggest(EN,"","say","hel",3,false));
        assertTrue(m.suggest(EN,"","say","hel",3,true).contains("helprivate"));
        assertTrue(m.suggest(EN,"","say","helprivate",3,false).isEmpty());
        assertEquals(Arrays.asList("helprivate"),m.suggest(EN,"","say","helprivate",3,true));
        m.removeCorpus(EN);
        assertTrue(m.suggest(EN,"","say","hel",3,false).isEmpty());
        assertFalse(m.suggest(EN,"","say","hel",3,true).isEmpty());
    }
}
