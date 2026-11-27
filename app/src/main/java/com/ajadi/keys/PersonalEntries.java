package com.ajadi.keys;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.*;

/** Explicit local dictionary entries, shortcuts and saved phrases. No implicit capture. */
final class PersonalEntries {
    static final int MAX_ENTRIES=200,MAX_TEXT=2048;
    private final SharedPreferences disk;
    PersonalEntries(Context context){disk=context.getSharedPreferences("explicit_personal_v1",Context.MODE_PRIVATE);}
    Map<String,String> list(String section){
        Map<String,String> result=new LinkedHashMap<>();
        try{
            JSONObject json=new JSONObject(disk.getString(section,"{}"));
            Iterator<String> keys=json.keys();
            while(keys.hasNext()&&result.size()<MAX_ENTRIES){
                String k=keys.next(),v=json.optString(k,"");
                if(valid(section,k,v))result.put(k,v);
            }
        }catch(JSONException ignored){}
        return result;
    }
    static boolean valid(String section,String key,String value){
        if(key==null||value==null||key.isEmpty()||value.isEmpty()||key.length()>128||value.length()>MAX_TEXT)return false;
        if("words".equals(section)||"shortcuts".equals(section)){
            if(!SuggestionModel.validWord(key)||CorrectionEngine.detectScript(key)==null)return false;
            if("words".equals(section)&&!key.equals(value))return false;
        }else if(!"phrases".equals(section))return false;
        return true;
    }
    boolean save(String section,String oldKey,String key,String value){
        if(!valid(section,key,value))return false;
        Map<String,String> entries=list(section);
        if(oldKey!=null)entries.remove(oldKey);
        if(!entries.containsKey(key)&&entries.size()>=MAX_ENTRIES)return false;
        entries.put(key,value);replace(section,entries);return true;
    }
    void remove(String section,String key){Map<String,String> entries=list(section);entries.remove(key);replace(section,entries);}
    void replace(String section,Map<String,String> entries){disk.edit().putString(section,new JSONObject(entries).toString()).apply();}
    boolean hasShortcuts(){return !disk.getString("shortcuts","{}").equals("{}");}
    String expansion(String trigger){
        if(trigger==null)return null;
        KeyLayout.Language lang=CorrectionEngine.detectScript(trigger);
        String normalized=lang==null?trigger:SuggestionModel.normalise(lang,trigger);
        for(Map.Entry<String,String> entry:list("shortcuts").entrySet()){
            if(normalized.equals(lang==null?entry.getKey():SuggestionModel.normalise(lang,entry.getKey())))return entry.getValue();
        }
        return null;
    }
    List<String> words(KeyLayout.Language language,String prefix){
        List<String> result=new ArrayList<>();
        String normalized=SuggestionModel.normalise(language,prefix);
        if(normalized.isEmpty())return result;
        for(String word:list("words").values()){
            if(CorrectionEngine.detectScript(word)==language&&SuggestionModel.normalise(language,word).startsWith(normalized))
                result.add(SuggestionModel.normalise(language,word));
        }
        return result;
    }
    boolean containsWord(KeyLayout.Language language,String word){return words(language,word).contains(SuggestionModel.normalise(language,word));}
}
