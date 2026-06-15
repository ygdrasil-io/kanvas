#!/usr/bin/env python3
import importlib.util
import sys
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR_PATH = PROJECT_ROOT / "scripts" / "validate_pure_kotlin_text_font_fixtures.py"

sys.dont_write_bytecode = True


def load_validator():
    if not VALIDATOR_PATH.is_file():
        raise AssertionError("missing validator script: scripts/validate_pure_kotlin_text_font_fixtures.py")
    spec = importlib.util.spec_from_file_location("validate_pure_kotlin_text_font_fixtures", VALIDATOR_PATH)
    if spec is None or spec.loader is None:
        raise AssertionError("unable to load validator module spec")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class PureKotlinTextFontFixtureInventoryTest(unittest.TestCase):
    def test_font_fixture_inventory_covers_all_font_families_and_excludes_gpu_text_work(self) -> None:
        validator = load_validator()
        inventory = validator.load_inventory(PROJECT_ROOT)
        validator.validate_inventory(PROJECT_ROOT, inventory)

        self.assertEqual(
            [
                "a8-sdf-artifacts",
                "cff-cff2-scaler",
                "color-glyphs",
                "emoji",
                "font-source-sfnt",
                "png-bitmap-glyphs",
                "svg-glyphs",
                "truetype-scaler",
            ],
            [family["familyId"] for family in inventory["families"]],
        )
        self.assertEqual("font-only-no-gpu-handoff", inventory["scope"])
        self.assertNotIn(
            "gpu-handoff",
            {family["familyId"] for family in inventory["families"]},
        )
        self.assertNotIn(
            "paragraph",
            {family["familyId"] for family in inventory["families"]},
        )
        self.assertNotIn(
            "shaping-scripts",
            {family["familyId"] for family in inventory["families"]},
        )
        cff_gates = {
            fixture["targetGate"]
            for family in inventory["families"]
            if family["familyId"] == "cff-cff2-scaler"
            for fixture in family["fixtures"]
        }
        self.assertIn("selected-face-cff-cff2-provenance-dump", cff_gates)
        self.assertIn("cff-cff2-malformed-index-dict-refusal", cff_gates)
        self.assertIn("cff2-variation-store-region", cff_gates)

    def test_validator_rejects_missing_font_fixture_gates(self) -> None:
        validator = load_validator()
        inventory = validator.load_inventory(PROJECT_ROOT)
        modified = dict(inventory)
        first_family = dict(inventory["families"][0])
        first_family["fixtures"] = first_family["fixtures"][:-1]
        modified["families"] = [first_family, *inventory["families"][1:]]

        with self.assertRaises(validator.ValidationError) as failure:
            validator.validate_inventory(PROJECT_ROOT, modified)
        self.assertIn("missing fixture gates", str(failure.exception))

    def test_validator_rejects_support_claims_without_current_positive_evidence(self) -> None:
        validator = load_validator()
        inventory = validator.load_inventory(PROJECT_ROOT)
        modified = dict(inventory)
        families = [dict(family) for family in inventory["families"]]
        fixtures = [dict(fixture) for fixture in families[1]["fixtures"]]
        fixtures[0]["status"] = "target-supported"
        families[1]["fixtures"] = fixtures
        modified["families"] = families

        with self.assertRaises(validator.ValidationError) as failure:
            validator.validate_inventory(PROJECT_ROOT, modified)
        self.assertIn("target-supported", str(failure.exception))

    def test_sfnt_optional_malformed_fixture_records_source_hash_and_intended_diagnostic(self) -> None:
        validator = load_validator()
        inventory = validator.load_inventory(PROJECT_ROOT)
        fixture = next(
            fixture
            for family in inventory["families"]
            if family["familyId"] == "font-source-sfnt"
            for fixture in family["fixtures"]
            if fixture["fixtureId"] == "font-source-sfnt-malformed-optional-table-diagnostic"
        )
        artifacts = fixture["expectedArtifacts"]

        self.assertTrue(any(artifact.startswith("sourceSha256:") for artifact in artifacts), artifacts)
        self.assertIn("intendedDiagnostic:font.sfnt.optional-table-malformed", artifacts)


if __name__ == "__main__":
    unittest.main()
