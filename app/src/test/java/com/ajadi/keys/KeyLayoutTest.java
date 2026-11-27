package com.ajadi.keys;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import static org.junit.Assert.*;

public class KeyLayoutTest {
    private Set<String> accessibleLetters(KeyLayout.Language lang) {
        Set<String> all = new HashSet<>();
        String[][] rows = KeyLayout.letters(lang);
        for(int i=0; i<rows.length; i++) {
            for(int j=0; j<rows[i].length; j++) {
                String key = rows[i][j];
                assertTrue("duplicate primary key "+key,all.add(key));
                for (String alt: KeyLayout.alternatives(lang,i,j,key)) {
                    if (Character.isLetter(alt.codePointAt(0))) all.add(alt);
                }
            }
        }
        return all;
    }
    private Set<String> expected(String letters) {
        return new HashSet<>(Arrays.asList(letters.split(" ")));
    }
    @Test public void englishHasEveryLetter() {
        assertEquals(expected("a b c d e f g h i j k l m n o p q r s t u v w x y z"),accessibleLetters(KeyLayout.Language.EN));
    }
    @Test public void russianHasEveryLetterAndYo() {
        assertEquals(expected("а б в г д е ё ж з и й к л м н о п р с т у ф х ц ч ш щ ъ ы ь э ю я"),
                accessibleLetters(KeyLayout.Language.RU));
        assertArrayEquals(KeyLayout.split("й ц у к е н г ш щ з х"),KeyLayout.letters(KeyLayout.Language.RU)[0]);
        assertArrayEquals(KeyLayout.split("ф ы в а п р о л д ж э"),KeyLayout.letters(KeyLayout.Language.RU)[1]);
        assertArrayEquals(KeyLayout.split("я ч с м и т ь б ю"),KeyLayout.letters(KeyLayout.Language.RU)[2]);
        assertArrayEquals(new String[]{"5","ё"},KeyLayout.alternatives(KeyLayout.Language.RU,0,4,"е"));
        assertArrayEquals(new String[]{"ъ","?"},KeyLayout.alternatives(KeyLayout.Language.RU,2,6,"ь"));
        assertEquals("ё",KeyLayout.numberRow(KeyLayout.Language.RU)[10]);
    }
    @Test public void hebrewIncludesFinalForms() {
        Set<String> keys=accessibleLetters(KeyLayout.Language.HE);
        assertEquals(expected("א ב ג ד ה ו ז ח ט י כ ל מ נ ס ע פ צ ק ר ש ת ך ם ן ף ץ"),keys);
        for(String key : KeyLayout.split("ך ם ן ף ץ")) assertTrue(keys.contains(key));
    }
    @Test public void allDigitsAccessibleByHoldInEachLanguage() {
        for (KeyLayout.Language lang: KeyLayout.Language.values()) {
            Set<String> digits = new HashSet<>();
            String[][] rows=KeyLayout.letters(lang);
            for (int i=0;i<rows.length;i++) for (int j=0;j<rows[i].length;j++)
                for (String alt: KeyLayout.alternatives(lang,i,j,rows[i][j]))
                    if (Character.isDigit(alt.codePointAt(0))) digits.add(alt);
            assertEquals(expected("0 1 2 3 4 5 6 7 8 9"),digits);
        }
    }
    @Test public void localeShift() {
        assertEquals("Ё",KeyLayout.shifted("ё",KeyLayout.Language.RU,true));
        assertEquals("Ъ",KeyLayout.shifted("ъ",KeyLayout.Language.RU,true));
        assertEquals("א",KeyLayout.shifted("א",KeyLayout.Language.HE,true));
    }
    @Test public void bothSymbolPagesKeepAllDigitsAndUsefulAlternatives() {
        String[] digits=KeyLayout.split("1 2 3 4 5 6 7 8 9 0");
        assertArrayEquals(digits,KeyLayout.symbols(false)[0]);
        assertArrayEquals(digits,KeyLayout.symbols(true)[0]);
        Set<String> reachable=new HashSet<>();
        for (boolean secondary:new boolean[]{false,true})
            for (String[] row:KeyLayout.symbols(secondary)) for (String glyph:row) {
                reachable.add(glyph);
                reachable.addAll(Arrays.asList(KeyLayout.symbolAlternatives(glyph)));
            }
        for (String required:KeyLayout.split("₽ $ € ₪ £ ¥ # № % & @ ! ? : ; + - × ÷ √ ^ = ≠ < > ≤ ≥ ( ) { } [ ]"))
            assertTrue("missing symbol "+required,reachable.contains(required));
    }
}
