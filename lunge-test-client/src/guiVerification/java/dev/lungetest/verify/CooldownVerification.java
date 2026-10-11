package dev.lungetest.verify;

import dev.lungetest.LungeTestClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import sun.misc.Unsafe;

/** Exercises Fabric-transformed vanilla methods without connecting to a server. */
public final class CooldownVerification {
    public static void verify(Minecraft client) {
        var previousPlayer = client.player;
        int checks = 0;
        try {
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var unsafe = (Unsafe) field.get(null);
            var local = (FixturePlayer) unsafe.allocateInstance(FixturePlayer.class);
            var other = (FixturePlayer) unsafe.allocateInstance(FixturePlayer.class);
            var connection = (ClientPacketListener) unsafe.allocateInstance(ClientPacketListener.class);
            var connectionField = LocalPlayer.class.getField("connection");
            connectionField.setAccessible(true);
            connectionField.set(local, connection);
            client.player = local;
            for (String material : new String[]{"wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"}) {
                var item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", material + "_spear"));
                require(item != null && item != Items.AIR, "Spear exists");
                local.held = new ItemStack(item);
                LungeTestClient.disable();
                require(local.cannotAttackWithItem(local.held, 0), material + " vanilla gate blocks an uncharged attack");
                require(local.getAttackStrengthScale(0) == 0.0F, material + " OFF retains vanilla recharge");
                LungeTestClient.toggle(client);
                require(LungeTestClient.enabled(), "Manual testing session enabled");
                require(!local.cannotAttackWithItem(local.held, 0), material + " bypass permits immediate attacks");
                require(local.getAttackStrengthScale(0) == 1.0F, material + " local attack charge is full");
                checks += 6;
            }
            other.held = local.held;
            require(other.cannotAttackWithItem(other.held, 0), "Other player keeps vanilla cooldown");
            require(other.getAttackStrengthScale(0) == 0.0F, "Other player remains uncharged");
            checks += 2;
            for (var weapon : new net.minecraft.world.item.Item[]{Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.TRIDENT}) {
                local.held = new ItemStack(weapon);
                require(local.getAttackStrengthScale(0) == 0.0F, "Other weapon keeps vanilla recharge");
                require(local.getAttackStrengthScale(2) < 1.0F, "Other weapon still needs time to recharge");
                checks += 2;
            }
            local.held = new ItemStack(Items.DIAMOND_SPEAR);
            LungeTestClient.disable();
            require(local.cannotAttackWithItem(local.held, 0), "Turning the module off restores cooldown");
            require(local.getAttackStrengthScale(0) == 0.0F, "Turning off restores uncharged state");
            checks += 2;
            LungeTestClient.LOG.info("LOCAL_COOLDOWN_VERIFIED: {} vanilla method checks passed; no packets or server connection used", checks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not initialize the world-free cooldown fixture", e);
        } finally {
            LungeTestClient.disable();
            client.player = previousPlayer;
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static final class FixturePlayer extends LocalPlayer {
        ItemStack held;
        private FixturePlayer() { super(null, null, null, null, null, null, false); }
        @Override public ItemStack getMainHandItem() { return held; }
        @Override public double getAttributeValue(Holder<Attribute> attribute) { return 4.0; }
        @Override public boolean isAlive() { return true; }
    }
}
