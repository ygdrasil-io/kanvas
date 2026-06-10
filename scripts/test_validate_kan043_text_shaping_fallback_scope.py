#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan043_text_shaping_fallback_scope as kan043


class TextShapingFallbackScopeTest(unittest.TestCase):
    def test_build_evidence_exposes_explicit_text_scope(self) -> None:
        evidence = kan043.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-043", evidence["ticket"])
        self.assertEqual("kan-043-text-shaping-fallback-scope", evidence["packId"])
        self.assertEqual("text-shaping-fallback-scope", evidence["closureDecision"])
        self.assertEqual("no-new-rendering-support", evidence["supportClaim"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["externalFontEngineAdded"])
        self.assertFalse(evidence["implicitSystemFallbackAllowed"])

        summary = evidence["summary"]
        self.assertEqual(4, summary["totalRows"])
        self.assertEqual(2, summary["supportRows"])
        self.assertEqual(2, summary["refusalRows"])
        self.assertEqual(0, summary["rowsMissingFontHash"])
        self.assertEqual(0, summary["rowsMissingClusters"])
        self.assertEqual(0, summary["rowsMissingGlyphIds"])
        self.assertEqual(0, summary["implicitFallbackRows"])

        rows = {row["rowId"]: row for row in evidence["textScopeRows"]}
        simple = rows["text.simple-latin.line.v1"]
        self.assertEqual("simple-latin-support", simple["pmCategory"])
        self.assertEqual("pass", simple["status"])
        self.assertEqual("simple-codepoint-order", simple["shapingRoute"]["mode"])
        self.assertEqual("none", simple["fallbackPolicy"]["reasonCode"])
        self.assertEqual("Liberation Sans", simple["font"]["face"])
        self.assertEqual("76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8", simple["font"]["sha256"])
        self.assertTrue(simple["proofs"]["cpu"])
        self.assertTrue(simple["proofs"]["gpu"])
        self.assertTrue(simple["proofs"]["diff"])
        self.assertTrue(simple["proofs"]["stats"])
        self.assertEqual(len(simple["glyphIds"]), len(simple["clusters"]))

        bounded = rows["font-kerning-style-fixture"]
        self.assertEqual("bounded-shaping-support", bounded["pmCategory"])
        self.assertEqual("simple-kerning-fixture", bounded["shapingRoute"]["mode"])
        self.assertEqual("none", bounded["fallbackPolicy"]["reasonCode"])
        self.assertEqual([65, 87, 67, 87, 69, 87], bounded["glyphIds"])
        self.assertEqual([0, 1, 2, 3, 4, 5], bounded["clusters"])

        complex_refusal = rows["font-complex-shaping-refusal"]
        self.assertEqual("bounded-shaping-refusal", complex_refusal["pmCategory"])
        self.assertEqual("font.shaping-feature-unsupported", complex_refusal["fallbackPolicy"]["reasonCode"])
        self.assertEqual("font.complex-shaping-requires-explicit-shaper", complex_refusal["fallbackPolicy"]["legacyReasonCode"])
        self.assertEqual("webgpu.text.refuse", complex_refusal["route"]["gpu"])
        self.assertEqual([], complex_refusal["glyphIds"])

        missing = rows["m62-missing-glyph-fallback-refusal"]
        self.assertEqual("fallback-missing-glyph-refusal", missing["pmCategory"])
        self.assertEqual("font.shaping-fallback-missing", missing["fallbackPolicy"]["reasonCode"])
        self.assertEqual("font.missing-glyph-fallback-unsupported", missing["fallbackPolicy"]["legacyReasonCode"])
        self.assertEqual("refuse-without-system-font-fallback", missing["fallbackPolicy"]["policy"])
        self.assertIn(0, missing["glyphIds"])

    def test_guards_prevent_hidden_fallback_or_broad_shaping_claims(self) -> None:
        evidence = kan043.build_evidence(PROJECT_ROOT)
        guard = evidence["claimGuard"]

        self.assertEqual([], guard["rowsMissingFontHash"])
        self.assertEqual([], guard["rowsMissingShapingRoute"])
        self.assertEqual([], guard["rowsMissingClusters"])
        self.assertEqual([], guard["rowsMissingGlyphIds"])
        self.assertEqual([], guard["supportRowsMissingProofs"])
        self.assertEqual([], guard["refusalRowsMissingReason"])
        self.assertEqual([], guard["implicitSystemFallbackRows"])
        self.assertEqual([], guard["hiddenBroadShapingClaims"])
        self.assertEqual([], guard["externalFontEngineClaims"])

        for row in evidence["textScopeRows"]:
            self.assertNotIn("implicit-system-font-fallback", row["fallbackPolicy"]["policy"])
            self.assertNotIn("use-system-font-fallback", row["fallbackPolicy"]["policy"])
            self.assertNotIn("harfbuzz", " ".join(row["nonClaims"]).lower())
            if row["status"] == "pass":
                self.assertEqual("none", row["fallbackPolicy"]["reasonCode"])
            else:
                self.assertNotEqual("none", row["fallbackPolicy"]["reasonCode"])

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan043.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "kan-043-text-shaping-fallback-scope.json").read_text())
            markdown = (output_dir / "kan-043-text-shaping-fallback-scope.md").read_text()

        self.assertEqual(evidence["summary"], payload["summary"])
        self.assertIn("# KAN-043 Text Shaping And Fallback Scope", markdown)
        self.assertIn("text.simple-latin.line.v1", markdown)
        self.assertIn("font.shaping-feature-unsupported", markdown)
        self.assertIn("font.shaping-fallback-missing", markdown)
        self.assertIn("No implicit system font fallback", markdown)


if __name__ == "__main__":
    unittest.main()
