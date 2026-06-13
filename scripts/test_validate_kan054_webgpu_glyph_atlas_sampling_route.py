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

import validate_kan054_webgpu_glyph_atlas_sampling_route as kan054


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

    for path in (
        "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/reference.png",
        "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/cpu.png",
        "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/webgpu.png",
        "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/cpu-diff.png",
        "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/webgpu-diff.png",
        "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/route-cpu.json",
    ):
        write_artifact(root, path)

    write_json(
        root,
        kan054.ROUTE_WEBGPU_JSON,
        {
            "sceneId": kan054.SELECTED_ROW_ID,
            "backend": "WebGPU",
            "selectedRoute": kan054.ATLAS_ROUTE,
            "fallbackReason": "none",
            "glyphSourceRoute": "font.glyph.outline-path",
            "atlasRouteIdentifier": kan054.ATLAS_ROUTE,
            "renderArtifact": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/webgpu.png",
            "diffArtifact": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/webgpu-diff.png",
            "referenceArtifact": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/reference.png",
            "nonClaims": ["no-broad-text-claim"],
        },
    )
    write_json(
        root,
        kan054.STATS_JSON,
        {
            "sceneId": kan054.SELECTED_ROW_ID,
            "webGpuRouteIdentifier": kan054.ATLAS_ROUTE,
            "atlasRouteIdentifier": kan054.ATLAS_ROUTE,
            "glyphSourceRoute": "font.glyph.outline-path",
            "atlasUploadByteCount": 12928,
            "atlasUploadSha256": "a" * 64,
            "glyphInventoryCount": 32,
            "dedupedGlyphCount": 26,
            "tolerance": 8,
            "cpuSimilarityThreshold": 95.0,
            "webGpuSimilarityThreshold": 95.0,
            "globalThresholdChanged": False,
            "fallbackPolicy": "none-for-supported-simple-latin-line",
        },
    )
    write_json(
        root,
        kan054.ATLAS_JSON,
        {
            "routeIdentifier": kan054.ATLAS_ROUTE,
            "textureLabel": "kan-054.fixture.glyph-atlas.generation-1",
            "textureFormat": "R8Unorm",
            "textureUsage": "TextureBinding|CopyDst",
            "maskFormat": "A8",
            "generation": 1,
            "width": 128,
            "height": 101,
            "uploadByteCount": 12928,
            "uploadSha256": "a" * 64,
            "sourceCacheSha256": "b" * 64,
            "diagnostics": {
                "sampler": "nearest-clamp-to-edge",
                "resourceKind": "webgpu.texture-upload-plan",
                "glyphEntryCount": 26,
                "nonEmptyGlyphCount": 25,
                "emptyGlyphCount": 1,
            },
        },
    )
    write_json(
        root,
        kan054.KAN053_JSON,
        {
            "ticket": "KAN-053",
            "blocked": True,
            "blocker": {
                "rootCause": kan054.KAN053_ROOT_CAUSE,
                "reasonCode": "requires-production-glyph-atlas-sampling-route",
            },
        },
    )
    write_json(
        root,
        kan054.KAN044_JSON,
        {
            "ticket": "KAN-044",
            "ownershipRows": [
                {
                    "rowId": "text.simple-latin.glyph-atlas.upload-plan",
                    "status": "pass",
                    "route": {"webGpu": kan054.ATLAS_ROUTE, "coverage": "not-owned-by-coverage"},
                    "atlas": {
                        "textureFormat": "R8Unorm",
                        "maskFormat": "A8",
                        "uploadByteCount": 12928,
                    },
                    "nonClaims": ["no-broad-glyph-atlas-claim"],
                },
                {
                    "rowId": "webgpu.standalone-alpha-mask-refusal",
                    "status": "expected-unsupported",
                    "reasonCode": kan054.ALPHA_MASK_REFUSAL,
                    "route": {"webGpu": "webgpu.refuse.standalone-alpha-mask"},
                    "coverageOwnsAtlas": False,
                    "nonClaims": ["no-broad-glyph-atlas-claim"],
                },
            ],
        },
    )
    return root


def remove_kan044_row(root: Path, row_id: str) -> None:
    path = root / kan054.KAN044_JSON
    payload = json.loads(path.read_text(encoding="utf-8"))
    payload["ownershipRows"] = [row for row in payload["ownershipRows"] if row.get("rowId") != row_id]
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


_FIXTURE_ROOTS: list[Path] = []


class WebGpuGlyphAtlasSamplingRouteValidatorTest(unittest.TestCase):
    def test_pm_validator_stays_headless_and_does_not_require_adapter_test(self) -> None:
        build_gradle = (PROJECT_ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        task_start = build_gradle.index('tasks.register<Exec>("validateKan054WebGpuGlyphAtlasSamplingRoute")')
        task_end = build_gradle.index('\ntasks.', task_start + 1)
        task_body = build_gradle[task_start:task_end]

        self.assertNotIn(":gpu-raster:kan054WebGpuGlyphAtlasSamplingRouteTest", task_body)

    def test_build_evidence_accepts_current_glyph_atlas_route(self) -> None:
        evidence = kan054.build_evidence(PROJECT_ROOT)

        self.assertEqual("pending-validation", evidence["status"])
        self.assertEqual(kan054.ATLAS_ROUTE, evidence["selectedRoute"])
        kan054.validate_evidence(evidence, PROJECT_ROOT)

    def test_write_outputs_sets_success_status_after_validation(self) -> None:
        root = fixture_root()
        evidence = kan054.build_evidence(root)
        self.assertEqual("pending-validation", evidence["status"])

        with tempfile.TemporaryDirectory() as temp:
            written = kan054.write_outputs(root, Path(temp))
            payload = json.loads((Path(temp) / kan054.OUTPUT_JSON).read_text(encoding="utf-8"))

        self.assertEqual("pass", written["status"])
        self.assertEqual("pass", payload["status"])

    def test_build_evidence_rejects_missing_kan044_atlas_upload_row(self) -> None:
        root = fixture_root()
        remove_kan044_row(root, "text.simple-latin.glyph-atlas.upload-plan")

        with self.assertRaisesRegex(
            kan054.ValidationError,
            "KAN-044 row missing: text.simple-latin.glyph-atlas.upload-plan",
        ):
            kan054.build_evidence(root)

    def test_build_evidence_rejects_missing_kan044_alpha_mask_refusal_row(self) -> None:
        root = fixture_root()
        remove_kan044_row(root, "webgpu.standalone-alpha-mask-refusal")

        with self.assertRaisesRegex(
            kan054.ValidationError,
            "KAN-044 row missing: webgpu.standalone-alpha-mask-refusal",
        ):
            kan054.build_evidence(root)

    def test_validation_rejects_missing_renderer_changed_marker(self) -> None:
        root = fixture_root()
        evidence = kan054.build_evidence(root)
        evidence["rendererChanged"] = False

        with self.assertRaisesRegex(kan054.ValidationError, "rendererChanged must be true"):
            kan054.validate_evidence(evidence, root)

    def test_validation_rejects_alpha_mask_promotion(self) -> None:
        root = fixture_root()
        evidence = kan054.build_evidence(root)
        evidence["standaloneAlphaMaskRefusal"]["status"] = "pass"

        with self.assertRaisesRegex(kan054.ValidationError, "standalone alpha-mask support changed"):
            kan054.validate_evidence(evidence, root)

    def test_validation_rejects_threshold_change(self) -> None:
        root = fixture_root()
        evidence = kan054.build_evidence(root)
        evidence["stats"]["globalThresholdChanged"] = True

        with self.assertRaisesRegex(kan054.ValidationError, "global threshold changed"):
            kan054.validate_evidence(evidence, root)

    def test_validation_rejects_unblocked_kan053_prior_evidence(self) -> None:
        root = fixture_root()
        evidence = kan054.build_evidence(root)
        evidence["kan053PriorEvidence"]["blocked"] = False

        with self.assertRaisesRegex(kan054.ValidationError, "KAN-053 prior evidence must remain blocked"):
            kan054.validate_evidence(evidence, root)

    def test_validation_rejects_changed_kan053_blocker_reason(self) -> None:
        root = fixture_root()
        evidence = kan054.build_evidence(root)
        evidence["kan053PriorEvidence"]["blocker"]["reasonCode"] = "outline-path-fixed"

        with self.assertRaisesRegex(kan054.ValidationError, "KAN-053 prior blocker reason changed"):
            kan054.validate_evidence(evidence, root)

    def test_validation_rejects_missing_kan053_root_cause(self) -> None:
        root = fixture_root()
        evidence = kan054.build_evidence(root)
        del evidence["kan053PriorEvidence"]["blocker"]["rootCause"]

        with self.assertRaisesRegex(kan054.ValidationError, "KAN-053 prior root cause changed"):
            kan054.validate_evidence(evidence, root)

    def test_validation_rejects_changed_kan053_root_cause(self) -> None:
        root = fixture_root()
        evidence = kan054.build_evidence(root)
        evidence["kan053PriorEvidence"]["blocker"]["rootCause"] = "renderer-fixed"

        with self.assertRaisesRegex(kan054.ValidationError, "KAN-053 prior root cause changed"):
            kan054.validate_evidence(evidence, root)

    def test_build_evidence_rejects_ambiguous_active_glyph_route_field(self) -> None:
        root = fixture_root()
        route_path = root / kan054.ROUTE_WEBGPU_JSON
        route = json.loads(route_path.read_text(encoding="utf-8"))
        route["glyphRoute"] = "font.glyph.outline-path"
        route_path.write_text(json.dumps(route, indent=2) + "\n", encoding="utf-8")

        with self.assertRaisesRegex(kan054.ValidationError, "ambiguous glyphRoute"):
            kan054.build_evidence(root)

    def test_validation_rejects_missing_non_claim(self) -> None:
        root = fixture_root()
        evidence = kan054.build_evidence(root)
        evidence["nonClaims"] = []

        with self.assertRaisesRegex(kan054.ValidationError, "missing non-claim"):
            kan054.validate_evidence(evidence, root)

    def test_write_outputs_materializes_report_with_fixture(self) -> None:
        root = fixture_root()
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp)
            evidence = kan054.write_outputs(root, output_dir)
            payload = json.loads((output_dir / kan054.OUTPUT_JSON).read_text(encoding="utf-8"))
            markdown = (output_dir / kan054.OUTPUT_MARKDOWN).read_text(encoding="utf-8")

        self.assertEqual(evidence["selectedRoute"], payload["selectedRoute"])
        self.assertIn("# KAN-054 WebGPU Glyph Atlas Sampling Route", markdown)
        self.assertIn(kan054.ATLAS_ROUTE, markdown)
        self.assertIn("unblocks KAN-055", markdown)

    def test_project_root_write_outputs_passes_after_renderer_route_lands(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            evidence = kan054.write_outputs(PROJECT_ROOT, Path(temp))
            payload = json.loads((Path(temp) / kan054.OUTPUT_JSON).read_text(encoding="utf-8"))

        self.assertEqual("pass", evidence["status"])
        self.assertEqual(kan054.ATLAS_ROUTE, payload["selectedRoute"])


if __name__ == "__main__":
    try:
        unittest.main()
    finally:
        for root in _FIXTURE_ROOTS:
            shutil.rmtree(root, ignore_errors=True)
