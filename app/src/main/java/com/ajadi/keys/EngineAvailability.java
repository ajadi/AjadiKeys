package com.ajadi.keys;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;

final class EngineAvailability {
    static ComponentName component(boolean voice){
        return new ComponentName(voice?EngineContract.VOICE:EngineContract.TRANSLATION,
                voice?EngineContract.VOICE_SERVICE:EngineContract.TRANSLATION_SERVICE);
    }
    static boolean ready(Context context,boolean voice){
        try{
            ComponentName component=component(voice);PackageManager pm=context.getPackageManager();
            if(pm.checkSignatures(context.getPackageName(),component.getPackageName())!=PackageManager.SIGNATURE_MATCH)return false;
            ServiceInfo s=pm.getServiceInfo(component,PackageManager.GET_META_DATA);
            return s.exported&&s.enabled&&s.applicationInfo.enabled&&EngineContract.PERMISSION.equals(s.permission)
                    &&s.metaData!=null&&s.metaData.getInt(EngineContract.PROTOCOL_METADATA,0)==EngineContract.PROTOCOL;
        }catch(PackageManager.NameNotFoundException|SecurityException e){return false;}
    }
    static final class Installation {
        final boolean installed,ready;
        final String version;
        final long code;
        Installation(boolean installed,boolean ready,String version,long code){
            this.installed=installed;this.ready=ready;this.version=version;this.code=code;
        }
    }
    static Installation installation(Context context,String packageName,boolean ready){
        try{
            android.content.pm.PackageInfo p=context.getPackageManager().getPackageInfo(packageName,0);
            return new Installation(true,ready,p.versionName==null?"?":p.versionName,
                    Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode);
        }catch(PackageManager.NameNotFoundException|SecurityException missing){
            return new Installation(false,false,"",0);
        }
    }
    static Intent intent(boolean voice){return new Intent().setComponent(component(voice));}
    static boolean aiReady(Context context){
        try{
            PackageManager pm=context.getPackageManager();
            if(pm.checkSignatures(context.getPackageName(),EngineContract.AI)!=PackageManager.SIGNATURE_MATCH)return false;
            android.content.pm.ActivityInfo info=pm.getActivityInfo(new ComponentName(EngineContract.AI,EngineContract.AI_ACTIVITY),0);
            return info.exported&&info.enabled&&info.applicationInfo.enabled;
        }catch(PackageManager.NameNotFoundException|SecurityException bad){return false;}
    }
    static Intent aiIntent(){return new Intent().setComponent(new ComponentName(EngineContract.AI,EngineContract.AI_ACTIVITY));}
    static String apkName(boolean voice){
        String[] abis=Build.SUPPORTED_ABIS;
        String abi=abis.length==0?"universal":abis[0];
        if(!abi.equals("arm64-v8a")&&!abi.equals("armeabi-v7a")&&!abi.equals("x86")&&!abi.equals("x86_64"))abi="universal";
        return "AjadiKeys-"+(voice?"voice":"translation")+"-engine-0.6.0-"+abi+".apk";
    }
}
