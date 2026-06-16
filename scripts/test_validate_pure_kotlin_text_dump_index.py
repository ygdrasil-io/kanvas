#!/usr/bin/env python3
import importlib.util
import sys
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR_PATH = PROJECT_ROOT / "scripts" / "validate_pure_kotlin_text_dump_index.py"

sys.dont_write_bytecode = True


def load_validator():
    if not VALIDATOR_PATH.is_file():
        raise AssertionError("missing validator script: scripts/validate_pure_kotlin_text_dump_index.py")
    spec = importlib.util.spec_from_file_location("validate_pure_kotlin_text_dump_index", VALIDATOR_PATH)
    if spec is None or spec.loader is None:
        raise AssertionError("unable to load validator module spec")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class PureKotlinTextDumpIndexTest(unittest.TestCase):
    def test_dump_index_covers_current_producer_surfaces_without_support_claims(self) -> None:
        validator = load_validator()
        index = validator.load_index(PROJECT_ROOT)
        validator.validate_index(PROJECT_ROOT, index)

        self.assertEqual(
            [
                "schemaVersion",
                "indexId",
                "sourceSpec",
                "supportClaim",
                "goldenUpdatePolicy",
                "nonClaims",
                "validationCommand",
                "dumpRows",
            ],
            list(index.keys()),
        )
        self.assertEqual("dump-index-coordination-artifact-no-support-claim", index["supportClaim"])
        self.assertEqual("must-not-overwrite-goldens", index["goldenUpdatePolicy"]["ordinaryTestRuns"])

        dump_ids = [row["dumpId"] for row in index["dumpRows"]]
        self.assertEqual(sorted(dump_ids), dump_ids)
        self.assertEqual(
            {
                "a8-atlas-build-result",
                "a8-glyph-mask",
                "a8-sdf-atlas-lifecycle",
                "arabic-seed-readiness",
                "bidi-runs",
                "cff-cff2-readiness",
                "cff-charstring-trace",
                "cff-index-dict",
                "cff-subroutine-trace",
                "cmap-contract",
                "color-svg-emoji-goldens",
                "fallback-catalog-build",
                "fallback-decision-trace",
                "font-catalog",
                "font-source-liberation-scan-root",
                "font-telemetry-schema",
                "font-telemetry-schema-fixture",
                "glyph-atlas-eviction-trace",
                "glyph-atlas-lifecycle",
                "glyph-cache-inventory",
                "glyph-cache-telemetry",
                "glyph-strike-key",
                "gpos-trace",
                "gsub-trace",
                "latin-gsub-gpos-goldens",
                "malformed-sfnt-fixtures",
                "paragraph-input-goldens",
                "paragraph-layout-result",
                "png-glyph-image",
                "resolved-font-runs",
                "script-runs",
                "sdf-atlas-build-result",
                "sdf-glyph-artifact",
                "sfnt-cmap-format14-readiness",
                "sfnt-table-facts",
                "shaped-glyph-run",
                "shaping-plan",
                "svg-glyph-document",
                "truetype-composite-glyphs",
                "truetype-variation-readiness",
                "unicode-data-manifest",
                "unicode-data-seed",
                "unicode-data-version-mismatch-diagnostic",
                "unicode-segments",
            },
            set(dump_ids),
        )
        self.assertNotIn("current-golden", {row["classification"] for row in index["dumpRows"]})

    def test_validator_rejects_current_golden_without_artifact_contract(self) -> None:
        validator = load_validator()
        index = validator.load_index(PROJECT_ROOT)
        rows = [dict(row) for row in index["dumpRows"]]
        rows[0]["classification"] = "current-golden"
        modified = dict(index)
        modified["dumpRows"] = rows

        with self.assertRaises(validator.ValidationError) as current_golden:
            validator.validate_index(PROJECT_ROOT, modified)
        self.assertIn("must not claim current-golden", str(current_golden.exception))

    def test_validator_rejects_unsorted_expected_fields(self) -> None:
        validator = load_validator()
        index = validator.load_index(PROJECT_ROOT)
        rows = [dict(row) for row in index["dumpRows"]]
        rows[0]["expectedFields"] = list(reversed(rows[0]["expectedFields"]))
        modified = dict(index)
        modified["dumpRows"] = rows

        with self.assertRaises(validator.ValidationError) as unsorted:
            validator.validate_index(PROJECT_ROOT, modified)
        self.assertIn("expectedFields must be sorted", str(unsorted.exception))

    def test_validator_rejects_external_oracle_in_dump_row(self) -> None:
        validator = load_validator()
        index = validator.load_index(PROJECT_ROOT)
        rows = [dict(row) for row in index["dumpRows"]]
        rows[0]["updatePolicy"] = "Use HarfBuzz as pass/fail oracle."
        modified = dict(index)
        modified["dumpRows"] = rows

        with self.assertRaises(validator.ValidationError) as external:
            validator.validate_index(PROJECT_ROOT, modified)
        self.assertIn("external engine", str(external.exception))

    def test_existing_path_guard_rejects_relative_traversal_outside_project_root(self) -> None:
        validator = load_validator()

        with self.assertRaises(validator.ValidationError) as traversal:
            validator.require_existing_path(PROJECT_ROOT, "../../../RTK.md", "escape")
        self.assertIn("stay under project root", str(traversal.exception))


if __name__ == "__main__":
    unittest.main()
