package com.ajadi.keys;
import org.junit.Test;
import static org.junit.Assert.*;
public class LocalBackupTest {
    @Test public void allowlistRejectsUnknownSecretsAndOutOfRangeValues(){
        assertTrue(LocalBackup.validSetting("theme","custom"));
        assertTrue(LocalBackup.validSetting("customAccent","#AABBCC"));
        assertTrue(LocalBackup.validSetting("customOpacity",42));
        assertFalse(LocalBackup.validSetting("customOpacity",101));
        assertFalse(LocalBackup.validSetting("apiKey","secret"));
        assertFalse(LocalBackup.validSetting("saved_logins","private"));
        assertFalse(LocalBackup.validSetting("clipboard","private"));
    }
    @Test public void explicitEntriesAreBounded(){
        assertTrue(PersonalEntries.valid("words","пример","пример"));
        assertTrue(PersonalEntries.valid("shortcuts","brb","be right back"));
        assertFalse(PersonalEntries.valid("words","a","different"));
        assertFalse(PersonalEntries.valid("shortcuts","123","text"));
    }
    @Test public void contextualEmojiUsesSpecificWordsOnly(){
        assertNotNull(EmojiCatalog.context("thank"));
        assertNull(EmojiCatalog.context(""));
        assertNull(EmojiCatalog.context("the"));
    }
}
