#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan046_tile_modes_mipmap_boundary as kan046


class TileModesMipmapBoundaryTest(unittest.TestCase):
    def test_build_evidence_exposes_bounded_tile_modes_and_mipmap_refusals(self) -> None:
        evidence = kan046.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-046", evidence["ticket"])
        self.assertEqual("kan-046-tile-modes-mipmap-boundary", evidence["packId"])
        self.assertEqual("tile-modes-mipmap-boundary", evidence["closureDecision"])
        self.assertEqual("bounded-bitmap-sampling-evidence", evidence["supportClaim"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["sharedShadersChanged"])
        self.assertFalse(evidence["thresholdsWeakened"])
        self.assertFalse(evidence["arbitraryTextureSupportClaim"])

        summary = evidence["summary"]
        self.assertEqual(4, summary["totalRows"])
        self.assertEqual(2, summary["tileModeSupportRows"])
        self.assertEqual(2, summary["mipmapExpectedUnsupportedRows"])
        self.assertEqual(0, summary["supportRowsMissingArtifacts"])
        self.assertEqual(0, summary["routesMissingSampling"])
        self.assertEqual(0, summary["routesMissingLocalMatrix"])
        self.assertEqual(0, summary["routesMissingTileMode"])
        self.assertEqual(0, summary["routesMissingMipmapMode"])
        self.assertEqual(0, summary["broadTextureClaims"])

        rows = {row["rowId"]: row for row in evidence["samplingRows"]}
        repeat = rows["bitmap-shader-repeat-tile"]
        self.assertEqual("tile-mode-support", repeat["pmCategory"])
        self.assertEqual("pass", repeat["status"])
        self.assertEqual("nearest", repeat["sampling"]["filterMode"])
        self.assertEqual({"x": "kRepeat", "y": "kRepeat"}, repeat["tileMode"])
        self.assertEqual("identity", repeat["localMatrix"]["kind"])
        self.assertEqual("none", repeat["mipmapMode"])
        self.assertEqual("none", repeat["route"]["fallbackReason"])
        self.assertTrue(repeat["proofs"]["reference"])
        self.assertTrue(repeat["proofs"]["cpu"])
        self.assertTrue(repeat["proofs"]["gpu"])
        self.assertTrue(repeat["proofs"]["diff"])
        self.assertTrue(repeat["proofs"]["stats"])
        self.assertTrue(repeat["proofs"]["route"])

        subset = rows["bitmap-subset-local-matrix-repeat"]
        self.assertEqual("tile-mode-support", subset["pmCategory"])
        self.assertEqual("pass", subset["status"])
        self.assertEqual("nearest", subset["sampling"]["filterMode"])
        self.assertEqual({"x": "kRepeat", "y": "kRepeat"}, subset["tileMode"])
        self.assertEqual("affine-scale-rotate", subset["localMatrix"]["kind"])
        self.assertEqual("none", subset["mipmapMode"])
        self.assertEqual("none", subset["route"]["fallbackReason"])

        mipmap = rows["bitmap-mipmap-sampler-refusal"]
        self.assertEqual("mipmap-boundary-refusal", mipmap["pmCategory"])
        self.assertEqual("expected-unsupported", mipmap["status"])
        self.assertEqual("image-sampling.mipmap-unsupported", mipmap["reasonCode"])
        self.assertEqual("nearest-with-mipmap-request", mipmap["sampling"]["filterMode"])
        self.assertEqual("required-but-no-chain", mipmap["mipmapMode"])
        self.assertFalse(mipmap["mipmapChainPresent"])

        npot = rows["bitmap-npot-mipmap-sampler-refusal"]
        self.assertEqual("mipmap-boundary-refusal", npot["pmCategory"])
        self.assertEqual("expected-unsupported", npot["status"])
        self.assertEqual("image-sampling.mipmap-unsupported", npot["reasonCode"])
        self.assertEqual("linear-with-mipmap-request", npot["sampling"]["filterMode"])
        self.assertEqual("required-but-no-chain", npot["mipmapMode"])
        self.assertFalse(npot["mipmapChainPresent"])

    def test_guards_prevent_hidden_texture_or_mipmap_claims(self) -> None:
        evidence = kan046.build_evidence(PROJECT_ROOT)
        guard = evidence["claimGuard"]

        self.assertEqual([], guard["supportRowsMissingArtifacts"])
        self.assertEqual([], guard["supportRowsWithFallback"])
        self.assertEqual([], guard["unsupportedRowsMissingReason"])
        self.assertEqual([], guard["routesMissingSampling"])
        self.assertEqual([], guard["routesMissingLocalMatrix"])
        self.assertEqual([], guard["routesMissingTileMode"])
        self.assertEqual([], guard["routesMissingMipmapMode"])
        self.assertEqual([], guard["hiddenArbitraryTextureClaims"])
        self.assertEqual([], guard["hiddenCodecDecodeClaims"])
        self.assertEqual([], guard["hiddenPerspectiveClaims"])
        self.assertEqual([], guard["hiddenMipmapSupportClaims"])

        for row in evidence["samplingRows"]:
            self.assertIn("no-arbitrary-texture-claim", row["nonClaims"])
            self.assertIn("no-codec-decode-claim", row["nonClaims"])
            self.assertIn("no-perspective-sampling-claim", row["nonClaims"])
            if row["status"] == "pass":
                self.assertEqual("none", row["route"]["fallbackReason"])
                self.assertEqual("none", row["mipmapMode"])
            else:
                self.assertEqual("image-sampling.mipmap-unsupported", row["reasonCode"])
                self.assertNotEqual("none", row["route"]["fallbackReason"])

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan046.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / "kan-046-tile-modes-mipmap-boundary.json").read_text())
            markdown = (output_dir / "kan-046-tile-modes-mipmap-boundary.md").read_text()

        self.assertEqual(evidence["summary"], payload["summary"])
        self.assertIn("# KAN-046 Tile Modes And Mipmap Boundary", markdown)
        self.assertIn("bitmap-shader-repeat-tile", markdown)
        self.assertIn("bitmap-subset-local-matrix-repeat", markdown)
        self.assertIn("image-sampling.mipmap-unsupported", markdown)
        self.assertIn("No arbitrary texture support", markdown)


if __name__ == "__main__":
    unittest.main()
