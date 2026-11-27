package com.ajadi.keys;

import android.content.SharedPreferences;
import android.content.ComponentName;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.Context;
import android.content.res.Configuration;
import android.view.inputmethod.InputMethodManager;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.Window;
import android.text.InputType;
import android.text.TextUtils;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import java.util.function.Supplier;
import java.util.ArrayDeque;
import java.util.List;

/** A native IME: all keystrokes go exclusively to the active editor. */
public final class AjadiImeService extends InputMethodService {
    private KeyboardSurface keyboard;
    private CompactKeyboardFrame keyboardFrame;
    private KeyButton shiftKey;
    private LanguageCycle cycle;
    private KeyboardPalette palette = KeyboardPalette.LIGHT;
    private boolean transparentTheme;
    private int surfaceColor;
    private android.graphics.drawable.Drawable normalWindowBackground;
    private boolean windowBackgroundSaved;
    private final Handler touchHandler = new Handler(Looper.getMainLooper());
    private final Handler digitPasteHandler=new Handler(Looper.getMainLooper());
    private String digitPasteText,digitPastePackage;
    private int digitPasteIndex;
    private long digitPasteDeadline;
    private final Runnable digitPasteStep=this::sendNextPastedDigit;
    private AlternatePopup alternatePopup;
    private boolean holdActive;
    private long touchEpoch,keyboardTouchSequence;
    private final WordDeleteGesture wordDelete=new WordDeleteGesture();
    private ClipboardHistory clipboardHistory;
    private boolean clipboardPanel, loginConfirmPanel, translationMode, emojiPanel, computerPanel;
    private final ComputerKeyboard computer=new ComputerKeyboard();
    private boolean computerWideRendered;
    private boolean manualCase;
    private boolean layoutPanel;
    private String layoutOriginal;
    private CursorWord layoutWord;
    private EditorInfo layoutOrigin;
    private InputConnection layoutConnection;
    private int layoutStart,layoutEnd;
    private DeleteUndo deleteUndo;
    private static final class DeleteUndo {
        final InputConnection connection; final EditorInfo editor;final String text;final int caret;
        final String before,after;
        DeleteUndo(InputConnection connection,EditorInfo editor,String text,int caret,String before,String after){
            this.connection=connection;this.editor=editor;this.text=text;this.caret=caret;this.before=before;this.after=after;
        }
    }
    private int emojiCategory;
    private boolean emojiSearch,clipboardSaved;
    private String emojiQuery="";
    private KeyLayout.Language emojiSearchLanguage=KeyLayout.Language.RU;
    private int emojiSuggestionIndex=-1,shortcutSuggestionIndex=-1;
    private PersonalEntries personalEntries;
    private EmojiCatalog emojiCatalog;
    private TranslationController translationController;
    private VoiceController voiceController;
    private VoiceRecorder voiceRecorder;
    private VoicePanel voicePanel;
    private boolean voiceMode,voiceRecording,voiceBusy;
    private long voiceGeneration;
    private String voiceLanguage="auto",voiceStatus="",voiceResult="";
    private EditorInfo voiceOrigin;
    private int voiceAnchorStart,voiceAnchorEnd;
    private TranslationPanel translationPanel;
    private final StringBuilder translationDraft=new StringBuilder();
    private String draftDeleted;
    private String translationOutput="",translationStatus="",translationSelected;
    private KeyLayout.Language translationFrom=KeyLayout.Language.RU,translationTo=KeyLayout.Language.EN;
    private EditorInfo translationOrigin;
    private int translationAnchorStart,translationAnchorEnd;
    private boolean translationBusy,translationModelsReady;
    private SavedLogins savedLogins;
    private SystemInlineAutofill systemInline;
    private final WordSession wordSession = new WordSession();
    private CursorWord editingWord;
    private final ArrayDeque<Integer> expectedSelections = new ArrayDeque<>();
    private SuggestionRepository predictionRepository;
    private SuggestionStrip suggestionStrip;
    private final KeyButton[] suggestionKeys = new KeyButton[3];
    private final String[] suggestedWords = new String[3];
    private CorrectionEngine.Candidate correctionCandidate;
    private int correctionIndex=-1, keepIndex=-1;
    private String suppressedCorrection;
    private UndoCorrection undoCorrection;
    private static final class UndoCorrection {
        final WordSession.Snapshot before;
        final KeyLayout.Language oldLayout, target;
        final String replacement, word,original;
        final CorrectionEngine.Kind kind;
        final int editingCaretOffset;
        UndoCorrection(WordSession.Snapshot before,KeyLayout.Language oldLayout,
                       KeyLayout.Language target,String replacement,String word,String original,CorrectionEngine.Kind kind,int editingCaretOffset) {
            this.before=before;this.oldLayout=oldLayout;this.target=target;
            this.replacement=replacement;this.word=word;this.original=original;this.kind=kind;this.editingCaretOffset=editingCaretOffset;
        }
    }
    private final android.content.BroadcastReceiver packChanges=new android.content.BroadcastReceiver(){
        @Override public void onReceive(android.content.Context context,android.content.Intent intent){
            android.net.Uri uri=intent.getData();
            if(uri==null)return;
            String name=uri.getSchemeSpecificPart();
            if(name==null)return;
            if(name.startsWith(LanguagePack.PREFIX))refreshLanguagePacks();
            else if((EngineContract.VOICE.equals(name)&&voiceMode)||(EngineContract.TRANSLATION.equals(name)&&translationMode)){
                clearVoice();clearTranslation();
                if(keyboard!=null&&isInputViewShown())render();
            }
        }
    };
    private void refreshLanguagePacks(){
        if(predictionRepository!=null&&predictionRepository.refreshPacks()){
            resetWordSession();
            if(keyboard!=null&&isInputViewShown()&&!clipboardPanel&&!voiceMode&&!translationMode&&!loginConfirmPanel&&!emojiPanel&&!computerPanel)render();
        }
    }
    private final SharedPreferences.OnSharedPreferenceChangeListener clipboardPreference = (prefs, key) -> {
        if ("clipboardHistory".equals(key) && clipboardHistory != null) {
            clipboardHistory.setEnabled(prefs.getBoolean("clipboardHistory", true));
            if (clipboardPanel) renderClipboardPanel();
        } else if("compactLandscape".equals(key)||"compactWidth".equals(key)||"compactRight".equals(key)) {
            configureKeyboardFrame();
            if(keyboard!=null&&isInputViewShown()&&!clipboardPanel&&!voiceMode&&!translationMode&&!loginConfirmPanel&&!emojiPanel&&!computerPanel)render();
        } else if("systemAutofill".equals(key)) {
            if(Build.VERSION.SDK_INT>=30 && systemInline!=null && !prefs.getBoolean("systemAutofill",true))systemInline.clear();
        } else if(key!=null&&key.startsWith("languagePackEnabled_")){
            refreshLanguagePacks();
        } else if ("suggestions".equals(key) || "learnStyle".equals(key) || "suggestionDisplay".equals(key)
                || "autocorrect".equals(key) || "wrongLayout".equals(key) || "autoLanguage".equals(key)
                || "savedLogins".equals(key) || "showTranslator".equals(key) || "showVoice".equals(key) || "showAiButton".equals(key) || "showGlobe".equals(key) || "showEmoji".equals(key) || "showComputer".equals(key) || "autoCapitalize".equals(key)) {
            if("showTranslator".equals(key)&&!prefs.getBoolean("showTranslator",true))clearTranslation();
            if("showVoice".equals(key)&&!prefs.getBoolean("showVoice",true))clearVoice();
            if("showComputer".equals(key)&&!prefs.getBoolean("showComputer",false))closeComputer();
            if("autoCapitalize".equals(key)){manualCase=false;if(!prefs.getBoolean("autoCapitalize",false)&&!this.capsLock)this.shifted=false;}
            resetWordSession();
            if (keyboard != null && isInputViewShown() && !clipboardPanel) render();
        }
    };
    private KeyLayout.Language language = KeyLayout.Language.RU;
    private boolean symbols, secondSymbols, shifted, capsLock;
    private long lastShift;
    private EditorInfo editor;
    private int selectionStart = -1, selectionEnd = -1;
    private static final int KEY = 0, ACTION = 1, ACCENT = 2;
    private SharedPreferences preferences() {
        return getSharedPreferences("appearance", MODE_PRIVATE);
    }

    @Override public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = preferences();
        language = KeyLayout.parse(prefs.getString("language", "RU"));
        cycle = new LanguageCycle(language,
                KeyLayout.parse(prefs.getString("primaryA", "RU")),
                KeyLayout.parse(prefs.getString("primaryB", "EN")),
                KeyLayout.parse(prefs.getString("lastPrimary", language.name())));
        loadAppearance();
        savedLogins=new SavedLogins(this);personalEntries=new PersonalEntries(this);emojiCatalog=new EmojiCatalog(this);
        translationController=new TranslationController(this);
        voiceController=new VoiceController(this);
        if(Build.VERSION.SDK_INT>=30)systemInline=new SystemInlineAutofill(this);
        clipboardHistory = new ClipboardHistory(this, this::isSelectedIme, this::localToolsEligible, () -> {
            if (clipboardPanel && keyboard != null) renderClipboardPanel();
        });
        clipboardHistory.setEnabled(prefs.getBoolean("clipboardHistory", true));
        prefs.registerOnSharedPreferenceChangeListener(clipboardPreference);
        android.content.IntentFilter packages=new android.content.IntentFilter();
        packages.addAction(android.content.Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(android.content.Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(android.content.Intent.ACTION_PACKAGE_REPLACED);
        packages.addAction(android.content.Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packChanges,packages,android.content.Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(packChanges,packages);
    }
    private boolean isSelectedIme() {
        String selected = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        ComponentName component = selected == null ? null : ComponentName.unflattenFromString(selected);
        return isInputViewShown() || (component != null && getPackageName().equals(component.getPackageName()));
    }
    private void loadAppearance() {
        SharedPreferences prefs = preferences();
        cycle.configure(KeyLayout.parse(prefs.getString("primaryA", "RU")),
                KeyLayout.parse(prefs.getString("primaryB", "EN")));
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        String choice=prefs.getString("theme","light");
        palette = KeyboardPalette.resolve(choice,dark);
        boolean custom="custom".equals(choice);
        if(custom)palette=KeyboardPalette.custom(
                KeyboardPalette.color(prefs.getString("customBackground","#E1E8E7"),KeyboardPalette.LIGHT.background),
                KeyboardPalette.color(prefs.getString("customKey","#FCFDF9"),KeyboardPalette.LIGHT.key),
                KeyboardPalette.color(prefs.getString("customText","#192D30"),KeyboardPalette.LIGHT.text),
                KeyboardPalette.color(prefs.getString("customAccent","#147E80"),KeyboardPalette.LIGHT.accent));
        transparentTheme="transparent".equals(choice)||custom;
        int opacity=Math.max(0,Math.min(100,prefs.getInt(custom?"customOpacity":"transparentOpacity",custom?100:45)));
        surfaceColor=transparentTheme?(palette.background&0xffffff)|(Math.round(opacity*255/100f)<<24):palette.background;
        if("adaptive".equals(choice)&&Build.VERSION.SDK_INT>=31){palette=systemPalette(dark);surfaceColor=palette.background;}
        if (keyboard != null) keyboard.setBackgroundColor(surfaceColor);
        if (keyboardFrame != null) keyboardFrame.setBackgroundColor(transparentTheme?android.graphics.Color.TRANSPARENT:palette.background);
        configureKeyboardFrame();
        if (getWindow() != null) {
            Window w = getWindow().getWindow();
            if (w != null) {
                if(!windowBackgroundSaved){normalWindowBackground=w.getDecorView().getBackground();windowBackgroundSaved=true;}
                if(transparentTheme)w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                else if(windowBackgroundSaved&&normalWindowBackground!=null)w.setBackgroundDrawable(normalWindowBackground);
                w.setNavigationBarColor(palette.background);
                boolean light = palette.lightBackground();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowInsetsController controller = w.getInsetsController();
                    if (controller != null) controller.setSystemBarsAppearance(
                            light ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0,
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                } else {
                    View decor = w.getDecorView();
                    int flags = decor.getSystemUiVisibility();
                    decor.setSystemUiVisibility(light ? flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                            : flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                }
            }
        }
    }
    @android.annotation.TargetApi(31)
    private KeyboardPalette systemPalette(boolean dark){
        try{
            return KeyboardPalette.adaptive(dark,
                    getColor(dark?android.R.color.system_neutral1_900:android.R.color.system_neutral1_100),
                    getColor(dark?android.R.color.system_neutral1_800:android.R.color.system_neutral1_10),
                    getColor(dark?android.R.color.system_accent2_800:android.R.color.system_accent2_100),
                    getColor(dark?android.R.color.system_accent1_200:android.R.color.system_accent1_600),
                    getColor(dark?android.R.color.system_neutral1_50:android.R.color.system_neutral1_900),
                    getColor(dark?android.R.color.system_neutral1_900:android.R.color.system_neutral1_10),
                    getColor(dark?android.R.color.system_neutral1_700:android.R.color.system_neutral1_100),
                    getColor(dark?android.R.color.system_accent2_700:android.R.color.system_accent2_200),
                    getColor(dark?android.R.color.system_accent1_100:android.R.color.system_accent1_700));
        }catch(android.content.res.Resources.NotFoundException unavailable){return dark?KeyboardPalette.DARK:KeyboardPalette.LIGHT;}
    }
    @Override public void onConfigurationChanged(Configuration config){
        super.onConfigurationChanged(config);
        if(computerPanel||"adaptive".equals(preferences().getString("theme","light"))||"transparent".equals(preferences().getString("theme","light"))||"custom".equals(preferences().getString("theme","light"))){loadAppearance();if(keyboard!=null&&isInputViewShown())render();}
    }
    private boolean compactLandscape(){
        return getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE
                &&preferences().getBoolean("compactLandscape",false);
    }
    private void configureKeyboardFrame(){
        if(keyboardFrame==null)return;
        SharedPreferences prefs=preferences();
        keyboardFrame.configure(compactLandscape(),prefs.getInt("compactWidth",CompactLayout.DEFAULT_PERCENT),
                prefs.getBoolean("compactRight",true));
    }
    @Override public View onCreateInputView() {
        loadAppearance();
        keyboard = new KeyboardSurface(this, () -> !voiceMode && !holdActive && !clipboardPanel && !loginConfirmPanel && !emojiPanel && !computerPanel && !layoutPanel, this::switchLanguage,()->{cancelDigitPaste();keyboardTouchSequence++;});
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            if(computerPanel&&right>left&&shouldUseWideComputer()!=computerWideRendered)
                keyboard.post(()->{if(computerPanel&&shouldUseWideComputer()!=computerWideRendered)render();});
        });
        keyboard.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        keyboard.setBackgroundColor(surfaceColor);
        keyboard.setPadding(dp(3), dp(7), dp(3), dp(7));
        keyboardFrame=new CompactKeyboardFrame(this);
        keyboardFrame.setBackgroundColor(transparentTheme?android.graphics.Color.TRANSPARENT:palette.background);
        keyboardFrame.addView(keyboard,new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.START));
        // Keep navigation space on the full-width frame, not on the narrowed keyboard.
        keyboardFrame.setOnApplyWindowInsetsListener((view,insets)->{
            int left=0,right=0,bottom;
            boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.navigationBars()|WindowInsets.Type.mandatorySystemGestures());
                bottom=bars.bottom;
                if(landscape){android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.navigationBars()|WindowInsets.Type.displayCutout());left=safe.left;right=safe.right;}
            }else{
                bottom=insets.getSystemWindowInsetBottom();
                if(landscape){left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();}
            }
            view.setPadding(left,0,right,bottom);
            return insets;
        });
        configureKeyboardFrame();
        render();
        return keyboardFrame;
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        cancelHeldKey();clearVoice();clearTranslation();closeComputer();closeLayoutPanel();deleteUndo=null;manualCase=false;editor = attribute;
        if(digitPasteText!=null&&(!numericKeyEditor()||!digitPastePackage.equals(attribute.packageName)))cancelDigitPaste();
        clearInlineAutofill();
        loginConfirmPanel=false;
        resetWordSession();
        selectionStart = attribute.initialSelStart;
        selectionEnd = attribute.initialSelEnd;
        shifted = capsLock = secondSymbols = false;
        lastShift = 0;
        if (cycle != null) cycle.reset();
        int type = attribute.inputType & InputType.TYPE_MASK_CLASS;
        symbols = type == InputType.TYPE_CLASS_NUMBER || type == InputType.TYPE_CLASS_PHONE
                || type == InputType.TYPE_CLASS_DATETIME;
    }
    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        loadAppearance();
        if(predictionRepository!=null)predictionRepository.refreshPacks();
        if (clipboardHistory != null) clipboardHistory.captureCurrent();
        captureCursorContext();
        if (keyboard != null) render();
    }
    @Override public void onDestroy() {
        clearVoice();clearTranslation();cancelDigitPaste();
        preferences().unregisterOnSharedPreferenceChangeListener(clipboardPreference);
        unregisterReceiver(packChanges);
        if (clipboardHistory != null) clipboardHistory.close();
        clearInlineAutofill();
        flushPredictions();
        resetWordSession();
        cancelHeldKey();
        closeComputer();
        super.onDestroy();
    }
    @Override public void onFinishInputView(boolean finishingInput) {
        clearVoice();clearTranslation();clearInlineAutofill();
        flushPredictions();
        resetWordSession();
        clipboardPanel = loginConfirmPanel = emojiPanel = false;
        closeComputer();closeLayoutPanel();deleteUndo=null;manualCase=false;
        cancelHeldKey();
        super.onFinishInputView(finishingInput);
    }
    @Override public void onFinishInput() {
        clearVoice();clearTranslation();clearInlineAutofill();
        flushPredictions();
        resetWordSession();
        cancelHeldKey();
        closeComputer();closeLayoutPanel();deleteUndo=null;manualCase=false;
        super.onFinishInput();
        if (cycle != null) cycle.reset();
        editor = null;
        selectionStart = selectionEnd = -1;
    }
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart,
                                            int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        if(voiceMode&&voiceAnchorStart>=0&&(newSelStart!=voiceAnchorStart||newSelEnd!=voiceAnchorEnd)){
            clearVoice();if(keyboard!=null&&isInputViewShown())render();
        }
        if(translationMode&&translationAnchorStart>=0&&(newSelStart!=translationAnchorStart||newSelEnd!=translationAnchorEnd)) {
            clearTranslation();if(keyboard!=null&&isInputViewShown())render();
        }
        boolean ownCommit = newSelStart >= 0 && newSelStart == newSelEnd
                && expectedSelections.contains(newSelStart);
        boolean external=!ownCommit&&(newSelStart!=selectionStart||newSelEnd!=selectionEnd);
        if (ownCommit) {
            while (!expectedSelections.isEmpty()) {
                if (expectedSelections.removeFirst() == newSelStart) break;
            }
        } else if(external)resetWordSession();
        if (!ownCommit || expectedSelections.isEmpty()) {
            selectionStart = newSelStart;
            selectionEnd = newSelEnd;
            if(external||editingWord!=null){
                if(external){manualCase=false;deleteUndo=null;if(layoutPanel){closeLayoutPanel();render();}}
                captureCursorContext();refreshSuggestions();
            }else updateAutoCapitalization();
        }
    }
    @android.annotation.TargetApi(30)
    @Override public android.view.inputmethod.InlineSuggestionsRequest onCreateInlineSuggestionsRequest(android.os.Bundle uiExtras) {
        if(Build.VERSION.SDK_INT<30||systemInline==null||!preferences().getBoolean("systemAutofill",true))return null;
        return systemInline.request();
    }
    @android.annotation.TargetApi(30)
    @Override public boolean onInlineSuggestionsResponse(android.view.inputmethod.InlineSuggestionsResponse response) {
        if(Build.VERSION.SDK_INT<30||systemInline==null||!preferences().getBoolean("systemAutofill",true))return false;
        systemInline.receive(response,this::showInlineAutofill);return true;
    }
    private void showInlineAutofill() {
        if(Build.VERSION.SDK_INT>=30&&systemInline!=null&&keyboard!=null&&!clipboardPanel&&!loginConfirmPanel&&!translationMode&&!voiceMode&&!emojiPanel
                &&preferences().getBoolean("systemAutofill",true))systemInline.attach(keyboard);
    }
    private void clearInlineAutofill() {
        if(Build.VERSION.SDK_INT>=30&&systemInline!=null)systemInline.clear();
    }
    @Override public void onWindowHidden() {
        clearVoice();clearTranslation();cancelDigitPaste();cancelHeldKey();super.onWindowHidden();
    }
    @Override public void onUnbindInput() {
        clearVoice();clearTranslation();cancelDigitPaste();cancelHeldKey();super.onUnbindInput();
    }
    @Override public boolean onEvaluateFullscreenMode() { return false; }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout row() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.HORIZONTAL);
        result.setGravity(Gravity.CENTER);
        keyboard.addView(result, new LinearLayout.LayoutParams(-1,-2));
        return result;
    }
    private KeyButton key(LinearLayout row, String label, String description, float weight, int kind,
                     float fontSize, Runnable action) {
        KeyButton k = new KeyButton(this);
        k.setGravity(Gravity.CENTER);
        k.setText(label);
        k.setTextSize(fontSize);
        k.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        k.setSingleLine(true);
        k.setContentDescription(description);
        k.setFocusable(true);
        k.setClickable(true);
        styleKey(k, kind);
        k.setOnClickListener(v -> {wordDelete.clear();action.run();});
        int h = Math.max(40, Math.min(64, preferences().getInt("height", 48)));
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            h = Math.min(h, 36);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(h), weight);
        p.setMargins(dp(1.5f), dp(2), dp(1.5f), dp(2));
        row.addView(k,p);
        return k;
    }
    private void styleKey(KeyButton k, int kind) {
        int normalColor = kind == ACCENT ? palette.accent : kind == ACTION ? palette.action : palette.key;
        int pressedColor = kind == ACCENT ? palette.accentPressed
                : kind == ACTION ? palette.actionPressed : palette.pressed;
        k.setTextColor(kind == ACCENT ? palette.accentText : palette.text);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(normalColor);
        normal.setCornerRadius(dp(8));
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(pressedColor);
        pressed.setCornerRadius(dp(8));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},pressed);
        states.addState(new int[]{},normal);
        k.setBackground(states);
    }
    private static final class Glyph {
        final String primary;
        final String[] alternatives;
        Glyph(String primary, String[] alternatives) {
            this.primary = primary; this.alternatives = alternatives;
        }
    }
    private String label(String value) {
        return !symbols ? KeyLayout.shifted(value, language, shifted) : value;
    }
    private String[] alternateLabels(String[] values) {
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) result[i] = label(values[i]);
        return result;
    }
    private void updateGlyph(KeyButton key, Glyph glyph) {
        String main = label(glyph.primary);
        String[] alt = alternateLabels(glyph.alternatives);
        key.setText(main);
        key.setKeyHint(alt.length == 0 ? "" : alt[0], palette.hint);
        key.setContentDescription(alt.length == 0 ? main : main + "; hold " + String.join(", ", alt));
    }
    private void glyph(LinearLayout row, String character, String[] alternatives, float fontSize) {
        Glyph data = new Glyph(character, alternatives);
        KeyButton k = key(row,label(character),label(character),1,KEY,fontSize,
                () -> input(label(character)));
        k.setTag(data);
        updateGlyph(k, data);
        if (alternatives.length != 0) attachHold(k, () -> alternateLabels(alternatives), null);
    }
    private void punctuation(LinearLayout row, String primary, String description, String[] alternatives) {
        KeyButton k = key(row,primary,description,0.8f,KEY,21,() -> input(primary));
        k.setKeyHint(alternatives[0], palette.hint);
        k.setContentDescription(description + "; hold " + String.join(", ", alternatives));
        attachHold(k, () -> alternatives, null);
    }
    private void cancelHeldKey() {
        touchEpoch++;
        touchHandler.removeCallbacksAndMessages(null);
        if (alternatePopup != null) {
            alternatePopup.dismiss();
            alternatePopup = null;
        }
        holdActive = false;
    }
    private void attachHold(KeyButton key,Supplier<String[]> alternatives,Runnable specialHold){
        attachHold(key,alternatives,specialHold,ViewConfiguration.getLongPressTimeout());
    }
    private void attachHold(KeyButton key, Supplier<String[]> alternatives, Runnable specialHold,long delayMs) {
        // Native accessibility long-click has the same default alternate or picker action.
        key.setOnLongClickListener(v -> {
            if (specialHold != null) specialHold.run();
            else input(alternatives.get()[0]);
            return true;
        });
        key.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private boolean held, cancelled;
            private String fallback;
            private Runnable hold;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        wordDelete.clear();cancelHeldKey();
                        startX = event.getRawX(); startY = event.getRawY();
                        held = cancelled = false; fallback = null;
                        key.setPressed(true);
                        hold = () -> {
                            if (cancelled || !key.isAttachedToWindow()) return;
                            held = holdActive = true;
                            if (specialHold != null) {
                                specialHold.run();
                            } else {
                                String[] values = alternatives.get();
                                fallback = values[0];
                                try { alternatePopup = new AlternatePopup(key, keyboard, values, palette); }
                                catch (WindowManager.BadTokenException ignored) {
                                    // Still allow the default hold character if the IME window closes.
                                }
                            }
                        };
                        touchHandler.postDelayed(hold,delayMs);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX, dy = event.getRawY() - startY;
                        if (held) {
                            if (alternatePopup != null && Math.abs(dx) > dp(7))
                                alternatePopup.move(event.getRawX());
                        } else {
                            if (Math.abs(dx) > dp(10) || Math.abs(dy) > dp(10))
                                touchHandler.removeCallbacks(hold);
                            if (Math.abs(dx) > dp(24) || Math.abs(dy) > dp(24))
                                cancelled = true;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        touchHandler.removeCallbacks(hold);
                        key.setPressed(false);
                        if (held) {
                            String chosen = alternatePopup != null ? alternatePopup.value() : fallback;
                            cancelHeldKey();
                            if (chosen != null) input(chosen);
                        } else if (!cancelled) {
                            key.performClick();
                        }
                        return true;
                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_CANCEL:
                        cancelled = true;
                        key.setPressed(false);
                        cancelHeldKey();
                        return true;
                    default:
                        return true;
                }
            }
        });
    }
    private boolean tripleClearEnabled(){
        // Do not change existing deletion behavior in protected/numeric/login fields.
        return localToolsEligible()&&BackspacePress.TRIPLE_MODE.equals(
                preferences().getString(BackspacePress.MODE_PREF,BackspacePress.HOLD_MODE));
    }
    /** A tap deletes a character, tap+hold deletes a word, the selected clear gesture clears once. */
    private void attachRepeatDelete(KeyButton key) {
        key.setContentDescription("Delete; tap then briefly hold for word; "+(tripleClearEnabled()?
                "three quick taps to clear field":"hold "+BackspacePress.clearDelay(preferences().getInt(BackspacePress.CLEAR_PREF,BackspacePress.DEFAULT_CLEAR_MS))+" ms to clear field"));
        key.setOnClickListener(v->tapDelete());
        key.setOnLongClickListener(v->{delete();return true;});
        key.setOnTouchListener(new View.OnTouchListener() {
            private final BackspacePress press=new BackspacePress();
            private final TripleDeleteGesture triple=new TripleDeleteGesture();
            private float downX,downY;
            private int swipe;
            private boolean moved;
            private long epoch;
            private EditorInfo origin;
            private InputConnection originConnection;
            private boolean inDraft,tripleMode;
            private int tapStart,tapEnd;
            private boolean valid(){
                return press.active()&&epoch==touchEpoch&&key.isAttachedToWindow()&&isInputViewShown()
                        &&origin!=null&&editor==origin&&translationMode==inDraft
                        &&getCurrentInputConnection()==originConnection;
            }
            private final Runnable clear=()->{
                if(valid()&&press.clear(android.os.SystemClock.uptimeMillis())){
                    holdActive=true;touchHandler.removeCallbacks(this.repeat);touchHandler.removeCallbacks(this.wordHold);
                    clearCurrentField(originConnection,inDraft);
                }
            };
            private final Runnable wordHold=()->{
                if(valid()&&press.canTryWord()&&tryDeleteWord(press.down())){
                    triple.clear();press.wordDone();holdActive=true;touchHandler.removeCallbacks(this.repeat);touchHandler.removeCallbacks(clear);
                }
            };
            private final Runnable repeat=new Runnable() {
                @Override public void run() {
                    if(!valid()||!press.repeat(android.os.SystemClock.uptimeMillis()))return;
                    triple.clear();holdActive=true;delete();
                    if(valid())touchHandler.postDelayed(this,75);
                }
            };
            private void stop() {
                press.cancel();touchHandler.removeCallbacks(repeat);touchHandler.removeCallbacks(wordHold);touchHandler.removeCallbacks(clear);
                if(epoch==touchEpoch)holdActive=false;
                if(key.getParent()!=null)key.getParent().requestDisallowInterceptTouchEvent(false);
                key.setPressed(false);origin=null;originConnection=null;
            }
            @Override public boolean onTouch(View view,MotionEvent event) {
                switch(event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        key.getParent().requestDisallowInterceptTouchEvent(true);
                        swipe=0;moved=false;
                        cancelHeldKey();epoch=touchEpoch;origin=editor;originConnection=getCurrentInputConnection();inDraft=translationMode;
                        tripleMode=tripleClearEnabled();if(!tripleMode)triple.clear();
                        tapStart=inDraft?translationDraft.length():selectionStart;
                        tapEnd=inDraft?translationDraft.length():selectionEnd;
                        press.begin(android.os.SystemClock.uptimeMillis(),preferences().getInt(BackspacePress.CLEAR_PREF,BackspacePress.DEFAULT_CLEAR_MS),!tripleMode);downX=event.getRawX();downY=event.getRawY();
                        key.setPressed(true);touchHandler.postDelayed(wordHold,WordDeleteGesture.HOLD_MS);
                        touchHandler.postDelayed(repeat,BackspacePress.REPEAT_MS);
                        if(!tripleMode)touchHandler.postDelayed(clear,press.clearDelay());return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                        if(!moved&&(Math.abs(dx)>dp(12)||Math.abs(dy)>dp(12))){
                            moved=true;swipe=valid()&&!holdActive&&Math.abs(dx)>Math.abs(dy)*1.5f?(dx>0?1:-1):0;
                            triple.clear();wordDelete.clear();press.cancel();
                            touchHandler.removeCallbacks(repeat);touchHandler.removeCallbacks(wordHold);touchHandler.removeCallbacks(clear);
                            key.setPressed(false);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        float finalDx=event.getRawX()-downX,finalDy=event.getRawY()-downY;
                        if(moved){
                            boolean act=swipe!=0&&epoch==touchEpoch&&key.isAttachedToWindow()&&isInputViewShown()
                                    &&origin==editor&&originConnection==getCurrentInputConnection()
                                    &&Math.abs(finalDx)>=dp(18)&&Math.abs(finalDx)>Math.abs(finalDy)*1.5f
                                    &&(finalDx>0?1:-1)==swipe;
                            int direction=swipe;InputConnection c=originConnection;boolean draft=inDraft;stop();
                            if(act)swipeBackspace(direction,c,draft);return true;
                        }
                        boolean tap=valid()&&press.release();
                        // A tap that undoes autocorrection is never one of the three clear taps.
                        boolean clearByTriple=tap&&tripleMode&&undoCorrection==null
                                &&(inDraft?translationDraft.length()==tapStart:selectionStart==tapStart&&selectionEnd==tapEnd)
                                &&triple.tap(press.down(),android.os.SystemClock.uptimeMillis(),keyboardTouchSequence,
                                        origin,originConnection,inDraft,tapStart,tapEnd);
                        if(!tap||!tripleMode||undoCorrection!=null)triple.clear();
                        InputConnection activeConnection=originConnection;boolean draft=inDraft;
                        stop();
                        if(clearByTriple)clearCurrentField(activeConnection,draft);
                        else if(tap)key.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:case MotionEvent.ACTION_POINTER_DOWN:
                        triple.clear();wordDelete.clear();stop();return true;
                    default:return true;
                }
            }
        });
    }
    /** Select through only the captured editor connection; never read/cache editor text. */
    private void clearCurrentField(InputConnection connection,boolean inDraft){
        deleteUndo=null;
        cancelDigitPaste();resetWordSession();recordInteraction();
        if(inDraft){
            draftDeleted=null;translationDraft.setLength(0);draftChanged();return;
        }
        if(connection==null||connection!=getCurrentInputConnection())return;
        boolean batch=connection.beginBatchEdit();
        try{
            if(connection.performContextMenuAction(android.R.id.selectAll)){
                connection.commitText("",1);
            }
        }finally{if(batch)connection.endBatchEdit();}
        selectionStart=selectionEnd=-1;refreshSuggestions();
    }
    private void renderClipboardToolbar() {
        LinearLayout toolbar = row();
        toolbar.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        KeyButton paste=key(toolbar,"","Paste current system clipboard; hold for recent copied text",
                1,ACTION,14,this::pasteCurrent);
        paste.setIcon(KeyButton.Icon.CLIPBOARD);
        LinearLayout.LayoutParams size=(LinearLayout.LayoutParams)paste.getLayoutParams();
        size.height=dp(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?26:33);
        size.width=dp(compactLandscape()?34:42);size.weight=0;
        paste.setLayoutParams(size);
        attachHold(paste, null, this::openClipboardPanel);
        if(localToolsEligible()&&preferences().getBoolean("showTranslator",true)) {
            KeyButton trans=key(toolbar,"","Open offline translator",1,ACTION,15,this::openTranslation);
            trans.setIcon(KeyButton.Icon.TRANSLATE);
            LinearLayout.LayoutParams tr=(LinearLayout.LayoutParams)trans.getLayoutParams();tr.width=dp(compactLandscape()?42:48);tr.weight=0;tr.height=size.height;trans.setLayoutParams(tr);
        }
        if(localToolsEligible()&&preferences().getBoolean("showEmoji",true)){
            KeyButton emoji=key(toolbar,"","Open emoji picker",1,ACTION,15,this::openEmoji);
            emoji.setIcon(KeyButton.Icon.EMOJI);
            LinearLayout.LayoutParams ep=(LinearLayout.LayoutParams)emoji.getLayoutParams();
            ep.width=dp(compactLandscape()?34:38);ep.weight=0;ep.height=size.height;emoji.setLayoutParams(ep);
        }
        if(editorEligible()&&preferences().getBoolean("wrongLayout",true)){
            KeyButton fix=key(toolbar,"⇄","Исправить раскладку выделения или слова",.85f,ACTION,17,this::openLayoutPanel);
            fix.setLayoutParams(new LinearLayout.LayoutParams(dp(42),size.height));
        }
        if(editorEligible()&&preferences().getBoolean("showAiButton",false)){
            KeyButton ai=key(toolbar,"AI","Process explicitly selected text",1,ACTION,14,this::openAiSelected);
            LinearLayout.LayoutParams ap=(LinearLayout.LayoutParams)ai.getLayoutParams();ap.width=dp(compactLandscape()?34:38);ap.weight=0;ap.height=size.height;ai.setLayoutParams(ap);
        }
        java.util.Arrays.fill(suggestionKeys,null);
        java.util.Arrays.fill(suggestedWords,null);
        suggestionStrip=null;
        if(numericKeyEditor()&&editor.inputType!=InputType.TYPE_NULL&&preferences().getBoolean("digitPaste",true)) {
            String label=language==KeyLayout.Language.RU?"\u041f\u043e \u0446\u0438\u0444\u0440\u0430\u043c":language==KeyLayout.Language.HE?"\u05e1\u05e4\u05e8\u05d5\u05ea \u05d1\u05e0\u05e4\u05e8\u05d3":"Paste digits";
            KeyButton digits=key(toolbar,label,"Paste digits as individual keys",2.6f,ACTION,14,this::pasteDigits);
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)digits.getLayoutParams();lp.height=size.height;digits.setLayoutParams(lp);
            toolbar.addView(new View(this),new LinearLayout.LayoutParams(0,1,1.8f));return;
        }
        if(loginMode()) {
            renderLoginChips(toolbar,paste,size);
            return;
        }
        if (predictionAllowed() && !symbols) {
            String mode=preferences().getString("suggestionDisplay","adaptive");
            boolean adaptive="adaptive".equals(mode);
            LinearLayout chips=toolbar;
            if(adaptive) {
                suggestionStrip=new SuggestionStrip(this);
                toolbar.addView(suggestionStrip,new LinearLayout.LayoutParams(0,-2,1));
                chips=new LinearLayout(this);
                chips.setOrientation(LinearLayout.HORIZONTAL);
                chips.setGravity(Gravity.CENTER_VERTICAL);
                suggestionStrip.addView(chips,new android.widget.FrameLayout.LayoutParams(-2,-2));
            }
            for (int i=0; i<suggestionKeys.length; i++) {
                final int index=i;
                KeyButton suggestion=key(chips,"","Suggestion",adaptive?1:4.4f/3f,KEY,13,
                        () -> chooseSuggestion(index));
                LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)suggestion.getLayoutParams();
                params.height=size.height;
                suggestion.setLayoutParams(params);
                if(adaptive) {
                    params.width=-2;
                    suggestion.setLayoutParams(params);
                    suggestion.setMinWidth(dp(56));
                    suggestion.setPadding(dp(9),0,dp(9),0);
                    suggestion.setEllipsize(null);
                } else if("marquee".equals(mode)) {
                    suggestion.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                    suggestion.setMarqueeRepeatLimit(-1);
                    suggestion.setHorizontallyScrolling(true);
                    suggestion.setSelected(true);
                    suggestion.setPadding(dp(4),0,dp(4),0);
                } else suggestion.setEllipsize(TextUtils.TruncateAt.END);
                suggestion.setOnLongClickListener(v->blockSuggestion(index));
                suggestionKeys[i]=suggestion;
            }
        } else {
            View spacer = new View(this);
            toolbar.addView(spacer, new LinearLayout.LayoutParams(0,1,4.4f));
        }
    }
    private void openAiSelected(){
        if(!editorEligible()||!preferences().getBoolean("showAiButton",false))return;
        if(!EngineAvailability.aiReady(this)){
            startActivity(new android.content.Intent(this,SettingsActivity.class).putExtra("show_ai",true)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));return;
        }
        InputConnection c=getCurrentInputConnection();
        if(c==null||selectionStart<0||selectionEnd<0||selectionStart==selectionEnd||Math.abs(selectionStart-selectionEnd)>4000){
            android.widget.Toast.makeText(this,"Выделите текст (до 4000 символов) для AI",android.widget.Toast.LENGTH_SHORT).show();return;
        }
        CharSequence text=c.getSelectedText(0);
        if(text==null||text.length()==0||text.length()>4000)return;
        android.content.Intent intent=EngineAvailability.aiIntent().setAction(android.content.Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain").putExtra(android.content.Intent.EXTRA_PROCESS_TEXT,text.toString())
                .putExtra(android.content.Intent.EXTRA_PROCESS_TEXT_READONLY,true)
                // Separate transient task: finishing returns to the source editor, not an existing AI settings task.
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        |android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try{startActivity(intent);}catch(RuntimeException denied){android.widget.Toast.makeText(this,"Модуль AI недоступен",android.widget.Toast.LENGTH_SHORT).show();}
    }
    private void clearVoice(){
        voiceGeneration++;
        if(voiceRecorder!=null){voiceRecorder.cancel();voiceRecorder=null;}
        if(voiceController!=null)voiceController.cancel();
        voiceMode=voiceRecording=voiceBusy=false;voiceResult="";voiceStatus="";voiceOrigin=null;voicePanel=null;
    }
    private void closeVoice(){clearVoice();if(keyboard!=null&&isInputViewShown())render();}
    private boolean translationReady(){return EngineAvailability.ready(this,false)&&TranslationModelStore.installed(this);}
    private boolean voiceReady(){return EngineAvailability.ready(this,true)&&activeVoiceModel()!=null&&micAllowed();}
    private VoiceModelStore.Model activeVoiceModel(){
        return new VoiceModelStore(this).active(preferences().getString("voiceModel","small"));
    }
    private boolean micAllowed(){return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
    private String voiceMessage(String ru,String en){return language==KeyLayout.Language.RU?ru:en;}
    private void openVoice(){
        if(!localToolsEligible()||!preferences().getBoolean("showVoice",true)||getCurrentInputConnection()==null)return;
        clearVoice();clearTranslation();resetWordSession();cancelHeldKey();voiceMode=true;
        voiceOrigin=editor;voiceAnchorStart=selectionStart;voiceAnchorEnd=selectionEnd;
        voiceLanguage=preferences().getString("voiceLanguage",language.name().toLowerCase(java.util.Locale.ROOT));
        if(!VoiceAudio.languageAllowed(voiceLanguage))voiceLanguage="auto";
        voiceStatus=!EngineAvailability.ready(this,true)?voiceMessage("\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438 \u0434\u0432\u0438\u0436\u043e\u043a \u0440\u0430\u0441\u043f\u043e\u0437\u043d\u0430\u0432\u0430\u043d\u0438\u044f \u0432 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430\u0445","Install the speech engine in settings")
                :activeVoiceModel()==null?voiceMessage("Установи модель речи","Import a speech model in settings")
                :!micAllowed()?voiceMessage("Разреши микрофон в настройках","Grant microphone permission in settings")
                :voiceMessage("Локальная запись","Ready for local voice input");
        render();
        if(voiceReady())startVoiceRecording();
    }
    private void renderVoicePanel(){
        java.util.Arrays.fill(suggestionKeys,null);java.util.Arrays.fill(suggestedWords,null);suggestionStrip=null;
        voicePanel=new VoicePanel(this,palette,language==KeyLayout.Language.RU,this::closeVoice,()->{
            if(voiceBusy)return;
            String[] options={"auto","ru","en","he"};int index=java.util.Arrays.asList(options).indexOf(voiceLanguage);
            voiceLanguage=options[(index+1)%options.length];preferences().edit().putString("voiceLanguage",voiceLanguage).apply();updateVoicePanel();
        },this::voiceAction,this::insertVoice);
        keyboard.addView(voicePanel.view(),new LinearLayout.LayoutParams(-1,-2));updateVoicePanel();
    }
    private void updateVoicePanel(){
        if(voicePanel!=null)voicePanel.update(voiceLanguage,voiceStatus,voiceResult,voiceRecording,voiceBusy,voiceReady());
    }
    private void voiceAction(){
        if(!voiceMode||voiceBusy)return;
        if(voiceRecording){
            voiceRecording=false;voiceBusy=true;voiceStatus=voiceMessage("Завершаю запись...","Stopping recording...");
            if(voiceRecorder!=null)voiceRecorder.stop();updateVoicePanel();return;
        }
        if(!voiceReady()){
            closeVoice();startActivity(new android.content.Intent(this,SettingsActivity.class)
                    .putExtra("show_voice",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));return;
        }
        startVoiceRecording();
    }
    private void startVoiceRecording(){
        VoiceModelStore.Model model=activeVoiceModel();
        if(!voiceMode||!isInputViewShown()||!localToolsEligible()||model==null||!voiceReady())return;
        if(voiceRecorder!=null)voiceRecorder.cancel();
        voiceController.cancel();long task=++voiceGeneration;
        voiceResult="";voiceRecording=true;voiceBusy=false;
        voiceStatus=voiceMessage("● Запись • до 20 секунд • Стоп для распознавания","\u25cf Recording - up to 20 seconds - tap Stop");
        updateVoicePanel();
        voiceRecorder=new VoiceRecorder(this);
        voiceRecorder.start((pcm,error)->{
            if(!voiceMode||task!=voiceGeneration){if(pcm!=null)java.util.Arrays.fill(pcm,(byte)0);return;}
            voiceRecorder=null;voiceRecording=false;
            if(error!=null||pcm==null){voiceBusy=false;voiceStatus=error==null?"No audio detected":error;updateVoicePanel();return;}
            voiceBusy=true;voiceStatus=voiceMessage("Распознавание без сети...","Recognizing on this device...");updateVoicePanel();
            voiceController.start(pcm,voiceLanguage,model.id,(text,failure)->{
                if(!voiceMode||task!=voiceGeneration)return;
                voiceBusy=false;voiceResult=text==null?"":text;
                voiceStatus=failure==null?voiceMessage("Проверь текст и нажми «Вставить»","Preview - insert if correct"):failure;updateVoicePanel();
            });
        });
    }
    private void insertVoice(){
        if(!voiceMode||voiceRecording||voiceBusy||voiceResult.isEmpty()||editor!=voiceOrigin||!localToolsEligible())return;
        if(voiceAnchorStart>=0&&(selectionStart!=voiceAnchorStart||selectionEnd!=voiceAnchorEnd)){closeVoice();return;}
        if(getCurrentInputConnection()==null)return;
        String text=voiceResult;clearVoice();render();commitInput(text,false);
    }
    private void clearTranslation() {
        draftDeleted=null;
        wordDelete.clear();
        if(translationController!=null)translationController.cancel();translationMode=false;translationBusy=false;
        translationDraft.setLength(0);translationOutput="";translationStatus="";translationSelected=null;
        translationOrigin=null;translationPanel=null;
    }
    private void closeTranslation(){clearTranslation();if(keyboard!=null)render();}
    private void openTranslation() {
        if(!localToolsEligible()||!preferences().getBoolean("showTranslator",true)||getCurrentInputConnection()==null)return;
        clearTranslation();resetWordSession();cancelHeldKey();
        translationOrigin=editor;translationAnchorStart=selectionStart;translationAnchorEnd=selectionEnd;
        if(selectionStart>=0&&selectionEnd>=0&&selectionStart!=selectionEnd) {
            if(Math.abs(selectionStart-selectionEnd)>1000){android.widget.Toast.makeText(this,"Select up to 1000 characters",android.widget.Toast.LENGTH_SHORT).show();return;}
            CharSequence selected=getCurrentInputConnection().getSelectedText(0);
            if(selected==null||selected.length()==0||selected.length()>1000){android.widget.Toast.makeText(this,"Cannot read selected text",android.widget.Toast.LENGTH_SHORT).show();return;}
            translationSelected=selected.toString();translationDraft.append(translationSelected);
        }
        KeyLayout.Language detected=CorrectionEngine.detectScript(translationDraft.toString());
        translationFrom=detected==null?language:detected;
        translationTo=KeyLayout.parse(preferences().getString("translationTarget",translationFrom==KeyLayout.Language.EN?"RU":"EN"));
        if(translationTo==translationFrom)translationTo=translationFrom.next();
        translationModelsReady=translationReady();translationMode=true;
        translationStatus=translationModelsReady?"Offline on this device":(!EngineAvailability.ready(this,false)?"Install the translation engine in settings first":"Import models in settings first");
        render();
    }
    private void renderTranslationToolbar(){
        java.util.Arrays.fill(suggestionKeys,null);java.util.Arrays.fill(suggestedWords,null);suggestionStrip=null;
        translationPanel=new TranslationPanel(this,palette,language==KeyLayout.Language.RU,this::closeTranslation,
            ()->{translationFrom=translationFrom.next();if(translationFrom==translationTo)translationFrom=translationFrom.next();draftChanged();},
            ()->{translationTo=translationTo.next();if(translationTo==translationFrom)translationTo=translationTo.next();preferences().edit().putString("translationTarget",translationTo.name()).apply();draftChanged();},
            this::pasteTranslationText,this::runTranslation,this::insertTranslation);
        keyboard.addView(translationPanel.view(),new LinearLayout.LayoutParams(-1,-2));updateTranslationPanel();
    }
    private void updateTranslationPanel(){
        if(translationPanel!=null)translationPanel.update(translationFrom,translationTo,translationDraft.toString(),translationOutput,translationStatus,translationBusy,translationModelsReady);
    }
    private void draftChanged(){
        wordDelete.clear();
        if(translationBusy&&translationController!=null)translationController.cancel();translationBusy=false;translationOutput="";
        translationStatus=translationModelsReady?"Offline on this device":(!EngineAvailability.ready(this,false)?"Install the translation engine in settings first":"Import models in settings first");updateTranslationPanel();
    }
    private void appendTranslation(String text){
        draftDeleted=null;
        if(translationDraft.length()+text.length()>1000){translationStatus="Maximum 1000 characters";updateTranslationPanel();return;}
        translationDraft.append(text);draftChanged();
        if(shifted&&!capsLock&&!symbols){shifted=false;refreshShiftState();}
    }
    private void deleteTranslation(){
        if(translationDraft.length()>0){int end=translationDraft.length(),start=translationDraft.offsetByCodePoints(end,-1);draftDeleted=translationDraft.substring(start);translationDraft.delete(start,end);draftChanged();}
    }
    private void pasteTranslationText(){
        // Explicit Paste action into the local draft; never capture editor or clipboard text in the background.
        android.content.ClipboardManager manager=(android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        try{android.content.ClipData clip=manager==null?null:manager.getPrimaryClip();if(clip==null||clip.getItemCount()<1)return;
            CharSequence text=clip.getItemAt(0).getText();if(text!=null)appendTranslation(text.toString());
        }catch(SecurityException ignored){}
    }
    private void runTranslation(){
        if(!translationMode||translationBusy)return;
        translationModelsReady=translationReady();
        if(!translationModelsReady){closeTranslation();startActivity(new android.content.Intent(this,SettingsActivity.class).putExtra("show_translation",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));return;}
        if(translationDraft.toString().trim().isEmpty())return;
        translationBusy=true;translationOutput="";translationStatus="Translating locally...";updateTranslationPanel();
        translationController.start(translationFrom,translationTo,translationDraft.toString(),(result,error)->{
            if(!translationMode)return;translationBusy=false;translationOutput=result==null?"":result;
            translationStatus=error==null?(translationSelected!=null?"Preview - replace selection":"Preview - insert if correct"):error;
            updateTranslationPanel();
        });
    }
    private void insertTranslation(){
        if(!translationMode||translationBusy||translationOutput.isEmpty()||editor!=translationOrigin||!localToolsEligible())return;
        if(translationAnchorStart>=0&&(selectionStart!=translationAnchorStart||selectionEnd!=translationAnchorEnd)){closeTranslation();return;}
        InputConnection connection=getCurrentInputConnection();if(connection==null)return;
        if(translationSelected!=null&&!TextUtils.equals(translationSelected,connection.getSelectedText(0))){closeTranslation();return;}
        String output=translationOutput;clearTranslation();render();commitInput(output,false);
    }
    private boolean declaredLogin() {
        return editor!=null && LoginFieldPolicy.declaredLogin(editor.inputType,editor.hintText,editor.fieldName);
    }
    private boolean loginMode() {
        return editor!=null && editor.packageName!=null && preferences().getBoolean("savedLogins",false)
                && LoginFieldPolicy.allowed(editor.inputType,editor.imeOptions,editor.hintText,editor.fieldName);
    }
    private void renderLoginChips(LinearLayout toolbar,KeyButton paste,LinearLayout.LayoutParams size) {
        SuggestionStrip strip=new SuggestionStrip(this);
        toolbar.addView(strip,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);chips.setGravity(Gravity.CENTER_VERTICAL);
        strip.addView(chips,new android.widget.FrameLayout.LayoutParams(-2,-2));
        String caption=language==KeyLayout.Language.RU?"+ \u041b\u043e\u0433\u0438\u043d":language==KeyLayout.Language.HE?"+ \u05e9\u05de\u05d5\u05e8":"+ Save login";
        KeyButton save=key(chips,caption,"Offer to save current username",1,ACTION,13,this::offerSaveLogin);
        wrapLoginChip(save,size.height);
        String app=editor.packageName;
        for(String value:savedLogins.list(app)) {
            KeyButton item=key(chips,value,"Fill saved username "+value,1,KEY,13,()->fillSavedLogin(value));
            wrapLoginChip(item,size.height);
            item.setOnLongClickListener(v->{savedLogins.remove(app,value);render();return true;});
        }
    }
    private void wrapLoginChip(KeyButton chip,int height) {
        LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)chip.getLayoutParams();
        lp.width=-2;lp.height=height;lp.weight=1;chip.setLayoutParams(lp);
        chip.setMinWidth(dp(65));chip.setPadding(dp(9),0,dp(9),0);chip.setEllipsize(null);
    }
    /** Read only the explicitly selected login editor, and only after a user tap on Save/Fill. */
    private String readLoginField() {
        if(!loginMode()||selectionStart<0||selectionStart!=selectionEnd)return null;
        InputConnection connection=getCurrentInputConnection();if(connection==null)return null;
        int max=LoginFieldPolicy.MAX_LENGTH;
        CharSequence before=connection.getTextBeforeCursor(max+1,0),after=connection.getTextAfterCursor(max+1,0);
        if(before==null||after==null||before.length()!=selectionStart||before.length()+after.length()>max)return null;
        return before.toString()+after;
    }
    private void offerSaveLogin() {
        // The Save chip appears automatically in supported fields, but typing alone never saves or reads a username.
        String value=readLoginField();
        if(!LoginFieldPolicy.validLogin(value)) {
            android.widget.Toast.makeText(this,"Enter a username or email first",android.widget.Toast.LENGTH_SHORT).show();return;
        }
        EditorInfo origin=editor;String app=origin.packageName;
        cancelHeldKey();loginConfirmPanel=true;clipboardPanel=false;keyboard.removeAllViews();
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(8),dp(12),dp(8),dp(12));
        android.widget.TextView title=new android.widget.TextView(this);title.setText("Save this login on this device for this app?");
        title.setTextColor(palette.text);title.setTextSize(18);panel.addView(title);
        android.widget.TextView current=new android.widget.TextView(this);current.setText(value);current.setTextColor(palette.text);
        current.setTextSize(18);current.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);current.setPadding(0,dp(16),0,dp(16));
        panel.addView(current,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);panel.addView(actions);
        key(actions,"Cancel","Cancel saving login",1,ACTION,16,this::render);
        key(actions,"Save","Confirm save login",1,ACCENT,16,()->{
            if(editor==origin&&loginMode())savedLogins.save(app,value);
            render();
        });
        keyboard.addView(panel,new LinearLayout.LayoutParams(-1,-2));
    }
    private void fillSavedLogin(String value) {
        if(!loginMode()||!savedLogins.list(editor.packageName).contains(value))return;
        // User chose a saved username; replace only this declared field, never a password or other editor.
        String existing=readLoginField();InputConnection connection=getCurrentInputConnection();
        if(existing==null||connection==null||!connection.beginBatchEdit())return;
        int caret=selectionEnd;boolean changed=false;
        try {
            if(!connection.setSelection(0,existing.length()))return;
            changed=connection.commitText(value,1);
            if(!changed)connection.setSelection(caret,caret);
            if(changed){resetWordSession();selectionStart=selectionEnd=value.length();rememberCaret(selectionEnd);recordInteraction();}
        }finally{connection.endBatchEdit();}
    }
    private boolean editorEligible() {
        return editor != null && !declaredLogin() && PredictionEligibility.allowed(editor.inputType, editor.imeOptions);
    }
    private boolean localToolsEligible() {
        return editor != null && PredictionEligibility.localToolsAllowed(editor.inputType,editor.imeOptions,
                editor.hintText,editor.fieldName);
    }
    private boolean predictionAllowed() {
        return editor!=null&&PredictionEligibility.manualSuggestionsAllowed(editor.inputType,editor.imeOptions,
                editor.hintText,editor.fieldName)&&preferences().getBoolean("suggestions",true);
    }
    // Keep only newly typed words in RAM for manual completions. Automatic processing and learning stay strict.
    private boolean shortcutsActive(){return editorEligible()&&personalEntries!=null&&personalEntries.hasShortcuts()
            &&preferences().getBoolean("expandShortcuts",true);}
    private boolean trackTypedWords(){return trackWords()||predictionAllowed()||shortcutsActive();}
    private boolean trackWords() {
        return editorEligible() && (preferences().getBoolean("suggestions",true)
                || preferences().getBoolean("autocorrect",true)
                || preferences().getBoolean("learnStyle",false));
    }
    private KeyLayout.Language predictionLanguage() {
        if(preferences().getBoolean("autoLanguage",true)) {
            KeyLayout.Language detected=CorrectionEngine.detectScript(wordSession.prefix());
            if(detected!=null)return detected;
        }
        return language;
    }
    private CorrectionEngine.Candidate findCorrection(boolean typoSuggestions){return findCorrection(typoSuggestions,false);}
    private CorrectionEngine.Candidate findCorrection(boolean typoSuggestions,boolean atBoundary) {
        if(!trackWords() || wordSession.prefix().equals(suppressedCorrection))return null;
        if(personalEntries.containsWord(predictionLanguage(),wordSession.prefix()))return null;
        return predictions().correction(language,wordSession,typoSuggestions,preferences().getBoolean("wrongLayout",true),atBoundary);
    }
    private SuggestionRepository predictions() {
        if (predictionRepository == null) predictionRepository=SuggestionRepository.get(this);
        return predictionRepository;
    }
    private WordSession.Observer observer() {
        return trackWords() && preferences().getBoolean("learnStyle", false)
                ? (lang,p2,p1,word) -> predictions().learn(lang,p2,p1,word) : null;
    }
    private void flushPredictions() {
        if(predictionRepository!=null) predictionRepository.flush();
    }
    private boolean cursorContextAllowed(){
        return editor!=null&&!translationMode&&!voiceMode&&!clipboardPanel&&!loginConfirmPanel&&!emojiPanel&&!symbols
                &&(predictionAllowed()||editorEligible()&&preferences().getBoolean("autocorrect",true)||shortcutsActive())
                &&PredictionEligibility.cursorContextAllowed(editor.inputType,editor.imeOptions,editor.hintText,editor.fieldName);
    }
    private CursorWord readCursorContext(){
        if(!cursorContextAllowed()||selectionStart<0||selectionStart!=selectionEnd)return null;
        InputConnection connection=getCurrentInputConnection();
        if(connection==null)return null;
        try{
            return CursorWord.parse(connection.getTextBeforeCursor(CursorWord.BEFORE_LIMIT,0),
                    connection.getTextAfterCursor(CursorWord.AFTER_LIMIT,0));
        }catch(RuntimeException unavailable){return null;}
    }
    private void captureCursorContext(){
        CursorWord cursor=readCursorContext();
        if(cursor==null||!cursor.hasSurrounding){
            if(editingWord!=null)wordSession.reset();
            editingWord=null;return;
        }
        adoptCursorWord(cursor);
    }
    private void adoptCursorWord(CursorWord cursor){
        editingWord=cursor;
        KeyLayout.Language script=CorrectionEngine.detectScript(cursor.word());
        if(script==null)script=CorrectionEngine.detectScript(cursor.previous1);
        wordSession.loadCursorContext(script==null?language:script,cursor);
    }
    private void captureExistingWordIfNeeded(){
        // Some editors do not send a selection callback when the user taps the already-active caret.
        if(editingWord!=null||wordSession.hasSuggestionContext())return;
        CursorWord cursor=readCursorContext();
        if(cursor!=null&&!cursor.word().isEmpty())adoptCursorWord(cursor);
    }
    private void resetWordSession() {
        editingWord=null;
        wordDelete.clear();
        wordSession.reset();
        expectedSelections.clear();
        undoCorrection=null;
        suppressedCorrection=null;
    }
    private void rememberCaret(int position) {
        if (position<0) return;
        expectedSelections.addLast(position);
        while(expectedSelections.size()>32) expectedSelections.removeFirst();
    }
    private String displaySuggestion(String word) {
        if (capsLock) return word.toUpperCase(language.locale);
        String prefix=wordSession.prefix();
        boolean capital=prefix.isEmpty() ? shifted : Character.isUpperCase(prefix.codePointAt(0));
        if (capital && !word.isEmpty()) {
            int end=Character.charCount(word.codePointAt(0));
            return word.substring(0,end).toUpperCase(language.locale)+word.substring(end);
        }
        return word;
    }
    private void refreshSuggestions() {
        updateAutoCapitalization();
        if (clipboardPanel || loginConfirmPanel || emojiPanel || computerPanel || layoutPanel || translationMode || symbols || suggestionKeys[0]==null) return;
        java.util.ArrayList<String> candidates=new java.util.ArrayList<>();
        correctionCandidate=null; correctionIndex=keepIndex=emojiSuggestionIndex=shortcutSuggestionIndex=-1;
        if(predictionAllowed()&&wordSession.hasSuggestionContext()) {
            correctionCandidate=findCorrection(true);
            if(correctionCandidate!=null) {
                correctionIndex=0;
                candidates.add(correctionCandidate.word);
                String original=SuggestionModel.normalise(predictionLanguage(),wordSession.prefix());
                if(!original.equals(correctionCandidate.word)) { keepIndex=candidates.size(); candidates.add(original); }
            }
            if(editorEligible()){
                String expansion=personalEntries.expansion(wordSession.prefix());
                if(expansion!=null&&candidates.size()<3){shortcutSuggestionIndex=candidates.size();candidates.add(expansion);}
                for(String custom:personalEntries.words(predictionLanguage(),wordSession.prefix()))if(!candidates.contains(custom))candidates.add(custom);
            }
            for(String candidate:predictions().suggest(predictionLanguage(),wordSession,editorEligible()))
                if(!candidates.contains(candidate)) candidates.add(candidate);
            String contextual=wordSession.prefix().isEmpty()?EmojiCatalog.context(wordSession.previous1()):null;
            if(contextual!=null&&preferences().getBoolean("showEmoji",true)&&!candidates.isEmpty()){
                while(candidates.size()>2)candidates.remove(candidates.size()-1);
                if(shortcutSuggestionIndex>=candidates.size())shortcutSuggestionIndex=-1;
                emojiSuggestionIndex=candidates.size();candidates.add(contextual);
            }
        }
        if(suggestionStrip!=null) suggestionStrip.scrollTo(0,0);
        for(int i=0;i<suggestionKeys.length;i++) {
            String word=i<candidates.size()?candidates.get(i):null;
            suggestedWords[i]=word;
            KeyButton button=suggestionKeys[i];
            String displayed=word==null?"":(i==emojiSuggestionIndex||i==shortcutSuggestionIndex?word:displaySuggestion(word));
            if(i==correctionIndex && correctionCandidate!=null)
                displayed=(correctionCandidate.kind==CorrectionEngine.Kind.LAYOUT?"\u21aa ":"\u2248 ")
                        +CorrectionEngine.caseLike(correctionSource(correctionCandidate),word,correctionCandidate.language);
            if(!TextUtils.equals(button.getText(),displayed))button.setText(displayed);
            button.setContentDescription(word==null?"":(i==correctionIndex?"Correct to ":"Suggestion ")+displayed);
            button.setEnabled(word!=null);
            button.setVisibility(word==null?View.INVISIBLE:View.VISIBLE);
        }
    }
    private boolean blockSuggestion(int index) {
        if(!editorEligible()||!predictionAllowed()||symbols||clipboardPanel||index==keepIndex||index==emojiSuggestionIndex||index==shortcutSuggestionIndex||suggestedWords[index]==null)return true;
        KeyLayout.Language lang=index==correctionIndex&&correctionCandidate!=null?correctionCandidate.language:predictionLanguage();
        if(predictions().block(lang,suggestedWords[index])) {
            android.widget.Toast.makeText(this,"Suggestion blocked",android.widget.Toast.LENGTH_SHORT).show();
            refreshSuggestions();
        }
        return true;
    }
    private void chooseSuggestion(int index) {
        deleteUndo=null;
        if (!predictionAllowed() || symbols || clipboardPanel) return;
        String word=suggestedWords[index];
        if (word==null) return;
        if(index==emojiSuggestionIndex){insertEmoji(word);return;}
        if(index==shortcutSuggestionIndex&&editorEligible()){expandShortcut(word,automaticSuggestionBoundary());return;}
        String boundary=automaticSuggestionBoundary();
        if(index==correctionIndex && correctionCandidate!=null) {
            finishPendingCorrection();
            if(!acceptCorrection(correctionCandidate,boundary)) { resetWordSession(); refreshSuggestions(); }
            return;
        }
        if(editingWord!=null&&!editingWord.word().isEmpty()){
            CursorWord expected=editingWord;
            String fixed=displaySuggestion(word);
            if(replaceEditingWord(expected,fixed+boundary)){
                finishPendingCorrection();recordInteraction();captureCursorContext();refreshSuggestions();
            }else{captureCursorContext();refreshSuggestions();}
            return;
        }
        String tail=wordSession.completion(predictionLanguage(),word,capsLock,shifted,boundary);
        // Explicitly choosing the literal typed word or a completion never triggers autocorrection.
        if (tail!=null&&!tail.isEmpty()) commitInput(tail,true,false);
    }
    private String automaticSuggestionBoundary() {
        if(editingWord!=null&&!editingWord.word().isEmpty())return PunctuationSpacing.suggestionBoundary(editingWord.next);
        InputConnection connection=getCurrentInputConnection();
        if(connection==null||selectionStart<0||selectionEnd<0||!predictionAllowed())return " ";
        // Only the immediate following character, in eligible fields and on explicit suggestion selection.
        try{return PunctuationSpacing.suggestionBoundary(connection.getTextAfterCursor(1,0));}
        catch(RuntimeException unavailable){return " ";}
    }
    private void finishPendingCorrection() {
        UndoCorrection pending=undoCorrection;
        undoCorrection=null;
        if(pending==null || pending.editingCaretOffset>=0 || !trackWords() || !preferences().getBoolean("learnStyle",false))return;
        boolean same=pending.oldLayout==pending.target;
        if(pending.kind==CorrectionEngine.Kind.NEGATION_JOIN)
            predictions().learn(pending.target,"",same?pending.before.previous2:"",pending.word);
        else if(pending.kind==CorrectionEngine.Kind.NEGATION_SPLIT){
            String[] words=pending.word.split(" ",2);
            if(words.length==2){
                predictions().learn(pending.target,same?pending.before.previous2:"",same?pending.before.previous1:"",words[0]);
                predictions().learn(pending.target,same?pending.before.previous1:"",words[0],words[1]);
            }
        }else predictions().learn(pending.target,same?pending.before.previous2:"",same?pending.before.previous1:"",pending.word);
    }
    /** Replace a bounded, freshly verified whole word; do not touch neighboring separators. */
    private boolean replaceEditingWord(CursorWord expected,String replacement){
        CursorWord current=readCursorContext();
        if(expected==null||expected.word().isEmpty()||!expected.sameSpan(current))return false;
        InputConnection connection=getCurrentInputConnection();
        if(connection==null||selectionStart<current.left.length())return false;
        int caret=selectionEnd,start=caret-current.left.length(),end=caret+current.right.length();
        boolean batch=connection.beginBatchEdit(),changed=false;
        try{
            if(!connection.setSelection(start,end))return false;
            changed=connection.commitText(replacement,1);
            if(!changed)connection.setSelection(caret,caret);
            return changed;
        }finally{
            if(changed){selectionStart=selectionEnd=start+replacement.length();expectedSelections.clear();rememberCaret(selectionEnd);}
            if(batch)connection.endBatchEdit();
        }
    }
    /** Replace only the verified suffix of our own recent typing; never read other editor content. */
    private boolean replaceTypedSuffix(String expected,String replacement) {
        if(!editorEligible() || expected.isEmpty() || selectionStart<0 || selectionStart!=selectionEnd
                || selectionEnd<expected.length()) return false;
        InputConnection connection=getCurrentInputConnection();
        if(connection==null)return false;
        CharSequence actual=connection.getTextBeforeCursor(expected.length(),0);
        if(!TextUtils.equals(expected,actual))return false;
        int caret=selectionEnd,start=caret-expected.length();
        boolean batch=connection.beginBatchEdit();
        boolean changed=false;
        try {
            if(!connection.setSelection(start,caret))return false;
            changed=connection.commitText(replacement,1);
            if(!changed)connection.setSelection(caret,caret);
            return changed;
        } finally {
            if(changed) {
                selectionStart=selectionEnd=start+replacement.length();
                expectedSelections.clear();
                rememberCaret(selectionEnd);
            }
            if(batch)connection.endBatchEdit();
        }
    }
    private void selectLayout(KeyLayout.Language target) {
        cycle.select(target);language=target;
        preferences().edit().putString("language",target.name()).apply();
        shifted=capsLock=false; symbols=secondSymbols=false; lastShift=0;
    }
    private String correctionSource(CorrectionEngine.Candidate candidate){
        if(candidate.kind==CorrectionEngine.Kind.NEGATION_JOIN)
            return wordSession.negationJoinPrefix()+wordSession.prefix();
        return wordSession.prefix();
    }
    private boolean acceptCorrection(CorrectionEngine.Candidate candidate,String boundary) {
        WordSession.Snapshot before=wordSession.snapshot();
        KeyLayout.Language old=language;
        boolean join=candidate.kind==CorrectionEngine.Kind.NEGATION_JOIN;
        boolean split=candidate.kind==CorrectionEngine.Kind.NEGATION_SPLIT;
        if(join&&wordSession.negationJoinPrefix().isEmpty())return false;
        CursorWord edited=editingWord;
        boolean fromEditor=edited!=null&&!edited.word().isEmpty();
        if(fromEditor&&join)return false;
        String original=correctionSource(candidate);
        String fixed=CorrectionEngine.caseLike(original,candidate.word,candidate.language);
        if(fromEditor?!replaceEditingWord(edited,fixed+boundary):!replaceTypedSuffix(original,fixed+boundary))return false;
        if(old!=candidate.language)wordSession.reset();else wordSession.restore(before);
        if(fromEditor){wordSession.replacePrefix("");wordSession.accept(candidate.language,fixed+boundary,null);}
        else if(join){wordSession.rewindNegationForCorrection();wordSession.accept(candidate.language,fixed+boundary,null);}
        else if(split){wordSession.replacePrefix("");wordSession.accept(candidate.language,fixed+boundary,null);}
        else{
            wordSession.replacePrefix(fixed);
            wordSession.accept(candidate.language,boundary,null);
        } // Learn only after next deliberate typing, not on guess.
        boolean switchLayout=old!=candidate.language&&preferences().getBoolean("autoLanguage",true);
        if(switchLayout)selectLayout(candidate.language);
        else if(old!=candidate.language)wordSession.reset();
        else if(shifted&&!capsLock) { shifted=false; refreshShiftState(); }
        undoCorrection=new UndoCorrection(before,old,candidate.language,fixed+boundary,candidate.word,original,candidate.kind,
                fromEditor?edited.left.length():-1);
        if(fromEditor)captureCursorContext();
        suppressedCorrection=null;
        recordInteraction();
        manualCase=false;
        if(switchLayout)render();else refreshSuggestions();
        return true;
    }
    private boolean undoLastCorrection() {
        UndoCorrection pending=undoCorrection;
        undoCorrection=null;
        if(pending==null || !replaceTypedSuffix(pending.replacement,pending.original))return false;
        boolean changedLayout=language!=pending.oldLayout;
        if(changedLayout)selectLayout(pending.oldLayout);
        wordSession.restore(pending.before);
        if(pending.editingCaretOffset>=0){
            InputConnection connection=getCurrentInputConnection();
            int caret=selectionEnd-pending.original.length()+pending.editingCaretOffset;
            if(connection!=null&&caret>=0&&connection.setSelection(caret,caret)){
                selectionStart=selectionEnd=caret;rememberCaret(caret);
            }
            captureCursorContext();
        }
        suppressedCorrection=pending.before.prefix;
        recordInteraction();
        if(changedLayout)render();else refreshSuggestions();
        return true;
    }
    private void cancelDigitPaste() {
        digitPasteHandler.removeCallbacks(digitPasteStep);digitPasteText=null;digitPastePackage=null;digitPasteIndex=0;
    }
    private void pasteDigits() {
        cancelDigitPaste();
        if(!numericKeyEditor()||editor.inputType==InputType.TYPE_NULL||editor.packageName==null)return;
        // Explicit user chose this separate paste action; no automatic clipboard reads or saved sequence.
        android.content.ClipboardManager manager=(android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        CharSequence text=null;
        try {
            android.content.ClipData clip=manager==null?null:manager.getPrimaryClip();
            if(clip!=null&&clip.getItemCount()==1)text=clip.getItemAt(0).getText();
        }catch(SecurityException ignored){}
        if(!DigitPastePolicy.accepts(text)) {
            android.widget.Toast.makeText(this,"Clipboard must contain 2-32 digits only",android.widget.Toast.LENGTH_SHORT).show();return;
        }
        digitPasteText=text.toString();digitPastePackage=editor.packageName;digitPasteIndex=0;
        digitPasteDeadline=android.os.SystemClock.uptimeMillis()+6000;
        sendNextPastedDigit();
    }
    private void sendNextPastedDigit() {
        if(digitPasteText==null)return;
        if(android.os.SystemClock.uptimeMillis()>digitPasteDeadline||!isInputViewShown()) {cancelDigitPaste();return;}
        if(editor==null||getCurrentInputConnection()==null) {
            digitPasteHandler.postDelayed(digitPasteStep,80);return;
        }
        if(!numericKeyEditor()||!digitPastePackage.equals(editor.packageName)) {cancelDigitPaste();return;}
        char digit=digitPasteText.charAt(digitPasteIndex++);
        input(String.valueOf(digit));
        if(digitPasteText==null)return;
        if(digitPasteIndex>=digitPasteText.length())cancelDigitPaste();
        else digitPasteHandler.postDelayed(digitPasteStep,130);
    }
    private void pasteCurrent() {
        deleteUndo=null;
        resetWordSession();
        // Explicit user action. Ask the active editor to paste; do not inspect
        // or retain the system clipboard merely to perform an ordinary paste.
        InputConnection connection = getCurrentInputConnection();
        if (connection != null && connection.performContextMenuAction(android.R.id.paste)) {
            recordInteraction();
            selectionStart = selectionEnd = -1;
        }
        refreshSuggestions();
    }
    private void openClipboardPanel() {
        if (clipboardHistory == null || keyboard == null || !localToolsEligible()) return;
        clipboardSaved=false;clipboardHistory.captureCurrent();
        renderClipboardPanel();
    }

    private void renderClipboardPanel() {
        wordDelete.clear();
        if (keyboard == null || clipboardHistory == null || !localToolsEligible()) return;
        cancelHeldKey();
        clipboardPanel = true;
        keyboard.removeAllViews();
        int keyHeight = Math.max(40, Math.min(64, preferences().getInt("height", 48)));
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            keyHeight = Math.min(keyHeight, 36);
        int rows = 4 + (!symbols && preferences().getBoolean("numberRow", false) ? 1 : 0);
        int listHeight = rows * (keyHeight+4) + 33 - 40;
        boolean ru = language == KeyLayout.Language.RU;
        boolean he = language == KeyLayout.Language.HE;
        String title = clipboardSaved?"Сохранённые":ru ? "Недавний буфер" : he ? "לוח אחרון" : "Recent clipboard";
        String clear = ru ? "Очистить" : he ? "נקה" : "Clear";
        boolean enabled = preferences().getBoolean("clipboardHistory", true);
        String empty = enabled
                ? (ru ? "Нет недавних элементов" : he ? "אין פריטים אחרונים" : "No recent copied text")
                : (ru ? "История отключена в настройках" : he ? "ההיסטוריה כבויה בהגדרות" : "History disabled in settings");
        ClipboardPanel panel = new ClipboardPanel(this, palette);
        java.util.Map<String,String> saved=personalEntries.list("phrases");
        java.util.List<String> items=clipboardSaved?new java.util.ArrayList<>(saved.keySet()):clipboardHistory.recent();
        keyboard.addView(panel.create(listHeight, title, clipboardSaved?"Назад":clear, clipboardSaved?"Нет сохранённых фраз":empty, items,
                this::render,
                () -> { if(clipboardSaved)clipboardSaved=false;else clipboardHistory.clear(); renderClipboardPanel(); },
                item -> { String value=clipboardSaved?saved.get(item):item;render();if(value!=null)commitInput(value,false); },
                clipboardSaved,()->{clipboardSaved=!clipboardSaved;renderClipboardPanel();},
                ()->startActivity(new android.content.Intent(this,SettingsActivity.class).putExtra("show_input",true)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)),
                item->{if(clipboardSaved)personalEntries.remove("phrases",item);else clipboardHistory.remove(item);renderClipboardPanel();},
                item->{String name=item.length()>128?item.substring(0,128):item;
                    if(personalEntries.save("phrases",null,name,item))renderClipboardPanel();
                }), new LinearLayout.LayoutParams(-1,-2));
    }

    private void openEmoji(){
        if(!localToolsEligible()||!preferences().getBoolean("showEmoji",true)||getCurrentInputConnection()==null)return;
        resetWordSession();emojiSearch=false;emojiQuery="";renderEmojiPanel();
    }
    private void renderEmojiPanel(){
        if(keyboard==null||!localToolsEligible())return;
        cancelHeldKey();wordDelete.clear();emojiPanel=true;clipboardPanel=loginConfirmPanel=false;
        java.util.Arrays.fill(suggestionKeys,null);java.util.Arrays.fill(suggestedWords,null);suggestionStrip=null;
        keyboard.removeAllViews();
        boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        String title=language==KeyLayout.Language.RU?"Эмодзи":language==KeyLayout.Language.HE?"אימוג׳י":"Emoji";
        if(emojiSearch){renderEmojiSearch();return;}
        keyboard.addView(new EmojiPanel(this,palette).create(emojiCategory,landscape?105:172,title,
                this::render,this::delete,group->{emojiCategory=group;renderEmojiPanel();},
                this::insertEmoji,()->{emojiSearch=true;emojiQuery="";renderEmojiPanel();},
                items->emojiCatalog.rank(items,emojiPersonalization())),new LinearLayout.LayoutParams(-1,-2));
    }

    private boolean emojiPersonalization(){return editorEligible()&&preferences().getBoolean("learnStyle",false);}
    private void insertEmoji(String emoji){if(!localToolsEligible())return;emojiCatalog.used(emoji,emojiPersonalization());commitInput(emoji,false,false);}
    private void renderEmojiSearch(){
        LinearLayout controls=row();
        key(controls,"←","Назад к категориям",1,ACTION,18,()->{emojiSearch=false;emojiQuery="";renderEmojiPanel();});
        key(controls,emojiQuery.isEmpty()?"Поиск RU / EN":emojiQuery,"Поиск эмодзи",4,KEY,15,()->{});
        key(controls,"⌫","Удалить символ поиска",1,ACTION,17,()->{
            if(!emojiQuery.isEmpty())emojiQuery=emojiQuery.substring(0,emojiQuery.offsetByCodePoints(emojiQuery.length(),-1));renderEmojiPanel();
        });
        java.util.List<String> found=emojiCatalog.search(emojiQuery,emojiPersonalization());
        android.widget.HorizontalScrollView results=new android.widget.HorizontalScrollView(this);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.HORIZONTAL);results.addView(list);
        for(String emoji:found){
            KeyButton item=key(list,emoji,emoji,1,KEY,24,()->insertEmoji(emoji));
            item.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(44)));
        }
        if(found.isEmpty()){
            android.widget.TextView message=new android.widget.TextView(this);message.setText(emojiQuery.isEmpty()?"Введите запрос":"Ничего не найдено");
            message.setTextColor(palette.text);list.addView(message,new LinearLayout.LayoutParams(-2,dp(36)));
        }
        keyboard.addView(results,new LinearLayout.LayoutParams(-1,dp(44)));
        for(String[] letters:KeyLayout.letters(emojiSearchLanguage)){
            LinearLayout r=row();
            for(String letter:letters)key(r,letter,letter,1,KEY,18,()->{
                if(emojiQuery.length()<64)emojiQuery+=letter;renderEmojiPanel();
            });
        }
        LinearLayout bottom=row();
        key(bottom,emojiSearchLanguage.name(),"Язык поиска",1,ACTION,15,()->{
            emojiSearchLanguage=emojiSearchLanguage==KeyLayout.Language.RU?KeyLayout.Language.EN:KeyLayout.Language.RU;renderEmojiPanel();
        });
        key(bottom,"Пробел","Пробел поиска",3,KEY,15,()->{if(emojiQuery.length()<64)emojiQuery+=" ";renderEmojiPanel();});
        key(bottom,"Очистить","Очистить поиск",1.3f,ACTION,13,()->{emojiQuery="";renderEmojiPanel();});
    }
    private void closeLayoutPanel(){layoutPanel=false;layoutOriginal=null;layoutWord=null;layoutOrigin=null;layoutConnection=null;}
    private void openLayoutPanel(){
        if(!editorEligible()||!preferences().getBoolean("wrongLayout",true)||selectionStart<0||selectionEnd<0)return;
        InputConnection connection=getCurrentInputConnection();if(connection==null)return;
        int length=Math.abs(selectionEnd-selectionStart);if(length>512)return;
        try{
            String original;CursorWord word=null;
            if(length>0){
                CharSequence selected=connection.getSelectedText(0);
                if(selected==null||selected.length()!=length)return;original=selected.toString();
            }else{
                word=CursorWord.parse(connection.getTextBeforeCursor(CursorWord.BEFORE_LIMIT,0),connection.getTextAfterCursor(CursorWord.AFTER_LIMIT,0));
                if(word==null||word.word().isEmpty())return;original=word.word();
            }
            KeyLayout.Language source=CorrectionEngine.detectScript(original);if(source==null)return;
            layoutOriginal=original;layoutWord=word;layoutOrigin=editor;layoutConnection=connection;
            layoutStart=selectionStart;layoutEnd=selectionEnd;layoutPanel=true;renderLayoutPanel(source);
        }catch(RuntimeException unavailable){closeLayoutPanel();}
    }
    private void renderLayoutPanel(KeyLayout.Language source){
        cancelHeldKey();keyboard.removeAllViews();
        LinearLayout header=row();
        key(header,"←","Назад",1,ACTION,18,()->{closeLayoutPanel();render();});
        key(header,"Исправить раскладку","Исправить раскладку",4,KEY,17,()->{});
        for(KeyLayout.Language target:KeyLayout.Language.values()){
            String converted=CorrectionEngine.convertSelectedLayout(source,target,layoutOriginal);
            if(converted!=null){
                LinearLayout r=row();key(r,target.display, target.display,1.2f,ACTION,14,()->acceptLayoutFix(converted,target));
                key(r,converted,"Заменить на "+converted,4,KEY,18,()->acceptLayoutFix(converted,target));
            }
        }
    }
    private void acceptLayoutFix(String value,KeyLayout.Language target){
        if(!layoutPanel||layoutConnection!=getCurrentInputConnection()||layoutOrigin!=editor||!editorEligible()
                ||layoutStart!=selectionStart||layoutEnd!=selectionEnd){closeLayoutPanel();render();return;}
        boolean changed=false;
        try{
            if(layoutWord!=null)changed=replaceEditingWord(layoutWord,value);
            else{
                CharSequence selected=layoutConnection.getSelectedText(0);
                if(layoutOriginal.contentEquals(selected==null?"":selected)&&layoutConnection.commitText(value,1)){
                    selectionStart=selectionEnd=Math.min(layoutStart,layoutEnd)+value.length();rememberCaret(selectionEnd);changed=true;
                }
            }
        }catch(RuntimeException unavailable){}
        closeLayoutPanel();
        if(changed){resetWordSession();deleteUndo=null;recordInteraction();if(preferences().getBoolean("autoLanguage",true))selectLayout(target);captureCursorContext();}
        render();
    }
    private void updateAutoCapitalization(){
        if(!preferences().getBoolean("autoCapitalize",false)||capsLock||manualCase||symbols||language==KeyLayout.Language.HE
                ||computerPanel||layoutPanel||translationMode||voiceMode||clipboardPanel||emojiPanel||loginConfirmPanel
                ||editor==null||!PredictionEligibility.cursorContextAllowed(editor.inputType,editor.imeOptions,editor.hintText,editor.fieldName)
                ||selectionStart!=selectionEnd)return;
        InputConnection connection=getCurrentInputConnection();
        if(connection==null)return;
        try{
            boolean next=SentenceCapitalization.atStart(connection.getTextBeforeCursor(SentenceCapitalization.CONTEXT,0),selectionStart);
            if(next!=shifted){shifted=next;lastShift=0;refreshShiftState();}
        }catch(RuntimeException unavailable){/* Editor cannot provide context; keep manual input. */}
    }
    private void closeComputer(){computerPanel=false;computer.reset();}
    private void openComputer(){
        if(translationMode||voiceMode||!preferences().getBoolean("showComputer",false)||getCurrentInputConnection()==null)return;
        resetWordSession();computer.reset();computerPanel=true;render();
    }
    private void computerModifier(LinearLayout r,String label,int flag){
        key(r,label,label+(computer.armed(flag)?" on":" off"),1,computer.armed(flag)?ACCENT:ACTION,14,()->{
            computer.toggle(flag);render();
        });
    }
    private void computerKey(LinearLayout r,String label,int code,int extra,float weight){
        key(r,label,label,weight,KEY,14,()->sendComputerKey(code,extra));
    }
    private void computerKeys(LinearLayout r,String[] labels,int[] codes){
        for(int i=0;i<labels.length;i++)computerKey(r,labels[i],codes[i],0,1);
    }
    private void computerCharacters(String chars,int extra){
        LinearLayout r=row();
        for(int i=0;i<chars.length();i++){
            char c=chars.charAt(i);computerKey(r,String.valueOf(c),ComputerKeyboard.keyCode(c),extra,1);
        }
    }
    private boolean shouldUseWideComputer(){
        Configuration config=getResources().getConfiguration();
        int width=config.screenWidthDp;
        if(keyboard!=null&&keyboard.getWidth()>0)
            width=Math.min(width,Math.round(keyboard.getWidth()/getResources().getDisplayMetrics().density));
        return ComputerKeyboard.wideLayout(width,config.screenHeightDp);
    }
    private void renderWideComputer(){
        LinearLayout fn=row();
        computerKey(fn,"Esc",KeyEvent.KEYCODE_ESCAPE,0,1);
        for(int i=0;i<12;i++)computerKey(fn,"F"+(i+1),KeyEvent.KEYCODE_F1+i,0,1);
        LinearLayout digits=row();
        for(char c:"`1234567890-=".toCharArray())computerKey(digits,String.valueOf(c),ComputerKeyboard.keyCode(c),0,1);
        computerKey(digits,"⌫",KeyEvent.KEYCODE_DEL,0,1.3f);
        LinearLayout qwerty=row();
        computerKey(qwerty,"Tab",KeyEvent.KEYCODE_TAB,0,1.3f);
        for(char c:"qwertyuiop[]\\".toCharArray())computerKey(qwerty,String.valueOf(c),ComputerKeyboard.keyCode(c),0,1);
        LinearLayout home=row();
        computerModifier(home,"Ctrl",ComputerKeyboard.CTRL);
        for(char c:"asdfghjkl;'".toCharArray())computerKey(home,String.valueOf(c),ComputerKeyboard.keyCode(c),0,1);
        computerKey(home,"Enter",KeyEvent.KEYCODE_ENTER,0,1.5f);
        LinearLayout low=row();
        computerModifier(low,"Shift",ComputerKeyboard.SHIFT);
        for(char c:"zxcvbnm,./".toCharArray())computerKey(low,String.valueOf(c),ComputerKeyboard.keyCode(c),0,1);
        computerKey(low,"↑",KeyEvent.KEYCODE_DPAD_UP,0,1);
        computerKey(low,"Del",KeyEvent.KEYCODE_FORWARD_DEL,0,1);
        LinearLayout base=row();
        key(base,"ABC","Return to normal keyboard",1.3f,ACTION,14,()->{closeComputer();render();});
        computerModifier(base,"Alt",ComputerKeyboard.ALT);
        computerKey(base,"Space",KeyEvent.KEYCODE_SPACE,0,4);
        computerKeys(base,new String[]{"←","↓","→","Ins"},new int[]{KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_INSERT});
        LinearLayout nav=row();
        computerKeys(nav,new String[]{"Home","End","PgUp","PgDn"},new int[]{KeyEvent.KEYCODE_MOVE_HOME,KeyEvent.KEYCODE_MOVE_END,KeyEvent.KEYCODE_PAGE_UP,KeyEvent.KEYCODE_PAGE_DOWN});
    }
    private void renderComputer(){
        computerWideRendered=shouldUseWideComputer();
        if(computerWideRendered){renderWideComputer();return;}
        LinearLayout top=row();
        key(top,"ABC","Return to normal keyboard",1.2f,ACTION,14,()->{closeComputer();render();});
        computerModifier(top,"Ctrl",ComputerKeyboard.CTRL);
        computerModifier(top,"Alt",ComputerKeyboard.ALT);
        computerModifier(top,"Shift",ComputerKeyboard.SHIFT);
        key(top,computer.page==0?"Keys":computer.page==1?"QWERTY":"#+=","Change computer page",1.25f,ACTION,13,()->{
            computer.page=(computer.page+1)%3;render();
        });
        if(computer.page==0){
            LinearLayout f1=row(),f2=row();
            for(int i=0;i<6;i++)computerKey(f1,"F"+(i+1),KeyEvent.KEYCODE_F1+i,0,1);
            for(int i=6;i<12;i++)computerKey(f2,"F"+(i+1),KeyEvent.KEYCODE_F1+i,0,1);
            computerKeys(row(),new String[]{"Esc","Tab","Home","End","PgUp","PgDn"},
                    new int[]{KeyEvent.KEYCODE_ESCAPE,KeyEvent.KEYCODE_TAB,KeyEvent.KEYCODE_MOVE_HOME,KeyEvent.KEYCODE_MOVE_END,KeyEvent.KEYCODE_PAGE_UP,KeyEvent.KEYCODE_PAGE_DOWN});
            computerKeys(row(),new String[]{"Ins","Del","←","↓","↑","→","Enter"},
                    new int[]{KeyEvent.KEYCODE_INSERT,KeyEvent.KEYCODE_FORWARD_DEL,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_ENTER});
        }else if(computer.page==1){
            computerCharacters("1234567890",0);computerCharacters("qwertyuiop",0);computerCharacters("asdfghjkl",0);
            LinearLayout r=row();
            for(char c:"zxcvbnm".toCharArray())computerKey(r,String.valueOf(c),ComputerKeyboard.keyCode(c),0,1);
            computerKey(r,"⌫",KeyEvent.KEYCODE_DEL,0,1);
            LinearLayout bottom=row();
            computerKey(bottom,"Esc",KeyEvent.KEYCODE_ESCAPE,0,1);
            computerKey(bottom,"Tab",KeyEvent.KEYCODE_TAB,0,1);
            computerKey(bottom,"Space",KeyEvent.KEYCODE_SPACE,0,3);
            computerKey(bottom,"Enter",KeyEvent.KEYCODE_ENTER,0,1.4f);
        }else{
            computerCharacters("`-=[]\\;',./",0);
            LinearLayout a=row(),b=row();
            String normal="1234567890", shiftedSymbols="!@#$%^&*()";
            for(int i=0;i<normal.length();i++)computerKey(a,shiftedSymbols.substring(i,i+1),ComputerKeyboard.keyCode(normal.charAt(i)),ComputerKeyboard.SHIFT,1);
            String originals="`-=[]\\;',./", shiftedPunctuation="~_+{}|:\"<>?";
            for(int i=0;i<originals.length();i++)computerKey(b,shiftedPunctuation.substring(i,i+1),ComputerKeyboard.keyCode(originals.charAt(i)),ComputerKeyboard.SHIFT,1);
            computerKeys(row(),new String[]{"Esc","Tab","Space","⌫","Enter"},
                    new int[]{KeyEvent.KEYCODE_ESCAPE,KeyEvent.KEYCODE_TAB,KeyEvent.KEYCODE_SPACE,KeyEvent.KEYCODE_DEL,KeyEvent.KEYCODE_ENTER});
        }
    }
    private void sendComputerKey(int code,int extra){
        deleteUndo=null;
        InputConnection connection=getCurrentInputConnection();
        int modifiers=computer.consume()|extra;
        if(connection==null||!computerPanel)return;
        resetWordSession();
        int held=0;
        long now=android.os.SystemClock.uptimeMillis();
        int[] bits={ComputerKeyboard.CTRL,ComputerKeyboard.ALT,ComputerKeyboard.SHIFT};
        try{
            for(int bit:bits)if((modifiers&bit)!=0){
                held|=bit;sendComputerEvent(connection,now,KeyEvent.ACTION_DOWN,ComputerKeyboard.modifierKey(bit),held);
            }
            try{sendComputerEvent(connection,now,KeyEvent.ACTION_DOWN,code,held);}
            finally{sendComputerEvent(connection,now,KeyEvent.ACTION_UP,code,held);}
        }catch(RuntimeException editorGone){/* Active editor may have closed. */}
        finally{
            for(int i=bits.length-1;i>=0;i--)if((held&bits[i])!=0){
                int bit=bits[i];held&=~bit;
                try{sendComputerEvent(connection,now,KeyEvent.ACTION_UP,ComputerKeyboard.modifierKey(bit),held);}
                catch(RuntimeException editorGone){/* Release every remaining modifier. */}
            }
            if(computerPanel&&isInputViewShown())render();
        }
    }
    private void sendComputerEvent(InputConnection connection,long down,int action,int code,int held){
        connection.sendKeyEvent(new KeyEvent(down,android.os.SystemClock.uptimeMillis(),action,code,0,
                ComputerKeyboard.meta(held),android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,0,
                KeyEvent.FLAG_SOFT_KEYBOARD|KeyEvent.FLAG_KEEP_TOUCH_MODE,android.view.InputDevice.SOURCE_KEYBOARD));
    }
    private void render() {
        wordDelete.clear();
        if (keyboard == null) return;
        clipboardPanel = loginConfirmPanel = emojiPanel = false;
        cancelHeldKey();
        keyboard.removeAllViews();shiftKey=null;
        if(voiceMode){renderVoicePanel();return;}
        if(computerPanel){renderComputer();return;}
        if(layoutPanel){KeyLayout.Language from=CorrectionEngine.detectScript(layoutOriginal);if(from!=null){renderLayoutPanel(from);return;}closeLayoutPanel();}
        updateAutoCapitalization();
        if(translationMode)renderTranslationToolbar();else{renderClipboardToolbar();refreshSuggestions();}
        if (!symbols && preferences().getBoolean("numberRow", false)) {
            LinearLayout numbers = row();
            for (String value : KeyLayout.numberRow(language))
                glyph(numbers, value, new String[0], 16);
        }
        String[][] rows = symbols ? KeyLayout.symbols(secondSymbols) : KeyLayout.letters(language);
        for (int i = 0; i < rows.length; i++) {
            LinearLayout r = row();
            float actionWidth = language == KeyLayout.Language.EN ? 1.15f : 1f;
            if (i == rows.length - 1) {
                shiftKey = key(r, symbols ? (secondSymbols ? "#1?" : "#+=") : "",
                        symbols ? "Change symbol page" : "Shift", actionWidth, shifted ? ACCENT : ACTION, 19, this::shift);
                if(!symbols) refreshShiftIcon();
            }
            for (int j = 0; j < rows[i].length; j++) {
                String value = rows[i][j];
                glyph(r, value, symbols ? KeyLayout.symbolAlternatives(value) : KeyLayout.alternatives(language, i, j, value), 21);
            }
            if (i == rows.length - 1) {
                KeyButton backspace=key(r,"\u232b","Delete; hold to repeat",actionWidth,ACTION,19,this::delete);
                attachRepeatDelete(backspace);
            }
        }
        LinearLayout bottom = row();
        KeyButton symbolKey=key(bottom,symbols?"ABC":"#1?",symbols?"Return to letters":"Toggle symbols; hold for settings",1.25f,ACTION,14,()->{
            symbols=!symbols;secondSymbols=false;shifted=capsLock=false;
            manualCase=false;lastShift=0;render();
        });
        if(!symbols){
            symbolKey.setCornerGear(true,palette.hint);
            attachHold(symbolKey,null,()->startActivity(new android.content.Intent(this,SettingsActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)),500);
        }
        boolean globeVisible=preferences().getBoolean("showGlobe",false);
        boolean computerVisible=!translationMode&&preferences().getBoolean("showComputer",false);
        if(computerVisible){
            KeyButton computerKey=key(bottom,"",globeVisible?"Computer keyboard; hold to switch language":"Computer keyboard",1.05f,ACTION,19,this::openComputer);
            computerKey.setIcon(KeyButton.Icon.COMPUTER);
            if(globeVisible){computerKey.setCornerGlobe(true,palette.hint);attachHold(computerKey,null,()->switchLanguage(1));}
        }else if(globeVisible){
            KeyButton globe=key(bottom,"", "Switch language; hold for system input methods",1.05f,ACTION,19,()->switchLanguage(1));
            globe.setGlobeIcon(true);
            attachHold(globe,null,()->{
                InputMethodManager manager=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                if(manager!=null)manager.showInputMethodPicker();
            });
        }
        punctuation(bottom, ",", "Comma", new String[]{"!", "?", ";", ":", "-", "@"});
        boolean voiceHold=localToolsEligible()&&!translationMode&&preferences().getBoolean("showVoice",true);
        KeyButton space=key(bottom,"‹ "+language.display+" ›",voiceHold?"Space; swipe for language; hold for voice":"Space; swipe to change language",
                (globeVisible||computerVisible)?4.55f:5.6f,KEY,14,()->input(" "));
        if(voiceHold){
            space.setCornerMic(true,palette.hint);
            space.setPadding(dp(15),0,dp(15),0);
            attachHold(space,null,this::openVoice);
        }
        punctuation(bottom, ".", "Period", new String[]{"?", "!", "…", ":", ";", "'"});
        KeyButton enter=key(bottom,"",enterDescription(),1.5f,ACCENT,17,this::enter);
        enter.setIcon(enterIcon());
        showInlineAutofill();
    }
    // Keep letter touch targets in place when only case changes.
    private void refreshShiftState() {
        if (keyboard == null || symbols || computerPanel) return;
        for (int i = 0; i < keyboard.getChildCount(); i++) {
            if(!(keyboard.getChildAt(i) instanceof LinearLayout))continue;
            LinearLayout row = (LinearLayout) keyboard.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); j++) {
                View v = row.getChildAt(j);
                if (v instanceof KeyButton && v.getTag() instanceof Glyph) {
                    updateGlyph((KeyButton)v, (Glyph)v.getTag());
                }
            }
        }
        refreshShiftIcon();
    }
    private void switchLanguage(int direction) {
        resetWordSession();
        language = cycle.step(direction);
        preferences().edit().putString("language", language.name()).apply();
        shifted = capsLock = false; symbols = secondSymbols = false; manualCase=false; lastShift = 0;
        render();
    }
    private void recordInteraction() {
        cycle.textEntered();
        SharedPreferences prefs = preferences();
        String last = cycle.lastPrimary().name();
        if (!last.equals(prefs.getString("lastPrimary", "")))
            prefs.edit().putString("lastPrimary", last).apply();
    }
    private void shift() {
        if (symbols) { secondSymbols = !secondSymbols; render(); return; }
        manualCase=true;
        long now = android.os.SystemClock.uptimeMillis();
        if (capsLock) { shifted = capsLock = false; }
        else if (shifted && lastShift != 0 && now - lastShift < 350) capsLock = true;
        else shifted = !shifted;
        lastShift = now;
        refreshShiftState();
        refreshSuggestions();
    }
    private boolean numericKeyEditor() {
        if(editor==null)return false;
        int type=editor.inputType & InputType.TYPE_MASK_CLASS;
        return type==InputType.TYPE_CLASS_NUMBER || type==InputType.TYPE_CLASS_PHONE
                || type==InputType.TYPE_CLASS_DATETIME || editor.inputType==InputType.TYPE_NULL;
    }
    private void input(String text) {
        if(translationMode){
            int spaces=PunctuationSpacing.spacesBefore(translationDraft,text);
            if(spaces>0)translationDraft.setLength(translationDraft.length()-spaces);
            appendTranslation(text);return;
        }
        commitInput(text,true,true);
    }
    private void commitInput(String text, boolean typed) { commitInput(text,typed,typed); }
    /** Commit typed attaching punctuation, replacing only a bounded adjacent run of ASCII spaces. */
    private boolean commitWithPunctuationSpacing(InputConnection connection,String text,boolean typed){
        if(connection==null)return false;
        if(!typed||!editorEligible()||selectionStart<1||selectionStart!=selectionEnd||!PunctuationSpacing.attaches(text))
            return connection.commitText(text,1);
        int spaces;
        try{spaces=PunctuationSpacing.spacesBefore(connection.getTextBeforeCursor(PunctuationSpacing.MAX_SPACES+1,0),text);}
        catch(RuntimeException unavailable){return connection.commitText(text,1);}
        if(spaces<=0||spaces>selectionStart)return connection.commitText(text,1);
        int caret=selectionStart;
        boolean batch=connection.beginBatchEdit(),trimmed=false;
        try{
            if(connection.deleteSurroundingText(spaces,0)){
                trimmed=true;selectionStart=selectionEnd=caret-spaces;rememberCaret(selectionEnd);
            }
            boolean committed=connection.commitText(text,1);
            if(!committed&&trimmed){
                // An editor may reject the punctuation; restore the removed spaces when possible.
                StringBuilder restore=new StringBuilder(spaces);for(int i=0;i<spaces;i++)restore.append(' ');
                if(connection.commitText(restore,1)){selectionStart=selectionEnd=caret;rememberCaret(caret);}
                else resetWordSession();
            }
            return committed;
        }finally{if(batch)connection.endBatchEdit();}
    }
    private boolean expandShortcut(String expansion,String boundary){
        if(!editorEligible()||wordSession.prefix().isEmpty())return false;
        CursorWord edited=editingWord;
        boolean replaced=edited!=null&&!edited.word().isEmpty()?replaceEditingWord(edited,expansion+boundary)
                :replaceTypedSuffix(wordSession.prefix(),expansion+boundary);
        if(replaced){resetWordSession();recordInteraction();captureCursorContext();manualCase=false;refreshSuggestions();}
        return replaced;
    }
    private void commitInput(String text, boolean typed, boolean allowAuto) {
        deleteUndo=null;
        InputConnection connection = getCurrentInputConnection();
        if(typed && connection!=null && numericKeyEditor() && text.length()==1
                && text.charAt(0)>='0' && text.charAt(0)<='9') {
            // Some segmented number editors advance from key events, not commitText.
            // Deliver one standard numeric key pair; never force focus or submit an action.
            resetWordSession(); selectionStart=selectionEnd=-1;
            recordInteraction();
            sendDownUpKeyEvents(KeyEvent.KEYCODE_0+text.charAt(0)-'0');
            return;
        }
        if (!typed || (selectionStart>=0 && selectionEnd>=0 && selectionStart!=selectionEnd))
            resetWordSession();
        if(typed){
            captureExistingWordIfNeeded();
            finishPendingCorrection();
        }
        if(typed&&allowAuto&&" ".equals(text)&&editorEligible()&&preferences().getBoolean("expandShortcuts",true)
                &&(editingWord==null||editingWord.right.isEmpty())){
            String expansion=personalEntries.expansion(wordSession.prefix());
            if(expansion!=null&&expandShortcut(expansion,automaticSuggestionBoundary()))return;
        }
        if(typed&&allowAuto&&CorrectionEngine.automaticBoundary(text)&&trackWords()
                &&(editingWord==null||editingWord.right.isEmpty())
                &&preferences().getBoolean("autocorrect",true)){
            CorrectionEngine.Candidate candidate=findCorrection(true,true);
            if(candidate!=null&&candidate.automatic&&acceptCorrection(candidate,text))return;
        }
        KeyLayout.Language wordLanguage=predictionLanguage();
        if (commitWithPunctuationSpacing(connection,text,typed)) {
            if(text.codePoints().anyMatch(Character::isLetter))manualCase=false;
            recordInteraction();
            if (selectionStart >= 0 && selectionEnd >= 0) {
                selectionStart = selectionEnd = Math.min(selectionStart,selectionEnd) + text.length();
                rememberCaret(selectionEnd);
            }
            // Editor-sourced words remain transient; only ordinary new typing is learned.
            if(typed&&editingWord!=null)captureCursorContext();
            else if (typed && trackTypedWords()) wordSession.accept(wordLanguage,text,observer());
            if(!wordSession.prefix().equals(suppressedCorrection))suppressedCorrection=null;
        }
        if (shifted && !capsLock && !symbols) { shifted = false; refreshShiftState(); }
        refreshSuggestions();
    }
    private void rememberDeletion(InputConnection connection,String removed,int caret){
        if(!editorEligible()||removed==null||removed.isEmpty()||removed.length()>WordDeleteGesture.CONTEXT_UNITS||caret<0){deleteUndo=null;return;}
        try{
            CharSequence left=connection.getTextBeforeCursor(16,0),right=connection.getTextAfterCursor(16,0);
            if(left==null||right==null){deleteUndo=null;return;}
            deleteUndo=new DeleteUndo(connection,editor,removed,caret,left.toString(),right.toString());
        }catch(RuntimeException unavailable){deleteUndo=null;}
    }
    private void swipeBackspace(int direction,InputConnection connection,boolean draft){
        if(draft){
            if(direction<0){
                WordDeleteGesture.Plan plan=WordDeleteGesture.plan(translationDraft,translationDraft.length());
                if(plan!=null){int end=translationDraft.length();draftDeleted=translationDraft.substring(end-plan.totalUnits);translationDraft.delete(end-plan.totalUnits,end);draftChanged();}
            }else if(draftDeleted!=null&&translationDraft.length()+draftDeleted.length()<=1000){
                String restored=draftDeleted;draftDeleted=null;translationDraft.append(restored);draftChanged();
            }
            return;
        }
        if(connection==null||!editorEligible()||selectionStart<0||selectionStart!=selectionEnd)return;
        if(direction>0){
            DeleteUndo undo=deleteUndo;
            if(undo==null||undo.connection!=connection||undo.editor!=editor||undo.caret!=selectionEnd)return;
            try{
                CharSequence left=connection.getTextBeforeCursor(16,0),right=connection.getTextAfterCursor(16,0);
                if(left!=null&&right!=null&&undo.before.contentEquals(left)&&undo.after.contentEquals(right)){
                    deleteUndo=null;commitInput(undo.text,false,false);captureCursorContext();refreshSuggestions();
                }
            }catch(RuntimeException unavailable){deleteUndo=null;}
            return;
        }
        finishPendingCorrection();
        CharSequence before=connection.getTextBeforeCursor(WordDeleteGesture.CONTEXT_UNITS,0);
        WordDeleteGesture.Plan plan=WordDeleteGesture.plan(before,selectionEnd);
        if(plan==null)return;
        String removed=before.subSequence(before.length()-plan.totalUnits,before.length()).toString();
        int caret=selectionEnd-plan.totalUnits;
        if(connection.deleteSurroundingText(plan.totalUnits,0)){
            resetWordSession();selectionStart=selectionEnd=caret;rememberCaret(caret);
            rememberDeletion(connection,removed,caret);recordInteraction();captureCursorContext();refreshSuggestions();
        }
    }
    private void delete() { wordDelete.clear();deleteSingle(false); }
    private void tapDelete() {
        wordDelete.clear();
        if(!preferences().getBoolean("doubleDeleteWord",true)){deleteSingle(false);return;}
        long now=android.os.SystemClock.uptimeMillis();
        if(translationMode){
            int caret=translationDraft.length();WordDeleteGesture.Plan plan=WordDeleteGesture.plan(translationDraft,caret);
            deleteTranslation();
            if(plan!=null)wordDelete.arm(now,caret-plan.firstUnits,plan.remaining(),true);
            return;
        }
        deleteSingle(true);
    }
    /** Only called after the second key has actually been held for HOLD_MS. */
    private boolean tryDeleteWord(long secondDown) {
        if(!preferences().getBoolean("doubleDeleteWord",true))return false;
        if(translationMode){
            int caret=translationDraft.length(),remaining=wordDelete.consume(secondDown,caret,true);
            if(remaining<0)return false;
            if(remaining>caret)return false;
            if(remaining>0){draftDeleted=translationDraft.substring(caret-remaining,caret);translationDraft.delete(caret-remaining,caret);draftChanged();}
            return true;
        }
        InputConnection connection=getCurrentInputConnection();
        if(connection==null||!editorEligible()||undoCorrection!=null||selectionStart<0||selectionStart!=selectionEnd){
            wordDelete.clear();return false;
        }
        int caret=selectionStart,remaining=wordDelete.consume(secondDown,caret,false);
        if(remaining<0)return false;
        if(remaining==0)return true;
        CharSequence before=connection.getTextBeforeCursor(WordDeleteGesture.CONTEXT_UNITS,0);
        WordDeleteGesture.Plan current=WordDeleteGesture.plan(before,caret);
        if(current!=null&&current.totalUnits==remaining&&connection.deleteSurroundingText(remaining,0)){
            String removed=before.subSequence(before.length()-remaining,before.length()).toString();
            resetWordSession();recordInteraction();selectionStart=selectionEnd=caret-remaining;rememberCaret(selectionEnd);rememberDeletion(connection,removed,selectionEnd);refreshSuggestions();return true;
        }
        return false;
    }
    private void deleteSingle(boolean armWord) {
        if(translationMode){deleteTranslation();return;}
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        if(numericKeyEditor()) {
            resetWordSession(); selectionStart=selectionEnd=-1;
            recordInteraction(); sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return;
        }
        if(undoLastCorrection()){deleteUndo=null;return;}
        deleteUndo=null;
        suppressedCorrection=null;
        recordInteraction();
        if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
            String removed=null;
            if(editorEligible()&&Math.abs(selectionStart-selectionEnd)<=WordDeleteGesture.CONTEXT_UNITS){
                CharSequence selected=connection.getSelectedText(0);if(selected!=null)removed=selected.toString();
            }
            resetWordSession();
            if (connection.commitText("",1)) {
                selectionStart = selectionEnd = Math.min(selectionStart,selectionEnd);
                rememberDeletion(connection,removed,selectionEnd);
                rememberCaret(selectionEnd);
                refreshSuggestions();
                return;
            }
        } else if (selectionStart >= 0 && selectionEnd >= 0) {
            int caret=selectionStart;
            WordDeleteGesture.Plan plan=armWord&&editorEligible()
                    ? WordDeleteGesture.plan(connection.getTextBeforeCursor(WordDeleteGesture.CONTEXT_UNITS,0),caret):null;
            String removed=null;
            if(editorEligible()){
                CharSequence prior=connection.getTextBeforeCursor(2,0);
                if(prior!=null&&prior.length()>0)removed=prior.subSequence(prior.length()-Character.charCount(Character.codePointBefore(prior,prior.length())),prior.length()).toString();
            }
            if(connection.deleteSurroundingTextInCodePoints(1,0)){
            if(removed!=null)rememberDeletion(connection,removed,Math.max(0,caret-removed.length()));
            String prefix=wordSession.prefix();
            if(editingWord!=null){
                selectionStart=selectionEnd=Math.max(0,caret-editingWord.previousUnits);
                rememberCaret(selectionEnd);captureCursorContext();
            }else if (trackTypedWords() && !prefix.isEmpty()) {
                int units=Character.charCount(prefix.codePointBefore(prefix.length()));
                wordSession.backspace();
                selectionStart=selectionEnd=Math.max(0,selectionStart-units);
                rememberCaret(selectionEnd);
            } else {
                resetWordSession();
                if(removed!=null){selectionStart=selectionEnd=Math.max(0,caret-removed.length());rememberCaret(selectionEnd);}
                else selectionStart=selectionEnd=-1;
            }
            if(plan!=null){
                selectionStart=selectionEnd=caret-plan.firstUnits;
                rememberCaret(selectionEnd);
                wordDelete.arm(android.os.SystemClock.uptimeMillis(),selectionEnd,plan.remaining(),false);
            }
            refreshSuggestions();
            return;
            }
        }
        resetWordSession();
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL));
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DEL));
        refreshSuggestions();
    }
    private void refreshShiftIcon() {
        if(shiftKey==null || symbols) return;
        styleKey(shiftKey, shifted ? ACCENT : ACTION);
        shiftKey.setIcon(capsLock ? KeyButton.Icon.CAPS : shifted ? KeyButton.Icon.SHIFT_ON : KeyButton.Icon.SHIFT);
        shiftKey.setContentDescription(capsLock ? "Caps lock on" : shifted ? "Shift on" : "Shift");
    }
    private KeyButton.Icon enterIcon() {
        if(translationMode||editor==null || (editor.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0) return KeyButton.Icon.RETURN;
        switch (editor.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_SEARCH: return KeyButton.Icon.SEARCH;
            case EditorInfo.IME_ACTION_SEND: return KeyButton.Icon.SEND;
            case EditorInfo.IME_ACTION_DONE: return KeyButton.Icon.DONE;
            case EditorInfo.IME_ACTION_NEXT: case EditorInfo.IME_ACTION_GO: return KeyButton.Icon.NEXT;
            default: return KeyButton.Icon.RETURN;
        }
    }
    private String enterDescription() {
        if(editor!=null && editor.actionLabel!=null) return editor.actionLabel.toString();
        switch(enterIcon()) {
            case SEARCH: return "Search"; case SEND: return "Send";
            case DONE: return "Done"; case NEXT: return "Next or go";
            default: return "Enter";
        }
    }
    private void enter() {
        deleteUndo=null;
        if(translationMode){appendTranslation("\n");return;}
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        finishPendingCorrection();
        if (trackWords()&&editingWord==null) wordSession.accept(predictionLanguage(),"\n",observer());
        resetWordSession();
        refreshSuggestions();
        recordInteraction();
        if (editor != null && (editor.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            if (editor.actionLabel != null && editor.actionId != 0 &&
                    connection.performEditorAction(editor.actionId)) return;
            if (sendDefaultEditorAction(true)) return;
        }
        if (editor != null && (editor.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
                && (editor.inputType & (InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE)) != 0
                && connection.commitText("\n",1)) {
            selectionStart = selectionEnd = -1;
            manualCase=false;updateAutoCapitalization();
            return;
        }
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));
    }
}
