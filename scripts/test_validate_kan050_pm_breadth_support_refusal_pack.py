#!/usr/bin/env python3
import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR = PROJECT_ROOT / "scripts" / "validate_kan050_pm_breadth_support_refusal_pack.py"
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))


class PmBreadthSupportRefusalPackTest(unittest.TestCase):
    def setUp(self) -> None:
        self.assertTrue(VALIDATOR.is_file(), f"missing validator: {VALIDATOR}")
        global kan050
        import validate_kan050_pm_breadth_support_refusal_pack as kan050

    def test_build_evidence_covers_release_readiness_families_and_non_claims(self) -> None:
        evidence = kan050.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-050", evidence["ticket"])
        self.assertEqual("kan-050-pm-breadth-support-refusal-pack", evidence["packId"])
        self.assertEqual("pass", evidence["status"])
        self.assertEqual(0.0, evidence["readinessDelta"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["thresholdsWeakened"])
        self.assertFalse(evidence["nativeKadreCiRequired"])
        self.assertFalse(evidence["releaseBlockingChange"])

        family_ids = {row["id"] for row in evidence["familyRows"]}
        self.assertEqual(
            {
                "runtime-effects-v2",
                "coverage-strokes-clips",
                "filters-layers",
                "text-glyphs",
                "color-bitmap-codec",
                "performance-cache",
            },
            family_ids,
        )

        categories = {row["category"] for row in evidence["categoryRows"]}
        self.assertTrue({"supported", "expected-unsupported", "dependency-gated", "reporting-only"} <= categories)

        non_claim_ids = {row["id"] for row in evidence["nonClaims"]}
        self.assertTrue(
            {
                "no-broad-skia-parity",
                "no-broad-codecs-fonts",
                "no-estimated-performance-measured",
                "no-native-kadre-ci-requirement",
                "no-dynamic-sksl-compilation",
            }
            <= non_claim_ids
        )

        manifest = evidence["pmBundleManifestEntry"]
        self.assertEqual("kan050PmBreadthSupportRefusalPack", manifest["key"])
        self.assertEqual("release/m99-breadth-pm-pack/evidence.json", manifest["evidenceJson"])
        self.assertEqual("release/m99-breadth-pm-pack/evidence.md", manifest["evidenceMarkdown"])

        self.assertEqual([], evidence["claimGuard"]["missingFamilies"])
        self.assertEqual([], evidence["claimGuard"]["supportRowsMissingProofs"])
        self.assertEqual([], evidence["claimGuard"]["requiredCategoriesMissing"])
        self.assertEqual([], evidence["claimGuard"]["nonClaimsMissing"])

    def test_support_rows_have_complete_evidence_and_stable_fallbacks(self) -> None:
        evidence = kan050.build_evidence(PROJECT_ROOT)

        self.assertGreaterEqual(len(evidence["supportRows"]), 10)
        for row in evidence["supportRows"]:
            proof = row["proof"]
            self.assertTrue(proof["reference"], row["id"])
            self.assertTrue(proof["cpuGpu"], row["id"])
            self.assertTrue(proof["diffStat"], row["id"])
            self.assertTrue(proof["routeDiagnostics"], row["id"])
            self.assertTrue(proof["fallbackStable"], row["id"])
            self.assertIn(row["fallbackReason"], {"none", ""}, row["id"])

    def test_validation_rejects_support_row_missing_route_diagnostics(self) -> None:
        evidence = kan050.build_evidence(PROJECT_ROOT)
        evidence["supportRows"] = copy.deepcopy(evidence["supportRows"])
        evidence["supportRows"][0]["proof"]["routeDiagnostics"] = False

        with self.assertRaisesRegex(kan050.ValidationError, "support row missing complete proof"):
            kan050.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_hidden_broad_or_estimated_perf_claims(self) -> None:
        evidence = kan050.build_evidence(PROJECT_ROOT)
        evidence["nonClaims"] = [row for row in evidence["nonClaims"] if row["id"] != "no-estimated-performance-measured"]

        with self.assertRaisesRegex(kan050.ValidationError, "missing required non-claim"):
            kan050.validate_evidence(evidence, PROJECT_ROOT)

    def test_write_outputs_materializes_pack_and_manifest_entry(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan050.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / kan050.OUTPUT_JSON).read_text(encoding="utf-8"))
            manifest_entry = json.loads((output_dir / kan050.OUTPUT_MANIFEST_ENTRY).read_text(encoding="utf-8"))
            markdown = (output_dir / kan050.OUTPUT_MARKDOWN).read_text(encoding="utf-8")

        self.assertEqual(evidence["summary"], payload["summary"])
        self.assertEqual("kan050PmBreadthSupportRefusalPack", manifest_entry["key"])
        self.assertIn("# KAN-050 PM Breadth Support Refusal Pack", markdown)
        self.assertIn("expected-unsupported", markdown)
        self.assertIn("dependency-gated", markdown)
        self.assertIn("reporting-only", markdown)


if __name__ == "__main__":
    unittest.main()
