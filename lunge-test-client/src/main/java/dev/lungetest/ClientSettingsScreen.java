package dev.lungetest;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Themes, client preferences and explicitly configured finite combat trials. */
public final class ClientSettingsScreen extends AzeiumScreen {
    public enum Page { CLIENT, THEMES, TESTS }
    public enum Trial { SHIELD, MACE, STUN }
    private final Screen parent;
    private Page page;
    private Trial trial = Trial.SHIELD;
    private boolean capturing;
    private String notice = "";
    private EditBox first, second;
    private Button bind;
    private final long openedAt = System.nanoTime();
    public ClientSettingsScreen(Screen parent, Page page) {
        super(Component.literal("Azeium settings"));
        this.parent = parent;
        this.page = page;
    }
    public Page page() { return page; }
    public Trial trial() { return trial; }
    public void show(Page next) { page = next; capturing = false; clearWidgets(); init(); }
    public boolean capturingKeybind() { return capturing; }
    private int left() { return windowLeft() + 8; }
    private int panelWidth() { return windowWidth() - 16; }
    @Override protected int windowWidth() { return Math.min(536, Math.min(width - 8, Math.max(312, (int)(width * .85)))); }
    @Override protected int windowHeight() { return Math.min(280, Math.min(height, Math.max(240, (int)(height * .85)))); }
    @Override protected boolean capturingBind() { return capturing; }
    private int top() { return windowTop(); }
    private int bottom() { return top() + windowHeight(); }
    private Button button(int x, int y, int w, String text, Button.OnPress pressed) {
        return addRenderableWidget(new GlassButton(x, top() + y, w, 18, text, pressed));
    }
    @Override protected void init() {
        int x = left(), w = panelWidth(), column = (w - 8) / 3;
        for (Page item : Page.values()) {
            var b = button(x + item.ordinal() * (column + 4), 36, column,
                    item == Page.CLIENT ? "Client" : item == Page.THEMES ? "Themes" : "Test scenarios", ignored -> show(item));
            b.active = item != page;
        }
        switch (page) {
            case CLIENT -> {
                bind = button(x, 64, w, "GUI bind: " + AzeiumKeybinds.menuLabel(), ignored -> {
                    capturing = true;
                    bind.setMessage(Component.literal("Press a supported key · Esc cancels"));
                });
                bind.setTooltip(Tooltip.create(UiTypography.text("Click, then press a keyboard key. Conflicting module/use keys are rejected. Saved across sessions.")));
                button(x, 86, w, "Glass opacity: " + LungeTestClient.uiOpacity() + "%", ignored -> {
                    LungeTestClient.cycleUiOpacity(); show(page);
                });
                button(x, 108, w, "New log session", ignored -> { CombatTestLog.begin("Manual"); notice = "Logs: logs/azeium-tests"; });
                button(x, 130, w, "Flush action log", ignored -> { CombatTestLog.flush(); notice = "Buffered events saved"; });
            }
            case THEMES -> {
                int cell = (w - 8) / 3;
                var presets = ThemeManager.Theme.values();
                for (int index = 0; index < presets.length - 1; index++) {
                    var theme = presets[index];
                    var preset = button(x + index % 3 * (cell + 4), 64 + index / 3 * 22, cell, theme.label, ignored -> {
                        ThemeManager.select(theme); notice = theme.label + " theme saved";
                    });
                    ((GlassButton) preset).palette = theme;
                }
                first = addRenderableWidget(new UiTextField(font, x, top() + 114, (w - 6) / 2, 18, Component.literal("Gradient colour one")));
                second = addRenderableWidget(new UiTextField(font, x + (w + 6) / 2, top() + 114, (w - 6) / 2, 18, Component.literal("Gradient colour two")));
                first.setMaxLength(7); second.setMaxLength(7);
                first.setValue(HighlightColors.format(ThemeManager.first()));
                second.setValue(HighlightColors.format(ThemeManager.second()));
                button(x, 138, w, "Apply custom gradient", ignored -> {
                    var a = HighlightColors.parse(first.getValue());
                    var b = HighlightColors.parse(second.getValue());
                    if (a.isEmpty() || b.isEmpty()) notice = "Use two colours in #RRGGBB format";
                    else { ThemeManager.custom(a.getAsInt(), b.getAsInt()); notice = "Custom theme saved"; }
                });
            }
            case TESTS -> buildTests(x, w);
        }
        if (page == Page.TESTS) {
            int cell = (w - 12) / 4;
            var start = button(x, windowHeight() - 24, cell, trialEnabled() ? "Stop" : "Start", ignored -> {
                if (trial == Trial.SHIELD) ShieldDrainerModule.setEnabled(!ShieldDrainerModule.enabled());
                else CombatAssistModule.setEnabled(feature(), !CombatAssistModule.enabled(feature()));
                show(page);
            });
            start.active = trialEnabled() || minecraft.player != null && minecraft.level != null && minecraft.getConnection() != null;
            button(x + cell + 4, windowHeight() - 24, cell, "Flag", ignored -> { CombatTestLog.mark("flag observed"); notice = "Flag annotation saved"; });
            button(x + (cell + 4) * 2, windowHeight() - 24, cell, "No flag", ignored -> { CombatTestLog.mark("no flag observed"); notice = "No-flag annotation saved"; });
            button(x + (cell + 4) * 3, windowHeight() - 24, cell, "Back", ignored -> onClose());
        } else button(x, windowHeight() - 24, w, "Back to modules", ignored -> onClose());
    }
    private boolean trialEnabled() {
        return trial == Trial.SHIELD ? ShieldDrainerModule.enabled() : CombatAssistModule.enabled(feature());
    }
    private CombatAssistModule.Feature feature() {
        return trial == Trial.MACE ? CombatAssistModule.Feature.MACE : CombatAssistModule.Feature.STUNSLAM;
    }
    private void buildTests(int x, int w) {
        int cell = (w - 8) / 3;
        for (Trial item : Trial.values()) {
            var b = button(x + item.ordinal() * (cell + 4), 62, cell,
                    item == Trial.SHIELD ? "Shield Drainer" : item == Trial.MACE ? "Auto Mace" : "Stun Slam", ignored -> {
                        trial = item; show(page);
                    });
            b.active = item != trial;
        }
        if (trial == Trial.SHIELD) {
            var p = ShieldDrainerModule.preferences();
            button(x, 82, w, "Scenario: " + (p.scenario == ShieldDrainerModule.Scenario.MACE_CLICKS ? "Mace click rate" : "Axe → mace"), ignored -> {
                p.scenario = p.scenario == ShieldDrainerModule.Scenario.MACE_CLICKS
                        ? ShieldDrainerModule.Scenario.AXE_TO_MACE : ShieldDrainerModule.Scenario.MACE_CLICKS;
                ShieldDrainerModule.settingsChanged(); show(page);
            });
            slider(x, 102, w, "Requested clicks / second", 1, 40, () -> p.clicksPerSecond, n -> { p.clicksPerSecond = n; ShieldDrainerModule.settingsChanged(); });
            slider(x, 122, w, "Trial duration (seconds)", 1, 60, () -> p.durationSeconds, n -> { p.durationSeconds = n; ShieldDrainerModule.settingsChanged(); });
            slider(x, 142, w, "Reaction delay (ticks)", 0, 200, () -> p.reactionTicks, n -> { p.reactionTicks = n; ShieldDrainerModule.settingsChanged(); });
            slider(x, 162, w, "Axe/mace interval (ticks)", 1, 200, () -> p.switchTicks, n -> { p.switchTicks = n; ShieldDrainerModule.settingsChanged(); });
            button(x, 182, w, p.shieldOnly ? "Targets: blocking shields" : "Targets: living entities", ignored -> {
                p.shieldOnly = !p.shieldOnly; ShieldDrainerModule.settingsChanged(); show(page);
            });
        } else if (trial == Trial.MACE) {
            var f = feature();
            var p = CombatAssistModule.preferences(f);
            button(x, 82, w, "Scenario: " + CombatAssistModule.maceActionLabel(), ignored -> {
                CombatAssistModule.cycleMaceAction();
                show(page);
            });
            button(x, 110, w, CombatAssistModule.smashReachLabel(), ignored -> {
                CombatAssistModule.cycleSmashReach(); show(page);
            });
            button(x, 134, w, "Return to previous slot: Always", ignored -> {}).active = false;
            button(x, 160, w, "Activation: " + CombatAssistModule.activation(f).label(), ignored -> {
                CombatAssistModule.cycleActivation(f); show(page);
            });
            button(x, 182, w, "New comparison log", ignored -> { CombatTestLog.begin(trial.name()); notice = "New trace; hit a target to begin"; });
        }
    }
    private void slider(int x, int y, int w, String label, int min, int max, IntSupplier get, IntConsumer set) {
        addRenderableWidget(new ValueSlider(x, top() + y, w, label, min, max, get.getAsInt(), set));
    }
    public boolean handleCapturedKey(KeyEvent event) {
        if (!capturing) return false;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { capturing = false; notice = "Binding cancelled"; }
        else if (AzeiumKeybinds.bindMenu(event.key())) { capturing = false; notice = "GUI bind saved"; }
        else notice = "Choose an unused supported keyboard key";
        if (!capturing) bind.setMessage(Component.literal("GUI bind: " + AzeiumKeybinds.menuLabel()));
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) { return handleCapturedKey(event) || super.keyPressed(event); }
    @Override public void onClose() { CombatTestLog.flush(); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) {
        if (minecraft.level == null) renderPanorama(g, delta);
        g.fillGradient(0, 0, width, height, ThemeManager.alpha(UiGlass.surfaceTop(), 25), ThemeManager.alpha(UiGlass.surfaceBottom(), 40));
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = left(), w = panelWidth();
        float reveal = 1 - (float) Math.pow(1 - Math.clamp((System.nanoTime() - openedAt) / 180_000_000F, 0, 1), 3);
        UiGlass.panel(g, x - 8, top(), w + 16, windowHeight(), reveal);
        UiGlass.gradient(g, x, top() + 60, w, windowHeight() - 93, 9, 0x17070B13, 0x30070B13);
        g.fill(x + 4, top() + 29, x + w - 4, top() + 30, 0x1DBCCADD);
        UiTypography.draw(g, font, "azeium", x + 4, top() + 11, ThemeManager.text(), false);
        UiTypography.draw(g, font, "/ " + (page == Page.TESTS ? "Test scenarios" : page == Page.THEMES ? "Themes" : "Client"),
                x + 47, top() + 11, ThemeManager.muted(), false);
        if (page == Page.THEMES) {
            int previewHeight = Math.min(30, bottom() - 44 - (top() + 165));
            UiGlass.gradient(g, x, top() + 165, w, previewHeight, 8,
                    ThemeManager.alpha(ThemeManager.first(), 90), ThemeManager.alpha(ThemeManager.second(), 50));
            UiGlass.rounded(g, x + 5, top() + 171, w - 10, Math.max(12, previewHeight - 12), 5, 0x850A101B);
            UiTypography.center(g, font, "Current theme: " + ThemeManager.theme().label, x + w / 2, top() + 176, ThemeManager.text());
        } else if (page == Page.TESTS && trial == Trial.STUN) {
            UiTypography.draw(g, font, "Automatic axe → mace", x, top() + 90, ThemeManager.text(), false);
            UiTypography.draw(g, font, "Click a hostile mob to start.", x, top() + 114, ThemeManager.muted(), false);
            UiTypography.draw(g, font, "Keep both tools in your hotbar.", x, top() + 138, ThemeManager.muted(), false);
        } else if (page == Page.CLIENT) {
            UiTypography.draw(g, font, "Preferences save automatically.", x, top() + 160, ThemeManager.muted(), false);
            UiTypography.draw(g, font, "Traces: logs/azeium-tests", x, top() + 176, ThemeManager.muted(), false);
        }
        String status = !notice.isEmpty() ? notice : page == Page.TESTS
                ? trial == Trial.SHIELD ? ShieldDrainerModule.status() : trial == Trial.STUN
                        ? CombatAssistModule.status(CombatAssistModule.Feature.STUNSLAM) : "Manual hit starts the configured sequence" : "";
        UiTypography.draw(g, font, UiTypography.fit(font, status, w), x, bottom() - 37, ThemeManager.secondary(), false);
        super.render(g, mouseX, mouseY, delta);
    }
    private static final class GlassButton extends Button implements UiGlass.Hinted {
        private final UiGlass.Hint hint = new UiGlass.Hint();
        @Override public UiGlass.Hint hint() { return hint; }
        @Override public void setTooltip(Tooltip tooltip) { hint.set(tooltip); }
        @Override public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            super.updateWidgetNarration(output); hint.narrate(output);
        }
        private ThemeManager.Theme palette;
        private float hover;
        private long lastDrawAt = System.nanoTime();
        GlassButton(int x, int y, int w, int h, String text, OnPress action) {
            super(x, y, w, h, Component.literal(text), action, DEFAULT_NARRATION);
        }
        @Override protected void renderContents(GuiGraphics g, int x, int y, float delta) {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            long now = System.nanoTime();
            hover = UiGlass.approach(hover, isHoveredOrFocused() && active ? 1 : 0, (now - lastDrawAt) / 1_000_000_000F);
            lastDrawAt = now;
            boolean selected = palette != null ? ThemeManager.theme() == palette : !active;
            UiGlass.button(g, getX(), getY(), getWidth(), getHeight(), hover, selected, active || selected, 1);
            if (palette != null) {
                UiGlass.gradient(g, getX() + 5, getY() + getHeight() - 4, getWidth() - 10, 2, 1, palette.first, palette.second);
                if (selected) UiGlass.rounded(g, getX() + getWidth() - 10, getY() + 4, 4, 4, 2, ThemeManager.secondary());
            }
            float scale = Math.min(1, (getWidth() - 8f) / Math.max(1, UiTypography.width(font, getMessage())));
            g.pose().pushMatrix();
            g.pose().translate(getX() + getWidth() / 2f, getY() + (getHeight() - 8 * scale) / 2);
            g.pose().scale(scale, scale);
            UiTypography.center(g, font, getMessage(), 0, 0, active || selected ? ThemeManager.text() : ThemeManager.muted());
            g.pose().popMatrix();
        }
    }
    public static final class ValueSlider extends AbstractSliderButton {
        private final String label;
        private final int min, max;
        private final IntConsumer set;
        ValueSlider(int x, int y, int w, String label, int min, int max, int initial, IntConsumer set) {
            super(x, y, w, 18, Component.empty(), (Math.clamp(initial, min, max) - min) / (double) (max - min));
            this.label = label; this.min = min; this.max = max; this.set = set;
            updateMessage();
        }
        public void choose(int number) { setValue((Math.clamp(number, min, max) - min) / (double) (max - min)); }
        public int number() { return min + (int) Math.round(value * (max - min)); }
        @Override protected void updateMessage() { setMessage(Component.literal(label + ": " + number())); }
        @Override protected void applyValue() { set.accept(number()); }
        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            UiGlass.rounded(g, getX(), getY(), getWidth(), getHeight(), 6, ThemeManager.alpha(UiGlass.surfaceBottom(), 155));
            int trackX = getX() + 7, trackY = getY() + getHeight() - 5, trackWidth = getWidth() - 14;
            int end = trackX + (int) Math.round(trackWidth * value);
            UiGlass.rounded(g, trackX, trackY, trackWidth, 2, 1, 0x48627389);
            UiGlass.gradient(g, trackX, trackY, Math.max(2, end - trackX), 2, 1, ThemeManager.first(), ThemeManager.second());
            UiGlass.rounded(g, end - 3, trackY - 2, 6, 6, 3,
                    isHoveredOrFocused() ? ThemeManager.secondary() : ThemeManager.text());
            UiTypography.center(g, font, getMessage(), getX() + getWidth() / 2, getY() + 2, ThemeManager.text());
        }
    }
}
