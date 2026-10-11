package dev.lungetest.verify;

import dev.lungetest.AutoClutchModule;
import dev.lungetest.CombatAssistModule;
import dev.lungetest.LungeTestClient;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Uses the actual vanilla MultiPlayerGameMode attack/use prediction and packet constructors.
 * World storage and transport are isolated; no remote server is contacted. Vanilla range,
 * recharge, collision raycasts, mace smash gates and wind-charge supply consumption run unchanged.
 */
public final class CombatAssistVerification {
    private static final CombatAssistModule.Feature MACE = CombatAssistModule.Feature.MACE;
    private static final CombatAssistModule.Feature SLAM = CombatAssistModule.Feature.STUNSLAM;
    private static int checks;
    private CombatAssistVerification() { }

    public static void verify(Minecraft client) {
        var previousPlayer = client.player;
        var previousLevel = client.level;
        var previousGameMode = client.gameMode;
        var previousScreen = client.screen;
        var previousHit = client.hitResult;
        var previousCrosshair = client.crosshairPickEntity;
        boolean previousActive = client.isWindowActive();
        boolean previousClutchEnabled = AutoClutchModule.enabled();
        boolean previousClutchActed = AutoClutchModule.actedThisTick();
        var previousMaceActivation = CombatAssistModule.activation(MACE);
        var previousMaceTarget = CombatAssistModule.targetMode(MACE);
        var previousMaceAction = CombatAssistModule.maceAction();
        String previousMaceMode = CombatAssistModule.maceModeLabel();
        checks = 0;
        try {
            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (Unsafe) unsafeField.get(null);
            var connection = (CapturedConnection) unsafe.allocateInstance(CapturedConnection.class);
            connection.packets = new ArrayList<>();
            connection.network = new CapturedNetwork();
            var player = (CombatPlayer) unsafe.allocateInstance(CombatPlayer.class);
            var connectionField = LocalPlayer.class.getField("connection");
            connectionField.setAccessible(true);
            connectionField.set(player, connection);
            player.inventory = new Inventory(player, new EntityEquipment());
            player.abilities = new Abilities();
            player.cooldowns = new ItemCooldowns();
            player.alive = true;
            player.velocity = Vec3.ZERO;
            player.offhand = ItemStack.EMPTY;
            player.setYRot(37);
            player.setXRot(-12);
            set(Entity.class, player, "eyeHeight", 1.62F);
            var level = (CombatLevel) unsafe.allocateInstance(CombatLevel.class);
            level.states = new HashMap<>();
            level.entities = new ArrayList<>();
            level.border = new WorldBorder();
            level.random = RandomSource.create(24L);
            set(Level.class, level, "isClientSide", true);
            set(ClientLevel.class, level, "blockStatePredictionHandler", new BlockStatePredictionHandler());
            player.fixtureLevel = level;
            var zombie = (CombatZombie) unsafe.allocateInstance(CombatZombie.class);
            zombie.fixtureLevel = level;
            zombie.alive = true;
            zombie.setId(901);
            set(Entity.class, zombie, "eyeHeight", 1.74F);
            locate(zombie, new Vec3(.5, 3, 2.5));
            level.entities.add(zombie);
            client.player = player;
            client.level = level;
            client.gameMode = new MultiPlayerGameMode(client, connection);
            client.screen = null;
            set(Minecraft.class, client, "windowActive", true);
            client.crosshairPickEntity = zombie;
            client.hitResult = new EntityHitResult(zombie);
            player.inventory.setItem(0, new ItemStack(Items.DIAMOND_SPEAR));
            player.inventory.setItem(3, new ItemStack(Items.MACE));
            player.inventory.setItem(5, new ItemStack(Items.WIND_CHARGE, 8));
            player.inventory.setItem(7, new ItemStack(Items.DIAMOND_AXE));
            player.inventory.setSelectedSlot(0);
            locate(player, new Vec3(.5, 3, .5));
            player.velocity = new Vec3(0, -.5, 0);
            player.fallDistance = 2.5;
            recharge(player);
            require(!player.isWithinAttackRange(zombie.getBoundingBox(), 0),
                    "A close mob is correctly outside the real spear's minimum attack range");
            player.inventory.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            require(player.isWithinAttackRange(zombie.getBoundingBox(), 0) && player.hasLineOfSight(zombie),
                    "The ordinary sword starts the combat fixture within actual vanilla reach and line of sight");
            setActivation(MACE, CombatAssistModule.Activation.AUTOMATIC);
            setTarget(MACE, CombatAssistModule.TargetMode.CROSSHAIR);
            AutoClutchModule.setEnabled(false);
            if (CombatAssistModule.maceModeLabel().equals("Falling smash")) CombatAssistModule.cycleMaceMode();

            verifyShieldTrials(client, player, zombie, connection);
            verifySwapPreferences();
            setMaceAction(CombatAssistModule.MaceAction.FALLING_SMASH);
            verifyFallingSmash(client, player, level, zombie, connection);
            verifyAxeCombo(client, player, level, zombie, connection);
            verifyPlayerCombo(client, player, level, connection, unsafe);
            LungeTestClient.LOG.info("COMBAT_ASSIST_VERIFIED: {} checks; same-click falling Smash, original-slot restoration, preference migration and fixed axe-to-mace combos, hold/release, session/focus guards and shared action budget; no network transport", checks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not prepare real vanilla combat-assist fixture", e);
        } finally {
            CombatAssistModule.setEnabled(MACE, false);
            CombatAssistModule.setEnabled(SLAM, false);
            setActivation(MACE, previousMaceActivation);
            setTarget(MACE, previousMaceTarget);
            setMaceAction(previousMaceAction);
            if (!CombatAssistModule.maceModeLabel().equals(previousMaceMode)) CombatAssistModule.cycleMaceMode();
            client.player = previousPlayer;
            client.level = previousLevel;
            client.gameMode = previousGameMode;
            client.screen = previousScreen;
            client.hitResult = previousHit;
            client.crosshairPickEntity = previousCrosshair;
            AutoClutchModule.setEnabled(previousClutchEnabled);
            try {
                set(Minecraft.class, client, "windowActive", previousActive);
                set(AutoClutchModule.class, null, "acted", previousClutchActed);
            }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
    }

    private static void verifySwapPreferences() {
        var original = CombatAssistModule.snapshot();
        var gson = new Gson();
        try {
            require(new CombatAssistModule.Preferences(MACE).maceAction
                            == CombatAssistModule.MaceAction.FALLING_SMASH,
                    "New Auto Mace preferences default to same-click falling smash");
            var migrated = gson.fromJson("{\"activation\":\"AUTOMATIC\",\"fallingOnly\":true}",
                    CombatAssistModule.Preferences.class);
            require(migrated.maceAction == CombatAssistModule.MaceAction.FALLING_SMASH,
                    "Actual Gson loading of older preferences with no action field defaults to falling smash");
            CombatAssistModule.initialize(Map.of(MACE.name(), migrated));
            require(CombatAssistModule.maceAction() == CombatAssistModule.MaceAction.FALLING_SMASH
                            && CombatAssistModule.maceModeLabel().equals("Falling smash"),
                    "Migration preserves an old follow-up fall preference without arming automated attacks");
            require(!CombatAssistModule.enabled(MACE), "Loading migrated preferences never arms Auto Mace");
            var nullAction = gson.fromJson("{\"maceAction\":null}", CombatAssistModule.Preferences.class);
            CombatAssistModule.initialize(Map.of(MACE.name(), nullAction));
            require(CombatAssistModule.maceAction() == CombatAssistModule.MaceAction.FALLING_SMASH,
                    "An explicitly null saved action also migrates safely to falling smash");
            var oldSwap = gson.fromJson("{\"maceAction\":\"SWAP_ONLY\"}", CombatAssistModule.Preferences.class);
            CombatAssistModule.initialize(Map.of(MACE.name(), oldSwap));
            require(CombatAssistModule.maceAction() == CombatAssistModule.MaceAction.FALLING_SMASH,
                    "The prior release's swap-only configuration migrates to the requested falling hit behavior");
            var explicitSwap = gson.fromJson("{\"maceAction\":\"SWAP_ONLY\",\"maceRevision\":1}", CombatAssistModule.Preferences.class);
            CombatAssistModule.initialize(Map.of(MACE.name(), explicitSwap));
            require(CombatAssistModule.maceAction() == CombatAssistModule.MaceAction.FALLING_SMASH,
                    "Saved swap-only preferences always migrate to Smash");
            var followUp = gson.fromJson("{\"maceAction\":\"CHARGED_FOLLOW_UP\"}",
                    CombatAssistModule.Preferences.class);
            CombatAssistModule.initialize(Map.of(MACE.name(), followUp));
            require(CombatAssistModule.maceAction() == CombatAssistModule.MaceAction.FALLING_SMASH,
                    "Saved follow-up preferences always migrate to Smash");
        } finally {
            CombatAssistModule.initialize(original);
        }
    }

    private static void verifyFallingSmash(Minecraft client, CombatPlayer player, CombatLevel level,
            CombatZombie zombie, CapturedConnection connection) throws ReflectiveOperationException {
        var startAttack = Minecraft.class.getDeclaredMethod("startAttack");
        startAttack.setAccessible(true);
        var missTime = Minecraft.class.getDeclaredField("missTime");
        missTime.setAccessible(true);
        int previousMissTime = missTime.getInt(client);
        ItemStack original = player.inventory.getItem(0).copy();
        try {
            player.grounded = false;
            player.velocity = new Vec3(0, -.5, 0);
            player.fallDistance = 2.5;
            for (var item : List.of(Items.WIND_CHARGE, Items.DIAMOND_AXE, Items.DIAMOND_SPEAR,
                    Items.DIAMOND_SWORD, Items.WATER_BUCKET)) {
                player.inventory.setItem(0, new ItemStack(item));
                restart(client, player, connection, MACE);
                client.gameMode.tick(); // Establish the original carried slot on the fixture server first.
                connection.packets.clear();
                client.hitResult = new EntityHitResult(zombie);
                missTime.setInt(client, 0);
                set(LivingEntity.class, player, "attackStrengthTicker", 0);
                startAttack.invoke(client);
                require(attacks(connection) == 1 && player.lastAttackStack.is(Items.MACE),
                        "The actual vanilla startAttack click with " + item + " uses the mace on that same falling hit");
                require(hasSlotBeforeAttack(connection, 3, 1),
                        "Vanilla sends the selected mace slot before the same-click falling attack");
                tick(client, player, 8);
                require(attacks(connection) == 1 && player.inventory.getSelectedSlot() == 0,
                        "Falling smash restores the original slot without a delayed second attack");
                require(player.fallDistance == 2.5 && player.velocity.equals(new Vec3(0, -.5, 0)),
                        "Preparing a falling mace does not rewrite fall distance or velocity");
            }
            player.inventory.setItem(0, new ItemStack(Items.WIND_CHARGE));
            for (String condition : List.of("ground", "rising", "short fall")) {
                restart(client, player, connection, MACE);
                player.grounded = condition.equals("ground");
                player.velocity = condition.equals("rising") ? new Vec3(0, .5, 0) : new Vec3(0, -.5, 0);
                player.fallDistance = condition.equals("short fall") ? 1 : 2.5;
                client.gameMode.attack(player, zombie);
                require(attacks(connection) == 1 && player.lastAttackStack.is(Items.WIND_CHARGE)
                                && player.inventory.getSelectedSlot() == 0,
                        "The falling-smash module preserves ordinary manual attacks during " + condition);
            }
            player.grounded = false;
            player.velocity = new Vec3(0, -.5, 0);
            player.fallDistance = 2.5;
            restart(client, player, connection, MACE);
            CombatAssistModule.setEnabled(SLAM, true);
            client.gameMode.attack(player, zombie);
            tick(client, player, 1);
            recharge(player);
            tick(client, player, 2);
            require(attacks(connection) == 1 && player.lastAttackStack.is(Items.DIAMOND_AXE),
                    "Stun Slam keeps its axe-first priority over Auto Mace during a falling click");
            tick(client, player, 2);
            recharge(player);
            tick(client, player, 4);
            require(attacks(connection) == 2 && player.lastAttackStack.is(Items.MACE)
                            && player.inventory.getSelectedSlot() == 0,
                    "The falling Stun Slam finishes with the mace and restores the original slot");
            for (String guard : List.of("range", "wall", "cooldown", "missing mace", "menu", "focus")) {
                restart(client, player, connection, MACE);
                if (guard.equals("range")) locate(zombie, new Vec3(.5, 3, 8));
                if (guard.equals("wall")) level.states.put(new BlockPos(0, 4, 1), Blocks.STONE.defaultBlockState());
                if (guard.equals("cooldown")) player.cooldowns.addCooldown(player.inventory.getItem(3), 10);
                if (guard.equals("missing mace")) player.inventory.setItem(3, ItemStack.EMPTY);
                if (guard.equals("menu")) client.screen = new net.minecraft.client.gui.screens.TitleScreen();
                if (guard.equals("focus")) set(Minecraft.class, client, "windowActive", false);
                require(!CombatAssistModule.prepareManualAttack(client, zombie)
                                && player.inventory.getSelectedSlot() == 0 && attacks(connection) == 0,
                        "Pre-click mace selection respects " + guard + " without dispatching or overwriting the held slot");
                locate(zombie, new Vec3(.5, 3, 2.5));
                level.states.clear();
                player.cooldowns = new ItemCooldowns();
                player.inventory.setItem(3, new ItemStack(Items.MACE));
                client.screen = null;
                set(Minecraft.class, client, "windowActive", true);
            }
            restart(client, player, connection, MACE);
            missTime.setInt(client, 5);
            startAttack.invoke(client);
            require(attacks(connection) == 0 && player.inventory.getSelectedSlot() == 0,
                    "A blocked vanilla click restores the temporary mace without attacking");
            missTime.setInt(client, 0);
            restart(client, player, connection, MACE);
            locate(zombie, new Vec3(.5, 3, 5.8));
            require(!CombatAssistModule.prepareManualAttack(client, zombie), "Vanilla mace reach excludes distant spear targets");
            while (CombatAssistModule.smashReach() != 6) CombatAssistModule.cycleSmashReach();
            require(CombatAssistModule.prepareManualAttack(client, zombie) && player.inventory.getSelectedSlot() == 3,
                    "Six-block setting allows a visible crosshair target within six blocks");
            CombatAssistModule.finishManualAttack(client);
            require(player.inventory.getSelectedSlot() == 0, "Extended-reach preparation also returns to original slot");
            locate(zombie, new Vec3(.5, 3, 7.5));
            require(!CombatAssistModule.prepareManualAttack(client, zombie), "Six-block setting rejects farther targets");
            CombatAssistModule.cycleSmashReach();
            locate(zombie, new Vec3(.5, 3, 2.5));
            restart(client, player, connection, MACE);
            require(CombatAssistModule.prepareManualAttack(client, zombie), "Ownership fixture prepares mace");
            player.inventory.setSelectedSlot(5);
            CombatAssistModule.finishManualAttack(client);
            require(player.inventory.getSelectedSlot() == 5, "Restore preserves a subsequent user slot change");
            restart(client, player, connection, MACE);
            CombatAssistModule.setEnabled(MACE, false);
            require(!CombatAssistModule.prepareManualAttack(client, zombie) && attacks(connection) == 0,
                    "Disabled Auto Mace never prepares or dispatches a falling attack");
        } finally {
            CombatAssistModule.setEnabled(MACE, false);
            CombatAssistModule.setEnabled(SLAM, false);
            player.inventory.setItem(0, original);
            player.inventory.setSelectedSlot(0);
            player.cooldowns = new ItemCooldowns();
            player.grounded = false;
            player.velocity = new Vec3(0, -.5, 0);
            player.fallDistance = 2.5;
            missTime.setInt(client, previousMissTime);
        }
    }

    private static void verifySwapOnly(Minecraft client, CombatPlayer player, CombatLevel level,
            CombatZombie zombie, CapturedConnection connection, Unsafe unsafe) throws ReflectiveOperationException {
        setActivation(MACE, CombatAssistModule.Activation.AUTOMATIC);
        restart(client, player, connection, MACE);
        require(CombatAssistModule.status(MACE).contains("any entity"),
                "Swap-only idle status explains that any entity can trigger selection");
        tick(client, player, 10);
        require(connection.packets.isEmpty() && player.inventory.getSelectedSlot() == 0,
                "Enabling swap-only waits for a manual hit and performs no automatic selection or attack");
        client.gameMode.attack(player, zombie);
        require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 1,
                "A manual hit immediately selects the actual mace in its tail hook without waiting a tick");
        require(player.lastAttackStack.is(Items.DIAMOND_SWORD),
                "The original hit still uses the player's original held item before the mace is selected");
        require(connection.packets.stream().noneMatch(packet -> packet instanceof ServerboundSetCarriedItemPacket carried
                        && carried.getSlot() == 3),
                "Swap-only changes local selection after the original attack without crafting a held-item packet");
        client.gameMode.tick();
        require(hasAttackBeforeSlot(connection, 3),
                "The real vanilla game-mode tick synchronizes the selected mace after the original hit packet");
        require(CombatAssistModule.status(MACE).contains("you control the next hit"),
                "Swap-only status makes manual control of the next hit explicit");
        tick(client, player, 110);
        require(attacks(connection) == 1 && player.inventory.getSelectedSlot() == 3,
                "Swap-only leaves the mace selected and never adds a follow-up or timeout restoration");

        Entity[] targets = {
                zombie,
                (Entity) unsafe.allocateInstance(net.minecraft.world.entity.animal.cow.Cow.class),
                (Entity) unsafe.allocateInstance(net.minecraft.world.entity.animal.wolf.Wolf.class),
                (Entity) unsafe.allocateInstance(CombatPlayer.class),
                (Entity) unsafe.allocateInstance(net.minecraft.world.entity.decoration.ArmorStand.class),
                (Entity) unsafe.allocateInstance(net.minecraft.world.entity.vehicle.boat.Boat.class),
                (Entity) unsafe.allocateInstance(net.minecraft.world.entity.item.ItemEntity.class)
        };
        String[] descriptions = {"hostile mob", "passive cow", "wolf", "another player", "armor stand", "nonliving boat", "nonliving item entity"};
        for (int i = 0; i < targets.length; i++) {
            targets[i].setId(920 + i);
            restart(client, player, connection, MACE);
            client.gameMode.attack(player, targets[i]);
            require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 1
                            && player.lastPredictedTarget == targets[i],
                    "A real manually dispatched hit on a " + descriptions[i] + " immediately selects the mace");
            tick(client, player, 4);
            require(attacks(connection) == 1,
                    "Swap-only never adds an attack against the " + descriptions[i]);
        }
        var team = new PlayerTeam(new Scoreboard(), "swap-fixture");
        player.team = team;
        zombie.team = team;
        restart(client, player, connection, MACE);
        client.gameMode.attack(player, zombie);
        require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 1,
                "A manual hit on an allied entity still selects the mace without generating attacks");
        player.team = null;
        zombie.team = null;

        ItemStack original = player.inventory.getItem(0).copy();
        for (var item : List.of(Items.STONE, Items.WATER_BUCKET, Items.WIND_CHARGE, Items.DIAMOND_SPEAR,
                Items.DIAMOND_AXE, Items.ROTTEN_FLESH)) {
            player.inventory.setItem(0, new ItemStack(item));
            restart(client, player, connection, MACE);
            client.gameMode.attack(player, zombie);
            require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 1
                            && player.lastAttackStack.is(item),
                    "A manual hit with " + item + " preserves the original held item and immediately selects the mace");
        }
        player.inventory.setItem(0, ItemStack.EMPTY);
        restart(client, player, connection, MACE);
        client.gameMode.attack(player, zombie);
        require(player.inventory.getSelectedSlot() == 3 && player.lastAttackStack.isEmpty() && attacks(connection) == 1,
                "An empty-hand manual hit also selects the mace without inventing an initial weapon");
        player.inventory.setItem(0, original);

        restart(client, player, connection, MACE);
        player.inventory.setItem(3, ItemStack.EMPTY);
        player.inventory.setItem(12, new ItemStack(Items.MACE));
        client.gameMode.attack(player, zombie);
        require(player.inventory.getSelectedSlot() == 0 && attacks(connection) == 1,
                "A mace elsewhere in the inventory is not moved or used as a hotbar mace");
        require(CombatAssistModule.status(MACE).contains("hotbar"),
                "Missing hotbar supplies produce an explicit status");
        player.inventory.setItem(12, ItemStack.EMPTY);
        player.inventory.setItem(8, new ItemStack(Items.MACE));
        client.gameMode.attack(player, zombie);
        require(player.inventory.getSelectedSlot() == 8 && attacks(connection) == 2,
                "Hotbar scanning finds a real mace in the last hotbar slot without a fixed slot assumption");
        player.inventory.setItem(8, ItemStack.EMPTY);
        player.inventory.setItem(3, new ItemStack(Items.MACE));
        restart(client, player, connection, MACE);
        player.inventory.setSelectedSlot(3);
        client.gameMode.attack(player, zombie);
        tick(client, player, 5);
        require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 1,
                "An already held mace remains selected with exactly the player's original attack");

        restart(client, player, connection, MACE);
        player.killOnAttack = true;
        client.gameMode.attack(player, zombie);
        player.killOnAttack = false;
        require(!zombie.alive && player.inventory.getSelectedSlot() == 3 && attacks(connection) == 1,
                "A killing blow still selects the mace after the original manual attack kills its target");
        zombie.alive = true;

        restart(client, player, connection, MACE);
        player.cooldowns.addCooldown(player.inventory.getItem(3), 10);
        set(LivingEntity.class, player, "attackStrengthTicker", 0);
        player.grounded = true;
        player.velocity = Vec3.ZERO;
        player.fallDistance = 0;
        client.gameMode.attack(player, zombie);
        require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 1,
                "Uncharged attacks, grounded position and a mace item cooldown do not delay simple selection");
        require(player.getAttackStrengthScale(.5F) < .98F
                        && player.cooldowns.isOnCooldown(player.inventory.getItem(3)),
                "Immediate selection preserves genuine attack recharge and the vanilla mace cooldown");
        player.cooldowns = new ItemCooldowns();
        if (!CombatAssistModule.maceModeLabel().equals("Falling smash")) CombatAssistModule.cycleMaceMode();
        restart(client, player, connection, MACE);
        client.gameMode.attack(player, zombie);
        require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 1,
                "A saved falling-only follow-up preference does not gate swap-only selection on the ground");
        CombatAssistModule.cycleMaceMode();

        for (String situation : List.of("water", "lava", "climbing", "creative", "item use", "passenger", "elytra", "flight")) {
            restart(client, player, connection, MACE);
            player.water = situation.equals("water");
            player.lava = situation.equals("lava");
            player.climbing = situation.equals("climbing");
            player.creative = situation.equals("creative");
            player.using = situation.equals("item use");
            player.passenger = situation.equals("passenger");
            player.flying = situation.equals("elytra");
            player.abilities.flying = situation.equals("flight");
            client.gameMode.attack(player, zombie);
            require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 1,
                    "Simple manual-hit selection remains immediate during " + situation);
            player.water = player.lava = player.climbing = player.creative = player.using = player.passenger = player.flying = false;
            player.abilities.flying = false;
        }

        restart(client, player, connection, MACE);
        setActivation(MACE, CombatAssistModule.Activation.HOLD);
        client.gameMode.attack(player, zombie);
        require(player.inventory.getSelectedSlot() == 0 && attacks(connection) == 1,
                "Held activation gates immediate selection while the key is released");
        hold(client, MACE, true);
        client.gameMode.attack(player, zombie);
        require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 2,
                "Holding the configured key permits immediate selection after a deliberate hit");
        hold(client, MACE, false);
        tick(client, player, 3);
        require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 2,
                "Releasing the activation key does not restore the selected mace or add an attack");
        setActivation(MACE, CombatAssistModule.Activation.AUTOMATIC);
        restart(client, player, connection, MACE);
        client.gameMode.attack(player, zombie);
        player.inventory.setSelectedSlot(2);
        tick(client, player, 3);
        require(player.inventory.getSelectedSlot() == 2 && CombatAssistModule.enabled(MACE),
                "Manual scrolling keeps the player's choice and leaves swap-only available for the next hit");
        client.gameMode.attack(player, zombie);
        require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 2,
                "Another deliberate hit after manual scrolling can immediately select the mace again");
        CombatAssistModule.setEnabled(MACE, false);
        tick(client, player, 3);
        require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 2,
                "Turning swap-only off leaves the selected mace under manual control");

        restart(client, player, connection, MACE);
        for (int i = 0; i < 6; i++) {
            player.inventory.setSelectedSlot(0);
            client.gameMode.attack(player, zombie);
            require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == i + 1,
                    "Repeated rapid deliberate hits produce exactly one original attack and immediate selection per click");
        }
        tick(client, player, 20);
        require(attacks(connection) == 6, "Rapid manual hits cannot accumulate a delayed automatic attack queue");

        for (String guard : List.of("menu", "focus loss", "spectator", "death")) {
            restart(client, player, connection, MACE);
            if (guard.equals("menu")) client.screen = new net.minecraft.client.gui.screens.TitleScreen();
            if (guard.equals("focus loss")) set(Minecraft.class, client, "windowActive", false);
            if (guard.equals("spectator")) player.spectator = true;
            if (guard.equals("death")) player.alive = false;
            client.gameMode.attack(player, zombie);
            require(player.inventory.getSelectedSlot() == 0 && attacks(connection) == 1,
                    "The swap-only tail hook adds no selection during " + guard);
            client.screen = null;
            set(Minecraft.class, client, "windowActive", true);
            player.spectator = false;
            player.alive = true;
        }
        restart(client, player, connection, MACE);
        var previousLevel = client.level;
        client.level = null;
        CombatAssistModule.onManualAttack(client, zombie);
        require(player.inventory.getSelectedSlot() == 0 && attacks(connection) == 0,
                "A disconnected manual-hook callback cannot select a weapon or send packets");
        client.level = previousLevel;
        restart(client, player, connection, MACE);
        CombatLevel otherLevel = (CombatLevel) unsafe.allocateInstance(CombatLevel.class);
        client.level = otherLevel;
        CombatAssistModule.onManualAttack(client, zombie);
        require(player.inventory.getSelectedSlot() == 0 && !CombatAssistModule.enabled(MACE),
                "A changed world session disarms the stale swap-only state before selecting anything");
        client.level = previousLevel;
        client.crosshairPickEntity = zombie;
        client.hitResult = new EntityHitResult(zombie);
        player.grounded = false;
        player.velocity = new Vec3(0, -.5, 0);
        player.fallDistance = 2.5;
        restart(client, player, connection, MACE);
    }

    private static void verifyMace(Minecraft client, CombatPlayer player, CombatLevel level,
            CombatZombie zombie, CapturedConnection connection, Unsafe unsafe) throws ReflectiveOperationException {
        // Behavioral cases are kept together so each observed packet has an explicit trigger.
        CombatAssistModule.setEnabled(MACE, false);
        CombatAssistModule.setEnabled(SLAM, false);
        tick(client, player, 8);
        require(connection.packets.isEmpty(), "Disabled combat modules generate no packets during a dangerous fall");
        CombatAssistModule.setEnabled(MACE, true);
        tick(client, player, 8);
        require(connection.packets.isEmpty(), "An enabled automatic module waits for the player's deliberate attack intent");
        client.gameMode.attack(player, zombie);
        require(attacks(connection) == 1, "Auto Mace preserves the original deliberate vanilla attack");
        tick(client, player, 1);
        require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 1,
                "Auto Mace selects an actual hotbar mace without attacking on the slot-change tick: slot="
                        + player.inventory.getSelectedSlot() + ", attacks=" + attacks(connection)
                        + ", strength=" + player.getAttackStrengthScale(.5F)
                        + ", status=" + CombatAssistModule.status(MACE));
        tick(client, player, 1);
        require(attacks(connection) == 1, "The original attack's ordinary cooldown prevents an immediate follow-up");
        recharge(player);
        set(AutoClutchModule.class, null, "acted", true);
        player.tickCount++;
        CombatAssistModule.tick(client);
        require(attacks(connection) == 1 && CombatAssistModule.status(MACE).contains("Clutch"),
                "A clutch action already taken in the same tick postpones a charged combat follow-up");
        tick(client, player, 5);
        require(attacks(connection) == 2 && player.predictedAttacks == 2,
                "One manual attack triggers exactly one ordinary charged mace follow-up: slot="
                        + player.inventory.getSelectedSlot() + ", attacks=" + attacks(connection)
                        + ", predicted=" + player.predictedAttacks + ", status=" + CombatAssistModule.status(MACE));
        require(hasSlotBeforeAttack(connection, 3, 2),
                "The ordinary held-item packet selects the actual mace before the normal attack packet");
        require(player.getAttackStrengthScale(.5F) < .98F,
                "The real vanilla game mode resets attack recharge after its attack");
        require(player.lastPredictedTarget == zombie && player.lastAttackStack.is(Items.MACE),
                "The captured attack targets the visible hostile zombie using the selected mace");
        recharge(player);
        tick(client, player, 20);
        require(attacks(connection) == 2, "A completed manual-trigger sequence cannot turn into an automatic attack loop");
        require(player.inventory.getSelectedSlot() == 0, "Completing the follow-up restores the original selected slot");

        // Held activation gates deliberate intent and restores the owned slot on release.
        restart(client, player, connection, MACE);
        setActivation(MACE, CombatAssistModule.Activation.HOLD);
        client.gameMode.attack(player, zombie);
        recharge(player);
        tick(client, player, 6);
        require(attacks(connection) == 1 && player.inventory.getSelectedSlot() == 0,
                "Held mode ignores combat intent while its activation key is released");
        hold(client, MACE, true);
        client.gameMode.attack(player, zombie);
        tick(client, player, 1);
        require(player.inventory.getSelectedSlot() == 3 && attacks(connection) == 2,
                "An explicit held key permits a follow-up after a deliberate attack");
        hold(client, MACE, false);
        tick(client, player, 1);
        require(player.inventory.getSelectedSlot() == 0 && attacks(connection) == 2,
                "Releasing the use key cancels the queued follow-up and restores the original slot");
        setActivation(MACE, CombatAssistModule.Activation.AUTOMATIC);

        // Manual scrolling must always win over an owned automatic slot.
        restart(client, player, connection, MACE);
        client.gameMode.attack(player, zombie);
        tick(client, player, 1);
        player.inventory.setSelectedSlot(2);
        recharge(player);
        tick(client, player, 6);
        require(attacks(connection) == 1 && player.inventory.getSelectedSlot() == 2 && CombatAssistModule.enabled(MACE),
                "A manual hotbar change cancels the queued follow-up while keeping Auto Mace enabled");
        client.gameMode.attack(player, zombie);
        tick(client, player, 2);
        recharge(player);
        tick(client, player, 4);
        require(attacks(connection) == 3 && CombatAssistModule.enabled(MACE),
                "The next deliberate hit starts a new Auto Mace follow-up without reopening the GUI");

        // Range and collision line-of-sight are the real vanilla tests, backed by actual block shapes.
        restart(client, player, connection, MACE);
        locate(zombie, new Vec3(.5, 3, 8));
        client.gameMode.attack(player, zombie);
        recharge(player);
        tick(client, player, 6);
        require(attacks(connection) == 1, "The module never adds a follow-up outside ordinary survival attack reach");
        locate(zombie, new Vec3(.5, 3, 2.5));
        restart(client, player, connection, MACE);
        level.states.put(new BlockPos(0, 4, 1), Blocks.STONE.defaultBlockState());
        require(!player.hasLineOfSight(zombie), "An actual stone collision shape blocks the fixture's vanilla line of sight");
        client.gameMode.attack(player, zombie);
        recharge(player);
        tick(client, player, 6);
        require(attacks(connection) == 1, "The module never adds attacks through actual solid blocks");
        level.states.clear();

        // The same manual dispatch path must never automate attacks on players, passives or allies.
        var otherPlayer = (CombatPlayer) unsafe.allocateInstance(CombatPlayer.class);
        verifyProtected(client, player, connection, otherPlayer, "another player");
        var cow = (net.minecraft.world.entity.animal.cow.Cow)
                unsafe.allocateInstance(net.minecraft.world.entity.animal.cow.Cow.class);
        verifyProtected(client, player, connection, cow, "a passive cow");
        var wolf = (net.minecraft.world.entity.animal.wolf.Wolf)
                unsafe.allocateInstance(net.minecraft.world.entity.animal.wolf.Wolf.class);
        verifyProtected(client, player, connection, wolf, "a pet wolf");
        var sharedTeam = new PlayerTeam(new Scoreboard(), "protected");
        player.team = sharedTeam;
        zombie.team = sharedTeam;
        verifyProtected(client, player, connection, zombie, "an allied hostile mob");
        player.team = null;
        zombie.team = null;

        // Wardens are eligible hostile entities under the same ordinary reach and recharge rules.
        var warden = (CombatWarden) unsafe.allocateInstance(CombatWarden.class);
        warden.fixtureLevel = level;
        warden.alive = true;
        warden.setId(902);
        set(Entity.class, warden, "eyeHeight", 2.5F);
        locate(warden, new Vec3(.5, 3, 2.5));
        level.entities.add(warden);
        restart(client, player, connection, MACE);
        client.hitResult = new EntityHitResult(warden);
        client.crosshairPickEntity = warden;
        client.gameMode.attack(player, warden);
        tick(client, player, 2);
        recharge(player);
        tick(client, player, 6);
        require(attacks(connection) == 2 && player.lastPredictedTarget == warden,
                "A deliberate attack can trigger one ordinary charged mace follow-up against a warden");
        client.hitResult = new EntityHitResult(zombie);
        client.crosshairPickEntity = zombie;

        restart(client, player, connection, MACE);
        player.inventory.setItem(3, ItemStack.EMPTY);
        client.gameMode.attack(player, zombie);
        recharge(player);
        tick(client, player, 6);
        require(attacks(connection) == 1 && player.inventory.getItem(3).isEmpty(),
                "A missing mace cannot be invented or replaced with a different weapon");
        player.inventory.setItem(3, new ItemStack(Items.MACE));

        restart(client, player, connection, MACE);
        client.gameMode.attack(player, zombie);
        player.cooldowns.addCooldown(player.inventory.getItem(3), 10);
        recharge(player);
        tick(client, player, 6);
        require(attacks(connection) == 1, "A genuine vanilla mace item cooldown blocks the queued follow-up");
        for (int i = 0; i < 11; i++) player.cooldowns.tick();
        tick(client, player, 4);
        require(attacks(connection) == 2, "The pending follow-up can resume after the actual item cooldown expires");

        // Falling-only is optional and gates actual MaceItem.canSmashAttack data, not fabricated velocity.
        restart(client, player, connection, MACE);
        CombatAssistModule.cycleMaceMode();
        player.grounded = true;
        player.velocity = Vec3.ZERO;
        player.fallDistance = 0;
        client.gameMode.attack(player, zombie);
        recharge(player);
        tick(client, player, 6);
        require(attacks(connection) == 1, "Falling-only mode waits instead of attacking from a grounded position");
        player.grounded = false;
        player.velocity = new Vec3(0, -.5, 0);
        player.fallDistance = 2.5;
        tick(client, player, 2);
        require(attacks(connection) == 2, "A real descending smash window permits the same pending mace follow-up");
        CombatAssistModule.cycleMaceMode();

        restart(client, player, connection, MACE);
        setTarget(MACE, CombatAssistModule.TargetMode.NEAREST_HOSTILE);
        locate(warden, new Vec3(.5, 3, 1.5));
        client.gameMode.attack(player, zombie);
        tick(client, player, 2);
        recharge(player);
        tick(client, player, 6);
        require(attacks(connection) == 2 && player.lastPredictedTarget == zombie,
                "An older nearest-hostile preference cannot redirect an Auto Mace follow-up away from the deliberately hit entity");
        setTarget(MACE, CombatAssistModule.TargetMode.CROSSHAIR);
        level.entities.remove(warden);

        verifyCancellation(client, player, connection, zombie);
    }

    private static void verifyProtected(Minecraft client, CombatPlayer player, CapturedConnection connection,
            Entity target, String description) throws ReflectiveOperationException {
        restart(client, player, connection, MACE);
        client.hitResult = new EntityHitResult(target);
        client.crosshairPickEntity = target;
        client.gameMode.attack(player, target);
        recharge(player);
        tick(client, player, 6);
        require(attacks(connection) == 1 && player.inventory.getSelectedSlot() == 0,
                "Manual intent on " + description + " does not schedule or select an automatic weapon");
    }

    private static void verifyCancellation(Minecraft client, CombatPlayer player,
            CapturedConnection connection, CombatZombie zombie) throws ReflectiveOperationException {
        client.hitResult = new EntityHitResult(zombie);
        client.crosshairPickEntity = zombie;
        restart(client, player, connection, MACE);
        client.gameMode.attack(player, zombie);
        tick(client, player, 1);
        var currentScreen = client.screen;
        client.screen = new net.minecraft.client.gui.screens.TitleScreen();
        recharge(player);
        tick(client, player, 4);
        require(attacks(connection) == 1 && player.inventory.getSelectedSlot() == 0,
                "Opening a menu cancels the owned automatic weapon and the queued attack");
        client.screen = currentScreen;
        restart(client, player, connection, MACE);
        client.gameMode.attack(player, zombie);
        tick(client, player, 1);
        set(Minecraft.class, client, "windowActive", false);
        recharge(player);
        tick(client, player, 4);
        require(attacks(connection) == 1 && player.inventory.getSelectedSlot() == 0,
                "Window focus loss cancels a queued attack and restores the original slot");
        set(Minecraft.class, client, "windowActive", true);
        restart(client, player, connection, MACE);
        client.gameMode.attack(player, zombie);
        player.alive = false;
        tick(client, player, 1);
        require(!CombatAssistModule.enabled(MACE) && attacks(connection) == 1,
                "Death disarms the combat module before a queued follow-up");
        player.alive = true;
        restart(client, player, connection, MACE);
        client.gameMode.attack(player, zombie);
        var currentLevel = client.level;
        client.level = null;
        tick(client, player, 1);
        require(!CombatAssistModule.enabled(MACE) && attacks(connection) == 1,
                "Disconnecting disarms a queued combat module");
        client.level = currentLevel;
    }

    private static void verifyConfiguredTiming(Minecraft client, CombatPlayer player, CombatZombie target,
            CapturedConnection connection) throws ReflectiveOperationException {
        int reaction = CombatAssistModule.preferences(MACE).reactionTicks;
        int interval = CombatAssistModule.preferences(MACE).actionIntervalTicks;
        try {
            for (int[] timing : new int[][]{{8, 10}, {200, 200}}) {
                CombatAssistModule.setTiming(MACE, timing[0], timing[1]);
                restart(client, player, connection, MACE);
                client.gameMode.attack(player, target);
                tick(client, player, timing[0]-1);
                require(attacks(connection) == 1 && player.inventory.getSelectedSlot() == 0,
                        "Reaction delay preserves original selection before the configured tick");
                tick(client, player, 1);
                require(attacks(connection) == 1 && player.inventory.getSelectedSlot() == 3,
                        "Configured reaction tick selects the mace without an early attack");
                for (int tick = 0; tick < timing[1]-1; tick++) { recharge(player); tick(client, player, 1); }
                require(attacks(connection) == 1, "Action interval prevents an early follow-up even when fully charged");
                recharge(player); tick(client, player, 1);
                require(attacks(connection) == 2, "Configured interval dispatches exactly one normal follow-up, including maximum delay");
            }
        } finally {
            CombatAssistModule.setTiming(MACE, reaction, interval);
            CombatAssistModule.setEnabled(MACE, false);
            connection.packets.clear();
            player.predictedAttacks = 0;
            player.inventory.setSelectedSlot(0);
        }
    }

    private static void verifyShieldTrials(Minecraft client, CombatPlayer player,
            CombatZombie target, CapturedConnection connection) throws ReflectiveOperationException {
        var original = dev.lungetest.ShieldDrainerModule.preferences();
        var prefs = new dev.lungetest.ShieldDrainerModule.Preferences();
        prefs.durationSeconds = 60;
        target.setUUID(java.util.UUID.randomUUID());
        target.blocking = true;
        try {
            dev.lungetest.ShieldDrainerModule.initialize(prefs);
            for (int cps : new int[]{1, 10, 20, 40}) {
                prefs.clicksPerSecond = cps;
                player.inventory.setSelectedSlot(0);
                connection.packets.clear();
                dev.lungetest.ShieldDrainerModule.setEnabled(true);
                for (int tick = 0; tick < 20; tick++) dev.lungetest.ShieldDrainerModule.tick(client);
                require(attacks(connection) == cps, "Requested CPS is bounded and dispatched accurately: " + cps + " got " + attacks(connection));
                require(player.lastAttackStack.is(Items.MACE), "Shield trial uses the actual hotbar mace");
                dev.lungetest.ShieldDrainerModule.setEnabled(false);
                require(player.inventory.getSelectedSlot() == 0, "Stopping a shield trial restores the owned slot");
            }
            prefs.clicksPerSecond = 20;
            prefs.reactionTicks = 5;
            connection.packets.clear();
            dev.lungetest.ShieldDrainerModule.setEnabled(true);
            for (int tick = 0; tick < 5; tick++) dev.lungetest.ShieldDrainerModule.tick(client);
            require(attacks(connection) == 0, "Configured reaction delay postpones the trial");
            dev.lungetest.ShieldDrainerModule.tick(client);
            require(attacks(connection) == 1, "First attack occurs after configured reaction delay");
            player.inventory.setSelectedSlot(5);
            dev.lungetest.ShieldDrainerModule.tick(client);
            require(!dev.lungetest.ShieldDrainerModule.enabled() && player.inventory.getSelectedSlot() == 5,
                    "Manual selection stops a trial without overwriting the user's slot");
            prefs.reactionTicks = 0;
            prefs.scenario = dev.lungetest.ShieldDrainerModule.Scenario.AXE_TO_MACE;
            prefs.switchTicks = 3;
            player.inventory.setSelectedSlot(0);
            connection.packets.clear();
            dev.lungetest.ShieldDrainerModule.setEnabled(true);
            dev.lungetest.ShieldDrainerModule.tick(client);
            require(attacks(connection) == 1 && player.lastAttackStack.is(Items.DIAMOND_AXE), "Shield scenario begins with actual axe");
            target.blocking = false;
            for (int tick = 0; tick < 2; tick++) dev.lungetest.ShieldDrainerModule.tick(client);
            require(attacks(connection) == 1, "Axe/mace interval is enforced");
            dev.lungetest.ShieldDrainerModule.tick(client);
            require(attacks(connection) == 2 && player.lastAttackStack.is(Items.MACE), "Same-target mace follows even after shield breaks");
            dev.lungetest.ShieldDrainerModule.setEnabled(false);
            connection.packets.clear();
            dev.lungetest.ShieldDrainerModule.setEnabled(true);
            for (int tick = 0; tick < 20; tick++) dev.lungetest.ShieldDrainerModule.tick(client);
            require(attacks(connection) == 0, "Shield-only trials do not attack a non-blocking target");
            dev.lungetest.CombatTestLog.observation("Fixture: anticheat test alert");
            dev.lungetest.CombatTestLog.mark("flag observed");
            dev.lungetest.CombatTestLog.flush();
            var rows = java.nio.file.Files.readAllLines(dev.lungetest.CombatTestLog.file());
            require(rows.stream().allMatch(row -> com.google.gson.JsonParser.parseString(row).getAsJsonObject()
                    .get("serverVerdict").getAsString().equals("unverified")), "Trace never invents a server verdict");
            require(rows.stream().anyMatch(row -> row.contains("visible_message")) && rows.stream().anyMatch(row -> row.contains("detection_annotation")),
                    "Observed messages and explicit detection annotations are distinguishable");
            LungeTestClient.LOG.info("SHIELD_TRIALS_VERIFIED: CPS 1/10/20/40, reaction delay, axe/mace interval, broken-shield follow-up, manual slot guards and JSONL observations");
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally {
            dev.lungetest.ShieldDrainerModule.initialize(original);
            target.blocking = false;
            player.inventory.setSelectedSlot(0);
            connection.packets.clear();
            recharge(player);
        }
    }

    private static void verifyAxeCombo(Minecraft client, CombatPlayer player, CombatLevel level,
            CombatZombie zombie, CapturedConnection connection) throws ReflectiveOperationException {
        // A previous configuration must not leave the now settings-free module in hold mode.
        var legacy = new Gson().fromJson("{\"activation\":\"HOLD\",\"activationKey\":71,\"targetMode\":\"NEAREST_HOSTILE\",\"reactionTicks\":200,\"actionIntervalTicks\":200,\"slamMode\":\"WIND_CHARGE\"}", CombatAssistModule.Preferences.class);
        var saved = CombatAssistModule.snapshot();
        saved.put("STUNSLAM", legacy);
        CombatAssistModule.initialize(saved);
        require(CombatAssistModule.activation(SLAM) == CombatAssistModule.Activation.AUTOMATIC
                        && CombatAssistModule.targetMode(SLAM) == CombatAssistModule.TargetMode.CROSSHAIR
                        && CombatAssistModule.activationKey(SLAM) == GLFW.GLFW_KEY_UNKNOWN
                        && CombatAssistModule.preferences(SLAM).reactionTicks == 0
                        && CombatAssistModule.preferences(SLAM).actionIntervalTicks == 2,
                "Old hold, wind and long-delay preferences migrate to the fixed automatic axe-to-mace behavior");
        CombatAssistModule.setTiming(SLAM, 200, 200);
        CombatAssistModule.cycleActivation(SLAM);
        CombatAssistModule.cycleTargetMode(SLAM);
        require(CombatAssistModule.activation(SLAM) == CombatAssistModule.Activation.AUTOMATIC
                        && CombatAssistModule.targetMode(SLAM) == CombatAssistModule.TargetMode.CROSSHAIR
                        && CombatAssistModule.preferences(SLAM).reactionTicks == 0,
                "Retired settings cannot change the automatic StunSlam policy");
        restart(client, player, connection, SLAM);
        player.grounded = true;
        player.velocity = Vec3.ZERO;
        player.fallDistance = 0;
        client.gameMode.attack(player, zombie);
        require(attacks(connection) == 0,
                "Auto StunSlam queues a deliberate click without sending the original weapon attack: slot="
                        + player.inventory.getSelectedSlot() + ", attacks=" + attacks(connection)
                        + ", status=" + CombatAssistModule.status(SLAM));
        tick(client, player, 1);
        require(player.inventory.getSelectedSlot() == 7 && attacks(connection) == 0,
                "The first combo stage selects the actual hotbar axe");
        recharge(player);
        tick(client, player, 2);
        require(attacks(connection) == 1 && player.lastAttackStack.is(Items.DIAMOND_AXE),
                "A charged first combo stage sends a normal axe attack even when the zombie has no shield");
        require(hasSlotBeforeAttack(connection, 7, 1), "The ordinary carried-item packet precedes the axe hit");
        tick(client, player, 2);
        require(attacks(connection) == 1 && player.inventory.getSelectedSlot() == 3,
                "The second combo stage selects the mace and respects the axe attack cooldown");
        recharge(player);
        tick(client, player, 4);
        require(attacks(connection) == 2 && player.lastAttackStack.is(Items.MACE),
                "The second charged combo stage sends exactly one ordinary mace attack");
        require(hasSlotBeforeAttack(connection, 3, 2), "The ordinary carried-item packet precedes the mace hit");
        require(player.inventory.getSelectedSlot() == 0,
                "A finished axe-to-mace combo restores the originally selected weapon");
        recharge(player);
        tick(client, player, 90);
        require(attacks(connection) == 2, "A completed StunSlam waits for another deliberate click instead of looping");

        for (boolean beforeTick : new boolean[]{true, false}) {
            restart(client, player, connection, SLAM);
            client.gameMode.attack(player, zombie);
            tick(client, player, 1);
            player.inventory.setSelectedSlot(5); // The clip switches back to wind charges between attempts.
            if (!beforeTick) tick(client, player, 1);
            require(CombatAssistModule.enabled(SLAM), "Manual equipment changes leave Stun Slam enabled");
            client.gameMode.attack(player, zombie);
            for (int i = 0; i < 8; i++) { recharge(player); tick(client, player, 1); }
            require(attacks(connection) == 2 && CombatAssistModule.enabled(SLAM),
                    "A new manual hit restarts axe-to-mace after scrolling, including before the next maintenance tick");
            require(player.inventory.getSelectedSlot() == 5,
                    "The restarted combo returns to the newly selected wind-charge slot");
        }
        restart(client, player, connection, SLAM);
        player.inventory.setItem(7, ItemStack.EMPTY);
        client.gameMode.attack(player, zombie);
        recharge(player);
        tick(client, player, 6);
        require(attacks(connection) <= 1 && player.inventory.getItem(7).isEmpty(),
                "Missing axe supplies never synthesize an axe combo stage");
        player.inventory.setItem(7, new ItemStack(Items.DIAMOND_AXE));

        restart(client, player, connection, SLAM);
        client.gameMode.attack(player, zombie);
        recharge(player);
        tick(client, player, 3);
        require(attacks(connection) == 1, "The target-loss fixture genuinely completed its axe stage first");
        zombie.alive = false;
        recharge(player);
        tick(client, player, 6);
        require(attacks(connection) == 1, "A target dying between combo stages cancels the remaining mace attack");
        zombie.alive = true;

        restart(client, player, connection, SLAM);
        CombatAssistModule.setEnabled(MACE, true);
        client.gameMode.attack(player, zombie);
        for (int i = 0; i < 12; i++) {
            recharge(player);
            long before = attacks(connection);
            tick(client, player, 1);
            require(attacks(connection) - before <= 1,
                    "Enabling both modules still permits at most one normal attack dispatch per client tick");
        }
        require(attacks(connection) == 2,
                "StunSlam priority prevents a third recursive Auto Mace follow-up when both modules are enabled");
    }

    private static void verifyPlayerCombo(Minecraft client, CombatPlayer player, CombatLevel level,
            CapturedConnection connection, Unsafe unsafe) throws ReflectiveOperationException {
        var target = (CombatPlayer) unsafe.allocateInstance(CombatPlayer.class);
        target.fixtureLevel = level;
        target.inventory = new Inventory(target, new EntityEquipment());
        target.abilities = new Abilities();
        target.cooldowns = new ItemCooldowns();
        target.offhand = new ItemStack(Items.SHIELD);
        target.velocity = Vec3.ZERO;
        target.alive = true;
        target.setId(903);
        set(Entity.class, target, "eyeHeight", 1.62F);
        locate(target, new Vec3(.5, 3, 2.5));
        locate(player, new Vec3(.5, 3, .5));
        player.grounded = true;
        player.velocity = Vec3.ZERO;
        player.fallDistance = 0;
        client.hitResult = new EntityHitResult(target);
        client.crosshairPickEntity = target;
        restart(client, player, connection, SLAM);
        client.gameMode.attack(player, target);
        require(attacks(connection) == 0, "A deliberate player click queues Stun Slam before the original attack");
        tick(client, player, 1);
        recharge(player);
        tick(client, player, 2);
        require(attacks(connection) == 1 && player.lastAttackStack.is(Items.DIAMOND_AXE)
                && player.lastPredictedTarget == target, "The axe stage attacks the clicked player");
        require(hasSlotBeforeAttack(connection, 7, 1), "Player combo synchronizes the axe before its hit");
        tick(client, player, 2);
        recharge(player);
        tick(client, player, 4);
        require(attacks(connection) == 2 && player.lastAttackStack.is(Items.MACE)
                && player.lastPredictedTarget == target, "The mace stage attacks that same player exactly once");
        require(hasSlotBeforeAttack(connection, 3, 2) && player.inventory.getSelectedSlot() == 0,
                "Player combo synchronizes the mace and restores the original slot");
        recharge(player);
        tick(client, player, 90);
        require(attacks(connection) == 2, "Player Stun Slam never repeats without another manual click");
        var startAttack = Minecraft.class.getDeclaredMethod("startAttack");
        startAttack.setAccessible(true);
        var missTime = Minecraft.class.getDeclaredField("missTime");
        missTime.setAccessible(true);
        int previousMissTime = missTime.getInt(client);
        ItemStack previousHeld = player.inventory.getItem(0).copy();
        try {
            for (var held : List.of(Items.DIAMOND_SWORD, Items.DIAMOND_SPEAR, Items.WIND_CHARGE)) {
                for (boolean falling : new boolean[]{false, true}) {
                    restart(client, player, connection, SLAM);
                    CombatAssistModule.setEnabled(MACE, true);
                    player.inventory.setItem(0, new ItemStack(held));
                    player.grounded = !falling;
                    player.velocity = falling ? new Vec3(0, -.5, 0) : Vec3.ZERO;
                    player.fallDistance = falling ? 2.5 : 0;
                    client.hitResult = new EntityHitResult(target);
                    missTime.setInt(client, 0);
                    startAttack.invoke(client);
                    require(attacks(connection) == 0, "Actual player click queues Stun Slam with " + held
                            + ", falling=" + falling + ", status=" + CombatAssistModule.status(SLAM));
                    tick(client, player, 1);
                    recharge(player);
                    tick(client, player, 2);
                    require(attacks(connection) == 1 && player.lastAttackStack.is(Items.DIAMOND_AXE),
                            "Actual clicked-player combo starts with axe, including falling AutoMace conflicts");
                    tick(client, player, 2);
                    recharge(player);
                    tick(client, player, 4);
                    require(attacks(connection) == 2 && player.lastAttackStack.is(Items.MACE)
                            && player.inventory.getSelectedSlot() == 0,
                            "Actual clicked-player combo completes mace and returns the original slot");
                }
            }
            restart(client, player, connection, SLAM);
            missTime.setInt(client, 5);
            startAttack.invoke(client);
            for (int i = 0; i < 8; i++) { recharge(player); tick(client, player, 1); }
            require(attacks(connection) == 0 && player.inventory.getSelectedSlot() == 0,
                    "A vanilla-blocked player click neither queues Stun Slam nor leaves the axe selected");
        } finally {
            missTime.setInt(client, previousMissTime);
            player.inventory.setItem(0, previousHeld);
            player.grounded = true;
            player.velocity = Vec3.ZERO;
            player.fallDistance = 0;
        }

        for (boolean spectator : new boolean[]{true, false}) {
            restart(client, player, connection, SLAM);
            target.spectator = spectator;
            target.creative = !spectator;
            require(!CombatAssistModule.beginManualAttack(client, target),
                    "Stun Slam excludes " + (spectator ? "spectator" : "creative") + " players");
            tick(client, player, 6);
            require(attacks(connection) == 0, "Excluded players never receive a scheduled combo");
        }
        target.spectator = target.creative = false;
        restart(client, player, connection, SLAM);
        var team = new PlayerTeam(new Scoreboard(), "survival-allies");
        player.team = target.team = team;
        require(!CombatAssistModule.beginManualAttack(client, target), "Stun Slam preserves allied-player exclusion");
        player.team = target.team = null;
        client.gameMode.attack(player, target);
        tick(client, player, 1);
        recharge(player);
        tick(client, player, 2);
        require(attacks(connection) == 1, "Player cancellation fixture completes its axe stage");
        target.alive = false;
        recharge(player);
        tick(client, player, 6);
        require(attacks(connection) == 1 && player.inventory.getSelectedSlot() == 0,
                "A player dying between stages cancels the mace hit and returns the original slot");
    }

    private static void hold(Minecraft client, CombatAssistModule.Feature feature, boolean pressed) {
        boolean handled = CombatAssistModule.handleActivationKey(client,
                        pressed ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE,
                        new KeyEvent(CombatAssistModule.activationKey(feature), 0, 0));
        require(pressed ? handled : !handled,
                "Combat activation key is handled for " + feature + (pressed ? " press" : " release"));
    }
    private static void tick(Minecraft client, CombatPlayer player, int count) {
        for (int i = 0; i < count; i++) {
            player.tickCount++;
            // Normal clients maintain clutch state at START before combat dispatch at END.
            AutoClutchModule.tick(client);
            CombatAssistModule.tick(client);
        }
    }
    private static void recharge(CombatPlayer player) throws ReflectiveOperationException {
        set(LivingEntity.class, player, "attackStrengthTicker", 100);
    }
    private static long attacks(CapturedConnection connection) {
        return connection.packets.stream().filter(ServerboundInteractPacket.class::isInstance).count();
    }
    private static long uses(CapturedConnection connection) {
        return connection.packets.stream().filter(ServerboundUseItemPacket.class::isInstance).count();
    }
    private static boolean hasSlotBefore(CapturedConnection connection, int slot, Class<? extends Packet<?>> use) {
        boolean selected = false;
        for (var packet : connection.packets) {
            if (packet instanceof ServerboundSetCarriedItemPacket carried) selected = carried.getSlot() == slot;
            if (use.isInstance(packet)) return selected;
        }
        return false;
    }
    private static boolean hasSlotBeforeAttack(CapturedConnection connection, int slot, int wantedAttack) {
        boolean selected = false;
        int count = 0;
        for (var packet : connection.packets) {
            if (packet instanceof ServerboundSetCarriedItemPacket carried) selected = carried.getSlot() == slot;
            if (packet instanceof ServerboundInteractPacket && ++count == wantedAttack) return selected;
        }
        return false;
    }
    private static boolean hasAttackBeforeSlot(CapturedConnection connection, int slot) {
        boolean attacked = false;
        for (var packet : connection.packets) {
            if (packet instanceof ServerboundInteractPacket) attacked = true;
            if (packet instanceof ServerboundSetCarriedItemPacket carried && carried.getSlot() == slot)
                return attacked;
        }
        return false;
    }
    private static void restart(Minecraft client, CombatPlayer player, CapturedConnection connection,
            CombatAssistModule.Feature feature) throws ReflectiveOperationException {
        CombatAssistModule.setEnabled(MACE, false);
        CombatAssistModule.setEnabled(SLAM, false);
        player.inventory.setSelectedSlot(0);
        connection.packets.clear();
        player.predictedAttacks = 0;
        player.lastPredictedTarget = null;
        player.lastAttackStack = ItemStack.EMPTY;
        recharge(player);
        CombatAssistModule.setEnabled(feature, true);
        tick(client, player, 1);
    }
    private static void locate(Entity entity, Vec3 position) throws ReflectiveOperationException {
        set(Entity.class, entity, "position", position);
        set(Entity.class, entity, "bb", new AABB(position.x - .3, position.y, position.z - .3,
                position.x + .3, position.y + 1.8, position.z + .3));
    }
    private static void set(Class<?> owner, Object target, String name, Object value) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static void setActivation(CombatAssistModule.Feature feature, CombatAssistModule.Activation target) {
        while (CombatAssistModule.activation(feature) != target) CombatAssistModule.cycleActivation(feature);
    }
    private static void setTarget(CombatAssistModule.Feature feature, CombatAssistModule.TargetMode target) {
        while (CombatAssistModule.targetMode(feature) != target) CombatAssistModule.cycleTargetMode(feature);
    }
    private static void setMaceAction(CombatAssistModule.MaceAction target) {
        while (CombatAssistModule.maceAction() != target) CombatAssistModule.cycleMaceAction();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public static final class CombatPlayer extends LocalPlayer {
        Inventory inventory;
        Abilities abilities;
        ItemCooldowns cooldowns;
        ItemStack offhand;
        Vec3 velocity;
        ClientLevel fixtureLevel;
        PlayerTeam team;
        boolean alive, grounded, using, spectator, flying, water, lava, climbing, creative, passenger, killOnAttack;
        int predictedAttacks;
        Entity lastPredictedTarget;
        ItemStack lastAttackStack;
        private CombatPlayer() { super(null, null, null, null, null, null, false); }
        @Override public Inventory getInventory() { return inventory; }
        @Override public Abilities getAbilities() { return abilities; }
        @Override public ItemCooldowns getCooldowns() { return cooldowns; }
        @Override public ItemStack getMainHandItem() { return inventory.getSelectedItem(); }
        @Override public ItemStack getOffhandItem() { return offhand; }
        @Override public ItemStack getItemInHand(InteractionHand hand) {
            return hand == InteractionHand.OFF_HAND ? offhand : inventory.getSelectedItem();
        }
        @Override public void setItemInHand(InteractionHand hand, ItemStack stack) {
            if (hand == InteractionHand.OFF_HAND) offhand = stack;
            else inventory.setItem(inventory.getSelectedSlot(), stack);
        }
        @Override public double getAttributeValue(Holder<Attribute> attribute) {
            if (attribute == Attributes.ATTACK_SPEED)
                return getMainHandItem().is(Items.MACE) ? .6 : getMainHandItem().is(Items.DIAMOND_AXE) ? 1 : 4;
            if (attribute == Attributes.ENTITY_INTERACTION_RANGE) return 3;
            return attribute.value().getDefaultValue();
        }
        @Override public Vec3 getDeltaMovement() { return velocity; }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean onGround() { return grounded; }
        @Override public boolean isInWater() { return water; }
        @Override public boolean isInLava() { return lava; }
        @Override public boolean onClimbable() { return climbing; }
        @Override public boolean isFallFlying() { return flying; }
        @Override public boolean isPassenger() { return passenger; }
        @Override public boolean isShiftKeyDown() { return false; }
        @Override public boolean isSecondaryUseActive() { return false; }
        @Override public boolean isUsingItem() { return using; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public boolean isCreative() { return creative; }
        @Override public boolean hasInfiniteMaterials() { return false; }
        @Override public PlayerTeam getTeam() { return team; }
        @Override public double blockInteractionRange() { return 4.5; }
        @Override public void awardStat(Stat<?> stat) { }
        @Override public Level level() { return fixtureLevel; }
        @Override public void swing(InteractionHand hand) { }
        @Override public void attack(Entity target) {
            predictedAttacks++;
            lastPredictedTarget = target;
            lastAttackStack = getMainHandItem().copy();
            if (killOnAttack && target instanceof CombatZombie zombie) zombie.alive = false;
        }
    }

    public static final class CombatZombie extends Zombie {
        ClientLevel fixtureLevel;
        PlayerTeam team;
        boolean alive, blocking;
        private CombatZombie() { super(EntityType.ZOMBIE, null); }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean isBlocking() { return blocking; }
        @Override public PlayerTeam getTeam() { return team; }
        @Override public Component getName() { return Component.literal("Fixture zombie"); }
        @Override public Level level() { return fixtureLevel; }
    }

    public static final class CombatWarden extends Warden {
        ClientLevel fixtureLevel;
        boolean alive;
        private CombatWarden() { super(EntityType.WARDEN, null); }
        @Override public boolean isAlive() { return alive; }
        @Override public PlayerTeam getTeam() { return null; }
        @Override public Component getName() { return Component.literal("Fixture warden"); }
        @Override public Level level() { return fixtureLevel; }
    }

    public static final class CombatLevel extends ClientLevel {
        Map<BlockPos, BlockState> states;
        List<Entity> entities;
        WorldBorder border;
        RandomSource random;
        private CombatLevel() { super(null, null, null, null, 0, 0, null, false, 0, 0); }
        @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public boolean isLoaded(BlockPos pos) { return true; }
        @Override public WorldBorder getWorldBorder() { return border; }
        @Override public RandomSource getRandom() { return random; }
        @Override public FeatureFlagSet enabledFeatures() { return FeatureFlags.DEFAULT_FLAGS; }
        @Override public BlockGetter getChunkForCollisions(int x, int z) { return this; }
        @Override public Iterable<Entity> entitiesForRendering() { return entities; }
        @Override public List<Entity> getEntities(Entity except, AABB bounds, Predicate<? super Entity> predicate) {
            return entities.stream().filter(entity -> entity != except && bounds.intersects(entity.getBoundingBox())
                    && predicate.test(entity)).toList();
        }
        @Override public void playSound(Entity source, double x, double y, double z, SoundEvent sound,
                SoundSource category, float volume, float pitch) { }
        @Override public void playSeededSound(Entity source, double x, double y, double z,
                net.minecraft.core.Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) { }
    }

    public static final class CapturedConnection extends ClientPacketListener {
        List<Packet<?>> packets;
        CapturedNetwork network;
        private CapturedConnection() { super(null, null, null); }
        @Override public void send(Packet<?> packet) { packets.add(packet); }
        @Override public Connection getConnection() { return network; }
        @Override public boolean isFeatureEnabled(FeatureFlagSet flags) { return flags.isSubsetOf(FeatureFlags.DEFAULT_FLAGS); }
    }

    public static final class CapturedNetwork extends Connection {
        CapturedNetwork() { super(PacketFlow.SERVERBOUND); }
        @Override public boolean isConnected() { return true; }
        @Override public void tick() { }
    }
}
