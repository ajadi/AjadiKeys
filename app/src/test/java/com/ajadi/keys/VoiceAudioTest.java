package com.ajadi.keys;
import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceAudioTest {
    @Test public void boundedPcm(){
        assertFalse(VoiceAudio.valid(null));assertFalse(VoiceAudio.valid(new byte[3201]));
        assertFalse(VoiceAudio.valid(new byte[3198]));assertTrue(VoiceAudio.valid(new byte[3200]));
        assertTrue(VoiceAudio.valid(new byte[640000]));assertFalse(VoiceAudio.valid(new byte[640002]));
    }
    @Test public void silenceAndSignal(){
        byte[] pcm=new byte[3200];assertFalse(VoiceAudio.hasSignal(pcm));
        java.util.Arrays.fill(pcm,(byte)1);assertTrue(VoiceAudio.hasSignal(pcm));
        java.util.Arrays.fill(pcm,(byte)0);pcm[1]=1;assertFalse(VoiceAudio.hasSignal(pcm));
    }
    @Test public void languages(){
        for(String s:new String[]{"auto","en","ru","he"})assertTrue(VoiceAudio.languageAllowed(s));
        assertFalse(VoiceAudio.languageAllowed("invalid"));assertFalse(VoiceAudio.languageAllowed(null));
    }
    @Test public void knownModels(){
        assertEquals(VoiceModelStore.Model.BASE,VoiceModelStore.Model.parse("base"));
        assertEquals(VoiceModelStore.Model.SMALL,VoiceModelStore.Model.parse("small"));
        assertNull(VoiceModelStore.Model.parse("../other"));
    }
}
