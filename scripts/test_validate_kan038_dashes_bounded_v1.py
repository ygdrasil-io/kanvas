#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan038_dashes_bounded_v1 as kan038


class DashesBoundedV1EvidenceTest(unittest.TestCase):
    def test_build_evidence_keeps_bounded_dash_candidate_as_stable_refusal(self) -> None:
        evidence = kan038.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-038", evidence["ticket"])
        self.assertEqual("kan-038-dashes-bounded-v1", evidence["packId"])
        self.assertEqual("stable-refusal-dashes-bounded-v1", evidence["closureDecision"])
        self.assertFalse(evidence["supportClaim"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["sharedShadersChanged"])
        self.assertFalse(evidence["thresholdsWeakened"])
        self.assertFalse(evidence["edgeBudgetChanged"])
        self.assertEqual(0, evidence["readinessDelta"])

        candidate = evidence["candidate"]
        self.assertEqual("skia-gm-dashing-width1-pattern1-1-aa", candidate["id"])
        self.assertEqual([1.0, 1.0], candidate["dashIntervals"])
        self.assertEqual(2, candidate["dashIntervalCount"])
        self.assertEqual(8, candidate["dashIntervalBudget"])
        self.assertEqual(0.0, candidate["phase"])
        self.assertEqual(1.0, candidate["strokeWidth"])
        self.assertEqual("before-stroke", candidate["pathEffectOrder"])
        self.assertEqual("expected-unsupported", candidate["status"])
        self.assertEqual("coverage.dashing.row-specific-artifacts-required", candidate["fallbackReason"])
        self.assertFalse(candidate["postDashEdgeCountProven"])
        self.assertFalse(candidate["supportReady"])

    def test_over_budget_sentinel_and_dash_taxonomy_stay_visible(self) -> None:
        evidence = kan038.build_evidence(PROJECT_ROOT)

        sentinel = evidence["overBudgetSentinel"]
        self.assertEqual("path-aa-dashing-edge-budget", sentinel["sceneId"])
        self.assertEqual("pass", sentinel["cpuStatus"])
        self.assertEqual("expected-unsupported", sentinel["gpuStatus"])
        self.assertEqual("coverage.edge-count-exceeded", sentinel["fallbackReason"])
        self.assertEqual(256, sentinel["edgeBudget"])
        self.assertEqual(2, sentinel["dashIntervalCount"])
        self.assertEqual(8, sentinel["dashIntervalBudget"])

        taxonomy = evidence["fallbackTaxonomy"]
        self.assertIn("coverage.edge-count-exceeded", taxonomy["observedOverBudgetReasons"])
        self.assertIn("coverage.dash-budget-exceeded", taxonomy["validBudgetReasons"])
        self.assertTrue(taxonomy["dashBudgetExceededReasonCodePresent"])

        availability = evidence["artifactAvailability"]
        self.assertFalse(availability["webGpuSupportImage"]["available"])
        self.assertFalse(availability["webGpuSupportDiff"]["available"])
        self.assertFalse(availability["postDashVerbEdgeStats"]["available"])

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan038.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "kan-038-dashes-bounded-v1.json").read_text())
            markdown = (output_dir / "kan-038-dashes-bounded-v1.md").read_text()

        self.assertEqual(evidence["candidate"], payload["candidate"])
        self.assertIn("# KAN-038 Dashes Bounded V1", markdown)
        self.assertIn("skia-gm-dashing-width1-pattern1-1-aa", markdown)
        self.assertIn("coverage.dashing.row-specific-artifacts-required", markdown)
        self.assertIn("coverage.edge-count-exceeded", markdown)


if __name__ == "__main__":
    unittest.main()
