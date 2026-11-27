package com.ajadi.keys;
import android.app.Service;
import android.content.Intent;
import android.os.*;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Signature-restricted local engine. Receives only explicit bounded audio through an anonymous pipe. */
public final class VoiceService extends Service {
    static final int REQUEST=EngineContract.REQUEST,CANCEL=EngineContract.CANCEL,RESULT=EngineContract.RESULT;
    private ExecutorService worker;
    private Future<?> pending;
    private Messenger receiver;
    private static final java.util.concurrent.atomic.AtomicLong SEQUENCE=new java.util.concurrent.atomic.AtomicLong();
    private long active;
    private ParcelFileDescriptor pendingPipe;
    @Override public void onCreate(){super.onCreate();worker=Executors.newSingleThreadExecutor();receiver=new Messenger(new Handler(Looper.getMainLooper(),this::handle));}
    @Override public IBinder onBind(Intent intent){return receiver.getBinder();}
    private boolean handle(Message request){
        if(!EngineAccess.isBaseSender(this,request.sendingUid))return false;
        if(request.what==CANCEL){cancel();return true;}
        if(request.what!=REQUEST||request.replyTo==null)return false;
        cancel();long id=SEQUENCE.incrementAndGet();active=id;
        Bundle data=request.getData();Messenger reply=request.replyTo;
        if(data.getInt("protocol")!=EngineContract.PROTOCOL)return false;
        ParcelFileDescriptor pipe=data.getParcelable("pcmPipe");pendingPipe=pipe;
        int length=data.getInt("pcmLength",0);
        String language=data.getString("language",""),modelId=data.getString("model","");
        pending=worker.submit(()->{
            Bundle result=new Bundle();byte[] pcm=null;
            try{
                if(!"base".equals(modelId)&&!"small".equals(modelId))throw new IllegalArgumentException("Install a speech model in settings first");
                if(pipe==null||!VoiceAudio.validLength(length)||!VoiceAudio.languageAllowed(language))throw new IllegalArgumentException("Invalid speech request");
                pcm=new byte[length];
                try(DataInputStream in=new DataInputStream(new ParcelFileDescriptor.AutoCloseInputStream(pipe))){in.readFully(pcm);}
                if(!VoiceAudio.hasSignal(pcm))throw new IllegalArgumentException("No audio detected");
                if(Thread.currentThread().isInterrupted())return;
                String text;try(ParcelFileDescriptor model=EngineAccess.open(this,"voice",modelId)){text=WhisperNative.transcribe(id,model.getFd(),language,pcm);}
                if(Thread.currentThread().isInterrupted())return;
                if(text.isEmpty())result.putString("error","No speech recognized; try again");
                else result.putString("result",text);
            }catch(IllegalArgumentException e){result.putString("error",e.getMessage());}
            catch(Exception|LinkageError e){result.putString("error","Local speech recognition failed; check the model and available memory");}
            finally{close(pipe);if(pcm!=null)Arrays.fill(pcm,(byte)0);}
            if(!Thread.currentThread().isInterrupted()){
                Message response=Message.obtain(null,RESULT);response.setData(result);
                try{reply.send(response);}catch(RemoteException ignored){}
            }
        });return true;
    }
    private static void close(ParcelFileDescriptor fd){if(fd!=null)try{fd.close();}catch(IOException ignored){}}
    private void cancel(){
        if(active>0){try{WhisperNative.cancelThrough(active);}catch(LinkageError ignored){}active=0;}
        if(pending!=null){pending.cancel(true);pending=null;}
        close(pendingPipe);pendingPipe=null;
    }
    @Override public boolean onUnbind(Intent intent){cancel();return false;}
    @Override public void onDestroy(){cancel();if(worker!=null)worker.shutdownNow();super.onDestroy();}
}
