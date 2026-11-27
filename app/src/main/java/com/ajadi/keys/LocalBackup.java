package com.ajadi.keys;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit SAF backup, no credentials, clipboard, saved logins, service keys or model files. */
final class LocalBackup {
    static final int MAX_BYTES=4*1024*1024;
    static final Set<String> BOOLEANS=new HashSet<>(Arrays.asList(
        "showGlobe","showComputer","showEmoji","autoCapitalize","numberRow","doubleDeleteWord","compactLandscape","compactRight",
        "clipboardHistory","digitPaste","suggestions","learnStyle","autocorrect","wrongLayout","autoLanguage","savedLogins",
        "systemAutofill","showTranslator","showVoice","showAiButton","languagePackEnabled_ru","languagePackEnabled_en","languagePackEnabled_he","expandShortcuts"));
    static final Set<String> INTS=new HashSet<>(Arrays.asList("height","compactWidth","transparentOpacity","customOpacity",BackspacePress.CLEAR_PREF));
    private static Set<String> set(String... values){return new HashSet<>(Arrays.asList(values));}
    static final Map<String,Set<String>> CHOICES=new HashMap<>();
    static{
        for(String key:Arrays.asList("primaryA","primaryB","language","lastPrimary"))CHOICES.put(key,set("RU","EN","HE"));
        CHOICES.put("theme",set("light","dark","system","adaptive","transparent","custom"));
        CHOICES.put("suggestionDisplay",set("adaptive","marquee","fixed"));
        CHOICES.put("voiceModel",set("small","base"));
        CHOICES.put(BackspacePress.MODE_PREF,set(BackspacePress.HOLD_MODE,BackspacePress.TRIPLE_MODE));
    }
    static JSONObject create(Context context)throws JSONException{
        JSONObject doc=new JSONObject(),settings=new JSONObject();
        SharedPreferences prefs=context.getSharedPreferences("appearance",Context.MODE_PRIVATE);
        for(Map.Entry<String,?> e:prefs.getAll().entrySet())if(validSetting(e.getKey(),e.getValue()))settings.put(e.getKey(),e.getValue());
        PersonalEntries entries=new PersonalEntries(context);
        SuggestionRepository repo=SuggestionRepository.get(context);
        doc.put("version",1);doc.put("settings",settings);
        doc.put("words",new JSONObject(entries.list("words")));doc.put("shortcuts",new JSONObject(entries.list("shortcuts")));
        doc.put("learning",new JSONObject(repo.learnedSnapshot()));doc.put("blocked",new JSONArray(repo.blockedWords()));
        doc.put("emojiUsage",new EmojiCatalog(context).exportUsage());
        return doc;
    }
    static boolean validSetting(String key,Object value){
        if(BOOLEANS.contains(key))return value instanceof Boolean;
        if(INTS.contains(key)&&value instanceof Integer){
            int n=(Integer)value;
            return "height".equals(key)?n>=40&&n<=64:"compactWidth".equals(key)?n==CompactLayout.clampPercent(n):("transparentOpacity".equals(key)||"customOpacity".equals(key))?n>=0&&n<=100:n==BackspacePress.clearDelay(n);
        }
        if(Arrays.asList("customBackground","customKey","customText","customAccent").contains(key))return value instanceof String&&KeyboardPalette.validColor((String)value);
        return CHOICES.containsKey(key)&&value instanceof String&&CHOICES.get(key).contains(value);
    }
    static JSONObject read(InputStream in)throws Exception{
        if(in==null)throw new IOException("Missing file");
        try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int total=0,n;
            while((n=input.read(buffer))!=-1){total+=n;if(total>MAX_BYTES)throw new IOException("Backup too large");out.write(buffer,0,n);}
            JSONObject doc=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));validate(doc);return doc;
        }
    }
    static Map<String,String> entries(JSONObject doc,String section)throws JSONException{
        JSONObject object=doc.getJSONObject(section);
        if(object.length()>PersonalEntries.MAX_ENTRIES)throw new JSONException("Too many entries");
        Map<String,String> result=new LinkedHashMap<>();
        Iterator<String> keys=object.keys();
        while(keys.hasNext()){String k=keys.next();Object v=object.get(k);
            if(!(v instanceof String)||!PersonalEntries.valid(section,k,(String)v))throw new JSONException("Invalid entry");
            result.put(k,(String)v);}
        return result;
    }
    static Map<String,Integer> learning(JSONObject doc)throws JSONException{
        JSONObject object=doc.getJSONObject("learning");
        if(object.length()>SuggestionModel.MAX_PERSONAL_GRAMS)throw new JSONException("Too many counts");
        Map<String,Integer> result=new LinkedHashMap<>();
        Iterator<String> keys=object.keys();
        while(keys.hasNext()){
            String k=keys.next();Object n=object.get(k);
            if(!(n instanceof Integer)||(Integer)n<1||(Integer)n>1000||k.length()>256)throw new JSONException("Invalid count");
            String[] fields=k.split("\\t",-1);
            if(fields.length<2||fields.length>4||!set("RU","EN","HE").contains(fields[0]))throw new JSONException("Invalid count");
            for(int i=1;i<fields.length;i++)if(!SuggestionModel.validWord(fields[i]))throw new JSONException("Invalid word");
            result.put(k,(Integer)n);
        }
        return result;
    }
    static Set<String> blocked(JSONObject doc)throws JSONException{
        JSONArray array=doc.getJSONArray("blocked");
        if(array.length()>10000)throw new JSONException("Too many blocked words");
        Set<String> result=new HashSet<>();
        for(int i=0;i<array.length();i++){
            String k=array.getString(i);String[] parts=k.split("\\t",-1);
            if(parts.length!=2||!set("RU","EN","HE").contains(parts[0])||!SuggestionModel.validWord(parts[1]))throw new JSONException("Invalid blocked word");
            result.add(k);
        }
        return result;
    }
    static void validate(JSONObject doc)throws JSONException{
        if(doc.getInt("version")!=1)throw new JSONException("Unsupported version");
        JSONObject settings=doc.getJSONObject("settings");
        Iterator<String> keys=settings.keys();
        while(keys.hasNext()){String key=keys.next();if(!validSetting(key,settings.get(key)))throw new JSONException("Invalid setting");}
        if(settings.optString("primaryA","RU").equals(settings.optString("primaryB","EN")))throw new JSONException("Invalid language pair");
        entries(doc,"words");entries(doc,"shortcuts");learning(doc);blocked(doc);
        if(doc.has("emojiUsage"))EmojiCatalog.validateUsage(doc.getJSONObject("emojiUsage"));
    }
    static void apply(Context context,JSONObject doc)throws JSONException{
        validate(doc);
        SharedPreferences.Editor edit=context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit();
        JSONObject settings=doc.getJSONObject("settings");
        for(String key:BOOLEANS)edit.remove(key);
        for(String key:INTS)edit.remove(key);
        for(String key:CHOICES.keySet())edit.remove(key);
        for(String key:Arrays.asList("customBackground","customKey","customText","customAccent"))edit.remove(key);
        Iterator<String> keys=settings.keys();
        while(keys.hasNext()){
            String k=keys.next();Object v=settings.get(k);
            if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);
            else if(v instanceof Integer)edit.putInt(k,(Integer)v);
            else edit.putString(k,(String)v);
        }
        PersonalEntries entries=new PersonalEntries(context);
        entries.replace("words",entries(doc,"words"));entries.replace("shortcuts",entries(doc,"shortcuts"));
        SuggestionRepository.get(context).replacePersonal(learning(doc),blocked(doc));
        new EmojiCatalog(context).importUsage(doc.optJSONObject("emojiUsage"));
        edit.apply();
    }
}
