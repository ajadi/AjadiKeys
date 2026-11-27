package com.ajadi.keys;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.ajadi.keys.KeyLayout.Language;

/** Soft grammatical reranking using compact, ambiguity-preserving observed word-form tags. */
final class GrammarRanker {
    static final int NOUN=1,PROPN=2,ADJ=3,DET=4,PRON=5,VERB=6,AUX=7,ADV=8,ADP=9,NUM=10;
    static final int NOM=1<<4,GEN=1<<5,DAT=1<<6,ACC=1<<7,INS=1<<8,LOC=1<<9,CASE=63<<4;
    static final int MASC=1<<10,FEM=1<<11,NEUT=1<<12,GENDER=7<<10;
    static final int SING=1<<13,PLUR=1<<14,NUMBER=3<<13;
    static final int P1=1<<15,P2=1<<16,P3=1<<17,PERSON=7<<15;
    static final int FIN=1<<18,INF=1<<19,PART=1<<20,GER=1<<21,FORM=15<<18;
    static final int PAST=1<<22,PRES=1<<23,FUT=1<<24,TENSE=7<<22;
    static final int DEF=1<<25,INDEF=1<<26,DEFINITE=3<<25;
    private static final int[] NONE=new int[0];
    private final Map<Language,Map<String,int[]>> data=new EnumMap<>(Language.class);
    private static final Map<String,Integer> RU_CASES=new HashMap<>();
    static {
        cases(GEN,"без для до из из-за из-под от ото у около после против вместо вокруг вдоль кроме возле среди мимо ради вследствие");
        cases(DAT,"к ко благодаря согласно вопреки");
        cases(DAT|LOC|ACC,"по");
        cases(GEN|INS,"с со между");
        cases(ACC|LOC,"в во на");
        cases(ACC|INS,"под подо за");
        cases(INS,"над надо перед передо");
        cases(ACC,"через про сквозь несмотря");
        cases(LOC|ACC,"о об обо");
        cases(LOC,"при");
    }
    private static void cases(int mask,String words){for(String w:words.split(" "))RU_CASES.put(w,mask);}
    private static final Set<String> RU_MODAL=set("хочу хочешь хочет хотим хотите хотят могу можешь может можем можете могут нужно надо должен должна должно должны буду будешь будет будем будете будут стал стала стали решил решила решили собираюсь собираешься собирается");
    private static final Set<String> EN_MODAL=set("can could will would shall should may might must do does did don't doesn't didn't can't couldn't won't wouldn't shouldn't");
    private static final Set<String> EN_PREP=set("to for with from at by of about against without between beside");
    private static final Set<String> EN_OBJECT=set("me him her us them");
    private static final Set<String> EN_SUBJECT=set("i he she we they");
    private static final Set<String> EN_TO_VERB=set("want wants wanted need needs needed try tries tried going have has had able like likes liked hope hopes plan plans decided decide");
    private static final Set<String> HE_PREP=set("עם בלי על אצל לפני אחרי בין ליד מול בתוך מתוך בגלל בשביל עבור של אל מן");
    private static Set<String> set(String words){return new HashSet<>(Arrays.asList(words.split(" ")));}
    private static int pos(int tag){return tag&15;}
    private static boolean nominal(int tag){int p=pos(tag);return p==NOUN||p==PROPN||p==PRON||p==ADJ||p==DET||p==NUM;}
    private static boolean noun(int tag){return pos(tag)==NOUN||pos(tag)==PROPN;}
    private static boolean modifier(int tag){return pos(tag)==ADJ||pos(tag)==DET;}
    private static boolean verb(int tag){return pos(tag)==VERB||pos(tag)==AUX;}
    private static boolean any(int[] tags,int p){for(int t:tags)if(pos(t)==p)return true;return false;}
    int[] tags(Language l,String w){Map<String,int[]> m=data.get(l);return m==null?NONE:m.getOrDefault(w,NONE);}
    boolean isUsuallyProper(Language l,String word){int[] forms=tags(l,word);return forms.length>0&&pos(forms[0])==PROPN;}
    void add(Language l,String word,int... tags){data.computeIfAbsent(l,k->new HashMap<>()).put(word,tags.clone());}
    void load(Language l,InputStream input)throws IOException{
        Map<String,int[]> table=new HashMap<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){
            String line;while((line=reader.readLine())!=null){
                String[] parts=line.split("\t",2);if(parts.length!=2||!SuggestionModel.validWord(parts[0]))continue;
                String[] raw=parts[1].split(",");if(raw.length<1||raw.length>12||table.size()>=60000)throw new IOException("Invalid morphology table");
                int[] tags=new int[raw.length];for(int i=0;i<raw.length;i++)try{
                    int t=Integer.parseInt(raw[i],16);if(pos(t)==0||pos(t)>14||(t>>>27)!=0)throw new NumberFormatException();tags[i]=t;
                }catch(NumberFormatException invalid){throw new IOException("Invalid morphology tag",invalid);}
                table.put(parts[0],tags);
            }
        }
        data.put(l,table);
    }
    void remove(Language language){data.remove(language);}
    private static double match(int a,int b,int mask,double good,double bad){
        int x=a&mask,y=b&mask;if(x==0||y==0)return 0;return (x&y)!=0?good:bad;
    }
    private static double nounSubject(int[] previous,int candidate,boolean russian){
        double best=Double.NEGATIVE_INFINITY;
        for(int p:previous){
            if(!noun(p)||(russian&&(p&CASE)!=0&&(p&NOM)==0))continue;
            double s=match(p,candidate,NUMBER,.35,-.75);
            if(!russian||(candidate&PAST)!=0)s+=match(p,candidate,GENDER,.35,-.75);
            best=Math.max(best,s);
        }
        return best==Double.NEGATIVE_INFINITY?0:best;
    }
    private static double bestPair(int[] previous,int candidate,boolean priorModifier,boolean he){
        double best=Double.NEGATIVE_INFINITY;
        for(int p:previous){
            if(priorModifier?!modifier(p):!noun(p))continue;
            double s=match(p,candidate,NUMBER,.55,-1.4)+match(p,candidate,GENDER,.55,-1.4);
            if(he)s+=match(p,candidate,DEFINITE,.4,-1.0);
            else s+=match(p,candidate,CASE,.65,-1.6);
            best=Math.max(best,s);
        }
        return best==Double.NEGATIVE_INFINITY?0:best;
    }
    private static int russianSubject(String w){
        switch(w){
            case "я":return P1|SING;case "ты":return P2|SING;
            case "он":return P3|SING|MASC;case "она":return P3|SING|FEM;case "оно":return P3|SING|NEUT;
            case "мы":return P1|PLUR;case "вы":return P2|PLUR;case "они":return P3|PLUR;
            default:return 0;
        }
    }
    private static int englishSubject(String w){
        switch(w){case "i":return P1|SING;case "you":return P2;case "he":case "she":case "it":return P3|SING;
            case "we":return P1|PLUR;case "they":return P3|PLUR;default:return 0;}
    }
    private static int hebrewSubject(String w){
        switch(w){
            case "אני":return P1|SING;case "אתה":return P2|SING|MASC;case "את":return P2|SING|FEM; // applied only to verb/adjective; noun path still treats direct object marker
            case "הוא":return P3|SING|MASC;case "היא":return P3|SING|FEM;
            case "אנחנו":return P1|PLUR;case "אתם":return P2|PLUR|MASC;case "אתן":return P2|PLUR|FEM;
            case "הם":return P3|PLUR|MASC;case "הן":return P3|PLUR|FEM;default:return 0;
        }
    }
    double score(Language lang,String p2,String p1,String word){
        int[] candidate=tags(lang,word);if(candidate.length==0||p1.isEmpty())return 0;
        int[] previous=tags(lang,p1);double best=Double.NEGATIVE_INFINITY;
        for(int tag:candidate){
            double s=lang==Language.RU?russian(p2,p1,word,tag,previous)
                    :lang==Language.EN?english(p2,p1,word,tag,previous)
                    :hebrew(p2,p1,word,tag,previous);
            best=Math.max(best,s);
        }
        return Math.max(-2.8,Math.min(1.8,best));
    }
    private static double russian(String p2,String p1,String word,int tag,int[] previous){
        double s=0;int governed=RU_CASES.getOrDefault(p1,0);
        boolean indirect=governed==0&&(any(previous,ADJ)||any(previous,DET));
        if(indirect)governed=RU_CASES.getOrDefault(p2,0);
        if(governed!=0){
            if(nominal(tag))s+=match(governed,tag,CASE,1.05,-1.85);
            else if(!indirect&&verb(tag))s-=.5;
        }
        if(noun(tag)||modifier(tag))s+=bestPair(previous,tag,true,false);
        int subject=russianSubject(p1);
        if(subject==0&&"не".equals(p1))subject=russianSubject(p2);
        if(subject!=0&&verb(tag)&&(tag&INF)==0){
            s+=match(subject,tag,NUMBER,.6,-1.5)+match(subject,tag,PERSON,.7,-1.6);
            if((tag&PAST)!=0)s+=match(subject,tag,GENDER,.75,-1.6);
        }
        if(subject==0&&verb(tag)&&(tag&FIN)!=0&&!RU_CASES.containsKey(p2))s+=nounSubject(previous,tag,true);
        if(RU_MODAL.contains(p1)&&verb(tag)){
            if((tag&INF)!=0)s+=1.35;
            else if((tag&FIN)!=0)s-=.65;
        }
        return s;
    }
    private static double english(String p2,String p1,String word,int tag,int[] previous){
        double s=0;int subject=englishSubject(p1);
        if(subject==0&&("not".equals(p1)||"really".equals(p1)))subject=englishSubject(p2);
        if(subject!=0&&verb(tag)){
            // Irregular present forms are explicit because tagging is ambiguous without syntax.
            boolean third=(subject&(P3|SING))==(P3|SING);
            if(word.equals("is")||word.equals("has")||word.equals("does"))s+=third?1.1:-1.8;
            else if(word.equals("am"))s+=(subject&(P1|SING))==(P1|SING)?1.1:-1.8;
            else if(word.equals("are"))s+=third||(subject&(P1|SING))==(P1|SING)?-1.6:1.0;
            else if(word.equals("have")||word.equals("do"))s+=third?-1.1:.4;
            else if((tag&PRES)!=0&&(tag&FIN)!=0){
                boolean thirdForm=(tag&(P3|SING))==(P3|SING);
                s+=third==thirdForm?.7:-1.3;
            }
        }
        if(subject==0&&verb(tag)&&(tag&PRES)!=0&&(tag&FIN)!=0){
            double best=Double.NEGATIVE_INFINITY;
            for(int p:previous)if(noun(p)&&(p&NUMBER)!=0){
                boolean singular=(p&SING)!=0,third=(tag&(P3|SING))==(P3|SING);
                best=Math.max(best,singular==third?.35:-.65);
            }
            if(best!=Double.NEGATIVE_INFINITY)s+=best;
        }
        if((EN_MODAL.contains(p1)||("to".equals(p1)&&EN_TO_VERB.contains(p2)))&&verb(tag)){
            if((tag&INF)!=0)s+=1.4;
            else if((tag&PAST)!=0||(tag&(P3|SING))==(P3|SING))s-=1.8;
            else if((tag&PRES)!=0)s+=.65;
        }
        int number=0;
        switch(p1){case "a":case "an":case "this":case "that":case "one":number=SING;break;
            case "these":case "those":case "many":case "several":case "both":number=PLUR;break;default:break;}
        if(number!=0&&noun(tag))s+=match(number,tag,NUMBER,.9,-1.7);
        if(pos(tag)==PRON&&EN_PREP.contains(p1)){
            if(EN_OBJECT.contains(word))s+=.7;
            else if(EN_SUBJECT.contains(word))s-=1.2;
        }
        return s;
    }
    private static double hebrew(String p2,String p1,String word,int tag,int[] previous){
        double s=0;int subject=hebrewSubject(p1);
        if(subject==0&&"לא".equals(p1))subject=hebrewSubject(p2);
        if(subject!=0&&(verb(tag)||pos(tag)==ADJ)&&(tag&INF)==0){
            s+=match(subject,tag,NUMBER,.6,-1.5)+match(subject,tag,GENDER,.75,-1.6);
            if(verb(tag))s+=match(subject,tag,PERSON,.5,-1.2);
        }
        if(subject==0&&verb(tag)&&(tag&INF)==0&&!HE_PREP.contains(p2)&&!"את".equals(p2))s+=nounSubject(previous,tag,false);
        if(pos(tag)==ADJ)s+=bestPair(previous,tag,false,true);
        if(HE_PREP.contains(p1)&&nominal(tag))s+=.35;
        if("את".equals(p1)&&noun(tag)&&(tag&DEF)!=0)s+=.45;
        return s;
    }
}
