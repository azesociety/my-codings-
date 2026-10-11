# aze's hub 2.5.1

Minecraft Java 1.21.11 · Fabric Loader 0.19.3+ · Java 21 · Fabric API.

- [Download the mod JAR](https://github.com/azesociety/my-codings-/raw/refs/heads/main/azes-hub-2.5.1-mc1.21.11.jar)
- [Browse the source project](lunge-test-client/)
- [Installation and controls](lunge-test-client/README.md)

Replace the older aze's hub JAR in your instance's mods folder. Build the source
with Java 21 using `gradlew.bat build` (Windows) or `./gradlew build` (Linux/macOS)
inside `lunge-test-client`. The first build downloads dependencies.

New in 2.5.1: the client is renamed to aze's hub, Stun Slam offers saved tick delays, and all module ON/OFF toggles persist across joins and restarts.

Stun Slam now uses your uploaded add-on's automatic crosshair behavior: enable it and aim at an actively raised shield. It attacks with an axe, follows with a mace after the configured number of client ticks, returns your slot, and waits the configured repeat delay before retriggering. It supports normal multiplayer players and inventory weapons. Server damage rules still apply.

Startup fix: saved spear keyboard binds no longer look up key names before GLFW initializes. Keep your existing settings.

Design: shared satin glass redesign with licensed Allura cursive headings,
Performance HUD for live FPS and reported ping, and Disable all modules.
Existing same-click Smash, slot return, keyboard/mouse spear shortcuts and
optional local reach settings are retained. Server rules determine valid hits.

Forward lunges require the Lunge enchantment and normal vanilla conditions.

An axe and mace may be in your main inventory or hotbar. Borrowed tools return after the combo; close other containers first. Autoclicker settings are saved, but it restores your saved toggle. Server rules still determine shield disables, damage and placement.

## Saved toggles and Stun Slam timing (2.5.1)

The client is now named **aze's hub**. In Auto StunSlam, **Mace delay**
cycles 1–40 client ticks between the axe and mace attacks. **Repeat delay**
cycles 1–60 client ticks before another automatic combo. Defaults remain
1 and 12 ticks. Twenty client ticks are about one second at normal speed;
these controls change timing, not damage or server acceptance.

Module ON/OFF choices and timing settings are saved in the existing
`config/azeium.json`, preserving your previous keybinds and settings.
Saved toggles restore when you join another server or world or restart Minecraft.
Gameplay modules resume once a live player exists and their normal conditions
are met. Death/disconnect still cancels pending attacks and inventory loans.
**Disable all modules** also saves OFF for every module.
