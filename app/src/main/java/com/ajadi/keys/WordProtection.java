package com.ajadi.keys;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Compact safe-side membership of known morphological forms. Never proposes a correction. */
final class WordProtection {
    private final int bits,hashes;
    private final byte[] bytes;
    private WordProtection(int bits,int hashes,byte[] bytes){this.bits=bits;this.hashes=hashes;this.bytes=bytes;}
    static WordProtection read(InputStream input)throws IOException{
        DataInputStream in=new DataInputStream(new BufferedInputStream(input));
        if(in.readInt()!=0x414b5031)throw new IOException("Unknown protection table");
        int bits=in.readInt(),hashes=in.readInt();
        if(bits<8||bits>80_000_000||bits%8!=0||hashes<1||hashes>16)throw new IOException("Invalid protection data");
        byte[] content=new byte[bits/8];in.readFully(content);
        if(in.read()!=-1)throw new IOException("Trailing protection data");
        return new WordProtection(bits,hashes,content);
    }
    boolean mightContain(String word){
        long a=0xcbf29ce484222325L;
        for(byte b:word.getBytes(StandardCharsets.UTF_8)){a^=b&255L;a*=0x100000001b3L;}
        long b=a^0x9e3779b97f4a7c15L;
        b=(b^(b>>>30))*0xbf58476d1ce4e5b9L;
        b=(b^(b>>>27))*0x94d049bb133111ebL;
        b=(b^(b>>>31))|1L;
        for(int i=0;i<hashes;i++){
            int index=(int)Long.remainderUnsigned(a+i*b,bits);
            if((bytes[index>>>3]&(1<<(index&7)))==0)return false;
        }
        return true;
    }
}
