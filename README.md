<img src="docs/images/logo.png" width="128" align="right" alt="Super Pallet Towner icon">

# Super Pallet Towner (超マサラ人)

**A NeoOrigins add-on for Cobblemon.** It replaces NeoOrigins' Monster Tamer with an Origin built for the Cobblemon world. Its abilities work right away: pick up to three types from your party Pokémon, and you and your Pokémon share their power.

Minecraft 1.21.1 / NeoForge · Version 1.1.0 · [日本語の説明はこちら](README_JA.md)

![Origin selection](docs/images/origin_select.png)

## Abilities

| Ability | What it does |
|---|---|
| **Type Resonance** | Choose up to three types from your conscious party Pokémon. Each type gives you its own bonus, such as movement, attack speed, mining, armor, or immunity to fire. |
| **Pokémon's Return Gift** | Party Pokémon of a chosen type get a bonus too, mainly against Minecraft mobs. Normal Pokémon battles are generally unaffected. |
| **Resonance Links and Harmonics** | Link two or three chosen types for a combination effect. There are 24 named links; any other pair still gives a smaller standard bonus. |
| **Trainer's Talent** | Catch rate +3%. |
| **When the Party Falls** | If your whole party faints, you return to a safe respawn point and your party is healed. It does not trigger with an empty party. |

All bonuses have caps, so the Origin stays balanced with other mods.

## Pokémon Affinity screen

Open it with the **Open Pokémon Affinity** key (set it in Controls). Choose the types, check every effect, and link types.

![Resonance effects](docs/images/resonance_effects.png)
![Resonance links](docs/images/resonance_links.png)

### Named links

| Pair | Link | Trio | Harmonic |
|---|---|---|---|
| Rock + Steel | Ore Vein | Fire + Rock + Steel | Master Forge |
| Fire + Steel | Forge Link | Ice + Rock + Ground | Absolute Bastion |
| Fire + Rock | Magma Link | Dragon + Fire + Flying | Dragonfire Glide |
| Water + Grass | Biosphere | Fairy + Grass + Water | Life Bloom |
| Ice + Water | Permafrost | Dark + Ghost + Psychic | Void Mind |
| Bug + Grass | Bio-Harvest | Fairy + Light + Psychic \* | Sacred Harmony |
| Electric + Steel | Electromagnetic | Air + Electric + Flying \* | Tempest Drive |
| Dark + Ghost | Abyss Stalker | Electric + Sound + Steel \* | Resonance Engine |
| Dragon + Fighting | Apex Might | | |
| Psychic + Fairy | Mystic Aegis | | |
| Normal + Fighting | Inner Force | | |
| Air + Flying \* | Jetstream | | |
| Air + Electric \* | Thundercloud | | |
| Fairy + Light \* | Radiance | | |
| Electric + Sound \* | Amplifier | | |
| Sound + Steel \* | Resonant Metal | | |

\* Air, Light and Sound are types added by other Cobblemon mods. These links only appear if such a mod is installed.

## Icon

The Origin uses its own icon, the **Trainer Emblem**: a capture ball wearing a red-and-white cap. You can also get it as an item with `/give @s super_pallet_towner:trainer_emblem`.

## HUD

A small HUD shows your active types and links. Move and resize it in the NeoOrigins HUD editor. New types pop in, a ring bursts when a link activates, and light flows along the links. You can turn the animations off in the HUD editor.

![HUD](docs/images/hud.png)

## Requirements

Install on both client and server.

| Mod | Tested version |
|---|---|
| NeoForge (Minecraft 1.21.1) | 21.1.251 |
| Cobblemon | 1.8.1 |
| NeoOrigins | 2.2.29 |

## Installation

1. Back up your world.
2. Download `super_pallet_towner-1.1.0.jar` from [Releases](https://github.com/ghasindusk/SuperPalletTowner-Standalone/releases) and put it in `mods`.
3. Start the game and choose **Super Pallet Towner** on the Origin screen. Players who chose Monster Tamer are moved to Super Pallet Towner automatically.
4. Set a key for **Open Pokémon Affinity** in Controls.

The Origin data is inside the Jar, so no separate data pack is needed. This add-on replaces NeoOrigins' main Origin list to swap out Monster Tamer. Other add-ons that replace the same list may conflict.

## Status

- Automated tests: 162 passed.
- Checked in game: the Origin screen, the Pokémon Affinity screen and the HUD.
- Also tested with only the required mods (NeoForge, Cobblemon with Kotlin for Forge, NeoOrigins): loads with no errors.
- Not yet tested on a dedicated server or with other add-ons that replace the NeoOrigins Origin list.

Please report problems in [Issues](https://github.com/ghasindusk/SuperPalletTowner-Standalone/issues).

## Building

Requires Java 21. Put the Cobblemon 1.8.1 and NeoOrigins 2.2.29 Jars in `libs/` (used only to compile; they are not bundled), then run `gradlew build`.

## License

MIT. This is an unofficial fan project, not endorsed by the developers of Cobblemon or NeoOrigins. No assets from those mods are included; see [THIRD_PARTY.md](THIRD_PARTY.md).
