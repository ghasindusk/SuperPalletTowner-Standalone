# Changelog

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
