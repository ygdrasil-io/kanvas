#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan039_nested_clip_stack_v1 as kan039


class NestedClipStackV1EvidenceTest(unittest.TestCase):
    def test_build_evidence_keeps_m60_nested_clip_as_stable_refusal(self) -> None:
        evidence = kan039.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-039", evidence["ticket"])
        self.assertEqual("kan-039-nested-clip-stack-v1", evidence["packId"])
        self.assertEqual("stable-refusal-nested-clip-stack-v1", evidence["closureDecision"])
        self.assertFalse(evidence["supportClaim"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["sharedShadersChanged"])
        self.assertFalse(evidence["thresholdsWeakened"])
        self.assertFalse(evidence["edgeBudgetChanged"])

        candidate = evidence["candidate"]
        self.assertEqual("m60-bounded-nested-rrect-clip", candidate["sceneId"])
        self.assertEqual("expected-unsupported", candidate["status"])
        self.assertEqual(
            "coverage.nested-clip-visual-parity-below-threshold",
            candidate["fallbackReason"],
        )
        self.assertEqual(3, candidate["clipDepth"])
        self.assertEqual(4, candidate["clipDepthBudget"])
        self.assertEqual(72, candidate["edgeCount"])
        self.assertEqual(256, candidate["edgeBudget"])
        self.assertFalse(candidate["supportReady"])

        sequence = candidate["clipSequence"]
        self.assertEqual(["rect", "rect", "rrect-oval"], [entry["type"] for entry in sequence])
        self.assertEqual(["intersect", "intersect", "difference"], [entry["op"] for entry in sequence])

    def test_m57_support_and_clip_refusal_policy_stay_visible(self) -> None:
        evidence = kan039.build_evidence(PROJECT_ROOT)

        baseline = evidence["m57SupportBaseline"]
        self.assertEqual("m57-aaclip-bounded-grid", baseline["sceneId"])
        self.assertEqual("pass", baseline["status"])
        self.assertEqual("none", baseline["fallbackReason"])
        self.assertEqual("webgpu.coverage.aaclip-bounded-grid", baseline["gpuRoute"])
        self.assertFalse(baseline["complexClip"])
        self.assertFalse(baseline["inverseClip"])

        policy = evidence["clipPolicy"]
        self.assertTrue(policy["noIntegerScissorSubstitution"])
        self.assertEqual("coverage.arbitrary-aa-clip-unsupported", policy["arbitraryAaClipFallback"])
        self.assertEqual("geometry.clip-stack-unsupported", policy["clipStackFallback"])
        self.assertIn("multi-shape-aa-difference", policy["refusedClipStackFamilies"])
        self.assertIn("shader-clip", policy["refusedClipStackFamilies"])

        forensic = evidence["forensicBlockers"]
        self.assertEqual("KEEP_EXPECTED_UNSUPPORTED", forensic["for302SupportDecision"])
        self.assertEqual(
            "SKAACLIP_DIFFERENCE_OP_ALPHA_MERGE_CAUSES_TARGET_HOLE",
            forensic["for301Decision"],
        )
        self.assertFalse(forensic["safeLocalFixApplied"])

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan039.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "kan-039-nested-clip-stack-v1.json").read_text())
            markdown = (output_dir / "kan-039-nested-clip-stack-v1.md").read_text()

        self.assertEqual(evidence["candidate"], payload["candidate"])
        self.assertIn("# KAN-039 Nested Clip-Stack V1", markdown)
        self.assertIn("m60-bounded-nested-rrect-clip", markdown)
        self.assertIn("coverage.nested-clip-visual-parity-below-threshold", markdown)
        self.assertIn("m57-aaclip-bounded-grid", markdown)


if __name__ == "__main__":
    unittest.main()
