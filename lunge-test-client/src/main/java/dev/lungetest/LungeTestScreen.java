package dev.lungetest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.Locale;

/** A translucent module browser. Changing a tab never changes its module state. */
public final class LungeTestScreen extends AzeiumScreen {
    private static int TEXT = ThemeManager.text(), MUTED = ThemeManager.muted(), ACCENT = ThemeManager.accent();
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("lunge_test_client", "icon.png");
    private static final String[] PRESETS = {"Cyan", "Purple", "Red", "Green", "Gold"};
    private static final int[] COLORS = {0xFF69E4EF, 0xFFB68AFF, 0xFFFF6B86, 0xFF70E5AB, 0xFFFFD166};
    private AzeiumModules.Module selected = AzeiumModules.Module.SPEAR;
    private AzeiumModules.Module captureModule;
    private CombatAssistModule.Feature captureActivation;
    private boolean captureMenu, captureRejoin, captureSpear;
    private static final int MODULES_PER_PAGE = 8;
    private int navPage;
    private Button spearKey, spearSlot, armorThreshold;
    private Button rejoinNow, rejoinKey;
    private Button toggle, moduleKey, menuKey, opacity, mode, corner, range, clutchSpeed;
    private Button combatActivation, combatTarget, combatBehavior, combatKey;
    private String lastCombatBehaviorHint;
    private EditBox colorInput;
    private String notice;
    private int noticeTicks;
    private boolean noticeError;
    private final long openedAt = System.nanoTime();
    private long changedTabAt = openedAt;
    private long lastFrameAt = openedAt;
    private float visibility, tabVisibility = 1, selectedRailY = Float.NaN;

    public LungeTestScreen() { super(Component.literal("Azeium")); }
    public AzeiumModules.Module selectedModule() { return selected; }
    public boolean capturingKeybind() { return captureMenu || captureRejoin || captureSpear || captureModule != null || captureActivation != null; }

    public void selectModule(AzeiumModules.Module module) {
        if (selected != module) changedTabAt = System.nanoTime();
        if (navPage != module.ordinal() / MODULES_PER_PAGE) selectedRailY = Float.NaN;
        navPage = module.ordinal() / MODULES_PER_PAGE;
        selected = module;
        cancelCapture();
        notice = null;
        noticeTicks = 0;
        noticeError = false;
        if (minecraft != null) { clearWidgets(); init(); }
    }

    @Override protected void init() {
        int left = panelLeft(), top = panelTop(), side = sidebarWidth();
        int x = contentLeft(), w = contentWidth(), settings = settingsY();
        spearKey = null; spearSlot = null; armorThreshold = null;
        mode = null; corner = null; range = null; clutchSpeed = null; colorInput = null;
        rejoinNow = null; rejoinKey = null;
        combatActivation = null; combatTarget = null; combatBehavior = null; combatKey = null;
        lastCombatBehaviorHint = null;
        int navTop = navTop();
        int navStep = navStep();
        int navHeight = navStep - (compact() ? 1 : 3);
        var settingsButton = addButton(left + panelWidth() - 135, top + 7, 66, 18, "Settings",
                b -> minecraft.setScreen(new ClientSettingsScreen(this, ClientSettingsScreen.Page.CLIENT)));
        settingsButton.setTooltip(hint("Client settings, gradient themes and combat test scenarios."));
        for (var module : AzeiumModules.Module.values()) {
            if (module.ordinal() / MODULES_PER_PAGE != navPage) continue;
            var button = addRenderableWidget(new AzeiumButton(left + 10, navTop + module.ordinal() % MODULES_PER_PAGE * navStep,
                    side - 20, navHeight, module.label(), b -> selectModule(module), module, null));
            button.setTooltip(hint(module.description()));
        }

        int pageWidth = (side - 24) / 2;
        var previousPage = addButton(left + 10, top + panelHeight() - 66, pageWidth, 16, "Previous", b -> showModulePage(navPage - 1));
        var nextPage = addButton(left + 14 + pageWidth, top + panelHeight() - 66, side - 24 - pageWidth, 16, "Next", b -> showModulePage(navPage + 1));
        previousPage.active = navPage > 0;
        nextPage.active = (navPage + 1) * MODULES_PER_PAGE < AzeiumModules.Module.values().length;

        opacity = addButton(left + 10, top + panelHeight() - 42, side - 20, 16,
                opacityLabel(), b -> { LungeTestClient.cycleUiOpacity(); refreshButtons(); });
        opacity.setTooltip(hint("Choose 55%, 70% or 85% panel opacity. The world stays visible behind this menu."));
        menuKey = addButton(left + 10, top + panelHeight() - 22, side - 20, 16,
                menuKeyLabel(), b -> beginCapture(null, true));
        menuKey.setTooltip(hint("Choose the key that opens and closes Azeium. Escape cancels key capture."));

        toggle = addButton(x, top + (compact() ? 75 : 87), w, 20, toggleLabel(), b -> {
            selected.toggle(minecraft); refreshButtons();
        });
        toggle.setTooltip(hint(selected.description()));
        moduleKey = addButton(x, top + (compact() ? 100 : 113), w, 18,
                moduleKeyLabel(), b -> beginCapture(selected, false));
        moduleKey.setTooltip(hint("Press a key to bind this module. Escape cancels; Backspace or Delete clears its key. Module keys work during gameplay."));

        switch (selected) {
            case SPEAR -> {
                toggle.setTooltip(hint("Click manually at your own pace. Turns off on death or disconnect. The server decides whether attacks and lunges succeed."));
                spearKey = addButton(x, settings, w, 20, spearKeyLabel(), b -> {
                    cancelCapture(); captureSpear = true; refreshButtons();
                });
                spearKey.setTooltip(hint("Separate action key: selects a hotbar spear, sends one normal jab and restores your previous slot. Enable Spear first. Lunge movement needs the real Lunge enchantment and vanilla conditions. Hold repeats do not attack."));
                spearSlot = addButton(x, settings + 24, w, 18, "Spear slot: " + SpearQuickLungeModule.slotLabel(), b -> {
                    SpearQuickLungeModule.cycleSlot(); refreshButtons();
                });
            }
            case SPEAROMETER -> {
                mode = addButton(x, settings, w, 18, modeLabel(), b -> {
                    LungeTestClient.cycleHudMode(); refreshButtons();
                });
                corner = addButton(x, settings + 23, w, 18, LungeTestClient.hudCorner().label(), b -> {
                    LungeTestClient.cycleHudCorner(); refreshButtons();
                });
                int half = (w - 6) / 2;
                addButton(x, settings + 46, half, 20, "Reset stats", b -> {
                    LungeTestClient.resetStats(); showNotice("Statistics reset", false);
                });
                addButton(x + half + 6, settings + 46, w - half - 6, 20,
                        "Export snapshot", b -> showNotice(LungeTestClient.exportStats(minecraft), false));
            }
            case PLAYER_HIGHLIGHTS, TRACERS -> buildColorControls(x, w, settings);
            case AUTO_CLUTCH -> {
                mode = addButton(x, settings, w, 20, modeLabel(), b -> {
                    LungeTestClient.cycleClutchMode(); selectModule(selected);
                });
                mode.setTooltip(hint("Auto Telly walks, jumps, looks back to place blocks, then faces forward. Face your bridge direction on a solid block, enable it and close the menu. Manual movement or mouse input stops it. Telly only and Telly bridge + clutch assist your own movement; the latter also keeps water recovery."));
                if (autoTellyMode()) toggle.setTooltip(hint("Stand on solid bridge support with blocks in your hotbar or offhand. Face the direction to extend the bridge, enable Auto Telly, then close this menu. Move or turn manually to stop; the module key also toggles it off."));
                if (tellyMode()) {
                    clutchSpeed = addButton(x, settings + 24, w, 18, clutchSpeedLabel(), b -> {
                        LungeTestClient.cycleTellySpeed(); refreshButtons();
                    });
                    clutchSpeed.setTooltip(hint("Choose 1, 2 or 4 supported placements per game tick. Fast fills short gaps sooner, but each block still needs real support and normal placement reach. Keep blocks in your hotbar or offhand."));
                }
            }
            case SHIELD_DRAINER -> {
                addButton(x, settings, w, 20, "Configure test scenario", b ->
                        minecraft.setScreen(new ClientSettingsScreen(this, ClientSettingsScreen.Page.TESTS)));
            }
            case COORDINATES, FULLBRIGHT, PERFORMANCE_HUD -> { }
            case ARMOR_ALERTS -> armorThreshold = addButton(x, settings, w, 20,
                    "Warn at: " + ArmorAlertsModule.threshold() + "%", b -> { ArmorAlertsModule.cycleThreshold(); refreshButtons(); });
            case SESSION_HUD -> addButton(x, settings, w, 20, "Reset session stats", b -> {
                SessionHudModule.reset(); showNotice("Session statistics reset", false);
            });
            case ZOOM -> {
                mode = addButton(x, settings, w, 20, modeLabel(), b -> {
                    ZoomModule.cycleFactor(); refreshButtons();
                });
                mode.setTooltip(hint("Choose 2x, 4x or 8x magnification. Toggle Zoom or bind a module key."));
            }
            case AUTO_MACE -> buildCombatControls(x, w, settings);
            case AUTO_STUNSLAM -> { }
            case AUTO_REJOIN -> {
                rejoinNow = addButton(x, settings, w, 20, "Rejoin now", b -> AutoRejoinModule.rejoin(minecraft));
                rejoinNow.setTooltip(hint("Disconnect and reconnect to the current or last multiplayer server using vanilla connection handling."));
                rejoinKey = addButton(x, settings + 24, w, 18, rejoinKeyLabel(), b -> {
                    cancelCapture(); captureRejoin = true; refreshButtons();
                });
                rejoinKey.setTooltip(hint("Assign a separate key to rejoin immediately during gameplay. Escape cancels; Delete clears it."));
            }
        }
        addButton(left + panelWidth() - 74, top + panelHeight() - 25, 60, 18, "Done", b -> onClose());
        refreshButtons();
    }

    private void buildCombatControls(int x, int w, int y) {
        var feature = combatFeature();
        // Three rows preserve the footer even at a 320 x 240 GUI scale.
        int half = (w - 6) / 2;
        combatActivation = addButton(x, y, half, 18, combatActivationLabel(), b -> {
            CombatAssistModule.cycleActivation(feature); refreshButtons();
        });
        combatActivation.setTooltip(hint(feature == CombatAssistModule.Feature.MACE
                ? "Automatic prepares your falling manual hit. Hold key requires the use key during that hit. Your previous hotbar slot is restored."
                : "Automatic starts the combo when you manually attack a hostile mob, without a use key. Hold requires the use key during your attack and follow-up. The module key above enables or disables the module."));
        combatTarget = addButton(x + half + 6, y, w - half - 6, 18, combatTargetLabel(), b -> {
            if (feature == CombatAssistModule.Feature.MACE) CombatAssistModule.cycleMaceAction();
            else CombatAssistModule.cycleTargetMode(feature);
            refreshButtons();
        });
        combatTarget.setTooltip(hint(feature == CombatAssistModule.Feature.MACE
                ? "Smash is always selected. A valid falling click uses your hotbar mace for that hit, then returns to the original slot."
                : "Crosshair follows up on the hostile mob you manually hit. Nearest hostile chooses the closest visible hostile mob within normal attack reach when the combo starts. The combo stays on that target."));
        combatBehavior = addButton(x, y + 21, w, 18, combatBehaviorLabel(), b -> {
            if (feature == CombatAssistModule.Feature.MACE) CombatAssistModule.cycleSmashReach();
            refreshButtons();
        });
        combatKey = addButton(x, y + 42, w, 18, combatKeyLabel(), b -> beginActivationCapture(feature));
        combatKey.setTooltip(hint("Choose the activation key for Hold key mode. Automatic needs no use key. This is separate from the enable/disable key above. Escape cancels; Backspace or Delete clears the use key."));
    }

    private CombatAssistModule.Feature combatFeature() {
        return selected == AzeiumModules.Module.AUTO_MACE
                ? CombatAssistModule.Feature.MACE : CombatAssistModule.Feature.STUNSLAM;
    }
    private String combatActivationLabel() { return "Activate: " + CombatAssistModule.activation(combatFeature()).label(); }
    private String combatTargetLabel() {
        if (combatFeature() == CombatAssistModule.Feature.MACE)
            return "Action: " + switch (CombatAssistModule.maceAction()) {
                case FALLING_SMASH -> "Smash";
                case SWAP_ONLY -> "Swap only";
                case CHARGED_FOLLOW_UP -> "Follow-up";
            };
        return "Aim: " + CombatAssistModule.targetMode(combatFeature()).label();
    }
    private boolean fallingSmash() { return CombatAssistModule.maceAction() == CombatAssistModule.MaceAction.FALLING_SMASH; }
    private boolean swapOnly() { return CombatAssistModule.maceAction() == CombatAssistModule.MaceAction.SWAP_ONLY; }
    private boolean tellyMode() {
        return AutoClutchModule.mode() == AutoClutchModule.Mode.TELLY
                || AutoClutchModule.mode() == AutoClutchModule.Mode.TELLY_ONLY
                || autoTellyMode();
    }
    private boolean autoTellyMode() { return AutoClutchModule.mode() == AutoClutchModule.Mode.AUTO_TELLY; }
    private String clutchSpeedLabel() { return "Speed: " + AutoClutchModule.tellySpeed().label(); }
    private String combatBehaviorLabel() {
        if (fallingSmash()) return CombatAssistModule.smashReachLabel();
        return combatFeature() == CombatAssistModule.Feature.MACE
                ? "Attacks: " + CombatAssistModule.maceModeLabel()
                : "Combo: Axe + mace";
    }
    private String combatBehaviorHint() {
        if (combatFeature() == CombatAssistModule.Feature.MACE)
            return fallingSmash() ? "While descending far enough for a vanilla mace smash, your click switches to a hotbar mace before that same hit. Your previous slot is restored after the hit. Click to select vanilla reach or 3–6 blocks. The server decides whether extended hits are accepted."
                    : swapOnly()
                    ? "Swap only keeps all attacks manual. This charge preference applies only if you choose Charged follow-up. Any entity hit can select your mace; your original hit still uses the item you held."
                    : "After your manual hit, switch to a hotbar mace. A hostile mob can receive one fully charged follow-up. Falling smash requires a real fall; Normal + smash also allows grounded hits. Your previous slot is restored afterward.";
        return "Click a raised shield to start the automatic axe-to-mace sequence.";
    }
    private String combatKeyLabel() {
        return captureActivation == combatFeature() ? "Press a use key..."
                : "Use key: " + CombatAssistModule.activationKeyLabel(combatFeature());
    }

    private void buildColorControls(int x, int w, int y) {
        range = addButton(x, y, w, 18, rangeLabel(), b -> {
            if (selected == AzeiumModules.Module.PLAYER_HIGHLIGHTS) LungeTestClient.cycleHighlightRange();
            else LungeTestClient.cycleTracerRange();
            refreshButtons();
        });
        range.setTooltip(hint("Choose 128, 256, 512 or 1024 blocks. Only players already loaded by the server can be shown."));
        int gap = 4, swatchWidth = (w - gap * 4) / 5;
        for (int i = 0; i < PRESETS.length; i++) {
            final int preset = i;
            int swatchX = x + i * (swatchWidth + gap);
            int width = i == PRESETS.length - 1 ? x + w - swatchX : swatchWidth;
            var swatch = addRenderableWidget(new AzeiumButton(swatchX, y + 23, width, 18, PRESETS[i], b -> {
                setSelectedColor(COLORS[preset]);
                colorInput.setValue(HighlightColors.format(COLORS[preset]));
                showNotice(PRESETS[preset] + " color saved", false);
            }, null, COLORS[i]));
            swatch.setTooltip(hint(PRESETS[i] + " " + HighlightColors.format(COLORS[i])));
        }
        colorInput = addRenderableWidget(new UiTextField(font, x + 20, y + 46, w - 76, 20,
                Component.literal("Player color, hexadecimal RGB")));
        colorInput.setMaxLength(7);
        colorInput.setValue(HighlightColors.format(selectedColor()));
        colorInput.setTextColor(TEXT);
        colorInput.setResponder(value -> { noticeError = false; noticeTicks = 0; });
        addButton(x + w - 48, y + 46, 48, 20, "Apply", b -> {
            var parsed = HighlightColors.parse(colorInput.getValue());
            if (parsed.isEmpty()) { showNotice("Use six hex digits: #RRGGBB", true); return; }
            setSelectedColor(parsed.getAsInt());
            colorInput.setValue(HighlightColors.format(parsed.getAsInt()));
            showNotice("Color saved", false);
        });
    }

    private void setSelectedColor(int value) {
        if (selected == AzeiumModules.Module.PLAYER_HIGHLIGHTS) LungeTestClient.setHighlightColor(value);
        else LungeTestClient.setTracerColor(value);
    }
    private int selectedColor() {
        return selected == AzeiumModules.Module.PLAYER_HIGHLIGHTS ? PlayerHighlightModule.color() : PlayerTracerModule.color();
    }
    private Button addButton(int x, int y, int w, int h, String label, Button.OnPress callback) {
        return addRenderableWidget(new AzeiumButton(x, y, w, h, label, callback, null, null));
    }
    private static Tooltip hint(String text) { return Tooltip.create(UiTypography.text(text)); }
    private String toggleLabel() { return selected.enabled() ? "Disable module" : "Enable module"; }
    private String opacityLabel() { return "Opacity: " + LungeTestClient.uiOpacity() + "%"; }
    private String moduleKeyLabel() {
        return captureModule == selected ? "Press a key..." : "Keybind: " + AzeiumKeybinds.label(selected);
    }
    private String menuKeyLabel() { return captureMenu ? "Press menu key..." : "Menu key: " + AzeiumKeybinds.menuLabel(); }
    private String rangeLabel() {
        int blocks = selected == AzeiumModules.Module.PLAYER_HIGHLIGHTS ? PlayerHighlightModule.range() : PlayerTracerModule.range();
        return "Range: " + blocks + " blocks";
    }
    private String modeLabel() {
        if (selected == AzeiumModules.Module.ZOOM) return "Magnification: " + ZoomModule.factor() + "x";
        return "Mode: " + (selected == AzeiumModules.Module.AUTO_CLUTCH
                ? AutoClutchModule.mode().label() : LungeTestClient.hudMode().label());
    }
    private void refreshButtons() {
        if (toggle == null) return;
        label(toggle, toggleLabel());
        toggle.active = selected.canToggle(minecraft);
        label(moduleKey, moduleKeyLabel());
        label(menuKey, menuKeyLabel());
        label(opacity, opacityLabel());
        if (mode != null) label(mode, modeLabel());
        if (corner != null) label(corner, LungeTestClient.hudCorner().label());
        if (range != null) label(range, rangeLabel());
        if (clutchSpeed != null) label(clutchSpeed, clutchSpeedLabel());
        if (combatActivation != null) label(combatActivation, combatActivationLabel());
        if (combatTarget != null) { label(combatTarget, combatTargetLabel()); combatTarget.active = false; }
        if (combatBehavior != null) {
            label(combatBehavior, combatBehaviorLabel());
            combatBehavior.active = true;
            String behaviorHint = combatBehaviorHint();
            if (!behaviorHint.equals(lastCombatBehaviorHint)) {
                combatBehavior.setTooltip(hint(behaviorHint));
                lastCombatBehaviorHint = behaviorHint;
            }
        }
        if (combatKey != null) label(combatKey, combatKeyLabel());
        if (rejoinNow != null) rejoinNow.active = AutoRejoinModule.canRejoin(minecraft);
        if (rejoinKey != null) label(rejoinKey, rejoinKeyLabel());
        if (spearKey != null) label(spearKey, spearKeyLabel());
        if (spearSlot != null) label(spearSlot, "Spear slot: " + SpearQuickLungeModule.slotLabel());
        if (armorThreshold != null) label(armorThreshold, "Warn at: " + ArmorAlertsModule.threshold() + "%");
    }
    private static void label(Button button, String value) {
        if (!value.equals(button.getMessage().getString())) button.setMessage(Component.literal(value));
    }
    private void showNotice(String message, boolean error) {
        notice = message; noticeTicks = 100; noticeError = error;
    }
    private void beginCapture(AzeiumModules.Module module, boolean menu) {
        cancelCapture();
        captureModule = module;
        captureMenu = menu;
        showNotice("Escape cancels key capture", false);
        refreshButtons();
    }
    private void beginActivationCapture(CombatAssistModule.Feature feature) {
        cancelCapture();
        captureActivation = feature;
        showNotice("Choose a use key for Hold mode", false);
        refreshButtons();
    }
    public int modulePage() { return navPage; }
    public void showModulePage(int page) {
        int last = (AzeiumModules.Module.values().length - 1) / MODULES_PER_PAGE;
        selectModule(AzeiumModules.Module.values()[Math.clamp(page, 0, last) * MODULES_PER_PAGE]);
    }
    private String spearKeyLabel() { return captureSpear ? "Press key / mouse button..." : "Quick-lunge key: " + SpearQuickLungeModule.keyLabel(); }
    private String rejoinKeyLabel() { return captureRejoin ? "Press a rejoin key..." : "Rejoin key: " + AutoRejoinModule.keyLabel(); }
    private void cancelCapture() { captureModule = null; captureMenu = false; captureRejoin = false; captureSpear = false; captureActivation = null; }

    /** The keyboard mixin routes capture here before checking menu or module shortcuts. */
    public boolean handleCapturedKey(KeyEvent event) {
        if (!capturingKeybind()) return false;
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancelCapture(); showNotice("Key capture cancelled", false);
        } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            if (captureMenu) showNotice("Choose a menu key; Escape cancels", true);
            else if (captureSpear) {
                SpearQuickLungeModule.bindKey(GLFW.GLFW_KEY_UNKNOWN);
                cancelCapture(); showNotice("Quick-lunge key cleared", false);
            }
            else if (captureRejoin) {
                AutoRejoinModule.bindKey(GLFW.GLFW_KEY_UNKNOWN);
                cancelCapture(); showNotice("Rejoin key cleared", false);
            }
            else if (captureActivation != null) {
                if (CombatAssistModule.bindActivationKey(captureActivation, GLFW.GLFW_KEY_UNKNOWN)) {
                    cancelCapture(); showNotice("Use key cleared", false);
                } else showNotice("Could not clear the use key", true);
            }
            else {
                AzeiumKeybinds.unbind(captureModule);
                cancelCapture(); showNotice("Module key cleared", false);
            }
        } else if (!AzeiumKeybinds.validKey(key)) {
            showNotice("Choose a keyboard key", true);
        } else {
            boolean accepted = captureSpear ? SpearQuickLungeModule.bindKey(key) : captureRejoin ? AutoRejoinModule.bindKey(key) : captureMenu ? AzeiumKeybinds.bindMenu(key)
                    : captureActivation != null ? CombatAssistModule.bindActivationKey(captureActivation, key)
                    : AzeiumKeybinds.bind(captureModule, key);
            if (accepted) { cancelCapture(); showNotice("Keybind saved", false); }
            else showNotice("That key is already assigned", true);
        }
        refreshButtons();
        return true;
    }
    public boolean handleCapturedMouse(int button) {
        if (!captureSpear) return false;
        if (SpearQuickLungeModule.bindKey(SpearQuickLungeModule.mouseCode(button))) {
            cancelCapture(); showNotice("Mouse bind saved", false);
        } else showNotice("Unsupported mouse button", true);
        refreshButtons();
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        return handleCapturedKey(event) || super.keyPressed(event);
    }
    @Override public void onClose() { cancelCapture(); super.onClose(); }
    @Override public void tick() { refreshButtons(); if (noticeTicks > 0) noticeTicks--; }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) renderPanorama(g, delta);
        g.fill(0, 0, width, height, 0x55050810);
        g.fillGradient(0, 0, width, height, glass(ThemeManager.first(), .12F), glass(ThemeManager.second(), .08F));
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        TEXT = ThemeManager.text(); MUTED = ThemeManager.muted(); ACCENT = ThemeManager.accent();
        int left = panelLeft(), top = panelTop(), w = panelWidth(), h = panelHeight(), side = sidebarWidth();
        int x = contentLeft(), contentWidth = contentWidth(), settings = settingsY();
        long now = System.nanoTime();
        visibility = ease(Math.clamp((now - openedAt) / 180_000_000F, 0, 1));
        tabVisibility = .65F + .35F * ease(Math.clamp((now - changedTabAt) / 120_000_000F, 0, 1));
        float elapsed = Math.min(.1F, (now - lastFrameAt) / 1_000_000_000F);
        lastFrameAt = now;
        float railTarget = navTop() + selected.ordinal() % MODULES_PER_PAGE * navStep();
        if (Float.isNaN(selectedRailY)) selectedRailY = railTarget;
        selectedRailY += (railTarget - selectedRailY) * (1 - (float) Math.exp(-elapsed * 18));

        UiGlass.panel(g, left, top, w, h, visibility);
        roundedGradient(g, left + 5, top + 43, side - 10, h - 48, 10,
                fade(0x29060B14, visibility), fade(0x19060B14, visibility));
        g.fill(left + side, top + 49, left + side + 1, top + h - 12, fade(0x16BCCADD, visibility));
        g.fill(left + 14, top + 40, left + w - 14, top + 41, fade(0x1DBCCADD, visibility));
        roundedGradient(g, x - 5, top + 44, contentWidth + 10, h - 78, 9,
                fade(0x15090E19, visibility), fade(0x23090E19, visibility));
        roundedGradient(g, left + 10, Math.round(selectedRailY), side - 20, navStep() - (compact() ? 1 : 3), 6,
                fade(ThemeManager.alpha(ThemeManager.first(), 43), visibility),
                fade(ThemeManager.alpha(ThemeManager.second(), 16), visibility));
        rounded(g, left + 11, Math.round(selectedRailY) + 4, 2, Math.max(2, navStep() - 11), 1,
                fade(ThemeManager.secondary(), visibility));

        rounded(g, left + 10, top + 6, 30, 30, 6, fade(ThemeManager.alpha(ThemeManager.secondary(), 112), visibility));
        g.blit(ICON, left + 11, top + 7, left + 39, top + 35, 0, 1, 0, 1);
        g.pose().pushMatrix();
        g.pose().translate(left + 48, top + 4);
        g.pose().scale(1.15F, 1.15F);
        UiTypography.heading(g, font, "azeium", 0, 0, TEXT, 100);
        g.pose().popMatrix();
        UiTypography.draw(g, font, "PERSONAL CLIENT", left + 48, top + 25, MUTED, false);
        long active = java.util.Arrays.stream(AzeiumModules.Module.values()).filter(AzeiumModules.Module::enabled).count();
        UiTypography.draw(g, font, "1.21.11", left + w - 58, top + 10, MUTED, false);
        UiTypography.draw(g, font, active + " / " + AzeiumModules.Module.values().length + " ON", left + w - 58, top + 25, MUTED, false);
        if (!compact()) UiTypography.draw(g, font, "MODULES  " + (navPage + 1) + " / " + ((AzeiumModules.Module.values().length + MODULES_PER_PAGE - 1) / MODULES_PER_PAGE), left + 14, top + 50, MUTED, false);

        int titleY = top + (compact() ? 47 : 53);
        UiTypography.heading(g, font, selected.label(), x, titleY - 5, TEXT, contentWidth - 40);
        String badge = selected.enabled() ? "ON" : "OFF";
        rounded(g, left + w - 47, titleY - 2, 33, 14, 7,
                selected.enabled() ? glass(0x235C53, .8F) : glass(0x202A40, .7F));
        UiTypography.center(g, font, badge, left + w - 30, titleY + 1, selected.enabled() ? 0xFF91ECD0 : MUTED);
        text(g, selected.description(), x, top + (compact() ? 61 : 72), MUTED, contentWidth);

        switch (selected) {
            case SPEAR -> {
                text(g, SpearQuickLungeModule.status(), x, settings + 51, ACCENT, contentWidth);
                if (!compact()) {
                    text(g, "Manual clicks keep their current behavior.", x, settings + 73, MUTED, contentWidth);
                    var stats = LungeTestClient.stats();
                    text(g, "Clicks / starts: " + stats.attempts() + " / " + stats.started(), x, settings + 95, TEXT, contentWidth);
                    text(g, LungeTestClient.activity(minecraft), x, settings + 113, MUTED, contentWidth);
                }
            }
            case SPEAROMETER -> {
                if (!compact()) {
                    var stats = LungeTestClient.stats();
                    text(g, decimal(stats.speed()) + " b/s  /  Peak " + decimal(stats.peakSpeed()) + " b/s",
                            x, settings + 85, ACCENT, contentWidth);
                    text(g, "Distance: " + decimal(stats.distance()) + " blocks", x, settings + 102, TEXT, contentWidth);
                }
            }
            case PLAYER_HIGHLIGHTS, TRACERS -> {
                g.fill(x, settings + 48, x + 14, settings + 64, selectedColor());
                g.fill(x, settings + 47, x + 14, settings + 48, 0x99FFFFFF);
                if (!compact()) {
                    text(g, "Visible through blocks. Loaded players only.", x, settings + 84, MUTED, contentWidth);
                    text(g, "Choose a preset or use #RRGGBB.", x, settings + 101, TEXT, contentWidth);
                    text(g, "Colors and range save automatically.", x, settings + 118, MUTED, contentWidth);
                }
            }
            case AUTO_CLUTCH -> {
                int detailOffset = tellyMode() ? 23 : 0;
                text(g, AutoClutchModule.status(), x, settings + 29 + detailOffset, ACCENT, contentWidth);
                if (!compact() || !tellyMode()) text(g,
                        AutoClutchModule.mode() == AutoClutchModule.Mode.TELLY_ONLY || autoTellyMode()
                                ? "Keep bridge blocks in your hotbar." : "Keep blocks + a filled bucket ready.",
                        x, settings + 48 + detailOffset, MUTED, contentWidth);
                if (!compact()) {
                    text(g, autoTellyMode() ? "Jump, look back, place, face forward." : tellyMode()
                            ? "Telly mode keeps placing during jumps." : "Blocks catch edges while bridging.",
                            x, settings + 77 + detailOffset, TEXT, contentWidth);
                    text(g, autoTellyMode() ? "Face your path. Move or turn to stop."
                                    : AutoClutchModule.mode() == AutoClutchModule.Mode.TELLY_ONLY
                                    ? "Bridge assistance without water recovery." : "Water catches longer falls.",
                            x, settings + 96 + detailOffset, MUTED, contentWidth);
                }
            }
            case SHIELD_DRAINER -> {
                text(g, ShieldDrainerModule.status(), x, settings + 30, ACCENT, contentWidth);
                text(g, "Timed crosshair combat trials.", x, settings + 50, MUTED, contentWidth);
                if (!compact()) text(g, "Rates, delays and logs in Settings.", x, settings + 80, TEXT, contentWidth);
            }
            case COORDINATES -> {
                text(g, "Your position, always in view.", x, settings + 3, ACCENT, contentWidth);
                text(g, "X / Y / Z and facing direction.", x, settings + 21, MUTED, contentWidth);
                text(g, "Updates as you travel.", x, settings + 43, TEXT, contentWidth);
                if (!compact()) text(g, "Bind a key for a quick HUD toggle.", x, settings + 73, MUTED, contentWidth);
            }
            case ARMOR_ALERTS -> {
                text(g, "Low durability on equipped armor.", x, settings + 33, TEXT, contentWidth);
                text(g, "Helmet, chest, leggings and boots.", x, settings + 54, MUTED, contentWidth);
                if (!compact()) text(g, "A quiet alert above your hotbar.", x, settings + 80, MUTED, contentWidth);
            }
            case FULLBRIGHT -> {
                text(g, "Brightens the vanilla lightmap.", x, settings + 3, ACCENT, contentWidth);
                text(g, "Your saved brightness stays unchanged.", x, settings + 24, TEXT, contentWidth);
                text(g, "Shader packs may override lighting.", x, settings + 46, MUTED, contentWidth);
            }
            case SESSION_HUD -> {
                text(g, "Active time: " + SessionHudModule.timeLabel(), x, settings + 33, TEXT, contentWidth);
                text(g, "Travel: " + SessionHudModule.distanceLabel(), x, settings + 54, ACCENT, contentWidth);
                if (!compact()) text(g, "Pauses and teleports do not add travel.", x, settings + 80, MUTED, contentWidth);
            }
            case PERFORMANCE_HUD -> {
                text(g, PerformanceHudModule.fpsLabel(minecraft), x, settings + 8, TEXT, contentWidth);
                text(g, PerformanceHudModule.pingLabel(minecraft), x, settings + 31, ACCENT, contentWidth);
                text(g, "Live FPS and reported server latency.", x, settings + 55, MUTED, contentWidth);
                if (!compact()) text(g, "No background polling or packet spam.", x, settings + 80, MUTED, contentWidth);
            }
            case ZOOM -> {
                text(g, "Bring distant details closer.", x, settings + 31, ACCENT, contentWidth);
                text(g, "Toggle again to restore your view.", x, settings + 51, MUTED, contentWidth);
                if (!compact()) text(g, "Try 2x, 4x or 8x magnification.", x, settings + 83, TEXT, contentWidth);
            }
            case AUTO_REJOIN -> {
                text(g, AutoRejoinModule.status(), x, settings + 53, ACCENT, contentWidth);
                if (!compact()) {
                    text(g, "Retries after 5s. Up to 6 attempts.", x, settings + 77, TEXT, contentWidth);
                    text(g, "Leaving deliberately stops retries.", x, settings + 98, MUTED, contentWidth);
                }
            }
            case AUTO_STUNSLAM -> {
                text(g, CombatAssistModule.status(CombatAssistModule.Feature.STUNSLAM), x, settings + 3, ACCENT, contentWidth);
                text(g, "Click a raised shield. Axe, then mace.", x, settings + 24, TEXT, contentWidth);
                text(g, "Keep both tools in your hotbar.", x, settings + 45, MUTED, contentWidth);
                if (!compact()) text(g, "Automatic timing. No extra use key.", x, settings + 75, MUTED, contentWidth);
            }
            case AUTO_MACE -> {
                if (!compact()) {
                    text(g, CombatAssistModule.status(combatFeature()), x, settings + 73, ACCENT, contentWidth);
                    text(g, selected == AzeiumModules.Module.AUTO_MACE
                            ? fallingSmash() ? "Falling click: switch and hit with mace."
                                : swapOnly() ? "Any entity. Any held item. Empty hand too."
                                : "Hit a hostile mob for a charged follow-up."
                            : "Hit a hostile mob to start a combo.",
                            x, settings + 93, TEXT, contentWidth);
                    text(g, selected == AzeiumModules.Module.AUTO_MACE && swapOnly()
                            ? "Instant slot swap. You control every click."
                            : fallingSmash() ? "No extra click. Normal smash conditions." : "Keep the required tools in your hotbar.",
                            x, settings + 113, MUTED, contentWidth);
                } else if (noticeTicks <= 0) {
                    text(g, CombatAssistModule.status(combatFeature()), x, settings + 65, ACCENT, contentWidth);
                }
            }
        }
        if (noticeTicks > 0 && notice != null) text(g, notice, x, top + h - 34,
                noticeError ? 0xFFFF8BA2 : ACCENT, contentWidth);
        super.render(g, mouseX, mouseY, delta);
    }

    private void text(GuiGraphics g, String value, int x, int y, int color, int maxWidth) {
        UiTypography.draw(g, font, UiTypography.fit(font, value, maxWidth), x, y,
                fade(color, visibility * tabVisibility), false);
    }
    private static int glass(int rgb, float factor) {
        int alpha = Math.clamp(Math.round(LungeTestClient.uiOpacity() * 2.55F * factor), 0, 255);
        return alpha << 24 | rgb & 0xFFFFFF;
    }
    private static float ease(float value) { return 1 - (float) Math.pow(1 - value, 3); }
    private static int fade(int color, float alpha) {
        return Math.clamp(Math.round((color >>> 24) * alpha), 0, 255) << 24 | color & 0xFFFFFF;
    }
    private static int blend(int from, int to, float amount) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = from >>> shift & 255, b = to >>> shift & 255;
            result |= Math.round(a + (b - a) * amount) << shift;
        }
        return result;
    }
    private static void rounded(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        roundedGradient(g, x, y, w, h, radius, color, color);
    }
    static void roundedGradient(GuiGraphics g, int x, int y, int w, int h, int radius, int top, int bottom) {
        UiGlass.gradient(g, x, y, w, h, radius, top, bottom);
    }
    private int navTop() { return panelTop() + (compact() ? 47 : 64); }
    private int navStep() {
        int available = panelTop() + panelHeight() - 74 - navTop();
        return Math.min(34, available / MODULES_PER_PAGE);
    }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private boolean compact() { return panelHeight() < 280; }
    @Override protected boolean capturingBind() { return capturingKeybind(); }
    @Override protected int windowWidth() { return panelWidth(); }
    @Override protected int windowHeight() { return panelHeight(); }
    private int panelWidth() { return Math.min(580, Math.min(width - 16, Math.max(304, (int)(width * .82)))); }
    private int panelHeight() { return Math.min(342, Math.min(height - 16, Math.max(224, (int)(height * .82)))); }
    private int sidebarWidth() { return panelWidth() >= 420 ? 148 : 106; }
    private int panelLeft() { return windowLeft(); }
    private int panelTop() { return windowTop(); }
    private int contentLeft() { return panelLeft() + sidebarWidth() + 14; }
    private int contentWidth() { return panelWidth() - sidebarWidth() - 28; }
    private int settingsY() { return panelTop() + (compact() ? 123 : 142); }

    private static final class AzeiumButton extends Button implements UiGlass.Hinted {
        private final UiGlass.Hint hint = new UiGlass.Hint();
        @Override public UiGlass.Hint hint() { return hint; }
        @Override public void setTooltip(Tooltip tooltip) { hint.set(tooltip); }
        @Override public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            super.updateWidgetNarration(output); hint.narrate(output);
        }
        private final AzeiumModules.Module module;
        private final Integer swatch;
        private float hover;
        private long lastDrawAt = System.nanoTime();
        AzeiumButton(int x, int y, int w, int h, String label, OnPress callback, AzeiumModules.Module module, Integer swatch) {
            super(x, y, w, h, Component.literal(label), callback, DEFAULT_NARRATION);
            this.module = module; this.swatch = swatch;
        }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            var client = Minecraft.getInstance();
            boolean selectedTab = module != null && client.screen instanceof LungeTestScreen screen && screen.selected == module;
            boolean focused = isHoveredOrFocused();
            long now = System.nanoTime();
            float elapsed = Math.min(.1F, (now - lastDrawAt) / 1_000_000_000F);
            lastDrawAt = now;
            hover += ((focused && active ? 1 : 0) - hover) * (1 - (float) Math.exp(-elapsed * 20));
            float reveal = client.screen instanceof LungeTestScreen screen ? screen.visibility : 1;
            boolean primary = client.screen instanceof LungeTestScreen screen && this == screen.toggle;
            if (module == null) {
                UiGlass.button(g, getX(), getY(), getWidth(), getHeight(), hover, primary, active, reveal);
            } else if (hover > .01F) {
                rounded(g, getX(), getY(), getWidth(), getHeight(), 6,
                        fade(ThemeManager.alpha(ThemeManager.first(), Math.round(hover * 23)), reveal));
            }
            if (swatch != null) rounded(g, getX() + 5, getY() + getHeight() - 4, getWidth() - 10, 2, 1, fade(swatch, reveal));
            String label = getMessage().getString();
            int color = fade(active ? TEXT : MUTED, reveal);
            if (module != null) {
                int dot = module.enabled() ? 0xFF7AEAC4 : 0xFF69758B;
                if (module.enabled()) rounded(g, getX() + getWidth() - 12, getY() + getHeight()/2 - 4, 8, 8, 4, fade(0x257AEAC4, reveal));
                rounded(g, getX() + getWidth() - 10, getY() + getHeight()/2 - 2, 4, 4, 2, fade(dot, reveal));
                float scale = Math.min(1, (getWidth() - 21.0F) / Math.max(1, UiTypography.width(client.font, label)));
                g.pose().pushMatrix();
                g.pose().translate(getX() + 7, getY() + (getHeight() - 8.0F * scale) / 2.0F);
                g.pose().scale(scale, scale);
                UiTypography.draw(g, client.font, label, 0, 0, color, false);
                g.pose().popMatrix();
            } else if (primary && client.screen instanceof LungeTestScreen screen) {
                int switchX = getX() + getWidth() - 30, switchY = getY() + (getHeight() - 10) / 2;
                rounded(g, switchX, switchY, 22, 10, 5,
                        fade(screen.selected.enabled() ? ThemeManager.alpha(ThemeManager.second(), 160) : 0x705B6578, reveal));
                rounded(g, switchX + (screen.selected.enabled() ? 13 : 2), switchY + 2, 6, 6, 3, fade(TEXT, reveal));
                float scale = Math.min(1, (getWidth() - 45f) / Math.max(1, UiTypography.width(client.font, label)));
                g.pose().pushMatrix();
                g.pose().translate(getX() + 9, getY() + (getHeight() - 8f * scale) / 2);
                g.pose().scale(scale, scale);
                UiTypography.draw(g, client.font, label, 0, 0, color, false);
                g.pose().popMatrix();
            } else {
                float scale = Math.min(1, (getWidth() - 8.0F) / Math.max(1, UiTypography.width(client.font, label)));
                g.pose().pushMatrix();
                g.pose().translate(getX() + getWidth()/2.0F, getY() + (getHeight() - 8.0F * scale)/2.0F);
                g.pose().scale(scale, scale);
                UiTypography.center(g, client.font, label, 0, 0, color);
                g.pose().popMatrix();
            }
        }
    }
}
