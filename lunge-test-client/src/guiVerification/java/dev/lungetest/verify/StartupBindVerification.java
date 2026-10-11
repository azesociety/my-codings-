package dev.lungetest.verify;

import dev.lungetest.LungeTestClient;
import dev.lungetest.SpearQuickLungeModule;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Runs in the actual Fabric initializer, before Minecraft initializes its window. */
public final class StartupBindVerification {
    private StartupBindVerification() {}
    public static void verify() {
        var client = Minecraft.getInstance();
        if (client != null && client.getWindow() != null)
            throw new AssertionError("Startup bind regression must execute before window initialization");
        int original = SpearQuickLungeModule.key(), slot = SpearQuickLungeModule.preferredSlot();
        int checks = 0;
        try {
            for (int code : new int[] {GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_F18,
                    SpearQuickLungeModule.mouseCode(4), GLFW.GLFW_KEY_UNKNOWN}) {
                SpearQuickLungeModule.initialize(code, 3);
                if (SpearQuickLungeModule.key() != code || SpearQuickLungeModule.preferredSlot() != 3)
                    throw new AssertionError("Saved input code/slot changed during startup: " + code);
                checks++;
            }
            // Resolving a saved keyboard label here used to queue GLFW_NOT_INITIALIZED.
            // Reaching the later rendered GUI verifies that no error poisoned GLX startup.
            LungeTestClient.LOG.info("STARTUP_BIND_VERIFIED: {} saved keyboard/function/mouse/unbound cases before window initialization", checks);
        } finally { SpearQuickLungeModule.initialize(original, slot); }
    }
}
