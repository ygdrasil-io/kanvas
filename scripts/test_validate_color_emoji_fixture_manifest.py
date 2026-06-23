#!/usr/bin/env python3
import copy
import importlib.util
import sys
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR_PATH = PROJECT_ROOT / "scripts" / "validate_color_emoji_fixture_manifest.py"

sys.dont_write_bytecode = True


def load_validator():
    if not VALIDATOR_PATH.is_file():
        raise AssertionError("missing validator script: scripts/validate_color_emoji_fixture_manifest.py")
    spec = importlib.util.spec_from_file_location("validate_color_emoji_fixture_manifest", VALIDATOR_PATH)
    if spec is None or spec.loader is None:
        raise AssertionError("unable to load validator module spec")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class ColorEmojiFixtureManifestValidatorTest(unittest.TestCase):
    def test_manifest_matches_expected_fixture_contract(self) -> None:
        validator = load_validator()
        manifest = validator.load_json(PROJECT_ROOT, validator.MANIFEST_PATH)
        validator.validate_manifest(PROJECT_ROOT, manifest)

        self.assertEqual("color-emoji-fixture-manifest", manifest["dumpId"])
        self.assertEqual(["KFONT-M10-010"], manifest["ownerTickets"])
        self.assertEqual(4, len(manifest["families"]))
        self.assertEqual(9, len(manifest["componentDumps"]))

    def test_validator_rejects_missing_component_dump(self) -> None:
        validator = load_validator()
        manifest = validator.load_json(PROJECT_ROOT, validator.MANIFEST_PATH)
        modified = copy.deepcopy(manifest)
        modified["componentDumps"] = [
            dump for dump in manifest["componentDumps"] if dump["dumpId"] != "emoji-route-trace"
        ]

        with self.assertRaises(validator.ValidationError) as missing:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("componentDumps changed", str(missing.exception))

    def test_validator_rejects_component_dump_hash_drift(self) -> None:
        validator = load_validator()
        manifest = validator.load_json(PROJECT_ROOT, validator.MANIFEST_PATH)
        modified = copy.deepcopy(manifest)
        modified["componentDumps"][0]["bodySha256"] = "0" * 64

        with self.assertRaises(validator.ValidationError) as drift:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("bodySha256 does not match", str(drift.exception))

    def test_validator_rejects_family_legacy_gate_drift(self) -> None:
        validator = load_validator()
        manifest = validator.load_json(PROJECT_ROOT, validator.MANIFEST_PATH)
        modified = copy.deepcopy(manifest)
        for family in modified["families"]:
            if family["familyId"] == "emoji":
                family["legacyGates"] = []

        with self.assertRaises(validator.ValidationError) as drift:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("emoji.legacyGates changed", str(drift.exception))

    def test_validator_rejects_unknown_provenance_hash(self) -> None:
        validator = load_validator()
        manifest = validator.load_json(PROJECT_ROOT, validator.MANIFEST_PATH)
        modified = copy.deepcopy(manifest)
        for family in modified["families"]:
            if family["familyId"] == "color-glyphs":
                family["provenance"]["sourceHashes"] = ["0" * 64]

        with self.assertRaises(validator.ValidationError) as drift:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("unknown provenance asset hash", str(drift.exception))

    def test_validator_rejects_non_claim_drift(self) -> None:
        validator = load_validator()
        manifest = validator.load_json(PROJECT_ROOT, validator.MANIFEST_PATH)
        modified = copy.deepcopy(manifest)
        modified["nonClaims"] = [
            non_claim for non_claim in manifest["nonClaims"] if non_claim != "no-gpu-color-glyph-support-claim"
        ]

        with self.assertRaises(validator.ValidationError) as drift:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("nonClaims changed", str(drift.exception))


if __name__ == "__main__":
    unittest.main()
