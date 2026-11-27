package com.ajadi.keys;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.*;

/** Imports only the exact packaged model bytes from a user-selected ZIP into app-private files. */
final class TranslationModelStore {
    private static final String DIR="translation-models-v1", MANIFEST="translation/model_manifest.json";
    private final File base,root;
    interface Progress {void update(int percent);}
    private static final class Entry {
        final long bytes;final String hash;
        Entry(long bytes,String hash){this.bytes=bytes;this.hash=hash;}
    }
    private final Map<String,Entry> expected=new LinkedHashMap<>();
    private final String marker;
    private final long totalBytes;
    TranslationModelStore(Context context) throws IOException {
        base=context.getFilesDir();root=new File(base,DIR);
        byte[] json;
        try(InputStream in=context.getAssets().open(MANIFEST);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int count;while((count=in.read(buf))!=-1)out.write(buf,0,count);json=out.toByteArray();
        }
        marker=hex(digest().digest(json));long total=0;
        try {
            JSONObject manifest=new JSONObject(new String(json,java.nio.charset.StandardCharsets.UTF_8));
            JSONArray entries=manifest.getJSONArray("entries");
            for(int i=0;i<entries.length();i++){
                JSONObject item=entries.getJSONObject(i);String name=item.getString("name");long bytes=item.getLong("size");String hash=item.getString("sha256");
                if(!name.matches("(?:en-ru|ru-en|en-he|he-en)/(?:encoder\\.onnx|decoder\\.onnx|tokenizer\\.bin)|MODEL-LICENSES\\.txt|APACHE-2\\.0\\.txt")
                        ||bytes<=0||!hash.matches("[a-f0-9]{64}")||expected.put(name,new Entry(bytes,hash))!=null)throw new IOException("Invalid model manifest");
                total+=bytes;
            }
        }catch(JSONException bad){throw new IOException("Invalid model manifest",bad);}
        totalBytes=total;
    }
    File root(){return root;}
    File openModel(String relative){
        if(!relative.matches("(?:en-ru|ru-en|en-he|he-en)/(?:encoder\\.onnx|decoder\\.onnx|tokenizer\\.bin)")||!expected.containsKey(relative)||!ready())return null;
        return new File(root,relative);
    }
    long size(){return totalBytes;}
    boolean hasFiles(){return root.exists();}
    boolean ready() {
        try {
            File signal=new File(root,"READY");if(!signal.isFile()||signal.length()!=marker.length())return false;
            byte[] bytes=new byte[marker.length()];try(DataInputStream in=new DataInputStream(new FileInputStream(signal))){in.readFully(bytes);}
            if(!marker.equals(new String(bytes,java.nio.charset.StandardCharsets.US_ASCII)))return false;
            for(Map.Entry<String,Entry> e:expected.entrySet()) {File file=new File(root,e.getKey());if(!file.isFile()||file.length()!=e.getValue().bytes)return false;}
            return true;
        }catch(IOException e){return false;}
    }
    static boolean installed(Context context){try{return new TranslationModelStore(context).ready();}catch(IOException e){return false;}}
    void importPack(InputStream source,Progress progress) throws IOException {
        if(source==null)throw new IOException("Cannot open selected file");
        if(ready()){source.close();progress.update(100);return;}
        if(base.getUsableSpace()<totalBytes+32L*1024*1024){source.close();throw new IOException("Not enough free storage for the model pack");}
        File stage=new File(base,"translation-stage-"+UUID.randomUUID()),old=new File(base,"translation-old-"+UUID.randomUUID());
        if(!stage.mkdir()){source.close();throw new IOException("Cannot prepare private model directory");}
        try {
            Set<String> seen=new HashSet<>();long copied=0;int last=0;
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(source,64*1024))) {
                byte[] buf=new byte[65536];ZipEntry ze;
                while((ze=zip.getNextEntry())!=null) {
                    String name=ze.getName();Entry entry=expected.get(name);
                    if(entry==null||ze.isDirectory()||!seen.add(name))throw new IOException("Unexpected file in translation model ZIP");
                    File file=new File(stage,name);File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create model directory");
                    MessageDigest sha=digest();long length=0;
                    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file),65536)) {
                        int count;while((count=zip.read(buf))!=-1){
                            length+=count;copied+=count;if(length>entry.bytes)throw new IOException("Incorrect model file length");
                            sha.update(buf,0,count);out.write(buf,0,count);
                            int percent=(int)(copied*100/totalBytes);if(percent>last){last=percent;progress.update(percent);}
                        }
                    }
                    if(length!=entry.bytes||!entry.hash.equals(hex(sha.digest())))throw new IOException("Model checksum mismatch: "+name);
                    zip.closeEntry();
                }
            }
            if(seen.size()!=expected.size())throw new IOException("Translation model ZIP is incomplete");
            try(OutputStream out=new FileOutputStream(new File(stage,"READY"))){out.write(marker.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
            if(root.exists()&&!root.renameTo(old))throw new IOException("Cannot replace model pack");
            if(!stage.renameTo(root)){if(old.exists())old.renameTo(root);throw new IOException("Cannot install model pack");}
            if(old.exists())deleteTree(old);progress.update(100);
        } finally {if(stage.exists())deleteTree(stage);}
    }
    void remove() throws IOException {if(root.exists())deleteTree(root);}
    private void deleteTree(File file) throws IOException {
        String prefix=base.getCanonicalPath()+File.separator;
        if(!file.getCanonicalPath().startsWith(prefix))throw new IOException("Invalid model path");
        if(file.isDirectory()) {File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);}
        if(!file.delete()&&file.exists())throw new IOException("Cannot remove model file");
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
}
