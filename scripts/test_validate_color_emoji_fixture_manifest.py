#!/usr/bin/env python3
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
        self.assertEqual(39, len(manifest["cases"]))
        self.assertEqual(["KFONT-M10-010"], manifest["ownerTickets"])

    def test_validator_rejects_missing_required_fixture(self) -> None:
        validator = load_validator()
        manifest = validator.load_json(PROJECT_ROOT, validator.MANIFEST_PATH)
        modified = dict(manifest)
        modified["cases"] = [case for case in manifest["cases"] if case["fixtureId"] != "emoji-variation-selector-colr"]

        with self.assertRaises(validator.ValidationError) as missing:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("exact required fixture ids", str(missing.exception))

    def test_validator_rejects_provenance_hash_drift_for_real_asset(self) -> None:
        validator = load_validator()
        manifest = validator.load_json(PROJECT_ROOT, validator.MANIFEST_PATH)
        modified = dict(manifest)
        mutated_cases = []
        for case in manifest["cases"]:
            cloned = dict(case)
            if cloned["fixtureId"] == "emoji-variation-selector-colr":
                provenance = dict(cloned["provenance"])
                provenance["sourceSha256"] = "0" * 64
                cloned["provenance"] = provenance
            mutated_cases.append(cloned)
        modified["cases"] = mutated_cases

        with self.assertRaises(validator.ValidationError) as drift:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("asset hash", str(drift.exception))

    def test_validator_rejects_provenance_hash_drift_for_synthetic_recipe(self) -> None:
        validator = load_validator()
        manifest = validator.load_json(PROJECT_ROOT, validator.MANIFEST_PATH)
        modified = dict(manifest)
        mutated_cases = []
        for case in manifest["cases"]:
            cloned = dict(case)
            if cloned["fixtureId"] == "svg-glyphs-svg-static-path":
                provenance = dict(cloned["provenance"])
                provenance["sourceSha256"] = "f" * 64
                cloned["provenance"] = provenance
            mutated_cases.append(cloned)
        modified["cases"] = mutated_cases

        with self.assertRaises(validator.ValidationError) as drift:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("generatedSourceRecipe hash", str(drift.exception))

    def test_validator_rejects_unknown_expected_route(self) -> None:
        validator = load_validator()
        manifest = validator.load_json(PROJECT_ROOT, validator.MANIFEST_PATH)
        modified = dict(manifest)
        mutated_cases = []
        for case in manifest["cases"]:
            cloned = dict(case)
            if cloned["fixtureId"] == "svg-glyphs-svg-static-path":
                cloned["expectedRoute"] = "svg-plan-typo"
            mutated_cases.append(cloned)
        modified["cases"] = mutated_cases

        with self.assertRaises(validator.ValidationError) as route:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("expectedRoute is unknown", str(route.exception))

    def test_validator_rejects_case_legacy_gate_drift(self) -> None:
        validator = load_validator()
        manifest = validator.load_json(PROJECT_ROOT, validator.MANIFEST_PATH)
        modified = dict(manifest)
        mutated_cases = []
        for case in manifest["cases"]:
            cloned = dict(case)
            if cloned["fixtureId"] == "emoji-variation-selector-colr":
                cloned["legacyGates"] = []
            mutated_cases.append(cloned)
        modified["cases"] = mutated_cases

        with self.assertRaises(validator.ValidationError) as drift:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("case legacyGates changed", str(drift.exception))


if __name__ == "__main__":
    unittest.main()
