package dev.lungetest;
import net.minecraft.client.Minecraft;

/** Selecting a module never changes its state. */
public final class AzeiumModules {
    private AzeiumModules() { }
    public static void disableAll(Minecraft client) {
        for (Module module : Module.values()) if (module.enabled()) module.applyToggle(client);
        LungeTestClient.saveAllModuleToggles();
    }
    public enum Module {
        SPEAR("Spear", "Remove the local spear cooldown.") {
            public boolean enabled() { return LungeTestClient.enabled(); }
            public boolean canToggle(Minecraft client) {
                return enabled() || client.player != null && client.getConnection() != null && client.player.isAlive();
            }
            public void applyToggle(Minecraft client) { LungeTestClient.toggle(client); }
        },
        SPEAROMETER("Spearometer", "Track travel speed and distance.") {
            public boolean enabled() { return LungeTestClient.hudEnabled(); }
            public void applyToggle(Minecraft client) { LungeTestClient.toggleHud(); }
        },
        PLAYER_HIGHLIGHTS("Player highlights", "See player outlines through blocks.") {
            public boolean enabled() { return PlayerHighlightModule.enabled(); }
            public void applyToggle(Minecraft client) { PlayerHighlightModule.setEnabled(!enabled()); }
        },
        TRACERS("Tracers", "Draw lines toward loaded players.") {
            public boolean enabled() { return PlayerTracerModule.enabled(); }
            public void applyToggle(Minecraft client) { PlayerTracerModule.setEnabled(!enabled()); }
        },
        AUTO_CLUTCH("Auto Clutch", "Bridge, jump and catch falls.") {
            public boolean enabled() { return AutoClutchModule.enabled(); }
            public boolean canToggle(Minecraft client) {
                return enabled() || client.player != null && client.getConnection() != null && client.player.isAlive();
            }
            public void applyToggle(Minecraft client) { AutoClutchModule.setEnabled(!enabled()); }
        },
        COORDINATES("Coordinates", "Show your position and facing direction.") {
            public boolean enabled() { return CoordinatesModule.enabled(); }
            public void applyToggle(Minecraft client) { CoordinatesModule.setEnabled(!enabled()); }
        },
        ZOOM("Zoom", "Smoothly zoom the view for distant details.") {
            public boolean enabled() { return ZoomModule.enabled(); }
            public void applyToggle(Minecraft client) { ZoomModule.setEnabled(!enabled()); }
        },
        AUTO_MACE("Auto Mace", "Use your mace on the same falling attack click.") {
            public boolean enabled() { return CombatAssistModule.enabled(CombatAssistModule.Feature.MACE); }
            public boolean canToggle(Minecraft client) {
                return enabled() || client.player != null && client.getConnection() != null && client.player.isAlive();
            }
            public void applyToggle(Minecraft client) { CombatAssistModule.setEnabled(CombatAssistModule.Feature.MACE, !enabled()); }
        },
        SHIELD_DRAINER("Shield Drainer", "Run a timed clicking or shield-interaction trial.") {
            public boolean enabled() { return ShieldDrainerModule.enabled(); }
            public boolean canToggle(Minecraft client) {
                return enabled() || client.player != null && client.getConnection() != null && client.player.isAlive();
            }
            public void applyToggle(Minecraft client) { ShieldDrainerModule.setEnabled(!enabled()); }
        },
        AUTO_STUNSLAM("Auto StunSlam", "Aim at a raised shield: axe, then mace.") {
            public boolean enabled() { return AutoStunSlamModule.enabled(); }
            public boolean canToggle(Minecraft client) {
                return enabled() || client.player != null && client.getConnection() != null && client.player.isAlive();
            }
            public void applyToggle(Minecraft client) { AutoStunSlamModule.setEnabled(!enabled()); }
        },
        AUTO_REJOIN("Auto Rejoin", "Reconnect after a lost server connection.") {
            public boolean enabled() { return AutoRejoinModule.enabled(); }
            public void applyToggle(Minecraft client) { AutoRejoinModule.setEnabled(!enabled()); }
        },
        ARMOR_ALERTS("Armor Alerts", "Warn when equipped armor needs repair.") {
            public boolean enabled() { return ArmorAlertsModule.enabled(); }
            public void applyToggle(Minecraft client) { ArmorAlertsModule.setEnabled(!enabled()); }
        },
        FULLBRIGHT("Fullbright", "Brighten the vanilla world lightmap.") {
            public boolean enabled() { return FullbrightModule.enabled(); }
            public void applyToggle(Minecraft client) { FullbrightModule.setEnabled(!enabled()); }
        },
        SESSION_HUD("Session HUD", "Track active play time and session travel.") {
            public boolean enabled() { return SessionHudModule.enabled(); }
            public void applyToggle(Minecraft client) { SessionHudModule.setEnabled(!enabled()); }
        },
        PERFORMANCE_HUD("Performance HUD", "View live FPS and reported server latency.") {
            public boolean enabled() { return PerformanceHudModule.enabled(); }
            public void applyToggle(Minecraft client) { PerformanceHudModule.setEnabled(!enabled()); }
        },
        AUTO_CLICKER("Autoclicker", "Repeated left or right clicks: toggle or hold.") {
            public boolean enabled() { return AutoClickerModule.enabled(); }
            public boolean canToggle(Minecraft client) {
                return enabled() || client.player != null && client.getConnection() != null && client.player.isAlive();
            }
            public void applyToggle(Minecraft client) { AutoClickerModule.setEnabled(!enabled()); }
        };
        private final String label;
        private final String description;
        Module(String label, String description) { this.label = label; this.description = description; }
        public String label() { return label; }
        public String description() { return description; }
        public abstract boolean enabled();
        public boolean canToggle(Minecraft client) { return true; }
        public abstract void applyToggle(Minecraft client);
        public final void toggle(Minecraft client) {
            applyToggle(client);
            LungeTestClient.saveModuleToggle(this);
        }
    }
}
