package com.ajadi.keys;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** One explicitly started microphone session, bounded in RAM. No storage or background service. */
final class VoiceRecorder {
    interface Callback{void done(byte[] pcm,String error);}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean stopped=new AtomicBoolean(),cancelled=new AtomicBoolean();
    VoiceRecorder(Context context){this.context=context;}
    void start(Callback callback){new Thread(()->capture(callback),"Ajadi-explicit-mic").start();}
    void stop(){stopped.set(true);}
    void cancel(){cancelled.set(true);stopped.set(true);}
    @SuppressLint("MissingPermission")
    private void capture(Callback callback){
        byte[] pcm=new byte[VoiceAudio.MAX_BYTES];
        short[] chunk=new short[640];int length=0;String error=null;
        AudioRecord record=null;
        try{
            if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
                throw new IllegalStateException("Microphone permission is not granted");
            int min=AudioRecord.getMinBufferSize(VoiceAudio.SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IllegalStateException("Microphone format unavailable");
            record=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,VoiceAudio.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,4096));
            if(record.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Microphone unavailable");
            if(!stopped.get())record.startRecording();
            if(!stopped.get()&&record.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)
                throw new IllegalStateException("Microphone could not start");
            long start=android.os.SystemClock.elapsedRealtime();
            while(!stopped.get()&&length<pcm.length&&android.os.SystemClock.elapsedRealtime()-start<VoiceAudio.MAX_SECONDS*1000L){
                int count=record.read(chunk,0,Math.min(chunk.length,(pcm.length-length)/2),AudioRecord.READ_NON_BLOCKING);
                if(count<0)throw new IllegalStateException("Microphone recording stopped");
                if(count==0){Thread.sleep(20);continue;}
                for(int i=0;i<count;i++){short sample=chunk[i];pcm[length++]=(byte)sample;pcm[length++]=(byte)(sample>>>8);}
            }
        }catch(Exception e){error=e instanceof IllegalStateException?e.getMessage():"Microphone unavailable; check permission and privacy switch";}
        finally{
            if(record!=null){try{if(record.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)record.stop();}catch(IllegalStateException ignored){}record.release();}
            Arrays.fill(chunk,(short)0);
        }
        byte[] result=cancelled.get()||error!=null?null:Arrays.copyOf(pcm,length);
        Arrays.fill(pcm,(byte)0);
        if(result!=null&&!VoiceAudio.hasSignal(result)){
            Arrays.fill(result,(byte)0);result=null;error="No audio detected. Check the microphone or try again.";
        }
        final byte[] audio=result;final String message=error;
        main.post(()->{if(cancelled.get()){if(audio!=null)Arrays.fill(audio,(byte)0);return;}callback.done(audio,message);});
    }
}
