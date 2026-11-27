package com.ajadi.keys;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Offline Unicode CLDR search and local usage counts. No surrounding text is stored. */
final class EmojiCatalog {
    private final Context context;
    private final SharedPreferences usage;
    private final Map<String,String> keywords=new LinkedHashMap<>();
    EmojiCatalog(Context context){this.context=context;usage=context.getSharedPreferences("emoji_usage_v1",Context.MODE_PRIVATE);}
    private void load(){
        if(!keywords.isEmpty())return;
        try(BufferedReader in=new BufferedReader(new InputStreamReader(context.getAssets().open("emoji/keywords.tsv"),StandardCharsets.UTF_8))){
            String line;while((line=in.readLine())!=null){String[] pair=line.split("\\t",2);if(pair.length==2)keywords.put(pair[0],normalize(pair[1]));}
        }catch(IOException ignored){}
    }
    static String normalize(String text){return text.toLowerCase(Locale.ROOT).replace('ё','е').trim();}
    List<String> search(String query,boolean personal){
        load();List<String> result=new ArrayList<>();
        String q=normalize(query);
        if(q.isEmpty())return result;
        String[] terms=q.split("\\s+");
        for(Map.Entry<String,String> entry:keywords.entrySet()){
            boolean found=true;
            for(String t:terms){
                boolean matches=false;
                for(String word:entry.getValue().split("[^\\p{L}\\p{N}]+"))if(word.startsWith(t)){matches=true;break;}
                if(!matches){found=false;break;}
            }
            if(found)result.add(entry.getKey());
        }
        return rank(result,personal);
    }
    List<String> rank(List<String> source,boolean personal){
        List<String> result=new ArrayList<>(source);
        if(personal)result.sort((a,b)->Long.compare(score(b),score(a)));
        return result;
    }
    private long score(String emoji){
        long when=usage.getLong("t:"+emoji,0);
        long age=Math.max(0,(System.currentTimeMillis()-when)/3600000);
        return Math.min(usage.getInt("n:"+emoji,0),1000)*8L+(when==0?0:Math.max(0,168-age));
    }
    void used(String emoji,boolean personal){
        if(!personal)return;load();if(!keywords.containsKey(emoji))return;
        usage.edit().putInt("n:"+emoji,Math.min(1000,usage.getInt("n:"+emoji,0)+1))
            .putLong("t:"+emoji,System.currentTimeMillis()).apply();
    }
    static String context(String word){
        switch(normalize(word)){
            case "спасибо":case "thanks":case "thank":case "תודה":return "🙏";
            case "люблю":case "love":case "אהבה":return "❤️";
            case "смешно":case "haha":case "lol":case "хаха":case "צחוק":return "😂";
            case "поздравляю":case "congratulations":case "congrats":case "מזל":return "🎉";
            case "отлично":case "great":case "класс":case "מעולה":return "👍";
            case "грустно":case "sad":case "печально":case "עצוב":return "😢";
            case "привет":case "hello":case "hi":case "שלום":return "👋";
            case "пицца":case "pizza":case "פיצה":return "🍕";
            case "кофе":case "coffee":case "קפה":return "☕";
            default:return null;
        }
    }
    org.json.JSONObject exportUsage()throws org.json.JSONException{
        load();org.json.JSONObject result=new org.json.JSONObject();
        for(String emoji:keywords.keySet()){
            int n=usage.getInt("n:"+emoji,0);
            if(n>0){org.json.JSONArray item=new org.json.JSONArray();item.put(n);item.put(usage.getLong("t:"+emoji,0));result.put(emoji,item);}
        }
        return result;
    }
    static void validateUsage(org.json.JSONObject object)throws org.json.JSONException{
        if(object.length()>1000)throw new org.json.JSONException("Invalid emoji data");
        java.util.Iterator<String> keys=object.keys();
        while(keys.hasNext()){
            String key=keys.next();org.json.JSONArray a=object.getJSONArray(key);
            if(key.length()>24||a.length()!=2||a.getInt(0)<1||a.getInt(0)>1000||a.getLong(1)<0
                    ||a.getLong(1)>System.currentTimeMillis()+86400000)throw new org.json.JSONException("Invalid emoji data");
        }
    }
    void importUsage(org.json.JSONObject object)throws org.json.JSONException{
        load();SharedPreferences.Editor edit=usage.edit().clear();
        if(object!=null){
            validateUsage(object);
            for(String key:keywords.keySet())if(object.has(key)){
                org.json.JSONArray a=object.getJSONArray(key);edit.putInt("n:"+key,a.getInt(0)).putLong("t:"+key,a.getLong(1));
            }
        }
        edit.apply();
    }
    void clear(){usage.edit().clear().apply();}
}
