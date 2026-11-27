package com.ajadi.keys;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.content.pm.PackageManager;
import java.io.*;
import java.util.List;

/** A signature-protected, read-only capability for exact public model files, never user data. */
public final class EngineModelProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode)||!EngineContract.AUTHORITY.equals(uri.getAuthority())||getContext()==null)throw new FileNotFoundException("Read-only model provider");
        String caller=getCallingPackage();
        if(caller==null||getContext().getPackageManager().checkSignatures(getContext().getPackageName(),caller)!=PackageManager.SIGNATURE_MATCH)
            throw new SecurityException("Model engine signature mismatch");
        List<String> parts=uri.getPathSegments();
        try{
            File file=null;
            if(EngineContract.TRANSLATION.equals(caller)&&parts.size()==3&&"translation".equals(parts.get(0))){
                String name=parts.get(1)+"/"+parts.get(2);
                TranslationModelStore store=new TranslationModelStore(getContext());
                file=store.openModel(name);
            }else if(EngineContract.VOICE.equals(caller)&&parts.size()==2&&"voice".equals(parts.get(0))){
                VoiceModelStore store=new VoiceModelStore(getContext());VoiceModelStore.Model model=VoiceModelStore.Model.parse(parts.get(1));
                if(store.ready(model))file=store.file(model);
            }
            if(file==null)throw new FileNotFoundException("Model not installed or unsupported path");
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
        }catch(IOException e){throw new FileNotFoundException("Model not installed or unavailable");}
    }
    @Override public String getType(Uri uri){return "application/octet-stream";}
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String order){throw new UnsupportedOperationException();}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
