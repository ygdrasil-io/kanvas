#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan040_coverage_closeout_matrix as kan040


class CoverageCloseoutMatrixTest(unittest.TestCase):
    def test_build_evidence_aggregates_coverage_wave_without_new_support(self) -> None:
        evidence = kan040.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-040", evidence["ticket"])
        self.assertEqual("kan-040-coverage-stroke-clip-closeout-matrix", evidence["packId"])
        self.assertEqual("coverage-stroke-clip-closeout-matrix", evidence["closureDecision"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["sharedShadersChanged"])
        self.assertFalse(evidence["thresholdsWeakened"])
        self.assertFalse(evidence["edgeBudgetChanged"])
        self.assertEqual(6, evidence["summary"]["totalRows"])
        self.assertEqual(1, evidence["summary"]["supportableBounded"])
        self.assertEqual(1, evidence["summary"]["visibleNonSupportable"])
        self.assertEqual(3, evidence["summary"]["expectedUnsupported"])
        self.assertEqual(1, evidence["summary"]["dependencyGated"])

        rows = {row["id"]: row for row in evidence["matrixRows"]}
        self.assertEqual("supportable-bounded", rows["m57-aaclip-bounded-grid"]["classification"])
        self.assertTrue(rows["m57-aaclip-bounded-grid"]["supportClaim"])
        self.assertEqual("none", rows["m57-aaclip-bounded-grid"]["fallbackReason"])
        self.assertTrue(all(rows["m57-aaclip-bounded-grid"]["proofs"].values()))

        self.assertEqual("visible-non-supportable", rows["skia-gm-hairlines"]["classification"])
        self.assertEqual(
            "coverage.hairline.row-specific-artifacts-required",
            rows["skia-gm-hairlines"]["fallbackReason"],
        )
        self.assertFalse(rows["skia-gm-hairlines"]["supportClaim"])

        self.assertEqual("dependency-gated", rows["skia-gm-dashing-width1-pattern1-1-aa"]["classification"])
        self.assertIn("postDashVerbEdgeStats", rows["skia-gm-dashing-width1-pattern1-1-aa"]["missingProofs"])
        self.assertEqual(
            "coverage.dashing.row-specific-artifacts-required",
            rows["skia-gm-dashing-width1-pattern1-1-aa"]["fallbackReason"],
        )

    def test_support_and_refusal_guards_are_fail_closed(self) -> None:
        evidence = kan040.build_evidence(PROJECT_ROOT)

        for row in evidence["matrixRows"]:
            if row["supportClaim"]:
                self.assertEqual("pass", row["rowStatus"])
                self.assertEqual("none", row["fallbackReason"])
                self.assertTrue(all(row["proofs"].values()), row["id"])
            else:
                self.assertNotEqual("none", row["fallbackReason"], row["id"])
                self.assertTrue(row["reasonCodeStable"], row["id"])

        policy = evidence["claimGuard"]
        self.assertEqual([], policy["supportRowsMissingProofs"])
        self.assertEqual([], policy["unsupportedRowsMissingFallback"])
        self.assertEqual([], policy["hiddenPromotionRows"])
        self.assertEqual([], policy["budgetOrThresholdChanges"])
        self.assertTrue(policy["pmBundleCategoriesVisible"])

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan040.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "kan-040-coverage-closeout-matrix.json").read_text())
            markdown = (output_dir / "kan-040-coverage-closeout-matrix.md").read_text()

        self.assertEqual(evidence["summary"], payload["summary"])
        self.assertIn("# KAN-040 Coverage Stroke Clip Closeout Matrix", markdown)
        self.assertIn("m57-aaclip-bounded-grid", markdown)
        self.assertIn("skia-gm-dashing-width1-pattern1-1-aa", markdown)
        self.assertIn("dependency-gated", markdown)


if __name__ == "__main__":
    unittest.main()
