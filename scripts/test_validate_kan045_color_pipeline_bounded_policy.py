#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan045_color_pipeline_bounded_policy as kan045


class ColorPipelineBoundedPolicyTest(unittest.TestCase):
    def test_build_evidence_exposes_bounded_support_and_refusals(self) -> None:
        evidence = kan045.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-045", evidence["ticket"])
        self.assertEqual("kan-045-color-pipeline-bounded-policy", evidence["packId"])
        self.assertEqual("bounded-color-pipeline-policy", evidence["closureDecision"])
        self.assertEqual("existing-evidence-only", evidence["claimLevel"])
        self.assertEqual("bounded-srgb-premul-only", evidence["supportClaim"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["sharedShadersChanged"])
        self.assertFalse(evidence["thresholdsWeakened"])

        summary = evidence["summary"]
        self.assertEqual(4, summary["totalRows"])
        self.assertEqual(2, summary["supportRows"])
        self.assertEqual(2, summary["expectedUnsupportedRows"])
        self.assertEqual(0, summary["rowsMissingReferenceCpuGpuDiffStatsRoute"])
        self.assertEqual(0, summary["rowsWithSemanticOpMismatch"])
        self.assertEqual(0, summary["rowsWithThresholdChanges"])
        self.assertEqual(0, summary["rowsWithSilentApproximation"])

        rows = {row["rowId"]: row for row in evidence["policyRows"]}
        src_over = rows["paint.src-over-alpha.rect-stack.v1"]
        self.assertEqual("supportable-bounded", src_over["pmCategory"])
        self.assertEqual("pass", src_over["status"])
        self.assertEqual("srgb-unmanaged-src-over-oracle", src_over["colorPolicy"])
        self.assertEqual("kSrcOver", src_over["semanticOps"]["blendMode"])
        self.assertEqual(src_over["cpuSemanticOps"], src_over["gpuSemanticOps"])
        self.assertTrue(src_over["proofs"]["reference"])
        self.assertTrue(src_over["proofs"]["cpu"])
        self.assertTrue(src_over["proofs"]["gpu"])
        self.assertTrue(src_over["proofs"]["diffStats"])
        self.assertTrue(src_over["proofs"]["route"])

        kplus = rows["paint.color-filter.blend-kplus.rect.v1"]
        self.assertEqual("supportable-bounded", kplus["pmCategory"])
        self.assertEqual("pass", kplus["status"])
        self.assertEqual("Blend", kplus["semanticOps"]["colorFilterKind"])
        self.assertEqual("kPlus", kplus["semanticOps"]["colorFilterBlendMode"])
        self.assertEqual("srgb-unmanaged-color-filter-oracle", kplus["colorPolicy"])
        self.assertTrue(kplus["wgslValidation"]["validated"])
        self.assertEqual(kplus["cpuSemanticOps"], kplus["gpuSemanticOps"])

        wide = rows["m63-wide-gamut-color-space-refusal"]
        self.assertEqual("expected-unsupported", wide["status"])
        self.assertEqual("wide-gamut-color-space", wide["pmCategory"])
        self.assertEqual("color.color-space-wide-gamut-unsupported", wide["reasonCode"])
        self.assertEqual("webgpu.color.refuse.wide-gamut-color-space", wide["route"]["webGpu"])

        f16 = rows["non-arc-rec2020-f16-src-over-rect"]
        self.assertEqual("expected-unsupported", f16["status"])
        self.assertEqual("f16-policy-candidate-refusal", f16["pmCategory"])
        self.assertEqual("color.f16-policy-candidate-worsens-reference", f16["reasonCode"])
        self.assertEqual("kRGBA_F16Norm", f16["colorType"])
        self.assertEqual("Rec.2020", f16["colorSpace"])
        self.assertGreater(f16["residuals"]["candidateMinusCurrentResidual"], 0)

    def test_guards_prevent_broad_color_claims(self) -> None:
        evidence = kan045.build_evidence(PROJECT_ROOT)
        guard = evidence["claimGuard"]

        self.assertEqual([], guard["rowsMissingReferenceCpuGpuDiffStatsRoute"])
        self.assertEqual([], guard["rowsWithSemanticOpMismatch"])
        self.assertEqual([], guard["rowsWithThresholdChanges"])
        self.assertEqual([], guard["rowsWithColorPolicyChanges"])
        self.assertEqual([], guard["rowsWithSilentApproximation"])
        self.assertEqual([], guard["wideGamutRowsClaimingSupport"])
        self.assertEqual([], guard["f16RowsClaimingGlobalPolicyChange"])
        self.assertEqual([], guard["broadBlendOrColorClaims"])
        self.assertEqual([], guard["ganeshGraphiteClaims"])
        self.assertEqual([], guard["skslCompilerClaims"])

        for row in evidence["policyRows"]:
            self.assertIn("no-wide-gamut-general-claim", row["nonClaims"])
            self.assertIn("no-hdr-or-gainmap-claim", row["nonClaims"])
            self.assertIn("no-all-blend-modes-claim", row["nonClaims"])

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan045.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "kan-045-color-pipeline-bounded-policy.json").read_text())
            markdown = (output_dir / "kan-045-color-pipeline-bounded-policy.md").read_text()

        self.assertEqual(evidence["summary"], payload["summary"])
        self.assertIn("# KAN-045 Color Pipeline Bounded Policy", markdown)
        self.assertIn("paint.src-over-alpha.rect-stack.v1", markdown)
        self.assertIn("paint.color-filter.blend-kplus.rect.v1", markdown)
        self.assertIn("color.color-space-wide-gamut-unsupported", markdown)
        self.assertIn("color.f16-policy-candidate-worsens-reference", markdown)


if __name__ == "__main__":
    unittest.main()
