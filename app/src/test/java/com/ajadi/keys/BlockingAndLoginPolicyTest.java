package com.ajadi.keys;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import org.junit.Test;
import static com.ajadi.keys.KeyLayout.Language.*;
import static org.junit.Assert.*;

public class BlockingAndLoginPolicyTest {
    @Test public void blockedWordDisappearsFromAllRankingsButTypingRemainsValid() {
        SuggestionModel m=new SuggestionModel();
        m.addCorpusWord(EN,"world",200000);m.addCorpusWord(EN,"word",10000);
        m.seedPhrase(EN,"hello world");m.learn(EN,"","hello","world");
        assertEquals("world",m.suggest(EN,"","hello","",3).get(0));
        assertTrue(m.block(EN,"WORLD"));assertTrue(m.isBlocked(EN,"world"));
        assertFalse(m.suggest(EN,"","hello","",3).contains("world"));
        assertFalse(m.oneEditCandidates(EN,"wrold").contains("world"));
        assertTrue(m.known(EN,"world"));
        SuggestionModel restored=new SuggestionModel();restored.restoreBlocked(m.blockedSnapshot());
        assertTrue(restored.isBlocked(EN,"world"));restored.unblock("EN\tworld");assertFalse(restored.isBlocked(EN,"world"));
    }
    @Test public void blockedLayoutTargetCannotBeAutomaticallyApplied() {
        SuggestionModel m=new SuggestionModel();String word="\u043f\u0440\u0438\u0432\u0435\u0442";
        m.addCorpusWord(RU,word,180000);CorrectionEngine e=new CorrectionEngine(m);
        assertEquals(word,e.best(EN,"","","ghbdtn",false,true).word);
        m.block(RU,word);assertNull(e.best(EN,"","","ghbdtn",false,true));
    }
    @Test public void onlyDeclaredLoginFieldsCanOfferLocalSave() {
        int text=InputType.TYPE_CLASS_TEXT;
        assertTrue(LoginFieldPolicy.allowed(text|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,0,null,null));
        assertTrue(LoginFieldPolicy.allowed(text,0,"Username",null));
        assertTrue(LoginFieldPolicy.allowed(text,0,null,"login_name"));
        assertFalse(LoginFieldPolicy.allowed(text,0,"Message",null));
        assertFalse(LoginFieldPolicy.allowed(text|InputType.TYPE_TEXT_VARIATION_PASSWORD,0,"Email",null));
        assertFalse(LoginFieldPolicy.allowed(text|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,0,"Username",null));
        assertFalse(LoginFieldPolicy.allowed(InputType.TYPE_CLASS_NUMBER,0,"Username",null));
        assertFalse(LoginFieldPolicy.allowed(text,EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,"Username",null));
        assertFalse(LoginFieldPolicy.allowed(text|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0,"Login",null));
        assertTrue(LoginFieldPolicy.validLogin("demo@example.invalid"));
        assertFalse(LoginFieldPolicy.validLogin("hello world"));
        assertFalse(LoginFieldPolicy.validLogin(""));
    }
    @Test public void digitPasteAcceptsOnlyExplicitShortDigitStrings() {
        assertTrue(DigitPastePolicy.accepts("123456"));
        assertFalse(DigitPastePolicy.accepts("1"));
        assertFalse(DigitPastePolicy.accepts("12 34"));
        assertFalse(DigitPastePolicy.accepts("+123"));
        assertFalse(DigitPastePolicy.accepts(null));
        assertFalse(DigitPastePolicy.accepts("123456789012345678901234567890123"));
    }
}
