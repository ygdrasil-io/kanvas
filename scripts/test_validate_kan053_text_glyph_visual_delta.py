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

import validate_kan053_text_glyph_visual_delta as kan053


class TextGlyphVisualDeltaTest(unittest.TestCase):
    def test_build_evidence_records_blocked_atlas_route_root_cause(self) -> None:
        evidence = kan053.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-053", evidence["ticket"])
        self.assertEqual("kan-053-text-glyph-visual-delta", evidence["packId"])
        self.assertEqual("blocked-root-cause", evidence["closureDecision"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertTrue(evidence["blocked"])
        self.assertFalse(evidence["thresholdsWeakened"])
        self.assertFalse(evidence["implicitSystemFontFallback"])
        self.assertFalse(evidence["broadShapingClaim"])
        self.assertFalse(evidence["lcdSdfColorFontClaim"])

        row = evidence["selectedRow"]
        self.assertEqual("text.simple-latin.line.v1", row["rowId"])
        self.assertEqual("KAN-043", row["sourceTicket"])
        self.assertEqual("simple-latin-support", row["pmCategory"])
        self.assertEqual("pass", row["status"])
        self.assertEqual("webgpu.text.outline-path.simple-latin", row["webGpuRoute"])
        self.assertEqual("webgpu.text.glyph-atlas.simple-latin", row["atlasRoute"])
        self.assertEqual("cpu-atlas-alpha-mask-oracle", row["referenceKind"])
        self.assertEqual("none", row["fallbackReason"])

        current = evidence["current"]
        self.assertEqual(581, current["stats"]["cpuMismatchingPixels"])
        self.assertEqual(608, current["stats"]["webGpuMismatchingPixels"])
        self.assertEqual(27, current["stats"]["webGpuMinusCpuReferenceMismatches"])

        blocker = evidence["blocker"]
        self.assertEqual("text-atlas-alpha-mask-draw-route-not-materialized", blocker["rootCause"])
        self.assertEqual("requires-production-glyph-atlas-sampling-route", blocker["reasonCode"])
        self.assertIn("KAN-043", blocker["supportingEvidence"])
        self.assertIn("KAN-044", blocker["supportingEvidence"])
        self.assertIn("KAN-012", blocker["supportingEvidence"])

        visual_delta = evidence["visualDeltaEvidence"]
        self.assertEqual("blocked-root-cause-no-renderer-after", visual_delta["mode"])
        self.assertEqual("selected-row-existing-evidence", visual_delta["before"]["phase"])
        self.assertEqual("not-materialized", visual_delta["after"]["phase"])
        self.assertTrue(visual_delta["after"]["blocked"])

    def test_write_outputs_materializes_report_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan053.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / kan053.OUTPUT_JSON).read_text(encoding="utf-8"))
            markdown = (output_dir / kan053.OUTPUT_MARKDOWN).read_text(encoding="utf-8")

        self.assertEqual(evidence["blocker"], payload["blocker"])
        self.assertIn("# KAN-053 Text Glyph Visual Delta", markdown)
        self.assertIn("blocked=true", markdown)
        self.assertIn("text.simple-latin.line.v1", markdown)
        self.assertIn("text-atlas-alpha-mask-draw-route-not-materialized", markdown)

    def test_validation_rejects_unblocked_without_renderer_change(self) -> None:
        evidence = kan053.build_evidence(PROJECT_ROOT)
        evidence["blocked"] = False
        evidence["rendererChanged"] = False

        with self.assertRaisesRegex(kan053.ValidationError, "rendererChanged=false requires blocked=true"):
            kan053.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_threshold_change(self) -> None:
        evidence = kan053.build_evidence(PROJECT_ROOT)
        evidence["current"]["stats"]["webGpuSimilarityThreshold"] = 94.0

        with self.assertRaisesRegex(kan053.ValidationError, "threshold changed"):
            kan053.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_missing_font_identity(self) -> None:
        evidence = kan053.build_evidence(PROJECT_ROOT)
        evidence["selectedRow"]["font"]["sha256"] = ""

        with self.assertRaisesRegex(kan053.ValidationError, "font identity missing"):
            kan053.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_atlas_route_claim_without_blocker(self) -> None:
        evidence = kan053.build_evidence(PROJECT_ROOT)
        evidence["blocker"]["reasonCode"] = "outline-path-local-aa-fix"

        with self.assertRaisesRegex(kan053.ValidationError, "atlas sampling route requirement"):
            kan053.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_missing_artifact(self) -> None:
        evidence = kan053.build_evidence(PROJECT_ROOT)
        evidence["current"]["artifacts"]["webGpu"] = (
            "reports/wgsl-pipeline/text-glyph-visual-delta/assets/missing-webgpu.png"
        )
        evidence["artifactAudit"] = kan053.audit_artifacts(PROJECT_ROOT, evidence["current"])

        with self.assertRaisesRegex(kan053.ValidationError, "current artifacts missing"):
            kan053.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_broad_text_claim(self) -> None:
        evidence = kan053.build_evidence(PROJECT_ROOT)
        evidence["broadShapingClaim"] = True

        with self.assertRaisesRegex(kan053.ValidationError, "broad shaping claim"):
            kan053.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_blocked_after_artifacts_claim(self) -> None:
        evidence = kan053.build_evidence(PROJECT_ROOT)
        evidence["visualDeltaEvidence"]["after"]["phase"] = "materialized"

        with self.assertRaisesRegex(kan053.ValidationError, "blocked renderer after artifacts"):
            kan053.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_missing_root_cause_detail(self) -> None:
        evidence = kan053.build_evidence(PROJECT_ROOT)
        evidence["blocker"] = copy.deepcopy(evidence["blocker"])
        evidence["blocker"]["rootCause"] = ""

        with self.assertRaisesRegex(kan053.ValidationError, "missing root cause"):
            kan053.validate_evidence(evidence, PROJECT_ROOT)


if __name__ == "__main__":
    unittest.main()
