package com.ajadi.keys;

import android.view.KeyEvent;

/** One-shot local modifier state. No held native event survives a key dispatch. */
final class ComputerKeyboard {
    static final int CTRL=1,ALT=2,SHIFT=4;
    private int modifiers;
    int page;
    void toggle(int flag){modifiers^=flag;}
    boolean armed(int flag){return (modifiers&flag)!=0;}
    int consume(){int result=modifiers;modifiers=0;return result;}
    void reset(){modifiers=0;page=0;}
    static boolean wideLayout(int availableWidthDp,int availableHeightDp){return availableWidthDp>=600&&availableHeightDp>=480;}
    static int meta(int flags){
        return ((flags&CTRL)!=0?KeyEvent.META_CTRL_ON|KeyEvent.META_CTRL_LEFT_ON:0)
                |((flags&ALT)!=0?KeyEvent.META_ALT_ON|KeyEvent.META_ALT_LEFT_ON:0)
                |((flags&SHIFT)!=0?KeyEvent.META_SHIFT_ON|KeyEvent.META_SHIFT_LEFT_ON:0);
    }
    static int modifierKey(int flag){
        return flag==CTRL?KeyEvent.KEYCODE_CTRL_LEFT:flag==ALT?KeyEvent.KEYCODE_ALT_LEFT:KeyEvent.KEYCODE_SHIFT_LEFT;
    }
    static int keyCode(char c){
        if(c>='a'&&c<='z')return KeyEvent.KEYCODE_A+c-'a';
        if(c>='0'&&c<='9')return KeyEvent.KEYCODE_0+c-'0';
        switch(c){
            case '-':return KeyEvent.KEYCODE_MINUS;
            case '=':return KeyEvent.KEYCODE_EQUALS;
            case '[':return KeyEvent.KEYCODE_LEFT_BRACKET;
            case ']':return KeyEvent.KEYCODE_RIGHT_BRACKET;
            case '\\':return KeyEvent.KEYCODE_BACKSLASH;
            case ';':return KeyEvent.KEYCODE_SEMICOLON;
            case '\'':return KeyEvent.KEYCODE_APOSTROPHE;
            case ',':return KeyEvent.KEYCODE_COMMA;
            case '.':return KeyEvent.KEYCODE_PERIOD;
            case '/':return KeyEvent.KEYCODE_SLASH;
            case '`':return KeyEvent.KEYCODE_GRAVE;
            default:throw new IllegalArgumentException("Unsupported keyboard key");
        }
    }
}
