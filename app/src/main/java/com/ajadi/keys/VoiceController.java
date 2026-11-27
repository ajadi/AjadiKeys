package com.ajadi.keys;
import android.content.*;
import android.os.*;
import java.io.IOException;
import java.util.Arrays;

/** Control messages are small; PCM moves through a bounded, anonymous RAM pipe, never a file. */
final class VoiceController {
    interface Callback{void finished(String text,String error);}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Messenger remote;
    private ServiceConnection connection;
    private byte[] pendingAudio;
    private ParcelFileDescriptor writePipe;
    private long generation;
    private Runnable timeout;
    VoiceController(Context context){this.context=context;}
    void start(byte[] pcm,String language,String model,Callback callback){
        cancel();long task=generation;pendingAudio=pcm;
        if(!VoiceAudio.valid(pcm)){wipe();callback.finished(null,"Invalid speech recording");return;}
        if(!EngineAvailability.ready(context,true)){wipe();callback.finished(null,"Install the signed speech engine first");return;}
        Messenger replies=new Messenger(new Handler(Looper.getMainLooper(),m->{
            if(task!=generation||m.what!=EngineContract.RESULT)return true;
            Bundle result=m.getData();String text=result.getString("result"),error=result.getString("error");
            cancel();callback.finished(text,error);return true;
        }));
        ServiceConnection bind=new ServiceConnection(){
            private void fail(String error){if(task==generation){cancel();callback.finished(null,error);}}
            @Override public void onServiceConnected(ComponentName name,IBinder binder){
                if(task!=generation)return;
                if(!EngineAvailability.ready(context,true)){fail("Signed speech engine unavailable");return;}
                remote=new Messenger(binder);
                ParcelFileDescriptor read=null,write=null;
                try{
                    ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();read=pipe[0];write=pipe[1];
                    Message request=Message.obtain(null,EngineContract.REQUEST);request.replyTo=replies;
                    Bundle data=new Bundle();data.putInt("protocol",EngineContract.PROTOCOL);
                    data.putParcelable("pcmPipe",read);data.putInt("pcmLength",pendingAudio.length);
                    data.putString("language",language);data.putString("model",model);request.setData(data);
                    remote.send(request);
                    // The Binder receiver now owns a duplicated read descriptor.
                    final byte[] audio=pendingAudio;pendingAudio=null;
                    writePipe=write;final ParcelFileDescriptor writer=write;write=null;
                    new Thread(()->{
                        try(ParcelFileDescriptor.AutoCloseOutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(writer)){
                            out.write(audio);out.flush();
                        }catch(IOException ignored){}finally{Arrays.fill(audio,(byte)0);}
                    },"Ajadi-voice-ram-pipe").start();
                }catch(IOException|RemoteException|RuntimeException e){fail("Local speech process unavailable");}
                finally{close(read);close(write);}
            }
            @Override public void onServiceDisconnected(ComponentName name){fail("Local speech process stopped; try the smaller model");}
            @Override public void onNullBinding(ComponentName name){fail("Local speech process unavailable");}
            @Override public void onBindingDied(ComponentName name){fail("Local speech process stopped");}
        };
        connection=bind;
        timeout=()->{if(task==generation){cancel();callback.finished(null,"Speech recognition timed out; try the faster model");}};
        main.postDelayed(timeout,120000);
        try{
            if(!context.bindService(EngineAvailability.intent(true),bind,Context.BIND_AUTO_CREATE)){
                connection=null;cancel();callback.finished(null,"Unable to start local speech recognition");
            }
        }catch(RuntimeException denied){connection=null;cancel();callback.finished(null,"Signed speech engine unavailable");}
    }
    private static void close(ParcelFileDescriptor fd){if(fd!=null)try{fd.close();}catch(IOException ignored){}}
    private void wipe(){if(pendingAudio!=null){Arrays.fill(pendingAudio,(byte)0);pendingAudio=null;}}
    void cancel(){
        generation++;if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}
        if(remote!=null){try{remote.send(Message.obtain(null,EngineContract.CANCEL));}catch(RemoteException|RuntimeException ignored){}remote=null;}
        close(writePipe);writePipe=null;
        if(connection!=null){try{context.unbindService(connection);}catch(IllegalArgumentException ignored){}connection=null;}
        wipe();
    }
}
