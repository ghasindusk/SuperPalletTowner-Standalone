# Super Pallet Towner — NeoOrigins add-on

Version `1.0.0` for Minecraft 1.21.1 / NeoForge. This build contains the Super Pallet Towner primary Origin and its Pokémon type resonance system. The Dragonborn Sub-Origin and its migration code are excluded.

## Features

- Choose up to three types represented by conscious party Pokémon. The selected types grant player affinity effects; matching Pokémon receive capped bonuses, mainly against Minecraft mobs.
- Link two or three selected types for resonance combinations. The UI shows the type effects and active links; a small HUD shows active type symbols and can be positioned and resized through NeoOrigins' HUD editor.
- HUD and resonance screen animations: new types pop in, a ring bursts when a link activates, light flows along links, and the harmonic star breathes. The resonance screen plays short confirm sounds. Animations can be turned off in the HUD editor (`hudAnimations` in the client config).
- Existing `neoorigins:monster_tamer` selections migrate to `starlight:super_pallet_towner` on the primary Origin layer. A party with no Pokémon, or a first-time all-fainted party, does not trigger the party-collapse return.
- The NeoOrigins power list, language files, and Origin layer are embedded in the mod Jar. No separate OriginPack ZIP is required.

## Required mods

| Dependency | Tested version | Why |
|---|---|---|
| Minecraft / NeoForge | 1.21.1 / 21.1.251 | Loader and game target |
| Cobblemon | 1.8.1 | Party and Pokémon integration |
| NeoOrigins | 2.2.29 | Origin selection and HUD editor |
| Cobblemon: Mega Showdown | 1.2.0 | Ash cap item used as the Origin icon; its asset is not bundled |

Install the same dependencies and this Jar on both client and server. This version was built and tested against the versions above; broader compatibility is untested.

## Installation

1. Back up your world and current `mods`/`config/originpacks` configuration.
2. Place `super_pallet_towner-1.0.0.jar` in `mods` on both client and server. Keep only one `super_pallet_towner` Jar.
3. If migrating from the Starlight Fusion private build, remove its old `StarlightSuperPalletTowner_OriginPack.zip` from `config/originpacks`. This version embeds the Origin data and does not include the Dragonborn path.
4. Launch the game, select **Super Pallet Towner** on the primary Origin layer, and bind **Open Type Resonance** in Controls. Existing Monster Tamer selections are migrated automatically when the world loads.

The add-on replaces the built-in Monster Tamer entry in NeoOrigins' primary Origin layer. The current implementation copies NeoOrigins 2.2.29's complete base layer to do so; other Origin add-ons that replace the same layer may need a compatibility patch. This package does not contain or modify the user's world saves.

## Status

- Build and automated tests pass (JUnit 162/0).
- In an isolated NeoForge instance, the Jar loads without the old OriginPack: the Origin appears in the selection screen with the Mega Showdown cap icon, and the resonance screen and HUD checks pass.
- Not yet tested on a dedicated server or alongside other add-ons that replace the NeoOrigins primary layer.

## Building

Requires Java 21. Put the Cobblemon 1.8.1 and NeoOrigins 2.2.29 Jars in `libs/` (compile-only, not bundled), then run `gradlew build`. `package_release.py` validates the Jar and writes the release files to `dist/`.

This add-on is an independent fan project. It is not endorsed by the maintainers of Cobblemon, NeoOrigins, or Mega Showdown. The Mega Showdown cap icon is referenced by item ID; no Mega Showdown model or texture is included.
