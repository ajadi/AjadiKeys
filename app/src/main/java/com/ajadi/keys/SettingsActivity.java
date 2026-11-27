package com.ajadi.keys;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.view.View;
import java.util.function.IntConsumer;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Small local settings and installation walkthrough. */
public final class SettingsActivity extends Activity {
    private LinearLayout main;
    private TextView translationModelStatus;
    private Button importModelsButton,removeModelsButton;
    private static volatile boolean modelImportRunning;
    private static final int MODEL_IMPORT=701,BACKUP_EXPORT=704,BACKUP_IMPORT=705;
    private static final int VOICE_IMPORT=702,MIC_PERMISSION=703;
    private TextView voiceModelStatus,aiModuleStatus;
    private Button aiSettingsButton,aiDownloadButton,aiRemoveButton;
    private Button translationEngineDownload,translationEngineRemove,translationModelDownload;
    private TextView translationEngineStatus,voiceEngineStatus;
    private Button voiceEngineDownload,voiceEngineRemove,voiceBaseDownload,voiceSmallDownload,voiceBaseRemove,voiceSmallRemove;
    private TextView voiceBaseStatus,voiceSmallStatus;
    private Button voiceImportButton,microphoneButton;
    private final java.util.Map<KeyLayout.Language,TextView> packStatus=new java.util.EnumMap<>(KeyLayout.Language.class);
    private final java.util.Map<KeyLayout.Language,Button> packDownload=new java.util.EnumMap<>(KeyLayout.Language.class),packRemove=new java.util.EnumMap<>(KeyLayout.Language.class);
    private final java.util.Map<KeyLayout.Language,Switch> packEnabled=new java.util.EnumMap<>(KeyLayout.Language.class);
    private final android.content.BroadcastReceiver packageChanges=new android.content.BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            android.net.Uri uri=intent.getData();
            if(uri==null)return;
            String name=uri.getSchemeSpecificPart();
            if(name!=null&&(name.startsWith(LanguagePack.PREFIX)||EngineContract.TRANSLATION.equals(name)
                    ||EngineContract.VOICE.equals(name)||EngineContract.AI.equals(name)))refreshAllStatuses();
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener statusChanges=(prefs,key)->{
        if(key!=null&&(key.startsWith("languagePackEnabled_")||"suggestions".equals(key)||"autocorrect".equals(key)
                ||"wrongLayout".equals(key)||"showTranslator".equals(key)||"showVoice".equals(key)||"showAiButton".equals(key)||"voiceModel".equals(key)))
            refreshAllStatuses();
    };
    private static volatile boolean voiceImportRunning;
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private String page="home";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs=getSharedPreferences("appearance",MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(statusChanges);
        android.content.IntentFilter packages=new android.content.IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_REPLACED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(packageChanges,packages,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(packageChanges,packages);
        String requested=getIntent().getBooleanExtra("show_ai",false)?"ai":
                getIntent().getBooleanExtra("show_voice",false)?"voice":
                getIntent().getBooleanExtra("show_translation",false)?"translation":getIntent().getBooleanExtra("show_input",false)?"input":"home";
        showPage(state==null?requested:state.getString("page","home"));
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putString("page",page);
    }
    @Override public void onBackPressed(){
        if("home".equals(page))super.onBackPressed();
        else showPage("packs".equals(page)?"languages":"custom".equals(page)?"appearance":"home");
    }
    private void clearPageViews(){
        translationModelStatus=voiceModelStatus=aiModuleStatus=null;
        translationEngineStatus=voiceEngineStatus=voiceBaseStatus=voiceSmallStatus=null;
        importModelsButton=removeModelsButton=translationEngineDownload=translationEngineRemove=translationModelDownload=null;
        aiSettingsButton=aiDownloadButton=aiRemoveButton=null;
        voiceEngineDownload=voiceEngineRemove=voiceBaseDownload=voiceSmallDownload=voiceBaseRemove=voiceSmallRemove=null;
        voiceImportButton=microphoneButton=null;
        packStatus.clear();packDownload.clear();packRemove.clear();packEnabled.clear();
    }
    private void showPage(String target){
        clearPageViews();page=target;
        SharedPreferences prefs=getSharedPreferences("appearance",MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(24),dp(24),dp(24),dp(24));
        main.setBackgroundColor(0xff111923);
        scroll.addView(main);
        setContentView(scroll);
        if(!"home".equals(page))button("‹ Назад",this::onBackPressed);
        switch(page){
            case "input":text("Ввод",28);showInput(prefs);break;
            case "appearance":text("Оформление",28);showAppearance(prefs);break;
            case "languages":text("Языки и словари",28);showLanguages(prefs);break;
            case "packs":text("Языковые пакеты",28);showPacks(prefs);break;
            case "custom":text("Своя тема",28);showCustomTheme(prefs);break;
            case "voice":text("Голос",28);showVoice(prefs);break;
            case "translation":text("Перевод",28);showTranslation(prefs);break;
            case "ai":text("ИИ",28);showAi(prefs);break;
            default:page="home";showHome(prefs);break;
        }
        refreshAllStatuses();
    }
    private void showHome(SharedPreferences prefs){
        text("Ajadi Keys", 30);
        text("Офлайн-клавиатура • RU / EN / HE", 16);
        button("Включить клавиатуру", () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button("Выбрать клавиатуру", () -> {
            InputMethodManager manager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.showInputMethodPicker();
        });
        button("Ввод",()->showPage("input"));
        button("Оформление",()->showPage("appearance"));
        button("Языки и словари",()->showPage("languages"));
        button("Голос",()->showPage("voice"));
        button("Перевод",()->showPage("translation"));
        button("ИИ",()->showPage("ai"));
        text("0.10.5 • Без телеметрии и доступа клавиатуры к сети",14);
        button("Версии и обновления",()->openDownload("https://github.com/ajadi/AjadiKeys/releases"));
    }
    private void showInput(SharedPreferences prefs){
        toggle(prefs,"autoCapitalize","Заглавная буква в начале предложения",false);
        text("Удаление",22);
        toggle(prefs,"doubleDeleteWord","Удалять слово: тап Backspace, затем короткое удержание",true);
        text("Жест Backspace для очистки всего поля",16);
        boolean tripleClear=BackspacePress.TRIPLE_MODE.equals(prefs.getString(BackspacePress.MODE_PREF,BackspacePress.HOLD_MODE));
        spinner(new String[]{"Длительное удержание","Три быстрых нажатия"},tripleClear?1:0,
                i->prefs.edit().putString(BackspacePress.MODE_PREF,i==1?BackspacePress.TRIPLE_MODE:BackspacePress.HOLD_MODE).apply());
        text("Задержка очистки удержанием",16);
        int clearDelay=BackspacePress.clearDelay(prefs.getInt(BackspacePress.CLEAR_PREF,BackspacePress.DEFAULT_CLEAR_MS));
        String[] clearLabels=new String[BackspacePress.CLEAR_OPTIONS_MS.length];
        int clearIndex=0;
        for(int i=0;i<clearLabels.length;i++){
            int value=BackspacePress.CLEAR_OPTIONS_MS[i];
            clearLabels[i]=value+" мс";
            if(value==clearDelay)clearIndex=i;
        }
        spinner(clearLabels,clearIndex,i->prefs.edit().putInt(BackspacePress.CLEAR_PREF,BackspacePress.CLEAR_OPTIONS_MS[i]).apply());
        text("Тройной тап или удержание до порога — очистка. Тап + удержание — слово.",14);
        text("Буфер обмена",22);
        Switch history = new Switch(this);
        history.setText("Недавние элементы (только в памяти)");
        history.setTextColor(0xfff0f4fa);
        history.setChecked(prefs.getBoolean("clipboardHistory",true));
        history.setOnCheckedChangeListener((v,checked)->prefs.edit()
                .putBoolean("clipboardHistory",checked).apply());
        main.addView(history);
        text("Тап — вставить, удержание — история (20 элементов, 1 час).",14);
        button("Закреплённые фразы и шаблоны",()->manageEntries("phrases"));
        toggle(prefs,"digitPaste","Показывать «По цифрам» в числовых полях",true);
        text("«По цифрам» передаёт цифры отдельными нажатиями. Поля переключает приложение.",16);
        text("Подсказки",22);
        Switch suggestions = new Switch(this);
        suggestions.setText("Подсказки слов");
        suggestions.setTextColor(0xfff0f4fa);
        suggestions.setChecked(prefs.getBoolean("suggestions",true));
        suggestions.setOnCheckedChangeListener((v,checked)->prefs.edit()
                .putBoolean("suggestions",checked).apply());
        main.addView(suggestions);
        Switch learning = new Switch(this);
        learning.setText("Обучение моему стилю");
        learning.setTextColor(0xfff0f4fa);
        learning.setChecked(prefs.getBoolean("learnStyle",false));
        learning.setOnCheckedChangeListener((v,checked)->prefs.edit()
                .putBoolean("learnStyle",checked).apply());
        main.addView(learning);
        button("Очистить обучение", () -> {
            SuggestionRepository.get(this).clear();new EmojiCatalog(this).clear();
            android.widget.Toast.makeText(this,"Обучение очищено",android.widget.Toast.LENGTH_SHORT).show();
        });
        text("Удержание подсказки — блокировка.",14);
        button("Заблокированные подсказки",this::showBlocked);
        text("Умный ввод",22);
        toggle(prefs,"autocorrect","Автокоррекция",true);
        text("Backspace отменяет автоисправление.",14);
        toggle(prefs,"wrongLayout","Исправлять раскладку",true);
        toggle(prefs,"autoLanguage","Определять язык",true);
        text("Логины и автозаполнение",22);
        toggle(prefs,"savedLogins","Предлагать сохранять логины и email",false);
        text("Только логины и email с подтверждением; не пароли.",14);
        button("Управление сохранёнными логинами",this::showLogins);
        toggle(prefs,"systemAutofill","Системное автозаполнение",true);
        text("Android 11+: заполняет системный менеджер.",14);
    }
    private void showAppearance(SharedPreferences prefs){
        toggle(prefs,"showGlobe","Кнопка смены языка",false);
        toggle(prefs,"showEmoji","Кнопка эмодзи",true);
        toggle(prefs,"showComputer","Расширенная компьютерная клавиатура",false);
        text("При двух кнопках: тап — режим разработчика, удержание — язык. На широком планшете — полная раскладка.",14);
        text("Цвета клавиатуры",16);
        String theme = prefs.getString("theme", "light");
        int themeIndex = "system".equals(theme) ? 0 : "dark".equals(theme) ? 2 : "adaptive".equals(theme) ? 3 : "transparent".equals(theme) ? 4 : "custom".equals(theme) ? 5 : 1;
        String[] themeIds = {"system", "light", "dark", "adaptive", "transparent", "custom"};
        spinner(new String[]{"Как в системе", "Светлая (бирюза)", "Тёмная (графит)", "Адаптивная", "Прозрачная", "Своя тема"}, themeIndex,
                i -> prefs.edit().putString("theme",themeIds[i]).apply());
        TextView opacity=text("Прозрачная тема: непрозрачность фона "+prefs.getInt("transparentOpacity",45)+"%",16);
        SeekBar alpha=new SeekBar(this);alpha.setMax(100);alpha.setProgress(Math.max(0,Math.min(100,prefs.getInt("transparentOpacity",45))));
        alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){
                opacity.setText("Прозрачная тема: непрозрачность фона "+progress+"%");
                if(user)prefs.edit().putInt("transparentOpacity",progress).apply();
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        main.addView(alpha);
        button("Настроить свою тему",()->showPage("custom"));
        Switch number = new Switch(this);
        number.setText("Отдельный ряд цифр (в русском — Ё)");
        number.setTextColor(0xfff0f4fa);
        number.setChecked(prefs.getBoolean("numberRow",false));
        number.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean("numberRow",checked).apply());
        main.addView(number);
        TextView height = text("Высота клавиш: " + prefs.getInt("height",48) + " dp",16);
        SeekBar size = new SeekBar(this);
        size.setMax(24);
        size.setProgress(Math.max(0, Math.min(24, prefs.getInt("height",48)-40)));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int progress,boolean user) {
                int value = progress+40;
                height.setText("Высота клавиш: "+value+" dp");
                if(user) prefs.edit().putInt("height",value).apply();
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        main.addView(size);
        text("Одна рука в альбомной ориентации",19);
        toggle(prefs,"compactLandscape","Компактная клавиатура для одной руки",false);
        text("Расположение",16);
        spinner(new String[]{"Справа (правая рука)","Слева (левая рука)"},
                prefs.getBoolean("compactRight",true)?0:1,i->prefs.edit().putBoolean("compactRight",i==0).apply());
        int width=CompactLayout.clampPercent(prefs.getInt("compactWidth",CompactLayout.DEFAULT_PERCENT));
        TextView compactWidth=text(getString(R.string.compact_width_percent,width),16);
        SeekBar compactSize=new SeekBar(this);
        compactSize.setMax(CompactLayout.MAX_PERCENT-CompactLayout.MIN_PERCENT);
        compactSize.setProgress(width-CompactLayout.MIN_PERCENT);
        compactSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int progress,boolean user){
                int value=CompactLayout.MIN_PERCENT+progress;
                compactWidth.setText(getString(R.string.compact_width_percent,value));
                if(user)prefs.edit().putInt("compactWidth",value).apply();
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        main.addView(compactSize);
        text("Отображение длинных подсказок",16);
        String display=prefs.getString("suggestionDisplay","adaptive");
        String[] displayIds={"adaptive","marquee","fixed"};
        spinner(new String[]{"Адаптивная ширина с прокруткой","Бегущая строка","Фиксированная ширина с усечением"},
                "marquee".equals(display)?1:"fixed".equals(display)?2:0,
                i -> prefs.edit().putString("suggestionDisplay",displayIds[i]).apply());
    }
    private void showCustomTheme(SharedPreferences prefs){
        colorEntry(prefs,"customBackground","Фон","#E1E8E7");
        colorEntry(prefs,"customKey","Клавиши","#FCFDF9");
        colorEntry(prefs,"customText","Текст","#192D30");
        colorEntry(prefs,"customAccent","Акцент","#147E80");
        TextView opacity=text("Непрозрачность фона "+prefs.getInt("customOpacity",100)+"%",16);
        SeekBar alpha=new SeekBar(this);alpha.setMax(100);alpha.setProgress(Math.max(0,Math.min(100,prefs.getInt("customOpacity",100))));
        alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){
                opacity.setText("Непрозрачность фона "+progress+"%");if(user)prefs.edit().putInt("customOpacity",progress).apply();
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });main.addView(alpha);
        text("Цвета текста корректируются при недостаточном контрасте.",14);
        button("Выбрать свою тему",()->{prefs.edit().putString("theme","custom").apply();showPage("appearance");});
        button("Сбросить цвета",()->{
            prefs.edit().remove("customBackground").remove("customKey").remove("customText").remove("customAccent").remove("customOpacity").apply();
            showPage("custom");
        });
    }
    private void colorEntry(SharedPreferences prefs,String key,String label,String fallback){
        String current=prefs.getString(key,fallback);
        Button b=button(label+"  "+current,()->{
            android.widget.EditText input=new android.widget.EditText(this);input.setSingleLine(true);input.setText(current);
            input.setHint("#RRGGBB");
            android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(label).setView(input)
                    .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
            dialog.setOnShowListener(d->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String value=input.getText().toString().trim();
                if(!KeyboardPalette.validColor(value)){input.setError("Формат #RRGGBB");return;}
                prefs.edit().putString(key,value.toUpperCase(java.util.Locale.ROOT)).apply();dialog.dismiss();showPage("custom");
            }));dialog.show();
        });
        int color=KeyboardPalette.color(current,0xffffffff);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        b.setTextColor(KeyboardPalette.contrast(0xff000000,color)>=4.5?0xff000000:0xffffffff);
    }
    private void showLanguages(SharedPreferences prefs){
        text("Основные языки",18);
        KeyLayout.Language[][] pairs = {
                {KeyLayout.Language.RU,KeyLayout.Language.EN},
                {KeyLayout.Language.RU,KeyLayout.Language.HE},
                {KeyLayout.Language.EN,KeyLayout.Language.HE}
        };
        String[] pairLabels = {"Русский + Английский", "Русский + עברית", "Английский + עברית"};
        KeyLayout.Language a = KeyLayout.parse(prefs.getString("primaryA","RU"));
        KeyLayout.Language b = KeyLayout.parse(prefs.getString("primaryB","EN"));
        int selectedPair = 0;
        for (int i=0; i<pairs.length; i++) {
            if ((pairs[i][0] == a && pairs[i][1] == b) ||
                    (pairs[i][0] == b && pairs[i][1] == a)) selectedPair = i;
        }
        spinner(pairLabels, selectedPair, i -> {
            prefs.edit().putString("primaryA",pairs[i][0].name())
                    .putString("primaryB",pairs[i][1].name()).apply();
        });
        text("Повторное переключение без набора — третий язык.",14);
        button("Языковые пакеты",()->showPage("packs"));
        button("Личный словарь",()->manageEntries("words"));
        button("Сокращения",()->manageEntries("shortcuts"));
        toggle(prefs,"expandShortcuts","Раскрывать сокращения по пробелу",true);
        button("Экспорт настроек и словаря",this::exportBackup);
        button("Импорт настроек и словаря",this::importBackup);
    }
    private void showPacks(SharedPreferences prefs){
        text("Скачайте APK, откройте его и подтвердите установку Android. Ввод работает и без пакетов.",14);
        for(KeyLayout.Language lang:KeyLayout.Language.values()){
            String title=lang.name()+" — "+lang.display;
            text(title,18);
            packStatus.put(lang,text("",15));
            Switch enable=new Switch(this);enable.setText("Использовать пакет "+lang.name());enable.setTextColor(0xfff0f4fa);
            enable.setChecked(LanguagePack.enabled(this,lang));
            enable.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean(LanguagePack.pref(lang),checked).apply());
            main.addView(enable);packEnabled.put(lang,enable);
            packDownload.put(lang,button("Скачать пакет "+lang.name()+" (APK)",()->downloadApk(LanguagePack.packageName(lang),LanguagePack.knownVersion(lang),LanguagePack.download(lang))));
            packRemove.put(lang,button("Удалить пакет "+lang.name(),()->requestUninstall(LanguagePack.packageName(lang),false)));
        }
        button("Все версии языковых пакетов и обновления",()->openDownload("https://github.com/ajadi/AjadiKeys/releases"));
        refreshPackStatus();
    }
    private void showTranslation(SharedPreferences prefs){
        toggle(prefs,"showTranslator","Показывать переводчик RU / EN / HE",true);
        text("Нужны APK-движок и ZIP моделей (~707 МиБ). RU ↔ HE через EN.",14);
        translationEngineStatus=text("",16);
        translationEngineDownload=button("Скачать движок перевода (APK)",()->downloadApk(EngineContract.TRANSLATION,1,EngineContract.RELEASE+EngineAvailability.apkName(false)));
        translationEngineRemove=button("Удалить движок перевода",()->requestUninstall(EngineContract.TRANSLATION,false));
        translationModelStatus=text("Проверяю модели перевода...",16);
        translationModelDownload=button("Скачать модели перевода (ZIP)",()->openDownload(EngineContract.MODELS_RELEASE+"AjadiKeys-translation-models-v1.zip"));
        importModelsButton=button("Импортировать модели перевода (ZIP)",this::chooseModels);
        removeModelsButton=button("Удалить модели перевода",this::confirmRemoveModels);
        refreshModelStatus();
    }
    private void showVoice(SharedPreferences prefs){
        toggle(prefs,"showVoice","Микрофон в углу пробела (удержание)",true);
        text("Нужны движок и модель. Удержание пробела — запись до 20 с. Аудио не сохраняется.",14);
        text("Base ~57 МиБ, Small ~181 МиБ. RU / EN / HE.",14);
        String[] voiceChoices={"small","base"};
        spinner(new String[]{"Предпочитать Small (точнее)","Предпочитать Base (быстрее)"}, "base".equals(prefs.getString("voiceModel","small"))?1:0,i->{
            prefs.edit().putString("voiceModel",voiceChoices[i]).apply();refreshVoiceStatus();
        });
        voiceEngineStatus=text("",16);
        voiceEngineDownload=button("Скачать движок речи (APK)",()->downloadApk(EngineContract.VOICE,1,EngineContract.RELEASE+EngineAvailability.apkName(true)));
        voiceEngineRemove=button("Удалить движок речи",()->requestUninstall(EngineContract.VOICE,false));
        voiceBaseStatus=text("",16);
        voiceBaseDownload=button("Скачать модель Base (ZIP)",()->openDownload(EngineContract.RELEASE+"AjadiKeys-voice-base-v1.zip"));
        voiceBaseRemove=button("Удалить модель Base",()->confirmRemoveVoiceModel(VoiceModelStore.Model.BASE));
        voiceSmallStatus=text("",16);
        voiceSmallDownload=button("Скачать модель Small (ZIP)",()->openDownload(EngineContract.RELEASE+"AjadiKeys-voice-small-v1.zip"));
        voiceSmallRemove=button("Удалить модель Small",()->confirmRemoveVoiceModel(VoiceModelStore.Model.SMALL));
        voiceModelStatus=text("Проверяю модели речи...",16);
        voiceImportButton=button("Импортировать модель речи (ZIP)",this::chooseVoiceModel);
        microphoneButton=button("Разрешить доступ к микрофону",this::requestMicPermission);
        refreshVoiceStatus();
    }
    private void showAi(SharedPreferences prefs){
        text("Отдельный модуль для выбранного текста и команд. Сетевой сервис включается только в Ajadi AI.",14);
        toggle(prefs,"showAiButton","Показывать кнопку AI в клавиатуре",false);
        aiModuleStatus=text("",16);
        aiDownloadButton=button("Скачать модуль AI (APK)",()->downloadApk(EngineContract.AI,1,EngineContract.AI_APK));
        aiRemoveButton=button("Удалить модуль AI",()->requestUninstall(EngineContract.AI,true));
        aiSettingsButton=button("Настроить AI-сервис и команды",()->{
            if(EngineAvailability.aiReady(this))startActivity(EngineAvailability.aiIntent());
        });
        refreshAiStatus();
    }
    @Override protected void onResume(){super.onResume();refreshAllStatuses();}
    @Override protected void onDestroy(){
        getSharedPreferences("appearance",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(statusChanges);
        unregisterReceiver(packageChanges);
        super.onDestroy();
    }
    private void refreshAllStatuses(){refreshPackStatus();refreshModelStatus();refreshVoiceStatus();refreshAiStatus();}
    private static String versionNote(boolean installed,long code,int knownCode,String known){
        String comparison=!installed?"Известная версия "+known:code<knownCode?"Доступно известное обновление "+known:code>knownCode?"Версия новее локального каталога ("+known+")":"Известная версия "+known+" установлена";
        return comparison+"; сеть не проверялась.";
    }
    private void refreshPackStatus(){
        if(packStatus.isEmpty())return;
        SuggestionRepository repo=SuggestionRepository.get(this);repo.refreshPacks();
        SharedPreferences prefs=getSharedPreferences("appearance",MODE_PRIVATE);
        boolean smart=prefs.getBoolean("suggestions",true)||prefs.getBoolean("autocorrect",true)||prefs.getBoolean("wrongLayout",true);
        for(KeyLayout.Language lang:KeyLayout.Language.values()){
            TextView view=packStatus.get(lang);if(view==null)continue;
            LanguagePack.State p=repo.packState(lang);
            if(p.active())repo.validatePack(lang);
            boolean failed=repo.packFailed(lang);
            String status=!p.installed?"Не установлен":!p.ready?"Установлен "+p.version+"; недоступен: "+p.reason:failed?"Установлен "+p.version+"; данные не прошли проверку":"Установлен "+p.version+"; "+(!p.wanted?"отключён":smart?"включён, используется при наборе на этом языке":"включён, умный ввод отключён");
            view.setText(lang.name()+": "+status+". "+versionNote(p.installed,p.code,LanguagePack.knownVersion(lang),LanguagePack.knownName(lang)));
            packRemove.get(lang).setEnabled(p.installed);
            packEnabled.get(lang).setEnabled(p.ready&&!failed);
            packEnabled.get(lang).setChecked(p.wanted);
            packDownload.get(lang).setText((!p.installed?"Скачать и установить":p.code<LanguagePack.knownVersion(lang)?"Обновить":"Версии и обновления:")+" пакет "+lang.name()+" (APK)");
        }
    }
    private String apkStatus(EngineAvailability.Installation item,String label,String known,boolean use,String unavailable){
        return label+": "+(!item.installed?"не установлен":!item.ready?"установлен "+item.version+"; несовместим или отключён":"установлен "+item.version+"; "+(use?"готов к использованию":unavailable))
            +". "+versionNote(item.installed,item.code,1,known);
    }
    private void apkActions(EngineAvailability.Installation item,Button download,Button remove,String label){
        if(download!=null)download.setText((!item.installed?"Скачать и установить":item.code<1?"Обновить":"Версии и обновления:")+" "+label+" (APK)");
        if(remove!=null)remove.setEnabled(item.installed);
    }
    private void downloadApk(String pkg,long knownCode,String knownUrl){
        EngineAvailability.Installation item=EngineAvailability.installation(this,pkg,false);
        // Check for a newer release explicitly in the browser; never downgrade automatically.
        openDownload(item.installed&&item.code>=knownCode?"https://github.com/ajadi/AjadiKeys/releases":knownUrl);
    }
    private void requestUninstall(String pkg,boolean ai){
        boolean allowed=EngineContract.TRANSLATION.equals(pkg)||EngineContract.VOICE.equals(pkg)||EngineContract.AI.equals(pkg);
        for(KeyLayout.Language language:KeyLayout.Language.values())allowed|=LanguagePack.packageName(language).equals(pkg);
        if(!allowed)return;
        Runnable launch=()->{
            try{startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE,android.net.Uri.parse("package:"+pkg)));}
            catch(android.content.ActivityNotFoundException|SecurityException unavailable){
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+pkg)));
            }
        };
        if(ai)new android.app.AlertDialog.Builder(this).setTitle("Удалить Ajadi AI?")
            .setMessage("Android удалит также отдельные настройки сервиса и команды Ajadi AI. Настройки клавиатуры и её обучение останутся.")
            .setNegativeButton("Отмена",null).setPositiveButton("Продолжить",(d,w)->launch.run()).show();
        else launch.run();
    }
    private void refreshModelStatus(){
        if(translationModelStatus==null)return;
        boolean ready=false,files=false;
        try{TranslationModelStore store=new TranslationModelStore(this);ready=store.ready();files=store.hasFiles();}
        catch(java.io.IOException ignored){}
        EngineAvailability.Installation engine=EngineAvailability.installation(this,EngineContract.TRANSLATION,EngineAvailability.ready(this,false));
        boolean enabled=getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("showTranslator",true);
        boolean use=engine.ready&&ready&&enabled;
        if(translationEngineStatus!=null)translationEngineStatus.setText(apkStatus(engine,"Движок перевода","0.6.0",use,!enabled?"переводчик отключён":"требуется импорт моделей"));
        apkActions(engine,translationEngineDownload,translationEngineRemove,"движок перевода");
        translationModelStatus.setText(modelImportRunning?"Импортирую модели перевода...":"Модели перевода: "+(ready?"установлены v1; "+(use?"используются при переводе":!enabled?"переводчик отключён":"требуется движок"):files?"файлы есть, пакет неполный или несовместимый":"не установлены")+"; известная версия v1, новые версии в сети не проверялись.");
        if(translationModelDownload!=null)translationModelDownload.setText(ready?"Скачать заново модели перевода v1 (ZIP)":"Скачать модели перевода v1 (ZIP)");
        if(importModelsButton!=null)importModelsButton.setEnabled(!modelImportRunning);
        if(removeModelsButton!=null)removeModelsButton.setEnabled(!modelImportRunning&&files);
    }
    private void chooseModels(){
        if(modelImportRunning)return;
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("*/*");
        startActivityForResult(pick,MODEL_IMPORT);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==BACKUP_EXPORT||request==BACKUP_IMPORT){
            if(result==RESULT_OK&&data!=null&&data.getData()!=null)finishBackup(request,data.getData());return;
        }
        if(request==VOICE_IMPORT){if(result==RESULT_OK)importVoiceModel(data);return;}
        if(request!=MODEL_IMPORT||result!=RESULT_OK||data==null||data.getData()==null||modelImportRunning)return;
        android.net.Uri uri=data.getData();modelImportRunning=true;refreshModelStatus();
        new Thread(()->{
            String error=null;
            try{new TranslationModelStore(this).importPack(getContentResolver().openInputStream(uri),percent->runOnUiThread(()->{
                if(!isDestroyed()&&translationModelStatus!=null)translationModelStatus.setText("Импорт и проверка: "+percent+"%");
            }));}catch(Exception failure){error=failure.getMessage()==null?"Ошибка импорта":failure.getMessage();}
            final String message=error;modelImportRunning=false;
            runOnUiThread(()->{if(!isDestroyed()){refreshModelStatus();if(message!=null)showImportError(translationModelStatus,message);}});
        },"Ajadi-model-import").start();
    }
    private void confirmRemoveModels(){
        new android.app.AlertDialog.Builder(this).setTitle("Удалить модели перевода?")
            .setMessage("Удалятся только модели. Настройки и обучение сохранятся.")
            .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{
                try{new TranslationModelStore(this).remove();refreshModelStatus();}catch(Exception e){translationModelStatus.setText("Не удалось удалить модели");}
            }).show();
    }
    private void refreshVoiceStatus(){
        if(voiceModelStatus==null)return;
        VoiceModelStore store=new VoiceModelStore(this);
        SharedPreferences prefs=getSharedPreferences("appearance",MODE_PRIVATE);
        VoiceModelStore.Model active=store.active(prefs.getString("voiceModel","small"));
        boolean enabled=prefs.getBoolean("showVoice",true);
        boolean permission=checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED;
        EngineAvailability.Installation engine=EngineAvailability.installation(this,EngineContract.VOICE,EngineAvailability.ready(this,true));
        boolean use=engine.ready&&active!=null&&permission&&enabled;
        if(voiceEngineStatus!=null)voiceEngineStatus.setText(apkStatus(engine,"Движок речи","0.6.0",use,!enabled?"голосовой ввод отключён":active==null?"требуется модель":"требуется разрешение микрофона"));
        apkActions(engine,voiceEngineDownload,voiceEngineRemove,"движок речи");
        voiceModelRow(store,VoiceModelStore.Model.BASE,active,use,voiceBaseStatus,voiceBaseDownload,voiceBaseRemove);
        voiceModelRow(store,VoiceModelStore.Model.SMALL,active,use,voiceSmallStatus,voiceSmallDownload,voiceSmallRemove);
        voiceModelStatus.setText(voiceImportRunning?"Импортирую модель речи...":active==null?"Нет активной модели речи":"Выбрана модель "+(active==VoiceModelStore.Model.SMALL?"Small":"Base")+(use?"; готова к записи":"; голосовой ввод пока недоступен"));
        if(voiceImportButton!=null)voiceImportButton.setEnabled(!voiceImportRunning);
        if(microphoneButton!=null){microphoneButton.setText(permission?"Доступ к микрофону разрешён":"Разрешить доступ к микрофону");microphoneButton.setEnabled(!permission);}
    }
    private void requestMicPermission(){
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},MIC_PERMISSION);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);if(request==MIC_PERMISSION)refreshVoiceStatus();
    }
    private void chooseVoiceModel(){
        if(voiceImportRunning)return;
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("*/*");startActivityForResult(pick,VOICE_IMPORT);
    }
    private void importVoiceModel(Intent data){
        if(voiceImportRunning||data==null||data.getData()==null)return;
        android.net.Uri uri=data.getData();voiceImportRunning=true;refreshVoiceStatus();
        new Thread(()->{
            String error=null;
            try{new VoiceModelStore(this).importPack(getContentResolver().openInputStream(uri),percent->runOnUiThread(()->{
                    if(!isDestroyed()&&voiceModelStatus!=null)voiceModelStatus.setText("Импорт и проверка модели речи: "+percent+"%");
                }));}
            catch(Exception e){error=e.getMessage()==null?"Ошибка импорта модели речи":e.getMessage();}
            final String message=error;voiceImportRunning=false;
            runOnUiThread(()->{if(!isDestroyed()){refreshVoiceStatus();if(message!=null)showImportError(voiceModelStatus,message);}});
        },"Ajadi-voice-model-import").start();
    }
    private void voiceModelRow(VoiceModelStore store,VoiceModelStore.Model m,VoiceModelStore.Model active,boolean use,TextView status,Button download,Button remove){
        if(status==null)return;
        String name=m==VoiceModelStore.Model.BASE?"Base":"Small";
        boolean installed=store.ready(m);
        status.setText("Модель "+name+": "+(installed?"установлена v1; "+(m==active?(use?"активна, используется при распознавании":"выбрана, но голосовой ввод недоступен"):"не используется") :store.hasFiles(m)?"файлы есть, модель неполная или несовместимая":"не установлена")+"; известная версия v1, новые версии в сети не проверялись.");
        if(download!=null)download.setText((installed?"Скачать заново":"Скачать")+" модель "+name+" v1 (ZIP)");
        if(remove!=null)remove.setEnabled(!voiceImportRunning&&store.hasFiles(m));
    }
    private void confirmRemoveVoiceModel(VoiceModelStore.Model model){
        String name=model==VoiceModelStore.Model.BASE?"Base":"Small";
        new android.app.AlertDialog.Builder(this).setTitle("Удалить модель "+name+"?")
            .setMessage("Удалятся только файлы выбранной модели. Другая модель и настройки сохранятся.")
            .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{
                try{new VoiceModelStore(this).remove(model);refreshVoiceStatus();}catch(Exception e){voiceModelStatus.setText("Не удалось удалить модель речи");}
            }).show();
    }
    private void refreshAiStatus(){
        if(aiModuleStatus==null)return;
        EngineAvailability.Installation item=EngineAvailability.installation(this,EngineContract.AI,EngineAvailability.aiReady(this));
        boolean enabled=getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("showAiButton",false);
        aiModuleStatus.setText(apkStatus(item,"Модуль AI","0.7.0",item.ready&&enabled,"кнопка клавиатуры отключена")+(item.ready?" Меню выделения доступно; сервис и команды проверяются в Ajadi AI.":""));
        apkActions(item,aiDownloadButton,aiRemoveButton,"модуль AI");
        if(aiSettingsButton!=null)aiSettingsButton.setEnabled(item.ready);
    }
    private void openDownload(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}
        catch(android.content.ActivityNotFoundException e){android.widget.Toast.makeText(this,"Не найден браузер для скачивания",android.widget.Toast.LENGTH_LONG).show();}
    }
    private void showImportError(TextView status,String error){
        String message=localizeModelError(error);
        if(status!=null)status.setText(message);
        else android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show();
    }
    private String localizeModelError(String error){
        if(error==null)return "Ошибка импорта модели.";
        String cause=error.toLowerCase(java.util.Locale.ROOT);
        if(cause.contains("checksum")||cause.contains("sha-256")||cause.contains("corrupt")||cause.contains("length"))return "Файл модели не прошёл проверку целостности. Скачайте правильный ZIP.";
        if(cause.contains("unexpected")||cause.contains("incomplete"))return "ZIP не соответствует известному пакету моделей. Проверьте выбранный архив.";
        if(cause.contains("space"))return "Недостаточно свободного места на телефоне.";
        if(cause.contains("open"))return "Не удалось открыть выбранный файл.";
        return "Ошибка импорта модели. Проверьте файл и свободное место.";
    }
    private void manageEntries(String section){
        PersonalEntries store=new PersonalEntries(this);
        java.util.Map<String,String> entries=store.list(section);
        java.util.List<String> keys=new java.util.ArrayList<>(entries.keySet());
        String[] labels=keys.stream().map(k->"words".equals(section)?k:k+" → "+entries.get(k)).toArray(String[]::new);
        String title="words".equals(section)?"Личный словарь":"shortcuts".equals(section)?"Сокращения":"Фразы и шаблоны";
        android.app.AlertDialog.Builder dialog=new android.app.AlertDialog.Builder(this).setTitle(title)
                .setPositiveButton("Добавить",(d,w)->editEntry(section,null)).setNegativeButton("Закрыть",null);
        if(labels.length==0)dialog.setMessage("Пока пусто");
        else dialog.setItems(labels,(d,i)->new android.app.AlertDialog.Builder(this).setTitle(keys.get(i))
                .setItems(new String[]{"Изменить","Удалить"},(a,b)->{
                    if(b==0)editEntry(section,keys.get(i));else{
                        store.remove(section,keys.get(i));manageEntries(section);
                    }
                }).show());
        dialog.show();
    }
    private void editEntry(String section,String oldKey){
        PersonalEntries store=new PersonalEntries(this);
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),0,dp(20),0);
        android.widget.EditText name=new android.widget.EditText(this),value=new android.widget.EditText(this);
        name.setSingleLine(true);name.setHint("words".equals(section)?"Слово":"shortcuts".equals(section)?"Сокращение":"Название");
        value.setHint("Текст");form.addView(name);if(!"words".equals(section))form.addView(value);
        if(oldKey!=null){name.setText(oldKey);value.setText(store.list(section).get(oldKey));}
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(oldKey==null?"Добавить":"Изменить")
                .setView(form).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String k=name.getText().toString().trim(),text="words".equals(section)?k:value.getText().toString();
            if(store.save(section,oldKey,k,text)){dialog.dismiss();manageEntries(section);}
            else name.setError("Проверьте длину и допустимые символы");
        }));dialog.show();
    }
    private void exportBackup(){
        new android.app.AlertDialog.Builder(this).setTitle("Экспорт в локальный файл?")
                .setMessage("Файл содержит настройки, личный словарь, сокращения и обучение. Нет паролей, ключей, логинов или буфера обмена.")
                .setNegativeButton("Отмена",null).setPositiveButton("Выбрать файл",(d,w)->{
                    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"AjadiKeys-backup.json");
                    startActivityForResult(i,BACKUP_EXPORT);
                }).show();
    }
    private void importBackup(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
        startActivityForResult(i,BACKUP_IMPORT);
    }
    private void finishBackup(int request,android.net.Uri uri){
        try{
            if(request==BACKUP_EXPORT){
                byte[] bytes=LocalBackup.create(this).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if(bytes.length>LocalBackup.MAX_BYTES)throw new java.io.IOException("Too large");
                try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                    if(out==null)throw new java.io.IOException("Missing file");out.write(bytes);
                }
                android.widget.Toast.makeText(this,"Экспорт завершён",android.widget.Toast.LENGTH_SHORT).show();
            }else{
                org.json.JSONObject doc=LocalBackup.read(getContentResolver().openInputStream(uri));
                new android.app.AlertDialog.Builder(this).setTitle("Восстановить настройки?")
                        .setMessage("Заменить личный словарь, сокращения, обучение и список блокировок. Остальные локальные данные сохранятся.")
                        .setNegativeButton("Отмена",null).setPositiveButton("Восстановить",(d,w)->{
                            try{LocalBackup.apply(this,doc);showPage(page);android.widget.Toast.makeText(this,"Импорт завершён",android.widget.Toast.LENGTH_SHORT).show();}
                            catch(Exception invalid){backupError();}
                        }).show();
            }
        }catch(Exception invalid){backupError();}
    }
    private void backupError(){android.widget.Toast.makeText(this,"Не удалось обработать резервную копию",android.widget.Toast.LENGTH_LONG).show();}
    private void showLogins() {
        SavedLogins store=new SavedLogins(this);
        java.util.List<String> apps=new java.util.ArrayList<>(),values=new java.util.ArrayList<>(),labels=new java.util.ArrayList<>();
        for(java.util.Map.Entry<String,java.util.List<String>> entry:store.all().entrySet()) for(String value:entry.getValue()) {
            apps.add(entry.getKey());values.add(value);labels.add(value+"\n"+entry.getKey());
        }
        android.app.AlertDialog.Builder dialog=new android.app.AlertDialog.Builder(this).setTitle("Сохранённые логины");
        if(labels.isEmpty())dialog.setMessage("Нет сохранённых логинов");
        else dialog.setItems(labels.toArray(new String[0]),(d,i)->new android.app.AlertDialog.Builder(this)
                .setTitle("Удалить сохранённый логин?").setMessage(values.get(i)).setPositiveButton("Удалить",(a,b)->{store.remove(apps.get(i),values.get(i));showLogins();})
                .setNegativeButton("Отмена",null).show()).setNeutralButton("Очистить всё",(d,w)->store.clear());
        dialog.setNegativeButton("Закрыть",null).show();
    }
    private void showBlocked() {
        SuggestionRepository repo=SuggestionRepository.get(this);
        java.util.List<String> keys=new java.util.ArrayList<>(repo.blockedWords());
        java.util.Collections.sort(keys);
        if(keys.isEmpty()) {
            new android.app.AlertDialog.Builder(this).setTitle("Заблокированные подсказки")
                    .setMessage("Нет заблокированных подсказок").setPositiveButton("ОК",null).show();return;
        }
        String[] labels=keys.stream().map(k->k.replace('\t',' ')).toArray(String[]::new);
        new android.app.AlertDialog.Builder(this).setTitle("Нажмите слово для разблокировки")
                .setItems(labels,(d,i)->{repo.unblock(keys.get(i));showBlocked();})
                .setNeutralButton("Очистить всё",(d,w)->repo.clearBlocked())
                .setNegativeButton("Закрыть",null).show();
    }
    private void toggle(SharedPreferences prefs,String id,String label,boolean defaultValue) {
        Switch control=new Switch(this);
        control.setText(label);control.setTextColor(0xfff0f4fa);
        control.setChecked(prefs.getBoolean(id,defaultValue));
        control.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean(id,checked).apply());
        main.addView(control);
    }
    private void spinner(String[] labels, int selected, IntConsumer change) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setSelection(selected);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                change.accept(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        main.addView(s, new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private TextView text(String label,int size) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(size);
        t.setTextColor(0xfff0f4fa);
        t.setPadding(0,dp(7),0,dp(7));
        main.addView(t);
        return t;
    }
    private Button button(String label,Runnable task) {
        Button b=new Button(this);
        b.setText(label);
        b.setOnClickListener(v->task.run());
        main.addView(b);return b;
    }
}
