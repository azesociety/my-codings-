# aze's hub 2.5.3

Minecraft Java 1.21.11 · Fabric Loader 0.19.3+ · Java 21 · Fabric API.

- [Download the mod JAR](https://github.com/azesociety/my-codings-/raw/refs/heads/main/azes-hub-2.5.3-mc1.21.11.jar)
- [Browse the source project](lunge-test-client/)
- [Installation and controls](lunge-test-client/README.md)

Replace the older aze's hub JAR in your instance's mods folder. Build the source
with Java 21 using `gradlew.bat build` (Windows) or `./gradlew build` (Linux/macOS)
inside `lunge-test-client`. The first build downloads dependencies.

New in 2.5.3: a saved Legit mode in Client settings shows actual spear, axe and mace selections briefly before returning to your original slot.

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

## Combat reliability (2.5.2)

Auto Mace now prepares the mace on both Minecraft's click handler and the normal
game-mode attack entry point. Each hit uses the mace before its ordinary attack
packet, then restores your previous slot and returns borrowed inventory weapons.
It requires a real descending fall over 1.5 blocks, a live world, normal reach
and a mace in your inventory. **Activate: Automatic** needs no extra key;
**Hold key** also requires the configured Use key. A slow negative vertical speed
is now sufficient when vanilla Smash conditions are satisfied.

A deliberate falling Auto Mace click cancels and takes over a pending automatic
Stun Slam, without a duplicate delayed mace attack. Stun Slam also cancels promptly
when its target dies, leaves ordinary reach or moves behind a wall. Shields
outside ordinary axe reach no longer cause repeated inventory borrowing. Existing
Stun Slam timing controls, inventory return and saved module toggles are retained.

These checks do not guarantee accepted damage or shield disables on your server.

## Legit mode: visible swaps (2.5.3)

Open **Client settings** and enable **Legit mode**. The actual selected hotbar
weapon stays visible for four client ticks (about 0.2 seconds at normal speed):
spear shortcut → spear → original slot; Auto Mace → mace → original slot;
Auto StunSlam → axe → mace → original slot. Stun Slam uses at least four ticks
for its axe-to-mace delay in this mode, while longer configured delays are kept.
Borrowed inventory weapons return after the visible hold finishes.

The setting is saved across sessions. OFF retains the existing immediate return
behavior. A new action finishes the previous owned return before selecting a
weapon. Manual hotbar changes are preserved, and opening a screen, losing focus
or death releases pending returns through the existing ownership checks. Normal
cooldowns, reach, falling Smash requirements and server damage rules still apply.
This setting displays real selections; it does not add anticheat bypass behavior.
