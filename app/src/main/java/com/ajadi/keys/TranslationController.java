package com.ajadi.keys;

import android.content.*;
import android.os.*;

/** Sends only the explicitly invoked draft to the private local inference service. */
final class TranslationController {
    interface Callback {void finished(String result,String error);}
    private final Context context;
    private ServiceConnection connection;
    private Messenger remote;
    private long generation;
    TranslationController(Context context){this.context=context;}
    void start(KeyLayout.Language from,KeyLayout.Language to,String text,Callback callback) {
        cancel();final long task=generation;
        if(!EngineAvailability.ready(context,false)){callback.finished(null,"Install the signed translation engine first");return;}
        Messenger replies=new Messenger(new Handler(Looper.getMainLooper(),m->{
            if(task!=generation||m.what!=EngineContract.RESULT)return true;
            Bundle data=m.getData();String output=data.getString("result"),error=data.getString("error");
            cancel();callback.finished(output,error);return true;
        }));
        ServiceConnection bind=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder binder){
                if(task!=generation)return;
                if(!EngineAvailability.ready(context,false)){fail("Signed translation engine unavailable");return;}remote=new Messenger(binder);
                Message request=Message.obtain(null,EngineContract.REQUEST);request.replyTo=replies;
                Bundle data=new Bundle();data.putInt("protocol",EngineContract.PROTOCOL);data.putString("text",text);data.putString("from",from.name());data.putString("to",to.name());request.setData(data);
                try{remote.send(request);}catch(RemoteException e){fail("Local translation process unavailable");}
            }
            private void fail(String error){if(task==generation){cancel();callback.finished(null,error);}}
            @Override public void onServiceDisconnected(ComponentName name){fail("Local translation process stopped; try a shorter message or check memory");}
            @Override public void onBindingDied(ComponentName name){fail("Local translation process stopped");}
            @Override public void onNullBinding(ComponentName name){fail("Local translation process unavailable");}
        };
        connection=bind;
        if(!context.bindService(EngineAvailability.intent(false),bind,Context.BIND_AUTO_CREATE)){
            connection=null;callback.finished(null,"Unable to start local translation");
        }
    }
    void cancel(){
        generation++;
        if(remote!=null){try{remote.send(Message.obtain(null,EngineContract.CANCEL));}catch(RemoteException ignored){}remote=null;}
        if(connection!=null){try{context.unbindService(connection);}catch(IllegalArgumentException ignored){}connection=null;}
    }
}
