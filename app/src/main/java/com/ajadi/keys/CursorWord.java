package com.ajadi.keys;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, transient view of one complete word and up to two preceding words. */
final class CursorWord {
    static final int BEFORE_LIMIT=160, AFTER_LIMIT=SuggestionModel.MAX_WORD_CHARS+2;
    private static final Pattern TOKEN=Pattern.compile("[\\p{L}\\p{M}\\p{Nd}]+(?:['’׳-][\\p{L}\\p{M}\\p{Nd}]+)*");
    final String left,right,previous1,previous2,next;
    final boolean grammarGap,grammarPreviousGap,hasSurrounding;
    final int previousUnits;
    private CursorWord(String left,String right,String p1,String p2,String next,
                       boolean gap,boolean previousGap,boolean surrounding,int previousUnits){
        this.left=left;this.right=right;previous1=p1;previous2=p2;this.next=next;
        grammarGap=gap;grammarPreviousGap=previousGap;hasSurrounding=surrounding;this.previousUnits=previousUnits;
    }
    String word(){return left+right;}
    boolean sameSpan(CursorWord other){
        return other!=null&&left.equals(other.left)&&right.equals(other.right)&&next.equals(other.next);
    }
    static CursorWord parse(CharSequence before,CharSequence after){
        if(before==null||after==null||before.length()>BEFORE_LIMIT||after.length()>AFTER_LIMIT)return null;
        int caret=before.length();
        String text=before.toString()+after.toString();
        Matcher m=TOKEN.matcher(text);
        ArrayList<int[]> prior=new ArrayList<>();
        int start=caret,end=caret;
        while(m.find()){
            if(m.start()<=caret&&m.end()>=caret){start=m.start();end=m.end();break;}
            if(m.end()<caret)prior.add(new int[]{m.start(),m.end()});
            if(m.start()>caret)break;
        }
        if(end-start>SuggestionModel.MAX_WORD_CHARS
                ||(start<end&&start==0&&before.length()==BEFORE_LIMIT)
                ||(start<end&&end==text.length()&&after.length()==AFTER_LIMIT))return null;
        String word=text.substring(start,end);
        if(!word.isEmpty()&&(!SuggestionModel.validWord(word)||CorrectionEngine.detectScript(word)==null))return null;
        String p1="",p2="";boolean gap=false,previousGap=false;
        int following=start;
        for(int i=prior.size()-1;i>=0;i--){
            int[] span=prior.get(i);
            if(span[1]>following)continue;
            if(span[0]==0&&before.length()==BEFORE_LIMIT)break;
            String separator=text.substring(span[1],following),token=text.substring(span[0],span[1]);
            if(sentenceEnd(separator)||!SuggestionModel.validWord(token)||CorrectionEngine.detectScript(token)==null)break;
            if(p1.isEmpty()){p1=token;gap=punctuation(separator);}
            else{p2=token;previousGap=punctuation(separator);break;}
            following=span[0];
        }
        String next=end<text.length()?text.substring(end,end+1):"";
        int previousUnits=caret==0?0:Character.charCount(text.codePointBefore(caret));
        return new CursorWord(text.substring(start,caret),text.substring(caret,end),p1,p2,next,
                gap,previousGap,!text.isEmpty(),previousUnits);
    }
    private static boolean sentenceEnd(String separator){
        for(int i=0;i<separator.length();i++)if(".!?;:\r\n".indexOf(separator.charAt(i))>=0)return true;
        return false;
    }
    private static boolean punctuation(String separator){
        for(int i=0;i<separator.length();i++)
            if(!Character.isWhitespace(separator.charAt(i))&&!Character.isSpaceChar(separator.charAt(i)))return true;
        return false;
    }
}
