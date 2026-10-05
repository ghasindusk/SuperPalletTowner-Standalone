"""Validate and assemble the standalone private release candidate."""

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
JAR = ROOT / "build" / "libs" / "super_pallet_towner-1.0.0-rc.1.jar"
NAMES = ["README.md", "README_JA.md", "CHANGELOG.md", "LICENSE", "THIRD_PARTY.md", "RELEASE_REPORT.md"]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate() -> tuple[int, str]:
    assert JAR.is_file(), JAR
    with zipfile.ZipFile(JAR) as archive:
        assert archive.testzip() is None
        names = set(archive.namelist())
        expected = {
            "LICENSE",
            "META-INF/neoforge.mods.toml",
            "data/neoorigins/origins/origin_layers/origin.json",
            "data/starlight/origins/origins/super_pallet_towner.json",
            "assets/starlight/lang/ja_jp.json",
            "assets/starlight/lang/en_us.json",
        }
        assert expected <= names, expected - names
        assert not any("dragonborn" in name.lower() for name in names)
        assert not any(name.endswith(".jar") for name in names)
        assert all(b"dragonborn_path" not in archive.read(name) for name in names if not name.endswith("/"))
        layer = json.loads(archive.read("data/neoorigins/origins/origin_layers/origin.json"))
        assert layer["replace"] is True
        assert layer["origins"].count("starlight:super_pallet_towner") == 1
        assert "neoorigins:monster_tamer" not in layer["origins"]
        origin = json.loads(archive.read("data/starlight/origins/origins/super_pallet_towner.json"))
        assert origin["icon"] == "mega_showdown:ash_cap"
        assert len(origin["powers"]) == 5
        meta = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode("utf-8"))
        assert meta["mods"][0]["modId"] == "super_pallet_towner"
        assert meta["mods"][0]["version"] == "1.0.0-rc.1"
        dependencies = {entry["modId"]: entry for entry in meta["dependencies"]["super_pallet_towner"]}
        assert {"minecraft", "neoforge", "cobblemon", "neoorigins", "mega_showdown"} == set(dependencies)
        assert all(entry["type"] == "required" for entry in dependencies.values())
    suites = [ET.parse(path).getroot() for path in (ROOT / "build" / "test-results" / "test").glob("TEST-*.xml")]
    tests = sum(int(suite.attrib["tests"]) for suite in suites)
    failures = sum(int(suite.attrib["failures"]) + int(suite.attrib["errors"]) for suite in suites)
    assert tests >= 150 and failures == 0, (tests, failures)
    return tests, sha256(JAR)


def main() -> None:
    tests, jar_hash = validate()
    DIST.mkdir(exist_ok=True)
    shutil.copy2(JAR, DIST / JAR.name)
    report = f"""# Standalone release candidate verification

Date: 2026-10-04 JST. Status: private pre-release candidate; public Modrinth upload on hold.

## Confirmed

- NeoForge build: successful with Java 21, offline Gradle dependencies.
- Unit tests: {tests} passed, 0 failed.
- Jar SHA-256: `{jar_hash}`.
- One Jar contains the NeoOrigins primary layer, the Super Pallet Towner Origin, five power descriptions, Japanese and English language entries, and an MIT LICENSE.
- No Dragonborn suborigin layer or `dragonborn_path` payload is present.
- NeoForge metadata requires Cobblemon, NeoOrigins and Mega Showdown.
- Monster Tamer is replaced in the embedded primary layer, and Java still migrates existing Monster Tamer selections.

## Unverified / publication gate

- This exact standalone Jar has not yet been loaded into an isolated world. The existing private instance must not be overwritten while Minecraft is running.
- Replacing NeoOrigins' complete 2.2.29 primary layer may interact with other add-ons that also replace it.
- Modrinth public publication requires a separate eligibility review: project history indicates substantial AI-generated code and page text. The 2026-08-13 Modrinth Content Rules prohibit public projects whose contents are primarily or entirely AI-generated. No upload or project creation has been performed.
- The author credit is provisional until the user provides a preferred name.

## Installation boundary

Do not install this RC alongside the private Starlight Fusion `super_pallet_towner-1.0.0.jar` or `StarlightSuperPalletTowner_OriginPack.zip`. This RC deliberately excludes the Dragonborn Sub-Origin and must first be tested in an isolated instance.
"""
    (ROOT / "RELEASE_REPORT.md").write_text(report, encoding="utf-8")
    with zipfile.ZipFile(DIST / "Super_Pallet_Towner_Standalone_1.0.0-rc.1.zip", "w", zipfile.ZIP_DEFLATED) as bundle:
        bundle.write(DIST / JAR.name, JAR.name)
        for name in NAMES:
            bundle.write(ROOT / name, name)
    source_files = ["build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat", "generate_release_resources.py", "LICENSE"]
    with zipfile.ZipFile(DIST / "Super_Pallet_Towner_Standalone_1.0.0-rc.1_sources.zip", "w", zipfile.ZIP_DEFLATED) as source_bundle:
        for name in source_files:
            source_bundle.write(ROOT / name, name)
        for directory in (ROOT / "src", ROOT / "gradle"):
            for path in directory.rglob("*"):
                if path.is_file():
                    source_bundle.write(path, path.relative_to(ROOT).as_posix())
    checksums = [f"{sha256(path)}  {path.name}" for path in sorted(DIST.iterdir()) if path.is_file() and path.name != "SHA256SUMS.txt"]
    (DIST / "SHA256SUMS.txt").write_text("\n".join(checksums) + "\n", encoding="ascii")
    print(f"tests={tests} jar_sha256={jar_hash}")
    for path in sorted(DIST.iterdir()):
        print(path.name, path.stat().st_size)


if __name__ == "__main__":
    main()
