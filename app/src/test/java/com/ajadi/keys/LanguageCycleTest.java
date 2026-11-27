package com.ajadi.keys;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.ajadi.keys.KeyLayout.Language.*;

public class LanguageCycleTest {
    @Test public void typingTogglesPrimaryPair() {
        LanguageCycle c = new LanguageCycle(RU,RU,EN,RU);
        c.textEntered();
        assertEquals(EN,c.step(1));
        c.textEntered();
        assertEquals(RU,c.step(-1));
        c.textEntered();
        assertEquals(EN,c.step(-1));
    }
    @Test public void consecutiveSwitchesReachSecondary() {
        LanguageCycle c=new LanguageCycle(RU,RU,EN,RU);
        assertEquals(EN,c.step(1));
        assertEquals(HE,c.step(1));
        assertEquals(RU,c.step(1));
    }
    @Test public void reverseDirectionReversesTraversal() {
        LanguageCycle c=new LanguageCycle(RU,RU,EN,RU);
        assertEquals(EN,c.step(-1));
        assertEquals(RU,c.step(1));
        assertEquals(HE,c.step(1));
        assertEquals(RU,c.step(-1));
    }
    @Test public void secondaryReturnsToLastTypedPrimary() {
        LanguageCycle c=new LanguageCycle(RU,RU,EN,RU);
        c.textEntered();
        assertEquals(EN,c.step(1)); // selected, not typed
        assertEquals(HE,c.step(1));
        c.textEntered();
        assertEquals(RU,c.step(1));
        c.textEntered();
        assertEquals(EN,c.step(1));
        c.textEntered();
        assertEquals(RU,c.step(1));
        assertEquals(HE,c.step(1));
        c.textEntered();
        assertEquals(EN,c.step(-1));
    }
    @Test public void differentPrimaryPairAndInvalidPair() {
        LanguageCycle c=new LanguageCycle(RU,RU,HE,RU);
        assertEquals(HE,c.step(1));
        c.textEntered();
        assertEquals(RU,c.step(1));
        assertEquals(EN,c.step(1));
        c.configure(EN,HE);
        c.textEntered();
        assertEquals(HE,c.step(-1));
        c.configure(HE,HE);
        c.textEntered();
        assertEquals(EN,c.step(-1));
    }
    @Test public void sessionResetDoesNotForceSecondary() {
        LanguageCycle c=new LanguageCycle(RU,RU,EN,RU);
        assertEquals(EN,c.step(1));
        c.reset();
        assertEquals(RU,c.step(1));
    }
}
