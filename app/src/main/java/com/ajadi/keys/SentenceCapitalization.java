package com.ajadi.keys;

/** A bounded, transient sentence-boundary decision, independent of learning. */
final class SentenceCapitalization {
    static final int CONTEXT=96;
    static boolean atStart(CharSequence before, int caret){
        if(before==null)return false;
        for(int i=before.length()-1;i>=0;i--){
            char c=before.charAt(i);
            if(c=='\n'||c=='\r')return true;
            if(Character.isWhitespace(c))continue;
            if(c=='"'||c=='\u00bb'||c=='\u201d'||c==')'||c==']')continue;
            return c=='.'||c=='!'||c=='?'||c=='\u2026';
        }
        return caret>=0&&caret<=before.length();
    }
}
