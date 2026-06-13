#!/usr/bin/env python3
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_kan056_glyph_atlas_route_hardening as kan056


def write_json(root: Path, relative_path: str, payload: dict) -> None:
    path = root / relative_path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def write_artifact(root: Path, relative_path: str) -> None:
    path = root / relative_path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"fixture")


def fixture_root() -> Path:
    root = Path(tempfile.mkdtemp())
    _FIXTURE_ROOTS.append(root)
    for path in [
        kan056.ROUTE_WEBGPU_JSON,
        kan056.ROUTE_CPU_JSON,
        kan056.STATS_JSON,
        kan056.ATLAS_JSON,
        "reports/wgsl-pipeline/text-glyph-atlas-visual-delta/after/reference.png",
        "reports/wgsl-pipeline/text-glyph-atlas-visual-delta/after/cpu.png",
        "reports/wgsl-pipeline/text-glyph-atlas-visual-delta/after/webgpu.png",
        "reports/wgsl-pipeline/text-glyph-atlas-visual-delta/after/webgpu-diff.png",
    ]:
        write_artifact(root, path)
    write_json(
        root,
        kan056.KAN054_JSON,
        {
            "ticket": "KAN-054",
            "status": "pass",
            "rendererChanged": True,
            "blocked": False,
            "selectedRowId": kan056.SELECTED_ROW_ID,
            "selectedRoute": kan056.ATLAS_ROUTE,
            "fallbackReason": "none",
            "atlas": {
                "generation": 1,
                "uploadByteCount": 12928,
                "textureFormat": "R8Unorm",
                "maskFormat": "A8",
                "sourceCacheSha256": "source-cache",
                "uploadSha256": "upload",
                "sampler": "nearest-clamp-to-edge",
            },
            "kan044AtlasUploadPlan": {
                "cacheIds": {
                    "scopeId": "text.simple-latin.liberation-sans-regular.v1",
                    "routeIdentifier": kan056.ATLAS_ROUTE,
                    "textureLabel": "kan-011.text.simple-latin.liberation-sans-regular.v1.glyph-atlas.generation-1",
                    "sourceCacheSha256": "source-cache",
                    "uploadSha256": "upload",
                }
            },
            "nonClaims": sorted(kan056.REQUIRED_SOURCE_NON_CLAIMS),
            "routeWebGpu": {
                "sceneId": kan056.SELECTED_ROW_ID,
                "selectedRoute": kan056.ATLAS_ROUTE,
                "fallbackReason": "none",
                "supportScope": "simple-latin-line-visible",
                "nonClaims": sorted(kan056.REQUIRED_ROUTE_NON_CLAIMS),
            },
            "standaloneAlphaMaskRefusal": {
                "rowId": "webgpu.standalone-alpha-mask-refusal",
                "status": "expected-unsupported",
                "reasonCode": kan056.ALPHA_MASK_REFUSAL,
                "coverageOwnsAtlas": False,
                "route": {
                    "coverage": "CoveragePlan.AlphaMask",
                    "webGpu": "webgpu.refuse.standalone-alpha-mask",
                },
            },
        },
    )
    write_json(
        root,
        kan056.KAN055_JSON,
        {
            "ticket": "KAN-055",
            "status": "pass",
            "selectedRowId": kan056.SELECTED_ROW_ID,
            "kan054RendererChanged": True,
            "after": {
                "routeWebGpu": {"selectedRoute": kan056.ATLAS_ROUTE, "fallbackReason": "none"},
                "artifacts": {
                    "reference": "reports/wgsl-pipeline/text-glyph-atlas-visual-delta/after/reference.png",
                    "cpu": "reports/wgsl-pipeline/text-glyph-atlas-visual-delta/after/cpu.png",
                    "webGpu": "reports/wgsl-pipeline/text-glyph-atlas-visual-delta/after/webgpu.png",
                    "webGpuDiff": "reports/wgsl-pipeline/text-glyph-atlas-visual-delta/after/webgpu-diff.png",
                    "routeWebGpu": kan056.ROUTE_WEBGPU_JSON,
                    "stats": kan056.STATS_JSON,
                    "atlas": kan056.ATLAS_JSON,
                },
                "stats": {"globalThresholdChanged": False},
            },
            "delta": {
                "webGpuMismatchingPixelsBefore": 608,
                "webGpuMismatchingPixelsAfter": 122,
                "webGpuMismatchingPixelsImprovement": 486,
            },
            "kan053Decision": "close-root-cause-resolved",
        },
    )
    write_json(
        root,
        kan056.ROUTE_WEBGPU_JSON,
        {
            "sceneId": kan056.SELECTED_ROW_ID,
            "backend": "WebGPU",
            "selectedRoute": kan056.ATLAS_ROUTE,
            "fallbackReason": "none",
            "atlasRouteIdentifier": kan056.ATLAS_ROUTE,
            "glyphSourceRoute": "font.glyph.outline-path",
            "supportScope": "simple-latin-line-visible",
            "nonClaims": sorted(kan056.REQUIRED_ROUTE_NON_CLAIMS),
        },
    )
    write_json(root, kan056.ROUTE_CPU_JSON, {"sceneId": kan056.SELECTED_ROW_ID, "backend": "CPU"})
    write_json(
        root,
        kan056.STATS_JSON,
        {
            "sceneId": kan056.SELECTED_ROW_ID,
            "webGpuRouteIdentifier": kan056.ATLAS_ROUTE,
            "atlasRouteIdentifier": kan056.ATLAS_ROUTE,
            "atlasUploadByteCount": 12928,
            "glyphInventoryCount": 26,
            "dedupedGlyphCount": 26,
            "globalThresholdChanged": False,
            "webGpuSimilarityThreshold": 95.0,
            "cpuSimilarityThreshold": 95.0,
        },
    )
    write_json(
        root,
        kan056.ATLAS_JSON,
        {
            "routeIdentifier": kan056.ATLAS_ROUTE,
            "generation": 1,
            "uploadByteCount": 12928,
            "textureFormat": "R8Unorm",
            "maskFormat": "A8",
            "sourceCacheSha256": "source-cache",
            "uploadSha256": "upload",
            "diagnostics": {
                "sampler": "nearest-clamp-to-edge",
                "resourceKind": "webgpu.texture-upload-plan",
                "glyphEntryCount": 26,
                "nonEmptyGlyphCount": 25,
            },
        },
    )
    return root


_FIXTURE_ROOTS: list[Path] = []


class GlyphAtlasRouteHardeningValidatorTest(unittest.TestCase):
    def test_build_evidence_accepts_bounded_glyph_atlas_hardening_pack(self) -> None:
        evidence = kan056.build_evidence(fixture_root())

        self.assertEqual("KAN-056", evidence["ticket"])
        self.assertEqual("pending-validation", evidence["status"])
        self.assertEqual(kan056.ATLAS_ROUTE, evidence["supportedRoute"]["routeId"])
        self.assertEqual({"supported", "expected-unsupported", "dependency-gated", "reporting-only"}, {row["category"] for row in evidence["matrixRows"]})
        self.assertEqual(1, evidence["diagnostics"]["atlasGeneration"])
        self.assertEqual(12928, evidence["diagnostics"]["atlasUploadBytes"])
        self.assertFalse(evidence["nativeKadreCiRequired"])
        kan056.validate_evidence(evidence, fixture_root())

    def test_write_outputs_sets_pass_and_manifest_entry(self) -> None:
        root = fixture_root()
        with tempfile.TemporaryDirectory() as temp:
            written = kan056.write_outputs(root, Path(temp))
            payload = json.loads((Path(temp) / kan056.OUTPUT_JSON).read_text(encoding="utf-8"))
            manifest = json.loads((Path(temp) / kan056.OUTPUT_MANIFEST_ENTRY).read_text(encoding="utf-8"))

        self.assertEqual("pass", written["status"])
        self.assertEqual("pass", payload["status"])
        self.assertEqual("kan056GlyphAtlasRouteHardening", manifest["key"])

    def test_inject_pm_bundle_copies_evidence_and_updates_manifest(self) -> None:
        root = fixture_root()
        with tempfile.TemporaryDirectory() as output_temp, tempfile.TemporaryDirectory() as bundle_temp:
            output_dir = Path(output_temp)
            bundle_dir = Path(bundle_temp)
            (bundle_dir / "manifest.json").write_text(json.dumps({"schemaVersion": 1}) + "\n", encoding="utf-8")
            kan056.write_outputs(root, output_dir)

            kan056.inject_pm_bundle(output_dir, bundle_dir)
            manifest = json.loads((bundle_dir / "manifest.json").read_text(encoding="utf-8"))
            copied = bundle_dir / "release/kan-056-glyph-atlas-route-hardening" / kan056.OUTPUT_JSON

            self.assertTrue(copied.is_file())
            self.assertEqual("glyph-atlas-route-hardening-pm-gates", manifest["kan056GlyphAtlasRouteHardening"]["claimLevel"])
            self.assertFalse(manifest["kan056GlyphAtlasRouteHardening"]["nativeKadreCiRequired"])

    def test_validation_rejects_broad_text_support_claim(self) -> None:
        root = fixture_root()
        evidence = kan056.build_evidence(root)
        evidence["matrixRows"].append({"rowId": "bad", "category": "supported", "claimScope": "broad-text"})

        with self.assertRaisesRegex(kan056.ValidationError, "broad text claim"):
            kan056.validate_evidence(evidence, root)

    def test_validation_rejects_missing_visual_proof_for_supported_row(self) -> None:
        root = fixture_root()
        evidence = kan056.build_evidence(root)
        evidence["matrixRows"][0]["proofs"]["reference"] = False

        with self.assertRaisesRegex(kan056.ValidationError, "supported row missing proof"):
            kan056.validate_evidence(evidence, root)

    def test_validation_rejects_threshold_change(self) -> None:
        root = fixture_root()
        stats_path = root / kan056.STATS_JSON
        stats = json.loads(stats_path.read_text(encoding="utf-8"))
        stats["globalThresholdChanged"] = True
        stats_path.write_text(json.dumps(stats, indent=2) + "\n", encoding="utf-8")

        evidence = kan056.build_evidence(root)
        with self.assertRaisesRegex(kan056.ValidationError, "global threshold changed"):
            kan056.validate_evidence(evidence, root)

    def test_validation_rejects_broadened_source_route_scope(self) -> None:
        root = fixture_root()
        route_path = root / kan056.ROUTE_WEBGPU_JSON
        route = json.loads(route_path.read_text(encoding="utf-8"))
        route["supportScope"] = "broad-text"
        route_path.write_text(json.dumps(route, indent=2) + "\n", encoding="utf-8")

        evidence = kan056.build_evidence(root)
        with self.assertRaisesRegex(kan056.ValidationError, "support scope"):
            kan056.validate_evidence(evidence, root)

    def test_validation_rejects_missing_source_alpha_mask_refusal(self) -> None:
        root = fixture_root()
        kan054_path = root / kan056.KAN054_JSON
        kan054 = json.loads(kan054_path.read_text(encoding="utf-8"))
        kan054["standaloneAlphaMaskRefusal"]["status"] = "supported"
        kan054["standaloneAlphaMaskRefusal"]["reasonCode"] = "coverage.alpha-mask-supported"
        kan054_path.write_text(json.dumps(kan054, indent=2) + "\n", encoding="utf-8")

        evidence = kan056.build_evidence(root)
        with self.assertRaisesRegex(kan056.ValidationError, "alpha mask refusal"):
            kan056.validate_evidence(evidence, root)

    def test_validation_rejects_missing_source_non_claim(self) -> None:
        root = fixture_root()
        kan054_path = root / kan056.KAN054_JSON
        kan054 = json.loads(kan054_path.read_text(encoding="utf-8"))
        kan054["nonClaims"].remove("no-fallback-font-claim")
        kan054_path.write_text(json.dumps(kan054, indent=2) + "\n", encoding="utf-8")

        evidence = kan056.build_evidence(root)
        with self.assertRaisesRegex(kan056.ValidationError, "source non-claims"):
            kan056.validate_evidence(evidence, root)

    @classmethod
    def tearDownClass(cls) -> None:
        for root in _FIXTURE_ROOTS:
            shutil.rmtree(root, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
