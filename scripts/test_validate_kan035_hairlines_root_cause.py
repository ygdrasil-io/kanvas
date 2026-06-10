#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan035_hairlines_root_cause as kan035


class HairlinesRootCauseEvidenceTest(unittest.TestCase):
    def test_build_evidence_classifies_hairlines_root_cause_without_support_claim(self) -> None:
        evidence = kan035.build_evidence(PROJECT_ROOT)

        self.assertEqual("kan-035-hairlines-root-cause-v1", evidence["packId"])
        self.assertEqual("pass", evidence["status"])
        self.assertEqual("stable-refusal-diagnostic-fix", evidence["closureDecision"])
        self.assertFalse(evidence["supportClaim"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["sharedShadersChanged"])
        self.assertFalse(evidence["thresholdsWeakened"])
        self.assertFalse(evidence["edgeBudgetChanged"])
        self.assertEqual(0, evidence["readinessDelta"])

        self.assertEqual("cap-join-parity", evidence["rootCause"]["primaryBucket"])
        self.assertEqual(
            ["cap-join-parity", "coverage-stroke-aa-residual", "hairline-row-specific-artifacts"],
            [row["bucket"] for row in evidence["rootCause"]["priorities"]],
        )
        self.assertEqual(
            "coverage.stroke-cap-join-visual-parity-below-threshold",
            evidence["observedRefusal"]["fallbackReason"],
        )
        self.assertEqual(75, evidence["observedRefusal"]["pathVerbCount"])
        self.assertEqual(96, evidence["observedRefusal"]["pathVerbBudget"])
        self.assertEqual(60, evidence["observedRefusal"]["coverageEdgeCount"])
        self.assertEqual(256, evidence["observedRefusal"]["edgeBudget"])
        self.assertEqual(["butt"], evidence["observedRefusal"]["strokeCaps"])
        self.assertEqual(["miter"], evidence["observedRefusal"]["strokeJoins"])

    def test_evidence_keeps_policy_rows_and_inventory_classification_stable(self) -> None:
        evidence = kan035.build_evidence(PROJECT_ROOT)

        self.assertEqual("expected-unsupported", evidence["dashboardPolicyRow"]["status"])
        self.assertEqual(
            "coverage.hairline.row-specific-artifacts-required",
            evidence["dashboardPolicyRow"]["fallbackReason"],
        )
        self.assertEqual("expected-unsupported", evidence["linkedM60Boundary"]["status"])
        self.assertEqual(99.95, evidence["linkedM60Boundary"]["supportThreshold"])
        self.assertEqual(
            "coverage.stroke-cap-join-aa-residual",
            evidence["linkedM60Boundary"]["remainingBoundary"],
        )
        self.assertEqual(
            {
                "expected-unsupported-diagnostic": 1,
                "similarity-regression": 0,
                "unsupported-image-filter": 0,
                "adapter-skip": 0,
                "adapter-missing": 0,
                "unexpected-exception": 0,
            },
            evidence["gpuInventoryClassification"]["byCategory"],
        )
        self.assertEqual(
            "coverage.stroke-cap-join-visual-parity-below-threshold",
            evidence["gpuInventoryClassification"]["records"][0]["reason"],
        )

        availability = evidence["rowLocalArtifactAvailability"]
        self.assertTrue(availability["reference"]["available"])
        self.assertTrue(availability["cpuStats"]["available"])
        self.assertTrue(availability["gpuStableRefusal"]["available"])
        self.assertFalse(availability["webGpuImage"]["available"])
        self.assertFalse(availability["rowLocalDiffImage"]["available"])
        self.assertEqual("stable-refusal-before-debug-images", availability["webGpuImage"]["reason"])

    def test_write_outputs_materializes_json_markdown_and_inventory_snapshot(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan035.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "kan-035-hairlines-root-cause.json").read_text())
            inventory = json.loads((output_dir / "gpu-inventory-hairlines-classification.json").read_text())
            markdown = (output_dir / "kan-035-hairlines-root-cause.md").read_text()
            inventory_markdown = (output_dir / "gpu-inventory-hairlines-classification.md").read_text()

        self.assertEqual(evidence["rootCause"], payload["rootCause"])
        self.assertEqual(0, payload["artifactAudit"]["missingCommittedArtifacts"])
        self.assertEqual(1, inventory["byCategory"]["expected-unsupported-diagnostic"])
        self.assertIn("# KAN-035 HairlinesGM Root Cause", markdown)
        self.assertIn("cap-join-parity", markdown)
        self.assertIn("skia-gm-hairlines", markdown)
        self.assertIn("expected-unsupported-diagnostic", inventory_markdown)


if __name__ == "__main__":
    unittest.main()
