"""Validate and assemble the standalone release (NeoForge and Fabric Jars)."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import shutil
import tomllib
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parent
DIST = ROOT / "dist"
VERSION = next(line.split("=", 1)[1].strip() for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines() if line.startswith("mod_version="))
NEO_JAR = ROOT / "neoforge" / "build" / "libs" / f"super_pallet_towner-neoforge-{VERSION}.jar"
FABRIC_JAR = ROOT / "fabric" / "build" / "libs" / f"super_pallet_towner-fabric-{VERSION}.jar"
NAMES = ["README.md", "README_JA.md", "CHANGELOG.md", "LICENSE", "THIRD_PARTY.md"]
SHARED = {
    "LICENSE",
    "assets/super_pallet_towner/lang/ja_jp.json",
    "assets/super_pallet_towner/lang/en_us.json",
    "super_pallet_towner_logo.png",
    "assets/super_pallet_towner/textures/item/trainer_emblem.png",
    "data/super_pallet_towner/resonance/type_traits.properties",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def class_texts(archive: zipfile.ZipFile) -> dict[str, bytes]:
    return {name: archive.read(name) for name in archive.namelist() if name.endswith(".class")}


def validate_neoforge() -> None:
    assert NEO_JAR.is_file(), NEO_JAR
    with zipfile.ZipFile(NEO_JAR) as archive:
        assert archive.testzip() is None
        names = set(archive.namelist())
        expected = SHARED | {
            "META-INF/neoforge.mods.toml",
            "super_pallet_towner.mixins.json",
            "data/neoorigins/origins/origin_layers/origin.json",
            "data/starlight/origins/origins/super_pallet_towner.json",
            "assets/starlight/lang/ja_jp.json",
            "assets/starlight/lang/en_us.json",
            "starlight/trainer/TrainerSettings.class",
            "starlight/trainer/NeoOriginsBridge.class",
            "starlight/trainer/SuperPalletTownerNeoForge.class",
        }
        assert expected <= names, expected - names
        assert not any(name.startswith("starlight/trainer/Fabric") or "/fabric/" in name for name in names)
        assert not any("dragonborn" in name.lower() for name in names)
        assert not any(name.endswith(".jar") for name in names)
        assert all(b"dragonborn_path" not in archive.read(name) for name in names if not name.endswith("/"))
        layer = json.loads(archive.read("data/neoorigins/origins/origin_layers/origin.json"))
        assert layer["replace"] is True
        assert layer["origins"].count("starlight:super_pallet_towner") == 1
        assert "neoorigins:monster_tamer" not in layer["origins"]
        origin = json.loads(archive.read("data/starlight/origins/origins/super_pallet_towner.json"))
        assert origin["icon"] == "super_pallet_towner:trainer_emblem"
        assert len(origin["powers"]) == 5
        meta = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode("utf-8"))
        assert meta["mods"][0]["modId"] == "super_pallet_towner"
        assert meta["mods"][0]["version"] == VERSION
        dependencies = {entry["modId"]: entry for entry in meta["dependencies"]["super_pallet_towner"]}
        assert {"minecraft", "neoforge", "cobblemon", "neoorigins"} == set(dependencies)
        # Since 1.2.0 NeoOrigins is optional, but its tested version range is still enforced when present.
        assert all(dependencies[m]["type"] == "required" for m in ("minecraft", "neoforge", "cobblemon"))
        assert dependencies["neoorigins"]["type"] == "optional"
        assert dependencies["neoorigins"]["versionRange"].startswith("[2.2.29,")


def validate_fabric() -> None:
    assert FABRIC_JAR.is_file(), FABRIC_JAR
    with zipfile.ZipFile(FABRIC_JAR) as archive:
        assert archive.testzip() is None
        names = set(archive.namelist())
        expected = SHARED | {
            "fabric.mod.json",
            "super_pallet_towner.fabric.mixins.json",
            "super_pallet_towner.shared.mixins.json",
            "starlight/trainer/SuperPalletTownerFabric.class",
            "starlight/trainer/fabric/mixin/LivingEntityMixin.class",
        }
        assert expected <= names, expected - names
        # NeoForge-only content: the NeoOrigins Origin data, its language entries and the NeoForge classes.
        assert not any(name.startswith(("data/neoorigins/", "data/starlight/", "assets/starlight/", "META-INF/neoforge"))
                       for name in names), sorted(n for n in names if "neo" in n)
        assert not any(name.endswith(".jar") for name in names)
        for name, data in class_texts(archive).items():
            assert b"net/neoforged" not in data and b"com/cyberday1" not in data, name
        meta = json.loads(archive.read("fabric.mod.json"))
        assert meta["id"] == "super_pallet_towner" and meta["version"] == VERSION
        assert meta["depends"]["minecraft"] == "1.21.1"
        assert meta["depends"]["cobblemon"].startswith(">=1.8.1")
        assert "fabric-api" in meta["depends"]


def test_counts() -> tuple[int, int]:
    suites = [ET.parse(path).getroot()
              for module in ("common", "neoforge")
              for path in (ROOT / module / "build" / "test-results" / "test").glob("TEST-*.xml")]
    tests = sum(int(suite.attrib["tests"]) for suite in suites)
    failures = sum(int(suite.attrib["failures"]) + int(suite.attrib["errors"]) for suite in suites)
    return tests, failures


def main() -> None:
    validate_neoforge()
    validate_fabric()
    tests, failures = test_counts()
    assert tests >= 160 and failures == 0, (tests, failures)
    DIST.mkdir(exist_ok=True)
    for jar in (NEO_JAR, FABRIC_JAR):
        shutil.copy2(jar, DIST / jar.name)
    report = f"""# Super Pallet Towner {VERSION} package check

- Unit tests: {tests} passed, 0 failed.
- NeoForge Jar SHA-256: `{sha256(NEO_JAR)}`.
- Fabric Jar SHA-256: `{sha256(FABRIC_JAR)}`.
- NeoForge: the NeoOrigins primary layer, the Super Pallet Towner Origin and five power descriptions; NeoOrigins is optional (2.2.29 – 2.2.32 when installed). No Dragonborn suborigin layer or `dragonborn_path` payload.
- Fabric: no NeoForge or NeoOrigins classes or Origin data; requires Fabric API and Cobblemon 1.8.1+.
- Both: Japanese and English language entries, the resonance data and an MIT LICENSE.
"""
    (ROOT / "RELEASE_REPORT.md").write_text(report, encoding="utf-8")
    with zipfile.ZipFile(DIST / f"Super_Pallet_Towner_Standalone_{VERSION}.zip", "w", zipfile.ZIP_DEFLATED) as bundle:
        for jar in (NEO_JAR, FABRIC_JAR):
            bundle.write(DIST / jar.name, jar.name)
        for name in NAMES:
            bundle.write(ROOT / name, name)
    source_files = ["build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat",
                    "generate_release_resources.py", "LICENSE"]
    with zipfile.ZipFile(DIST / f"Super_Pallet_Towner_Standalone_{VERSION}_sources.zip", "w", zipfile.ZIP_DEFLATED) as source_bundle:
        for name in source_files:
            source_bundle.write(ROOT / name, name)
        for module in ("common", "neoforge", "fabric"):
            source_bundle.write(ROOT / module / "build.gradle", f"{module}/build.gradle")
            for path in (ROOT / module / "src").rglob("*"):
                if path.is_file():
                    source_bundle.write(path, path.relative_to(ROOT).as_posix())
        for path in (ROOT / "gradle").rglob("*"):
            if path.is_file():
                source_bundle.write(path, path.relative_to(ROOT).as_posix())
    checksums = [f"{sha256(path)}  {path.name}" for path in sorted(DIST.iterdir()) if path.is_file() and path.name != "SHA256SUMS.txt"]
    (DIST / "SHA256SUMS.txt").write_text("\n".join(checksums) + "\n", encoding="ascii")
    print(f"tests={tests} neoforge={sha256(NEO_JAR)} fabric={sha256(FABRIC_JAR)}")
    for path in sorted(DIST.iterdir()):
        print(path.name, path.stat().st_size)


if __name__ == "__main__":
    main()
