"""Validate and assemble the standalone release."""

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
JAR = ROOT / "build" / "libs" / f"super_pallet_towner-{VERSION}.jar"
NAMES = ["README.md", "README_JA.md", "CHANGELOG.md", "LICENSE", "THIRD_PARTY.md"]


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
        assert meta["mods"][0]["version"] == VERSION
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
    report = f"""# Super Pallet Towner {VERSION} package check

- Unit tests: {tests} passed, 0 failed.
- Jar SHA-256: `{jar_hash}`.
- One Jar contains the NeoOrigins primary layer, the Super Pallet Towner Origin, five power descriptions, Japanese and English language entries, and an MIT LICENSE.
- No Dragonborn suborigin layer or `dragonborn_path` payload is present.
- NeoForge metadata requires Cobblemon, NeoOrigins and Mega Showdown.
"""
    (ROOT / "RELEASE_REPORT.md").write_text(report, encoding="utf-8")
    with zipfile.ZipFile(DIST / f"Super_Pallet_Towner_Standalone_{VERSION}.zip", "w", zipfile.ZIP_DEFLATED) as bundle:
        bundle.write(DIST / JAR.name, JAR.name)
        for name in NAMES:
            bundle.write(ROOT / name, name)
    source_files = ["build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat", "generate_release_resources.py", "LICENSE"]
    with zipfile.ZipFile(DIST / f"Super_Pallet_Towner_Standalone_{VERSION}_sources.zip", "w", zipfile.ZIP_DEFLATED) as source_bundle:
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
