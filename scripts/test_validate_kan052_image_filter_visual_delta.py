#!/usr/bin/env python3
import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan052_image_filter_visual_delta as kan052


class ImageFilterVisualDeltaTest(unittest.TestCase):
    def test_build_evidence_records_blocked_root_cause(self) -> None:
        evidence = kan052.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-052", evidence["ticket"])
        self.assertEqual("kan-052-image-filter-visual-delta", evidence["packId"])
        self.assertEqual("blocked-root-cause", evidence["closureDecision"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertTrue(evidence["blocked"])
        self.assertFalse(evidence["thresholdsWeakened"])
        self.assertFalse(evidence["broadDagClaim"])
        self.assertFalse(evidence["implicitCpuReadbackFallback"])

        row = evidence["selectedRow"]
        self.assertEqual("crop-image-filter-nonnull-prepass", row["rowId"])
        self.assertEqual("skia-upstream", row["referenceKind"])
        self.assertEqual("webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite", row["gpuRoute"])
        self.assertEqual("none", row["fallbackReason"])

        visual_delta = evidence["visualDeltaEvidence"]
        self.assertEqual("blocked-root-cause-no-renderer-after", visual_delta["mode"])
        self.assertEqual("selected-row-existing-evidence", visual_delta["before"]["phase"])
        self.assertEqual("not-materialized", visual_delta["after"]["phase"])
        self.assertFalse(visual_delta["after"]["rendererChanged"])
        self.assertTrue(visual_delta["after"]["blocked"])
        self.assertEqual(
            "rgba16float-intermediate-store-to-present-byte-quantization-policy",
            visual_delta["after"]["blockedBy"],
        )

        blocker = evidence["blocker"]
        self.assertEqual("rgba16float-intermediate-store-to-present-byte-quantization-policy", blocker["rootCause"])
        self.assertEqual("not-bounded-to-image-filter-crop-prepass", blocker["reasonCode"])
        self.assertIn("FOR-252", blocker["supportingDiagnostics"])
        self.assertIn("FOR-259", blocker["supportingDiagnostics"])
        self.assertIn("FOR-260", blocker["supportingDiagnostics"])

    def test_write_outputs_materializes_report_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan052.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / kan052.OUTPUT_JSON).read_text(encoding="utf-8"))
            markdown = (output_dir / kan052.OUTPUT_MARKDOWN).read_text(encoding="utf-8")

        self.assertEqual(evidence["blocker"], payload["blocker"])
        self.assertIn("# KAN-052 Image Filter Visual Delta", markdown)
        self.assertIn("blocked=true", markdown)
        self.assertIn("crop-image-filter-nonnull-prepass", markdown)
        self.assertIn("rgba16float-intermediate-store-to-present-byte-quantization-policy", markdown)

    def test_validation_rejects_unblocked_without_renderer_change(self) -> None:
        evidence = kan052.build_evidence(PROJECT_ROOT)
        evidence["blocked"] = False
        evidence["rendererChanged"] = False

        with self.assertRaisesRegex(kan052.ValidationError, "rendererChanged=false requires blocked=true"):
            kan052.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_fake_blocker_without_cross_route_diagnostics(self) -> None:
        evidence = kan052.build_evidence(PROJECT_ROOT)
        evidence["blocker"]["supportingDiagnostics"] = ["FOR-259"]

        with self.assertRaisesRegex(kan052.ValidationError, "missing blocker diagnostic"):
            kan052.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_threshold_change(self) -> None:
        evidence = kan052.build_evidence(PROJECT_ROOT)
        evidence["current"]["stats"]["threshold"] = 49.0

        with self.assertRaisesRegex(kan052.ValidationError, "threshold changed"):
            kan052.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_missing_artifact(self) -> None:
        evidence = kan052.build_evidence(PROJECT_ROOT)
        evidence["current"]["artifacts"]["gpu"] = (
            "reports/wgsl-pipeline/image-filter-visual-delta/assets/missing-gpu.png"
        )
        evidence["artifactAudit"] = kan052.audit_artifacts(PROJECT_ROOT, evidence["current"])

        with self.assertRaisesRegex(kan052.ValidationError, "current artifacts missing"):
            kan052.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_blocked_after_artifacts_claim(self) -> None:
        evidence = kan052.build_evidence(PROJECT_ROOT)
        evidence["visualDeltaEvidence"]["after"]["phase"] = "materialized"

        with self.assertRaisesRegex(kan052.ValidationError, "blocked renderer after artifacts"):
            kan052.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_image_filter_crop_claim_for_non_image_filter_residual(self) -> None:
        evidence = kan052.build_evidence(PROJECT_ROOT)
        evidence["blocker"]["reasonCode"] = "crop-prepass-local-fix-ready"

        with self.assertRaisesRegex(kan052.ValidationError, "blocker reason must preserve non-image-filter scope"):
            kan052.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_implementation_gap_converted_to_support(self) -> None:
        evidence = kan052.build_evidence(PROJECT_ROOT)
        evidence["kan042Matrix"]["implementationGapRows"][0]["status"] = "pass"

        with self.assertRaisesRegex(kan052.ValidationError, "implementation-gap converted to support"):
            kan052.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_no_root_cause_detail(self) -> None:
        evidence = kan052.build_evidence(PROJECT_ROOT)
        evidence["blocker"] = copy.deepcopy(evidence["blocker"])
        evidence["blocker"]["rootCause"] = ""

        with self.assertRaisesRegex(kan052.ValidationError, "missing root cause"):
            kan052.validate_evidence(evidence, PROJECT_ROOT)


if __name__ == "__main__":
    unittest.main()
