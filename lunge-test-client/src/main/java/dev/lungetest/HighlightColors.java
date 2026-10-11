package dev.lungetest;
import java.util.Locale;
import java.util.OptionalInt;

/** Strict RGB input: an optional # and exactly six hexadecimal digits. */
public final class HighlightColors {
    private HighlightColors() { }
    public static OptionalInt parse(String input) {
        if (input == null) return OptionalInt.empty();
        String hex = input.strip();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("[0-9a-fA-F]{6}")) return OptionalInt.empty();
        return OptionalInt.of(0xFF000000 | Integer.parseInt(hex, 16));
    }
    public static String format(int argb) { return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF); }
}
