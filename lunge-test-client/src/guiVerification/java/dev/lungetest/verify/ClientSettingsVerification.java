package dev.lungetest.verify;

import dev.lungetest.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import java.util.HashSet;

/** Real settings widgets, persistence model, key capture and minimum-size geometry. */
public final class ClientSettingsVerification {
    private static int checks;
    public static void verify(Minecraft client) {
        var parent = client.screen;
        var saved = new com.google.gson.Gson().toJson(ThemeManager.snapshot());
        int originalBind = AzeiumKeybinds.menuKey();
        int originalCps = ShieldDrainerModule.preferences().clicksPerSecond;
        try {
            var palettes = new HashSet<Integer>();
            for (var theme : ThemeManager.Theme.values()) {
                if (theme == ThemeManager.Theme.CUSTOM) continue;
                ThemeManager.select(theme);
                palettes.add(ThemeManager.first());
                require(ThemeManager.first() != ThemeManager.second(), "Preset has distinct gradient endpoints");
            }
            require(palettes.size() == 6, "Six distinct presets");
            ThemeManager.custom(0x123456, 0xABCDEF);
            var restored = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(ThemeManager.snapshot()), ThemeManager.Preferences.class);
            ThemeManager.initialize(restored);
            require(ThemeManager.theme() == ThemeManager.Theme.CUSTOM && ThemeManager.first() == 0xFF123456
                    && ThemeManager.second() == 0xFFABCDEF, "Custom palette survives serialization with opaque colours");
            for (var page : ClientSettingsScreen.Page.values()) {
                var screen = new ClientSettingsScreen(parent, page);
                client.setScreen(screen);
                screen.init(320, 240);
                checkWidgets(screen);
                if (page == ClientSettingsScreen.Page.TESTS) {
                    var cps = screen.children().stream().filter(ClientSettingsScreen.ValueSlider.class::isInstance)
                            .map(ClientSettingsScreen.ValueSlider.class::cast).findFirst().orElseThrow();
                    cps.choose(999); require(ShieldDrainerModule.preferences().clicksPerSecond == 40, "Actual rate slider clamps high values");
                    cps.choose(-1); require(ShieldDrainerModule.preferences().clicksPerSecond == 1, "Actual rate slider clamps low values");
                    for (String name : new String[]{"Auto Mace", "Stun Slam"}) {
                        button(screen, name).onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                        checkWidgets(screen);
                        if (name.equals("Stun Slam")) {
                            require(screen.children().stream().noneMatch(ClientSettingsScreen.ValueSlider.class::isInstance),
                                    "Stun Slam has no timing sliders in test settings");
                            require(screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                                    .noneMatch(b -> b.getMessage().getString().startsWith("Scenario:")
                                            || b.getMessage().getString().startsWith("Activation:")),
                                    "Stun Slam has no scenario or activation choices in test settings");
                        }
                    }
                }
            }
            var screen = new ClientSettingsScreen(parent, ClientSettingsScreen.Page.CLIENT);
            client.setScreen(screen);
            button(screen, "GUI bind:").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            require(screen.capturingKeybind(), "Click starts key capture");
            screen.handleCapturedKey(new KeyEvent(GLFW.GLFW_KEY_F8, 0, 0));
            require(AzeiumKeybinds.menuKey() == GLFW.GLFW_KEY_F8 && !screen.capturingKeybind(), "Supported GUI key saves");
            button(screen, "GUI bind:").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            screen.handleCapturedKey(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
            require(AzeiumKeybinds.menuKey() == GLFW.GLFW_KEY_F8, "Escape cancels capture without changing bind");
            LungeTestClient.LOG.info("CLIENT_SETTINGS_VERIFIED: {} checks, six presets, custom palette persistence, three pages/all combat layouts at 320x240, GUI bind capture/cancel", checks);
        } finally {
            ThemeManager.initialize(new com.google.gson.Gson().fromJson(saved, ThemeManager.Preferences.class));
            LungeTestClient.saveThemePreferences();
            AzeiumKeybinds.bindMenu(originalBind);
            ShieldDrainerModule.preferences().clicksPerSecond = originalCps;
            LungeTestClient.saveTestPreferences();
            client.setScreen(parent);
        }
    }
    private static Button button(ClientSettingsScreen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow(() -> new AssertionError(prefix));
    }
    private static void checkWidgets(ClientSettingsScreen screen) {
        var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
        for (var a : widgets) {
            require(a.getX() >= 0 && a.getY() >= 0 && a.getX()+a.getWidth() <= 320 && a.getY()+a.getHeight() <= 240, "Settings widget within minimum window");
            for (var b : widgets) if (a != b)
                require(a.getX()+a.getWidth() <= b.getX() || b.getX()+b.getWidth() <= a.getX()
                        || a.getY()+a.getHeight() <= b.getY() || b.getY()+b.getHeight() <= a.getY(), "Settings controls do not overlap");
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
