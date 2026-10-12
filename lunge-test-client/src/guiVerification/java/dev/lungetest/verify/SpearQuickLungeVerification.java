package dev.lungetest.verify;

import dev.lungetest.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

/** Actual transformed attack/keyboard dispatch and ordinary packets; transport is captured. */
public final class SpearQuickLungeVerification {
    private static int checks;
    public static void verify(Minecraft client) {
        checks = 0;
        var oldPlayer = client.player; var oldLevel = client.level; var oldMode = client.gameMode;
        var oldScreen = client.screen; var oldHit = client.hitResult;
        boolean focus = client.isWindowActive();
        int oldKey = SpearQuickLungeModule.key(), oldSlot = SpearQuickLungeModule.preferredSlot();
        int oldMiss = 0;
        try {
            var unsafeField = field(Unsafe.class, "theUnsafe");
            var unsafe = (Unsafe) unsafeField.get(null);
            var connection = (CombatAssistVerification.CapturedConnection) unsafe.allocateInstance(CombatAssistVerification.CapturedConnection.class);
            connection.packets = new ArrayList<>(); connection.network = new CombatAssistVerification.CapturedNetwork();
            var player = (QuickPlayer) unsafe.allocateInstance(QuickPlayer.class);
            field(LocalPlayer.class, "connection").set(player, connection);
            player.inventory = new Inventory(player, new EntityEquipment()); player.abilities = new Abilities();
            player.cooldowns = new ItemCooldowns(); player.food = new FoodData(); player.alive = true;
            var level = (CombatAssistVerification.CombatLevel) unsafe.allocateInstance(CombatAssistVerification.CombatLevel.class);
            level.states = new HashMap<>(); level.entities = new ArrayList<>(); level.border = new WorldBorder(); level.random = RandomSource.create(19);
            field(Level.class, "threadSafeRandom").set(level, RandomSource.create(20));
            player.fixtureLevel = level;
            field(Entity.class, "position").set(player, Vec3.ZERO); field(Entity.class, "blockPosition").set(player, BlockPos.ZERO);
            client.player = player; client.level = level; client.gameMode = new MultiPlayerGameMode(client, connection);
            client.screen = null; client.hitResult = BlockHitResult.miss(Vec3.ZERO, Direction.DOWN, BlockPos.ZERO);
            field(Minecraft.class, "windowActive").setBoolean(client, true);
            oldMiss = field(Minecraft.class, "missTime").getInt(client); field(Minecraft.class, "missTime").setInt(client, 0);
            player.inventory.setItem(0, new ItemStack(Items.DIAMOND_SWORD)); player.inventory.setItem(3, new ItemStack(Items.DIAMOND_SPEAR));
            player.inventory.setSelectedSlot(0);
            SpearQuickLungeModule.initialize(GLFW.GLFW_KEY_UNKNOWN, -1);
            var menu = new LungeTestScreen(); client.setScreen(menu);
            button(menu, "Quick-lunge key: ").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            require(menu.capturingKeybind(), "Spear action has independent capture");
            key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_PRESS);
            require(SpearQuickLungeModule.key() == GLFW.GLFW_KEY_F18 && !menu.capturingKeybind(), "Actual keyboard capture saves quick-lunge key");
            require(!AzeiumKeybinds.bindMenu(GLFW.GLFW_KEY_F18), "Menu rejects spear action key");
            require(!AzeiumKeybinds.bind(AzeiumModules.Module.COORDINATES, GLFW.GLFW_KEY_F18), "Module bind rejects spear action key");
            require(!AutoRejoinModule.bindKey(GLFW.GLFW_KEY_F18), "Rejoin rejects spear action key");
            require(!CombatAssistModule.bindActivationKey(CombatAssistModule.Feature.MACE, GLFW.GLFW_KEY_F18), "Combat activation rejects spear action key");
            button(menu, "Quick-lunge key: ").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            key(client, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_PRESS);
            require(SpearQuickLungeModule.key() == GLFW.GLFW_KEY_F18 && !menu.capturingKeybind(), "Cancel keeps saved action bind");
            client.setScreen(null);
            // Mouse recapture intentionally sets missTime=10000. A normal gameplay tick
            // clears it through continueAttack(false); this fixture does not run that tick.
            field(Minecraft.class, "missTime").setInt(client, 0);
            field(net.minecraft.world.entity.LivingEntity.class, "attackStrengthTicker").setInt(player, 200);
            key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_PRESS); SpearQuickLungeModule.tick(client);
            require(stabs(connection) == 1 && player.inventory.getSelectedSlot() == 0,
                    "Spear shortcut works with module OFF and restores the original slot");
            connection.packets.clear(); player.lunges = 0;
            LungeTestClient.toggle(client);
            key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_PRESS);
            require(stabs(connection) == 1, "Input dispatches the spear action immediately");
            SpearQuickLungeModule.tick(client);
            require(stabs(connection) == 1 && player.inventory.getSelectedSlot() == 0, "One press jabs once and restores original sword");
            require(connection.packets.size() == 3 && connection.packets.get(0) instanceof ServerboundSetCarriedItemPacket a && a.getSlot() == 3
                    && connection.packets.get(1) instanceof ServerboundPlayerActionPacket stab && stab.getAction() == ServerboundPlayerActionPacket.Action.STAB
                    && connection.packets.get(2) instanceof ServerboundSetCarriedItemPacket b && b.getSlot() == 0, "Vanilla spear selection precedes STAB; vanilla slot restoration follows it");
            require(player.lunges == 1 && player.getDeltaMovement().equals(Vec3.ZERO), "Vanilla lunge callback runs; unenchanted spear has no invented movement");
            key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_REPEAT); SpearQuickLungeModule.tick(client);
            for (int i = 0; i < 20; i++) SpearQuickLungeModule.tick(client);
            require(stabs(connection) == 1, "Held repeats and idle ticks cannot auto attack");
            player.inventory.setItem(0, ItemStack.EMPTY);
            key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_PRESS); SpearQuickLungeModule.tick(client);
            require(stabs(connection) == 2 && player.inventory.getSelectedSlot() == 0 && player.getMainHandItem().isEmpty(), "Empty hand is restored too");
            player.inventory.setSelectedSlot(3);
            key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_PRESS); SpearQuickLungeModule.tick(client);
            require(stabs(connection) == 3 && player.inventory.getSelectedSlot() == 3, "Already holding spear retains its selected slot");
            player.inventory.setSelectedSlot(0);
            int before = stabs(connection);
            key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_PRESS); key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_PRESS);
            SpearQuickLungeModule.tick(client);
            require(stabs(connection) == before + 2 && player.inventory.getSelectedSlot() == 0, "Two deliberate presses between ticks remain two separate jabs");
            before += 2;
            for (String guard : new String[]{"menu", "focus", "death", "using", "spectator", "missing", "cooldown"}) {
                switch (guard) {
                    case "menu" -> client.setScreen(new TitleScreen());
                    case "focus" -> field(Minecraft.class, "windowActive").setBoolean(client, false);
                    case "death" -> player.alive = false;
                    case "using" -> player.using = true;
                    case "spectator" -> player.spectator = true;
                    case "missing" -> player.inventory.setItem(3, ItemStack.EMPTY);
                    case "cooldown" -> player.cooldowns.addCooldown(player.inventory.getItem(3), 10);
                }
                key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_PRESS); SpearQuickLungeModule.tick(client);
                require(stabs(connection) == before && player.inventory.getSelectedSlot() == 0, "Guard prevents action and slot changes: " + guard);
                client.setScreen(null); field(Minecraft.class, "missTime").setInt(client, 0);
                field(Minecraft.class, "windowActive").setBoolean(client, true);
                player.alive = true; player.using = false; player.spectator = false;
                player.inventory.setItem(3, new ItemStack(Items.DIAMOND_SPEAR)); player.cooldowns = new ItemCooldowns();
            }
            client.setScreen(new TitleScreen()); key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_PRESS); SpearQuickLungeModule.tick(client); client.setScreen(null);
            require(stabs(connection) == before, "Opening a menu cancels queued action");
            var savedLevel = client.level; client.level = null; key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_PRESS); SpearQuickLungeModule.tick(client); client.level = savedLevel;
            require(stabs(connection) == before, "Disconnect cancels queued action without slot changes");
            field(Minecraft.class, "missTime").setInt(client, 5); key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_PRESS); SpearQuickLungeModule.tick(client);
            require(stabs(connection) == before && player.inventory.getSelectedSlot() == 0, "A vanilla-blocked jab still restores the original slot");
            field(Minecraft.class, "missTime").setInt(client, 0);
            SpearQuickLungeModule.initialize(GLFW.GLFW_KEY_F18, 5);
            key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_PRESS); SpearQuickLungeModule.tick(client);
            require(stabs(connection) == before, "An explicit empty spear slot does not choose another spear");
            player.inventory.setItem(5, new ItemStack(Items.IRON_SPEAR));
            key(client, GLFW.GLFW_KEY_F18, GLFW.GLFW_PRESS); SpearQuickLungeModule.tick(client);
            require(stabs(connection) == before + 1 && player.inventory.getSelectedSlot() == 0, "Configured hotbar slot jabs and restores");
            client.setScreen(new LungeTestScreen());
            var mouseMenu = (LungeTestScreen) client.screen;
            button(mouseMenu, "Quick-lunge key: ").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            var mouseButton = new net.minecraft.client.input.MouseButtonInfo(4, 0);
            var mouseHandler = client.mouseHandler.getClass().getDeclaredMethod("onButton", long.class,
                    net.minecraft.client.input.MouseButtonInfo.class, int.class);
            mouseHandler.setAccessible(true);
            mouseHandler.invoke(client.mouseHandler, client.getWindow().handle(), mouseButton, GLFW.GLFW_PRESS);
            require(!mouseMenu.capturingKeybind() && SpearQuickLungeModule.key() == SpearQuickLungeModule.mouseCode(4),
                    "Actual mouse hook captures a side-button bind");
            SpearQuickLungeModule.initialize(SpearQuickLungeModule.key(), -1);
            require(SpearQuickLungeModule.keyLabel().equals("Mouse 5"), "Saved mouse bind survives initialization");
            client.setScreen(null); field(Minecraft.class, "missTime").setInt(client, 0);
            long mouseBefore = stabs(connection);
            mouseHandler.invoke(client.mouseHandler, client.getWindow().handle(), mouseButton, GLFW.GLFW_PRESS);
            SpearQuickLungeModule.tick(client);
            mouseHandler.invoke(client.mouseHandler, client.getWindow().handle(), mouseButton, GLFW.GLFW_RELEASE);
            SpearQuickLungeModule.tick(client);
            require(stabs(connection) == mouseBefore + 1 && player.inventory.getSelectedSlot() == 0,
                    "Mouse press jabs once and returns; release does not attack");
            require(!SpearQuickLungeModule.bindKey(SpearQuickLungeModule.mouseCode(8)), "Unsupported mouse buttons are rejected");
            require(!SpearQuickLungeModule.bindKey(AzeiumKeybinds.menuKey()), "Action key rejects existing menu bind");
            client.setScreen(new LungeTestScreen()); var clearMenu = (LungeTestScreen) client.screen;
            button(clearMenu, "Quick-lunge key: ").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)); key(client, GLFW.GLFW_KEY_DELETE, GLFW.GLFW_PRESS);
            require(SpearQuickLungeModule.key() == GLFW.GLFW_KEY_UNKNOWN && !clearMenu.capturingKeybind(), "Delete clears spear action key through real GUI capture");
            int oldReturn = LungeTestClient.spearReturnTicks();
            LungeTestClient.setSpearReturnTicks(4);
            boolean previousLegit = LungeTestClient.legitMode();
            if (!previousLegit) LungeTestClient.toggleLegitMode();
            try {
                client.setScreen(null);
                SpearQuickLungeModule.initialize(SpearQuickLungeModule.mouseCode(4), -1);
                player.inventory.setSelectedSlot(0);
                field(Minecraft.class, "missTime").setInt(client, 0);
                long visibleBefore = stabs(connection);
                mouseHandler.invoke(client.mouseHandler, client.getWindow().handle(), mouseButton, GLFW.GLFW_PRESS);
                require(stabs(connection) == visibleBefore + 1, "Spear shortcut dispatches during the actual mouse press without waiting for END tick");
                SpearQuickLungeModule.tick(client);
                require(stabs(connection) == visibleBefore + 1 && LungeTestClient.isSpear(player.getMainHandItem()), "Legit spear shortcut visibly selects the actual spear after its jab");
                for (int i = 0; i < 3; i++) dev.lungetest.VisibleSwapReturn.tick(client);
                require(LungeTestClient.isSpear(player.getMainHandItem()), "Legit spear selection remains visible for three intervening ticks");
                dev.lungetest.VisibleSwapReturn.tick(client);
                require(player.inventory.getSelectedSlot() == 0, "Legit spear shortcut returns the original slot after four ticks");
                LungeTestClient.setSpearReturnTicks(0);
                mouseHandler.invoke(client.mouseHandler, client.getWindow().handle(), mouseButton, GLFW.GLFW_PRESS);
                require(stabs(connection) == visibleBefore + 2 && player.inventory.getSelectedSlot() == 0
                                && !dev.lungetest.VisibleSwapReturn.pending("spear"),
                        "Zero-tick spear return selects, stabs and restores during the same input event even with Legit mode ON");
            } finally {
                dev.lungetest.VisibleSwapReturn.finishNow();
                if (LungeTestClient.legitMode() != previousLegit) LungeTestClient.toggleLegitMode();
                LungeTestClient.setSpearReturnTicks(oldReturn);
            }
            LungeTestClient.LOG.info("QUICK_LUNGE_VERIFIED: {} checks; transformed input/attack, one STAB per press, ordinary select-STAB-return order, empty hand/selected spear, slot preference, conflicts and cancellation guards; no network transport", checks);
        } catch (ReflectiveOperationException e) { throw new AssertionError("Spear shortcut verification failed", e); }
        finally {
            SpearQuickLungeModule.cancel(); LungeTestClient.disable(); SpearQuickLungeModule.initialize(oldKey, oldSlot);
            client.player = oldPlayer; client.level = oldLevel; client.gameMode = oldMode; client.screen = oldScreen; client.hitResult = oldHit;
            try { field(Minecraft.class, "windowActive").setBoolean(client, focus); field(Minecraft.class, "missTime").setInt(client, oldMiss); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
    }
    private static int stabs(CombatAssistVerification.CapturedConnection connection) {
        return (int) connection.packets.stream().filter(p -> p instanceof ServerboundPlayerActionPacket a && a.getAction() == ServerboundPlayerActionPacket.Action.STAB).count();
    }
    private static Button button(LungeTestScreen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow();
    }
    private static void key(Minecraft client, int code, int action) throws ReflectiveOperationException {
        var method = client.keyboardHandler.getClass().getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class); method.setAccessible(true);
        method.invoke(client.keyboardHandler, client.getWindow().handle(), action, new KeyEvent(code, 0, 0));
        method.invoke(client.keyboardHandler, client.getWindow().handle(), GLFW.GLFW_RELEASE, new KeyEvent(code, 0, 0));
    }
    private static Field field(Class<?> type, String name) throws ReflectiveOperationException { var f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    private static void require(boolean value, String text) { if (!value) throw new AssertionError(text); checks++; }
    public static final class QuickPlayer extends LocalPlayer {
        Inventory inventory; Abilities abilities; ItemCooldowns cooldowns; FoodData food;
        net.minecraft.client.multiplayer.ClientLevel fixtureLevel; boolean alive, using, spectator; int lunges;
        private QuickPlayer() { super(null, null, null, null, null, null, false); }
        @Override public Inventory getInventory() { return inventory; }
        @Override public Abilities getAbilities() { return abilities; }
        @Override public ItemCooldowns getCooldowns() { return cooldowns; }
        @Override public FoodData getFoodData() { return food; }
        @Override public ItemStack getMainHandItem() { return inventory.getSelectedItem(); }
        @Override public ItemStack getItemInHand(InteractionHand hand) { return hand == InteractionHand.MAIN_HAND ? getMainHandItem() : ItemStack.EMPTY; }
        @Override public Level level() { return fixtureLevel; }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean isDeadOrDying() { return !alive; }
        @Override public boolean isUsingItem() { return using; }
        @Override public boolean isHandsBusy() { return false; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public double getAttributeValue(Holder<Attribute> attribute) { return attribute.value().getDefaultValue(); }
        @Override public Vec3 getDeltaMovement() { return Vec3.ZERO; }
        @Override public void swing(InteractionHand hand) { }
        @Override public void playSound(SoundEvent event, float volume, float pitch) { }
        @Override public void lungeForwardMaybe() { lunges++; super.lungeForwardMaybe(); }
    }
}
