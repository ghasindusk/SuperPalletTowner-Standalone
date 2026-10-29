# Changelog

## 1.3.3 — 2026-10-29

- Supports **NeoOrigins 2.2.31 and 2.2.32** (2.2.29 – 2.2.30 still work). With these versions, 1.3.2 and older stopped the game from starting. Their main Origin list is unchanged, so the embedded Origin data is the same.
- Supports **Cobblemon 1.8.4** on both NeoForge and Fabric. Both Jars are built against 1.8.4; no code changes were needed.
- Includes 1.3.2, which was released on GitHub only.

## 1.3.2 — 2026-10-26

- NeoForge: a player is a trainer when **any** Origin layer holds Super Pallet Towner, not only the main layer. Modpacks that offer Super Pallet Towner as a sub-origin on a layer of their own lost Type Resonance from 1.1.x on; it works again. With only the main layer (the usual setup) nothing changes.
- Fabric: no change (rebuilt with the same version number).

## 1.3.1 — 2026-10-25

- Supports **Cobblemon 1.8.3** on both NeoForge and Fabric (1.8.1 and 1.8.2 still work). Both Jars are built and tested against 1.8.3; the Cobblemon parts this add-on uses are unchanged, so the game code is the same as 1.3.0.

## 1.3.0 — 2026-10-25

- **New: a Fabric edition** (`super_pallet_towner-fabric-1.3.0.jar`) for Minecraft 1.21.1 with Fabric Loader, Fabric API and Cobblemon 1.8.1 – 1.8.2. There is no NeoOrigins on Fabric, so every player is a Super Pallet Towner, as in the NeoForge edition without NeoOrigins. The same `config/super_pallet_towner-common.toml` switch turns it off for everyone.
- The NeoForge Jar is now named `super_pallet_towner-neoforge-1.3.0.jar`. Its behaviour is unchanged from 1.2.0.
- Fabric differences: the Psychic type's shorter Origin power cooldowns do nothing (there are no Origin powers), the HUD is drawn after the vanilla HUD instead of just above the hotbar, damage changes apply just before absorption hearts instead of just after, and the Ice + Water path asks vanilla's spawn-protection check instead of NeoForge's block-place event.
- Code: the source is split into `common` (shared, compiled against plain Minecraft), `neoforge` and `fabric`. NeoForge events and Fabric callbacks/mixins call the same shared methods.

## 1.2.0 — 2026-10-23

- **NeoOrigins is now optional.** With only Cobblemon (and Kotlin for Forge), every player is a Super Pallet Towner. Turn this off with `allPlayersWithoutNeoOrigins` in the new `config/super_pallet_towner-common.toml`.
- The **Open Pokémon Affinity** key is now **Y** by default (it was unbound). Y is not used by Minecraft, Cobblemon or NeoOrigins. Existing players keep the key they set.
- With NeoOrigins installed nothing changes: you become a Super Pallet Towner by choosing the Origin, and NeoOrigins must still be 2.2.29 – 2.2.30.
- Without NeoOrigins, the Psychic type's shorter Origin power cooldowns have nothing to shorten, so that bonus does nothing.
- Includes 1.1.3 (prepared on 2026-10-23 but not published): that version changed only the version number from 1.1.2.
- Code: all NeoOrigins calls moved into bridge classes that load only when NeoOrigins is present; a new test checks that no other class refers to NeoOrigins.

## 1.1.2 — 2026-10-12

- Supports NeoOrigins 2.2.30 (2.2.29 still works). Built against 2.2.30; its main Origin list is unchanged from 2.2.29, so the embedded Origin data is the same.
- Supports Cobblemon 1.8.2 (1.8.1 still works). Built against 1.8.2.

## 1.1.1 — 2026-10-09

- Fixed a crash on dedicated servers: the mod failed to load while registering network handlers.
- Tested on a dedicated server with only the required mods.

## 1.1.0 — 2026-10-08

- New Origin icon: the Trainer Emblem, a capture ball wearing a red-and-white cap (its own item, `super_pallet_towner:trainer_emblem`).
- Cobblemon: Mega Showdown is no longer required. It was only needed for the old cap icon.
- The mod list shows the new logo.
- README screenshots are now in English.

## 1.0.1 — 2026-10-05

- Mod metadata: author (ghasindusk), project page and issue tracker links, shown in the in-game mod list.
- The release ZIP now includes the rewritten README.

## 1.0.0 — 2026-10-05

- HUD animations: pop-in for newly shown types, a ring burst when a link activates, light flowing along links, and a breathing harmonic star. Toggle with the new HUD editor button or `hudAnimations` in the client config.
- Resonance screen: slot pop, short confirm sounds, a light sweep on the signature badge, and a breathing outline on the active link row.
- The info box wraps long messages instead of cutting them off.
- The Rock + Steel link is now named "Ore Vein"; its effect text stays in the details panel.
- Verified in an isolated instance: Origin selection with the embedded data and icon, resonance screen and HUD checks.

## 1.0.0-rc.1 — 2026-10-04

- First standalone build of the Super Pallet Towner Origin.
- Embedded the NeoOrigins Origin definition, five power descriptions, language entries, and primary layer in one mod Jar.
- Kept automatic migration from NeoOrigins Monster Tamer and the existing Pokémon affinity, link, harmonic, and HUD systems.
- Declared Cobblemon, NeoOrigins, and Mega Showdown as required dependencies.
- Changed the Origin description of resonance links to cover all implemented combinations.
- Build and automated tests pass; isolated game loading of this RC is pending.
