#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan044_glyph_mask_atlas_ownership as kan044


class GlyphMaskAtlasOwnershipTest(unittest.TestCase):
    def test_build_evidence_exposes_text_owned_atlas_boundary(self) -> None:
        evidence = kan044.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-044", evidence["ticket"])
        self.assertEqual("kan-044-glyph-mask-atlas-ownership", evidence["packId"])
        self.assertEqual("glyph-mask-atlas-ownership-boundary", evidence["closureDecision"])
        self.assertEqual("no-new-rendering-support", evidence["supportClaim"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["coverageOwnsGlyphAtlas"])
        self.assertFalse(evidence["broadAtlasEvictionClaim"])

        summary = evidence["summary"]
        self.assertEqual(4, summary["totalRows"])
        self.assertEqual(1, summary["atlasUploadPlanRows"])
        self.assertEqual(1, summary["cpuMaskOracleRows"])
        self.assertEqual(1, summary["coverageHandoffRows"])
        self.assertEqual(1, summary["webGpuRefusalRows"])
        self.assertEqual(0, summary["rowsMissingGlyphKeys"])
        self.assertEqual(0, summary["rowsMissingAtlasGeneration"])
        self.assertEqual(0, summary["rowsMissingUploadBytes"])
        self.assertEqual(0, summary["coverageOwnershipViolations"])

        rows = {row["rowId"]: row for row in evidence["ownershipRows"]}
        atlas = rows["text.simple-latin.glyph-atlas.upload-plan"]
        self.assertEqual("atlas-upload-plan", atlas["pmCategory"])
        self.assertEqual("pass", atlas["status"])
        self.assertEqual("webgpu.text.glyph-atlas.simple-latin", atlas["route"]["webGpu"])
        self.assertEqual("R8Unorm", atlas["atlas"]["textureFormat"])
        self.assertEqual("A8", atlas["atlas"]["maskFormat"])
        self.assertEqual(1, atlas["atlas"]["generation"])
        self.assertEqual(12928, atlas["atlas"]["uploadByteCount"])
        self.assertEqual(26, atlas["atlas"]["glyphEntryCount"])
        self.assertEqual(atlas["atlas"]["glyphEntryCount"], len(atlas["glyphKeys"]))
        self.assertTrue(all(key.startswith("text.simple-latin.liberation-sans-regular.v1|") for key in atlas["glyphKeys"]))
        self.assertEqual("text-glyph-infrastructure", atlas["ownership"]["atlasLifetimeOwner"])
        self.assertEqual("coverage-consumes-opaque-mask-ref-only", atlas["coverageBoundary"])

        cpu = rows["text.simple-latin.cpu-mask-oracle"]
        self.assertEqual("cpu-mask-oracle", cpu["pmCategory"])
        self.assertEqual("pass", cpu["status"])
        self.assertEqual("SkWebGpuGlyphAtlasTest", cpu["oracle"]["testName"])
        self.assertGreater(cpu["oracle"]["nonEmptyGlyphCount"], 0)
        self.assertEqual(cpu["oracle"]["sampledMaskMatchesAtlas"], True)
        self.assertTrue(cpu["oracle"]["firstMaskSha256"])

        handoff = rows["geometry.glyph-mask.alpha-mask-handoff"]
        self.assertEqual("coverage-handoff", handoff["pmCategory"])
        self.assertEqual("pass", handoff["status"])
        self.assertEqual("geometry.glyph-mask.alpha-mask-handoff", handoff["route"]["coverage"])
        self.assertEqual("CoveragePlan.AlphaMask", handoff["coveragePlan"])
        self.assertEqual("CoverageModel.AlphaMask", handoff["coverageModel"])
        self.assertEqual("text-glyph-infrastructure", handoff["ownership"]["discoveryOwner"])
        self.assertFalse(handoff["coverageOwnsAtlas"])

        refusal = rows["webgpu.standalone-alpha-mask-refusal"]
        self.assertEqual("webgpu-alpha-mask-refusal", refusal["pmCategory"])
        self.assertEqual("expected-unsupported", refusal["status"])
        self.assertEqual("coverage.alpha-mask-unsupported", refusal["reasonCode"])
        self.assertEqual("webgpu.refuse.standalone-alpha-mask", refusal["route"]["webGpu"])

    def test_guards_prevent_atlas_claims_without_ownership_proof(self) -> None:
        evidence = kan044.build_evidence(PROJECT_ROOT)
        guard = evidence["claimGuard"]

        self.assertEqual([], guard["rowsMissingGlyphKeys"])
        self.assertEqual([], guard["rowsMissingAtlasGeneration"])
        self.assertEqual([], guard["rowsMissingUploadBytes"])
        self.assertEqual([], guard["rowsMissingCacheIds"])
        self.assertEqual([], guard["cpuMaskOracleMissing"])
        self.assertEqual([], guard["coverageOwnershipViolations"])
        self.assertEqual([], guard["webGpuRouteMissingDecision"])
        self.assertEqual([], guard["hiddenAtlasEvictionClaims"])
        self.assertEqual([], guard["lcdOrSdfClaims"])
        self.assertEqual([], guard["ganeshGraphiteClaims"])
        self.assertEqual([], guard["thresholdOrRendererChanges"])

        for row in evidence["ownershipRows"]:
            self.assertNotIn("broad-atlas-eviction-claim", row["nonClaims"])
            self.assertIn("no-broad-glyph-atlas-claim", row["nonClaims"])

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan044.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "kan-044-glyph-mask-atlas-ownership.json").read_text())
            markdown = (output_dir / "kan-044-glyph-mask-atlas-ownership.md").read_text()

        self.assertEqual(evidence["summary"], payload["summary"])
        self.assertIn("# KAN-044 Glyph Mask Atlas Ownership", markdown)
        self.assertIn("text-glyph-infrastructure", markdown)
        self.assertIn("coverage.alpha-mask-unsupported", markdown)
        self.assertIn("coverage consumes opaque mask refs only", markdown)


if __name__ == "__main__":
    unittest.main()
