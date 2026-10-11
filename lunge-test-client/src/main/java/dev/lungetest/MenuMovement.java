package dev.lungetest;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/** Only movement keys are read; game actions and global keybinding states remain untouched. */
public final class MenuMovement {
    private MenuMovement() {}
    public static boolean allowed(Minecraft client) {
        return client.screen instanceof AzeiumScreen screen && screen.allowsMovement()
                && client.player != null && client.player.isAlive() && client.level != null
                && client.isWindowActive() && !client.isPaused();
    }
    public static boolean movementKey(Minecraft client, KeyEvent event) {
        if (!allowed(client) || LungeTestClient.menuMatches(event)) return false;
        var o=client.options;
        return o.keyUp.matches(event)||o.keyDown.matches(event)||o.keyLeft.matches(event)
                ||o.keyRight.matches(event)||o.keyJump.matches(event)||o.keyShift.matches(event)||o.keySprint.matches(event);
    }
    public static TellyPilotModule.InputOverride read(Minecraft client) {
        return read(client, code -> InputConstants.isKeyDown(client.getWindow(), code));
    }
    public static TellyPilotModule.InputOverride read(Minecraft client, IntPredicate physicalKey) {
        if (!(client.screen instanceof AzeiumScreen)) return null;
        if (!allowed(client)) return new TellyPilotModule.InputOverride(Input.EMPTY, Vec2.ZERO);
        var o=client.options;
        boolean forward=down(o.keyUp,physicalKey), back=down(o.keyDown,physicalKey),
                left=down(o.keyLeft,physicalKey),right=down(o.keyRight,physicalKey);
        var input=new Input(forward,back,left,right,down(o.keyJump,physicalKey),down(o.keyShift,physicalKey),down(o.keySprint,physicalKey));
        var movement=new Vec2((left?1:0)-(right?1:0),(forward?1:0)-(back?1:0));
        if (movement.lengthSquared()>1) movement=movement.normalized();
        return new TellyPilotModule.InputOverride(input,movement);
    }
    public static void restoreMovementKeys(Minecraft client) {
        if (client == null) return;
        var o=client.options;
        for (var mapping: new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint})
            mapping.setDown(client.player != null && client.isWindowActive()
                    && down(mapping, code -> InputConstants.isKeyDown(client.getWindow(), code)));
    }
    private static boolean down(KeyMapping mapping, IntPredicate physicalKey) {
        var key=InputConstants.getKey(mapping.saveString());
        return key.getType()==InputConstants.Type.KEYSYM && key.getValue()>=0 && physicalKey.test(key.getValue());
    }
}
