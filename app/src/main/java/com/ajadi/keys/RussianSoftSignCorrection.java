package com.ajadi.keys;

import static com.ajadi.keys.GrammarRanker.*;
import static com.ajadi.keys.KeyLayout.Language.RU;

/** Dictionary-checked ш/шь contrast, with a subject or explicit noun agreement required for automatic repair. */
final class RussianSoftSignCorrection {
    private static String alternate(String word){
        if(word.endsWith("шь"))return word.substring(0,word.length()-1);
        if(word.endsWith("ш"))return word+"ь";
        return null;
    }
    static boolean isPair(String word,String other){return other.equals(alternate(word));}
    private static boolean secondPerson(int[] tags){
        for(int tag:tags)if(((tag&15)==VERB||(tag&15)==AUX)&&(tag&(FIN|P2|SING))==(FIN|P2|SING))return true;
        return false;
    }
    private static boolean nounAgreement(int[] modifier,int[] target){
        for(int noun:target){
            if((noun&15)!=NOUN||(noun&GENDER)==0||(noun&NUMBER)==0)continue;
            for(int mod:modifier){
                int pos=mod&15;
                if(pos!=ADJ&&pos!=DET&&pos!=PRON)continue;
                if((mod&GENDER&noun)==0||(mod&NUMBER&noun)==0)continue;
                if((mod&CASE)!=0&&(noun&CASE)!=0&&(mod&CASE&noun)==0)continue;
                return true;
            }
        }
        return false;
    }
    static CorrectionEngine.Candidate candidate(SuggestionModel model,String p2,String p1,String word,boolean safeCase){
        String other=alternate(word);
        if(other==null||other.length()<3||model.isBlocked(RU,other))return null;
        int[] target=model.grammar().tags(RU,other),source=model.grammar().tags(RU,word);
        if(target.length==0)return null;
        // Valid competing word forms such as плач / плачь are never automatically replaced.
        if(source.length>0)return null;
        boolean subject="ты".equals(p1)||("не".equals(p1)&&"ты".equals(p2));
        boolean context=subject&&secondPerson(target)||nounAgreement(model.grammar().tags(RU,p1),target);
        boolean automatic=context&&!model.protectedWord(RU,word)&&safeCase;
        return new CorrectionEngine.Candidate(other,RU,CorrectionEngine.Kind.TYPO,automatic);
    }
}
