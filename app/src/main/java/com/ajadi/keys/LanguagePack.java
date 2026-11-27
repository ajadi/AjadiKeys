package com.ajadi.keys;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;
import com.ajadi.keys.KeyLayout.Language;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;

/** Same-signer, code-free, independently installable corpus assets. */
final class LanguagePack {
    static final String PREFIX="com.ajadi.keys.language.";
    static final String PROTOCOL="com.ajadi.keys.LANGUAGE_PROTOCOL", ID="com.ajadi.keys.LANGUAGE_ID";
    static final String DOWNLOADS="https://github.com/ajadi/AjadiKeys/releases/tag/language-packs-v1";
    static final String RELEASE="https://github.com/ajadi/AjadiKeys/releases/download/language-packs-v1/";
    static final int FORMAT=1, KNOWN_VERSION=1;
    static final String KNOWN_NAME="1.0.0";
    private static final String[] SUFFIXES={".tsv",".akn",".morph.tsv",".akp"};
    static String id(Language language){return language.name().toLowerCase(Locale.ROOT);}
    static String packageName(Language language){return PREFIX+id(language);}
    static String pref(Language language){return "languagePackEnabled_"+id(language);}
    static int knownVersion(Language language){return language==Language.RU?2:KNOWN_VERSION;}
    static String knownName(Language language){return language==Language.RU?"1.1.0":KNOWN_NAME;}
    static String filename(Language language){return "AjadiKeys-language-"+id(language)+"-"+knownName(language)+".apk";}
    static String download(Language language){return (language==Language.RU?RELEASE.replace("language-packs-v1","language-packs-v2"):RELEASE)+filename(language);}
    static boolean enabled(Context context,Language language){
        return context.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean(pref(language),true);
    }
    static final class State {
        final boolean installed, ready, wanted;
        final String version, reason;
        final long code, updated;
        State(boolean installed,boolean ready,boolean wanted,String version,long code,long updated,String reason){
            this.installed=installed;this.ready=ready;this.wanted=wanted;this.version=version;this.code=code;this.updated=updated;this.reason=reason;
        }
        boolean active(){return ready&&wanted;}
        boolean same(State that){
            return that!=null&&installed==that.installed&&ready==that.ready&&wanted==that.wanted&&code==that.code&&updated==that.updated
                    &&Objects.equals(version,that.version)&&Objects.equals(reason,that.reason);
        }
    }
    static State inspect(Context context,Language language){
        String pkg=packageName(language);boolean wanted=enabled(context,language);
        PackageManager pm=context.getPackageManager();PackageInfo info;
        try{info=pm.getPackageInfo(pkg,PackageManager.GET_META_DATA);}
        catch(PackageManager.NameNotFoundException missing){return new State(false,false,wanted,"",0,0,"");}
        catch(SecurityException denied){return new State(false,false,wanted,"",0,0,"");}
        long code=Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
        String reason="";
        try{
            ApplicationInfo app=info.applicationInfo;
            if(pm.checkSignatures(context.getPackageName(),pkg)!=PackageManager.SIGNATURE_MATCH)reason="другая подпись";
            else if(app==null||!app.enabled)reason="отключён в Android";
            else if((app.flags&ApplicationInfo.FLAG_HAS_CODE)!=0)reason="пакет содержит код";
            else if(app.metaData==null||app.metaData.getInt(PROTOCOL,0)!=FORMAT||!id(language).equals(app.metaData.getString(ID)))
                reason="несовместимый формат или язык";
            else {
                AssetManager assets=context.createPackageContext(pkg,0).getAssets();
                for(String suffix:SUFFIXES)try(InputStream in=assets.open("words/"+id(language)+suffix)){
                    if(in.read()==-1)throw new IOException("empty asset");
                }
                try(InputStream in=assets.open("words/contexts.tsv")){if(in.read()==-1)throw new IOException("empty contexts");}
            }
        }catch(IOException|PackageManager.NameNotFoundException|SecurityException bad){reason="недоступны файлы данных";}
        return new State(true,reason.isEmpty(),wanted,info.versionName==null?"?":info.versionName,code,info.lastUpdateTime,reason);
    }
    static AssetManager assets(Context context,Language language)throws PackageManager.NameNotFoundException{
        // Never load code from another package; only Android's signed asset resources.
        return context.createPackageContext(packageName(language),0).getAssets();
    }
}
