package com.ajadi.keys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Layout data and alternatives, independent of Android framework. */
final class KeyLayout {
    enum Language {
        RU("Русский", Locale.forLanguageTag("ru")),
        EN("English", Locale.ENGLISH),
        HE("עברית", Locale.forLanguageTag("he"));

        final String display;
        final Locale locale;
        Language(String display, Locale locale) { this.display = display; this.locale = locale; }
        Language next() { return values()[(ordinal() + 1) % values().length]; }
    }

    static String[][] letters(Language language) {
        switch (language) {
            case RU: return new String[][] {
                    split("й ц у к е н г ш щ з х"), split("ф ы в а п р о л д ж э"),
                    split("я ч с м и т ь б ю") };
            case HE: return new String[][] {
                    split("ק ר א ט ו ן ם פ"), split("ש ד ג כ ע י ח ל ך ף"),
                    split("ז ס ב ה נ מ צ ת ץ") };
            default: return new String[][] {
                    split("q w e r t y u i o p"), split("a s d f g h j k l"),
                    split("z x c v b n m") };
        }
    }

    static String[] numberRow(Language language) {
        return language == Language.RU
                ? split("1 2 3 4 5 6 7 8 9 0 ё")
                : split("1 2 3 4 5 6 7 8 9 0");
    }

    /** First alternative is the default on long-press release. */
    static String[] alternatives(Language language, int row, int column, String glyph) {
        List<String> result = new ArrayList<>();
        if (row == 0 && column < 10) {
            result.add(String.valueOf("1234567890".charAt(column)));
        } else if (language == Language.HE && row == 1 && column < 2) {
            // Hebrew has eight primary letters in its first row.
            result.add(column == 0 ? "9" : "0");
        }
        if (language == Language.RU) {
            if ("е".equals(glyph)) result.add("ё");
            if ("ь".equals(glyph)) { result.add("ъ"); result.add("?"); }
        }
        return result.toArray(new String[0]);
    }

    static String[][] symbols(boolean second) {
        return second ? new String[][] {
                split("1 2 3 4 5 6 7 8 9 0"),
                split("× ÷ ^ √ Δ − ± < > ="),
                split("α ° • | { } [ ]") } : new String[][] {
                split("1 2 3 4 5 6 7 8 9 0"),
                split("& # ₽ % _ - + ( ) /"),
                split("@ * \" ' : ; ! ?") };
    }

    static String[] symbolAlternatives(String glyph) {
        switch (glyph) {
            case "&": return split("§");
            case "#": return split("№");
            case "₽": return split("$ € ₪ £ ¥ ¢");
            case "%": return split("‰");
            case "_": return split("¯");
            case "-": case "−": return split("– —");
            case "+": return split("±");
            case "(": return split("< [ {");
            case ")": return split("> ] }");
            case "/": return split("\\");
            case "@": return split("~");
            case "*": return split("… ×");
            case "\"": return split("“ ” « »");
            case "'": return split("‘ ’ `");
            case ":": return split("{");
            case ";": return split("}");
            case "!": return split("¡");
            case "?": return split("¿");
            case "α": return split("π β Ω");
            case "°": return split("ø");
            case "•": return split("© ® ™ ¶");
            case "<": return split("≤");
            case ">": return split("≥");
            case "=": return split("≠ ≈");
            case "|": return split("\\ ¦");
            default: return new String[0];
        }
    }

    static String[] split(String line) { return line.split(" "); }
    static String shifted(String letter, Language language, boolean shift) {
        return shift ? letter.toUpperCase(language.locale) : letter;
    }

    static Language parse(String name) {
        try { return Language.valueOf(name); }
        catch (IllegalArgumentException | NullPointerException e) { return Language.RU; }
    }
}
