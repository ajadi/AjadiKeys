package com.ajadi.keys;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;

/** Explicitly confirmed usernames only, scoped by editor package. No password collection. */
final class SavedLogins {
    private static final int MAX_PER_APP=20, MAX_APPS=100;
    private static final String PREFIX="app:";
    private final SharedPreferences disk;
    SavedLogins(Context context) { disk=context.getSharedPreferences("saved_logins_v1",Context.MODE_PRIVATE); }
    private static boolean validPackage(String packageName) {
        return packageName!=null&&packageName.length()<=255&&packageName.matches("[A-Za-z0-9_.]+");
    }
    List<String> list(String packageName) {
        if(!validPackage(packageName))return Collections.emptyList();
        ArrayList<String> values=new ArrayList<>();
        try {
            JSONArray array=new JSONArray(disk.getString(PREFIX+packageName,"[]"));
            for(int i=0;i<array.length()&&values.size()<MAX_PER_APP;i++) {
                String value=array.optString(i,"");
                if(LoginFieldPolicy.validLogin(value)&&!values.contains(value))values.add(value);
            }
        }catch(JSONException|ClassCastException ignored){}
        return values;
    }
    /** Called only after the user approves the shown value. */
    boolean save(String packageName,String confirmedValue) {
        if(!validPackage(packageName)||!LoginFieldPolicy.validLogin(confirmedValue))return false;
        if(!disk.contains(PREFIX+packageName)&&all().size()>=MAX_APPS)return false;
        List<String> values=new ArrayList<>(list(packageName));
        values.remove(confirmedValue);values.add(0,confirmedValue);
        while(values.size()>MAX_PER_APP)values.remove(values.size()-1);
        write(packageName,values);return true;
    }
    void remove(String packageName,String value) {
        if(!validPackage(packageName))return;
        List<String> values=new ArrayList<>(list(packageName));values.remove(value);write(packageName,values);
    }
    private void write(String packageName,List<String> values) {
        if(values.isEmpty())disk.edit().remove(PREFIX+packageName).apply();
        else disk.edit().putString(PREFIX+packageName,new JSONArray(values).toString()).apply();
    }
    Map<String,List<String>> all() {
        Map<String,List<String>> result=new LinkedHashMap<>();
        for(String key:disk.getAll().keySet()) if(key.startsWith(PREFIX)) {
            String packageName=key.substring(PREFIX.length());
            List<String> values=list(packageName);if(!values.isEmpty())result.put(packageName,values);
        }
        return result;
    }
    void clear() {disk.edit().clear().apply();}
}
