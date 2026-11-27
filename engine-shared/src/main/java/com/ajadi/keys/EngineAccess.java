package com.ajadi.keys;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/** Models stay in the original keyboard's private storage; only exact public weight files are opened read-only. */
final class EngineAccess {
    static boolean isBaseSender(Context context,int uid){
        try{
            PackageManager pm=context.getPackageManager();
            return pm.getApplicationInfo(EngineContract.BASE,0).uid==uid
                    &&pm.checkSignatures(context.getPackageName(),EngineContract.BASE)==PackageManager.SIGNATURE_MATCH;
        }catch(PackageManager.NameNotFoundException|SecurityException e){return false;}
    }
    static ParcelFileDescriptor open(Context context,String... path)throws IOException{
        Uri.Builder uri=new Uri.Builder().scheme("content").authority(EngineContract.AUTHORITY);
        for(String part:path)uri.appendPath(part);
        ParcelFileDescriptor pfd=context.getContentResolver().openFileDescriptor(uri.build(),"r");
        if(pfd==null)throw new FileNotFoundException("Model not installed");
        return pfd;
    }
    static ByteBuffer map(Context context,String... path)throws IOException{
        ParcelFileDescriptor pfd=open(context,path);
        long size=pfd.getStatSize();
        try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(pfd)){
            if(size<=0||size>500_000_000L)throw new IOException("Invalid model size");
            return in.getChannel().map(FileChannel.MapMode.READ_ONLY,0,size).order(ByteOrder.nativeOrder());
        }
    }
    static DataInputStream tokenizer(Context context,String pair)throws IOException{
        return new DataInputStream(new BufferedInputStream(new ParcelFileDescriptor.AutoCloseInputStream(
                open(context,"translation",pair,"tokenizer.bin"))));
    }
}
