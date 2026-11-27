package com.ajadi.keys;

import com.ajadi.keys.KeyLayout.Language;
import java.io.*;
import java.text.Normalizer;
import java.util.*;

/**
 * Offline word-form model: indexed public uni/bi/trigram counts, soft grammar
 * and bounded context-local personal counts. No full message storage.
 */
final class SuggestionModel {
    static final int MAX_PERSONAL_GRAMS=8000,MAX_WORD_CHARS=40,MAX_BLOCKED=4096;
    private final Map<Language,Map<String,Integer>> frequencies=new EnumMap<>(Language.class);
    private final Map<Language,Long> baseTotals=new EnumMap<>(Language.class);
    private final Map<Language,NgramData> chat=new EnumMap<>(Language.class);
    private final Map<Language,WordProtection> protection=new EnumMap<>(Language.class);
    private final Map<Language,Double> chatScale=new EnumMap<>(Language.class);
    private final Map<Language,List<String>> lexicon=new EnumMap<>(Language.class);
    private final GrammarRanker grammar=new GrammarRanker();
    private boolean grammarEnabled=true;
    private final Map<String,Integer> seeds=new HashMap<>();
    private final LinkedHashMap<String,Integer> personal=new LinkedHashMap<>();
    private Map<String,Stats> seedIndex,personalIndex;
    private final Set<String> blocked=new HashSet<>();

    private static final class Stats {
        final Map<String,Integer> counts=new HashMap<>();
        long total;
        List<String> ordered;
        void add(String word,int count){counts.merge(word,count,Integer::sum);total+=count;}
        int count(String word){return counts.getOrDefault(word,0);}
        void collect(String prefix,Set<String> result,int limit){
            if(ordered==null){
                ordered=new ArrayList<>(counts.keySet());
                ordered.sort(Comparator.<String>comparingInt(counts::get).reversed().thenComparing(Comparator.naturalOrder()));
            }
            int n=0;
            for(String w:ordered)if(w.startsWith(prefix)){result.add(w);if(++n>=limit)break;}
        }
    }
    private static Map<String,Stats> index(Map<String,Integer> grams){
        Map<String,Stats> index=new HashMap<>();
        for(Map.Entry<String,Integer> e:grams.entrySet()){
            String k=e.getKey();int split=k.lastIndexOf('\t');if(split<0)continue;
            index.computeIfAbsent(k.substring(0,split),x->new Stats()).add(k.substring(split+1),e.getValue());
        }
        return index;
    }
    private Map<String,Stats> personalIndex(){if(personalIndex==null)personalIndex=index(personal);return personalIndex;}
    private Map<String,Stats> seedIndex(){if(seedIndex==null)seedIndex=index(seeds);return seedIndex;}
    private static Stats stats(Map<String,Stats> index,Language lang,String... context){
        return index.get(context.length==0?lang.name():key(lang,context));
    }
    boolean isBlocked(Language language,String word){return blocked.contains(key(language,normalise(language,word)));}
    boolean block(Language language,String word){
        String normalized=normalise(language,word);
        return validWord(normalized)&&blocked.size()<MAX_BLOCKED&&blocked.add(key(language,normalized));
    }
    void unblock(String storedKey){blocked.remove(storedKey);}
    Set<String> blockedSnapshot(){return new HashSet<>(blocked);}
    void restoreBlocked(Set<String> keys){
        blocked.clear();
        for(String value:keys){
            String[] parts=value.split("\t",2);
            if(parts.length==2)try{block(Language.valueOf(parts[0]),parts[1]);}catch(IllegalArgumentException ignored){}
        }
    }
    static String normalise(Language language,String word){
        String w=Normalizer.normalize(word==null?"":word,Normalizer.Form.NFC).toLowerCase(language.locale)
                .replace('\u2019','\'').replace('\u05f3','\'');
        if(language==Language.HE){
            StringBuilder plain=new StringBuilder(w.length());
            for(int i=0;i<w.length();){int cp=w.codePointAt(i);i+=Character.charCount(cp);if(Character.getType(cp)!=Character.NON_SPACING_MARK)plain.appendCodePoint(cp);}
            w=plain.toString();
        }
        return w;
    }
    static boolean validWord(String word){
        if(word==null||word.isEmpty()||word.length()>MAX_WORD_CHARS||!Character.isLetter(word.codePointAt(0)))return false;
        boolean letter=false;
        for(int i=0;i<word.length();){
            int cp=word.codePointAt(i);i+=Character.charCount(cp);
            if(Character.isLetter(cp)){letter=true;continue;}
            int category=Character.getType(cp);
            if(cp==39||cp==0x2019||cp==0x05f3||cp=='-'){
                if(i==word.length()||!Character.isLetter(word.codePointAt(i)))return false;
            }else if(category!=Character.NON_SPACING_MARK&&category!=Character.COMBINING_SPACING_MARK)return false;
        }
        return letter;
    }
    /** Discard only replaceable public data, not learning or blocked words. */
    void removeCorpus(Language language){
        frequencies.remove(language);baseTotals.remove(language);chat.remove(language);
        protection.remove(language);chatScale.remove(language);lexicon.remove(language);grammar.remove(language);
        String prefix=language.name()+"\t";
        seeds.keySet().removeIf(key->key.startsWith(prefix));seedIndex=null;
    }
    void addCorpusWord(Language language,String word,int count){
        String w=normalise(language,word);
        if(!validWord(w)||count<=0)return;
        Map<String,Integer> map=frequencies.computeIfAbsent(language,l->new HashMap<>());
        int old=map.getOrDefault(w,0);
        if(count>old){map.put(w,count);baseTotals.merge(language,(long)count-old,Long::sum);lexicon.remove(language);}
    }
    void loadChat(Language language,InputStream input)throws IOException{
        NgramData data=NgramData.read(input);
        chat.put(language,data);lexicon.remove(language);
        int baseMax=0,chatMax=0;
        Map<String,Integer> base=frequencies.get(language);if(base!=null)for(int n:base.values())baseMax=Math.max(baseMax,n);
        for(String w:data.words)chatMax=Math.max(chatMax,data.count(w));
        chatScale.put(language,chatMax==0?1:Math.max(1,baseMax)/(double)chatMax);
    }
    void loadMorphology(Language language,InputStream input)throws IOException{grammar.load(language,input);}
    void loadProtection(Language language,InputStream input)throws IOException{protection.put(language,WordProtection.read(input));}
    boolean protectedWord(Language lang,String word){
        WordProtection filter=protection.get(lang);
        return known(lang,word)||(filter!=null&&filter.mightContain(word));
    }
    void setGrammarEnabled(boolean enabled){grammarEnabled=enabled;} // deterministic ablation in QA
    GrammarRanker grammar(){return grammar;}
    void prepare(Language language){orderedLexicon(language);}
    void seedPhrase(Language language,String phrase){
        String p2="",p1="";
        for(String raw:phrase.split(" +")){
            String w=normalise(language,raw);if(!validWord(w))continue;
            if(!p1.isEmpty())seeds.merge(key(language,p1,w),1,Integer::sum);
            if(!p2.isEmpty()&&!p1.isEmpty())seeds.merge(key(language,p2,p1,w),1,Integer::sum);
            p2=p1;p1=w;
        }
        seedIndex=null;
    }
    void learn(Language language,String previous2,String previous1,String raw){
        String word=normalise(language,raw);if(!validWord(word))return;
        String p1=normalise(language,previous1),p2=normalise(language,previous2);
        increment(key(language,word));
        if(validWord(p1))increment(key(language,p1,word));
        if(validWord(p1)&&validWord(p2))increment(key(language,p2,p1,word));
        personalIndex=null;
    }
    private static String key(Language language,String... words){return language.name()+"\t"+String.join("\t",words);}
    private void increment(String k){
        Integer old=personal.remove(k);personal.put(k,Math.min(1000,old==null?1:old+1));
        while(personal.size()>MAX_PERSONAL_GRAMS){Iterator<String> it=personal.keySet().iterator();it.next();it.remove();}
    }
    private int baseCount(Language language,String word){
        Map<String,Integer> f=frequencies.get(language);return f==null?0:f.getOrDefault(word,0);
    }
    private double prior(Language language,String word){
        double base=baseCount(language,word)/(double)Math.max(1L,baseTotals.getOrDefault(language,0L));
        NgramData d=chat.get(language);
        if(d==null||d.total<=0)return Math.max(1e-11,base);
        double p=d.count(word)/(double)d.total;
        return Math.max(1e-11,.35*base+.65*p);
    }
    private List<String> orderedLexicon(Language lang){
        List<String> list=lexicon.get(lang);if(list!=null)return list;
        Set<String> words=new HashSet<>();Map<String,Integer> base=frequencies.get(lang);if(base!=null)words.addAll(base.keySet());
        NgramData d=chat.get(lang);
        if(d!=null)for(String w:d.words)if(!w.isEmpty()&&d.count(w)>0)words.add(w);
        list=new ArrayList<>(words);
        list.sort(Comparator.<String>comparingDouble(w->prior(lang,w)).reversed().thenComparing(Comparator.naturalOrder()));
        lexicon.put(lang,list);return list;
    }
    private static double mix(double previous,int count,long total,double alpha,double cap){
        if(total<=0)return previous;
        double weight=cap*total/(total+alpha);
        return previous*(1-weight)+(count/(double)total)*weight;
    }
    private static double mix(double prev,Stats evidence,String word,double alpha,double cap){
        return evidence==null?prev:mix(prev,evidence.count(word),evidence.total,alpha,cap);
    }
    private static double mix(double prev,NgramData.Followers evidence,String word,double alpha,double cap){
        return evidence==null?prev:mix(prev,evidence.count(word),evidence.total,alpha,cap);
    }
    private static int count(Stats s,String w){return s==null?0:s.count(w);}
    private static int count(NgramData.Followers s,String w){return s==null?0:s.count(w);}
    private static void collect(Stats evidence,String prefix,Set<String> result,int limit){if(evidence!=null)evidence.collect(prefix,result,limit);}
    List<String> suggest(Language language,String prev2,String prev1,String rawPrefix,int limit){
        return suggest(language,prev2,prev1,rawPrefix,limit,true);
    }
    List<String> suggest(Language language,String prev2,String prev1,String rawPrefix,int limit,boolean personalize){
        if(limit<=0)return Collections.emptyList();
        String prefix=normalise(language,rawPrefix),p1=normalise(language,prev1),p2=normalise(language,prev2);
        Set<String> candidates=new HashSet<>();int n=0;
        for(String word:orderedLexicon(language))if(word.startsWith(prefix)&&!isBlocked(language,word)){
            candidates.add(word);if(++n>=128)break;
        }
        NgramData data=chat.get(language);
        NgramData.Followers bi=data==null?null:data.bigram(p1),tri=data==null||p1.isEmpty()?null:data.trigram(p2,p1);
        if(bi!=null)bi.collect(prefix,candidates);if(tri!=null)tri.collect(prefix,candidates);
        Map<String,Stats> sindex=seedIndex(),pindex=personalize?personalIndex():Collections.emptyMap();
        Stats sb=p1.isEmpty()?null:stats(sindex,language,p1),
                st=p1.isEmpty()||p2.isEmpty()?null:stats(sindex,language,p2,p1);
        Stats pu=stats(pindex,language),
                pb=p1.isEmpty()?null:stats(pindex,language,p1),
                pt=p1.isEmpty()||p2.isEmpty()?null:stats(pindex,language,p2,p1);
        collect(sb,prefix,candidates,64);collect(st,prefix,candidates,64);
        collect(pu,prefix,candidates,64);collect(pb,prefix,candidates,64);collect(pt,prefix,candidates,64);
        Map<String,Double> scores=new HashMap<>();
        for(String word:candidates){
            if(isBlocked(language,word))continue;
            double p=prior(language,word);
            // A learned word boosts autocomplete, but does not swamp an unrelated sentence.
            p=mix(p,pu,word,30.0,p1.isEmpty()?.13:.020);
            p=mix(p,bi,word,18.0,.94);
            p=mix(p,tri,word,6.0,.95);
            // Small hand-authored fallback, subordinate to actual corpus evidence.
            p=mix(p,sb,word,bi==null?.30:2.0,bi==null?.94:.08);
            p=mix(p,st,word,tri==null?.25:1.5,tri==null?.95:.12);
            // Repeated personal context matters far more than a repeated isolated word.
            p=mix(p,pb,word,bi==null?1.2:5.0,bi==null?.90:p2.isEmpty()?.80:pt==null?.30:.70);
            p=mix(p,pt,word,2.0,.88);
            double agreement=grammarEnabled?grammar.score(language,p2,p1,word)*(prefix.isEmpty()?.70:1):0;
            if(count(pb,word)>0||count(pt,word)>0)agreement*=.25;
            // Parallel educational corpora overrepresent recurring names. Do not filter them:
            // soften a name's untyped next-word rank only when corpus frequency is disproportionate.
            if(prefix.isEmpty()&&data!=null&&grammar.isUsuallyProper(language,word)&&(!personalize||personalFrequency(language,word)==0)
                    &&data.count(word)/(double)Math.max(1L,data.total)>3.0*baseCount(language,word)/(double)Math.max(1L,baseTotals.getOrDefault(language,0L)))
                agreement-=.85;
            scores.put(word,Math.log(Math.max(1e-12,p))+agreement);
        }
        List<String> output=new ArrayList<>(scores.keySet());
        output.sort(Comparator.<String>comparingDouble(scores::get).reversed().thenComparing(Comparator.naturalOrder()));
        return new ArrayList<>(output.subList(0,Math.min(limit,output.size())));
    }
    int frequency(Language lang,String word){
        NgramData d=chat.get(lang);int base=baseCount(lang,word);
        if(d==null)return base;
        long normalized=Math.round(d.count(word)*chatScale.getOrDefault(lang,1.0));
        return (int)Math.min(Integer.MAX_VALUE,Math.max(base,normalized));
    }
    int personalFrequency(Language language,String word){return personal.getOrDefault(key(language,word),0);}
    boolean known(Language lang,String word){return frequency(lang,word)>0||personalFrequency(lang,word)>0;}
    double correctionScore(Language lang,String previous2,String previous1,String word){
        double value=Math.log10(1.0+frequency(lang,word))+1.6*Math.log1p(personalFrequency(lang,word));
        if(!previous1.isEmpty()){
            String bigram=key(lang,previous1,word);
            value+=1.5*Math.log1p(seeds.getOrDefault(bigram,0))+2.5*Math.log1p(personal.getOrDefault(bigram,0));
            if(!previous2.isEmpty()){
                String trigram=key(lang,previous2,previous1,word);
                value+=2.0*Math.log1p(seeds.getOrDefault(trigram,0))+3.0*Math.log1p(personal.getOrDefault(trigram,0));
            }
            NgramData d=chat.get(lang);
            if(d!=null){
                value+=Math.min(1.25,.25*Math.log1p(count(d.bigram(previous1),word)));
                value+=Math.min(1.0,.3*Math.log1p(count(d.trigram(previous2,previous1),word)));
            }
            if(grammarEnabled)value+=.35*grammar.score(lang,previous2,previous1,word);
        }
        return value;
    }
    boolean hasCompletion(Language lang,String prefix){
        for(String w:orderedLexicon(lang))if(w.length()>prefix.length()&&w.startsWith(prefix))return true;
        Stats u=stats(personalIndex(),lang);
        if(u!=null)for(String w:u.counts.keySet())if(w.length()>prefix.length()&&w.startsWith(prefix))return true;
        return false;
    }
    Set<String> oneEditCandidates(Language lang,String word){
        Set<String> found=new LinkedHashSet<>();
        for(String w:orderedLexicon(lang))if(CorrectionEngine.oneEdit(word,w)&&!isBlocked(lang,w))found.add(w);
        Stats u=stats(personalIndex(),lang);
        if(u!=null)for(String w:u.counts.keySet())if(CorrectionEngine.oneEdit(word,w)&&!isBlocked(lang,w))found.add(w);
        return found;
    }
    Map<String,Integer> snapshot(){return new LinkedHashMap<>(personal);}
    void restore(Map<String,Integer> counts){
        clear();
        for(Map.Entry<String,Integer> e:counts.entrySet()){
            String[] parts=e.getKey().split("\t",-1);
            if(parts.length<2||parts.length>4||e.getValue()==null||e.getValue()<=0)continue;
            Language lang;try{lang=Language.valueOf(parts[0]);}catch(IllegalArgumentException ignored){continue;}
            boolean valid=true;for(int i=1;i<parts.length;i++){parts[i]=normalise(lang,parts[i]);if(!validWord(parts[i]))valid=false;}
            if(valid&&personal.size()<MAX_PERSONAL_GRAMS)personal.merge(String.join("\t",parts),Math.min(1000,e.getValue()),(a,b)->Math.min(1000,a+b));
        }
        personalIndex=null;
    }
    void clear(){personal.clear();personalIndex=null;}
}
