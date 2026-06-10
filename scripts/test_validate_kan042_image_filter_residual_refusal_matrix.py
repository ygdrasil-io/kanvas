#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan042_image_filter_residual_refusal_matrix as kan042


class ImageFilterResidualRefusalMatrixTest(unittest.TestCase):
    def test_build_evidence_groups_residual_rows(self) -> None:
        evidence = kan042.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-042", evidence["ticket"])
        self.assertEqual("kan-042-image-filter-residual-refusal-matrix", evidence["packId"])
        self.assertEqual("image-filter-residual-refusal-matrix", evidence["closureDecision"])
        self.assertEqual("no-new-rendering-support", evidence["supportClaim"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["thresholdsWeakened"])

        summary = evidence["summary"]
        self.assertEqual(15, summary["totalRows"])
        self.assertEqual(5, summary["supportableBoundedRows"])
        self.assertEqual(6, summary["implementationGapRows"])
        self.assertEqual(4, summary["dependencyGatedRows"])
        self.assertEqual(0, summary["rowsMissingStableReason"])
        self.assertEqual(0, summary["dashboardFailRows"])
        self.assertEqual(0, summary["dashboardTrackedGapRows"])

        rows = {row["rowId"]: row for row in evidence["matrixRows"]}
        self.assertEqual("supportable-bounded", rows["crop-image-filter-nonnull-prepass"]["pmCategory"])
        self.assertEqual("none", rows["crop-image-filter-nonnull-prepass"]["reasonCode"])
        self.assertTrue(rows["crop-image-filter-nonnull-prepass"]["referenceAvailable"])
        self.assertTrue(rows["crop-image-filter-nonnull-prepass"]["routeDiagnosticsAvailable"])

        self.assertEqual("implementation-gap", rows["image-filter-crop-nonnull-prepass-required"]["pmCategory"])
        self.assertEqual("image-filter.crop-input-nonnull-prepass-required", rows["image-filter-crop-nonnull-prepass-required"]["reasonCode"])
        self.assertEqual("implementation-gap", rows["m54-imagefilters-graph-boundary"]["pmCategory"])
        self.assertEqual("image-filter.dag-or-picture-prepass-required", rows["m54-imagefilters-graph-boundary"]["reasonCode"])
        self.assertEqual("image-filter.blur-large-sigma-unsupported", rows["skia-gm-blurbigsigma"]["reasonCode"])
        self.assertEqual("image-filter.perspective-clip-unsupported", rows["skia-gm-perspectiveclip"]["reasonCode"])

        self.assertEqual("dependency-gated", rows["skia-gm-animatedimageblurs"]["pmCategory"])
        self.assertEqual("image-filter.animated-image-decode-dependency-gated", rows["skia-gm-animatedimageblurs"]["reasonCode"])
        self.assertEqual("dependency-gated", rows["skia-gm-imagefiltersstroked"]["pmCategory"])
        self.assertEqual("image-filter.path-aa-stroke-dependency-gated", rows["skia-gm-imagefiltersstroked"]["reasonCode"])

    def test_guards_prevent_hidden_support_or_reasonless_refusals(self) -> None:
        evidence = kan042.build_evidence(PROJECT_ROOT)

        guard = evidence["claimGuard"]
        self.assertEqual([], guard["unsupportedRowsMissingStableReason"])
        self.assertEqual([], guard["unsupportedRowsMissingCategory"])
        self.assertEqual([], guard["supportRowsMissingProofs"])
        self.assertEqual([], guard["hiddenBroadSupportClaims"])
        self.assertEqual([], guard["unexpectedDashboardRows"])
        self.assertEqual([], guard["thresholdOrBudgetChanges"])

        for row in evidence["matrixRows"]:
            if row["pmCategory"] != "supportable-bounded":
                self.assertNotEqual("none", row["reasonCode"])
                self.assertIn(row["pmCategory"], {"implementation-gap", "dependency-gated"})

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan042.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "kan-042-image-filter-residual-refusal-matrix.json").read_text())
            markdown = (output_dir / "kan-042-image-filter-residual-refusal-matrix.md").read_text()

        self.assertEqual(evidence["summary"], payload["summary"])
        self.assertIn("# KAN-042 Image Filter Residual Refusal Matrix", markdown)
        self.assertIn("Supportable borne", markdown)
        self.assertIn("implementation-gap", markdown)
        self.assertIn("dependency-gated", markdown)
        self.assertIn("image-filter.dag-or-picture-prepass-required", markdown)


if __name__ == "__main__":
    unittest.main()
