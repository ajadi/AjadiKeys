package com.ajadi.keys;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static com.ajadi.keys.GrammarRanker.*;
import static com.ajadi.keys.KeyLayout.Language.RU;

/** Conservative, dictionary-validated Russian -тся/-ться contrast using adjacent context only. */
final class RussianReflexiveCorrection {
    private static final Set<String> INFINITIVE_GOVERNORS=new HashSet<>(Arrays.asList(
        "буду будешь будет будем будете будут хочу хочешь хочет хотим хотите хотят могу можешь можем можете должен должна должно должны нужно надо решил решила решили начал начала начали стал стала стали собираюсь собираешься собирается собираемся собираетесь собираются".split(" ")));
    private static String alternate(String word){
        if(word.endsWith("ться"))return word.substring(0,word.length()-4)+"тся";
        if(word.endsWith("тся"))return word.substring(0,word.length()-3)+"ться";
        return null;
    }
    static boolean isPair(String word,String other){return other.equals(alternate(word));}
    private static int subjectNumber(String word){
        if("он".equals(word)||"она".equals(word)||"оно".equals(word))return SING;
        return "они".equals(word)?PLUR:0;
    }
    private static boolean form(int[] tags,boolean infinitive,int number){
        if(tags.length==0)return false;
        for(int tag:tags){
            if((tag&15)!=VERB&&(tag&15)!=AUX)return false;
            if(infinitive){if((tag&INF)==0)return false;}
            else if((tag&FIN)==0||(tag&P3)==0||(number!=0&&(tag&number)==0))return false;
        }
        return true;
    }
    static CorrectionEngine.Candidate candidate(SuggestionModel model,String p2,String p1,String word,boolean safeCase){
        String other=alternate(word);
        if(other==null||model.isBlocked(RU,other))return null;
        boolean infinitive=word.endsWith("ться"),otherInfinitive=!infinitive;
        int[] sourceTags=model.grammar().tags(RU,word),otherTags=model.grammar().tags(RU,other);
        if(!form(otherTags,otherInfinitive,0))return null;
        String governor="не".equals(p1)?p2:p1;
        boolean wantInf=INFINITIVE_GOVERNORS.contains(governor)
                ||("может".equals(governor)&&subjectNumber(p2)==SING&&!"не".equals(p1))
                ||("могут".equals(governor)&&subjectNumber(p2)==PLUR&&!"не".equals(p1));
        int number=subjectNumber(governor);
        if(wantInf&&infinitive&&form(sourceTags,true,0))return null;
        if(number!=0&&!infinitive&&form(sourceTags,false,number))return null;
        // Never propose a third-person form with a mismatched explicit subject.
        if(number!=0&&!otherInfinitive&&!form(otherTags,false,number))return null;
        boolean contextMatches=wantInf&&otherInfinitive
                ||number!=0&&!otherInfinitive&&form(otherTags,false,number);
        // A known/learnt word without reliable morphology, or ambiguous POS, stays suggestion-only.
        boolean reliableSource=form(sourceTags,infinitive,0)
                ||sourceTags.length==0&&!model.protectedWord(RU,word);
        boolean automatic=contextMatches&&reliableSource&&safeCase;
        return new CorrectionEngine.Candidate(other,RU,CorrectionEngine.Kind.TYPO,automatic);
    }
}
