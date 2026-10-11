package dev.lungetest;

/** Shared, readable gradient palettes for the menu, settings and HUD. */
public final class ThemeManager {
    public enum Theme {
        AURORA("Aurora", 0xFF9E8CFF, 0xFF62DDD4),
        OCEAN("Ocean", 0xFF54B8FF, 0xFF82E5D4),
        SUNSET("Sunset", 0xFFFF9B74, 0xFFD689E8),
        ROSE("Rose", 0xFFFF86B2, 0xFFB09DFF),
        FOREST("Forest", 0xFF88DBA6, 0xFFC7D78A),
        MONO("Graphite", 0xFFCFD8E8, 0xFF8999B5),
        CUSTOM("Custom", 0xFF9E8CFF, 0xFF62DDD4);
        public final String label;
        public final int first, second;
        Theme(String label, int first, int second) { this.label = label; this.first = first; this.second = second; }
    }
    public static final class Preferences {
        public Theme theme = Theme.AURORA;
        public int first = 0xFF9E8CFF, second = 0xFF62DDD4;
    }
    private static Preferences preferences = new Preferences();
    private ThemeManager() {}
    public static void initialize(Preferences value) {
        preferences = value == null ? new Preferences() : value;
        if (preferences.theme == null) preferences.theme = Theme.AURORA;
        preferences.first |= 0xFF000000;
        preferences.second |= 0xFF000000;
    }
    public static Preferences snapshot() { return preferences; }
    public static Theme theme() { return preferences.theme; }
    public static void select(Theme theme) {
        preferences.theme = theme == null ? Theme.AURORA : theme;
        LungeTestClient.saveThemePreferences();
    }
    public static void custom(int first, int second) {
        preferences.first = first | 0xFF000000;
        preferences.second = second | 0xFF000000;
        select(Theme.CUSTOM);
    }
    public static int first() { return theme() == Theme.CUSTOM ? preferences.first : theme().first; }
    public static int second() { return theme() == Theme.CUSTOM ? preferences.second : theme().second; }
    public static int accent() { return mix(first(), 0xFFFFFFFF, .15f); }
    public static int secondary() { return mix(second(), 0xFFFFFFFF, .15f); }
    public static int text() { return 0xFFF2F5FC; }
    public static int muted() { return 0xFFB5C0D2; }
    public static int top() { return mix(0xFF101624, first(), .23f); }
    public static int bottom() { return mix(0xFF070C16, second(), .12f); }
    public static int alpha(int color, int alpha) { return (color & 0xFFFFFF) | (Math.clamp(alpha, 0, 255) << 24); }
    public static int mix(int a, int b, float amount) {
        int result = 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8)
            result |= Math.round((a >>> shift & 255) * (1 - amount) + (b >>> shift & 255) * amount) << shift;
        return result;
    }
}
