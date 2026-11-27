package com.ajadi.keys;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.ajadi.keys.KeyLayout.Language;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local learning survives independently installed or replaced public corpora. */
final class SuggestionRepository {
    private static SuggestionRepository instance;

    static synchronized SuggestionRepository get(Context context) {
        if (instance==null) instance=new SuggestionRepository(context.getApplicationContext());
        return instance;
    }

    private final Context appContext;
    private final java.util.Set<Language> loaded=java.util.EnumSet.noneOf(Language.class);
    private final java.util.Set<Language> failed=java.util.EnumSet.noneOf(Language.class);
    private final Map<Language,LanguagePack.State> packs=new java.util.EnumMap<>(Language.class);
    private final SuggestionModel model=new SuggestionModel();
    private final CorrectionEngine corrections=new CorrectionEngine(model);
    private final SharedPreferences disk, blockedDisk;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable saveTask=this::flush;
    private boolean dirty;

    private SuggestionRepository(Context context) {
        appContext=context;
        disk=context.getSharedPreferences("learned_predictions_v1",Context.MODE_PRIVATE);
        blockedDisk=context.getSharedPreferences("blocked_suggestions_v1",Context.MODE_PRIVATE);
        model.restoreBlocked(blockedDisk.getStringSet("words",java.util.Collections.emptySet()));
        try {
            JSONObject json=new JSONObject(disk.getString("counts","{}"));
            Map<String,Integer> counts=new LinkedHashMap<>();
            Iterator<String> keys=json.keys();
            while(keys.hasNext()&&counts.size()<SuggestionModel.MAX_PERSONAL_GRAMS) {
                String key=keys.next(); counts.put(key,json.optInt(key,0));
            }
            model.restore(counts);
        } catch(org.json.JSONException ignored) { model.clear(); }
    }

    /** Called on input start, package change, and settings changes, not on each keystroke. */
    boolean refreshPacks(){
        boolean changed=false;
        for(Language language:Language.values()){
            LanguagePack.State next=LanguagePack.inspect(appContext,language);
            if(!next.same(packs.get(language))){
                packs.put(language,next);loaded.remove(language);failed.remove(language);
                model.removeCorpus(language);changed=true;
            }
        }
        return changed;
    }
    LanguagePack.State packState(Language language){
        if(!packs.containsKey(language))refreshPacks();
        return packs.get(language);
    }
    boolean packFailed(Language language){return failed.contains(language);}
    void validatePack(Language language){ensureLanguage(language);}
    private void ensureLanguage(Language language){
        LanguagePack.State state=packState(language);
        if(!state.active()||!loaded.add(language))return;
        String prefix="words/"+LanguagePack.id(language);
        try{
            android.content.res.AssetManager assets=LanguagePack.assets(appContext,language);
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(assets.open(prefix+".tsv"),StandardCharsets.UTF_8))){
                String line;int count=0;
                while((line=reader.readLine())!=null){
                    String[] piece=line.split("\t",2);
                    if(++count>80000||line.length()>256||piece.length!=2)throw new java.io.IOException("Invalid lexicon");
                    int n=Integer.parseInt(piece[1]);if(n<=0)throw new java.io.IOException("Invalid frequency");
                    model.addCorpusWord(language,piece[0],n);
                }
                if(count==0)throw new java.io.IOException("Empty lexicon");
            }
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(assets.open("words/contexts.tsv"),StandardCharsets.UTF_8))){
                String line;int count=0;
                while((line=reader.readLine())!=null){
                    String[] parts=line.split("\t",2);
                    if(++count>10000||line.length()>1024||parts.length!=2||!language.name().equals(parts[0]))
                        throw new java.io.IOException("Invalid contexts");
                    model.seedPhrase(language,parts[1]);
                }
            }
            try(java.io.InputStream in=assets.open(prefix+".akn")){model.loadChat(language,in);}
            try(java.io.InputStream in=assets.open(prefix+".morph.tsv")){model.loadMorphology(language,in);}
            try(java.io.InputStream in=assets.open(prefix+".akp")){model.loadProtection(language,in);}
            model.prepare(language);
        }catch(java.io.IOException|android.content.pm.PackageManager.NameNotFoundException|RuntimeException invalid){
            // Keep typing and personal predictions usable, but never retain a partial corpus.
            model.removeCorpus(language);failed.add(language);
        }
    }
    List<String> suggest(Language language,WordSession session,boolean personalize) {
        ensureLanguage(language);
        return model.suggest(language,session.previous2(),session.previous1(),session.prefix(),3,personalize);
    }

    CorrectionEngine.Candidate correction(Language language,WordSession session,boolean typos,boolean layout,boolean atBoundary) {
        ensureLanguage(language);
        Language script=CorrectionEngine.detectScript(session.prefix());
        if(script!=null&&script!=language)ensureLanguage(script);
        return corrections.best(language,session.previous2(),session.previous1(),session.prefix(),typos,layout,atBoundary,
                session.adjacentPrevious2(),session.adjacentPrevious1(),session.negationJoinPrefix());
    }

    void learn(Language language,String p2,String p1,String word) {
        ensureLanguage(language);
        model.learn(language,p2,p1,word);
        dirty=true;
        handler.removeCallbacks(saveTask);
        handler.postDelayed(saveTask,1600);
    }

    void flush() {
        handler.removeCallbacks(saveTask);
        if (!dirty) return;
        dirty=false;
        disk.edit().putString("counts",new JSONObject(model.snapshot()).toString()).apply();
    }

    boolean block(Language language,String word) {
        boolean changed=model.block(language,word);
        if(changed)saveBlocked();
        return changed;
    }
    java.util.Set<String> blockedWords() {return model.blockedSnapshot();}
    void unblock(String key) { model.unblock(key);saveBlocked(); }
    void clearBlocked() { model.restoreBlocked(java.util.Collections.emptySet());saveBlocked(); }
    private void saveBlocked() {blockedDisk.edit().putStringSet("words",model.blockedSnapshot()).apply();}

    Map<String,Integer> learnedSnapshot(){return model.snapshot();}
    void replacePersonal(Map<String,Integer> counts,java.util.Set<String> blocked){
        handler.removeCallbacks(saveTask);model.restore(counts);model.restoreBlocked(blocked);
        dirty=true;flush();saveBlocked();
    }
    void clear() {
        handler.removeCallbacks(saveTask);
        dirty=false;
        model.clear();
        disk.edit().clear().apply();
    }
}
