package com.ajadi.keys;

import com.ajadi.keys.KeyLayout.Language;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Conservative, offline single-edit and wrong-layout candidates. */
final class CorrectionEngine {
    enum Kind { TYPO, LAYOUT, NEGATION_SPLIT, NEGATION_JOIN }
    static final class Candidate {
        final String word;
        final Language language;
        final Kind kind;
        final boolean automatic;
        Candidate(String word,Language language,Kind kind,boolean automatic) {
            this.word=word; this.language=language; this.kind=kind; this.automatic=automatic;
        }
    }
    private static final class Rated {
        final String word; final Language language; final double score;
        Rated(String word,Language language,double score) {
            this.word=word;this.language=language;this.score=score;
        }
    }
    private final SuggestionModel model;
    CorrectionEngine(SuggestionModel model) { this.model=model; }

    // Physical key positions of the standard RU, QWERTY and Hebrew layouts.
    // U+0000 is an unassigned position, not an input character.
    private static final String EN_KEYS="`qwertyuiop[]asdfghjkl;'zxcvbnm,./";
    private static final String RU_KEYS="\u0451\u0439\u0446\u0443\u043a\u0435\u043d\u0433\u0448\u0449\u0437\u0445\u044a\u0444\u044b\u0432\u0430\u043f\u0440\u043e\u043b\u0434\u0436\u044d\u044f\u0447\u0441\u043c\u0438\u0442\u044c\u0431\u044e.";
    private static final String HE_KEYS="\u0000/\u0027\u05e7\u05e8\u05d0\u05d8\u05d5\u05df\u05dd\u05e4][\u05e9\u05d3\u05d2\u05db\u05e2\u05d9\u05d7\u05dc\u05da\u05e3,\u05d6\u05e1\u05d1\u05d4\u05e0\u05de\u05e6\u05ea\u05e5.";
    private static String keys(Language lang) {
        return lang==Language.RU?RU_KEYS:lang==Language.HE?HE_KEYS:EN_KEYS;
    }
    static Language detectScript(String token) {
        Language found=null;
        for(int i=0;i<token.length();) {
            int cp=token.codePointAt(i); i+=Character.charCount(cp);
            if(!Character.isLetter(cp)) continue;
            Character.UnicodeScript script=Character.UnicodeScript.of(cp);
            Language l=script==Character.UnicodeScript.CYRILLIC?Language.RU
                    :script==Character.UnicodeScript.LATIN?Language.EN
                    :script==Character.UnicodeScript.HEBREW?Language.HE:null;
            if(l==null || (found!=null&&found!=l)) return null;
            found=l;
        }
        return found;
    }
    static String convertLayout(Language from,Language to,String token) {
        if(from==to) return token;
        String src=keys(from), dst=keys(to), lower=SuggestionModel.normalise(from,token);
        StringBuilder out=new StringBuilder();
        for(int i=0;i<lower.length();i++) {
            int pos=src.indexOf(lower.charAt(i));
            if(pos<0 || pos>=dst.length() || dst.charAt(pos)==0 || !Character.isLetter(dst.charAt(pos))) return null;
            out.append(dst.charAt(pos));
        }
        return detectScript(out.toString())==to?out.toString():null;
    }
    static String convertSelectedLayout(Language from,Language to,String text){
        if(from==to||text==null||text.isEmpty()||text.length()>512||detectScript(text)!=from)return null;
        StringBuilder result=new StringBuilder();
        String src=keys(from),dst=keys(to);
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i),lower=Character.toLowerCase(c);
            if(!Character.isLetter(c)){result.append(c);continue;}
            int pos=src.indexOf(lower);
            if(pos<0||pos>=dst.length()||dst.charAt(pos)==0||!Character.isLetter(dst.charAt(pos)))return null;
            result.append(Character.isUpperCase(c)?Character.toUpperCase(dst.charAt(pos)):dst.charAt(pos));
        }
        return result.toString();
    }
    static boolean oneEdit(String a,String b) {
        if(a.equals(b)||Math.abs(a.length()-b.length())>1) return false;
        if(a.length()==b.length()) {
            int first=-1,second=-1;
            for(int i=0;i<a.length();i++) if(a.charAt(i)!=b.charAt(i)) {
                if(first<0)first=i;else if(second<0)second=i;else return false;
            }
            return second<0 || (second==first+1 && a.charAt(first)==b.charAt(second) && a.charAt(second)==b.charAt(first));
        }
        String longer=a.length()>b.length()?a:b, shorter=longer==a?b:a;
        int i=0,j=0;boolean skipped=false;
        while(j<shorter.length()) {
            if(longer.charAt(i)==shorter.charAt(j)) { i++;j++; }
            else { if(skipped)return false;skipped=true;i++; }
        }
        return true;
    }
    static String caseLike(String original,String candidate,Language language) {
        if(original.isEmpty()) return candidate;
        boolean upper=true, hasCase=false;
        for(int i=0;i<original.length();i++) {
            if(Character.isLowerCase(original.charAt(i))) upper=false;
            if(Character.isUpperCase(original.charAt(i))) hasCase=true;
        }
        if(upper&&hasCase)return candidate.toUpperCase(language.locale);
        if(Character.isUpperCase(original.codePointAt(0))&&!candidate.isEmpty())
            return candidate.substring(0,1).toUpperCase(language.locale)+candidate.substring(1);
        return candidate;
    }
    private static boolean safeCase(String token) {
        // Keep user-entered mixed case, acronyms and product names intact.
        for(int i=1;i<token.length();i++)if(Character.isUpperCase(token.charAt(i)))return false;
        return true;
    }
    /** Only explicit word boundaries; apostrophes and hyphens remain part of words. */
    static boolean automaticBoundary(String text){
        return text!=null&&text.length()==1&&" .,!?:;".indexOf(text.charAt(0))>=0;
    }
    private enum Edit { TRANSPOSE, REPEATED, ADJACENT, HEBREW_FINAL, OMISSION, DISTANT, OTHER }
    private static final java.util.Map<Language,java.util.Map<Character,Integer>> POSITIONS=new java.util.EnumMap<>(Language.class);
    static{
        for(Language lang:Language.values()){
            java.util.Map<Character,Integer> p=new java.util.HashMap<>();
            String[][] rows=KeyLayout.letters(lang);
            for(int i=0;i<rows.length;i++)for(int j=0;j<rows[i].length;j++)if(rows[i][j].length()==1)
                p.put(rows[i][j].charAt(0),i*32+j);
            POSITIONS.put(lang,p);
        }
    }
    private static boolean adjacent(Language language,char a,char b){
        Integer x=POSITIONS.get(language).get(a),y=POSITIONS.get(language).get(b);
        return x!=null&&y!=null&&Math.abs(x/32-y/32)<=1&&Math.abs(x%32-y%32)<=1;
    }
    private static boolean finalLetter(char c){return "ךםןףץ".indexOf(c)>=0;}
    private static boolean medialWithFinal(char c){return "כמנפצ".indexOf(c)>=0;}
    private static Edit editType(Language lang,String source,String target){
        if(source.length()==target.length()){
            int first=-1,second=-1;
            for(int i=0;i<source.length();i++)if(source.charAt(i)!=target.charAt(i)){
                if(first<0)first=i;else if(second<0)second=i;else return Edit.OTHER;
            }
            if(second==first+1&&second>=0&&source.charAt(first)==target.charAt(second)&&source.charAt(second)==target.charAt(first))
                return Edit.TRANSPOSE;
            if(first>=0&&second<0){
                if(lang==Language.HE&&first==source.length()-1&&finalLetter(target.charAt(first))
                        &&(medialWithFinal(source.charAt(first))||adjacent(lang,source.charAt(first),target.charAt(first))))return Edit.HEBREW_FINAL;
                if(adjacent(lang,source.charAt(first),target.charAt(first)))return Edit.ADJACENT;
                return lang==Language.RU?Edit.DISTANT:Edit.OTHER;
            }
        }else if(Math.abs(source.length()-target.length())==1){
            String longer=source.length()>target.length()?source:target,shorter=longer==source?target:source;
            int i=0;while(i<shorter.length()&&longer.charAt(i)==shorter.charAt(i))i++;
            if((i>0&&longer.charAt(i-1)==longer.charAt(i))||(i+1<longer.length()&&longer.charAt(i+1)==longer.charAt(i)))
                return Edit.REPEATED;
            if(lang==Language.RU&&target.length()==source.length()+1&&i>0&&i<source.length()&&source.length()>=5)return Edit.OMISSION;
        }
        return Edit.OTHER;
    }
    private static double plausibility(Edit edit){
        switch(edit){case TRANSPOSE:return 1.55;case REPEATED:return 1.05;case HEBREW_FINAL:return 1.30;case ADJACENT:return .45;case OMISSION:return 1.30;case DISTANT:return -1.50;default:return 0;}
    }
    private static boolean strong(Edit edit){return edit==Edit.TRANSPOSE||edit==Edit.REPEATED||edit==Edit.HEBREW_FINAL;}
    Candidate best(Language layout,String p2,String p1,String raw,boolean typos,boolean wrongLayout){
        return best(layout,p2,p1,raw,typos,wrongLayout,false);
    }
    Candidate best(Language layout,String p2,String p1,String raw,boolean typos,boolean wrongLayout,boolean atBoundary) {
        return best(layout,p2,p1,raw,typos,wrongLayout,atBoundary,p2,p1);
    }
    Candidate best(Language layout,String p2,String p1,String raw,boolean typos,boolean wrongLayout,boolean atBoundary,String adjacentP2,String adjacentP1) {
        return best(layout,p2,p1,raw,typos,wrongLayout,atBoundary,adjacentP2,adjacentP1,"");
    }
    Candidate best(Language layout,String p2,String p1,String raw,boolean typos,boolean wrongLayout,boolean atBoundary,String adjacentP2,String adjacentP1,String negationPrefix) {
        if(raw.length()<3 || raw.length()>SuggestionModel.MAX_WORD_CHARS || !SuggestionModel.validWord(raw)) return null;
        Language script=detectScript(raw);
        if(script==null)return null;
        Language source=script;
        String word=SuggestionModel.normalise(source,raw);
        if(typos&&source==Language.RU){
            Candidate negation=RussianNegationCorrection.candidate(model,word,negationPrefix,
                    safeCase(raw),safeCase(negationPrefix+raw));
            if(negation!=null)return negation;
            Candidate reflexive=RussianReflexiveCorrection.candidate(model,
                    SuggestionModel.normalise(source,adjacentP2),SuggestionModel.normalise(source,adjacentP1),word,safeCase(raw));
            if(reflexive!=null)return reflexive;
            Candidate soft=RussianSoftSignCorrection.candidate(model,
                    SuggestionModel.normalise(source,adjacentP2),SuggestionModel.normalise(source,adjacentP1),word,safeCase(raw));
            if(soft!=null)return soft;
        }
        if(model.protectedWord(source,word))return null; // Preserve lexicon, morphology and personal word forms.
        boolean completion=model.hasCompletion(source,word);
        // During typing, protect normal prefix completion. At a deliberate boundary a strong
        // transposition/repeated-key/final-form typo may be corrected even if it has completions.
        if(completion&&!atBoundary)return null;
        List<Rated> converted=new ArrayList<>();
        if(wrongLayout && source==layout&&!completion) for(Language target:Language.values()) if(target!=source) {
            String candidate=convertLayout(source,target,word);
            if(candidate!=null&&model.known(target,candidate)&&!model.isBlocked(target,candidate))
                converted.add(new Rated(candidate,target,model.correctionScore(target,"","",candidate)));
        }
        converted.sort(Comparator.comparingDouble((Rated x)->x.score).reversed());
        if(!converted.isEmpty()) {
            Rated first=converted.get(0);
            double gap=converted.size()==1?Double.POSITIVE_INFINITY:first.score-converted.get(1).score;
            if(gap>=0.45)return new Candidate(first.word,first.language,Kind.LAYOUT,
                    word.length()>=4&&gap>=1.15&&first.score>=3.8&&safeCase(raw));
        }
        if(!typos)return null;
        List<Rated> nearby=new ArrayList<>();
        String previous1=SuggestionModel.normalise(source,p1),previous2=SuggestionModel.normalise(source,p2);
        for(String candidate:model.oneEditCandidates(source,word)){
            if(candidate.startsWith(word))continue;
            Edit edit=editType(source,word,candidate);
            if(completion&&!strong(edit))continue;
            nearby.add(new Rated(candidate,source,model.correctionScore(source,previous2,previous1,candidate)+plausibility(edit)));
        }
        nearby.sort(Comparator.comparingDouble((Rated x)->x.score).reversed().thenComparing(x->x.word));
        if(nearby.isEmpty())return null;
        Rated first=nearby.get(0);Edit edit=editType(source,word,first.word);
        double gap=nearby.size()==1?Double.POSITIVE_INFINITY:first.score-nearby.get(1).score;
        boolean high=strong(edit);
        // Short words can only be silently repaired for a very frequent adjacent transposition.
        boolean shortAllowed=word.length()>=4||edit==Edit.TRANSPOSE&&model.frequency(source,first.word)>=10000;
        boolean automatic=shortAllowed&&first.score>=(high?4.2:3.8)&&gap>=(high?.65:.90)&&safeCase(raw)
                &&(!completion||atBoundary&&high&&model.frequency(source,first.word)>=10000&&gap>=.95);
        // An omitted interior key in an otherwise unknown, sufficiently long word:
        // accept only a supported and distinctly better candidate at a deliberate boundary.
        if(edit==Edit.OMISSION&&atBoundary&&!completion&&word.length()>=5&&safeCase(raw)
                &&!(source==Language.RU&&(word.endsWith("тся")||word.endsWith("ться")||first.word.endsWith("тся")||first.word.endsWith("ться")))
                &&model.frequency(source,first.word)>=350&&first.score>=3.25&&gap>=.85)automatic=true;
        // A bare one-edit match is not sufficient evidence for the Russian reflexive ending.
        if(source==Language.RU&&(RussianReflexiveCorrection.isPair(word,first.word)
                ||RussianSoftSignCorrection.isPair(word,first.word)
                ||edit==Edit.OMISSION&&(word.endsWith("тся")||word.endsWith("ться")||first.word.endsWith("тся")||first.word.endsWith("ться"))))automatic=false;
        return new Candidate(first.word,first.language,Kind.TYPO,automatic);
    }
}
