package com.ajadi.keys;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Non-exported, same-app, offline inference in its own process to isolate the IME from model memory. */
public final class TranslationService extends Service {
    static final int REQUEST=EngineContract.REQUEST,CANCEL=EngineContract.CANCEL,RESULT=EngineContract.RESULT;
    private ExecutorService worker;
    private Future<?> pending;
    private Messenger receiver;
    @Override public void onCreate(){super.onCreate();worker=Executors.newSingleThreadExecutor();receiver=new Messenger(new Handler(Looper.getMainLooper(),this::handle));}
    @Override public IBinder onBind(Intent intent){return receiver.getBinder();}
    private boolean handle(Message request) {
        if(!EngineAccess.isBaseSender(this,request.sendingUid))return false;
        if(request.what==CANCEL){cancel();return true;}
        if(request.what!=REQUEST||request.replyTo==null)return false;
        cancel();Bundle data=request.getData();Messenger reply=request.replyTo;
        if(data.getInt("protocol")!=EngineContract.PROTOCOL)return false;
        String text=data.getString("text","");String from=data.getString("from",""),to=data.getString("to","");
        pending=worker.submit(()->{
            Bundle result=new Bundle();
            try {
                MarianTranslator.Models models=new MarianTranslator.Models(){
                    public java.nio.ByteBuffer onnx(String pair,String file)throws java.io.IOException{return EngineAccess.map(TranslationService.this,"translation",pair,file);}
                    public java.io.DataInputStream tokenizer(String pair)throws java.io.IOException{return EngineAccess.tokenizer(TranslationService.this,pair);}
                };
                String output=new MarianTranslator(models).translate(from,to,text);
                if(Thread.currentThread().isInterrupted())return;
                result.putString("result",output);
            } catch(InterruptedException cancelled){Thread.currentThread().interrupt();return;}
            catch(IllegalArgumentException bad){result.putString("error",bad.getMessage());}
            catch(Exception|LinkageError failure){result.putString("error","Local translation failed; check the installed model pack and available memory");}
            Message message=Message.obtain(null,RESULT);message.setData(result);
            try{reply.send(message);}catch(RemoteException ignored){}
        });return true;
    }
    private void cancel(){if(pending!=null){pending.cancel(true);pending=null;}}
    @Override public boolean onUnbind(Intent intent){cancel();return false;}
    @Override public void onDestroy(){cancel();if(worker!=null)worker.shutdownNow();super.onDestroy();}
}
