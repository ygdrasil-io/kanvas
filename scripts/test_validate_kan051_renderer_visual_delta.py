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

import validate_kan051_renderer_visual_delta as kan051


class RendererVisualDeltaTest(unittest.TestCase):
    def test_build_evidence_records_renderer_change_and_metric_improvement(self) -> None:
        evidence = kan051.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-051", evidence["ticket"])
        self.assertEqual("kan-051-renderer-visual-delta", evidence["packId"])
        self.assertTrue(evidence["rendererChanged"])
        self.assertFalse(evidence["blocked"])
        self.assertFalse(evidence["evidenceOnlyClosure"])
        self.assertFalse(evidence["thresholdsWeakened"])
        self.assertEqual("clip-rect-difference", evidence["selectedScene"]["sceneId"])

        before = evidence["before"]["stats"]
        after = evidence["after"]["stats"]
        self.assertEqual(80.0, before["threshold"])
        self.assertEqual(before["threshold"], after["threshold"])
        self.assertEqual(before["tolerance"], after["tolerance"])
        self.assertEqual(130672, before["gpuMatchingPixels"])
        self.assertEqual(131064, after["gpuMatchingPixels"])
        self.assertEqual(21, before["gpuMaxChannelDelta"]["r"])
        self.assertEqual(10, after["gpuMaxChannelDelta"]["r"])
        self.assertEqual(17, before["haloSamples"]["clipRectTop"]["rgbAbsError"])
        self.assertEqual(3, after["haloSamples"]["clipRectTop"]["rgbAbsError"])

        improvements = evidence["improvements"]
        self.assertEqual(392, improvements["gpuMatchingPixels"]["delta"])
        self.assertEqual(-392, improvements["gpuMismatchingPixels"]["delta"])
        self.assertEqual(-11, improvements["gpuMaxChannelDelta"]["delta"])
        self.assertEqual(-14, improvements["clipRectTopHaloRgbAbsError"]["delta"])
        self.assertTrue(any(item["improved"] for item in improvements.values()))

    def test_write_outputs_materializes_report_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan051.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / kan051.OUTPUT_JSON).read_text(encoding="utf-8"))
            markdown = (output_dir / kan051.OUTPUT_MARKDOWN).read_text(encoding="utf-8")

        self.assertEqual(evidence["improvements"], payload["improvements"])
        self.assertIn("# KAN-051 Renderer Visual Delta", markdown)
        self.assertIn("GPU matching pixels", markdown)
        self.assertIn("clipRect top halo RGB abs error", markdown)
        self.assertIn("image-sampling.mipmap-unsupported", markdown)

    def test_validation_rejects_renderer_unchanged_without_blocker(self) -> None:
        evidence = kan051.build_evidence(PROJECT_ROOT)
        evidence["rendererChanged"] = False
        evidence["blocked"] = False

        with self.assertRaisesRegex(kan051.ValidationError, "rendererChanged=false"):
            kan051.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_evidence_only_closure(self) -> None:
        evidence = kan051.build_evidence(PROJECT_ROOT)
        evidence["evidenceOnlyClosure"] = True

        with self.assertRaisesRegex(kan051.ValidationError, "evidence-only closure"):
            kan051.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_missing_before_after_artifacts(self) -> None:
        evidence = kan051.build_evidence(PROJECT_ROOT)
        evidence["before"]["artifacts"]["gpu"] = "reports/wgsl-pipeline/renderer-visual-delta/assets/before/missing-gpu.png"
        evidence["artifactAudit"] = [
            kan051.audit_artifacts(PROJECT_ROOT, evidence["before"]),
            kan051.audit_artifacts(PROJECT_ROOT, evidence["after"]),
        ]

        with self.assertRaisesRegex(kan051.ValidationError, "evidence artifacts missing"):
            kan051.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_threshold_change(self) -> None:
        evidence = kan051.build_evidence(PROJECT_ROOT)
        evidence["after"]["stats"]["threshold"] = 79.0

        with self.assertRaisesRegex(kan051.ValidationError, "threshold changed"):
            kan051.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_no_metric_improvement(self) -> None:
        evidence = kan051.build_evidence(PROJECT_ROOT)
        before_stats = evidence["before"]["stats"]
        after_stats = copy.deepcopy(before_stats)
        after_stats["phase"] = "after"
        evidence["after"]["stats"] = after_stats

        with self.assertRaisesRegex(kan051.ValidationError, "no pixel/diff/stat metric improved"):
            kan051.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_wiring_only_change(self) -> None:
        evidence = kan051.build_evidence(PROJECT_ROOT)
        evidence["rendererSourceFiles"] = [
            "scripts/validate_kan051_renderer_visual_delta.py",
            "build.gradle.kts",
            "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ClipDifferenceCrossTest.kt",
        ]

        with self.assertRaisesRegex(kan051.ValidationError, "missing renderer source change"):
            kan051.validate_evidence(evidence, PROJECT_ROOT)


if __name__ == "__main__":
    unittest.main()
