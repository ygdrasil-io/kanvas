#!/usr/bin/env python3
import importlib.util
import sys
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR_PATH = PROJECT_ROOT / "scripts" / "validate_pure_kotlin_text_fixture_manifest.py"

sys.dont_write_bytecode = True


def load_validator():
    if not VALIDATOR_PATH.is_file():
        raise AssertionError("missing validator script: scripts/validate_pure_kotlin_text_fixture_manifest.py")
    spec = importlib.util.spec_from_file_location("validate_pure_kotlin_text_fixture_manifest", VALIDATOR_PATH)
    if spec is None or spec.loader is None:
        raise AssertionError("unable to load validator module spec")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class PureKotlinTextFixtureManifestTest(unittest.TestCase):
    def test_manifest_covers_spec_07_dashboard_families_without_target_supported_rows(self) -> None:
        validator = load_validator()
        manifest = validator.load_manifest(PROJECT_ROOT)
        validator.validate_manifest(PROJECT_ROOT, manifest)

        self.assertEqual(
            [
                "target-supported",
                "current-supported",
                "tracked-gap",
                "expected-unsupported",
                "fixture-gated",
                "GPU-gated",
                "drift-only",
            ],
            manifest["dashboardClassifications"],
        )
        self.assertEqual(
            "coordination-evidence-infrastructure-no-support-claim",
            manifest["supportClaim"],
        )

        rows = manifest["fixtureFamilies"]
        family_ids = [row["familyId"] for row in rows]
        self.assertEqual(sorted(family_ids), family_ids)
        self.assertEqual(
            {
                "a8-sdf-artifacts",
                "cff-cff2-scaler",
                "color-glyphs",
                "emoji",
                "font-source-sfnt",
                "font-source-system-scan",
                "gpu-handoff",
                "paragraph",
                "png-bitmap-glyphs",
                "sfnt-malformed-tables",
                "shaping-scripts",
                "svg-glyphs",
                "truetype-scaler",
                "unicode-data-generation",
            },
            set(family_ids),
        )
        self.assertNotIn("target-supported", {row["classification"] for row in rows})

        rows_by_id = {row["familyId"]: row for row in rows}
        self.assertIn(
            "no-platform-font-api-claim",
            rows_by_id["font-source-system-scan"]["nonClaims"],
        )
        self.assertIn(
            "Add cmap format 14 positive variation-selector fixture rows.",
            rows_by_id["sfnt-malformed-tables"]["requiredEvidenceGates"],
        )
        self.assertIn(
            "no-complete-ucd-claim",
            rows_by_id["unicode-data-generation"]["nonClaims"],
        )

    def test_validator_rejects_hidden_support_claims_and_missing_gates(self) -> None:
        validator = load_validator()
        manifest = validator.load_manifest(PROJECT_ROOT)

        first = dict(manifest["fixtureFamilies"][0])
        first["classification"] = "target-supported"
        modified = dict(manifest)
        modified["fixtureFamilies"] = [first, *manifest["fixtureFamilies"][1:]]

        with self.assertRaises(validator.ValidationError) as target_supported:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("target-supported", str(target_supported.exception))

        gated = dict(manifest["fixtureFamilies"][0])
        gated["classification"] = "fixture-gated"
        gated["requiredEvidenceGates"] = []
        modified = dict(manifest)
        modified["fixtureFamilies"] = [gated, *manifest["fixtureFamilies"][1:]]

        with self.assertRaises(validator.ValidationError) as missing_gate:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("non-empty requiredEvidenceGates", str(missing_gate.exception))

    def test_validator_rejects_normative_external_engine_oracles(self) -> None:
        validator = load_validator()
        manifest = validator.load_manifest(PROJECT_ROOT)
        rows = [dict(row) for row in manifest["fixtureFamilies"]]
        rows[0]["requiredEvidenceGates"] = [
            *rows[0]["requiredEvidenceGates"],
            "Use HarfBuzz as the pass/fail oracle",
        ]
        modified = dict(manifest)
        modified["fixtureFamilies"] = rows

        with self.assertRaises(validator.ValidationError) as external_oracle:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("external engine", str(external_oracle.exception))

    def test_existing_path_guard_rejects_relative_traversal_outside_project_root(self) -> None:
        validator = load_validator()

        with self.assertRaises(validator.ValidationError) as traversal:
            validator.require_existing_path(PROJECT_ROOT, "../../../RTK.md", "escape")
        self.assertIn("stay under project root", str(traversal.exception))


if __name__ == "__main__":
    unittest.main()
