package com.ajadi.keys;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** CPU-only native speech recognition. No network, files containing audio, or history. */
final class WhisperNative {
    static {System.loadLibrary("ajadi_whisper");}
    private static native byte[] transcribeUtf8(long request,int modelFd,String language,byte[] pcm,int threads);
    static native void cancelThrough(long request);
    static String transcribe(long request,int modelFd,String language,byte[] pcm) {
        byte[] result=transcribeUtf8(request,modelFd,language,pcm,
                Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors())));
        if(result==null)return "";
        try{return new String(result,StandardCharsets.UTF_8).trim();}
        finally{Arrays.fill(result,(byte)0);}
    }
}
