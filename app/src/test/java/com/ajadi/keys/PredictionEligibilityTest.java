package com.ajadi.keys;

import org.junit.Test;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import static org.junit.Assert.*;

public class PredictionEligibilityTest {
    @Test public void normalTextOnly() {
        int text=InputType.TYPE_CLASS_TEXT;
        assertTrue(PredictionEligibility.allowed(text,0));
        assertTrue(PredictionEligibility.allowed(text|InputType.TYPE_TEXT_FLAG_MULTI_LINE,0));
        assertFalse(PredictionEligibility.allowed(text|InputType.TYPE_TEXT_VARIATION_PASSWORD,0));
        assertFalse(PredictionEligibility.allowed(text|InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,0));
        assertFalse(PredictionEligibility.allowed(text|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,0));
        assertFalse(PredictionEligibility.allowed(text|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,0));
        assertFalse(PredictionEligibility.allowed(text|InputType.TYPE_TEXT_VARIATION_URI,0));
        assertFalse(PredictionEligibility.allowed(text|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,0));
        assertFalse(PredictionEligibility.allowed(text,EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING));
        assertFalse(PredictionEligibility.allowed(InputType.TYPE_CLASS_NUMBER,0));
    }
    @Test public void chatToolsIndependentOfPredictionFlags() {
        int[] variants={InputType.TYPE_TEXT_VARIATION_NORMAL,InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
                InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT};
        for(int variation:variants) {
            int text=InputType.TYPE_CLASS_TEXT|variation|InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            for(int noSuggestions:new int[]{0,InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS})
                for(int noLearning:new int[]{0,EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING}) {
                    assertTrue(PredictionEligibility.localToolsAllowed(text|noSuggestions,noLearning,"Message",null));
                    assertTrue(PredictionEligibility.manualSuggestionsAllowed(text|noSuggestions,noLearning,"Message",null));
                    assertTrue(PredictionEligibility.cursorContextAllowed(text|noSuggestions,noLearning,"Message",null));
                    assertEquals(noSuggestions==0&&noLearning==0,PredictionEligibility.allowed(text|noSuggestions,noLearning));
                }
        }
    }

    @Test public void localToolsPreserveProtectedFieldRestrictions() {
        int text=InputType.TYPE_CLASS_TEXT;
        int[] protectedTypes={text|InputType.TYPE_TEXT_VARIATION_PASSWORD,
                text|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,text|InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                text|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,text|InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                text|InputType.TYPE_TEXT_VARIATION_URI,InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_PHONE,InputType.TYPE_CLASS_DATETIME,InputType.TYPE_NULL};
        for(int type:protectedTypes)
            for(int noSuggestions:new int[]{0,InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS})
                for(int noLearning:new int[]{0,EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING})
                    assertFalse(PredictionEligibility.localToolsAllowed(type|noSuggestions,noLearning,null,null));
        for(int noSuggestions:new int[]{0,InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS})
            for(int noLearning:new int[]{0,EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING}) {
                assertFalse(PredictionEligibility.localToolsAllowed(text|noSuggestions,noLearning,"Username",null));
                assertFalse(PredictionEligibility.localToolsAllowed(text|noSuggestions,noLearning,null,"login"));
            }
    }

    @Test public void restrictionsForOtherTextVariationsRemainUnchanged() {
        int field=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_FILTER;
        assertTrue(PredictionEligibility.localToolsAllowed(field,0,null,null));
        assertFalse(PredictionEligibility.localToolsAllowed(field|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,0,null,null));
        assertFalse(PredictionEligibility.localToolsAllowed(field,EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,null,null));
    }
    @Test public void manualSuggestionsNeverEnterProtectedEditors(){
        int text=InputType.TYPE_CLASS_TEXT;
        int[] types={text|InputType.TYPE_TEXT_VARIATION_PASSWORD,text|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                text|InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,text|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                text|InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,text|InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_CLASS_NUMBER,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_PHONE,InputType.TYPE_CLASS_DATETIME,InputType.TYPE_NULL};
        for(int type:types)
            for(int noSuggestions:new int[]{0,InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS})
                for(int noLearning:new int[]{0,EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING})
                    assertFalse(PredictionEligibility.manualSuggestionsAllowed(type|noSuggestions,noLearning,null,null));
        assertFalse(PredictionEligibility.manualSuggestionsAllowed(text,0,"Username",null));
        assertFalse(PredictionEligibility.manualSuggestionsAllowed(text|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,null,"login"));
        assertFalse(PredictionEligibility.manualSuggestionsAllowed(text|InputType.TYPE_TEXT_VARIATION_FILTER|
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,0,null,null));
    }
    @Test public void cursorReadsAreLimitedToOrdinaryText(){
        int text=InputType.TYPE_CLASS_TEXT;
        for(int variation:new int[]{InputType.TYPE_TEXT_VARIATION_PASSWORD,InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_PERSON_NAME,InputType.TYPE_TEXT_VARIATION_FILTER}){
            assertFalse(PredictionEligibility.cursorContextAllowed(text|variation,0,null,null));
        }
        assertFalse(PredictionEligibility.cursorContextAllowed(text,0,"Username",null));
        assertFalse(PredictionEligibility.cursorContextAllowed(InputType.TYPE_CLASS_NUMBER,0,null,null));
    }
}
