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

import validate_kan047_codec_provenance_matrix as kan047


class CodecProvenanceMatrixTest(unittest.TestCase):
    def test_build_evidence_separates_scene_sources_from_codec_support(self) -> None:
        evidence = kan047.build_evidence(PROJECT_ROOT)

        self.assertEqual("KAN-047", evidence["ticket"])
        self.assertEqual("kan-047-codec-provenance-matrix", evidence["packId"])
        self.assertEqual("codec-provenance-matrix", evidence["closureDecision"])
        self.assertEqual("pm-codec-provenance-evidence", evidence["claimLevel"])
        self.assertFalse(evidence["rendererChanged"])
        self.assertFalse(evidence["codecRuntimeChanged"])
        self.assertFalse(evidence["thresholdsWeakened"])
        self.assertFalse(evidence["nativeJniBridgeClaim"])
        self.assertFalse(evidence["animatedRendererSupportClaim"])

        summary = evidence["summary"]
        self.assertEqual(6, summary["sceneRows"])
        self.assertEqual(3, summary["deterministicFixtureSceneRows"])
        self.assertEqual(1, summary["realCodecDecodeSceneRows"])
        self.assertEqual(2, summary["dependencyGatedSceneRows"])
        self.assertEqual(7, summary["portableCodecDecodeFormats"])
        self.assertEqual(4, summary["dependencyGatedFormatRows"])
        self.assertEqual(0, summary["stubCodecPassRows"])
        self.assertEqual(0, summary["fixtureRowsClaimingCodecDecode"])
        self.assertEqual(0, summary["sceneRowsMissingProvenance"])

        rows = {row["rowId"]: row for row in evidence["sceneRows"]}

        fixture = rows["paint.bitmap-rect.nearest.fixture.v1"]
        self.assertEqual("pass", fixture["status"])
        self.assertEqual("raw-rgba8888-fixture", fixture["format"])
        self.assertEqual("none", fixture["decoder"]["name"])
        self.assertEqual("deterministic-fixture", fixture["decoder"]["kind"])
        self.assertEqual("fixture-no-codec-decode", fixture["decodeResult"]["status"])
        self.assertEqual("srgb-unmanaged-fixture-oracle", fixture["colorInfo"]["policy"])
        self.assertEqual(15360, fixture["stats"]["matchingPixels"])
        self.assertEqual(15360, fixture["stats"]["gpuMatchingPixels"])
        self.assertIn("no-codec-decode-claim", fixture["nonClaims"])

        subset = rows["bitmap-subset-local-matrix-repeat"]
        self.assertEqual("pass", subset["status"])
        self.assertEqual("PNG", subset["format"])
        self.assertEqual("codec-png-kotlin", subset["decoder"]["module"])
        self.assertEqual("kSuccess", subset["decodeResult"]["status"])
        self.assertEqual("images/color_wheel.png", subset["origin"]["resource"])
        self.assertEqual("bounded-bitmap-sampling-only", subset["supportClaim"])
        self.assertIn("no-broad-codec-support-claim", subset["nonClaims"])

        webp = rows["animated-image-gm-stoplight-webp"]
        self.assertEqual("dependency-gated", webp["status"])
        self.assertEqual("WebP", webp["format"])
        self.assertEqual("codec.animated-frame-unsupported", webp["reasonCode"])
        self.assertFalse(webp["supportClaim"])

        gif = rows["animated-image-gm-flight-gif"]
        self.assertEqual("dependency-gated", gif["status"])
        self.assertEqual("GIF", gif["format"])
        self.assertEqual("codec.animated-frame-unsupported", gif["reasonCode"])
        self.assertFalse(gif["supportClaim"])

    def test_codec_format_rows_keep_stubs_dependency_gated(self) -> None:
        evidence = kan047.build_evidence(PROJECT_ROOT)
        formats = {row["format"]: row for row in evidence["codecFormatRows"]}

        self.assertEqual("supported", formats["PNG"]["status"])
        self.assertEqual("codec-png-kotlin", formats["PNG"]["decoder"]["module"])
        self.assertEqual("kSuccess-for-covered-real-fixtures", formats["PNG"]["decodeResult"])

        self.assertEqual("supported", formats["ICO / CUR"]["status"])
        self.assertEqual("codec-ico-kotlin", formats["ICO / CUR"]["decoder"]["module"])
        self.assertEqual("delegates-to-selected-payload-decoder", formats["ICO / CUR"]["decodeResult"])

        for name in ["AVIF", "JPEG XL", "RAW", "video"]:
            with self.subTest(name=name):
                row = formats[name]
                self.assertEqual("dependency-gated", row["status"])
                self.assertEqual("stub", row["decoder"]["kind"])
                self.assertEqual("codec.decoder-unavailable", row["reasonCode"])
                self.assertNotEqual("kSuccess", row["decodeResult"])

    def test_claim_guard_rejects_stub_codec_pass_rows(self) -> None:
        evidence = kan047.build_evidence(PROJECT_ROOT)
        evidence["sceneRows"] = copy.deepcopy(evidence["sceneRows"])
        evidence["sceneRows"].append(
            {
                "rowId": "fake-avif-pass",
                "status": "pass",
                "format": "AVIF",
                "decoder": {"name": "avif", "kind": "stub", "module": "codec-extended"},
                "colorInfo": {"policy": "unknown"},
                "origin": {"kind": "synthetic"},
                "decodeResult": {"status": "kSuccess"},
                "supportClaim": "fake",
                "nonClaims": [],
            }
        )

        with self.assertRaisesRegex(kan047.ValidationError, "stub codec pass rows"):
            kan047.validate_evidence(evidence, PROJECT_ROOT)

    def test_claim_guard_rejects_fixture_row_claiming_real_decode(self) -> None:
        evidence = kan047.build_evidence(PROJECT_ROOT)
        evidence["sceneRows"] = copy.deepcopy(evidence["sceneRows"])
        evidence["sceneRows"][0]["decoder"]["kind"] = "portable-codec"
        evidence["sceneRows"][0]["decodeResult"]["status"] = "kSuccess"

        with self.assertRaisesRegex(kan047.ValidationError, "fixture rows claiming codec decode"):
            kan047.validate_evidence(evidence, PROJECT_ROOT)

    def test_claim_guard_requires_dependency_reason_codes(self) -> None:
        evidence = kan047.build_evidence(PROJECT_ROOT)
        evidence["codecFormatRows"] = copy.deepcopy(evidence["codecFormatRows"])
        evidence["codecFormatRows"][-1]["reasonCode"] = ""

        with self.assertRaisesRegex(kan047.ValidationError, "dependency-gated format rows missing reason"):
            kan047.validate_evidence(evidence, PROJECT_ROOT)

    def test_write_outputs_materializes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan047.write_outputs(PROJECT_ROOT, output_dir)
            payload = json.loads((output_dir / kan047.OUTPUT_JSON).read_text(encoding="utf-8"))
            markdown = (output_dir / kan047.OUTPUT_MARKDOWN).read_text(encoding="utf-8")

        self.assertEqual(evidence["summary"], payload["summary"])
        self.assertIn("# KAN-047 Codec Provenance Matrix", markdown)
        self.assertIn("bitmap-subset-local-matrix-repeat", markdown)
        self.assertIn("codec-png-kotlin", markdown)
        self.assertIn("codec.animated-frame-unsupported", markdown)
        self.assertIn("No stub codec renders a scene pass", markdown)


if __name__ == "__main__":
    unittest.main()
