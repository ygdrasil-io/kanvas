#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan036_butt_stroke_non_hairline as kan036


class ButtStrokeNonHairlineEvidenceTest(unittest.TestCase):
    def test_build_evidence_keeps_selected_butt_stroke_as_stable_refusal(self) -> None:
        evidence = kan036.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-036", evidence["ticket"])
        self.assertEqual("kan-036-butt-stroke-non-hairline-v1", evidence["packId"])
        self.assertEqual("pass", evidence["status"])
        self.assertEqual("stable-refusal-existing-selector", evidence["closureDecision"])
        self.assertFalse(evidence["supportClaim"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["sharedShadersChanged"])
        self.assertFalse(evidence["thresholdsWeakened"])
        self.assertFalse(evidence["edgeBudgetChanged"])
        self.assertEqual(0, evidence["readinessDelta"])

        selected = evidence["selectedRow"]
        self.assertEqual("circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true", selected["fixtureId"])
        self.assertEqual(15, selected["strokeWidth"])
        self.assertEqual("kButt_Cap", selected["strokeCap"])
        self.assertFalse(selected["includesHairlineStrokeWidth0"])
        self.assertFalse(selected["includesDash"])
        self.assertFalse(selected["includesFill"])

        refusal = evidence["webGpuRefusal"]
        self.assertEqual("expected-unsupported", refusal["status"])
        self.assertEqual("coverage.stroke-cap-join-visual-parity-below-threshold", refusal["fallbackReason"])
        self.assertEqual(67, refusal["pathVerbCount"])
        self.assertEqual(96, refusal["pathVerbBudget"])
        self.assertEqual(66, refusal["coverageEdgeCount"])
        self.assertEqual(256, refusal["edgeBudget"])
        self.assertEqual("not coverage.edge-count-exceeded", refusal["edgeBudgetReason"])

    def test_evidence_lists_required_artifacts_and_blocks_support_claim(self) -> None:
        evidence = kan036.build_evidence(PROJECT_ROOT)

        availability = evidence["artifactAvailability"]
        self.assertTrue(availability["skiaReference"]["available"])
        self.assertTrue(availability["cpuOracle"]["available"])
        self.assertTrue(availability["cpuVsSkiaDiff"]["available"])
        self.assertTrue(availability["webGpuStableRefusal"]["available"])
        self.assertFalse(availability["webGpuImage"]["available"])
        self.assertFalse(availability["webGpuDiff"]["available"])

        cpu_vs_skia = evidence["cpuVsSkia"]
        self.assertEqual(
            "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_DIFF_REQUIRES_RASTER_AUDIT",
            cpu_vs_skia["decision"],
        )
        self.assertFalse(cpu_vs_skia["supportReady"])

        self.assertEqual("expected-unsupported", evidence["supportPolicy"]["rowStatus"])
        self.assertEqual("stable-refusal", evidence["supportPolicy"]["decision"])
        self.assertEqual(0, evidence["artifactAudit"]["missingCommittedArtifacts"])

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan036.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "kan-036-butt-stroke-non-hairline.json").read_text())
            markdown = (output_dir / "kan-036-butt-stroke-non-hairline.md").read_text()

        self.assertEqual(evidence["webGpuRefusal"], payload["webGpuRefusal"])
        self.assertIn("# KAN-036 Butt Stroke Non-Hairline", markdown)
        self.assertIn("coverage.stroke-cap-join-visual-parity-below-threshold", markdown)
        self.assertIn("supportClaim", markdown)


if __name__ == "__main__":
    unittest.main()
