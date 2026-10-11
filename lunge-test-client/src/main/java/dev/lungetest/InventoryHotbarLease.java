package dev.lungetest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/** Borrows main-inventory supplies using vanilla SWAP clicks, never direct inventory writes. */
public final class InventoryHotbarLease {
    private record Borrowed(int source, int hotbar, ItemStack weapon, ItemStack displaced) {}
    private final List<Borrowed> borrowed = new ArrayList<>();
    private LocalPlayer owner;
    private ClientPacketListener connection;
    private boolean pendingReturn;

    @SafeVarargs
    public final boolean ensure(Minecraft client, Predicate<ItemStack>... requirements) {
        tick(client);
        if (pendingReturn || client.player == null || client.gameMode == null) return false;
        var inventory = client.player.getInventory();
        var missing = new ArrayList<Predicate<ItemStack>>();
        for (var requirement : requirements) {
            boolean present = false;
            for (int i = 0; i < 9; i++) if (requirement.test(inventory.getItem(i))) { present = true; break; }
            if (!present) missing.add(requirement);
        }
        if (missing.isEmpty()) return true;
        if (client.player.inventoryMenu == null || client.player.containerMenu != client.player.inventoryMenu
                || !client.player.inventoryMenu.getCarried().isEmpty()) return false;
        // Check all supplies before moving any of them.
        for (var requirement : missing) {
            boolean found = false;
            for (int i = 9; i < 36; i++) if (requirement.test(inventory.getItem(i))) { found = true; break; }
            if (!found) return false;
        }
        if (owner == null) { owner = client.player; connection = client.getConnection(); }
        for (var requirement : missing) {
            int source = -1, destination = -1;
            for (int i = 9; i < 36; i++) if (requirement.test(inventory.getItem(i))) { source = i; break; }
            for (int emptyPass = 0; emptyPass < 2 && destination < 0; emptyPass++) {
                for (int i = 8; i >= 0; i--) {
                    if (i == inventory.getSelectedSlot()) continue;
                    boolean protectedSlot = false;
                    for (var needed : requirements) if (needed.test(inventory.getItem(i))) protectedSlot = true;
                    if (!protectedSlot && (emptyPass == 1 || inventory.getItem(i).isEmpty())) { destination = i; break; }
                }
            }
            if (source < 0 || destination < 0) { release(client); return false; }
            var entry = new Borrowed(source, destination, inventory.getItem(source).copy(), inventory.getItem(destination).copy());
            swap(client, source, destination);
            borrowed.add(entry);
            if (!requirement.test(inventory.getItem(destination))) { release(client); return false; }
        }
        return true;
    }

    public void release(Minecraft client) { pendingReturn = !borrowed.isEmpty(); tick(client); }

    public void tick(Minecraft client) {
        if (owner != null && (client.player != owner || client.getConnection() != connection || !owner.isAlive())) {
            borrowed.clear(); owner = null; connection = null; pendingReturn = false; return;
        }
        if (!pendingReturn || client.gameMode == null || owner == null
                || owner.containerMenu != owner.inventoryMenu || !owner.inventoryMenu.getCarried().isEmpty()) return;
        var inventory = owner.getInventory();
        for (int i = borrowed.size() - 1; i >= 0; i--) {
            var entry = borrowed.get(i);
            // Weapon durability may have changed. Preserve a user's moved/displaced supplies.
            if (sameLoan(inventory.getItem(entry.hotbar()), entry.weapon())
                    && ItemStack.matches(inventory.getItem(entry.source()), entry.displaced()))
                swap(client, entry.source(), entry.hotbar());
        }
        borrowed.clear(); owner = null; connection = null; pendingReturn = false;
    }

    private static boolean sameLoan(ItemStack current, ItemStack original) {
        if (current.isEmpty() || !current.is(original.getItem()) || current.getCount() != original.getCount()) return false;
        ItemStack comparable = current.copy();
        if (comparable.isDamageableItem()) comparable.setDamageValue(original.getDamageValue());
        return ItemStack.isSameItemSameComponents(comparable, original);
    }

    private static void swap(Minecraft client, int source, int destination) {
        client.gameMode.handleInventoryMouseClick(client.player.inventoryMenu.containerId,
                source, destination, ClickType.SWAP, client.player);
    }
}
