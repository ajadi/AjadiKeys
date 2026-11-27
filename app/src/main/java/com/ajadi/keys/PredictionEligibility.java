package com.ajadi.keys;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

/** Respect Android editor-type privacy and no-personalization metadata. */
final class PredictionEligibility {
    static boolean allowed(int inputType, int imeOptions) {
        if ((inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false;
        if ((imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
                || (inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return false;
        int variation=inputType & InputType.TYPE_MASK_VARIATION;
        return variation!=InputType.TYPE_TEXT_VARIATION_PASSWORD
                && variation!=InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                && variation!=InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                && variation!=InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                && variation!=InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                && variation!=InputType.TYPE_TEXT_VARIATION_URI;
    }

    /** Explicitly invoked local tools are independent of prediction flags in ordinary chat/text fields. */
    static boolean localToolsAllowed(int inputType, int imeOptions, CharSequence hint, String fieldName) {
        if (LoginFieldPolicy.declaredLogin(inputType,hint,fieldName)) return false;
        if (allowed(inputType,imeOptions)) return true;
        if ((inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false;
        int variation=inputType & InputType.TYPE_MASK_VARIATION;
        return variation==InputType.TYPE_TEXT_VARIATION_NORMAL
                || variation==InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
                || variation==InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE
                || variation==InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT;
    }

    /** Ordinary text/chat can show user-selected offline dictionary completions without automatic processing. */
    static boolean manualSuggestionsAllowed(int inputType,int imeOptions,CharSequence hint,String fieldName){
        return localToolsAllowed(inputType,imeOptions,hint,fieldName);
    }
    /** Surrounding-word reads are restricted to ordinary text/chat fields, even when manual chips are available elsewhere. */
    static boolean cursorContextAllowed(int inputType,int imeOptions,CharSequence hint,String fieldName){
        if(!localToolsAllowed(inputType,imeOptions,hint,fieldName))return false;
        int variation=inputType&InputType.TYPE_MASK_VARIATION;
        return variation==InputType.TYPE_TEXT_VARIATION_NORMAL||variation==InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
                ||variation==InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE||variation==InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT;
    }
}
