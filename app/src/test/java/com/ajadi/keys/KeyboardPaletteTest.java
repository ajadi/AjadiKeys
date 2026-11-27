package com.ajadi.keys;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardPaletteTest {
    @Test public void explicitThemesOverrideSystem() {
        assertSame(KeyboardPalette.LIGHT,KeyboardPalette.resolve("light",true));
        assertSame(KeyboardPalette.DARK,KeyboardPalette.resolve("dark",false));
    }
    @Test public void systemFollowsNightMode() {
        assertSame(KeyboardPalette.LIGHT,KeyboardPalette.resolve("system",false));
        assertSame(KeyboardPalette.DARK,KeyboardPalette.resolve("system",true));
    }
}
