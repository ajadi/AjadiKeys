package com.ajadi.keys.ai;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Only connection configuration and user-authored command templates, never selected text or results. */
final class AiPreferences {
    static final class Command{
        final String name,instruction;
        Command(String name,String instruction){this.name=name;this.instruction=instruction;}
        boolean valid(){return name!=null&&!name.trim().isEmpty()&&name.length()<=80&&AiProtocol.validCommand(instruction);}
    }
    private final SharedPreferences prefs;
    AiPreferences(Context context){prefs=context.getSharedPreferences("ai_config",Context.MODE_PRIVATE);}
    String endpoint(){return prefs.getString("endpoint","");}
    String model(){return prefs.getString("model","");}
    boolean configured(){try{AiProtocol.endpoint(endpoint());return AiProtocol.validModel(model());}catch(IllegalArgumentException e){return false;}}
    void save(String endpoint,String model){if(!prefs.edit().putString("endpoint",endpoint).putString("model",model).commit())throw new IllegalStateException();}
    List<Command> commands(){
        List<Command> result=new ArrayList<>();
        try{
            JSONArray array=new JSONArray(prefs.getString("commands","[]"));
            for(int i=0;i<Math.min(20,array.length());i++){
                JSONObject item=array.getJSONObject(i);Command c=new Command(item.getString("name"),item.getString("instruction"));if(c.valid())result.add(c);
            }
        }catch(Exception ignored){}
        if(result.isEmpty())result.addAll(defaults());
        return result;
    }
    void saveCommands(List<Command> commands)throws Exception{
        if(commands.isEmpty()||commands.size()>20)throw new IllegalArgumentException();
        JSONArray array=new JSONArray();for(Command c:commands){if(!c.valid())throw new IllegalArgumentException();array.put(new JSONObject().put("name",c.name).put("instruction",c.instruction));}
        if(!prefs.edit().putString("commands",array.toString()).commit())throw new IllegalStateException();
    }
    static List<Command> defaults(){
        List<Command> out=new ArrayList<>();
        out.add(new Command("Исправить грамматику","Исправь орфографию, пунктуацию и грамматику. Сохрани язык, смысл, тон и стиль автора. Верни только исправленный текст."));
        out.add(new Command("Переформулировать","Переформулируй текст естественно и ясно. Сохрани язык, смысл и намерение автора. Верни только новый текст."));
        out.add(new Command("Сократить","Сократи текст, сохранив смысл, язык и тон. Верни только сокращённый текст."));
        return out;
    }
}
