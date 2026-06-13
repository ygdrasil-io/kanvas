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

import validate_kan055_text_glyph_atlas_visual_delta as kan055


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
    artifact_paths = {
        "reference": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/reference.png",
        "cpu": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/cpu.png",
        "webGpu": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/webgpu.png",
        "cpuDiff": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/cpu-diff.png",
        "webGpuDiff": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/webgpu-diff.png",
        "routeCpu": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/route-cpu.json",
        "routeWebGpu": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/route-webgpu.json",
        "stats": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/stats.json",
        "atlas": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/atlas.json",
    }
    for path in artifact_paths.values():
        write_artifact(root, path)
    for path in [*kan055.MATERIALIZED_BEFORE_ARTIFACTS.values(), *kan055.MATERIALIZED_AFTER_ARTIFACTS.values()]:
        write_artifact(root, path)

    before_stats = {
        "sceneId": kan055.SELECTED_ROW_ID,
        "webGpuMismatchingPixels": 608,
        "cpuMismatchingPixels": 581,
        "webGpuMinusCpuReferenceMismatches": 27,
        "webGpuSimilarity": 99.010417,
        "cpuSimilarity": 99.054362,
        "tolerance": 8,
        "webGpuSimilarityThreshold": 95.0,
        "cpuSimilarityThreshold": 95.0,
        "globalThresholdChanged": False,
    }
    write_json(
        root,
        kan055.KAN053_JSON,
        {
            "ticket": "KAN-053",
            "blocked": True,
            "blocker": {
                "rootCause": kan055.KAN053_ROOT_CAUSE,
                "reasonCode": kan055.KAN053_BLOCKER_REASON,
            },
            "current": {
                "artifacts": artifact_paths,
                "stats": before_stats,
                "routeWebGpu": {
                    "sceneId": kan055.SELECTED_ROW_ID,
                    "selectedRoute": kan055.LEGACY_ROUTE,
                    "fallbackReason": "none",
                },
            },
        },
    )
    write_json(
        root,
        kan055.KAN054_JSON,
        {
            "ticket": "KAN-054",
            "rendererChanged": True,
            "blocked": False,
            "selectedRoute": kan055.ATLAS_ROUTE,
            "fallbackReason": "none",
            "routeWebGpu": {
                "path": kan055.ROUTE_WEBGPU_JSON,
                "sceneId": kan055.SELECTED_ROW_ID,
                "selectedRoute": kan055.ATLAS_ROUTE,
                "fallbackReason": "none",
                "atlasRouteIdentifier": kan055.ATLAS_ROUTE,
            },
            "stats": {
                "path": kan055.STATS_JSON,
                "sceneId": kan055.SELECTED_ROW_ID,
                "webGpuRouteIdentifier": kan055.ATLAS_ROUTE,
                "globalThresholdChanged": False,
            },
        },
    )
    write_json(
        root,
        kan055.ROUTE_WEBGPU_JSON,
        {
            "sceneId": kan055.SELECTED_ROW_ID,
            "backend": "WebGPU",
            "selectedRoute": kan055.ATLAS_ROUTE,
            "legacyRoute": kan055.LEGACY_ROUTE,
            "atlasRouteIdentifier": kan055.ATLAS_ROUTE,
            "fallbackReason": "none",
            "referenceArtifact": artifact_paths["reference"],
            "renderArtifact": artifact_paths["webGpu"],
            "diffArtifact": artifact_paths["webGpuDiff"],
            "nonClaims": ["no-broad-text-claim"],
        },
    )
    write_json(
        root,
        kan055.STATS_JSON,
        {
            "sceneId": kan055.SELECTED_ROW_ID,
            "webGpuRouteIdentifier": kan055.ATLAS_ROUTE,
            "atlasRouteIdentifier": kan055.ATLAS_ROUTE,
            "tolerance": 8,
            "cpuSimilarityThreshold": 95.0,
            "webGpuSimilarityThreshold": 95.0,
            "globalThresholdChanged": False,
            "fallbackPolicy": "none",
            "cpuComparison": {"mismatchingPixels": 581, "similarity": 99.054362},
            "webGpuComparison": {"mismatchingPixels": 122, "similarity": 99.801432},
            "referenceArtifact": artifact_paths["reference"],
            "cpuArtifact": artifact_paths["cpu"],
            "webGpuArtifact": artifact_paths["webGpu"],
            "cpuDiffArtifact": artifact_paths["cpuDiff"],
            "webGpuDiffArtifact": artifact_paths["webGpuDiff"],
            "routeCpuArtifact": artifact_paths["routeCpu"],
            "routeWebGpuArtifact": artifact_paths["routeWebGpu"],
        },
    )
    for key, source in artifact_paths.items():
        shutil.copyfile(root / source, root / kan055.MATERIALIZED_AFTER_ARTIFACTS[key])
    return root


_FIXTURE_ROOTS: list[Path] = []


class TextGlyphAtlasVisualDeltaValidatorTest(unittest.TestCase):
    def test_build_evidence_accepts_improved_glyph_atlas_delta(self) -> None:
        evidence = kan055.build_evidence(PROJECT_ROOT)

        self.assertEqual("pending-validation", evidence["status"])
        self.assertEqual(kan055.ATLAS_ROUTE, evidence["after"]["routeWebGpu"]["selectedRoute"])
        self.assertEqual(608, evidence["delta"]["webGpuMismatchingPixelsBefore"])
        self.assertEqual(122, evidence["delta"]["webGpuMismatchingPixelsAfter"])
        self.assertEqual(486, evidence["delta"]["webGpuMismatchingPixelsImprovement"])
        self.assertGreater(evidence["delta"]["webGpuMismatchingPixelsImprovement"], 0)
        self.assertNotEqual(evidence["before"]["artifacts"]["webGpu"], evidence["after"]["artifacts"]["webGpu"])
        self.assertIn("text-glyph-atlas-visual-delta/before/", evidence["before"]["artifacts"]["webGpu"])
        self.assertIn("text-glyph-atlas-visual-delta/after/", evidence["after"]["artifacts"]["webGpu"])
        kan055.validate_evidence(evidence, PROJECT_ROOT)

    def test_write_outputs_sets_pass_and_close_decision(self) -> None:
        root = fixture_root()

        with tempfile.TemporaryDirectory() as temp:
            written = kan055.write_outputs(root, Path(temp))
            payload = json.loads((Path(temp) / kan055.OUTPUT_JSON).read_text(encoding="utf-8"))

        self.assertEqual("pass", written["status"])
        self.assertEqual("pass", payload["status"])
        self.assertEqual("close-root-cause-resolved", payload["kan053Decision"])

    def test_validation_rejects_no_improvement(self) -> None:
        root = fixture_root()
        stats_path = root / kan055.STATS_JSON
        stats = json.loads(stats_path.read_text(encoding="utf-8"))
        stats["webGpuComparison"]["mismatchingPixels"] = 608
        stats_path.write_text(json.dumps(stats, indent=2) + "\n", encoding="utf-8")

        evidence = kan055.build_evidence(root)
        with self.assertRaisesRegex(kan055.ValidationError, "after mismatch count must match"):
            kan055.validate_evidence(evidence, root)

    def test_validation_rejects_non_atlas_after_route(self) -> None:
        root = fixture_root()
        route_path = root / kan055.ROUTE_WEBGPU_JSON
        route = json.loads(route_path.read_text(encoding="utf-8"))
        route["selectedRoute"] = kan055.LEGACY_ROUTE
        route_path.write_text(json.dumps(route, indent=2) + "\n", encoding="utf-8")

        evidence = kan055.build_evidence(root)
        with self.assertRaisesRegex(kan055.ValidationError, "after route must be glyph atlas"):
            kan055.validate_evidence(evidence, root)

    def test_validation_rejects_threshold_change(self) -> None:
        root = fixture_root()
        stats_path = root / kan055.STATS_JSON
        stats = json.loads(stats_path.read_text(encoding="utf-8"))
        stats["globalThresholdChanged"] = True
        stats_path.write_text(json.dumps(stats, indent=2) + "\n", encoding="utf-8")

        evidence = kan055.build_evidence(root)
        with self.assertRaisesRegex(kan055.ValidationError, "global threshold changed"):
            kan055.validate_evidence(evidence, root)

    def test_validation_rejects_materialized_artifact_drift(self) -> None:
        root = fixture_root()
        (root / kan055.MATERIALIZED_AFTER_ARTIFACTS["webGpu"]).write_bytes(b"stale-after-webgpu")

        evidence = kan055.build_evidence(root)
        with self.assertRaisesRegex(kan055.ValidationError, "materialized artifact hash mismatch"):
            kan055.validate_evidence(evidence, root)

    @classmethod
    def tearDownClass(cls) -> None:
        for root in _FIXTURE_ROOTS:
            shutil.rmtree(root, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
