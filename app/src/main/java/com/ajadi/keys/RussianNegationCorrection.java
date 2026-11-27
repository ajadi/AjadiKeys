package com.ajadi.keys;

import static com.ajadi.keys.GrammarRanker.*;
import static com.ajadi.keys.KeyLayout.Language.RU;

/** Dictionary-validated Russian negation boundaries; semantic ambiguity stays suggestion-only. */
final class RussianNegationCorrection {
    private static boolean verb(int[] tags){
        if(tags.length==0)return false;
        for(int tag:tags)
            if(((tag&15)!=VERB&&(tag&15)!=AUX)||(tag&(FIN|INF|GER))==0)return false;
        return true;
    }
    static CorrectionEngine.Candidate candidate(SuggestionModel model,String word,String exactNegationPrefix,
                                                  boolean safeSplitCase,boolean safeJoinCase){
        if(!exactNegationPrefix.isEmpty()){
            String joined="не"+word;
            if(SuggestionModel.validWord(joined)&&!model.isBlocked(RU,joined)){
                int[] joinedTags=model.grammar().tags(RU,joined),bareTags=model.grammar().tags(RU,word);
                if(joinedTags.length>0){
                    boolean bareUnknown=bareTags.length==0&&!model.protectedWord(RU,word);
                    boolean automatic=verb(joinedTags)&&bareUnknown&&safeJoinCase;
                    // Prefix typing must not propose a merge unless a complete dictionary form is available.
                    if(bareUnknown||bareTags.length>0||model.known(RU,word))
                        return new CorrectionEngine.Candidate(joined,RU,CorrectionEngine.Kind.NEGATION_JOIN,automatic);
                }
            }
        }
        if(!word.startsWith("не")||word.length()<5)return null;
        String bare=word.substring(2);
        if(model.isBlocked(RU,bare)||!SuggestionModel.validWord(bare))return null;
        int[] bareTags=model.grammar().tags(RU,bare),joinedTags=model.grammar().tags(RU,word);
        if(bareTags.length==0)return null;
        boolean joinedUnknown=joinedTags.length==0&&!model.protectedWord(RU,word);
        // Verbs/Gerunds normally separate; known lexicalized forms and недо- are never blindly split.
        boolean automatic=verb(bareTags)&&joinedUnknown&&!word.startsWith("недо")&&safeSplitCase;
        if(automatic||joinedTags.length>0||model.known(RU,word)||verb(bareTags))
            return new CorrectionEngine.Candidate("не "+bare,RU,CorrectionEngine.Kind.NEGATION_SPLIT,automatic);
        return null;
    }
}
