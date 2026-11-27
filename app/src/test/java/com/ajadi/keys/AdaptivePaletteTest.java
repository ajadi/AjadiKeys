package com.ajadi.keys;
import org.junit.Test;
import static org.junit.Assert.*;
public class AdaptivePaletteTest {
    @Test public void fallbackAndContrast(){
        assertSame(KeyboardPalette.DARK,KeyboardPalette.resolve("adaptive",true));
        assertSame(KeyboardPalette.LIGHT,KeyboardPalette.resolve("adaptive",false));
        KeyboardPalette p=KeyboardPalette.adaptive(false,0xffeeeaff,0xffffffff,0xffebe0ff,0xff64419e,
                0xffdddddd,0xffeeeeee,0xffefeaff,0xffe1cfff,0xff53358b);
        assertTrue(p.lightBackground());
        assertTrue(KeyboardPalette.contrast(p.text,p.key)>=4.5);
        assertTrue(KeyboardPalette.contrast(p.text,p.actionPressed)>=4.5);
        assertTrue(KeyboardPalette.contrast(p.accentText,p.accent)>=4.5);
        assertTrue(KeyboardPalette.contrast(p.accentText,p.accentPressed)>=4.5);
        assertTrue(KeyboardPalette.contrast(p.hint,p.key)>=4.5);
    }
    @Test public void customPaletteKeepsReadableLabels(){
        KeyboardPalette p=KeyboardPalette.custom(0xff222222,0xff000000,0xff000000,0xffffffff);
        assertTrue(KeyboardPalette.contrast(p.text,p.key)>=4.5);
        assertTrue(KeyboardPalette.contrast(p.accentText,p.accent)>=4.5);
        assertTrue(KeyboardPalette.validColor("#aBc123"));assertFalse(KeyboardPalette.validColor("#ffffff00"));
    }
}
