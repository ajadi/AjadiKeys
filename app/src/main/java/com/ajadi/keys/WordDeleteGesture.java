package com.ajadi.keys;

/** Bounded, transient word-boundary calculation for an explicit Backspace gesture.
 * No editor text is retained: a pending pair contains only counts, caret and time.
 */
final class WordDeleteGesture {
    static final long WINDOW_MS=350,HOLD_MS=200;
    static final int CONTEXT_UNITS=257;
    static final class Plan {
        final int totalUnits, firstUnits;
        Plan(int total,int first){totalUnits=total;firstUnits=first;}
        int remaining(){return totalUnits-firstUnits;}
    }
    private long time=-1;
    private int caret=-1,remaining;
    private boolean draft;
    void clear(){time=-1;caret=-1;remaining=0;}
    void arm(long now,int nextCaret,int units,boolean inDraft){
        time=now;caret=nextCaret;remaining=units;draft=inDraft;
    }
    /** Consume one tap-and-hold pair only. Fast short taps re-arm after deleting one character. */
    int consume(long now,int currentCaret,boolean inDraft){
        int result=time>=0 && now>=time && now-time<=WINDOW_MS && caret==currentCaret && draft==inDraft
                ? remaining : -1;
        clear();return result;
    }
    static Plan plan(CharSequence before,int caret){
        if(before==null||before.length()==0||caret<before.length())return null;
        int end=before.length(),i=end;
        int first=Character.charCount(Character.codePointBefore(before,end));
        // Include trailing spaces and punctuation, but never cross a line break.
        while(i>0){
            int cp=Character.codePointBefore(before,i);
            if(cp=='\n'||cp=='\r'||cp==0x2028||cp==0x2029)return null;
            if(!separator(cp))break;
            i-=Character.charCount(cp);
        }
        int wordEnd=i;
        while(i>0){
            int cp=Character.codePointBefore(before,i);
            if(wordPart(cp)){i-=Character.charCount(cp);continue;}
            int left=i-Character.charCount(cp);
            if(joiner(cp)&&i<wordEnd&&left>0&&wordPart(Character.codePointBefore(before,left))){
                i=left;continue;
            }
            break;
        }
        // No word, or the word extends beyond the supplied bounded context.
        if(i==wordEnd || (i==0&&caret>before.length()))return null;
        return new Plan(end-i,first);
    }
    private static boolean wordPart(int cp){
        int kind=Character.getType(cp);
        return Character.isLetterOrDigit(cp)||kind==Character.NON_SPACING_MARK
                ||kind==Character.COMBINING_SPACING_MARK||kind==Character.ENCLOSING_MARK;
    }
    private static boolean joiner(int cp){return cp=='\''||cp==0x2019||cp=='-'||cp==0x2010||cp==0x05f3||cp==0x05f4;}
    private static boolean separator(int cp){
        int kind=Character.getType(cp);
        return Character.isWhitespace(cp)||Character.isSpaceChar(cp)
                ||kind==Character.CONNECTOR_PUNCTUATION||kind==Character.DASH_PUNCTUATION
                ||kind==Character.START_PUNCTUATION||kind==Character.END_PUNCTUATION
                ||kind==Character.INITIAL_QUOTE_PUNCTUATION||kind==Character.FINAL_QUOTE_PUNCTUATION
                ||kind==Character.OTHER_PUNCTUATION;
    }
}
