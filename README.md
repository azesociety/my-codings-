# Azeium 2.5.0

Minecraft Java 1.21.11 · Fabric Loader 0.19.3+ · Java 21 · Fabric API.

- [Download the mod JAR](https://github.com/azesociety/my-codings-/raw/refs/heads/main/azeium-2.5.0-mc1.21.11.jar)
- [Browse the source project](lunge-test-client/)
- [Installation and controls](lunge-test-client/README.md)

Replace the older Azeium JAR in your instance's mods folder. Build the source
with Java 21 using `gradlew.bat build` (Windows) or `./gradlew build` (Linux/macOS)
inside `lunge-test-client`. The first build downloads dependencies.

New in 2.5.0: inventory-backed combat swaps, corrected slot synchronization, descending Auto Telly turns, and an Autoclicker with toggle/hold modes and left/right click selection.

Stun Slam now uses your uploaded add-on's automatic crosshair behavior: enable it and aim at an actively raised shield. It attacks with an axe, follows with a mace on the next client tick, returns your slot, and waits 12 ticks before retriggering. It supports normal multiplayer players and inventory weapons. Server damage rules still apply.

Startup fix: saved spear keyboard binds no longer look up key names before GLFW initializes. Keep your existing settings.

Design: shared satin glass redesign with licensed Allura cursive headings,
Performance HUD for live FPS and reported ping, and Disable all modules.
Existing same-click Smash, slot return, keyboard/mouse spear shortcuts and
optional local reach settings are retained. Server rules determine valid hits.

Forward lunges require the Lunge enchantment and normal vanilla conditions.

An axe and mace may be in your main inventory or hotbar. Borrowed tools return after the combo; close other containers first. Autoclicker settings are saved, but it starts OFF. Server rules still determine shield disables, damage and placement.
