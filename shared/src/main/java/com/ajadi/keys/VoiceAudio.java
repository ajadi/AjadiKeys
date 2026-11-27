package com.ajadi.keys;

/** PCM16 mono at 16 kHz, bounded to one short dictation. */
final class VoiceAudio {
    static final int SAMPLE_RATE=16000, MAX_SECONDS=20, MAX_BYTES=SAMPLE_RATE*2*MAX_SECONDS;
    static final int MIN_BYTES=3200;
    static boolean validLength(int length){return length>=MIN_BYTES&&length<=MAX_BYTES&&length%2==0;}
    static boolean valid(byte[] bytes){return bytes!=null&&validLength(bytes.length);}
    static boolean hasSignal(byte[] bytes){
        if(!valid(bytes))return false;
        long sum=0;int peak=0;
        for(int i=0;i<bytes.length;i+=2){
            int sample=(short)((bytes[i]&255)|((bytes[i+1]&255)<<8));
            int a=Math.abs(sample);peak=Math.max(peak,a);sum+=(long)sample*sample;
        }
        // Reject a muted/silent microphone only. This is not semantic voice classification.
        return peak>=80&&sum/(bytes.length/2)>=64;
    }
    static boolean languageAllowed(String language){
        return "auto".equals(language)||"ru".equals(language)||"en".equals(language)||"he".equals(language);
    }
}
