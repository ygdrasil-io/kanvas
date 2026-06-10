#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan041_image_filter_dag_bounded_v3 as kan041


class ImageFilterDagBoundedV3EvidenceTest(unittest.TestCase):
    def test_build_evidence_selects_two_bounded_support_scenes(self) -> None:
        evidence = kan041.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-041", evidence["ticket"])
        self.assertEqual("kan-041-image-filter-dag-bounded-v3", evidence["packId"])
        self.assertEqual("bounded-image-filter-dag-v3", evidence["closureDecision"])
        self.assertEqual("two-bounded-image-filter-dags", evidence["supportClaim"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["sharedShadersChanged"])
        self.assertFalse(evidence["thresholdsWeakened"])

        summary = evidence["summary"]
        self.assertEqual(2, summary["supportScenes"])
        self.assertEqual(3, summary["expectedUnsupportedScenes"])
        self.assertEqual(0, summary["supportRowsMissingProofs"])
        self.assertEqual(0, summary["implicitCpuReadbackFallbacks"])

        rows = {row["sceneId"]: row for row in evidence["supportScenes"]}
        self.assertEqual(2, rows["crop-image-filter-nonnull-prepass"]["nodeCount"])
        self.assertEqual("none", rows["crop-image-filter-nonnull-prepass"]["fallbackReason"])
        self.assertFalse(rows["crop-image-filter-nonnull-prepass"]["strictSkiaParityClaim"])
        self.assertTrue(all(rows["crop-image-filter-nonnull-prepass"]["proofs"].values()))

        self.assertEqual(3, rows["m61-compose-cf-matrix-transform-dag-v2"]["nodeCount"])
        self.assertEqual(1, rows["m61-compose-cf-matrix-transform-dag-v2"]["intermediateTextureCount"])
        self.assertEqual(["matrix-transform-prepass", "color-filter-final-composite"], rows["m61-compose-cf-matrix-transform-dag-v2"]["passOrder"])
        self.assertTrue(all(rows["m61-compose-cf-matrix-transform-dag-v2"]["proofs"].values()))

    def test_refusals_stay_visible_with_stable_reasons(self) -> None:
        evidence = kan041.build_evidence(PROJECT_ROOT)

        refusals = {row["sceneId"]: row for row in evidence["refusalScenes"]}
        self.assertEqual("image-filter.dag-or-picture-prepass-required", refusals["m52-big-tile-image-filter-dag-refusal"]["fallbackReason"])
        self.assertEqual("image-filter.dag-or-picture-prepass-required", refusals["m54-imagefilters-graph-boundary"]["fallbackReason"])
        self.assertEqual("image-filter.crop-input-nonnull-prepass-required", refusals["image-filter-crop-nonnull-prepass-required"]["fallbackReason"])
        self.assertEqual(3, refusals["m52-big-tile-image-filter-dag-refusal"]["nodeCount"])
        self.assertEqual(4, refusals["m54-imagefilters-graph-boundary"]["nodeCount"])

        guard = evidence["claimGuard"]
        self.assertEqual([], guard["supportRowsMissingProofs"])
        self.assertEqual([], guard["supportRowsMissingFallbackNone"])
        self.assertEqual([], guard["unsupportedRowsMissingStableReason"])
        self.assertEqual([], guard["implicitCpuReadbackFallbacks"])
        self.assertEqual([], guard["overBudgetSupportRows"])

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan041.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "kan-041-image-filter-dag-bounded-v3.json").read_text())
            markdown = (output_dir / "kan-041-image-filter-dag-bounded-v3.md").read_text()

        self.assertEqual(evidence["summary"], payload["summary"])
        self.assertIn("# KAN-041 Image Filter DAG Bounded V3", markdown)
        self.assertIn("crop-image-filter-nonnull-prepass", markdown)
        self.assertIn("m61-compose-cf-matrix-transform-dag-v2", markdown)
        self.assertIn("image-filter.dag-or-picture-prepass-required", markdown)


if __name__ == "__main__":
    unittest.main()
