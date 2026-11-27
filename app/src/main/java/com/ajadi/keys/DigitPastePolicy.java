package com.ajadi.keys;

/** Explicit paste-as-keys accepts ordinary short digit-only strings, not arbitrary text. */
final class DigitPastePolicy {
    static final int MAX_DIGITS=32;
    static boolean accepts(CharSequence text) {
        if(text==null||text.length()<2||text.length()>MAX_DIGITS)return false;
        for(int i=0;i<text.length();i++)if(text.charAt(i)<'0'||text.charAt(i)>'9')return false;
        return true;
    }
}
