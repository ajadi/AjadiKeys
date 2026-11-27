package com.ajadi.keys.ai;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Optional visible companion. The Android selection action is the only entry point for text. */
public final class AiActivity extends Activity {
    private AiPreferences config;
    private AiSecretStore secrets;
    private LinearLayout page;
    private boolean processingMode,readOnly=true,inConfig,busy;
    private String selected="",result="",status="";
    private int commandIndex;
    private List<AiPreferences.Command> commands;
    private TextView instructionView,statusView;
    private Button sendButton,applyButton,copyButton;
    private EditText resultEditor;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private AiClient activeClient;private Future<?> activeTask;private long generation;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        config=new AiPreferences(this);secrets=new AiSecretStore(this);
        processingMode=Intent.ACTION_PROCESS_TEXT.equals(getIntent().getAction());
        if(processingMode){
            try{
                CharSequence value=getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
                if(value!=null&&value.length()<=AiProtocol.MAX_SELECTION)selected=value.toString();
                readOnly=getIntent().getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,true)||getCallingActivity()==null;
            }catch(RuntimeException invalid){selected="";}
            // Do not retain the selected text in our instance-state or a reused Intent.
            setIntent(new Intent(Intent.ACTION_PROCESS_TEXT));
            if(!AiProtocol.validSelection(selected)){
                status="Выделите непустой фрагмент до 4000 символов.";
            }
            showProcess();
        }else showConfig();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void startPage(String title){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(18),dp(20),dp(18));
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets sys=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(sys.left,sys.top,sys.right,sys.bottom);
            }
            return insets;
        });
        setContentView(scroll);
        TextView heading=text(title,26);heading.setTypeface(null,android.graphics.Typeface.BOLD);
    }
    private TextView text(String value,int size){
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(0xff172b30);
        v.setPadding(0,dp(5),0,dp(5));page.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private Button button(String label,Runnable click){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setOnClickListener(v->click.run());page.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;
    }
    private EditText field(String hint,String value,int limit,int type){
        EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setInputType(type);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});e.setSaveEnabled(false);
        e.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        page.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }

    private void showConfig(){
        inConfig=true;startPage("Ajadi AI — настройки");
        text("Отдельное приложение с доступом к интернету. Клавиатура остаётся офлайн. Запросы выполняются только после вашего подтверждения в окне обработки выделенного текста.",16);
        text("HTTPS-адрес API, совместимого с Chat Completions",14);
        EditText endpoint=field("https://…/v1/chat/completions",config.endpoint(),512,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText model=field("Идентификатор модели",config.model(),160,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        text("Ключ API (необязательно для сервера без авторизации). Хранится шифрованно на устройстве. При смене адреса прежний ключ не используется.",14);
        EditText token=field(secrets.has(config.endpoint())?"Ключ сохранён; пустое поле — не менять":"Введите ключ API","",4096,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        button("Сохранить подключение",()->{
            String url=endpoint.getText().toString().trim(),name=model.getText().toString().trim();
            String key=token.getText().toString();
            try{
                if(!url.isEmpty()||!name.isEmpty()){
                    url=AiProtocol.endpoint(url).toString();
                    if(!AiProtocol.validModel(name))throw new IllegalArgumentException();
                }
                boolean changed=!url.equals(config.endpoint());
                if(!key.isEmpty()){
                    if(url.isEmpty())throw new IllegalArgumentException();
                    secrets.save(url,key);
                }else if(changed)secrets.clear();
                config.save(url,name);token.setText("");
                toast("Подключение сохранено локально");
                if(processingMode)showProcess();else showConfig();
            }catch(IllegalArgumentException invalid){toast("Укажите полный HTTPS-адрес без ключа в URL и имя модели.");}
            catch(Exception failure){toast("Не удалось безопасно сохранить настройки подключения.");}
        });
        button("Удалить сохранённый ключ",()->{
            new AlertDialog.Builder(this).setTitle("Удалить ключ API?").setNegativeButton("Отмена",null)
                .setPositiveButton("Удалить",(d,w)->{secrets.clear();token.setText("");showConfig();}).show();
        });
        text("Команды",22);text("Можно редактировать готовые команды и добавлять свои. На сервер отправляется только выбранная команда и показанный фрагмент.",15);
        List<AiPreferences.Command> items=config.commands();
        for(int i=0;i<items.size();i++){
            int index=i;AiPreferences.Command command=items.get(i);
            text(command.name,18);text(command.instruction,14);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.START);
            Button edit=new Button(this);edit.setAllCaps(false);edit.setText("Изменить");edit.setOnClickListener(v->editCommand(index));
            Button remove=new Button(this);remove.setAllCaps(false);remove.setText("Удалить");remove.setEnabled(items.size()>1);remove.setOnClickListener(v->{
                new AlertDialog.Builder(this).setTitle("Удалить команду?").setMessage(command.name).setNegativeButton("Отмена",null)
                    .setPositiveButton("Удалить",(d,w)->{List<AiPreferences.Command> list=config.commands();if(index<list.size()&&list.size()>1){list.remove(index);saveCommands(list);}}).show();
            });
            row.addView(edit,new LinearLayout.LayoutParams(0,-2,1));row.addView(remove,new LinearLayout.LayoutParams(0,-2,1));page.addView(row);
        }
        button("Добавить команду",()->editCommand(-1)).setEnabled(items.size()<20);
        if(processingMode)button("Вернуться к выделенному тексту",this::showProcess);
        else text("Использование: выделите текст в любом редакторе, откройте меню выделения и выберите Ajadi AI. Для приложений без такого меню можно включить кнопку AI в настройках Ajadi Keys.",15);
    }
    private void editCommand(int index){
        List<AiPreferences.Command> list=config.commands();AiPreferences.Command old=index>=0&&index<list.size()?list.get(index):null;
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(16),0,dp(16),dp(8));
        EditText name=new EditText(this);name.setHint("Название");name.setSaveEnabled(false);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        EditText prompt=new EditText(this);prompt.setHint("Инструкция для модели");prompt.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);prompt.setMinLines(3);
        prompt.setSaveEnabled(false);prompt.setFilters(new InputFilter[]{new InputFilter.LengthFilter(AiProtocol.MAX_COMMAND)});
        for(EditText field:new EditText[]{name,prompt}){
            field.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        if(old!=null){name.setText(old.name);prompt.setText(old.instruction);}
        form.addView(name);form.addView(prompt);
        new AlertDialog.Builder(this).setTitle(old==null?"Новая команда":"Редактирование команды").setView(form).setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(d,w)->{
                AiPreferences.Command c=new AiPreferences.Command(name.getText().toString().trim(),prompt.getText().toString().trim());
                if(!c.valid()){toast("Введите название и инструкцию");return;}
                List<AiPreferences.Command> next=config.commands();
                if(old==null&&next.size()<20)next.add(c);else if(index>=0&&index<next.size())next.set(index,c);
                saveCommands(next);
            }).show();
    }
    private void saveCommands(List<AiPreferences.Command> list){
        try{config.saveCommands(list);commandIndex=0;showConfig();}catch(Exception failure){toast("Не удалось сохранить команду");}
    }

    private void showProcess(){
        inConfig=false;startPage("Ajadi AI — обработка текста");
        text("Будет использован только явно выделенный фрагмент. Выбранный внешний сервис получит текст лишь после подтверждения.",16);
        text("Выделенный текст",18);
        TextView source=text(selected.isEmpty()?"Текст не выбран":selected,17);
        source.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);source.setBackgroundColor(0xffedf2f1);source.setPadding(dp(10),dp(10),dp(10),dp(10));source.setSaveEnabled(false);
        commands=config.commands();if(commandIndex>=commands.size())commandIndex=0;
        String[] names=new String[commands.size()];for(int i=0;i<names.length;i++)names[i]=commands.get(i).name;
        Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);spinner.setSelection(commandIndex);page.addView(spinner,new LinearLayout.LayoutParams(-1,-2));
        instructionView=text(commands.get(commandIndex).instruction,15);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View v,int i,long id){commandIndex=i;instructionView.setText(commands.get(i).instruction);}
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        text(config.configured()?"Сервер: "+config.endpoint()+"\nМодель: "+config.model():"Сначала настройте HTTPS-адрес и модель.",15);
        button("Настроить сервис и команды",()->{cancel();showConfig();});
        sendButton=button("Отправить выбранный текст",this::confirmSend);
        sendButton.setEnabled(AiProtocol.validSelection(selected)&&config.configured()&&!busy);
        statusView=text(status,14);
        text("Предпросмотр результата",18);
        resultEditor=field("Результат появится здесь",result,AiProtocol.MAX_OUTPUT,
                InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        resultEditor.setMinLines(3);resultEditor.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);resultEditor.setEnabled(!result.isEmpty()&&!busy);
        applyButton=button(readOnly?"Скопировать результат":"Заменить выделенный текст",this::applyResult);
        applyButton.setEnabled(!result.isEmpty()&&!busy);
        copyButton=button("Скопировать в буфер",this::copyResult);
        copyButton.setVisibility(readOnly?View.GONE:View.VISIBLE);copyButton.setEnabled(!result.isEmpty()&&!busy);
        button("Отменить и закрыть",()->{cancel();setResult(RESULT_CANCELED);finish();});
    }
    private void confirmSend(){
        if(busy||!AiProtocol.validSelection(selected)||!config.configured())return;
        URI endpoint;try{endpoint=AiProtocol.endpoint(config.endpoint());}catch(IllegalArgumentException bad){return;}
        String model=config.model(),command=commands.get(commandIndex).instruction,fragment=selected;
        new AlertDialog.Builder(this).setTitle("Отправить выделенный текст?")
            .setMessage("Адрес: "+endpoint+"\nМодель: "+model+"\n\nБудут отправлены только показанные команда и выделенный фрагмент ("+fragment.length()+" символов). Внешний сервис может хранить данные по своим правилам.")
            .setNegativeButton("Отмена",null).setPositiveButton("Отправить",(d,w)->startRequest(endpoint,model,command,fragment)).show();
    }
    private void startRequest(URI endpoint,String model,String command,String fragment){
        cancel();long task=++generation;busy=true;result="";status="Отправляю запрос по вашему подтверждению...";
        sendButton.setEnabled(false);applyButton.setEnabled(false);copyButton.setEnabled(false);resultEditor.setText("");resultEditor.setEnabled(false);statusView.setText(status);
        AiClient client=new AiClient();activeClient=client;
        activeTask=executor.submit(()->{
            String output=null,error=null;
            try{output=client.process(endpoint,model,command,fragment,secrets.read(endpoint.toString()));}
            catch(AiClient.HttpFailure fail){error="Сервис вернул HTTP "+fail.status+". Проверьте адрес, модель и права ключа.";}
            catch(Exception fail){error="Не удалось получить ответ. Проверьте соединение и настройки сервиса.";}
            final String value=output,message=error;
            runOnUiThread(()->{
                if(task!=generation||isFinishing()||isDestroyed())return;
                busy=false;activeClient=null;activeTask=null;result=value==null?"":value;
                status=message==null?"Проверьте результат и подтвердите замену или копирование.":message;
                if(!inConfig)showProcess();
            });
        });
    }
    private String output(){return resultEditor==null?"":resultEditor.getText().toString();}
    private void applyResult(){
        String value=output();if(busy||result.isEmpty()||value.trim().isEmpty())return;
        if(readOnly){copyResult();return;}
        setResult(RESULT_OK,new Intent().putExtra(Intent.EXTRA_PROCESS_TEXT,value));cancel();finish();
    }
    private void copyResult(){
        String value=output();if(busy||result.isEmpty()||value.trim().isEmpty())return;
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("Ajadi AI",value));toast("Результат скопирован");}
        setResult(RESULT_CANCELED);cancel();finish();
    }
    private void cancel(){
        generation++;busy=false;
        if(activeClient!=null){activeClient.cancel();activeClient=null;}
        if(activeTask!=null){activeTask.cancel(true);activeTask=null;}
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    @Override protected void onStop(){super.onStop();if(busy){cancel();status="Запрос отменён при уходе из приложения.";if(!isFinishing()&&processingMode&&!inConfig)showProcess();}}
    @Override protected void onDestroy(){cancel();selected="";result="";executor.shutdownNow();super.onDestroy();}
}
