#!/usr/bin/env python3
"""Validate FOR-247 WebGPU Crop(input = Offset) scratch-probe evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "crop-image-filter-nonnull-prepass"
REFUSAL_ID = "image-filter-crop-nonnull-prepass-required"
SELECTED_ROUTE = "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
EXPECTED_RGBA = [202, 59, 19, 255]
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts"
    / SCENE_ID
)
SCRATCH_PROBE = ARTIFACT_ROOT / "scratch-probe-for247.json"
ROUTE_PREPASS = ARTIFACT_ROOT / "route-prepass.json"
WEBGPU_DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
WEBGPU_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleOffsetImageFilterWebGpuTest.kt"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-247-crop-offset-scratch-probe.md"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-247 validation failed: {message}")


def load_json(path: Path) -> Any:
    if not path.is_file():
        fail(f"missing JSON file: {path.relative_to(PROJECT_ROOT)}")
    return json.loads(path.read_text())


def find_scene(results: dict[str, Any], scene_id: str) -> dict[str, Any]:
    for scene in results.get("scenes", []):
        if scene.get("id") == scene_id:
            return scene
    fail(f"missing generated scene `{scene_id}`")


def require_text(owner: dict[str, Any], field: str, expected: str) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected `{expected}`, got `{actual}`")


def require_array(owner: dict[str, Any], field: str, expected: list[int]) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected {expected}, got {actual}")


def require_file_text(path: Path, needles: list[str]) -> None:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(PROJECT_ROOT)}")
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")


def main() -> None:
    results = load_json(PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/results.json")
    scene = find_scene(results, SCENE_ID)
    gpu = scene.get("gpu")
    if not isinstance(gpu, dict) or not isinstance(gpu.get("route"), dict):
        fail("selected scene must include GPU route diagnostics")
    require_text(gpu["route"], "selectedRoute", SELECTED_ROUTE)
    require_text(gpu["route"], "fallbackReason", "none")

    refusal = find_scene(results, REFUSAL_ID)
    refusal_gpu = refusal.get("gpu")
    if not isinstance(refusal_gpu, dict) or not isinstance(refusal_gpu.get("route"), dict):
        fail("refusal row must keep GPU route diagnostics")
    require_text(refusal_gpu["route"], "fallbackReason", FALLBACK_REASON)

    route_prepass = load_json(ROUTE_PREPASS)
    require_text(route_prepass, "fallbackReason", "none")
    require_text(route_prepass, "unsupportedReasonRemoved", FALLBACK_REASON)
    require_text(route_prepass, "sampleRemap", "scratchPixel(p) samples childSource(p - offset)")
    require_text(route_prepass, "for247ScratchProbe", "scratch-probe-for247.json")
    require_text(
        route_prepass,
        "for247ProbeResult",
        "scratchPixel(45,5) == sourceLocal(5,5) == rgba(202,59,19,255)",
    )

    probe = load_json(SCRATCH_PROBE)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "sceneId", SCENE_ID)
    require_text(probe, "linear", "FOR-247")
    require_text(probe, "targetCell", "crop == clip == dst")
    require_text(probe, "selectedRoute", SELECTED_ROUTE)
    require_text(probe, "fallbackReason", "none")
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    if probe.get("matchesSourceLocal") is not True:
        fail("scratchPixel(45,5) must explicitly match sourceLocal(5,5)")
    source = probe.get("sourceLocal")
    scratch = probe.get("scratchPixel")
    if not isinstance(source, dict) or not isinstance(scratch, dict):
        fail("probe must include sourceLocal and scratchPixel objects")
    require_array(source, "xy", [5, 5])
    require_array(scratch, "xy", [45, 5])
    require_array(source, "rgba", EXPECTED_RGBA)
    require_array(scratch, "rgba", EXPECTED_RGBA)

    require_file_text(
        WEBGPU_DEVICE,
        [
            "kanvas.webgpu.for247.cropOffsetScratchProbe",
            "shouldProbeFor247CropOffsetScratch",
            "originX == 340",
            "originY == 120",
            "dstOriginX = -5",
            "dstOriginX = -44",
            "SkWebGpuDevice.cropNonNullOffsetChildPrePassScratch",
        ],
    )
    require_file_text(
        WEBGPU_TEST,
        [
            "FOR-247 crop offset prepass scratch pixel matches source local probe",
            "scratchPixel(45,5) must match sourceLocal(5,5)",
            "scratch-probe-for247.json",
        ],
    )
    require_file_text(
        REPORT,
        [
            "KEEP_DIAGNOSTIC",
            "`scratchPixel(45,5)` matches `sourceLocal(5,5)`",
            "`rgba(202,59,19,255)`",
            "No bounded materialization correction is applied",
            "image-filter.crop-input-nonnull-prepass-required",
        ],
    )

    print(
        "FOR-247 crop/offset scratch probe OK: "
        "scratchPixel(45,5) == sourceLocal(5,5) == rgba(202,59,19,255); "
        f"route={SELECTED_ROUTE}"
    )


if __name__ == "__main__":
    main()
