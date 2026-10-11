# Azeium 2.2.0

Minecraft Java 1.21.11 client mod for Fabric.

## What is included

- azeium-2.2.0-mc1.21.11.jar — the mod to install.
- azeium-2.2.0-source.zip — the full mod source and Gradle project.
- Open-me.html — a small page linking to the local files.
- README.md — this guide.

## Install

1. Extract this entire download ZIP with Windows “Extract All”.
2. Use Minecraft Java 1.21.11, Java 21, Fabric Loader 0.19.3 or newer,
   and Fabric API for Minecraft 1.21.11.
3. Open your launcher profile's mods folder. For Dawn/Feather, use the mods
   folder belonging to that exact profile, rather than another Minecraft instance.
4. Remove your older Azeium or Spear Controls JAR to avoid duplicates.
5. Copy azeium-2.2.0-mc1.21.11.jar into the mods folder alongside Fabric API.
6. Start Minecraft and press Right Shift to open the Azeium menu.

Install only the mod JAR. The source ZIP and the outer download ZIP do not go
in the mods folder. This is a client mod, not a Paper server plugin.

## Menu and keybinds

- Right Shift opens or closes the menu by default.
- Select a module and click Enable module / Disable module.
- Previous / Next switches between the two pages of modules.
- Click Keybind, then press a keyboard key to assign a module toggle.
- Escape cancels key capture. Delete or Backspace clears a binding.
- Spear's Quick-lunge key is a separate action key, not its module toggle.
- Settings contains Client, Themes and Test scenarios.
- Drag an empty area of the menu header to move the panel.
- Movement works while the menu is open, except during typing or key capture.

## Spear shortcut

1. Put a spear in your hotbar and enable the Spear module while in a world.
2. In Spear, click Quick-lunge key and press the key you want to use.
3. Leave Spear slot on Auto, or select a specific hotbar slot from 1 to 9.
4. Close the menu and press the assigned key.

One press selects the spear, performs one normal jab, and returns to your
previous hotbar slot. Holding the key does not repeat attacks. Actual forward
lunges require the Lunge enchantment and normal vanilla conditions, including
food and server acceptance. A missing spear, active item cooldown, item use,
menus, focus loss, death or disconnect prevent or cancel the shortcut.
The existing manual spear-click behavior is preserved.

## Modules

Spear, Spearometer, Player highlights, Tracers, Auto Clutch, Coordinates, Zoom,
Auto Mace, Shield Drainer, Auto StunSlam, Auto Rejoin, Armor Alerts, Fullbright
and Session HUD.

- Armor Alerts: warns when equipped armor reaches the configured durability
  threshold of 10%, 20% or 30%.
- Fullbright: brightens the vanilla lightmap. Shader packs can override lighting.
- Session HUD: displays active play time and horizontal travel, with a reset.
- Spearometer: displays local speed/distance or click statistics.
- Auto Mace: default Falling smash selects a hotbar mace before the same manual
  entity attack click while descending with sufficient real fall distance.
- Auto StunSlam: an axe attack followed by a normally charged mace strike against
  hostile mobs. Keep both weapons in your hotbar.
- Auto Clutch: block/water fallback and optional Telly modes. Keep the required
  supplies in your hotbar or offhand. Placement still needs normal reach/support.
- Auto Rejoin: five-second retries after an unexpected multiplayer disconnect,
  up to six attempts, plus a separate saved manual rejoin key.

Read the README inside the source ZIP for complete module behavior and limits.

## Source code

Extract azeium-2.2.0-source.zip into a separate folder. The project contains
its README, Gradle wrapper, Java sources, resources and verification sources.
For a normal build, use Java 21 and run `gradlew.bat build` on Windows or
`./gradlew build` on Linux/macOS. The first build needs internet for dependencies.
The built mod appears under build/libs. Do not install a sources-only JAR.

## Troubleshooting

If the HTML shows a page of code, save/open the .html file in Chrome, Edge or
Firefox instead of a text editor or the chat's source preview. The JAR and
source ZIP are also directly available in the extracted download folder.

If Minecraft fails to start, first check the version, Fabric API, and duplicate
Azeium files. This JAR includes MixinExtras 0.5.5 for the earlier startup
compatibility issue. If it still fails, provide the final “Caused by” section
from the affected profile's logs/latest.log or crash report.

## Verification and limits

Unit, controlled functional, GUI, isolated Fabric startup and browser download
checks passed. The full Dawn modpack and live-server gameplay were not tested.
Server rules still determine accepted attacks and movement. This release does
not guarantee an anticheat bypass or reliable placement during severe lag.
For hardcore play, try the modules in a separate test world first; automated
combat and clutch helpers cannot guarantee survival.
