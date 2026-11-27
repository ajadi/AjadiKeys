package com.ajadi.keys;
import org.junit.Test;
import android.view.KeyEvent;
import static org.junit.Assert.*;
public class ComputerKeyboardTest {
    @Test public void oneShotAndReset(){
        ComputerKeyboard state=new ComputerKeyboard();
        state.toggle(ComputerKeyboard.CTRL);state.toggle(ComputerKeyboard.ALT);
        assertTrue(state.armed(ComputerKeyboard.CTRL));assertEquals(3,state.consume());
        assertEquals(0,state.consume());state.toggle(ComputerKeyboard.SHIFT);state.page=2;
        state.reset();assertEquals(0,state.consume());assertEquals(0,state.page);
    }
    @Test public void nativeKeyCodes(){
        assertEquals(KeyEvent.KEYCODE_C,ComputerKeyboard.keyCode('c'));
        assertEquals(KeyEvent.KEYCODE_7,ComputerKeyboard.keyCode('7'));
        assertEquals(KeyEvent.KEYCODE_SLASH,ComputerKeyboard.keyCode('/'));
        assertEquals(KeyEvent.META_CTRL_ON|KeyEvent.META_CTRL_LEFT_ON|KeyEvent.META_SHIFT_ON|KeyEvent.META_SHIFT_LEFT_ON,
                ComputerKeyboard.meta(ComputerKeyboard.CTRL|ComputerKeyboard.SHIFT));
    }
    @Test public void wideComputerUsesAvailableSizeAndNotOrientation(){
        assertFalse(ComputerKeyboard.wideLayout(411,850));
        assertFalse(ComputerKeyboard.wideLayout(900,410));
        assertTrue(ComputerKeyboard.wideLayout(600,800));
        assertTrue(ComputerKeyboard.wideLayout(840,480));
        assertFalse(ComputerKeyboard.wideLayout(599,840));
    }
}
