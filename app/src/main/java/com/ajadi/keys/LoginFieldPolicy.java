package com.ajadi.keys;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/** Only declared username/email editors; never passwords or ordinary message fields. */
final class LoginFieldPolicy {
    static final int MAX_LENGTH=254;
    private static final Set<String> LABELS=new HashSet<>(Arrays.asList(
            "username","user name","user_name","login","login name","login_name","email","e-mail",
            "email address","email_address","user_email","account email",
            "\u043b\u043e\u0433\u0438\u043d","\u0438\u043c\u044f \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044f",
            "\u044d\u043b\u0435\u043a\u0442\u0440\u043e\u043d\u043d\u0430\u044f \u043f\u043e\u0447\u0442\u0430","\u05e9\u05dd \u05de\u05e9\u05ea\u05de\u05e9","\u05d3\u05d5\u05d0\u05e8 \u05d0\u05dc\u05e7\u05d8\u05e8\u05d5\u05e0\u05d9"));
    private static boolean label(CharSequence text) {
        if(text==null)return false;
        return LABELS.contains(text.toString().trim().toLowerCase(Locale.ROOT));
    }
    static boolean password(int type) {
        int cls=type & InputType.TYPE_MASK_CLASS,var=type & InputType.TYPE_MASK_VARIATION;
        return cls==InputType.TYPE_CLASS_TEXT && (var==InputType.TYPE_TEXT_VARIATION_PASSWORD
                ||var==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||var==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                ||cls==InputType.TYPE_CLASS_NUMBER&&var==InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }
    static boolean declaredLogin(int type,CharSequence hint,String fieldName) {
        if((type & InputType.TYPE_MASK_CLASS)!=InputType.TYPE_CLASS_TEXT || password(type)
                ||(type & (InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE))!=0)return false;
        int var=type & InputType.TYPE_MASK_VARIATION;
        if(var==InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS||var==InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)return true;
        return (var==InputType.TYPE_TEXT_VARIATION_NORMAL||var==InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT)
                &&(label(hint)||label(fieldName));
    }
    static boolean allowed(int type,int imeOptions,CharSequence hint,String fieldName) {
        return (imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)==0 && declaredLogin(type,hint,fieldName);
    }
    static boolean validLogin(String value) {
        if(value==null||value.isEmpty()||value.length()>MAX_LENGTH)return false;
        boolean useful=false;
        for(int i=0;i<value.length();) {
            int cp=value.codePointAt(i);i+=Character.charCount(cp);
            if(Character.isWhitespace(cp)||Character.isISOControl(cp)||Character.getType(cp)==Character.FORMAT)return false;
            if(Character.isLetterOrDigit(cp))useful=true;
        }
        return useful;
    }
}
