"""Build the NeoOrigins content embedded in the standalone mod Jar.

The main origin layer is intentionally replaced: public release policy is to
replace Monster Tamer with Super Pallet Towner and migrate existing players.
Only the NeoOrigins baseline is copied; no private Dragonborn pack is imported.
"""

from __future__ import annotations

import json
from pathlib import Path
import zipfile


ROOT = Path(__file__).resolve().parent
NEO_JAR = ROOT / "libs" / "neoorigins-2.2.29+1.21.1.jar"
OUT = ROOT / "src" / "main" / "resources"
LAYER = "data/neoorigins/origins/origin_layers/origin.json"
ICON = "super_pallet_towner:trainer_emblem"
POWERS = {
    "type_resonance": (
        "タイプ共鳴",
        "元気な手持ちポケモンのタイプから最大3つを選び、移動・戦闘・採掘などの恩恵を受ける。キー設定の「タイプ共鳴を開く」から変更できる。",
        "Type Resonance",
        "Choose up to three types from conscious party Pokémon for movement, combat, mining and other benefits.",
    ),
    "pokemon_return": (
        "ポケモンへの返礼",
        "共鳴タイプを持つ手持ちポケモン各個体にも対応する効果が届く。主にMinecraftのMobとの戦闘が対象で、通常のポケモン同士のバトルには原則適用されない。",
        "Pokémon's Return Gift",
        "Each matching party Pokémon receives a bonus, mainly against Minecraft mobs. Normal Pokémon battles are generally unaffected.",
    ),
    "resonance_link": (
        "共鳴リンクと重奏",
        "共鳴中の2〜3タイプをリンク枠に置くと、対応する組み合わせの効果が発動する。効果の詳細はタイプ共鳴画面で確認できる。",
        "Resonance Links and Harmonics",
        "Link two or three active types for combination effects. Inspect their details in the Type Resonance screen.",
    ),
    "capture_bonus": (
        "トレーナーの素質",
        "ポケモンの捕獲率が基礎値から3%上昇する。",
        "Trainer's Talent",
        "Pokémon catch rate increases by 3% from its base value.",
    ),
    "party_collapse": (
        "仲間が倒れた時",
        "元気な手持ちがいた状態から全員瀕死になると、安全なリスポーン地点へ帰還して手持ちが回復する。初回や手持ち0匹の状態では発動しない。",
        "When the Party Falls",
        "If a previously conscious party fully faints, return to a safe respawn location and heal the party. This does not trigger for a first-time or empty party.",
    ),
}


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    with zipfile.ZipFile(NEO_JAR) as source:
        layer = json.loads(source.read(LAYER))
    assert layer["origins"].count("neoorigins:monster_tamer") == 1
    assert not any("starlight_origins:dragonborn" in str(x) for x in layer["origins"])
    layer["origins"] = [
        "starlight:super_pallet_towner" if x == "neoorigins:monster_tamer" else x
        for x in layer["origins"]
    ]
    layer["replace"] = True
    write_json(OUT / LAYER, layer)

    origin = {
        "name": "origins.starlight.super_pallet_towner.name",
        "description": "origins.starlight.super_pallet_towner.description",
        "icon": ICON,
        "impact": "high",
        "order": 39,
        "powers": [f"starlight:trainer_{name}" for name in POWERS],
        "upgrades": [],
    }
    write_json(OUT / "data/starlight/origins/origins/super_pallet_towner.json", origin)
    for name in POWERS:
        write_json(
            OUT / f"data/starlight/origins/powers/trainer_{name}.json",
            {"type": "neoorigins:attribute_modifier", "attribute": "minecraft:luck", "amount": 0.0, "operation": "add_value"},
        )

    languages = {
        "ja_jp": {
            "origins.starlight.super_pallet_towner.name": "超マサラ人",
            "origins.starlight.super_pallet_towner.description": (
                "とある少年は、ポケモンと旅を重ねるうちに、仲間たちの力をその身に宿したという。"
                "深まる絆は互いの力を呼び覚まし、時にその少年を、ポケモンをも凌ぐ存在へと変える。"
                "\n\n手持ちのタイプから最大3つを選んで共鳴し、トレーナーとポケモンが互いに力を分け合う。"
            ),
        },
        "en_us": {
            "origins.starlight.super_pallet_towner.name": "Super Pallet Towner",
            "origins.starlight.super_pallet_towner.description": (
                "A boy who traveled with Pokémon came to carry their power within him. "
                "As their bond deepened, he sometimes surpassed even Pokémon.\n\n"
                "Choose up to three party types to share power with your Pokémon."
            ),
        },
    }
    for name, (ja_name, ja_description, en_name, en_description) in POWERS.items():
        for locale, title, description in (
            ("ja_jp", ja_name, ja_description),
            ("en_us", en_name, en_description),
        ):
            languages[locale][f"power.starlight.trainer_{name}.name"] = title
            languages[locale][f"power.starlight.trainer_{name}.description"] = description
    for locale, strings in languages.items():
        write_json(OUT / f"assets/starlight/lang/{locale}.json", strings)

    assert layer["origins"].count("starlight:super_pallet_towner") == 1
    assert "neoorigins:monster_tamer" not in layer["origins"]
    print(f"Generated 9 resource files in {OUT}")


if __name__ == "__main__":
    main()
