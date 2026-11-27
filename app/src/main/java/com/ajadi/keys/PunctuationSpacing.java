package com.ajadi.keys;

/** Punctuation that attaches to the previous token in RU, EN and HE prose. */
final class PunctuationSpacing {
    static final int MAX_SPACES=32;
    static boolean attaches(String typed){
        return typed!=null&&typed.length()==1&&".,!?;:…)]}»”׃".indexOf(typed.charAt(0))>=0;
    }
    /** Reuse a following separator; never insert a second automatic space or remove editor text. */
    static String suggestionBoundary(CharSequence after){
        if(after==null||after.length()==0)return " ";
        char next=after.charAt(0);
        return Character.isWhitespace(next)||Character.isSpaceChar(next)||attaches(String.valueOf(next))?"":" ";
    }
    /** Only a bounded run of ordinary spaces immediately before the insertion point.
     * No tabs, nonbreaking spaces, newline, leading indentation, opening punctuation or pasted text.
     */
    static int spacesBefore(CharSequence before,String typed){
        if(!attaches(typed)||before==null)return 0;
        int end=before.length(),start=end;
        while(start>0&&before.charAt(start-1)==' '&&end-start<=MAX_SPACES)start--;
        int count=end-start;
        if(count==0||count>MAX_SPACES||start==0)return 0;
        int prior=Character.codePointBefore(before,start);
        return Character.isWhitespace(prior)||Character.isSpaceChar(prior)?0:count;
    }
}
