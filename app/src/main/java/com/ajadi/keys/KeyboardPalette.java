package com.ajadi.keys;

/** Local, visually distinct keyboard color presets. */
final class KeyboardPalette {
    final int background, key, action, accent;
    final int text, accentText, hint, pressed, actionPressed, accentPressed;

    private KeyboardPalette(int background, int key, int action, int accent,
                            int text, int accentText, int hint, int pressed,
                            int actionPressed, int accentPressed) {
        this.background = background;
        this.key = key;
        this.action = action;
        this.accent = accent;
        this.text = text;
        this.accentText = accentText;
        this.hint = hint;
        this.pressed = pressed;
        this.actionPressed = actionPressed;
        this.accentPressed = accentPressed;
    }

    static final KeyboardPalette LIGHT = new KeyboardPalette(
            0xffe1e8e7, 0xfffcfdf9, 0xffcbdcd9, 0xff147e80,
            0xff192d30, 0xfffcfdf9, 0xff526c6c, 0xffd7e8e5,
            0xffadccc8, 0xff0e6669);
    static final KeyboardPalette DARK = new KeyboardPalette(
            0xff111923, 0xff293747, 0xff3c4d61, 0xff78dbaf,
            0xfff0f4fa, 0xff111923, 0xffb8cbd8, 0xff465c71,
            0xff576b80, 0xffadf4d4);
    static double luminance(int color){
        double r=linear((color>>16)&255),g=linear((color>>8)&255),b=linear(color&255);
        return .2126*r+.7152*g+.0722*b;
    }
    private static double linear(int channel){
        double value=channel/255d;
        return value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4);
    }
    static double contrast(int a,int b){
        double x=luminance(a),y=luminance(b);
        return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);
    }
    private static int readable(int preferred,int... backgrounds){
        boolean good=true;for(int bg:backgrounds)good&=contrast(preferred,bg)>=4.5;
        if(good)return preferred;
        int black=0xff000000,white=0xffffffff;
        double a=21,b=21;
        for(int bg:backgrounds){a=Math.min(a,contrast(black,bg));b=Math.min(b,contrast(white,bg));}
        return a>=b?black:white;
    }
    /** Android-provided tonal colors; use existing palette if system tones cannot provide readable labels. */
    static KeyboardPalette adaptive(boolean dark,int background,int key,int action,int accent,int text,int accentText,int pressed,int actionPressed,int accentPressed){
        int ink=readable(text,key,action,pressed,actionPressed);
        int accentInk=readable(accentText,accent,accentPressed);
        int hint=readable(ink,key,action);
        if(contrast(ink,key)<4.5||contrast(ink,action)<4.5||contrast(ink,pressed)<4.5
                ||contrast(ink,actionPressed)<4.5||contrast(accentInk,accent)<4.5||contrast(accentInk,accentPressed)<4.5)
            return dark?DARK:LIGHT;
        return new KeyboardPalette(background,key,action,accent,ink,accentInk,hint,pressed,actionPressed,accentPressed);
    }
    static KeyboardPalette custom(int background,int key,int text,int accent){
        int action=mix(key,background,.20);
        int ink=readable(text,key,action);
        int accentInk=readable(text,accent);
        int pressed=mix(key,ink,.08),actionPressed=mix(action,ink,.08),accentPressed=mix(accent,accentInk,.08);
        if(contrast(ink,pressed)<4.5)pressed=key;
        if(contrast(ink,actionPressed)<4.5)actionPressed=action;
        if(contrast(accentInk,accentPressed)<4.5)accentPressed=accent;
        if(contrast(ink,action)<4.5)action=actionPressed=key;
        return new KeyboardPalette(background,key,action,accent,ink,accentInk,ink,pressed,actionPressed,accentPressed);
    }
    private static int mix(int a,int b,double weight){
        int c=0xff000000;
        for(int shift:new int[]{0,8,16})c|=((int)Math.round(((a>>shift)&255)*(1-weight)+((b>>shift)&255)*weight))<<shift;
        return c;
    }
    static boolean validColor(String color){return color!=null&&color.matches("#[0-9a-fA-F]{6}");}
    static int color(String value,int fallback){return validColor(value)?(int)(0xff000000L|Long.parseLong(value.substring(1),16)):fallback;}
    boolean lightBackground(){return luminance(background)>.45;}
    static KeyboardPalette resolve(String choice, boolean darkSystem) {
        if ("dark".equals(choice) || (("system".equals(choice)||"adaptive".equals(choice)||"transparent".equals(choice)) && darkSystem)) return DARK;
        return LIGHT;
    }
}
