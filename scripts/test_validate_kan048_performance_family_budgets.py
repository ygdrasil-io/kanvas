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

import validate_kan048_performance_family_budgets as kan048


class PerformanceFamilyBudgetsTest(unittest.TestCase):
    def test_build_evidence_reports_required_families_without_release_gates(self) -> None:
        evidence = kan048.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-048", evidence["ticket"])
        self.assertEqual("kan-048-performance-family-budgets", evidence["packId"])
        self.assertEqual("pass", evidence["status"])
        self.assertFalse(evidence["releaseBlockingChange"])
        self.assertFalse(evidence["slowBenchmarkCiRequired"])

        summary = evidence["summary"]
        self.assertEqual(3, summary["familyRows"])
        self.assertEqual(1, summary["measuredFamilies"])
        self.assertEqual(2, summary["unavailableFamilies"])
        self.assertEqual(0, summary["estimatedPayloadsCountedAsMeasured"])
        self.assertEqual(0, summary["releaseBlockingRows"])

        rows = {row["familyId"]: row for row in evidence["familyRows"]}
        self.assertEqual({"image-filter", "text-glyph", "bitmap-color"}, set(rows))

        bitmap = rows["bitmap-color"]
        self.assertEqual("measured", bitmap["status"])
        self.assertTrue(bitmap["measured"])
        self.assertEqual("reporting-only", bitmap["gate"]["phase"])
        self.assertFalse(bitmap["releaseBlocking"])
        self.assertGreaterEqual(len(bitmap["rawPayloads"]), 4)
        self.assertIn("host", bitmap["environment"])
        self.assertIn("jdk", bitmap["environment"])
        self.assertIn("Apple M2 Max", bitmap["environment"]["adapters"])
        self.assertGreater(bitmap["timing"]["p50Ms"], 0)
        self.assertGreater(bitmap["timing"]["p95Ms"], 0)

        filters = rows["image-filter"]
        self.assertEqual("unavailable", filters["status"])
        self.assertFalse(filters["measured"])
        self.assertEqual("performance.image-filter.intermediate-benchmark-unavailable", filters["reason"])
        self.assertIn("rgba16float-intermediate-store-to-present-byte-quantization-policy", filters["sourceBlocker"])

        text = rows["text-glyph"]
        self.assertEqual("unavailable", text["status"])
        self.assertFalse(text["measured"])
        self.assertEqual("performance.text-glyph.production-sampling-route-unavailable", text["reason"])
        self.assertIn("text-atlas-alpha-mask-draw-route-not-materialized", text["sourceBlocker"])

    def test_validation_rejects_estimated_payloads_counted_as_measured(self) -> None:
        evidence = kan048.build_evidence(PROJECT_ROOT)
        evidence["familyRows"] = copy.deepcopy(evidence["familyRows"])
        bitmap = next(row for row in evidence["familyRows"] if row["familyId"] == "bitmap-color")
        bitmap["rawPayloads"][0]["status"] = "estimated"

        with self.assertRaisesRegex(kan048.ValidationError, "estimated payload counted as measured"):
            kan048.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_rejects_release_blocking_family_gate(self) -> None:
        evidence = kan048.build_evidence(PROJECT_ROOT)
        evidence["familyRows"] = copy.deepcopy(evidence["familyRows"])
        evidence["familyRows"][0]["releaseBlocking"] = True

        with self.assertRaisesRegex(kan048.ValidationError, "release-blocking row"):
            kan048.validate_evidence(evidence, PROJECT_ROOT)

    def test_validation_requires_unavailable_reasons(self) -> None:
        evidence = kan048.build_evidence(PROJECT_ROOT)
        evidence["familyRows"] = copy.deepcopy(evidence["familyRows"])
        image_filter = next(row for row in evidence["familyRows"] if row["familyId"] == "image-filter")
        image_filter["reason"] = ""

        with self.assertRaisesRegex(kan048.ValidationError, "unavailable row missing reason"):
            kan048.validate_evidence(evidence, PROJECT_ROOT)

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan048.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / kan048.OUTPUT_JSON).read_text(encoding="utf-8"))
            markdown = (output_dir / kan048.OUTPUT_MARKDOWN).read_text(encoding="utf-8")

        self.assertEqual(evidence["summary"], payload["summary"])
        self.assertIn("# KAN-048 Performance Family Budgets", markdown)
        self.assertIn("bitmap/color", markdown)
        self.assertIn("performance.image-filter.intermediate-benchmark-unavailable", markdown)
        self.assertIn("reporting-only", markdown)


if __name__ == "__main__":
    unittest.main()
