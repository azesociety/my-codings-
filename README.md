# aze's hub 2.5.4

Minecraft Java 1.21.11 · Fabric Loader 0.19.3+ · Java 21 · Fabric API.

- [Download JAR](https://github.com/azesociety/my-codings-/raw/refs/heads/main/azes-hub-2.5.4-mc1.21.11.jar)
- [Source](lunge-test-client/)
- [Full controls and build instructions](lunge-test-client/README.md)

Replace your previous hub/Azeium JAR; keep one version in the mods folder.

**Stun Slam:** choose **Trigger: Click shield** (default) or **Look at shield**.
Mace delay and repeat delay both support **0–10 ticks**. Zero sends axe then mace
in the same client tick. Normal attacks also work during Elytra flight.
Weapons may be in your hotbar or main inventory; close other containers first.

**Spear:** the shortcut acts on the deliberate key/mouse press. In Legit mode,
**Return delay: 0** selects the spear, jabs and returns during that same input
event; choose 1–10 ticks to show the selected spear briefly. Hold repeats do not
attack. Forward movement still requires Lunge enchantment and vanilla conditions.

**Legit mode** is in Client settings. At 0 Stun Slam delay the axe cannot appear
in a separate rendered frame; choose a positive delay to see it. Module toggles,
keybinds, trigger modes and delays are saved across worlds and restarts.

Normal reach, item cooldowns and server damage rules apply. Zero delay does not
guarantee extra damage or shield disables; Elytra flight does not automatically
qualify a mace hit as a vanilla Smash.

Build with Java 21: `gradlew.bat build` or `./gradlew build` in `lunge-test-client`.
