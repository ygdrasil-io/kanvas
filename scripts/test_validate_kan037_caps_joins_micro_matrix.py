#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan037_caps_joins_micro_matrix as kan037


class CapsJoinsMicroMatrixEvidenceTest(unittest.TestCase):
    def test_build_evidence_keeps_round_round_candidate_refused(self) -> None:
        evidence = kan037.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-037", evidence["ticket"])
        self.assertEqual("kan-037-caps-joins-micro-matrix-v1", evidence["packId"])
        self.assertEqual("stable-refusal-micro-matrix", evidence["closureDecision"])
        self.assertFalse(evidence["supportClaim"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["sharedShadersChanged"])
        self.assertFalse(evidence["thresholdsWeakened"])
        self.assertFalse(evidence["edgeBudgetChanged"])

        candidate = evidence["candidate"]
        self.assertEqual("round-round", candidate["id"])
        self.assertEqual("round", candidate["cap"])
        self.assertEqual("round", candidate["join"])
        self.assertEqual(4.0, candidate["miterLimit"])
        self.assertEqual("expected-unsupported", candidate["status"])
        self.assertEqual(
            "missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells",
            candidate["blockingCondition"],
        )

        refusal = evidence["webGpuRefusal"]
        self.assertEqual("coverage.stroke-cap-join-visual-parity-below-threshold", refusal["fallbackReason"])
        self.assertEqual(9, refusal["pathVerbCount"])
        self.assertEqual(96, refusal["pathVerbBudget"])
        self.assertEqual(18, refusal["coverageEdgeCount"])
        self.assertEqual(256, refusal["edgeBudget"])
        self.assertEqual("pathAaStrokeCapJoinBlocked", refusal["pipelineKeyContains"])

    def test_sentinels_and_cpu_join_gap_are_visible(self) -> None:
        evidence = kan037.build_evidence(PROJECT_ROOT)

        sentinels = {sentinel["id"]: sentinel for sentinel in evidence["sentinels"]}
        self.assertEqual({"butt-bevel", "square-bevel"}, set(sentinels))
        self.assertEqual("butt", sentinels["butt-bevel"]["cap"])
        self.assertEqual("square", sentinels["square-bevel"]["cap"])
        self.assertEqual("stable-refusal", sentinels["butt-bevel"]["decision"])
        self.assertEqual("stable-refusal", sentinels["square-bevel"]["decision"])

        cpu_evidence = evidence["cpuEvidence"]
        self.assertTrue(cpu_evidence["openContourCaps"]["available"])
        self.assertFalse(cpu_evidence["closedContourJoins"]["available"])
        self.assertEqual("support-blocker", cpu_evidence["closedContourJoins"]["classification"])

        for row in evidence["validationRows"]:
            self.assertEqual("pass", row["status"], row["id"])

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan037.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "kan-037-caps-joins-micro-matrix.json").read_text())
            markdown = (output_dir / "kan-037-caps-joins-micro-matrix.md").read_text()

        self.assertEqual(evidence["candidate"], payload["candidate"])
        self.assertIn("# KAN-037 Caps Joins Micro-Matrix", markdown)
        self.assertIn("round-round", markdown)
        self.assertIn("coverage.stroke-cap-join-visual-parity-below-threshold", markdown)
        self.assertIn("closed-contour", markdown)


if __name__ == "__main__":
    unittest.main()
