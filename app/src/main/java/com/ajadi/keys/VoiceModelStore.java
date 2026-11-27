package com.ajadi.keys;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Only the two known, hash-pinned multilingual Whisper model packs can be imported. */
final class VoiceModelStore {
    enum Model {
        BASE("base","Base (fast)",59707625L,"422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"),
        SMALL("small","Small (higher accuracy)",190085487L,"ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb");
        final String id,label,hash;final long size;
        Model(String id,String label,long size,String hash){this.id=id;this.label=label;this.size=size;this.hash=hash;}
        static Model parse(String id){for(Model m:values())if(m.id.equals(id))return m;return null;}
    }
    interface Progress{void update(int percent);}
    private final File root;
    VoiceModelStore(Context context){root=new File(context.getFilesDir(),"voice-models-v1");}
    File file(Model m){return new File(root,m.id+".bin");}
    private File marker(Model m){return new File(root,m.id+".ready");}
    boolean hasFiles(Model m){return file(m).exists()||marker(m).exists();}
    boolean ready(Model m){
        if(m==null)return false;
        File f=file(m),r=marker(m);
        try{return f.isFile()&&f.length()==m.size&&r.isFile()&&r.length()==64
                &&m.hash.equals(new String(Files.readAllBytes(r.toPath()),StandardCharsets.US_ASCII));}
        catch(IOException e){return false;}
    }
    Model active(String preference){
        Model m=Model.parse(preference);if(ready(m))return m;
        for(Model candidate:new Model[]{Model.SMALL,Model.BASE})if(ready(candidate))return candidate;
        return null;
    }
    Model importPack(InputStream source,Progress progress) throws IOException {
        if(source==null)throw new IOException("Cannot open selected speech ZIP");
        if(!root.isDirectory()&&!root.mkdirs()){source.close();throw new IOException("Cannot create private speech model directory");}
        if(root.getUsableSpace()<Model.BASE.size+8*1024*1024){source.close();throw new IOException("Not enough free storage");}
        File stage=File.createTempFile("voice-import-",".part",root);
        File markStage=null;
        try{
            Set<String> seen=new HashSet<>();long length=0;MessageDigest digest=sha();
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(source,65536))){
                byte[] buf=new byte[65536];ZipEntry entry;
                while((entry=zip.getNextEntry())!=null){
                    String name=entry.getName();
                    if(entry.isDirectory()||!seen.add(name)||!(name.equals("model.bin")||name.equals("LICENSE-MIT.txt")||name.equals("SOURCE.txt")))
                        throw new IOException("Unexpected file in speech model ZIP");
                    if(name.equals("model.bin")){
                        int last=0;
                        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(stage),65536)){
                            int count;while((count=zip.read(buf))!=-1){
                                length+=count;if(length>Model.SMALL.size)throw new IOException("Unexpected speech model length");
                                digest.update(buf,0,count);out.write(buf,0,count);
                                long expected=entry.getSize()==Model.BASE.size?Model.BASE.size:Model.SMALL.size;
                                int pct=(int)Math.min(99,length*100/expected);if(pct>last){last=pct;progress.update(pct);}
                            }
                        }
                    }else{
                        int count,total=0;while((count=zip.read(buf))!=-1){total+=count;if(total>65536)throw new IOException("Unexpected speech pack metadata length");}
                    }
                    zip.closeEntry();
                }
            }
            if(seen.size()!=3)throw new IOException("Incomplete speech model ZIP");
            String hash=hex(digest.digest());Model model=null;
            for(Model m:Model.values())if(m.size==length&&m.hash.equals(hash))model=m;
            if(model==null)throw new IOException("Unknown or corrupt speech model (SHA-256 mismatch)");
            markStage=File.createTempFile("voice-ready-",".part",root);
            Files.write(markStage.toPath(),model.hash.getBytes(StandardCharsets.US_ASCII));
            Files.move(stage.toPath(),file(model).toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            Files.move(markStage.toPath(),marker(model).toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            progress.update(100);return model;
        } finally{
            Files.deleteIfExists(stage.toPath());
            if(markStage!=null)Files.deleteIfExists(markStage.toPath());
        }
    }
    void remove(Model model)throws IOException{
        Files.deleteIfExists(marker(model).toPath());Files.deleteIfExists(file(model).toPath());
    }
    void removeAll()throws IOException{
        for(Model m:Model.values())remove(m);
    }
    private static MessageDigest sha(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return b.toString();}
}
