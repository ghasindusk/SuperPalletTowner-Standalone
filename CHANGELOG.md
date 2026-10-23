# Changelog

## 1.2.0 — 2026-10-23

- **NeoOrigins is now optional.** With only Cobblemon (and Kotlin for Forge), every player is a Super Pallet Towner. Turn this off with `allPlayersWithoutNeoOrigins` in the new `config/super_pallet_towner-common.toml`.
- With NeoOrigins installed nothing changes: you become a Super Pallet Towner by choosing the Origin, and NeoOrigins must still be 2.2.29 – 2.2.30.
- Without NeoOrigins, the Psychic type's shorter Origin power cooldowns have nothing to shorten, so that bonus does nothing.
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
