"""Regression checks for the target-specific Forge client integration."""
import json
from pathlib import Path
import tempfile
import unittest

import generate_mods
import generate_sources
from version_config import ALL_VERSIONS, MODS_DIR


class ForgeClientGenerationTest(unittest.TestCase):
    def test_regeneration_preserves_client_entrypoint(self):
        target = Path(MODS_DIR) / "1.20.1/forge/src/main/java"
        entrypoint = target / generate_sources.PKG / "ModDevMcpMod.java"
        self.assertEqual(
            entrypoint.read_text(encoding="utf-8"),
            generate_sources.get_forge_mod_template("1.20.1"),
        )

    def test_regenerated_metadata_matches_pack_and_translations(self):
        resources = Path(MODS_DIR) / "1.20.1/forge/src/main/resources"
        generated = json.loads(generate_sources.get_pack_mcmeta("1.20.1"))
        self.assertEqual(generated, json.loads(
            (resources / "pack.mcmeta").read_text(encoding="utf-8")))
        self.assertEqual(15, generated["pack"]["pack_format"])
        key = generated["pack"]["description"]["translate"]
        locales = list((resources / "assets/mcpmod/lang").glob("*.json"))
        self.assertEqual(8, len(locales))
        for locale in locales:
            with self.subTest(locale=locale.name):
                self.assertTrue(json.loads(locale.read_text(encoding="utf-8"))[key])

    def test_build_generator_limits_overrides_to_target(self):
        with tempfile.TemporaryDirectory() as tmp:
            for version in ("1.20.1", "1.20.2"):
                generate_mods.write_forge_build_fg6(version, ALL_VERSIONS[version], tmp)
                generated = (Path(tmp) / "build.gradle").read_text()
                self.assertEqual(version == "1.20.1", "client-overrides.gradle" in generated)

    def test_other_versions_keep_existing_metadata(self):
        for version in ALL_VERSIONS:
            if version != "1.20.1":
                with self.subTest(version=version):
                    self.assertEqual(generate_sources.PACK_MCMETA,
                                     generate_sources.get_pack_mcmeta(version))


if __name__ == "__main__":
    unittest.main()
