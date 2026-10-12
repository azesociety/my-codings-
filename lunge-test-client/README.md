# aze's hub 2.5.4

Auto StunSlam offers **Click shield** and **Look at shield** triggers, with
saved mace/repeat delays from **0–10 ticks**. Zero sends the axe and mace attacks
in the same client tick. Normal combat also works while flying with an Elytra.
The spear shortcut dispatches on the key/mouse press, with a saved **0–10 tick**
return delay in Legit mode.

Retained from 2.4.1: restoring a saved spear shortcut no longer resolves its keyboard
label before GLFW initializes. This fixes the reproduced "GLFW library is not
initialized" launch failure with saved keyboard shortcuts. Existing settings
and shortcuts are retained; no config reset is needed.

New in 2.4.0: a full shared visual refresh with licensed Allura cursive brand and
module headings, readable Open Sans controls, satin pastel gradients, rounded
surfaces, softer highlights and consistent styling across menus and settings.
Sixteen modules fit across two pages. Performance HUD displays actual client FPS
and server-reported player latency without sending extra requests. Its ON/OFF toggle is saved
and has its own saved toggle bind. Client settings includes Disable all modules.

Retained from 2.3.0: Auto Mace always uses falling Smash and returns to the previous
hotbar slot after that hit. Its Reach control cycles Vanilla (default), then
3–6 blocks in half-block steps. This changes local eligibility for an already
selected entity; it does not expand crosshair picking or server permissions.
A spear-selected distant target may still be rejected by the server after switching
to a mace. There is no promise of extra damage or anticheat acceptance.

To use the spear shortcut, assign its Quick-lunge key or mouse button and keep a
spear in your hotbar. One press selects that spear, runs one ordinary jab and
returns to your previously selected slot. Holding the key does not repeat attacks.
Spear's regular Keybind still toggles the module. The Quick-lunge key is a separate
action bind and works even with Spear OFF; normal vanilla cooldowns still apply then.
Spear slot offers Auto (held spear first, then first hotbar spear) or
an explicit slot 1–9. An explicit missing spear does not fall back to another slot.
No inventory moving, enchantment adding, velocity edits or fabricated lunges occur.
Actual forward lunges require a Lunge enchanted spear and vanilla conditions,
including food and server acceptance. Menus, focus loss, death, disconnect,
item use and item cooldowns cancel or prevent the shortcut.

Armor Alerts shows a quiet notice when equipped armor reaches a saved 10%, 20%
or 30% durability threshold. It does not equip, repair or move items. Fullbright
brightens the vanilla lightmap while retaining stored gamma and normal world
lighting; shader packs may supply their own lighting. Session HUD shows active
play time and horizontal distance for the current connection, with its own reset.
Pause/focus loss, teleports and long sampling gaps do not inflate travel; changing
dimensions clears the movement anchor and a new connection starts a new session.
View modules have saved ON/OFF toggles and independent keybinds.

A Minecraft Java 1.21.11 Fabric client mod with a Right Shift module menu,
manual spear controls, a travel spearometer, configurable player outlines,
independent tracers, Auto Clutch with automatic Telly bridging, Coordinates, Zoom,
AutoMace, Auto StunSlam, Armor Alerts, Fullbright, Session HUD and configurable Shield Drainer trials. The Fabric mod listing includes the cat icon.
Requires Java 21, Fabric Loader **0.19.3 or newer**, and Fabric API for 1.21.11.
MIT licensed.

## Smooth movable menus and movement

aze's hub screens use bundled Allura cursive display headings and Open Sans controls
with rounded satin gradient panels.
Drag an empty part of the header to move the panel; its controls move with it and
remain inside the window after resizing. Position is retained while switching
tabs in the same screen. Use your Minecraft movement keys to walk, jump, crouch
and sprint while the menu is open. Typing in a colour field, keybind capture,
focus loss and other Minecraft screens suppress menu movement. The menus do not
pause a singleplayer world. Attacks and item use are not forwarded through the UI.
Open Sans is distributed under Apache-2.0; Allura under SIL OFL 1.1.
Both fonts include their attribution and license in the source and mod JAR.

## Themes, client settings and combat tests

Open the module menu with Right Shift (or your saved key), then click **Settings**.
The **Client**, **Themes** and **Test scenarios** tabs provide the new controls.
Client offers click-then-key GUI binding, glass opacity and log session controls.
Bindings and themes save to the existing client configuration automatically.

Themes includes Aurora, Ocean, Sunset, Rose, Forest and Graphite, plus a custom
pair of `#RRGGBB` colours. The menu, settings, Coordinates and Spearometer share
that palette. Player outline and tracer colours remain independently configurable.

Shield Drainer runs finite, crosshair-targeted trials (1–60 seconds), requesting
1–40 clicks per second. Keep a mace in the hotbar; axe-to-mace trials also need an
axe. Shield-only filtering is ON by default. Choose mace clicks or axe-to-mace,
reaction delay (0–200 ticks) and switch interval (1–200 ticks). The requested rate
is scheduled on normal 20 Hz client ticks; settings, reach, item cooldowns, target
availability and server handling can reduce the effective rate. Partial-charge
attacks are logged with their actual vanilla attack strength. No damage, shield
break or server detection result is guaranteed. Opening a menu, losing focus,
manual slot changes, death or disconnect stop an armed trial. Other combat/bridge
modules must be disabled to avoid overlapping trial actions.

Auto Mace exposes Smash, Reach and activation controls. Smash has no queued extra hit.
Stun Slam has no timing or strategy settings: enable it and aim at a target actively blocking with a raised shield.
It borrows an axe and mace from your main inventory when needed, then sends the uploaded add-on's quick sequence. Server damage rules can reduce the second hit.
Legacy Stun Slam preferences are ignored when loading existing configurations.

Traces are buffered JSONL files in the instance's `logs/azeium-tests` directory.
They record wall-clock time, elapsed nanoseconds, client tick, actions, selected
slots, target identifiers and attack strength. Server chat visible to the client
is recorded as observations. **Flag** and **No flag** add explicit user annotations;
server-only anticheat alerts must be compared separately with server logs.
`serverVerdict: unverified` never implies a successful bypass or confirmed flag.
Logs are capped at 12,000 entries per session. Start a new session when needed.

## Startup compatibility

aze's hub 2.5.1 includes MixinExtras **0.5.5** inside its JAR and requires that version
or newer. This resolves the reproduced `ArrayList cannot be cast to AnnotationNode`
exception in `FactoryRedirectWrapperMixinTransformer` when MixinExtras 0.5.4
processes the newer Redirect annotation arrays used with Sponge Mixin 0.17.4.
Fabric Loader 0.19.3 is supported. Replace the previous aze's hub JAR with this build.

If a custom launcher still fails, check whether its new log says
`Initializing MixinExtras ... version=0.5.5` and capture the final `Caused by` section.
The supplied Dawn/Feather profile's full set of 118 mods is not available here;
validation covers the reproduced library failure and an isolated Fabric client.

## Install and open the menu

Remove previous Spear Controls, Lunge Test Controls, Spear Test Lab, Spear Sprint
and combined test JARs, including previous aze's hub versions. Put
`azes-hub-2.5.4-mc1.21.11.jar` in your Minecraft
instance's `mods` folder alongside Fabric API. This is a client mod, not a Paper
plugin. EntityCulling is not included or required.

Press **Right Shift** to open or close **aze's hub**. Select a module in the sidebar,
then use its ON/OFF control. Selecting a module does not enable it.

The translucent menu's **Opacity** control cycles through **55%, 70% and 85%**;
the default is **70%**. Use **Menu key** to change the menu binding.

Each of the sixteen modules has its own **Keybind** control. Click it, then press the
keyboard key to assign. **Escape** cancels capture; **Delete** or **Backspace**
clears a module binding. Duplicate aze's hub module/menu keys are rejected. Module
bindings start at **None**, fire once per key press, and only work while playing
in a live world with no menu open. Holding a key does not repeatedly toggle it.

The uploaded `gonuts-cli-0.1.0.jar` is an auction-analysis command-line application,
with no Fabric entrypoint or Minecraft menu framework. aze's hub is therefore a
standalone Fabric mod rather than an extension embedded in that CLI. The supplied
Gonuts source ZIP exceeded the environment's 32 MiB transfer limit and could not
be inspected. `GONUTS-INPUT-ANALYSIS.md` in the download bundle records this boundary.

## Modules

**Spear** removes the local vanilla spear attack cooldown while enabled. Close
the menu and click manually at your own pace: there is no autoclicker or generated
attack input. Other weapons retain their normal recharge. The module defaults to OFF until you save an ON toggle,
requires a live world to enable, and turns OFF on death or connection replacement.
OFF restores the vanilla client cooldown. Servers still decide which attacks and
movement they accept; this setting cannot remove server cooldown checks.

**Spearometer** toggles the HUD. Its default Travel mode shows horizontal speed
in blocks per second, peak speed, total distance and a recent-sample graph. Mode
cycles through Travel, Clicks and Both; Corner moves the HUD between four corners.
Reset clears measurements. Export saves local JSON snapshots in
`logs/spearometer` and records the filename in the client log. Click measurements
include manual spear attempts and locally started attacks, including OFF baselines.
The HUD works independently of the Spear module and the held item. It is hidden
in menus and with F1.

Travel includes walking and other horizontal movement, not just Lunge effects.
It uses a rolling speed window; server position updates, pauses, invalid samples,
gaps above two seconds and jumps above 32 blocks reset or exclude a movement
sample. The graph contains 20 recent observations. These are local measurements,
not proof of server-accepted movement or anticheat evasion.

**Player highlights** draws outlines through blocks around other players loaded
in your current client world. Choose Cyan, Purple, Red, Green or Gold, or enter a
custom `#RRGGBB` value and press Apply. Colors are fully opaque, including black.
The module defaults to OFF until you save an ON toggle. Turning it OFF restores vanilla outlines and glowing team
colors. It never highlights your own player or changes server entity metadata.
The **Range** control cycles through **128, 256, 512 and 1024 blocks**, with
**256** as the default. Within that range, highlights can render beyond vanilla's
entity distance limit and through terrain occlusion; camera frustum limits still
apply. Players not sent to or loaded by the client cannot be highlighted.

**Tracers** draws lines from the camera's forward center toward other loaded
players. It has its own ON/OFF control, color presets, `#RRGGBB` custom color,
keybind and range setting, independent of Player highlights. Its range choices
are **128, 256, 512 and 1024 blocks**, default **256**. Lines remain visible through
terrain and fog. The module never targets your own player, obtains positions from
the current client world, and defaults to OFF until you save an ON toggle. Increasing the range does not obtain
players the server has not sent to the client.

**Auto Clutch** uses both bridge blocks and water by default. Its **Blocks + water**
mode checks your next movement before your feet drop below a bridge edge, then
places a solid block beneath that step when a real adjacent support face is visible
and in reach. It uses a water bucket for longer falls, aiming near the predicted
landing position. You can also choose **Blocks** or **Water bucket** separately.
The first upgrade from 1.7.0 switches its old default to **Blocks + water**; later
mode choices are saved. **Blocks + water** remains the default in this release.

For jumping Telly-style bridging, press **Right Shift**, select **Auto Clutch**,
cycle **Mode** to **Telly bridge + clutch**, then enable the module. This
mode maintains the bridge's block height during your jump and places reachable
supported blocks beneath the next horizontal step while you rise, pass the apex
or descend. It keeps the water-bucket fallback for a longer fall.

Choose **Telly only** for the same jump bridge assistance without automatic water
or emergency fall recovery. You still control walking, jumping and the camera
in both of these assistance modes.

Choose **Auto Telly** to let aze's hub perform the bridge sequence: walk forward,
jump, visibly look back and down to place blocks, then face forward and repeat
after landing. Stand on a full solid bridge block, keep solid blocks in your
hotbar or offhand, face the direction you want to extend the bridge, enable the
module and close the menu. The direction you face at the start defines the path.
This mode places bridge blocks only, with no automatic water fallback.

Manual mouse movement, movement keys, jump/crouch or an attack/use input stops
the automatic sequence and returns control to you. Toggle the module OFF and ON
to start another run. Its assigned module key also provides a quick stop. A menu
or lost window focus pauses movement and releases the helper's controls; it can
start again on solid ground when play resumes. Missing blocks, an obstructed path
or unsafe support stop the run. Auto Telly uses normal jumping and gravity, and
cannot create unsupported bridge blocks or prevent falls during a long freeze.

These are separate modes; **Blocks + water** remains the initial mode. All Telly
modes expose a **Speed** control: **Careful** allows one,
**Balanced** two, and **Fast** four supported placements per game tick. **Fast**
is the default Telly speed. This helps fill short gaps while moving quickly;
the helper still needs confirmed support and reachable placement faces.

Telly modes use normal block interactions, with a shared per-tick budget and
bounded retries when an earlier placement has not appeared. Render callbacks
cannot duplicate the tick's placement allowance. The helper needs actual
adjacent support, normal placement reach and blocks in your hotbar or offhand;
it cannot place unsupported or unreachable blocks. Lag can still delay the
world's response and cause a fall, and a large lunge may move past reachable
support before the next tick. Faster attempts and retries do not guarantee recovery.

Keep **solid blocks and a filled water bucket** in your hotbar or offhand. Enable
Auto Clutch through its ON/OFF control or assigned key after joining your world.
The module restores your selected slot after using a supply. Assisted clutch modes
restore the camera immediately; Auto Telly shows the full turn and return while
bridging. Blocks need valid nearby support; water needs a reachable landing
surface and does not
work where water evaporates. The helper respects item cooldowns and placement
permissions, and does not erase existing fall damage or create supplies.

Retry limits are separate for block and water attempts. A locally consumed
interaction does not prevent a later water fallback when placement fails. Auto
Clutch defaults to OFF until you save an ON toggle and stops on death, disconnect or world replacement. It pauses
in menus, while unfocused or using an item, and during creative/spectator flight,
elytra flight or riding. Placement is still subject to the server's response.

**Coordinates** shows X/Y/Z block position and facing direction above the hotbar.
It has an independent toggle and keybind, and hides with F1 or an open menu.

**Zoom** smoothly narrows the view at **2x**, **4x** or **8x** magnification. Toggle
it again to smoothly restore the view, or assign its own module key. Your normal
FOV setting is retained. Zoom defaults to OFF until you save an ON toggle and its magnification preference is saved.

**Performance HUD** shows client FPS and reported server ping. Missing network
data is shown as unavailable; singleplayer says Local world. The compact panel
sits below Session HUD and avoids the travel/armor panels at tested GUI sizes.
It sends no probe packets and does not alter networking or game tick speed.

**AutoMace** always uses **Action: Smash**. With more than 1.5 blocks of real
fall distance and downward movement, your entity attack click selects a
mace from your hotbar or main inventory before that same hit, then restores your previous slot. No second attack
or extra recharge delay is added. Grounded, rising and short-fall clicks retain
the held item. Wind Burst requires an enchanted mace and a successful server hit.
Your own intervening slot change is preserved. A cancelled vanilla attack also
releases the temporary selection. Old alternate action preferences migrate to Smash.

**Reach** cycles Vanilla (default), 3, 3.5, 4, 4.5, 5, 5.5, 6 blocks and back to
Vanilla. Visibility and item cooldowns remain checked. Extended reach applies only
to an entity already selected by the crosshair: it does not extend crosshair picking.
The server may reject a distant mace hit even when a spear could reach that entity.

**Auto StunSlam** requires a living target actively blocking with a raised shield.
Choose **Trigger: Click shield** to require a deliberate attack or **Look at
shield** to run while aiming at it. An axe and mace may be in your hotbar or main
inventory; ordinary inventory SWAP actions borrow and return them as needed.

**Mace delay** and **Repeat delay** cycle through 0–10 client ticks. Default for
fresh settings is 0. Zero sends axe then mace without waiting for another tick.
Positive delays are honored, including in Legit mode. Death, disconnect, invalid
targets, menus and manual slot changes retain the existing cancellation guards.
Team friendly fire, normal reach and line of sight remain checked.

Stun Slam permits Elytra flight. The mace hit still follows vanilla damage rules;
a flying hit does not automatically qualify as a Smash. Short delays can produce
reduced or rejected damage. The module does not change server cooldowns or damage
immunity. Ordinary zombies and wardens do not block with shields, so they do not
trigger this module.

AutoMace remains a same-click falling Smash helper with original-slot return.
Its optional Hold key mode requires its Use key (default R) during the falling hit.

The **Liquid Glass** menu uses rounded translucent panels, layered reflections,
soft hover highlights, quick opening fades, rounded state badges and an animated
selection rail. Equal glass rows share draw calls, and unchanged button labels
are reused. Its sixteen module tabs span two pages and fit the minimum 320x240 GUI size.

## Rejoin controls

The **Auto Rejoin** module reconnects to the last ordinary multiplayer server after
an unexpected disconnection. Retries wait five seconds and stop after six attempts.
Going Back, cancelling or intentionally leaving stops the retry sequence. Singleplayer
worlds and Realms are excluded. The server address is remembered only during the
current client session; the client uses vanilla connection handling.

The same module page has **Rejoin now** and a separate **Rejoin key**. Click the
bind and press a keyboard key; Escape cancels, Delete clears, and conflicts with
other aze's hub bindings are rejected. The manual rejoin key saves between launches.
The module's regular Keybind toggles automatic rejoining; it is separate from the
manual rejoin key. Auto Rejoin restores its saved ON/OFF toggle each launch.

## Preferences and testing limits

`config/azeium.json` saves highlight/tracer colors and ranges, HUD visibility,
mode and corner, all module keybinds, the menu key, menu opacity and Auto Clutch
mode and Telly speed, Zoom magnification and combat preferences, including the
AutoMace action and separate use keys, the Quick-lunge action key and spear slot,
the manual Rejoin key, and Armor Alerts' durability threshold.
Spear, Player highlights, Tracers, Auto Clutch, Coordinates, Zoom, AutoMace and
Auto StunSlam ON/OFF states are saved and restored when you join another world or server. Spearometer retains its saved visibility
preference. When aze's hub's config does not exist, it reads the old
`config/spearometer.json` as a fallback; the next
preference change writes `azeium.json`. Measurements stay local and are never
uploaded.

No live Paper/Grim bypass or fix for the reported Post “held item change” alert has
been confirmed. Spear controls retain normal manual input timing and do not cancel,
queue or reorder packets. Auto Clutch uses vanilla item interaction methods.
Auto Telly drives walking/jumping input and visible camera turns while the game
computes actual motion and fall distance. Combat helpers use ordinary
attacks and item interactions; they do not bypass server cooldown or combat
validation. Compare OFF and ON trials with
your server's accepted attacks, movement and complete anticheat logs; client HUD values alone cannot
establish server acceptance. The bundle's `VERIFICATION.md` records completed
build and fixture checks without substituting for a live server test.

## Build and verification

Use JDK 21 in this directory:

```sh
./gradlew build
```

The resulting mod JAR is in `build/libs`. With a graphical display available,
run `./gradlew runGuiVerification` to exercise the client verification fixture.
Verification code is excluded from the production JAR.

For the provided cloud environment:

```sh
export JAVA_HOME=/workspace/toolchains/jdk-21.0.12.1
export GRADLE_USER_HOME=/workspace/.gradle-spear
./gradlew build
DISPLAY=:99 ./gradlew runGuiVerification
```

The display must already be running. The root repository README documents the
cloud proxy/trust options when dependency downloads need them. Ordinary machines
do not need those environment-specific options.

## Inventory swaps and shield hits (2.5.1)

Auto Mace and Stun Slam find weapons in inventory slots 9–35 as well as the hotbar.
They use ordinary inventory SWAP actions to borrow tools, preserve displaced
hotbar stacks, and return the weapons when finished or cancelled. Close chests and
other containers and clear the cursor first. Manually moved stacks are preserved;
returning tools is deferred while another container is open.

Stun Slam requires an actively raised shield when its automatic sequence starts.
Aim within ordinary axe range, on the shield-facing side of the opponent.
The server decides whether the shield blocks, is disabled, or accepts either hit.
Its configurable mace follow-up uses the uploaded add-on's short timing instead of
the previous charged manual-click combo. Settings from that older combo are ignored.

## Autoclicker

Open the module on the second Modules page. **Mode** selects **Toggle** (click
continuously until disabled) or **Hold mouse** (click only while the selected
mouse action is physically held). **Click** selects left/attack or right/use.
**Rate** cycles 1, 2, 4, 6, 8, 10, 12, 16 and 20 requested clicks per second.
Assign its normal module keybind for quick ON/OFF control. Mode, button and rate
are saved; the module restores its saved toggle on launch and stops on death or world changes.
Menus, focus loss, held item use and other active gameplay actions pause it.
It follows client ticks and never catches up with a burst after lag. Normal item
cooldowns and server hit acceptance still apply. AFK clicking requires the game
window to retain focus.

Auto Telly now returns the visible view forward during descent once support is
confirmed, keeps ordinary sprint-jump momentum, and sends its current visible
rotation before using a block face. It uses blocks in your hotbar/offhand, normal
reach and ordinary placement. Start on a full block, face your bridge direction,
select **Auto Telly** in Auto Clutch, enable and close the menu. Do not hold movement
or attack keys while the pilot runs; manual input stops it. Server placement
restrictions and lag can still interrupt a bridge; test without risking a hardcore
world first.

Validation exercises actual Minecraft item prediction, packet ordering, inventory
swaps, keyboard input, jump impulse, acceleration, crouch edge safety and collision
in controlled fixtures. These fixtures are not a joined multiplayer-server test.

## Saved toggles and Stun Slam timing (2.5.1)

The client is now named **aze's hub**. In Auto StunSlam, **Mace delay**
cycles 0–10 client ticks between the axe and mace attacks. **Repeat delay**
cycles 0–10 client ticks before another automatic combo. Fresh settings default to
0 and 0 ticks. Twenty client ticks are about one second at normal speed;
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
Auto StunSlam → axe → mace → original slot. Stun Slam uses the configured number of ticks
for its axe-to-mace delay in this mode, while longer configured delays are kept.
Borrowed inventory weapons return after the visible hold finishes.

The setting is saved across sessions. OFF retains the existing immediate return
behavior. A new action finishes the previous owned return before selecting a
weapon. Manual hotbar changes are preserved, and opening a screen, losing focus
or death releases pending returns through the existing ownership checks. Normal
cooldowns, reach, falling Smash requirements and server damage rules still apply.
This setting displays real selections; it does not add anticheat bypass behavior.

## Current fast controls (2.5.4)

In **Auto StunSlam**, choose **Trigger: Click shield** to require a deliberate
attack on an actively raised shield, or **Trigger: Look at shield** to start
while aiming at one. **Mace delay** and **Repeat delay** cycle through **0–10
client ticks**. Fresh settings default to 0; existing valid delay choices remain.
Zero sends axe then mace in the same client tick, while 1–10 waits for the
configured client-tick callbacks. Looking alone never attacks in Click mode.
Normal axe/mace attacks are allowed during Elytra flight. Vanilla falling Smash
conditions and server damage/immunity rules are not changed.

The **Spear** shortcut now acts immediately on a deliberate key or mouse-button
press on Minecraft's main thread, rather than waiting for END tick. Its saved
**Return delay** cycles **0–10 ticks** in Legit mode; default **0** selects the
spear, sends one ordinary jab and restores the original slot during that same
input event. A positive delay briefly shows the selected spear before returning.
Hold repeats and idle ticks still never dispatch extra jabs.

Legit mode honors zero Stun Slam delay instead of forcing a four-tick axe hold.
At zero, axe and mace selections occur in the same tick, so the axe cannot appear
in a separate rendered frame. The mace still uses Legit mode's visible return.
Choose a positive mace delay to see the axe phase.
