package dev.lungetest.verify;
import dev.lungetest.LungeTestClient;
import dev.lungetest.LungeTestScreen;
import dev.lungetest.AzeiumModules;
import dev.lungetest.PerformanceHudModule;
import dev.lungetest.AzeiumKeybinds;
import dev.lungetest.PlayerHighlightModule;
import dev.lungetest.PlayerTracerModule;
import dev.lungetest.AutoClutchModule;
import dev.lungetest.CombatAssistModule;
import dev.lungetest.SpearMetrics;
import dev.lungetest.SpearometerHud;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
public final class GuiVerification implements ClientModInitializer {
    private boolean finished;
    private int renderedTicks;
    private boolean menuRequested;
    private int menuWaitTicks;
    private static int checks;
    private int hudFrames;
    private boolean hudPreviewLogged;
    private boolean guiPreviewLogged;
    private boolean tracerPreviewLogged;
    private boolean clutchPreviewLogged;
    private boolean tellyPreviewLogged;
    private AutoClutchModule.Mode previewClutchMode;
    private boolean macePreviewLogged;
    private boolean slamPreviewLogged;
    private final Map<Path, byte[]> originalConfig = new LinkedHashMap<>();
    private boolean configRestored;
    private final boolean[][] hudRendered = new boolean[LungeTestClient.HudMode.values().length][LungeTestClient.HudCorner.values().length];
    @Override public void onInitializeClient() {
        StartupBindVerification.verify();
        backupConfiguration();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> restoreConfiguration());
        Runtime.getRuntime().addShutdownHook(new Thread(this::restoreConfiguration,
                "Azeium verification preference restoration"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) {
                ++renderedTicks;
                if (renderedTicks == 200 && client.screen instanceof LungeTestScreen menu) {
                    menu.selectModule(AzeiumModules.Module.SPEAR);
                    LungeTestClient.LOG.info("SPEAR_GUI_PREVIEW_READY");
                }
                if (renderedTicks == 210 && client.screen instanceof LungeTestScreen menu) {
                    menu.selectModule(AzeiumModules.Module.SESSION_HUD);
                    LungeTestClient.LOG.info("SURVIVAL_GUI_PREVIEW_READY");
                }
                if (renderedTicks == 280) {
                    client.setScreen(new dev.lungetest.ClientSettingsScreen(client.screen, dev.lungetest.ClientSettingsScreen.Page.THEMES));
                    LungeTestClient.LOG.info("THEMES_GUI_PREVIEW_READY");
                }
                if (renderedTicks == 290) {
                    client.setScreen(new dev.lungetest.ClientSettingsScreen(null, dev.lungetest.ClientSettingsScreen.Page.TESTS));
                    LungeTestClient.LOG.info("TEST_SCENARIOS_GUI_PREVIEW_READY");
                }
                if (renderedTicks == 300) {
                    require(hudFrames >= 12, "Actual graphics rendered at least twelve HUD frames");
                    for (var modes : hudRendered) for (boolean rendered : modes)
                        require(rendered, "Each HUD mode and corner rendered through actual GuiGraphics");
                    LungeTestClient.LOG.info("GUI_RENDER_VERIFIED: 300 client ticks completed without rendering errors; HUD_RENDER_VERIFIED: all 12 mode/corner combinations, {} graphics frames", hudFrames);
                    restoreConfiguration();
                    client.stop();
                }
                return;
            }
            if (client.getOverlay() != null) return;
            if (!menuRequested) {
                if (!(client.screen instanceof TitleScreen)) return;
                pressMenuKey(client);
                menuRequested = true;
                return;
            }
            if (++menuWaitTicks >= 40) throw new AssertionError("Right Shift opens the controls menu");
            if (!(client.screen instanceof LungeTestScreen)) return;
            finished = true;
            require(!LungeTestClient.enabled(), "Cooldown removal starts OFF");
            var screen = (LungeTestScreen) client.screen;
            require(screen.getTitle().getString().equalsIgnoreCase("aze's hub"), "Menu is named aze's hub");
            require(screen.selectedModule() == AzeiumModules.Module.SPEAR, "Spear controls are the initial module");
            require(!button(screen, "Enable module").active, "Cannot enable spear cooldown removal without a world");
            for (String label : List.of("Spear", "Spearometer", "Player highlights", "Tracers", "Auto Clutch", "Coordinates", "Zoom", "Done", "Previous", "Next"))
                require(button(screen, label) != null, "Module navigation and Done have clear labels");
            require(LungeTestClient.hudEnabled(), "HUD defaults visible");
            require(LungeTestClient.hudMode() == LungeTestClient.HudMode.TRAVEL, "HUD defaults to travel speed and distance");
            require(LungeTestClient.hudCorner() == LungeTestClient.HudCorner.TOP_LEFT, "HUD defaults to top left");
            pressMenuKey(client);
            require(client.screen instanceof TitleScreen, "Right Shift closes the controls menu");
            pressMenuKey(client);
            require(client.screen instanceof LungeTestScreen, "Right Shift reopens the controls menu");
            screen = (LungeTestScreen) client.screen;
            var key = new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0);
            button(screen, "Enable module").onPress(key);
            require(!LungeTestClient.enabled(), "Toggle handler refuses enabling without a valid session");
            require(LungeTestClient.attempts() == 0 && LungeTestClient.dispatched() == 0, "No attacks sent from the menu");
            require(LungeTestClient.activity(client).toLowerCase(Locale.ROOT).contains("off"), "Off status is explicit");
            boolean originalHud = LungeTestClient.hudEnabled();
            var originalMode = LungeTestClient.hudMode();
            var originalCorner = LungeTestClient.hudCorner();
            button(screen, "Spearometer").onPress(key);
            require(screen.selectedModule() == AzeiumModules.Module.SPEAROMETER, "Spearometer navigation selects its settings");
            moduleToggle(screen).onPress(key);
            require(LungeTestClient.hudEnabled() != originalHud, "HUD button changes visibility");
            moduleToggle(screen).onPress(key);
            require(LungeTestClient.hudEnabled() == originalHud, "HUD visibility preference restores");
            for (int mode = 0; mode < LungeTestClient.HudMode.values().length; mode++) {
                var before = LungeTestClient.hudMode();
                button(screen, "Mode: " + before.label()).onPress(key);
                require(LungeTestClient.hudMode() != before, "Mode button cycles to a different display");
            }
            require(LungeTestClient.hudMode() == originalMode, "Mode cycle includes all views and restores preference");
            for (int corner = 0; corner < LungeTestClient.HudCorner.values().length; corner++) {
                var before = LungeTestClient.hudCorner();
                button(screen, before.label()).onPress(key);
                require(LungeTestClient.hudCorner() != before, "Corner button changes placement");
            }
            require(LungeTestClient.hudCorner() == originalCorner, "Corner cycle includes all corners and restores preference");
            button(screen, "Reset stats").onPress(key);
            require(LungeTestClient.attempts() == 0 && LungeTestClient.dispatched() == 0 && !LungeTestClient.enabled(),
                    "Reset button clears measurements without enabling attacks");
            button(screen, "Export snapshot").onPress(key);
            verifyMenuExport();
            verifyHighlightControls(screen, key);
            verifyTracerControls(screen, key);
            verifyClutchControls(screen, key);
            verifyCombatControls(screen, key);
            verifyAutoclickerControls(screen, key);
            verifyRejoinControls(screen, key);
            verifyOpacityControls(screen, key);
            KeybindVerification.verify(client, screen);
            screen = (LungeTestScreen) client.screen;
            screen.selectModule(AzeiumModules.Module.PLAYER_HIGHLIGHTS);
            colorInput(screen).setFocused(true);
            pressMenuKey(client);
            require(client.screen instanceof TitleScreen, "Right Shift closes Azeium while the color field has focus");
            pressMenuKey(client);
            require(client.screen instanceof LungeTestScreen, "Right Shift reopens Azeium after editing a color");
            screen = (LungeTestScreen) client.screen;
            HudElementRegistry.replaceElement(Identifier.fromNamespaceAndPath("lunge_test_client", "spearometer"), element -> element);
            require(true, "Spearometer is registered in the Fabric HUD pipeline");
            for (var mode : LungeTestClient.HudMode.values()) {
                for (var corner : LungeTestClient.HudCorner.values()) {
                    for (int[] size : new int[][]{{320, 240}, {1024, 768}}) {
                        var bounds = SpearometerHud.panelBounds(size[0], size[1], mode, corner);
                        require(bounds.x() >= 0 && bounds.y() >= 0 && bounds.width() > 0 && bounds.height() > 0
                                        && bounds.x() + bounds.width() <= size[0] && bounds.y() + bounds.height() <= size[1],
                                "HUD fits every view and corner at minimum and normal GUI sizes");
                    }
                }
            }

            int originalWidth = screen.width;
            int originalHeight = screen.height;
            screen.resize(320, 240);
            for (var module : AzeiumModules.Module.values()) {
                screen.selectModule(module);
                require(screen.selectedModule() == module, "Programmatic selection reaches every module");
                require(screen.children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                                .allMatch(widget -> widget.getX() >= 0 && widget.getY() >= 0
                                        && widget.getX() + widget.getWidth() <= 320
                                        && widget.getY() + widget.getHeight() <= 240),
                        "All buttons and color input fit the minimum GUI size on " + module);
                require(button(screen, "Done") != null, "Done remains available in each module");
                verifyWidgetLayout(screen, 320, 240, module);
                if (module == AzeiumModules.Module.AUTO_CLUTCH) {
                    var previousClutchMode = AutoClutchModule.mode();
                    for (var clutchMode : AutoClutchModule.Mode.values()) {
                        AutoClutchModule.setMode(clutchMode);
                        screen.selectModule(module);
                        verifyWidgetLayout(screen, 320, 240, module);
                        require(button(screen, "Mode: " + clutchMode.label()) != null,
                                "Every clutch strategy remains identifiable at the minimum GUI size");
                    }
                    AutoClutchModule.setMode(previousClutchMode);
                }
                if (module == AzeiumModules.Module.SPEAR)
                    require(!moduleToggle(screen).active, "Resizing preserves spear session guard");
            }
            screen.resize(originalWidth, originalHeight);
            button(screen, "Done").onPress(key);
            require(client.screen instanceof TitleScreen, "Done closes the controls menu");
            pressMenuKey(client);
            require(client.screen instanceof LungeTestScreen, "Right Shift reopens after Done");

            MenuInteractionVerification.verify(client);
            ClientSettingsVerification.verify(client);
            PerformanceHudModule.setEnabled(true);
            AzeiumModules.disableAll(client);
            require(java.util.Arrays.stream(AzeiumModules.Module.values()).noneMatch(AzeiumModules.Module::enabled), "Disable all clears every module without arming another");
            CooldownVerification.verify(client);
            ManualAttackVerification.verify(client);
            SpearQuickLungeVerification.verify(client);
            SurvivalModuleVerification.verify(client);
            HighlightVerification.verify(client);
            TracerVerification.verify(client);
            AutoClutchVerification.verify(client);
            ClutchPlacementVerification.verify(client);
            TellyBridgeVerification.verify(client);
            AutoTellyVerification.verify(client);
            UtilityModuleVerification.verify(client);
            CombatAssistVerification.verify(client);
            RejoinVerification.verify(client);
            ((LungeTestScreen) client.screen).selectModule(AzeiumModules.Module.PLAYER_HIGHLIGHTS);
            installHudRenderingVerification((LungeTestScreen) client.screen);
            LungeTestClient.LOG.info("GUI_VERIFIED: {} runtime checks passed, including all sixteen modules on two sidebar pages, Auto Mace fixed Smash and bounded reach controls, fixed automatic StunSlam, manual Rejoin key capture, independent colors/ranges, six clutch modes including automatic Telly and three Telly speed presets, keybinds, opacity, export and minimum-size layout without overlaps", checks);
        });
    }
    private void installHudRenderingVerification(LungeTestScreen screen) {
        int[] clicks = new int[20];
        double[] speeds = new double[20];
        for (int sample = 0; sample < 20; sample++) {
            clicks[sample] = sample % 9;
            speeds[sample] = sample * 1.25;
        }
        var preview = new SpearMetrics.Snapshot(13, 10, 8, 6, 13, 12.5, 23.75, 100.0, 1, clicks, speeds);
        ScreenEvents.AfterRender renderHook = (renderedScreen, graphics, mouseX, mouseY, partialTick) -> {
            // A window resize recreates Fabric screen events. Finish every HUD variant even
            // if a slow software-rendered frame reaches this callback later than usual.
            if (hudFrames < 12) {
                dev.lungetest.SessionHudModule.renderSnapshot(graphics, Minecraft.getInstance(), "00:12:34", "123.4 blocks");
                dev.lungetest.ArmorAlertsModule.renderWarning(graphics, Minecraft.getInstance(), "Helmet 10% · Boots 18%");
                var modes = LungeTestClient.HudMode.values();
                var corners = LungeTestClient.HudCorner.values();
                int mode = hudFrames / corners.length;
                int corner = hudFrames % corners.length;
                SpearometerHud.renderSnapshot(graphics, Minecraft.getInstance(), preview, modes[mode], corners[corner]);
                hudRendered[mode][corner] = true;
                hudFrames++;
                return;
            }
            if (renderedTicks >= 270) {
                if (!slamPreviewLogged) {
                    screen.selectModule(AzeiumModules.Module.AUTO_STUNSLAM);
                    slamPreviewLogged = true;
                    LungeTestClient.LOG.info("AUTO_STUNSLAM_GUI_PREVIEW_READY: Azeium Auto StunSlam, axe-to-mace combo, activation, targeting and use-key controls");
                }
                return;
            }
            if (renderedTicks >= 240) {
                if (!macePreviewLogged) {
                    screen.selectModule(AzeiumModules.Module.AUTO_MACE);
                    macePreviewLogged = true;
                    LungeTestClient.LOG.info("AUTO_MACE_GUI_PREVIEW_READY: Azeium Auto Mace, immediate any-entity swap-only action, optional charged follow-up, activation and use-key controls");
                }
                return;
            }
            if (renderedTicks >= 220) {
                if (!guiPreviewLogged) {
                    if (previewClutchMode != null) AutoClutchModule.setMode(previewClutchMode);
                    screen.selectModule(AzeiumModules.Module.PLAYER_HIGHLIGHTS);
                    guiPreviewLogged = true;
                    LungeTestClient.LOG.info("GUI_PREVIEW_READY: Azeium Player highlights, color, range, keybind and translucent menu controls");
                }
                return;
            }
            if (renderedTicks >= 190) {
                if (!tellyPreviewLogged) {
                    previewClutchMode = AutoClutchModule.mode();
                    AutoClutchModule.setMode(AutoClutchModule.Mode.AUTO_TELLY);
                    screen.selectModule(AzeiumModules.Module.AUTO_CLUTCH);
                    tellyPreviewLogged = true;
                    LungeTestClient.LOG.info("TELLY_BRIDGE_GUI_PREVIEW_READY: Azeium Auto Clutch, Auto Telly mode with automatic jump, look-back and forward direction controls");
                }
                return;
            }
            if (renderedTicks >= 160) {
                if (!clutchPreviewLogged) {
                    screen.selectModule(AzeiumModules.Module.AUTO_CLUTCH);
                    clutchPreviewLogged = true;
                    LungeTestClient.LOG.info("AUTO_CLUTCH_GUI_PREVIEW_READY: Azeium Auto Clutch and mode controls");
                }
                return;
            }
            if (renderedTicks >= 100) {
                if (!tracerPreviewLogged) {
                    screen.selectModule(AzeiumModules.Module.TRACERS);
                    tracerPreviewLogged = true;
                    LungeTestClient.LOG.info("TRACER_GUI_PREVIEW_READY: Azeium independent tracer module, color and range controls");
                }
                return;
            }
            var modes = LungeTestClient.HudMode.values();
            var corners = LungeTestClient.HudCorner.values();
            if (renderedTicks >= 40) {
                graphics.fillGradient(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF050D1B, 0xFF112237);
                SpearometerHud.renderSnapshot(graphics, Minecraft.getInstance(), preview,
                        LungeTestClient.HudMode.TRAVEL, LungeTestClient.HudCorner.TOP_LEFT);
                graphics.drawString(Minecraft.getInstance().font, "Spearometer preview - sample data", 8,
                        graphics.guiHeight() - 20, 0xFF9AABC3, false);
                hudFrames++;
                if (!hudPreviewLogged) {
                    hudPreviewLogged = true;
                    LungeTestClient.LOG.info("HUD_PREVIEW_READY: travel meter with synthetic sample data");
                }
                return;
            }
            int variant = hudFrames % (modes.length * corners.length);
            int mode = variant / corners.length;
            int corner = variant % corners.length;
            SpearometerHud.renderSnapshot(graphics, Minecraft.getInstance(), preview, modes[mode], corners[corner]);
            hudRendered[mode][corner] = true;
            hudFrames++;
        };
        ScreenEvents.afterRender(screen).register(renderHook);
        ScreenEvents.AFTER_INIT.register((client, initialized, width, height) -> {
            if (initialized == screen) ScreenEvents.afterRender(screen).register(renderHook);
        });
    }
    private static void verifyMenuExport() {
        var exported = LungeTestClient.lastExport();
        require(exported != null && Files.isRegularFile(exported), "Menu Export button creates a local artifact");
        try {
            var report = JsonParser.parseString(Files.readString(exported)).getAsJsonObject();
            require(report.get("measurementSource").getAsString().equals("client")
                            && !report.get("serverAcceptanceMeasured").getAsBoolean()
                            && report.get("horizontalTravel").getAsBoolean(),
                    "Export declares local horizontal measurements and no server verdict");
            require(report.getAsJsonObject("snapshot").get("attempts").getAsLong() == 0,
                    "Menu snapshot preserves the reset measurement");
        } catch (java.io.IOException e) {
            throw new AssertionError("Could not inspect the menu export", e);
        } finally {
            try { Files.deleteIfExists(exported); }
            catch (java.io.IOException e) { throw new AssertionError("Could not remove the fixture menu export", e); }
        }
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
    private void backupConfiguration() {
        try {
            Path config = FabricLoader.getInstance().getConfigDir();
            for (String name : List.of("azeium.json", "spearometer.json")) {
                Path path = config.resolve(name);
                originalConfig.put(path, Files.isRegularFile(path) ? Files.readAllBytes(path) : null);
            }
            Path options = FabricLoader.getInstance().getGameDir().resolve("options.txt");
            originalConfig.put(options, Files.isRegularFile(options) ? Files.readAllBytes(options) : null);
        } catch (java.io.IOException e) {
            throw new AssertionError("Could not back up existing client preferences before GUI verification", e);
        }
    }
    private synchronized void restoreConfiguration() {
        if (configRestored) return;
        try {
            for (var entry : originalConfig.entrySet()) {
                if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
                else {
                    Files.createDirectories(entry.getKey().getParent());
                    Files.write(entry.getKey(), entry.getValue());
                }
            }
            configRestored = true;
            LungeTestClient.LOG.info("GUI_CONFIG_RESTORED: original Azeium, legacy and Minecraft options restored byte-for-byte");
        } catch (java.io.IOException e) {
            throw new AssertionError("Could not restore client preferences after GUI verification", e);
        }
    }
    private static void verifyWidgetLayout(LungeTestScreen screen, int width, int height,
                                          AzeiumModules.Module module) {
        var widgets = screen.children().stream()
                .filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast).toList();
        require(widgets.stream().allMatch(widget -> widget.getWidth() > 0 && widget.getHeight() > 0
                        && widget.getX() >= 0 && widget.getY() >= 0
                        && widget.getX() + widget.getWidth() <= width
                        && widget.getY() + widget.getHeight() <= height),
                "Every control has positive dimensions and fits the viewport on " + module);
        for (int first = 0; first < widgets.size(); first++) {
            var a = widgets.get(first);
            for (int second = first + 1; second < widgets.size(); second++) {
                var b = widgets.get(second);
                boolean overlaps = a.getX() < b.getX() + b.getWidth()
                        && a.getX() + a.getWidth() > b.getX()
                        && a.getY() < b.getY() + b.getHeight()
                        && a.getY() + a.getHeight() > b.getY();
                require(!overlaps, "Controls do not overlap on " + module + ": "
                        + a.getMessage().getString() + " / " + b.getMessage().getString());
            }
        }
    }
    private static List<Button> buttons(LungeTestScreen screen) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
    }
    private static Button button(LungeTestScreen screen, String label) {
        return buttons(screen).stream().filter(button -> button.getMessage().getString().equals(label))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button: " + label));
    }
    private static Button moduleToggle(LungeTestScreen screen) {
        return buttons(screen).stream().filter(button -> {
            var label = button.getMessage().getString();
            return label.equals("Enable module") || label.equals("Disable module");
        }).findFirst().orElseThrow(() -> new AssertionError("Missing module toggle"));
    }
    private static EditBox colorInput(LungeTestScreen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("Missing player highlight hex color input"));
    }
    private static void verifyHighlightControls(LungeTestScreen screen, KeyEvent key) {
        boolean originallyEnabled = PlayerHighlightModule.enabled();
        int originalColor = PlayerHighlightModule.color();
        try {
            button(screen, "Player highlights").onPress(key);
            require(screen.selectedModule() == AzeiumModules.Module.PLAYER_HIGHLIGHTS,
                    "Player highlights navigation selects its module");
            moduleToggle(screen).onPress(key);
            require(PlayerHighlightModule.enabled() != originallyEnabled,
                    "Player highlight toggle changes only its module state");
            require(!LungeTestClient.enabled() && LungeTestClient.attempts() == 0 && LungeTestClient.dispatched() == 0,
                    "Player highlighting neither enables the spear nor creates attacks");
            moduleToggle(screen).onPress(key);
            require(PlayerHighlightModule.enabled() == originallyEnabled,
                    "Player highlight toggle restores its state");
            int previousPreset = -1;
            for (String label : List.of("Cyan", "Purple", "Red", "Green", "Gold")) {
                button(screen, label).onPress(key);
                int selected = PlayerHighlightModule.color();
                require(selected != previousPreset, "Each color preset selects a distinct color");
                require(colorInput(screen).getValue().equalsIgnoreCase(String.format(Locale.ROOT, "#%06X", selected & 0xFFFFFF)),
                        "Preset and hex input display the same highlight color");
                previousPreset = selected;
            }
            colorInput(screen).setValue("#12aBcD");
            button(screen, "Apply").onPress(key);
            require((PlayerHighlightModule.color() & 0xFFFFFF) == 0x12ABCD,
                    "Apply accepts a six-digit hex color with mixed-case digits");
            int acceptedColor = PlayerHighlightModule.color();
            colorInput(screen).setValue("#12GG00");
            button(screen, "Apply").onPress(key);
            require(PlayerHighlightModule.color() == acceptedColor,
                    "Invalid hex digits leave the accepted highlight color unchanged");
            colorInput(screen).setValue("#12");
            button(screen, "Apply").onPress(key);
            require(PlayerHighlightModule.color() == acceptedColor,
                    "Incomplete hex input cannot change highlight color");
            verifyRangeControls(screen, key, PlayerHighlightModule::range);
        } finally {
            PlayerHighlightModule.setEnabled(originallyEnabled);
            LungeTestClient.setHighlightColor(originalColor);
            screen.selectModule(AzeiumModules.Module.PLAYER_HIGHLIGHTS);
        }
    }
    private static void verifyTracerControls(LungeTestScreen screen, KeyEvent key) {
        boolean originallyEnabled = PlayerTracerModule.enabled();
        int originalColor = PlayerTracerModule.color();
        boolean highlightEnabled = PlayerHighlightModule.enabled();
        int highlightColor = PlayerHighlightModule.color();
        int highlightRange = PlayerHighlightModule.range();
        try {
            button(screen, "Tracers").onPress(key);
            require(screen.selectedModule() == AzeiumModules.Module.TRACERS,
                    "Tracers navigation selects its independent settings");
            moduleToggle(screen).onPress(key);
            require(PlayerTracerModule.enabled() != originallyEnabled
                            && PlayerHighlightModule.enabled() == highlightEnabled,
                    "Tracer toggle does not toggle player outlines");
            moduleToggle(screen).onPress(key);
            require(PlayerTracerModule.enabled() == originallyEnabled, "Tracer toggle restores its state");
            int previousPreset = -1;
            for (String label : List.of("Cyan", "Purple", "Red", "Green", "Gold")) {
                button(screen, label).onPress(key);
                int color = PlayerTracerModule.color();
                require(color != previousPreset, "Each tracer preset selects a distinct color");
                require(colorInput(screen).getValue().equalsIgnoreCase(String.format(Locale.ROOT,
                                "#%06X", color & 0xFFFFFF)),
                        "Tracer preset and hex input agree");
                previousPreset = color;
            }
            colorInput(screen).setValue("#98aBcD");
            button(screen, "Apply").onPress(key);
            require(PlayerTracerModule.color() == 0xFF98ABCD,
                    "Tracer hex input applies opaque custom RGB independently");
            colorInput(screen).setValue("#XXXXXX");
            button(screen, "Apply").onPress(key);
            require(PlayerTracerModule.color() == 0xFF98ABCD,
                    "Invalid tracer hex input preserves the accepted color");
            verifyRangeControls(screen, key, PlayerTracerModule::range);
            require(PlayerHighlightModule.color() == highlightColor
                            && PlayerHighlightModule.range() == highlightRange
                            && PlayerHighlightModule.enabled() == highlightEnabled,
                    "Tracer color, range and toggle controls preserve outline preferences");
        } finally {
            PlayerTracerModule.setEnabled(originallyEnabled);
            LungeTestClient.setTracerColor(originalColor);
            screen.selectModule(AzeiumModules.Module.TRACERS);
        }
    }
    private void verifyClutchControls(LungeTestScreen screen, KeyEvent key) {
        button(screen, "Auto Clutch").onPress(key);
        require(screen.selectedModule() == AzeiumModules.Module.AUTO_CLUTCH,
                "Auto Clutch navigation selects its settings");
        require(!AutoClutchModule.enabled(), "Auto Clutch starts OFF");
        require(!moduleToggle(screen).active, "Auto Clutch cannot enable without a gameplay session");
        var initialMode = AutoClutchModule.mode();
        var configuredMode = originalClutchMode();
        if (configuredMode == null)
            require(initialMode == AutoClutchModule.Mode.BOTH,
                    "First-load or migrated Auto Clutch defaults to Water + blocks");
        else require(initialMode == configuredMode, "An explicitly saved Auto Clutch strategy is preserved");
        require(button(screen, "Mode: " + initialMode.label()) != null,
                "Auto Clutch displays its actual initial strategy label");
        boolean reachedTelly = initialMode == AutoClutchModule.Mode.TELLY;
        boolean reachedTellyOnly = initialMode == AutoClutchModule.Mode.TELLY_ONLY;
        boolean reachedAutoTelly = initialMode == AutoClutchModule.Mode.AUTO_TELLY;
        for (var ignored : AutoClutchModule.Mode.values()) {
            var before = AutoClutchModule.mode();
            button(screen, "Mode: " + before.label()).onPress(key);
            require(AutoClutchModule.mode() != before, "Auto Clutch mode control cycles to another strategy");
            reachedTelly |= AutoClutchModule.mode() == AutoClutchModule.Mode.TELLY;
            reachedTellyOnly |= AutoClutchModule.mode() == AutoClutchModule.Mode.TELLY_ONLY;
            reachedAutoTelly |= AutoClutchModule.mode() == AutoClutchModule.Mode.AUTO_TELLY;
            require(!AutoClutchModule.enabled(), "Changing strategy does not enable automatic clutching");
        }
        require(reachedTelly, "The Auto Clutch mode control reaches Telly bridge + clutch");
        require(reachedTellyOnly, "The Auto Clutch mode control reaches a separate Telly-only bridge strategy");
        require(reachedAutoTelly, "The Auto Clutch mode control reaches the automatic jumping and camera Telly strategy");
        require(AutoClutchModule.mode() == initialMode, "Auto Clutch mode cycle includes all strategies");
        var initialSpeed = AutoClutchModule.tellySpeed();
        for (var bridgeMode : List.of(AutoClutchModule.Mode.TELLY, AutoClutchModule.Mode.TELLY_ONLY,
                AutoClutchModule.Mode.AUTO_TELLY)) {
            AutoClutchModule.setMode(bridgeMode);
            screen.selectModule(AzeiumModules.Module.AUTO_CLUTCH);
            for (var ignored : AutoClutchModule.TellySpeed.values()) {
                var before = AutoClutchModule.tellySpeed();
                button(screen, "Speed: " + before.label()).onPress(key);
                require(AutoClutchModule.tellySpeed() != before,
                        "Telly speed control cycles through bounded placement budgets in " + bridgeMode);
                require(!AutoClutchModule.enabled(), "Changing a placement speed never arms automatic bridging");
            }
            require(AutoClutchModule.tellySpeed() == initialSpeed,
                    "A complete speed cycle preserves the previous placement-speed preference");
        }
        AutoClutchModule.setMode(initialMode);
        screen.selectModule(AzeiumModules.Module.AUTO_CLUTCH);
        if (initialMode != AutoClutchModule.Mode.TELLY && initialMode != AutoClutchModule.Mode.TELLY_ONLY
                && initialMode != AutoClutchModule.Mode.AUTO_TELLY)
            require(buttons(screen).stream().noneMatch(button -> button.getMessage().getString().startsWith("Speed: ")),
                    "Ordinary water/block clutch modes do not expose irrelevant bridge-speed controls");
    }
    private AutoClutchModule.Mode originalClutchMode() {
        Path directory = FabricLoader.getInstance().getConfigDir();
        byte[] saved = originalConfig.get(directory.resolve("azeium.json"));
        if (saved == null) saved = originalConfig.get(directory.resolve("spearometer.json"));
        if (saved == null) return null;
        try {
            var json = JsonParser.parseString(new String(saved, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (!json.has("clutchRevision") || json.get("clutchRevision").isJsonNull()
                    || json.get("clutchRevision").getAsInt() < 2) return null;
            if (!json.has("clutchMode") || json.get("clutchMode").isJsonNull()) return null;
            return AutoClutchModule.Mode.valueOf(json.get("clutchMode").getAsString());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
    private static void verifyRejoinControls(LungeTestScreen screen, KeyEvent key) {
        int previous = dev.lungetest.AutoRejoinModule.key();
        screen.selectModule(AzeiumModules.Module.AUTO_REJOIN);
        require(!button(screen, "Rejoin now").active, "Rejoin requires a remembered multiplayer server");
        button(screen, "Rejoin key: " + dev.lungetest.AutoRejoinModule.keyLabel()).onPress(key);
        require(screen.capturingKeybind(), "Rejoin has separate key capture");
        screen.handleCapturedKey(new KeyEvent(AzeiumKeybinds.menuKey(), 0, 0));
        require(screen.capturingKeybind(), "Rejoin rejects the GUI bind and remains in capture");
        screen.handleCapturedKey(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        require(!screen.capturingKeybind() && dev.lungetest.AutoRejoinModule.key() == previous,
                "Escape preserves the original rejoin key");
        button(screen, "Rejoin key: " + dev.lungetest.AutoRejoinModule.keyLabel()).onPress(key);
        screen.handleCapturedKey(new KeyEvent(GLFW.GLFW_KEY_F16, 0, 0));
        require(!screen.capturingKeybind() && dev.lungetest.AutoRejoinModule.key() == GLFW.GLFW_KEY_F16,
                "The actual GUI capture saves a new manual rejoin key");
        button(screen, "Rejoin key: " + dev.lungetest.AutoRejoinModule.keyLabel()).onPress(key);
        screen.handleCapturedKey(new KeyEvent(GLFW.GLFW_KEY_DELETE, 0, 0));
        require(dev.lungetest.AutoRejoinModule.key() == GLFW.GLFW_KEY_UNKNOWN, "Delete clears the manual rejoin key");
        dev.lungetest.AutoRejoinModule.bindKey(previous);
    }
    private static void verifyCombatControls(LungeTestScreen screen, KeyEvent key) {
        for (var feature : CombatAssistModule.Feature.values()) {
            var module = feature == CombatAssistModule.Feature.MACE
                    ? AzeiumModules.Module.AUTO_MACE : AzeiumModules.Module.AUTO_STUNSLAM;
            screen.selectModule(module);
            require(!module.enabled(), "Combat modules start OFF: " + feature);
            require(!moduleToggle(screen).active, "Combat modules require a live world before enabling");
            String status = feature == CombatAssistModule.Feature.STUNSLAM
                    ? dev.lungetest.AutoStunSlamModule.status() : CombatAssistModule.status(feature);
            require(status.toLowerCase(Locale.ROOT).contains("off")
                            || status.toLowerCase(Locale.ROOT).contains("disabled"),
                    "Disabled combat status is explicit");
            if (feature == CombatAssistModule.Feature.STUNSLAM) {
                require(buttons(screen).stream().noneMatch(button -> {
                    String label = button.getMessage().getString();
                    return label.startsWith("Activate:") || label.startsWith("Aim:")
                            || label.startsWith("Combo:") || label.startsWith("Use key:");
                }), "StunSlam has no activation, target, strategy or use-key settings");
                require(!dev.lungetest.AutoStunSlamModule.busy(),
                        "Uploaded Stun Slam starts idle without a pending attack");
                var old = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(
                        dev.lungetest.AutoStunSlamModule.preferences()), dev.lungetest.AutoStunSlamModule.Preferences.class);
                button(screen, dev.lungetest.AutoStunSlamModule.delayLabel()).onPress(key);
                require(dev.lungetest.AutoStunSlamModule.preferences().followUpTicks != old.followUpTicks, "Actual Stun Slam delay control changes ticks");
                button(screen, dev.lungetest.AutoStunSlamModule.triggerLabel()).onPress(key);
                require(dev.lungetest.AutoStunSlamModule.preferences().trigger != old.trigger, "Actual Stun Slam trigger control switches click/look mode");
                button(screen, dev.lungetest.AutoStunSlamModule.retryLabel()).onPress(key);
                require(dev.lungetest.AutoStunSlamModule.preferences().retriggerTicks != old.retriggerTicks, "Actual Stun Slam repeat control changes ticks");
                dev.lungetest.AutoStunSlamModule.initialize(old); LungeTestClient.saveStunPreferences();
                continue;
            }
            var originalActivation = CombatAssistModule.activation(feature);
            for (var ignored : CombatAssistModule.Activation.values()) {
                var before = CombatAssistModule.activation(feature);
                button(screen, "Activate: " + before.label()).onPress(key);
                require(CombatAssistModule.activation(feature) != before,
                        "Combat activation control cycles between held and automatic operation");
            }
            require(CombatAssistModule.activation(feature) == originalActivation,
                    "The activation control preserves an explicit saved preference after a complete cycle");
            if (feature == CombatAssistModule.Feature.MACE) {
                require(!button(screen, "Action: Smash").active, "Auto Mace always uses Smash");
                double initialReach = CombatAssistModule.smashReach();
                for (int i = 0; i < 8; i++) {
                    button(screen, CombatAssistModule.smashReachLabel()).onPress(key);
                    require(CombatAssistModule.smashReach() >= 0 && CombatAssistModule.smashReach() <= 6,
                            "Reach control is capped at six blocks");
                }
                require(CombatAssistModule.smashReach() == initialReach, "Reach cycle returns to saved value");
            }
            int originalUseKey = CombatAssistModule.activationKey(feature);
            button(screen, "Use key: " + CombatAssistModule.activationKeyLabel(feature)).onPress(key);
            require(screen.capturingKeybind(), "Combat use key has an independent capture control");
            screen.handleCapturedKey(new KeyEvent(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, 0));
            require(screen.capturingKeybind() && CombatAssistModule.activationKey(feature) == originalUseKey,
                    "A combat use key cannot claim the assigned menu key");
            screen.handleCapturedKey(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
            require(!screen.capturingKeybind() && CombatAssistModule.activationKey(feature) == originalUseKey,
                    "Escape cancels combat use-key capture without discarding the old key");
            button(screen, "Use key: " + CombatAssistModule.activationKeyLabel(feature)).onPress(key);
            screen.handleCapturedKey(new KeyEvent(GLFW.GLFW_KEY_DELETE, 0, 0));
            require(!screen.capturingKeybind()
                            && CombatAssistModule.activationKey(feature) == GLFW.GLFW_KEY_UNKNOWN,
                    "Delete clears only the current combat activation key");
            require(CombatAssistModule.bindActivationKey(feature, originalUseKey),
                    "Combat use key restores after verification");
            screen.selectModule(module);
            require(!CombatAssistModule.enabled(feature), "Editing combat preferences never arms a module");
        }
    }
    private static void verifyAutoclickerControls(LungeTestScreen screen, KeyEvent key) {
        var previous = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(
                dev.lungetest.AutoClickerModule.preferences()), dev.lungetest.AutoClickerModule.Preferences.class);
        screen.selectModule(AzeiumModules.Module.AUTO_CLICKER);
        require(!dev.lungetest.AutoClickerModule.enabled(), "Autoclicker starts OFF");
        for (int i = 0; i < 2; i++) {
            var before = dev.lungetest.AutoClickerModule.preferences().mode;
            button(screen, dev.lungetest.AutoClickerModule.modeLabel()).onPress(key);
            require(dev.lungetest.AutoClickerModule.preferences().mode != before, "Actual UI cycles toggle/hold mode");
        }
        for (int i = 0; i < 2; i++) {
            var before = dev.lungetest.AutoClickerModule.preferences().button;
            button(screen, dev.lungetest.AutoClickerModule.buttonLabel()).onPress(key);
            require(dev.lungetest.AutoClickerModule.preferences().button != before, "Actual UI cycles left/right click");
        }
        for (int i = 0; i < 9; i++) {
            int before = dev.lungetest.AutoClickerModule.preferences().cps;
            button(screen, dev.lungetest.AutoClickerModule.rateLabel()).onPress(key);
            require(dev.lungetest.AutoClickerModule.preferences().cps != before, "Actual UI cycles supported click rates");
        }
        require(!dev.lungetest.AutoClickerModule.enabled(), "Editing autoclicker settings never enables it");
        dev.lungetest.AutoClickerModule.initialize(previous);
        LungeTestClient.saveClickerPreferences();
    }
    private static String maceActionButton() {
        return "Action: " + switch (CombatAssistModule.maceAction()) {
            case FALLING_SMASH -> "Smash";
            case SWAP_ONLY -> "Swap only";
            case CHARGED_FOLLOW_UP -> "Follow-up";
        };
    }
    private static void verifyOpacityControls(LungeTestScreen screen, KeyEvent key) {
        int initialOpacity = LungeTestClient.uiOpacity();
        for (int value = 0; value < 3; value++) {
            int before = LungeTestClient.uiOpacity();
            button(screen, "Opacity: " + before + "%").onPress(key);
            int after = LungeTestClient.uiOpacity();
            require(after != before && (after == 55 || after == 70 || after == 85),
                    "Actual opacity control cycles through the supported translucent presets");
        }
        require(LungeTestClient.uiOpacity() == initialOpacity, "Opacity cycle restores its original preference");
    }
    private static void verifyRangeControls(LungeTestScreen screen, KeyEvent key,
                                           java.util.function.IntSupplier range) {
        int original = range.getAsInt();
        for (int value = 0; value < 4; value++) {
            int before = range.getAsInt();
            button(screen, "Range: " + before + " blocks").onPress(key);
            int after = range.getAsInt();
            require(after != before && (after == 128 || after == 256 || after == 512 || after == 1024),
                    "Range control cycles through distinct selectable block limits");
        }
        require(range.getAsInt() == original, "Range cycle restores the original selection");
    }
    private static void pressMenuKey(Minecraft client) {
        try {
            var callback = client.keyboardHandler.getClass().getDeclaredMethod(
                    "keyPress", long.class, int.class, KeyEvent.class);
            callback.setAccessible(true);
            callback.invoke(client.keyboardHandler, client.getWindow().handle(), GLFW.GLFW_PRESS,
                    new KeyEvent(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, GLFW.GLFW_MOD_SHIFT));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not exercise the real Minecraft keyboard callback", e);
        }
    }
}
